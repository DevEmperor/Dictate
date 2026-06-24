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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import dev.patrickgold.florisboard.dictate.sync.DictateSyncedSettings
import net.devemperor.dictate.wear.sync.WearSettingsStore
import net.devemperor.dictate.wear.sync.WearSyncClient
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

/**
 * On-watch settings: microphone permission and the standalone opt-in. Everything else
 * (provider, model, language, prompts) is synced read-only from the phone via the Data Layer
 * (P2 of the Wear roadmap); this screen only surfaces what must be decided on the watch itself.
 */
class WearSettingsActivity : ComponentActivity() {

    private var micGranted by mutableStateOf(false)

    private val requestMic = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micGranted = granted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        micGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

        // Load the last-known synced settings, then ask the phone for a fresh snapshot.
        WearSettingsStore.load(this)
        lifecycleScope.launch {
            runCatching { WearSyncClient.requestSettingsSync(this@WearSettingsActivity) }
        }

        setContent {
            val synced by WearSettingsStore.settings.collectAsState()
            SettingsScreen(
                micGranted = micGranted,
                synced = synced,
                onRequestMic = { requestMic.launch(Manifest.permission.RECORD_AUDIO) },
                onSetStandalone = { enabled ->
                    lifecycleScope.launch {
                        runCatching { WearSyncClient.setStandalone(this@WearSettingsActivity, enabled) }
                    }
                },
            )
        }
    }
}

@Composable
private fun SettingsScreen(
    micGranted: Boolean,
    synced: DictateSyncedSettings,
    onRequestMic: () -> Unit,
    onSetStandalone: (Boolean) -> Unit,
) {
    // The phone owns the standalone flag (it owns the key); the synced snapshot is the source of truth.
    val standalone = synced.standaloneEnabled

    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = "Dictate settings", textAlign = TextAlign.Center)
            Text(text = "Settings are synced from your phone.", textAlign = TextAlign.Center)

            val providerLine = synced.transcriptionProviderId.ifBlank { "—" } +
                (synced.model.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
            Text(text = "Provider: $providerLine", textAlign = TextAlign.Center)

            Chip(
                onClick = onRequestMic,
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.primaryChipColors(),
                label = { Text(if (micGranted) "Microphone: granted" else "Grant microphone access") },
            )

            Chip(
                onClick = { onSetStandalone(!standalone) },
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.secondaryChipColors(),
                label = { Text(if (standalone) "Transcribe on watch: ON" else "Transcribe on watch: OFF") },
                secondaryLabel = { Text(if (standalone) "Watch calls provider directly" else "Phone transcribes (recommended)") },
            )
        }
    }
}
