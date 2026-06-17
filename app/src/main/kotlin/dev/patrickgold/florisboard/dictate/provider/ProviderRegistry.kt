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

/**
 * A selectable provider option shown to the user.
 *
 * Base URLs are stable facts. Default model ids are conservative starting points only – the source
 * of truth is the live catalog via [LlmProvider.listModels] when [supportsDynamicModels] is true, so
 * users can freely pick any model the provider offers (important for OpenRouter's large catalog).
 *
 * NOTE: model ids must be re-verified against the provider when extending defaults – never guessed.
 */
data class ProviderPreset(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val capabilities: ProviderCapabilities,
    val supportsDynamicModels: Boolean,
    val apiKeyUrl: String? = null,
    val defaultChatModel: String? = null,
    val defaultTranscriptionModel: String? = null,
    val extraHeaders: Map<String, String> = emptyMap(),
    val isCustom: Boolean = false,
    /**
     * Curated, known-good model ids used by the model picker as an offline-capable starting set (and,
     * for transcription, to distinguish STT models since the `/models` catalog doesn't say which is
     * which). The live [LlmProvider.listModels] catalog is merged on top. Verify ids against the
     * provider when editing – never guessed.
     */
    val curatedChatModels: List<String> = emptyList(),
    val curatedTranscriptionModels: List<String> = emptyList(),
    /** Wire format of this provider's speech-to-text endpoint (OpenRouter differs – see [TranscriptionApi]). */
    val transcriptionApi: TranscriptionApi = TranscriptionApi.OPENAI_MULTIPART,
)

/**
 * Catalog of built-in OpenAI-compatible providers plus a factory for user-defined custom endpoints.
 */
object ProviderRegistry {

    private val CHAT_ONLY = ProviderCapabilities(chat = true, transcription = false)
    private val CHAT_AND_STT = ProviderCapabilities(chat = true, transcription = true)
    private val STT_ONLY = ProviderCapabilities(chat = false, transcription = true)

    val OPENAI = ProviderPreset(
        id = "openai",
        displayName = "OpenAI",
        baseUrl = "https://api.openai.com/v1/",
        capabilities = CHAT_AND_STT,
        supportsDynamicModels = true,
        apiKeyUrl = "https://platform.openai.com/api-keys",
        defaultChatModel = "gpt-4o-mini",
        defaultTranscriptionModel = "gpt-4o-mini-transcribe",
        curatedChatModels = listOf(
            "gpt-4o-mini", "gpt-4o", "gpt-4.1-mini", "gpt-4.1", "gpt-4.1-nano",
        ),
        curatedTranscriptionModels = listOf(
            "gpt-4o-mini-transcribe", "gpt-4o-transcribe", "whisper-1",
        ),
    )

    val GROQ = ProviderPreset(
        id = "groq",
        displayName = "Groq",
        baseUrl = "https://api.groq.com/openai/v1/",
        capabilities = CHAT_AND_STT,
        supportsDynamicModels = true,
        apiKeyUrl = "https://console.groq.com/keys",
        defaultChatModel = "llama-3.3-70b-versatile",
        defaultTranscriptionModel = "whisper-large-v3-turbo",
        curatedChatModels = listOf(
            "llama-3.3-70b-versatile", "llama-3.1-8b-instant", "gemma2-9b-it",
        ),
        curatedTranscriptionModels = listOf(
            "whisper-large-v3-turbo", "whisper-large-v3", "distil-whisper-large-v3-en",
        ),
    )

    val OPENROUTER = ProviderPreset(
        id = "openrouter",
        displayName = "OpenRouter",
        baseUrl = "https://openrouter.ai/api/v1/",
        // OpenRouter routes both chat and speech-to-text (its STT endpoint fronts Whisper, Voxtral,
        // MAI-Transcribe, …) – but via a JSON/base64 body, not the OpenAI multipart upload.
        capabilities = CHAT_AND_STT,
        transcriptionApi = TranscriptionApi.OPENROUTER_JSON,
        supportsDynamicModels = true,
        apiKeyUrl = "https://openrouter.ai/keys",
        // OpenRouter exposes hundreds of models (incl. Claude, Gemini, Llama …); users pick from the
        // live catalog. This is just a safe default to start with and is fully user-overridable.
        defaultChatModel = "openai/gpt-4o-mini",
        defaultTranscriptionModel = "openai/whisper-large-v3",
        // Verified against OpenRouter's STT docs; the live picker adds the rest (Voxtral, MAI, …).
        curatedTranscriptionModels = listOf(
            "openai/whisper-large-v3", "openai/whisper-1",
        ),
        // Attribution headers recommended by OpenRouter: both are used for app ranking and some routes
        // reject requests without an HTTP-Referer. The value is a stable identifier, not a real URL.
        extraHeaders = mapOf(
            "HTTP-Referer" to "https://github.com/DevEmperor/Dictate",
            "X-Title" to "Dictate",
        ),
    )

    val TOGETHER = ProviderPreset(
        id = "together",
        displayName = "Together AI",
        baseUrl = "https://api.together.xyz/v1/",
        capabilities = CHAT_ONLY,
        supportsDynamicModels = true,
        apiKeyUrl = "https://api.together.ai/settings/api-keys",
    )

    val DEEPINFRA = ProviderPreset(
        id = "deepinfra",
        displayName = "DeepInfra",
        baseUrl = "https://api.deepinfra.com/v1/openai/",
        capabilities = CHAT_ONLY,
        supportsDynamicModels = true,
        apiKeyUrl = "https://deepinfra.com/dash/api_keys",
    )

    val MISTRAL = ProviderPreset(
        id = "mistral",
        displayName = "Mistral AI",
        baseUrl = "https://api.mistral.ai/v1/",
        // Voxtral transcription via the standard OpenAI multipart endpoint (/v1/audio/transcriptions).
        capabilities = CHAT_AND_STT,
        supportsDynamicModels = true,
        apiKeyUrl = "https://console.mistral.ai/api-keys",
        defaultTranscriptionModel = "voxtral-mini-latest",
        curatedTranscriptionModels = listOf("voxtral-mini-latest"),
    )

    val SONIOX = ProviderPreset(
        id = "soniox",
        displayName = "Soniox",
        // Soniox is transcription-only and does NOT speak the OpenAI wire format: it uses a multi-step
        // async REST flow (see TranscriptionApi.SONIOX_ASYNC). Very accurate, strong multilingual/German.
        baseUrl = "https://api.soniox.com/v1/",
        capabilities = STT_ONLY,
        transcriptionApi = TranscriptionApi.SONIOX_ASYNC,
        // /v1/models is supported and returns transcription_mode per model; the client filters to async.
        supportsDynamicModels = true,
        apiKeyUrl = "https://console.soniox.com",
        defaultTranscriptionModel = "stt-async-v5",
        // Verified against Soniox's model catalog; the live picker adds any newer async models.
        curatedTranscriptionModels = listOf("stt-async-v5"),
    )

    val XAI = ProviderPreset(
        id = "xai",
        displayName = "xAI (Grok)",
        baseUrl = "https://api.x.ai/v1/",
        capabilities = CHAT_ONLY,
        supportsDynamicModels = true,
        apiKeyUrl = "https://console.x.ai",
    )

    val DEEPSEEK = ProviderPreset(
        id = "deepseek",
        displayName = "DeepSeek",
        baseUrl = "https://api.deepseek.com/v1/",
        capabilities = CHAT_ONLY,
        supportsDynamicModels = true,
        apiKeyUrl = "https://platform.deepseek.com/api_keys",
    )

    /** Local Ollama server (OpenAI-compatible). No API key required by default. */
    val OLLAMA = ProviderPreset(
        id = "ollama",
        displayName = "Ollama (local)",
        baseUrl = "http://localhost:11434/v1/",
        capabilities = CHAT_ONLY,
        supportsDynamicModels = true,
        apiKeyUrl = null,
    )

    /** All built-in presets in display order. The custom option is added by the UI on top of these. */
    val presets: List<ProviderPreset> = listOf(
        OPENAI, GROQ, OPENROUTER, TOGETHER, DEEPINFRA, MISTRAL, SONIOX, XAI, DEEPSEEK, OLLAMA,
    )

    fun byId(id: String): ProviderPreset? = presets.firstOrNull { it.id == id }

    /** Builds a preset for a user-defined OpenAI-compatible endpoint. */
    fun custom(
        baseUrl: String,
        displayName: String = "Custom server",
        capabilities: ProviderCapabilities = CHAT_AND_STT,
    ): ProviderPreset = ProviderPreset(
        id = "custom",
        displayName = displayName,
        baseUrl = baseUrl,
        capabilities = capabilities,
        supportsDynamicModels = true,
        isCustom = true,
    )
}
