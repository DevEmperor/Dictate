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
 * Providers with a genuinely different API (e.g. Anthropic, Google Gemini native) would need their
 * own [LlmProvider] implementation; until then they are reachable via OpenRouter.
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
    ): TranscriptionResult = when (config.transcriptionApi) {
        TranscriptionApi.OPENAI_MULTIPART -> transcribeMultipart(request, onRetry)
        TranscriptionApi.OPENROUTER_JSON -> transcribeOpenRouterJson(request, onRetry)
        TranscriptionApi.SONIOX_ASYNC -> transcribeSonioxAsync(request, onRetry)
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
        return response.data.map { ModelInfo(it.id) }.sortedBy { it.id.lowercase() }
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

    @Serializable
    private data class TranscriptionResponseDto(val text: String = "")

    @Serializable
    private data class ModelsResponseDto(val data: List<ModelEntryDto> = emptyList())

    @Serializable
    private data class ModelEntryDto(val id: String)

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
        ): OpenAiCompatibleClient = OpenAiCompatibleClient(
            ProviderConfig(
                baseUrl = baseUrlOverride ?: preset.baseUrl,
                apiKey = apiKey,
                extraHeaders = preset.extraHeaders,
                proxy = proxy,
                transcriptionApi = preset.transcriptionApi,
            )
        )
    }
}
