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

/** Chat/rewording capability. Implemented by any OpenAI Chat Completions compatible endpoint. */
interface LlmProvider {
    /** @throws DictateApiException on failure. */
    suspend fun complete(request: ChatRequest): ChatResult

    /**
     * Fetches the provider's model catalog (`GET /models`). Returns an empty list if the provider
     * does not support it. Used to let users freely pick a model (e.g. OpenRouter's large catalog).
     */
    suspend fun listModels(): List<ModelInfo>
}

/** Speech-to-text capability (e.g. OpenAI / Groq Whisper-style transcription endpoints). */
interface TranscriptionProvider {
    /** @throws DictateApiException on failure. */
    suspend fun transcribe(request: TranscriptionRequest): TranscriptionResult
}
