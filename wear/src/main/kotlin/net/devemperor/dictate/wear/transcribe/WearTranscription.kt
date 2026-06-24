/*
 * Copyright (C) 2026 The Dictate Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */

package net.devemperor.dictate.wear.transcribe

import android.content.Context
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
 * Turns a recorded `.wav` into text using whichever path is configured (#106):
 *
 *  - **Standalone** (opt-in): the watch calls the provider directly with the synced API key. Used when
 *    the user enabled it and a key is present.
 *  - **Tethered** (default): the watch streams the audio to the paired phone, which transcribes with
 *    its own credentials and sends the transcript back. Used otherwise; requires a reachable phone.
 */
object WearTranscription {

    /** Thrown when neither path is currently usable (no phone reachable and standalone is off). */
    class Unavailable(message: String) : Exception(message)

    suspend fun transcribe(context: Context, audio: File): String {
        val settings = WearSettingsStore.current()
        return if (settings.standaloneEnabled && settings.apiKey.isNotBlank()) {
            standalone(settings, audio)
        } else {
            tether(context, audio)
        }
    }

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
    private suspend fun tether(context: Context, audio: File): String {
        val nodeId = WearSyncClient.findPhoneNodeId(context)
            ?: throw Unavailable("No paired phone with Dictate is reachable")

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
                return withTimeout(TRANSCRIBE_TIMEOUT_MS) { result.await() }.trim()
            } finally {
                channelClient.close(channel)
            }
        } finally {
            messageClient.removeListener(listener)
        }
    }

    private const val TRANSCRIBE_TIMEOUT_MS = 120_000L
}
