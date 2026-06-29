/*
 * Copyright (C) 2026 The Dictate Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */

package net.devemperor.dictate.wear.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import dev.patrickgold.florisboard.dictate.sync.DictateSyncedSettings
import kotlinx.coroutines.launch
import net.devemperor.dictate.wear.sync.WearSettingsStore
import net.devemperor.dictate.wear.sync.WearSyncClient
import androidx.lifecycle.lifecycleScope

/**
 * On-watch settings & status (#106): microphone permission, the connection/sync state, and a manual
 * re-sync. Provider/model and the rest are synced read-only from the phone; the watch transcribes
 * through the phone when it is reachable and on its own otherwise (no mode toggle needed).
 */
class WearSettingsActivity : ComponentActivity() {

    private var micGranted by mutableStateOf(false)

    private val requestMic = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micGranted = granted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshMic()

        WearSettingsStore.load(this)
        syncFromPhone()

        setContent {
            WearDictateTheme {
                val synced by WearSettingsStore.settings.collectAsState()
                var phoneConnected by remember { mutableStateOf<Boolean?>(null) }

                LaunchedEffect(Unit) {
                    phoneConnected = runCatching {
                        WearSyncClient.findPhoneNodeId(this@WearSettingsActivity) != null
                    }.getOrDefault(false)
                }

                SettingsScreen(
                    micGranted = micGranted,
                    phoneConnected = phoneConnected,
                    synced = synced,
                    onRequestMic = { requestMic.launch(Manifest.permission.RECORD_AUDIO) },
                    onResync = {
                        syncFromPhone()
                        lifecycleScope.launch {
                            phoneConnected = runCatching {
                                WearSyncClient.findPhoneNodeId(this@WearSettingsActivity) != null
                            }.getOrDefault(false)
                        }
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshMic()
        // Opening (or returning to) settings should always reconcile with the phone, as requested.
        syncFromPhone()
    }

    /**
     * Pull the phone's current settings: read the latest replicated DataItem (works even right after a
     * flaky reconnect) AND nudge the phone to (re)publish, so a change made on the phone — accent color,
     * provider, prompt — lands on the watch every time settings are opened.
     */
    private fun syncFromPhone() {
        lifecycleScope.launch {
            runCatching { WearSyncClient.requestSettingsSync(this@WearSettingsActivity) }
            runCatching { WearSyncClient.fetchPublishedSettings(this@WearSettingsActivity) }
                .getOrNull()
                ?.let { WearSettingsStore.save(this@WearSettingsActivity, it) }
        }
    }

    private fun refreshMic() {
        micGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }
}

@Composable
private fun SettingsScreen(
    micGranted: Boolean,
    phoneConnected: Boolean?,
    synced: DictateSyncedSettings,
    onRequestMic: () -> Unit,
    onResync: () -> Unit,
) {
    val listState = rememberScalingLazyListState()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxWidth(),
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { ListHeader { Text("Dictate", fontWeight = FontWeight.SemiBold) } }

        // --- Status card: connection + active provider/model + resolved transcription path ---
        item { StatusCard(phoneConnected = phoneConnected, synced = synced) }

        // --- Microphone permission ---
        item {
            Chip(
                onClick = onRequestMic,
                modifier = Modifier.fillMaxWidth(),
                colors = if (micGranted) ChipDefaults.secondaryChipColors() else ChipDefaults.primaryChipColors(),
                label = { Text(if (micGranted) "Microphone ✓" else "Grant microphone") },
                secondaryLabel = if (micGranted) null else { { Text("Required to dictate") } },
            )
        }

        // --- Manual re-sync ---
        item {
            Chip(
                onClick = onResync,
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.secondaryChipColors(),
                label = { Text("Re-sync from phone") },
            )
        }
    }
}

@Composable
private fun StatusCard(
    phoneConnected: Boolean?,
    synced: DictateSyncedSettings,
) {
    val connectionText = when (phoneConnected) {
        null -> "Checking phone…"
        true -> "Phone connected"
        false -> "Phone out of range"
    }
    // The transport the watch will actually use, mirroring WearTranscription's AUTO decision.
    val pathText = when {
        phoneConnected == true -> "Transcribes via phone"
        synced.canStandalone -> "Transcribes on watch"
        else -> "Open phone app to sync"
    }
    val provider = synced.providerLabel.ifBlank { synced.transcriptionProviderId.ifBlank { "—" } } +
        (synced.model.takeIf { it.isNotBlank() }?.let { "\n$it" } ?: "")

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = (if (phoneConnected == true) "⌚↔📱  " else "⌚  ") + connectionText,
            style = MaterialTheme.typography.caption2,
            textAlign = TextAlign.Center,
        )
        Text(
            text = pathText,
            style = MaterialTheme.typography.caption2,
            color = MaterialTheme.colors.primary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = provider,
            style = MaterialTheme.typography.caption1,
            textAlign = TextAlign.Center,
        )
    }
}
