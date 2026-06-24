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
 * On-watch settings & status (#106): microphone permission, the connection/sync state, and the
 * standalone opt-in. Provider/model and most options are synced read-only from the phone.
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
        lifecycleScope.launch {
            runCatching { WearSyncClient.requestSettingsSync(this@WearSettingsActivity) }
        }

        setContent {
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
                onSetStandalone = { enabled ->
                    lifecycleScope.launch {
                        runCatching { WearSyncClient.setStandalone(this@WearSettingsActivity, enabled) }
                    }
                },
                onResync = {
                    lifecycleScope.launch {
                        runCatching { WearSyncClient.requestSettingsSync(this@WearSettingsActivity) }
                        phoneConnected = runCatching {
                            WearSyncClient.findPhoneNodeId(this@WearSettingsActivity) != null
                        }.getOrDefault(false)
                    }
                },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refreshMic()
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
    onSetStandalone: (Boolean) -> Unit,
    onResync: () -> Unit,
) {
    val standalone = synced.standaloneEnabled
    val listState = rememberScalingLazyListState()

    MaterialTheme {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxWidth(),
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item { ListHeader { Text("Dictate", fontWeight = FontWeight.SemiBold) } }

            // --- Status card: connection + active provider/model + mode ---
            item {
                StatusCard(phoneConnected = phoneConnected, synced = synced, standalone = standalone)
            }

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

            // --- Standalone opt-in ---
            item {
                Chip(
                    onClick = { onSetStandalone(!standalone) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.secondaryChipColors(),
                    label = { Text(if (standalone) "On-watch: ON" else "On-watch: OFF") },
                    secondaryLabel = {
                        Text(if (standalone) "Watch calls provider" else "Phone transcribes")
                    },
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
}

@Composable
private fun StatusCard(
    phoneConnected: Boolean?,
    synced: DictateSyncedSettings,
    standalone: Boolean,
) {
    val connectionText = when (phoneConnected) {
        null -> "Checking phone…"
        true -> "Phone connected"
        false -> "Phone not reachable"
    }
    val mode = when {
        standalone -> "Mode: on-watch"
        phoneConnected == false -> "Mode: tether (no phone!)"
        else -> "Mode: tether"
    }
    val provider = synced.transcriptionProviderId.ifBlank { "—" } +
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
        Text(text = mode, style = MaterialTheme.typography.caption2, textAlign = TextAlign.Center)
        Text(
            text = provider,
            style = MaterialTheme.typography.caption1,
            textAlign = TextAlign.Center,
        )
    }
}
