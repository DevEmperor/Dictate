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

import java.io.InterruptedIOException

/**
 * Normalised error for all provider calls. The [kind] lets the UI show the right message/action
 * (mirrors the error handling of the original Dictate input method service).
 */
class DictateApiException(
    val kind: Kind,
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause) {

    enum class Kind {
        INVALID_API_KEY,
        QUOTA_EXCEEDED,
        CONTENT_SIZE_LIMIT,
        FORMAT_NOT_SUPPORTED,
        TIMEOUT,
        NETWORK,
        UNKNOWN;

        /** Whether retrying the same request could plausibly succeed. */
        val isRetryable: Boolean
            get() = this == TIMEOUT || this == NETWORK || this == UNKNOWN
    }

    companion object {
        /** Classifies a non-2xx HTTP response into a [Kind] using status code + error message. */
        fun fromHttp(status: Int, message: String?): DictateApiException {
            val msg = message?.lowercase().orEmpty()
            val kind = when {
                status == 401 || status == 403 || msg.contains("api key") -> Kind.INVALID_API_KEY
                status == 429 || msg.contains("quota") -> Kind.QUOTA_EXCEEDED
                status == 413 || msg.contains("audio duration") || msg.contains("content size limit") ->
                    Kind.CONTENT_SIZE_LIMIT
                msg.contains("format") -> Kind.FORMAT_NOT_SUPPORTED
                else -> Kind.UNKNOWN
            }
            return DictateApiException(kind, message ?: "HTTP $status", null)
        }

        /** Classifies a transport-level exception (timeouts, no connection, …). */
        fun fromIo(cause: Throwable): DictateApiException {
            val m = cause.message?.lowercase().orEmpty()
            val kind = when {
                cause is InterruptedIOException || m.contains("timeout") || m.contains("timed out") -> Kind.TIMEOUT
                else -> Kind.NETWORK
            }
            return DictateApiException(kind, cause.message, cause)
        }
    }
}
