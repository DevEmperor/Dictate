/*
 * Copyright (C) 2026 The Dictate Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */

package net.devemperor.dictate.wear.sync

import android.content.Context
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import dev.patrickgold.florisboard.dictate.sync.DictateWearProtocol
import kotlinx.coroutines.tasks.await

/**
 * Thin helper around the Data Layer clients for the watch -> phone direction (#106): finding the
 * paired phone that runs Dictate and asking it to (re)publish the current settings.
 */
object WearSyncClient {

    /**
     * The id of a connected node that advertises the phone Dictate capability, or null if no paired
     * phone with the app is currently reachable (-> the watch should fall back to its cached settings
     * and, for transcription, to standalone if enabled).
     */
    suspend fun findPhoneNodeId(context: Context): String? {
        val info = Wearable.getCapabilityClient(context)
            .getCapability(DictateWearProtocol.CAPABILITY_PHONE_APP, CapabilityClient.FILTER_REACHABLE)
            .await()
        // Prefer a directly-connected (nearby) node when several are returned.
        return info.nodes.firstOrNull { it.isNearby }?.id ?: info.nodes.firstOrNull()?.id
    }

    /** Ask the phone to push a fresh settings snapshot. No-op if no phone node is reachable. */
    suspend fun requestSettingsSync(context: Context) {
        val nodeId = findPhoneNodeId(context) ?: return
        Wearable.getMessageClient(context)
            .sendMessage(nodeId, DictateWearProtocol.PATH_SYNC_REQUEST, ByteArray(0))
            .await()
    }
}
