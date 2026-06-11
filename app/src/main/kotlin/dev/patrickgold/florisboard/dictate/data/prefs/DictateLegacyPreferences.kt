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

import android.content.Context
import android.content.SharedPreferences

/**
 * Reads the SharedPreferences written by the original Dictate app (≤ 3.2.0).
 *
 * This is the executable form of the data contract in `docs/COMPATIBILITY.md`. Because the new app
 * keeps `applicationId = net.devemperor.dictate`, the old prefs file survives the in-place update and
 * can be read here to seed the new settings layer exactly once (see [isPresent] / [readSnapshot]).
 *
 * This class only READS. The one-time import into the new (JetPref) settings store happens in the
 * settings layer (roadmap step 6) and should set a "migrated" flag in the NEW store afterwards.
 */
class DictateLegacyPreferences(context: Context) {

    private val sp: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** True if the legacy prefs file contains any data (i.e. this is an upgrading user). */
    fun isPresent(): Boolean = sp.all.isNotEmpty()

    fun readSnapshot(): DictateLegacySettings = DictateLegacySettings(
        userId = sp.getString(K_USER_ID, null),
        onboardingComplete = sp.getBoolean(K_ONBOARDING_COMPLETE, false),

        rewordingEnabled = sp.getBoolean(K_REWORDING_ENABLED, true),
        autoFormattingEnabled = sp.getBoolean(K_AUTO_FORMATTING_ENABLED, false),

        inputLanguages = sp.getStringSet(K_INPUT_LANGUAGES, DEFAULT_INPUT_LANGUAGES) ?: DEFAULT_INPUT_LANGUAGES,
        inputLanguagePos = sp.getInt(K_INPUT_LANGUAGE_POS, 0),
        overlayCharacters = sp.getString(K_OVERLAY_CHARACTERS, DEFAULT_OVERLAY_CHARACTERS) ?: DEFAULT_OVERLAY_CHARACTERS,
        autoEnter = sp.getBoolean(K_AUTO_ENTER, false),
        resendButton = sp.getBoolean(K_RESEND_BUTTON, false),
        instantRecording = sp.getBoolean(K_INSTANT_RECORDING, false),
        instantOutput = sp.getBoolean(K_INSTANT_OUTPUT, true),
        outputSpeed = sp.getInt(K_OUTPUT_SPEED, 5),
        audioFocus = sp.getBoolean(K_AUDIO_FOCUS, true),
        useBluetoothMic = sp.getBoolean(K_USE_BLUETOOTH_MIC, false),
        vibration = sp.getBoolean(K_VIBRATION, true),

        theme = sp.getString(K_THEME, "system") ?: "system",
        accentColor = sp.getInt(K_ACCENT_COLOR, DEFAULT_ACCENT_COLOR),
        animations = sp.getBoolean(K_ANIMATIONS, true),
        appLanguage = sp.getString(K_APP_LANGUAGE, "system") ?: "system",

        transcriptionProvider = sp.getInt(K_TRANSCRIPTION_PROVIDER, 0),
        rewordingProvider = sp.getInt(K_REWORDING_PROVIDER, 0),
        legacyApiKey = sp.getString(K_API_KEY, null),
        transcriptionApiKeyOpenai = sp.getString(K_TRANSCRIPTION_API_KEY_OPENAI, null),
        transcriptionApiKeyGroq = sp.getString(K_TRANSCRIPTION_API_KEY_GROQ, null),
        transcriptionApiKeyCustom = sp.getString(K_TRANSCRIPTION_API_KEY_CUSTOM, null),
        rewordingApiKeyOpenai = sp.getString(K_REWORDING_API_KEY_OPENAI, null),
        rewordingApiKeyGroq = sp.getString(K_REWORDING_API_KEY_GROQ, null),
        rewordingApiKeyCustom = sp.getString(K_REWORDING_API_KEY_CUSTOM, null),
        transcriptionOpenaiModel = sp.getString(K_TRANSCRIPTION_OPENAI_MODEL, null),
        transcriptionGroqModel = sp.getString(K_TRANSCRIPTION_GROQ_MODEL, null),
        transcriptionCustomHost = sp.getString(K_TRANSCRIPTION_CUSTOM_HOST, null),
        transcriptionCustomModel = sp.getString(K_TRANSCRIPTION_CUSTOM_MODEL, null),
        rewordingOpenaiModel = sp.getString(K_REWORDING_OPENAI_MODEL, null),
        rewordingGroqModel = sp.getString(K_REWORDING_GROQ_MODEL, null),
        rewordingCustomHost = sp.getString(K_REWORDING_CUSTOM_HOST, null),
        rewordingCustomModel = sp.getString(K_REWORDING_CUSTOM_MODEL, null),

        proxyEnabled = sp.getBoolean(K_PROXY_ENABLED, false),
        proxyHost = sp.getString(K_PROXY_HOST, null),

        stylePromptSelection = sp.getInt(K_STYLE_PROMPT_SELECTION, 1),
        stylePromptCustomText = sp.getString(K_STYLE_PROMPT_CUSTOM_TEXT, null),
        systemPromptSelection = sp.getInt(K_SYSTEM_PROMPT_SELECTION, 1),
        systemPromptCustomText = sp.getString(K_SYSTEM_PROMPT_CUSTOM_TEXT, null),

        lastVersionCode = sp.getInt(K_LAST_VERSION_CODE, 0),
        hasRatedInPlaystore = sp.getBoolean(K_FLAG_HAS_RATED, false),
        hasDonated = sp.getBoolean(K_FLAG_HAS_DONATED, false),
    )

    companion object {
        const val PREFS_NAME = "net.devemperor.dictate"
        private const val P = "net.devemperor.dictate."

        const val DEFAULT_OVERLAY_CHARACTERS = "()-:!?,."
        const val DEFAULT_ACCENT_COLOR = -14700810
        val DEFAULT_INPUT_LANGUAGES: Set<String> = setOf("detect", "en")

        private const val K_USER_ID = P + "user_id"
        private const val K_ONBOARDING_COMPLETE = P + "onboarding_complete"
        private const val K_REWORDING_ENABLED = P + "rewording_enabled"
        private const val K_AUTO_FORMATTING_ENABLED = P + "auto_formatting_enabled"
        private const val K_INPUT_LANGUAGES = P + "input_languages"
        private const val K_INPUT_LANGUAGE_POS = P + "input_language_pos"
        private const val K_OVERLAY_CHARACTERS = P + "overlay_characters"
        private const val K_AUTO_ENTER = P + "auto_enter"
        private const val K_RESEND_BUTTON = P + "resend_button"
        private const val K_INSTANT_RECORDING = P + "instant_recording"
        private const val K_INSTANT_OUTPUT = P + "instant_output"
        private const val K_OUTPUT_SPEED = P + "output_speed"
        private const val K_AUDIO_FOCUS = P + "audio_focus"
        private const val K_USE_BLUETOOTH_MIC = P + "use_bluetooth_mic"
        private const val K_VIBRATION = P + "vibration"
        private const val K_THEME = P + "theme"
        private const val K_ACCENT_COLOR = P + "accent_color"
        private const val K_ANIMATIONS = P + "animations"
        private const val K_APP_LANGUAGE = P + "app_language"
        private const val K_TRANSCRIPTION_PROVIDER = P + "transcription_provider"
        private const val K_REWORDING_PROVIDER = P + "rewording_provider"
        private const val K_API_KEY = P + "api_key"
        private const val K_TRANSCRIPTION_API_KEY_OPENAI = P + "transcription_api_key_openai"
        private const val K_TRANSCRIPTION_API_KEY_GROQ = P + "transcription_api_key_groq"
        private const val K_TRANSCRIPTION_API_KEY_CUSTOM = P + "transcription_api_key_custom"
        private const val K_REWORDING_API_KEY_OPENAI = P + "rewording_api_key_openai"
        private const val K_REWORDING_API_KEY_GROQ = P + "rewording_api_key_groq"
        private const val K_REWORDING_API_KEY_CUSTOM = P + "rewording_api_key_custom"
        private const val K_TRANSCRIPTION_OPENAI_MODEL = P + "transcription_openai_model"
        private const val K_TRANSCRIPTION_GROQ_MODEL = P + "transcription_groq_model"
        private const val K_TRANSCRIPTION_CUSTOM_HOST = P + "transcription_custom_host"
        private const val K_TRANSCRIPTION_CUSTOM_MODEL = P + "transcription_custom_model"
        private const val K_REWORDING_OPENAI_MODEL = P + "rewording_openai_model"
        private const val K_REWORDING_GROQ_MODEL = P + "rewording_groq_model"
        private const val K_REWORDING_CUSTOM_HOST = P + "rewording_custom_host"
        private const val K_REWORDING_CUSTOM_MODEL = P + "rewording_custom_model"
        private const val K_PROXY_ENABLED = P + "proxy_enabled"
        private const val K_PROXY_HOST = P + "proxy_host"
        private const val K_STYLE_PROMPT_SELECTION = P + "style_prompt_selection"
        private const val K_STYLE_PROMPT_CUSTOM_TEXT = P + "style_prompt_custom_text"
        private const val K_SYSTEM_PROMPT_SELECTION = P + "system_prompt_selection"
        private const val K_SYSTEM_PROMPT_CUSTOM_TEXT = P + "system_prompt_custom_text"
        private const val K_LAST_VERSION_CODE = P + "last_version_code"
        private const val K_FLAG_HAS_RATED = P + "flag_has_rated_in_playstore"
        private const val K_FLAG_HAS_DONATED = P + "flag_has_donated"
    }
}
