/*
 * Copyright (C) 2026 The Dictate Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */

package dev.patrickgold.florisboard.dictate.wear

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.dictate.sync.DictateWearProtocol

/**
 * Phone-side endpoint of the Wear OS Data Layer (#106).
 *
 * Handles requests coming from the watch:
 *  - [DictateWearProtocol.PATH_SYNC_REQUEST]: publish a fresh settings snapshot the watch can cache.
 *
 * The transcription relay ([DictateWearProtocol.PATH_TRANSCRIBE_REQUEST]) is added in P2b together with
 * the watch-side audio capture; until then the watch falls back to standalone (when the user opts in).
 *
 * The phone advertises the [DictateWearProtocol.CAPABILITY_PHONE_APP] capability (see
 * res/values/wear.xml) so the watch's CapabilityClient can discover it.
 */
class DictateWearService : WearableListenerService() {

    private val prefs by FlorisPreferenceStore

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            DictateWearProtocol.PATH_SYNC_REQUEST -> publishSettings()
        }
    }

    /** Serialize the active transcription settings and put them on the Data Layer for the watch. */
    private fun publishSettings() {
        val settings = PhoneWearSettingsResolver.resolve(prefs)
        val request = PutDataMapRequest.create(DictateWearProtocol.PATH_SETTINGS).run {
            dataMap.putString(DictateWearProtocol.KEY_SETTINGS_JSON, settings.encode())
            // Vary a field every push so an identical settings payload still produces a DATA_CHANGED
            // event on the watch when it explicitly re-requests a sync.
            dataMap.putLong("published_at", System.currentTimeMillis())
            asPutDataRequest().setUrgent()
        }
        Wearable.getDataClient(this).putDataItem(request)
    }
}
