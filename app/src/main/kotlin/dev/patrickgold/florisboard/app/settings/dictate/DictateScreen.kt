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

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.ModelTraining
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.app.FlorisPreferenceModel
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.PreferenceData
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.datastore.ui.ListPreference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import dev.patrickgold.jetpref.datastore.ui.PreferenceUiScope
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.listPrefEntries
import dev.patrickgold.jetpref.material.ui.JetPrefAlertDialog
import kotlinx.coroutines.launch

// TODO: move all user-facing strings to strings.xml when the Dictate resource set is localized.
@Composable
fun DictateScreen() = FlorisScreen {
    title = "Dictate"
    previewFieldVisible = false
    iconSpaceReserved = true

    val prefs by FlorisPreferenceStore

    content {
        val providerId by prefs.dictate.transcriptionProviderId.collectAsState()

        ListPreference(
            prefs.dictate.transcriptionProviderId,
            icon = Icons.Default.Mic,
            title = "Transcription provider",
            entries = listPrefEntries {
                entry(key = "openai", label = "OpenAI")
                entry(key = "groq", label = "Groq")
                entry(key = "custom", label = "Custom (OpenAI-compatible)")
            },
        )

        TextInputPreference(
            pref = prefs.dictate.apiKey,
            icon = Icons.Default.Key,
            title = "API key",
            placeholder = "sk-…",
            isSecret = true,
            summaryProvider = { if (it.isBlank()) "Not set – tap to configure" else "••••••" + it.takeLast(4) },
        )

        TextInputPreference(
            pref = prefs.dictate.transcriptionModel,
            icon = Icons.Default.ModelTraining,
            title = "Transcription model",
            placeholder = "leave empty for provider default",
            summaryProvider = { it.ifBlank { "Provider default" } },
        )

        if (providerId == "custom") {
            PreferenceGroup(title = "Custom server") {
                TextInputPreference(
                    pref = prefs.dictate.customBaseUrl,
                    icon = Icons.Default.Dns,
                    title = "Base URL",
                    placeholder = "https://your-server/v1/",
                    summaryProvider = { it.ifBlank { "Required for the custom provider" } },
                )
            }
        }
    }
}

/**
 * A [Preference] row that edits a string preference through a single-line text dialog. Used for the
 * API key, model and custom base URL. Secret fields are masked while typing.
 */
@Composable
private fun PreferenceUiScope<FlorisPreferenceModel>.TextInputPreference(
    pref: PreferenceData<String>,
    title: String,
    icon: ImageVector? = null,
    placeholder: String = "",
    isSecret: Boolean = false,
    summaryProvider: (String) -> String = { it.ifBlank { "Not set" } },
) {
    val value by pref.collectAsState()
    val scope = rememberCoroutineScope()
    var dialogOpen by remember { mutableStateOf(false) }

    Preference(
        icon = icon,
        title = title,
        summary = summaryProvider(value),
        onClick = { dialogOpen = true },
    )

    if (dialogOpen) {
        var text by remember(value) { mutableStateOf(value) }
        JetPrefAlertDialog(
            title = title,
            confirmLabel = "OK",
            dismissLabel = "Cancel",
            onConfirm = {
                scope.launch { pref.set(text.trim()) }
                dialogOpen = false
            },
            onDismiss = { dialogOpen = false },
        ) {
            OutlinedTextField(
                modifier = Modifier.padding(top = 4.dp),
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text(placeholder) },
                visualTransformation = if (isSecret) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (isSecret) KeyboardType.Password else KeyboardType.Uri,
                ),
            )
        }
    }
}
