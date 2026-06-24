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
 * The subset of the phone's Dictate settings the watch needs to transcribe.
 *
 * For the default tethered flow the watch only needs the non-secret fields (it forwards audio to the
 * phone). For the opt-in standalone flow it additionally needs [baseUrl]/[apiKey]/[model] so it can
 * call the provider itself. [apiKey] is only populated by the phone when the user has enabled
 * standalone, and is stored encrypted on the watch.
 */
@Serializable
data class DictateSyncedSettings(
    val transcriptionProviderId: String = "openai",
    val transcriptionApi: TranscriptionApi = TranscriptionApi.OPENAI_MULTIPART,
    val baseUrl: String = "",
    val model: String = "",
    /** Only set when the user opted into standalone transcription on the watch; empty otherwise. */
    val apiKey: String = "",
    /** ISO language code, or null to let the provider auto-detect. */
    val language: String? = null,
    /** Style/punctuation prompt biasing recognition, or null. */
    val stylePrompt: String? = null,
    /** Whether the user enabled standalone (watch-direct) transcription. */
    val standaloneEnabled: Boolean = false,
) {
    fun encode(): String = DictateWearProtocol.json.encodeToString(this)

    companion object {
        fun decode(raw: String?): DictateSyncedSettings? =
            raw?.let { runCatching { DictateWearProtocol.json.decodeFromString<DictateSyncedSettings>(it) }.getOrNull() }
    }
}
