/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate.data.prefs

/**
 * Immutable snapshot of the legacy Dictate settings (see `docs/COMPATIBILITY.md`).
 * Produced by [DictateLegacyPreferences.readSnapshot] and consumed once by the new settings layer.
 *
 * Legacy provider index: 0 = OpenAI, 1 = Groq, 2 = Custom.
 */
data class DictateLegacySettings(
    val userId: String?,
    val onboardingComplete: Boolean,

    val rewordingEnabled: Boolean,
    val autoFormattingEnabled: Boolean,

    val inputLanguages: Set<String>,
    val inputLanguagePos: Int,
    val overlayCharacters: String,
    val autoEnter: Boolean,
    val resendButton: Boolean,
    val instantRecording: Boolean,
    val instantOutput: Boolean,
    val outputSpeed: Int,
    val audioFocus: Boolean,
    val useBluetoothMic: Boolean,
    val vibration: Boolean,

    val theme: String,
    val accentColor: Int,
    val animations: Boolean,
    val appLanguage: String,

    val transcriptionProvider: Int,
    val rewordingProvider: Int,
    val legacyApiKey: String?,
    val transcriptionApiKeyOpenai: String?,
    val transcriptionApiKeyGroq: String?,
    val transcriptionApiKeyCustom: String?,
    val rewordingApiKeyOpenai: String?,
    val rewordingApiKeyGroq: String?,
    val rewordingApiKeyCustom: String?,
    val transcriptionOpenaiModel: String?,
    val transcriptionGroqModel: String?,
    val transcriptionCustomHost: String?,
    val transcriptionCustomModel: String?,
    val rewordingOpenaiModel: String?,
    val rewordingGroqModel: String?,
    val rewordingCustomHost: String?,
    val rewordingCustomModel: String?,

    val proxyEnabled: Boolean,
    val proxyHost: String?,

    val stylePromptSelection: Int,
    val stylePromptCustomText: String?,
    val systemPromptSelection: Int,
    val systemPromptCustomText: String?,

    val lastVersionCode: Int,
    val hasRatedInPlaystore: Boolean,
    val hasDonated: Boolean,
) {
    /** Effective transcription API key for the active provider, with legacy global key fallback. */
    fun effectiveTranscriptionApiKey(): String? = when (transcriptionProvider) {
        1 -> transcriptionApiKeyGroq
        2 -> transcriptionApiKeyCustom
        else -> transcriptionApiKeyOpenai
    }?.takeUnless { it.isNullOrEmpty() } ?: legacyApiKey

    /** Effective rewording API key for the active provider, with legacy global key fallback. */
    fun effectiveRewordingApiKey(): String? = when (rewordingProvider) {
        1 -> rewordingApiKeyGroq
        2 -> rewordingApiKeyCustom
        else -> rewordingApiKeyOpenai
    }?.takeUnless { it.isNullOrEmpty() } ?: legacyApiKey
}
