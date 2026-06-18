/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.app.settings.dictate

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.dictate.overlay.DictateAccessibilityService
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import dev.patrickgold.jetpref.datastore.ui.SwitchPreference
import org.florisboard.lib.compose.stringRes

/**
 * Settings for the floating dictation button (issue #88). The in-app master toggle
 * ([dev.patrickgold.florisboard.app.AppPrefs.Dictate.floatingButtonEnabled]) hides/shows the bubble; the
 * bubble only actually appears once the [DictateAccessibilityService] is also enabled in the system
 * accessibility settings, which the user reaches from here after a prominent disclosure of what the
 * service does. The service-enabled status is re-read on every resume so it reflects changes made in the
 * system settings.
 */
@Composable
fun DictateFloatingButtonScreen() = FlorisScreen {
    title = stringRes(R.string.dictate__floating_button_title)
    previewFieldVisible = false
    iconSpaceReserved = true

    val prefs by FlorisPreferenceStore

    content {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val enabled by prefs.dictate.floatingButtonEnabled.collectAsState()

        var serviceEnabled by remember { mutableStateOf(isOverlayServiceEnabled(context)) }
        var micGranted by remember { mutableStateOf(isMicGranted(context)) }
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    serviceEnabled = isOverlayServiceEnabled(context)
                    micGranted = isMicGranted(context)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
        val requestMic = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted -> micGranted = granted }

        var showDisclosure by remember { mutableStateOf(false) }

        SwitchPreference(
            prefs.dictate.floatingButtonEnabled,
            icon = Icons.Default.Adjust,
            title = stringRes(R.string.dictate__floating_button_enable_title),
            summary = stringRes(R.string.dictate__floating_button_enable_summary),
        )

        if (enabled) {
            PreferenceGroup(title = stringRes(R.string.dictate__floating_button_permission_group)) {
                Preference(
                    icon = Icons.Default.Accessibility,
                    title = stringRes(R.string.dictate__floating_button_service_title),
                    summary = if (serviceEnabled) {
                        stringRes(R.string.dictate__floating_button_service_enabled)
                    } else {
                        stringRes(R.string.dictate__floating_button_service_disabled)
                    },
                    onClick = {
                        if (serviceEnabled) {
                            openAccessibilitySettings(context)
                        } else {
                            showDisclosure = true
                        }
                    },
                )
                // The bubble records through the same pipeline as the keyboard, so it needs the mic
                // permission. Usually already granted via the keyboard onboarding, but surface it here in
                // case the user enabled the bubble without ever recording.
                if (!micGranted) {
                    Preference(
                        icon = Icons.Default.Mic,
                        title = stringRes(R.string.dictate__floating_button_mic_title),
                        summary = stringRes(R.string.dictate__floating_button_mic_summary),
                        onClick = { requestMic.launch(Manifest.permission.RECORD_AUDIO) },
                    )
                }
            }
        }

        if (showDisclosure) {
            AlertDialog(
                onDismissRequest = { showDisclosure = false },
                title = { Text(stringRes(R.string.dictate__floating_button_disclosure_title)) },
                text = { Text(stringRes(R.string.dictate__floating_button_disclosure_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        showDisclosure = false
                        openAccessibilitySettings(context)
                    }) {
                        Text(stringRes(R.string.dictate__floating_button_disclosure_continue))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDisclosure = false }) {
                        Text(stringRes(android.R.string.cancel))
                    }
                },
            )
        }
    }
}

/** Whether the Dictate accessibility service is currently enabled in the system accessibility settings. */
private fun isOverlayServiceEnabled(context: Context): Boolean {
    val flattened = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ) ?: return false
    // ComponentName picks up the running package (incl. the .debug suffix) automatically.
    val component = ComponentName(context, DictateAccessibilityService::class.java).flattenToString()
    return flattened.split(':').any { it.equals(component, ignoreCase = true) }
}

private fun isMicGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

private fun openAccessibilitySettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
