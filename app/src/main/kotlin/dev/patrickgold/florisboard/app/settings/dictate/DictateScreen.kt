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
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceModel
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import org.florisboard.lib.compose.stringRes
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
    title = stringRes(R.string.dictate__title)
    previewFieldVisible = false
    iconSpaceReserved = true

    val prefs by FlorisPreferenceStore

    content {
        val providerId by prefs.dictate.transcriptionProviderId.collectAsState()

        ListPreference(
            prefs.dictate.transcriptionProviderId,
            icon = Icons.Default.Mic,
            title = stringRes(R.string.dictate__provider_title),
            entries = listPrefEntries {
                entry(key = "openai", label = stringRes(R.string.dictate__provider__openai))
                entry(key = "groq", label = stringRes(R.string.dictate__provider__groq))
                entry(key = "custom", label = stringRes(R.string.dictate__provider__custom))
            },
        )

        val apiKeyNotSet = stringRes(R.string.dictate__api_key_not_set)
        TextInputPreference(
            pref = prefs.dictate.apiKey,
            icon = Icons.Default.Key,
            title = stringRes(R.string.dictate__api_key_title),
            placeholder = stringRes(R.string.dictate__api_key_placeholder),
            isSecret = true,
            summaryProvider = { if (it.isBlank()) apiKeyNotSet else "••••••" + it.takeLast(4) },
        )

        val modelDefault = stringRes(R.string.dictate__model_default_summary)
        TextInputPreference(
            pref = prefs.dictate.transcriptionModel,
            icon = Icons.Default.ModelTraining,
            title = stringRes(R.string.dictate__model_title),
            placeholder = stringRes(R.string.dictate__model_placeholder),
            summaryProvider = { it.ifBlank { modelDefault } },
        )

        if (providerId == "custom") {
            val baseUrlRequired = stringRes(R.string.dictate__base_url_required)
            PreferenceGroup(title = stringRes(R.string.dictate__custom_server_title)) {
                TextInputPreference(
                    pref = prefs.dictate.customBaseUrl,
                    icon = Icons.Default.Dns,
                    title = stringRes(R.string.dictate__base_url_title),
                    placeholder = stringRes(R.string.dictate__base_url_placeholder),
                    summaryProvider = { it.ifBlank { baseUrlRequired } },
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
            confirmLabel = stringRes(R.string.action__ok),
            dismissLabel = stringRes(R.string.action__cancel),
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
