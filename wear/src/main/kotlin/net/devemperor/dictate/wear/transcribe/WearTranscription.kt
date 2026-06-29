/*
 * Copyright (C) 2026 The Dictate Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */

package net.devemperor.dictate.wear.transcribe

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import dev.patrickgold.florisboard.dictate.provider.OpenAiCompatibleClient
import dev.patrickgold.florisboard.dictate.provider.ProviderConfig
import dev.patrickgold.florisboard.dictate.provider.TranscriptionRequest
import dev.patrickgold.florisboard.dictate.sync.DictateSyncedSettings
import dev.patrickgold.florisboard.dictate.sync.DictateWearProtocol
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import net.devemperor.dictate.wear.sync.WearSettingsStore
import net.devemperor.dictate.wear.sync.WearSyncClient
import java.io.File

/**
 * Turns a recorded `.wav` into text, choosing the transport automatically (#106) so the watch works
 * as independently as possible:
 *
 *  - When a paired phone running Dictate is **reachable**, the watch **tethers**: it streams the audio
 *    to the phone, which transcribes with its own connection/credentials and sends the transcript back.
 *    This is preferred because watches are often BT-only and reach the internet through the phone.
 *  - When the phone is **out of range** (or the tether attempt fails), the watch falls back to a
 *    **standalone** call straight to the provider, using the synced key — as long as one was synced and
 *    the watch has its own internet (Wi-Fi/LTE).
 */
object WearTranscription {

    /** Thrown when no transport is currently usable (no phone reachable and no synced key to go solo). */
    class Unavailable(message: String) : Exception(message)

    suspend fun transcribe(context: Context, audio: File): String {
        val settings = WearSettingsStore.current()
        val phoneNode = WearSyncClient.findPhoneNodeId(context)
        Log.i(TAG, "transcribe: phoneNode=${phoneNode != null}, canStandalone=${settings.canStandalone}, " +
            "provider=${settings.transcriptionProviderId}, model=${settings.model}, bytes=${audio.length()}")

        if (phoneNode != null) {
            // Phone in range: tether through it. Fall back to a direct call only if tethering fails and
            // we actually have a usable key (e.g. phone reachable over BT but has no internet itself).
            return try {
                tether(context, phoneNode, audio)
            } catch (e: Exception) {
                Log.w(TAG, "tether failed (${e.message}); standalone=${settings.canStandalone}", e)
                if (settings.canStandalone) standalone(settings, audio) else throw e
            }
        }

        // No phone: go solo if we can, otherwise tell the user why nothing happened.
        if (settings.canStandalone) return standalone(settings, audio)
        throw Unavailable("No phone in range and no on-watch key — open Dictate settings to sync.")
    }

    private const val TAG = "WearTranscription"

    /** Direct provider call from the watch using the synced config + key. */
    private suspend fun standalone(settings: DictateSyncedSettings, audio: File): String {
        val client = OpenAiCompatibleClient(
            ProviderConfig(
                baseUrl = settings.baseUrl,
                apiKey = settings.apiKey,
                transcriptionApi = settings.transcriptionApi,
            )
        )
        return client.transcribe(
            TranscriptionRequest(
                audioFile = audio,
                model = settings.model,
                language = settings.language,
                prompt = settings.stylePrompt,
            )
        ).text.trim()
    }

    /** Stream the audio to the phone and await its transcript over the Data Layer. */
    private suspend fun tether(context: Context, nodeId: String, audio: File): String {
        val messageClient = Wearable.getMessageClient(context)
        val result = CompletableDeferred<String>()
        val listener = MessageClient.OnMessageReceivedListener { event ->
            if (event.path == DictateWearProtocol.PATH_TRANSCRIBE_RESPONSE) {
                result.complete(String(event.data, Charsets.UTF_8))
            }
        }
        messageClient.addListener(listener).await()
        try {
            val channelClient = Wearable.getChannelClient(context)
            val channel = channelClient.openChannel(nodeId, DictateWearProtocol.PATH_TRANSCRIBE_REQUEST).await()
            try {
                val output = channelClient.getOutputStream(channel).await()
                output.use { os -> audio.inputStream().use { it.copyTo(os) } }
                Log.i(TAG, "tether: audio sent to $nodeId, awaiting response…")
                val text = withTimeout(TRANSCRIBE_TIMEOUT_MS) { result.await() }
                Log.i(TAG, "tether: response received, len=${text.length}")
                return text.trim()
            } finally {
                channelClient.close(channel)
            }
        } finally {
            messageClient.removeListener(listener)
        }
    }

    private const val TRANSCRIBE_TIMEOUT_MS = 120_000L
}
