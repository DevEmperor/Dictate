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

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.ModelTraining
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.dictate.DictatePromptsLayout
import dev.patrickgold.florisboard.dictate.data.prompts.DictatePromptDefaults
import dev.patrickgold.florisboard.dictate.data.prompts.PromptsDatabaseHelper
import dev.patrickgold.florisboard.dictate.provider.ProviderRegistry
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.datastore.ui.ListPreference
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import dev.patrickgold.jetpref.datastore.ui.SwitchPreference
import dev.patrickgold.jetpref.datastore.ui.listPrefEntries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.florisboard.lib.compose.stringRes

/**
 * Rewording (AI) settings: the master toggle, the dedicated chat provider/key/model, prompt
 * management and the system/auto-formatting options. The transcription provider is configured on the
 * main Dictate screen; this screen is purely about the rewording (GPT) side (roadmap section 4, P2).
 */
@Composable
fun DictateRewordingScreen() = FlorisScreen {
    title = stringRes(R.string.dictate__rewording_title)
    previewFieldVisible = false
    iconSpaceReserved = true

    val prefs by FlorisPreferenceStore

    content {
        val navController = LocalNavController.current
        val context = LocalContext.current

        SwitchPreference(
            prefs.dictate.rewordingEnabled,
            icon = Icons.Default.AutoFixHigh,
            title = stringRes(R.string.dictate__rewording_enabled_title),
            summary = stringRes(R.string.dictate__rewording_enabled_summary),
        )

        ListPreference(
            prefs.dictate.promptsLayout,
            icon = Icons.Default.ViewAgenda,
            title = stringRes(R.string.dictate__prompts_layout_title),
            entries = listPrefEntries {
                entry(
                    key = DictatePromptsLayout.PANEL,
                    label = stringRes(R.string.dictate__prompts_layout_panel_label),
                    description = stringRes(R.string.dictate__prompts_layout_panel_description),
                )
                entry(
                    key = DictatePromptsLayout.ROW,
                    label = stringRes(R.string.dictate__prompts_layout_row_label),
                    description = stringRes(R.string.dictate__prompts_layout_row_description),
                )
            },
            enabledIf = { prefs.dictate.rewordingEnabled isEqualTo true },
        )

        val rewordingProviderId by prefs.dictate.rewordingProviderId.collectAsState()
        PreferenceGroup(title = stringRes(R.string.dictate__rewording_provider_group)) {
            ListPreference(
                prefs.dictate.rewordingProviderId,
                icon = Icons.Default.SmartToy,
                title = stringRes(R.string.dictate__rewording_provider_title),
                entries = listPrefEntries {
                    ProviderRegistry.presets
                        .filter { it.capabilities.chat }
                        .forEach { entry(key = it.id, label = it.displayName) }
                    entry(key = "custom", label = stringRes(R.string.dictate__provider__custom))
                },
            )

            val reuseKey = stringRes(R.string.dictate__rewording_api_key_reuse)
            TextInputPreference(
                pref = prefs.dictate.rewordingApiKey,
                icon = Icons.Default.Key,
                title = stringRes(R.string.dictate__rewording_api_key_title),
                placeholder = stringRes(R.string.dictate__api_key_placeholder),
                isSecret = true,
                summaryProvider = { if (it.isBlank()) reuseKey else "••••••" + it.takeLast(4) },
            )

            val modelDefault = stringRes(R.string.dictate__model_default_summary)
            TextInputPreference(
                pref = prefs.dictate.rewordingModel,
                icon = Icons.Default.ModelTraining,
                title = stringRes(R.string.dictate__rewording_model_title),
                placeholder = stringRes(R.string.dictate__model_placeholder),
                summaryProvider = { it.ifBlank { modelDefault } },
            )

            if (rewordingProviderId == "custom") {
                val baseUrlRequired = stringRes(R.string.dictate__base_url_required)
                TextInputPreference(
                    pref = prefs.dictate.rewordingCustomBaseUrl,
                    icon = Icons.Default.Dns,
                    title = stringRes(R.string.dictate__base_url_title),
                    placeholder = stringRes(R.string.dictate__base_url_placeholder),
                    summaryProvider = { it.ifBlank { baseUrlRequired } },
                )
            }
        }

        PreferenceGroup(title = stringRes(R.string.dictate__rewording_prompts_group)) {
            val promptCount by produceState(initialValue = -1) {
                value = withContext(Dispatchers.IO) {
                    PromptsDatabaseHelper(context.applicationContext).count()
                }
            }
            Preference(
                icon = Icons.Default.ListAlt,
                title = stringRes(R.string.dictate__manage_prompts_title),
                summary = if (promptCount < 0) {
                    stringRes(R.string.dictate__manage_prompts_summary_loading)
                } else {
                    stringRes(R.string.dictate__manage_prompts_summary, "count" to promptCount)
                },
                onClick = { navController.navigate(Routes.Settings.DictatePrompts) },
            )

            SwitchPreference(
                prefs.dictate.autoFormattingEnabled,
                icon = Icons.Default.AutoFixHigh,
                title = stringRes(R.string.dictate__auto_formatting_title),
                summary = stringRes(R.string.dictate__auto_formatting_summary),
            )

            val systemSelection by prefs.dictate.systemPromptSelection.collectAsState()
            ListPreference(
                prefs.dictate.systemPromptSelection,
                icon = Icons.Default.Psychology,
                title = stringRes(R.string.dictate__system_prompt_title),
                entries = promptSelectionEntries(),
            )
            if (systemSelection == DictatePromptDefaults.SELECTION_CUSTOM) {
                TextInputPreference(
                    pref = prefs.dictate.systemPromptCustom,
                    icon = Icons.Default.Edit,
                    title = stringRes(R.string.dictate__system_prompt_custom_title),
                    placeholder = stringRes(R.string.dictate__system_prompt_custom_placeholder),
                    multiline = true,
                )
            }
        }
    }
}
