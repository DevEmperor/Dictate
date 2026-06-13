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

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
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
import dev.patrickgold.jetpref.datastore.model.PreferenceData
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.florisboard.dictate.data.prompts.DictatePromptDefaults
import dev.patrickgold.jetpref.datastore.ui.ListPreferenceEntry
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceUiScope
import dev.patrickgold.jetpref.datastore.ui.listPrefEntries
import dev.patrickgold.jetpref.material.ui.JetPrefAlertDialog
import org.florisboard.lib.compose.stringRes
import kotlinx.coroutines.launch

/**
 * The shared none/predefined/custom choice for the system and style prompts, keyed by the integer
 * selection values in [DictatePromptDefaults] (0/1/2) used by the rewording engine.
 */
@Composable
internal fun promptSelectionEntries(): List<ListPreferenceEntry<Int>> = listPrefEntries {
    entry(
        key = DictatePromptDefaults.SELECTION_NONE,
        label = stringRes(R.string.dictate__prompt_selection_none),
    )
    entry(
        key = DictatePromptDefaults.SELECTION_PREDEFINED,
        label = stringRes(R.string.dictate__prompt_selection_predefined),
    )
    entry(
        key = DictatePromptDefaults.SELECTION_CUSTOM,
        label = stringRes(R.string.dictate__prompt_selection_custom),
    )
}

/**
 * A [Preference] row that edits a string preference through a text dialog. Shared across the Dictate
 * settings screens (API key, model, base URL, custom prompts). Secret fields are masked while typing;
 * pass [multiline] for longer free-text values such as custom prompts.
 */
@Composable
internal fun PreferenceUiScope<FlorisPreferenceModel>.TextInputPreference(
    pref: PreferenceData<String>,
    title: String,
    icon: ImageVector? = null,
    placeholder: String = "",
    isSecret: Boolean = false,
    multiline: Boolean = false,
    notSetSummary: String = stringRes(R.string.dictate__value_not_set),
    summaryProvider: (String) -> String = { it.ifBlank { notSetSummary } },
) {
    val value by pref.collectAsState()
    val scope = rememberCoroutineScope()
    var dialogOpen by remember { mutableStateOf(false) }
    val summary = summaryProvider(value)

    Preference(
        icon = icon,
        title = title,
        summary = summary,
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
                modifier = Modifier
                    .padding(top = 4.dp)
                    .then(if (multiline) Modifier.heightIn(min = 120.dp) else Modifier),
                value = text,
                onValueChange = { text = it },
                singleLine = !multiline,
                placeholder = { Text(placeholder) },
                visualTransformation = if (isSecret) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = when {
                        isSecret -> KeyboardType.Password
                        multiline -> KeyboardType.Text
                        else -> KeyboardType.Uri
                    },
                ),
            )
        }
    }
}
