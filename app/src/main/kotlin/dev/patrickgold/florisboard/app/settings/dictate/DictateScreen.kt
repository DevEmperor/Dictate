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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.ModelTraining
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.dictate.DictateLanguages
import dev.patrickgold.florisboard.dictate.data.prompts.DictatePromptDefaults
import dev.patrickgold.florisboard.dictate.provider.ProviderRegistry
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import org.florisboard.lib.compose.stringRes
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.datastore.ui.DialogSliderPreference
import dev.patrickgold.jetpref.datastore.ui.ListPreference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.SwitchPreference
import dev.patrickgold.jetpref.datastore.ui.listPrefEntries

@Composable
fun DictateScreen() = FlorisScreen {
    title = stringRes(R.string.dictate__title)
    previewFieldVisible = false
    iconSpaceReserved = true

    val prefs by FlorisPreferenceStore

    content {
        val navController = LocalNavController.current

        // The active transcription provider's display name, for the summary of the providers row.
        val transcriptionProviderId by prefs.dictate.transcriptionProviderId.collectAsState()
        val providerName = remember(transcriptionProviderId) {
            ProviderRegistry.byId(transcriptionProviderId)?.displayName ?: transcriptionProviderId
        }
        PreferenceGroup(title = stringRes(R.string.dictate__transcription_group)) {
            Preference(
                icon = Icons.Default.Cloud,
                title = stringRes(R.string.dictate__providers_title),
                summary = stringRes(R.string.dictate__providers_summary, "provider" to providerName),
                onClick = { navController.navigate(Routes.Settings.DictateProviders) },
            )

            val selectionRaw by prefs.dictate.inputLanguages.collectAsState()
            val detectLabel = stringRes(R.string.dictate__language_detect)
            val summary = remember(selectionRaw, detectLabel) {
                DictateLanguages.parseSelection(selectionRaw).joinToString(", ") {
                    if (it.code == DictateLanguages.DETECT) detectLabel else it.displayName()
                }
            }
            Preference(
                icon = Icons.Default.Translate,
                title = stringRes(R.string.dictate__languages_title),
                summary = summary,
                onClick = { navController.navigate(Routes.Settings.DictateLanguages) },
            )

            // Style prompt biases the transcription model towards proper punctuation/casing in the
            // active language (roadmap 2.4 / 4.11). It is sent with the transcription request.
            val styleSelection by prefs.dictate.stylePromptSelection.collectAsState()
            ListPreference(
                prefs.dictate.stylePromptSelection,
                icon = Icons.Default.Spellcheck,
                title = stringRes(R.string.dictate__style_prompt_title),
                entries = promptSelectionEntries(),
            )
            if (styleSelection == DictatePromptDefaults.SELECTION_CUSTOM) {
                TextInputPreference(
                    pref = prefs.dictate.stylePromptCustom,
                    icon = Icons.Default.Edit,
                    title = stringRes(R.string.dictate__style_prompt_custom_title),
                    placeholder = stringRes(R.string.dictate__style_prompt_custom_placeholder),
                    multiline = true,
                )
            }
        }

        PreferenceGroup(title = stringRes(R.string.dictate__recording_group)) {
            SwitchPreference(
                prefs.dictate.audioFocus,
                icon = Icons.Default.VolumeOff,
                title = stringRes(R.string.dictate__audio_focus_title),
                summary = stringRes(R.string.dictate__audio_focus_summary),
            )
            SwitchPreference(
                prefs.dictate.useBluetoothMic,
                icon = Icons.Default.Bluetooth,
                title = stringRes(R.string.dictate__bluetooth_mic_title),
                summary = stringRes(R.string.dictate__bluetooth_mic_summary),
            )
            SwitchPreference(
                prefs.dictate.keepScreenAwake,
                icon = Icons.Default.BrightnessHigh,
                title = stringRes(R.string.dictate__keep_screen_awake_title),
                summary = stringRes(R.string.dictate__keep_screen_awake_summary),
            )
            SwitchPreference(
                prefs.dictate.instantRecording,
                icon = Icons.Default.Bolt,
                title = stringRes(R.string.dictate__instant_recording_title),
                summary = stringRes(R.string.dictate__instant_recording_summary),
            )
        }

        PreferenceGroup(title = stringRes(R.string.dictate__rewording_group)) {
            val rewordingEnabled by prefs.dictate.rewordingEnabled.collectAsState()
            Preference(
                icon = Icons.Default.AutoAwesome,
                title = stringRes(R.string.dictate__rewording_title),
                summary = stringRes(
                    if (rewordingEnabled) R.string.dictate__rewording_summary_on
                    else R.string.dictate__rewording_summary_off,
                ),
                onClick = { navController.navigate(Routes.Settings.DictateRewording) },
            )
        }

        PreferenceGroup(title = stringRes(R.string.dictate__output_group)) {
            SwitchPreference(
                prefs.dictate.autoEnter,
                icon = Icons.AutoMirrored.Filled.KeyboardReturn,
                title = stringRes(R.string.dictate__auto_enter_title),
                summary = stringRes(R.string.dictate__auto_enter_summary),
            )
            SwitchPreference(
                prefs.dictate.instantOutput,
                icon = Icons.Default.Keyboard,
                title = stringRes(R.string.dictate__instant_output_title),
                summary = stringRes(R.string.dictate__instant_output_summary),
            )
            DialogSliderPreference(
                prefs.dictate.outputSpeed,
                icon = Icons.Default.Speed,
                title = stringRes(R.string.dictate__output_speed_title),
                valueLabel = { stringRes(R.string.dictate__output_speed_value, "v" to it) },
                min = 1,
                max = 10,
                stepIncrement = 1,
                // Only relevant when the text is "typed" out rather than committed instantly.
                enabledIf = { prefs.dictate.instantOutput isEqualTo false },
            )
            SwitchPreference(
                prefs.dictate.resendButton,
                icon = Icons.Default.Replay,
                title = stringRes(R.string.dictate__resend_button_title),
                summary = stringRes(R.string.dictate__resend_button_summary),
            )
        }
    }
}
