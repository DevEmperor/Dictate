/*
 * Copyright (C) 2026 The Dictate Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */

package dev.patrickgold.florisboard.dictate.wear

import android.util.Log
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.dictate.sync.DictateWearProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File

/**
 * Phone-side endpoint of the Wear OS Data Layer (#106).
 *
 * Handles requests coming from the watch:
 *  - [DictateWearProtocol.PATH_SYNC_REQUEST]: publish a fresh settings snapshot the watch can cache.
 *  - [DictateWearProtocol.PATH_SET_STANDALONE]: store the standalone opt-in and re-publish settings
 *    (the API key is only included while standalone is on).
 *  - [DictateWearProtocol.PATH_TRANSCRIBE_REQUEST] (ChannelClient): receive recorded audio, transcribe
 *    it with the phone's active provider and send the transcript back.
 *
 * The phone advertises the [DictateWearProtocol.CAPABILITY_PHONE_APP] capability (res/values/wear.xml)
 * so the watch's CapabilityClient can discover it.
 */
class DictateWearService : WearableListenerService() {

    private val prefs by FlorisPreferenceStore
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            DictateWearProtocol.PATH_SYNC_REQUEST -> scope.launch { publishSettings() }
            DictateWearProtocol.PATH_SET_STANDALONE -> {
                val enabled = event.data.firstOrNull() == 1.toByte()
                scope.launch {
                    prefs.dictate.wearStandaloneEnabled.set(enabled)
                    publishSettings()
                }
            }
            DictateWearProtocol.PATH_SET_AUTO_REWORDING -> {
                val enabled = event.data.firstOrNull() == 1.toByte()
                scope.launch {
                    prefs.dictate.wearAutoRewordingEnabled.set(enabled)
                    publishSettings()
                }
            }
        }
    }

    override fun onChannelOpened(channel: ChannelClient.Channel) {
        if (channel.path != DictateWearProtocol.PATH_TRANSCRIBE_REQUEST) return
        scope.launch { handleTranscribeChannel(channel) }
    }

    private suspend fun handleTranscribeChannel(channel: ChannelClient.Channel) {
        val channelClient = Wearable.getChannelClient(applicationContext)
        val audio = File(cacheDir, "wear_tether_${channel.nodeId}.wav")
        var transcript = ""
        try {
            // Drain the watch's audio into a temp file.
            channelClient.getInputStream(channel).await().use { input ->
                audio.outputStream().use { input.copyTo(it) }
            }
            Log.i(TAG, "tether: received ${audio.length()} bytes from ${channel.nodeId}, transcribing…")
            transcript = try {
                PhoneTranscriber.transcribe(applicationContext, prefs, audio)
            } catch (e: Exception) {
                Log.e(TAG, "tether: phone transcription failed", e)
                ""
            }
            Log.i(TAG, "tether: transcript length=${transcript.length}")
        } finally {
            audio.delete()
            channelClient.close(channel)
            // Always answer, even on failure (empty transcript -> the watch surfaces an error) so the
            // watch never hangs waiting for a reply.
            Wearable.getMessageClient(applicationContext).sendMessage(
                channel.nodeId,
                DictateWearProtocol.PATH_TRANSCRIBE_RESPONSE,
                transcript.toByteArray(Charsets.UTF_8),
            )
        }
    }

    /** Serialize the active transcription settings and put them on the Data Layer for the watch. */
    private suspend fun publishSettings() {
        DictateWearPublisher.publish(applicationContext)
    }

    private companion object {
        const val TAG = "DictateWear"
    }
}
