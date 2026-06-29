/*
 * Copyright (C) 2026 The Dictate Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */

package dev.patrickgold.florisboard.dictate.sync

import dev.patrickgold.florisboard.dictate.provider.TranscriptionApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The contract shared by the phone app and the Wear OS app for the Wearable Data Layer (#106).
 *
 * Lives in `:lib:dictate-core` so both sides compile against the exact same paths and the exact same
 * serialized settings schema — the single biggest source of "works on phone, silently no-ops on watch"
 * bugs in Data Layer code is the two ends disagreeing on a path string or a JSON field.
 */
object DictateWearProtocol {
    /** DataClient item path: the phone publishes the current settings snapshot here. */
    const val PATH_SETTINGS = "/dictate/settings"

    /** MessageClient path: the watch asks the phone to (re)publish [PATH_SETTINGS] right now. */
    const val PATH_SYNC_REQUEST = "/dictate/sync/request"

    /** MessageClient/ChannelClient path: the watch sends recorded audio for the phone to transcribe. */
    const val PATH_TRANSCRIBE_REQUEST = "/dictate/transcribe/request"

    /** MessageClient path: the phone returns the transcript (or an error) for a request. */
    const val PATH_TRANSCRIBE_RESPONSE = "/dictate/transcribe/response"

    /**
     * MessageClient path: the watch toggles standalone transcription on/off. Payload is a single byte
     * (1 = on, 0 = off). The phone stores it and re-publishes [PATH_SETTINGS] (the key is only included
     * in the snapshot while standalone is on).
     */
    const val PATH_SET_STANDALONE = "/dictate/set_standalone"

    /** CapabilityClient capability the phone app advertises so the watch can detect a usable peer. */
    const val CAPABILITY_PHONE_APP = "dictate_phone_transcriber"

    /** DataMap key under [PATH_SETTINGS] holding the JSON of [DictateSyncedSettings]. */
    const val KEY_SETTINGS_JSON = "settings_json"

    /** Lenient JSON used on both ends; unknown keys are ignored so the two apps can version independently. */
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
}

/**
 * The phone's Dictate settings the watch needs to mirror the phone experience (#106).
 *
 * The watch picks its transport at dictation time (see `WearTranscription`): it tethers through the
 * phone when one is reachable, and otherwise falls back to calling the provider itself. So it needs
 * both the non-secret config (provider/model/language/prompt) and, for the offline fallback,
 * [baseUrl]/[apiKey]. The key is only populated when the phone-side master toggle allows it
 * (`wearStandaloneEnabled`, default on); it is stored private to the watch app.
 *
 * [accentColorArgb] carries the phone's accent color so the watch UI matches the phone's look.
 */
@Serializable
data class DictateSyncedSettings(
    val transcriptionProviderId: String = "openai",
    /** Human-readable provider name for the watch UI (e.g. "Groq", "xAI (Grok)"). */
    val providerLabel: String = "",
    val transcriptionApi: TranscriptionApi = TranscriptionApi.OPENAI_MULTIPART,
    val baseUrl: String = "",
    val model: String = "",
    /** Populated unless the phone master toggle forbids syncing the key; empty -> watch can only tether. */
    val apiKey: String = "",
    /** ISO language code, or null to let the provider auto-detect. */
    val language: String? = null,
    /** Style/punctuation prompt biasing recognition (already includes the custom-words glossary), or null. */
    val stylePrompt: String? = null,
    /** Phone accent color as a packed ARGB int, so the watch UI themes itself like the phone. */
    val accentColorArgb: Int = DEFAULT_ACCENT_ARGB,
) {
    /** True when the watch can transcribe on its own (a key is present), i.e. works without the phone. */
    val canStandalone: Boolean get() = apiKey.isNotBlank() && baseUrl.isNotBlank()

    fun encode(): String = DictateWearProtocol.json.encodeToString(this)

    companion object {
        /** Dictate light blue — the app's default accent, used until a real sync arrives. */
        const val DEFAULT_ACCENT_ARGB: Int = 0xFF30B7E6.toInt()

        fun decode(raw: String?): DictateSyncedSettings? =
            raw?.let { runCatching { DictateWearProtocol.json.decodeFromString<DictateSyncedSettings>(it) }.getOrNull() }
    }
}
