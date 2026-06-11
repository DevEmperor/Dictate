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
) {
    /** Base URL guaranteed to end with a single trailing slash. */
    val normalizedBaseUrl: String
        get() = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
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
