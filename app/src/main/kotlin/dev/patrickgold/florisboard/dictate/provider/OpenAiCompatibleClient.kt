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
     * transient failure triggers a retry, so the UI can surface a "retrying…" indicator.
     */
    suspend fun transcribe(
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

    override suspend fun transcribe(request: TranscriptionRequest): TranscriptionResult {
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
        val body = executeForBody(httpRequest)
        val response = json.decodeFromString(TranscriptionResponseDto.serializer(), body)
        return TranscriptionResult(response.text.trim())
    }

    override suspend fun listModels(): List<ModelInfo> {
        val httpRequest = Request.Builder()
            .url(config.normalizedBaseUrl + "models")
            .headers(authHeaders())
            .get()
            .build()
        val body = executeForBody(httpRequest, maxRetries = 1)
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
                throw DictateApiException.fromHttp(response.code, extractErrorMessage(body) ?: body.take(500))
            }
            return body
        }
    }

    private fun extractErrorMessage(body: String): String? = try {
        json.decodeFromString(ErrorEnvelopeDto.serializer(), body).error?.message
    } catch (_: Exception) {
        null
    }

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
    private data class TranscriptionResponseDto(val text: String = "")

    @Serializable
    private data class ModelsResponseDto(val data: List<ModelEntryDto> = emptyList())

    @Serializable
    private data class ModelEntryDto(val id: String)

    @Serializable
    private data class ErrorEnvelopeDto(val error: ErrorBodyDto? = null)

    @Serializable
    private data class ErrorBodyDto(val message: String? = null)

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val RETRY_DELAY_MS = 3000L

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
            )
        )
    }
}
