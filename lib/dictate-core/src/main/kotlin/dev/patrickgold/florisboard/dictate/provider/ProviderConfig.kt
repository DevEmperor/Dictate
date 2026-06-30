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

import java.net.InetSocketAddress
import java.net.Proxy

/**
 * Runtime configuration for a single OpenAI-compatible endpoint call.
 *
 * @param baseUrl OpenAI-compatible base URL (e.g. `https://openrouter.ai/api/v1/`). A trailing
 *   slash is added automatically if missing.
 * @param apiKey bearer token; may be blank for keyless local servers (e.g. Ollama).
 * @param extraHeaders provider-specific headers (e.g. OpenRouter's `X-Title`).
 */
data class ProviderConfig(
    val baseUrl: String,
    val apiKey: String,
    val extraHeaders: Map<String, String> = emptyMap(),
    val proxy: ProxyConfig? = null,
    val timeoutSeconds: Long = 120,
    val transcriptionApi: TranscriptionApi = TranscriptionApi.OPENAI_MULTIPART,
    /**
     * Single-call multimodal transcription (issue #130): when true, audio is sent to `chat/completions`
     * as an `input_audio` content part of a multimodal model (e.g. Gemini Flash) instead of using the
     * dedicated speech-to-text endpoint, so transcription + formatting happen in one request. Overrides
     * [transcriptionApi] for the transcribe path. Only valid for models that accept audio input.
     */
    val useChatAudio: Boolean = false,
) {
    /** Base URL guaranteed to end with a single trailing slash. */
    val normalizedBaseUrl: String
        get() = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
}

/**
 * Wire format of a provider's speech-to-text endpoint. Most providers (OpenAI, Groq, Mistral, …) accept
 * the OpenAI `multipart/form-data` file upload at `audio/transcriptions`. OpenRouter instead exposes a
 * JSON endpoint that takes base64-encoded audio in an `input_audio` object, so it needs its own path.
 * Soniox uses a multi-step async REST flow (upload → create job → poll → fetch transcript).
 */
enum class TranscriptionApi {
    /** OpenAI-style `multipart/form-data` upload with a `file` part. */
    OPENAI_MULTIPART,

    /** OpenRouter-style JSON body: `{ model, input_audio: { data (base64), format } }`. */
    OPENROUTER_JSON,

    /**
     * Soniox async flow against `api.soniox.com/v1/`: upload the file (`POST /files`), create a
     * transcription job (`POST /transcriptions` with `file_id`), poll `GET /transcriptions/{id}` until
     * `status == completed`, then fetch `GET /transcriptions/{id}/transcript`. See [OpenAiCompatibleClient].
     */
    SONIOX_ASYNC,

    /**
     * Google Gemini has no dedicated speech-to-text endpoint and its OpenAI-compatible layer (used for
     * chat/rewording) does not accept audio. Instead the audio is base64-inlined into a single
     * `POST {baseUrl}/../models/{model}:generateContent` call against the native Gemini API, instructing
     * the multimodal model to emit only the verbatim transcript. See [OpenAiCompatibleClient].
     */
    GEMINI_GENERATE_CONTENT,

    /**
     * On-device transcription (issue #104): no network call at all. Handled by
     * [dev.patrickgold.florisboard.dictate.provider.LocalTranscriptionProvider] (sherpa-onnx), not by
     * [OpenAiCompatibleClient]; this value only marks a provider preset as local so the dictation flow
     * dispatches to the offline engine instead of building an HTTP client.
     */
    LOCAL_ONDEVICE,
}

/**
 * Proxy protocol exposed in the settings UI. Maps onto the JVM [Proxy.Type] used by OkHttp; kept as a
 * dedicated enum so it can be persisted by name as a JetPref `enum` preference and shown in a dropdown.
 */
enum class DictateProxyType(val javaType: Proxy.Type) {
    /** HTTP CONNECT proxy. Supports username/password via the `Proxy-Authorization` header. */
    HTTP(Proxy.Type.HTTP),

    /** SOCKS5 proxy. Credentials are not currently forwarded (OkHttp/JVM limitation). */
    SOCKS5(Proxy.Type.SOCKS),
}

/**
 * Parsed proxy specification. Accepts `socks5|http://user:pass@host:port` (scheme + credentials
 * optional). Ported from the original Dictate `DictateUtils.isValidProxy` / `applyProxy`.
 */
data class ProxyConfig(
    val type: Proxy.Type,
    val host: String,
    val port: Int,
    val username: String? = null,
    val password: String? = null,
) {
    fun toJavaProxy(): Proxy = Proxy(type, InetSocketAddress(host, port))

    val hasCredentials: Boolean
        get() = !username.isNullOrEmpty() && !password.isNullOrEmpty()

    companion object {
        private val REGEX =
            Regex("^(?:(socks5|http)://)?(?:(\\w+):(\\w+)@)?([\\w.-]+):(\\d+)$")

        /**
         * Builds a config from the individual settings fields, or `null` when the proxy is disabled or
         * incompletely/invalidly configured (in which case calls go out directly). [host] may be a
         * hostname or IPv4 literal; [port] must be a valid TCP port. Blank credentials become `null`.
         */
        fun of(
            enabled: Boolean,
            type: DictateProxyType,
            host: String,
            port: Int,
            username: String,
            password: String,
        ): ProxyConfig? {
            if (!enabled) return null
            val trimmedHost = host.trim()
            if (trimmedHost.isEmpty() || port !in 1..65535) return null
            return ProxyConfig(
                type = type.javaType,
                host = trimmedHost,
                port = port,
                username = username.trim().ifEmpty { null },
                password = password.ifEmpty { null },
            )
        }

        /** Returns true if [spec] is a syntactically valid proxy string. */
        fun isValid(spec: String?): Boolean {
            if (spec.isNullOrEmpty()) return false
            val match = REGEX.matchEntire(spec) ?: return false
            val host = match.groupValues[4]
            if (host.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))) {
                return host.split(".").all { part -> part.toIntOrNull()?.let { it in 0..255 } == true }
            }
            return true
        }

        /** Parses [spec] into a [ProxyConfig], or null if invalid. */
        fun parse(spec: String?): ProxyConfig? {
            if (!isValid(spec)) return null
            val match = REGEX.matchEntire(spec!!) ?: return null
            val scheme = match.groupValues[1]
            val user = match.groupValues[2].ifEmpty { null }
            val pass = match.groupValues[3].ifEmpty { null }
            val host = match.groupValues[4]
            val port = match.groupValues[5].toIntOrNull() ?: return null
            val type = if (scheme.equals("socks5", ignoreCase = true)) Proxy.Type.SOCKS else Proxy.Type.HTTP
            return ProxyConfig(type, host, port, user, pass)
        }
    }
}
