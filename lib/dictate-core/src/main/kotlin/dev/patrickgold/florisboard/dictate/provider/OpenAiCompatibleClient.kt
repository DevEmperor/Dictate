/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate.provider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.Headers
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.net.Proxy
import java.time.Duration

/**
 * A single client implementation that talks to any OpenAI Chat Completions / Audio Transcriptions
 * compatible endpoint. This one class covers OpenAI, Groq, OpenRouter, Together, DeepInfra, Mistral,
 * xAI, DeepSeek, local Ollama and arbitrary custom servers – they only differ by base URL, key and
 * a few headers (see [ProviderRegistry] and [ProviderConfig]).
 *
 * Google Gemini is also handled here: chat/rewording goes through its OpenAI-compatible layer
 * unchanged, while transcription uses the native generateContent endpoint (see
 * [transcribeGeminiGenerateContent]). Providers with a genuinely different chat API (e.g. Anthropic
 * native) would still need their own [LlmProvider] implementation; until then they are reachable via
 * OpenRouter.
 */
class OpenAiCompatibleClient(
    private val config: ProviderConfig,
) : LlmProvider, TranscriptionProvider {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val client: OkHttpClient by lazy { buildClient() }

    override suspend fun complete(request: ChatRequest): ChatResult {
        val dto = ChatCompletionRequestDto(
            model = request.model,
            messages = request.messages.map { MessageDto(it.role.wire, it.content) },
            temperature = request.temperature,
            maxTokens = request.maxTokens,
        )
        val payload = json.encodeToString(ChatCompletionRequestDto.serializer(), dto)
        val httpRequest = Request.Builder()
            .url(config.normalizedBaseUrl + "chat/completions")
            .headers(authHeaders())
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val body = executeForBody(httpRequest)
        val response = json.decodeFromString(ChatCompletionResponseDto.serializer(), body)
        val text = response.choices.firstOrNull()?.message?.content.orEmpty()
        // Some OpenAI-compatible gateways (notably OpenRouter) report errors as HTTP 200 with an empty
        // `choices` array and an `{ "error": { ... } }` envelope. Surface that instead of returning "".
        if (text.isBlank() && response.choices.isEmpty()) {
            val message = extractErrorMessage(body)
            throw DictateApiException(DictateApiException.Kind.UNKNOWN, message ?: "Empty response from provider")
        }
        val usage = response.usage?.let { TokenUsage(it.promptTokens, it.completionTokens) }
        return ChatResult(text, usage)
    }

    /**
     * Transcribes [request]. [onRetry] is invoked with the (1-based) attempt number each time a
     * transient failure triggers a retry, so the UI can surface a "retrying…" indicator. Dispatches to
     * the right wire format for the configured provider (see [TranscriptionApi]).
     */
    suspend fun transcribe(
        request: TranscriptionRequest,
        onRetry: (attempt: Int) -> Unit,
    ): TranscriptionResult = when {
        // Single-call multimodal (issue #130): route audio through chat/completions with input_audio,
        // overriding the dedicated STT endpoint, so one request transcribes and formats together.
        config.useChatAudio -> transcribeViaChatAudio(request, onRetry)
        else -> transcribeByApi(request, onRetry)
    }

    private suspend fun transcribeByApi(
        request: TranscriptionRequest,
        onRetry: (attempt: Int) -> Unit,
    ): TranscriptionResult = when (config.transcriptionApi) {
        TranscriptionApi.OPENAI_MULTIPART -> transcribeMultipart(request, onRetry)
        TranscriptionApi.OPENROUTER_JSON -> transcribeOpenRouterJson(request, onRetry)
        TranscriptionApi.SONIOX_ASYNC -> transcribeSonioxAsync(request, onRetry)
        TranscriptionApi.GEMINI_GENERATE_CONTENT -> transcribeGeminiGenerateContent(request, onRetry)
        // On-device transcription never uses this HTTP client; the dictation flow routes local providers
        // to LocalTranscriptionProvider before one is ever constructed.
        TranscriptionApi.LOCAL_ONDEVICE -> error("LOCAL_ONDEVICE is handled by LocalTranscriptionProvider")
    }

    override suspend fun transcribe(request: TranscriptionRequest): TranscriptionResult =
        transcribe(request, onRetry = {})

    /** OpenAI-style `multipart/form-data` upload (OpenAI, Groq, Mistral, most custom servers). */
    private suspend fun transcribeMultipart(
        request: TranscriptionRequest,
        onRetry: (attempt: Int) -> Unit,
    ): TranscriptionResult {
        val fileBody = request.audioFile.asRequestBody(guessAudioMediaType(request.audioFile))
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", request.audioFile.name, fileBody)
            .addFormDataPart("model", request.model)
            .addFormDataPart("response_format", "json")
            .apply {
                val lang = request.language
                if (!lang.isNullOrEmpty() && lang != "detect") addFormDataPart("language", lang)
                if (!request.prompt.isNullOrEmpty()) addFormDataPart("prompt", request.prompt)
            }
            .build()
        val httpRequest = Request.Builder()
            .url(config.normalizedBaseUrl + "audio/transcriptions")
            .headers(authHeaders())
            .post(multipart)
            .build()
        val body = executeForBody(httpRequest, onRetry = onRetry)
        val response = json.decodeFromString(TranscriptionResponseDto.serializer(), body)
        return TranscriptionResult(response.text.trim())
    }

    /**
     * OpenRouter's JSON transcription endpoint: the audio is base64-encoded and wrapped in an
     * `input_audio` object instead of a multipart upload. `prompt` has no equivalent here and is
     * ignored. See https://openrouter.ai/docs/api/api-reference/transcriptions/create-audio-transcriptions
     */
    private suspend fun transcribeOpenRouterJson(
        request: TranscriptionRequest,
        onRetry: (attempt: Int) -> Unit,
    ): TranscriptionResult {
        val base64 = withContext(Dispatchers.IO) {
            android.util.Base64.encodeToString(request.audioFile.readBytes(), android.util.Base64.NO_WRAP)
        }
        val lang = request.language?.takeIf { it.isNotEmpty() && it != "detect" }
        val dto = TranscriptionJsonRequestDto(
            model = request.model,
            inputAudio = InputAudioDto(data = base64, format = guessAudioFormat(request.audioFile)),
            language = lang,
        )
        val payload = json.encodeToString(TranscriptionJsonRequestDto.serializer(), dto)
        val httpRequest = Request.Builder()
            .url(config.normalizedBaseUrl + "audio/transcriptions")
            .headers(authHeaders())
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val body = executeForBody(httpRequest, onRetry = onRetry)
        val response = json.decodeFromString(TranscriptionResponseDto.serializer(), body)
        return TranscriptionResult(response.text.trim())
    }

    /**
     * Single-call multimodal transcription (issue #130): sends the audio as an `input_audio` content part
     * to `chat/completions` of a multimodal model (e.g. Gemini Flash) together with a text instruction, so
     * the model transcribes (and formats, per the instruction) in one request. The instruction comes from
     * [TranscriptionRequest.prompt] (the caller builds it: style + formatting); a sane default is prepended.
     * Returns the model's text output. Reuses the chat error-envelope handling from [complete].
     */
    private suspend fun transcribeViaChatAudio(
        request: TranscriptionRequest,
        onRetry: (attempt: Int) -> Unit,
    ): TranscriptionResult {
        val base64 = withContext(Dispatchers.IO) {
            android.util.Base64.encodeToString(request.audioFile.readBytes(), android.util.Base64.NO_WRAP)
        }
        val extra = request.prompt?.trim()?.takeIf { it.isNotEmpty() }
        val instruction = buildString {
            append("Transcribe the speech in the attached audio.")
            if (extra != null) {
                append(
                    " Then apply ALL of the following instructions to the transcript before returning it — " +
                        "they are mandatory and may change the wording or even the language (e.g. translation, " +
                        "formatting):\n\n",
                )
                append(extra)
            }
            request.language?.takeIf { it.isNotEmpty() && it != "detect" }
                ?.let { append("\n\nThe language spoken in the audio is '$it'.") }
            append("\n\nReturn ONLY the final resulting text after applying the instructions — no preamble, no quotes, no explanations, no notes.")
        }
        val dto = ChatAudioRequestDto(
            model = request.model,
            temperature = 0.0,
            messages = listOf(
                ChatAudioMessageDto(
                    role = "user",
                    content = listOf(
                        ContentPartDto(type = "text", text = instruction),
                        ContentPartDto(
                            type = "input_audio",
                            inputAudio = InputAudioDto(data = base64, format = guessAudioFormat(request.audioFile)),
                        ),
                    ),
                ),
            ),
        )
        val payload = json.encodeToString(ChatAudioRequestDto.serializer(), dto)
        val httpRequest = Request.Builder()
            .url(config.normalizedBaseUrl + "chat/completions")
            .headers(authHeaders())
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val body = executeForBody(httpRequest, onRetry = onRetry)
        val response = json.decodeFromString(ChatCompletionResponseDto.serializer(), body)
        val text = response.choices.firstOrNull()?.message?.content.orEmpty()
        if (text.isBlank() && response.choices.isEmpty()) {
            val message = extractErrorMessage(body)
            throw DictateApiException(DictateApiException.Kind.UNKNOWN, message ?: "Empty response from provider")
        }
        return TranscriptionResult(text.trim())
    }

    /**
     * Soniox async transcription. Unlike the OpenAI/OpenRouter one-shot endpoints this is a multi-step
     * REST flow (see [TranscriptionApi.SONIOX_ASYNC]):
     *   1. upload the audio (`POST /files`) → `file_id`
     *   2. create a job (`POST /transcriptions` with `file_id`) → transcription id
     *   3. poll `GET /transcriptions/{id}` until `status == completed` (or `error`)
     *   4. fetch `GET /transcriptions/{id}/transcript` → the assembled `text`
     * The uploaded file and the transcription are deleted afterwards (best-effort) because Soniox caps the
     * number of stored files/transcriptions per organization. [onRetry] only covers transient per-request
     * network retries; the polling itself is normal operation and does not report a retry.
     */
    private suspend fun transcribeSonioxAsync(
        request: TranscriptionRequest,
        onRetry: (attempt: Int) -> Unit,
    ): TranscriptionResult {
        val base = config.normalizedBaseUrl

        // 1. Upload the audio file.
        val fileBody = request.audioFile.asRequestBody(guessAudioMediaType(request.audioFile))
        val uploadBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", request.audioFile.name, fileBody)
            .build()
        val uploadRequest = Request.Builder()
            .url(base + "files")
            .headers(authHeaders())
            .post(uploadBody)
            .build()
        val fileId = json.decodeFromString(
            SonioxFileDto.serializer(),
            executeForBody(uploadRequest, onRetry = onRetry),
        ).id

        var transcriptionId: String? = null
        try {
            // 2. Create the transcription job referencing the uploaded file.
            val lang = request.language?.takeIf { it.isNotEmpty() && it != "detect" }
            val createDto = SonioxCreateDto(
                model = request.model,
                fileId = fileId,
                languageHints = lang?.let { listOf(it) },
                // The style/punctuation prompt maps onto Soniox's free-text `context` field.
                context = request.prompt?.takeIf { it.isNotBlank() },
            )
            val createRequest = Request.Builder()
                .url(base + "transcriptions")
                .headers(authHeaders())
                .post(json.encodeToString(SonioxCreateDto.serializer(), createDto).toRequestBody(JSON_MEDIA_TYPE))
                .build()
            val id = json.decodeFromString(
                SonioxTranscriptionDto.serializer(),
                executeForBody(createRequest, onRetry = onRetry),
            ).id
            transcriptionId = id

            // 3. Poll until the job completes or fails (or we exceed the overall budget).
            val statusUrl = base + "transcriptions/" + id
            var waitedMs = 0L
            while (true) {
                val statusRequest = Request.Builder()
                    .url(statusUrl)
                    .headers(authHeaders())
                    .get()
                    .build()
                val status = json.decodeFromString(
                    SonioxTranscriptionDto.serializer(),
                    executeForBody(statusRequest, maxRetries = 2, onRetry = onRetry),
                )
                when (status.status) {
                    "completed" -> break
                    "error", "failed" -> {
                        // Soniox reports billing/quota problems as a job error (not an HTTP 402), so run the
                        // message through the same classifier — a balance/quota issue must not look like a
                        // transient "try again" server error. The 502 default keeps genuine processing
                        // failures retryable.
                        throw DictateApiException.fromHttp(
                            status = 502,
                            message = status.errorMessage ?: "Soniox transcription failed",
                        )
                    }
                    // queued / processing / downloading → keep waiting
                    else -> {
                        if (waitedMs >= SONIOX_POLL_TIMEOUT_MS) {
                            throw DictateApiException(
                                DictateApiException.Kind.TIMEOUT,
                                "Soniox transcription timed out",
                            )
                        }
                        delay(SONIOX_POLL_INTERVAL_MS)
                        waitedMs += SONIOX_POLL_INTERVAL_MS
                    }
                }
            }

            // 4. Fetch the finished transcript (the top-level `text` is already fully assembled).
            val transcriptRequest = Request.Builder()
                .url(statusUrl + "/transcript")
                .headers(authHeaders())
                .get()
                .build()
            val transcript = json.decodeFromString(
                SonioxTranscriptDto.serializer(),
                executeForBody(transcriptRequest, onRetry = onRetry),
            )
            return TranscriptionResult(transcript.text.trim())
        } finally {
            // Best-effort cleanup so we don't pile up against Soniox's stored-object limits.
            transcriptionId?.let { sonioxDelete(base + "transcriptions/" + it) }
            sonioxDelete(base + "files/" + fileId)
        }
    }

    /** Fire-and-forget DELETE used to clean up Soniox files/transcriptions; failures are ignored. */
    private suspend fun sonioxDelete(url: String) {
        runCatching {
            withContext(Dispatchers.IO) {
                val request = Request.Builder().url(url).headers(authHeaders()).delete().build()
                client.newCall(request).execute().use { /* ignore body/status */ }
            }
        }
    }

    /**
     * Google Gemini transcription. Gemini exposes no speech-to-text endpoint; its multimodal models
     * transcribe audio sent as base64 `inline_data` to the native `generateContent` endpoint (the
     * OpenAI-compatible layer used for chat does not accept audio). We give the model a strict instruction
     * to emit only the verbatim transcript – and nothing at all for silence – so the output can be used
     * directly and won't echo the style hint or hallucinate on empty audio.
     */
    private suspend fun transcribeGeminiGenerateContent(
        request: TranscriptionRequest,
        onRetry: (attempt: Int) -> Unit,
    ): TranscriptionResult {
        val base64 = withContext(Dispatchers.IO) {
            android.util.Base64.encodeToString(request.audioFile.readBytes(), android.util.Base64.NO_WRAP)
        }
        val mimeType = guessAudioMediaType(request.audioFile).toString().substringBefore(";").trim()
        val dto = GeminiGenerateRequestDto(
            contents = listOf(
                GeminiContentDto(
                    parts = listOf(
                        GeminiPartDto(text = buildGeminiTranscriptionInstruction(request)),
                        GeminiPartDto(inlineData = GeminiInlineDataDto(mimeType = mimeType, data = base64)),
                    ),
                ),
            ),
            // Temperature 0 keeps the model faithful to the audio and discourages creative rewrites.
            generationConfig = GeminiGenerationConfigDto(temperature = 0.0),
        )
        val payload = json.encodeToString(GeminiGenerateRequestDto.serializer(), dto)
        // The native URL carries the `models/` prefix itself, so strip any the user/catalog included.
        val model = request.model.removePrefix("models/")
        val httpRequest = Request.Builder()
            .url(geminiNativeBaseUrl() + "models/" + model + ":generateContent")
            .headers(geminiNativeHeaders())
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val body = executeForBody(httpRequest, onRetry = onRetry)
        val response = json.decodeFromString(GeminiGenerateResponseDto.serializer(), body)
        val text = response.candidates.firstOrNull()?.content?.parts.orEmpty()
            .mapNotNull { it.text }
            .joinToString("")
        return TranscriptionResult(text.trim())
    }

    /** Strict transcription prompt for [transcribeGeminiGenerateContent]; folds in the language and style hints. */
    private fun buildGeminiTranscriptionInstruction(request: TranscriptionRequest): String = buildString {
        append("Transcribe the speech in the audio exactly as spoken, with correct punctuation and ")
        append("capitalization. Output only the transcription text. Do not add any preamble, commentary, ")
        append("translation, quotation marks, or formatting. If there is no intelligible speech, output ")
        append("nothing at all.")
        request.language?.takeIf { it.isNotEmpty() && it != "detect" }?.let { lang ->
            append(" The spoken language is '").append(lang).append("'; transcribe in that language.")
        }
        request.prompt?.takeIf { it.isNotBlank() }?.let { style ->
            append("\n\nStyle/context hint (use it to guide spelling and punctuation, but never transcribe ")
            append("the hint itself):\n").append(style)
        }
    }

    /** Native Gemini base URL (`.../v1beta/`) derived from the OpenAI-compat base (`.../v1beta/openai/`). */
    private fun geminiNativeBaseUrl(): String = config.normalizedBaseUrl.removeSuffix("openai/")

    /** Gemini's native API authenticates via the `x-goog-api-key` header rather than a bearer token. */
    private fun geminiNativeHeaders(): Headers {
        val builder = Headers.Builder()
        if (config.apiKey.isNotBlank()) {
            builder.add("x-goog-api-key", config.apiKey)
        }
        config.extraHeaders.forEach { (key, value) -> builder.add(key, value) }
        return builder.build()
    }

    override suspend fun listModels(): List<ModelInfo> {
        val httpRequest = Request.Builder()
            .url(config.normalizedBaseUrl + "models")
            .headers(authHeaders())
            .get()
            .build()
        val body = executeForBody(httpRequest, maxRetries = 1)
        // Soniox returns `{ models: [ { id, transcription_mode, … } ] }` instead of OpenAI's `{ data: [...] }`,
        // and lists both async and real-time models; only the async ones work with our SONIOX_ASYNC flow.
        if (config.transcriptionApi == TranscriptionApi.SONIOX_ASYNC) {
            val response = json.decodeFromString(SonioxModelsDto.serializer(), body)
            return response.models
                .filter { it.transcriptionMode == "async" }
                .map { ModelInfo(it.id) }
                .sortedBy { it.id.lowercase() }
        }
        val response = json.decodeFromString(ModelsResponseDto.serializer(), body)
        // Gemini's catalog reports ids as `models/gemini-…`; strip that prefix so the picker shows clean
        // ids that also work directly as the `model` field in both chat and generateContent calls.
        val stripPrefix = config.transcriptionApi == TranscriptionApi.GEMINI_GENERATE_CONTENT
        return response.data
            .map {
                ModelInfo(
                    id = if (stripPrefix) it.id.removePrefix("models/") else it.id,
                    // Normalize each provider's own modality reporting to a single "audio" flag, used by
                    // the single-call multimodal feature (issue #130) and the 🎤 markers (#132).
                    inputModalities = if (isAudioInputChatModel(it)) listOf("audio") else emptyList(),
                )
            }
            .sortedBy { it.id.lowercase() }
    }

    /**
     * Whether a catalog entry is an audio-input **chat** model usable for single-call multimodal
     * transcription (issue #130). Each provider reports this differently (verified against the live APIs):
     *  - **Mistral** exposes a `capabilities` object → `audio && completion_chat` (e.g. Voxtral).
     *  - **OpenRouter** lists `architecture.input_modalities` → contains `audio` (its audio entries are
     *    chat models; Whisper-style STT is served elsewhere and isn't listed with audio input).
     *  - **Groq** uses top-level `input_modalities`/`output_modalities` → audio in, **text** out; this
     *    excludes Whisper, whose output modality is `transcription` (STT-only, not a chat model).
     *  - **OpenAI** and **Gemini** report no modality info at all → treated as unknown (false).
     */
    private fun isAudioInputChatModel(m: ModelEntryDto): Boolean {
        m.capabilities?.let { return it.audio && it.completionChat }
        m.architecture?.let { arch ->
            return arch.inputModalities.any { it.equals("audio", ignoreCase = true) }
        }
        m.inputModalities?.let { inputs ->
            val audioIn = inputs.any { it.equals("audio", ignoreCase = true) }
            val textOut = m.outputModalities?.any { it.equals("text", ignoreCase = true) } == true
            return audioIn && textOut
        }
        return false
    }

    private fun authHeaders(): Headers {
        val builder = Headers.Builder()
        if (config.apiKey.isNotBlank()) {
            builder.add("Authorization", "Bearer ${config.apiKey}")
        }
        config.extraHeaders.forEach { (key, value) -> builder.add(key, value) }
        return builder.build()
    }

    private suspend fun executeForBody(
        request: Request,
        maxRetries: Int = 3,
        onRetry: (attempt: Int) -> Unit = {},
    ): String {
        var attempt = 0
        while (true) {
            try {
                return withContext(Dispatchers.IO) { executeOnce(request) }
            } catch (e: Throwable) {
                val mapped = when (e) {
                    is DictateApiException -> e
                    is IOException -> DictateApiException.fromIo(e)
                    else -> DictateApiException(DictateApiException.Kind.UNKNOWN, e.message, e)
                }
                if (mapped.kind.isRetryable && attempt < maxRetries) {
                    attempt++
                    onRetry(attempt + 1) // report the upcoming attempt (2nd, 3rd, …)
                    delay(RETRY_DELAY_MS)
                } else {
                    throw mapped
                }
            }
        }
    }

    /** Blocking single HTTP call. Throws [DictateApiException] on non-2xx, [IOException] on transport errors. */
    private fun executeOnce(request: Request): String {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val error = parseError(body)
                throw DictateApiException.fromHttp(
                    status = response.code,
                    message = error?.message ?: body.take(500),
                    code = error?.code,
                    type = error?.type,
                )
            }
            return body
        }
    }

    /**
     * Extracts the error detail from a non-2xx body. Tries the OpenAI-style `{ "error": { … } }` envelope
     * first, then falls back to Soniox's flat `{ error_type, message, status_code }` shape; null if the body
     * is neither (e.g. plain-text gateways).
     */
    private fun parseError(body: String): ErrorBodyDto? {
        runCatching { json.decodeFromString(ErrorEnvelopeDto.serializer(), body).error }
            .getOrNull()?.let { return it }
        return runCatching {
            val soniox = json.decodeFromString(SonioxErrorDto.serializer(), body)
            if (soniox.message.isNullOrBlank() && soniox.errorType.isNullOrBlank()) {
                null
            } else {
                ErrorBodyDto(message = soniox.message, code = soniox.errorType, type = soniox.errorType)
            }
        }.getOrNull()
    }

    private fun extractErrorMessage(body: String): String? = parseError(body)?.message

    private fun buildClient(): OkHttpClient {
        val timeout = Duration.ofSeconds(config.timeoutSeconds)
        val builder = OkHttpClient.Builder()
            .callTimeout(timeout)
            .connectTimeout(timeout)
            .readTimeout(timeout)
            .writeTimeout(timeout)
        config.proxy?.let { proxy ->
            builder.proxy(proxy.toJavaProxy())
            if (proxy.type == Proxy.Type.HTTP && proxy.hasCredentials) {
                builder.proxyAuthenticator { _, response ->
                    response.request.newBuilder()
                        .header("Proxy-Authorization", Credentials.basic(proxy.username!!, proxy.password!!))
                        .build()
                }
            }
            // SOCKS proxy authentication is not handled here (OkHttp limitation). Add a
            // java.net.Authenticator if SOCKS-with-credentials support is ever required.
        }
        return builder.build()
    }

    private fun guessAudioMediaType(file: File): MediaType {
        val type = when (file.extension.lowercase()) {
            "mp3", "mpeg", "mpga" -> "audio/mpeg"
            "mp4", "m4a" -> "audio/mp4"
            "wav" -> "audio/wav"
            "webm" -> "audio/webm"
            "ogg", "oga" -> "audio/ogg"
            "flac" -> "audio/flac"
            "amr" -> "audio/amr"
            else -> "application/octet-stream"
        }
        return type.toMediaType()
    }

    /**
     * Maps a file to one of OpenRouter's accepted `format` strings (wav, mp3, flac, m4a, ogg, webm,
     * aac). Dictate records m4a; other extensions come from picked files. Unknown extensions are passed
     * through as-is so a still-valid container isn't rejected client-side.
     */
    private fun guessAudioFormat(file: File): String = when (val ext = file.extension.lowercase()) {
        "mp4", "m4a", "aac" -> "m4a"
        "mpeg", "mpga", "mp3" -> "mp3"
        "oga", "ogg" -> "ogg"
        "wav", "flac", "webm" -> ext
        else -> ext
    }

    @Serializable
    private data class ChatCompletionRequestDto(
        val model: String,
        val messages: List<MessageDto>,
        val temperature: Double? = null,
        @SerialName("max_tokens") val maxTokens: Int? = null,
    )

    @Serializable
    private data class MessageDto(val role: String, val content: String)

    @Serializable
    private data class ChatCompletionResponseDto(
        val choices: List<ChoiceDto> = emptyList(),
        val usage: UsageDto? = null,
    )

    @Serializable
    private data class ChoiceDto(val message: ResponseMessageDto? = null)

    @Serializable
    private data class ResponseMessageDto(val content: String? = null)

    @Serializable
    private data class UsageDto(
        @SerialName("prompt_tokens") val promptTokens: Long = 0,
        @SerialName("completion_tokens") val completionTokens: Long = 0,
    )

    @Serializable
    private data class TranscriptionJsonRequestDto(
        val model: String,
        @SerialName("input_audio") val inputAudio: InputAudioDto,
        val language: String? = null,
    )

    @Serializable
    private data class InputAudioDto(val data: String, val format: String)

    // Single-call multimodal chat request (issue #130): chat/completions with array content carrying a
    // text instruction + an input_audio part. `encodeDefaults = false` keeps the unused nullable out.
    @Serializable
    private data class ChatAudioRequestDto(
        val model: String,
        val messages: List<ChatAudioMessageDto>,
        // 0 → deterministic, accurate transcription (mirrors the Gemini generateContent path); max_tokens
        // is intentionally left unset so a long dictation is never truncated.
        val temperature: Double? = null,
    )

    @Serializable
    private data class ChatAudioMessageDto(
        val role: String,
        val content: List<ContentPartDto>,
    )

    @Serializable
    private data class ContentPartDto(
        val type: String,
        val text: String? = null,
        @SerialName("input_audio") val inputAudio: InputAudioDto? = null,
    )

    @Serializable
    private data class TranscriptionResponseDto(val text: String = "")

    // --- Gemini native generateContent DTOs (see transcribeGeminiGenerateContent) ---

    @Serializable
    private data class GeminiGenerateRequestDto(
        val contents: List<GeminiContentDto>,
        val generationConfig: GeminiGenerationConfigDto? = null,
    )

    @Serializable
    private data class GeminiContentDto(
        // Defaulted so the same shape parses the response, where a blocked candidate may omit `parts`.
        val parts: List<GeminiPartDto> = emptyList(),
        val role: String? = null,
    )

    @Serializable
    private data class GeminiPartDto(
        val text: String? = null,
        // Gemini's proto-JSON accepts the snake_case `inline_data` on input; responses only carry `text`.
        @SerialName("inline_data") val inlineData: GeminiInlineDataDto? = null,
    )

    @Serializable
    private data class GeminiInlineDataDto(
        @SerialName("mime_type") val mimeType: String,
        val data: String,
    )

    @Serializable
    private data class GeminiGenerationConfigDto(val temperature: Double? = null)

    @Serializable
    private data class GeminiGenerateResponseDto(
        val candidates: List<GeminiCandidateDto> = emptyList(),
    )

    @Serializable
    private data class GeminiCandidateDto(val content: GeminiContentDto? = null)

    @Serializable
    private data class ModelsResponseDto(val data: List<ModelEntryDto> = emptyList())

    // Each provider exposes audio-input capability differently in its /models response (verified against
    // the live APIs): OpenRouter under `architecture.input_modalities`, Groq as top-level
    // `input_modalities`/`output_modalities`, Mistral via a `capabilities` object. OpenAI and Gemini
    // report no modality info at all. See [isAudioInputChatModel] (issue #130/#132).
    @Serializable
    private data class ModelEntryDto(
        val id: String,
        val architecture: ArchitectureDto? = null, // OpenRouter
        @SerialName("input_modalities") val inputModalities: List<String>? = null, // Groq (top-level)
        @SerialName("output_modalities") val outputModalities: List<String>? = null, // Groq (top-level)
        val capabilities: CapabilitiesDto? = null, // Mistral
    )

    @Serializable
    private data class ArchitectureDto(
        @SerialName("input_modalities") val inputModalities: List<String> = emptyList(),
    )

    @Serializable
    private data class CapabilitiesDto(
        val audio: Boolean = false,
        @SerialName("completion_chat") val completionChat: Boolean = false,
    )

    // --- Soniox async REST DTOs (see transcribeSonioxAsync) ---

    @Serializable
    private data class SonioxFileDto(val id: String)

    @Serializable
    private data class SonioxCreateDto(
        val model: String,
        @SerialName("file_id") val fileId: String,
        @SerialName("language_hints") val languageHints: List<String>? = null,
        val context: String? = null,
    )

    @Serializable
    private data class SonioxTranscriptionDto(
        val id: String = "",
        val status: String = "",
        @SerialName("error_message") val errorMessage: String? = null,
    )

    @Serializable
    private data class SonioxTranscriptDto(val text: String = "")

    @Serializable
    private data class SonioxModelsDto(val models: List<SonioxModelDto> = emptyList())

    @Serializable
    private data class SonioxModelDto(
        val id: String,
        @SerialName("transcription_mode") val transcriptionMode: String = "",
    )

    @Serializable
    private data class SonioxErrorDto(
        @SerialName("error_type") val errorType: String? = null,
        val message: String? = null,
    )

    @Serializable
    private data class ErrorEnvelopeDto(val error: ErrorBodyDto? = null)

    @Serializable
    private data class ErrorBodyDto(
        val message: String? = null,
        // OpenAI-style machine-readable hints (e.g. code = "invalid_api_key", type = "insufficient_quota").
        // Decoded as strings; providers that send a non-string code simply fall back to status/keywords.
        val code: String? = null,
        val type: String? = null,
    )

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val RETRY_DELAY_MS = 3000L

        /** Soniox async polling: interval between status checks and the overall budget before giving up. */
        private const val SONIOX_POLL_INTERVAL_MS = 1500L
        private const val SONIOX_POLL_TIMEOUT_MS = 300_000L

        /** Builds a client from a registry [preset] plus the user's key/proxy. */
        fun from(
            preset: ProviderPreset,
            apiKey: String,
            baseUrlOverride: String? = null,
            proxy: ProxyConfig? = null,
            useChatAudio: Boolean = false,
        ): OpenAiCompatibleClient = OpenAiCompatibleClient(
            ProviderConfig(
                baseUrl = baseUrlOverride ?: preset.baseUrl,
                apiKey = apiKey,
                extraHeaders = preset.extraHeaders,
                proxy = proxy,
                transcriptionApi = preset.transcriptionApi,
                useChatAudio = useChatAudio,
            )
        )
    }
}
