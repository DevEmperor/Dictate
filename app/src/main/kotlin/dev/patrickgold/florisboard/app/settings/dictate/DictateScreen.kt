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
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.ModelTraining
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.dictate.DictateLanguages
import dev.patrickgold.florisboard.dictate.data.prompts.DictatePromptDefaults
import dev.patrickgold.florisboard.dictate.provider.ProviderAccounts
import dev.patrickgold.florisboard.dictate.provider.ProviderRegistry
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import org.florisboard.lib.compose.stringRes
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.datastore.ui.DialogSliderPreference
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

        // The active providers' display names, for the summary of the providers row. Show both the
        // transcription and (when rewording is on) the rewording provider, since they are configured
        // independently and often differ — the row previously surfaced only the transcription one.
        val transcriptionProviderId by prefs.dictate.transcriptionProviderId.collectAsState()
        val rewordingProviderId by prefs.dictate.rewordingProviderId.collectAsState()
        val rewordingEnabled by prefs.dictate.rewordingEnabled.collectAsState()
        val accounts by prefs.dictate.providerAccounts.collectAsState()
        val transcriptionName = remember(transcriptionProviderId, accounts) {
            providerDisplayName(transcriptionProviderId, accounts)
        }
        val rewordingName = remember(rewordingProviderId, accounts) {
            providerDisplayName(rewordingProviderId, accounts)
        }
        PreferenceGroup(title = stringRes(R.string.dictate__transcription_group)) {
            Preference(
                icon = Icons.Default.Cloud,
                title = stringRes(R.string.dictate__providers_title),
                summary = if (rewordingEnabled && transcriptionName != rewordingName) {
                    stringRes(
                        R.string.dictate__providers_summary_both,
                        "transcription" to transcriptionName,
                        "rewording" to rewordingName,
                    )
                } else {
                    stringRes(R.string.dictate__providers_summary, "provider" to transcriptionName)
                },
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
            val activeLang by prefs.dictate.activeInputLanguage.collectAsState()
            PromptSelectionPreference(
                pref = prefs.dictate.stylePromptSelection,
                icon = Icons.Default.Spellcheck,
                title = stringRes(R.string.dictate__style_prompt_title),
                entries = promptSelectionEntries(),
                infoTitle = stringRes(R.string.dictate__style_prompt_info_title),
                infoDescription = stringRes(R.string.dictate__style_prompt_info_description),
                infoPromptText = DictatePromptDefaults.punctuationPromptFor(activeLang),
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

            // Custom words (roadmap 11.12): names/jargon appended to the transcription prompt so the
            // model spells them correctly. Works on top of any style prompt selection above.
            TextInputPreference(
                pref = prefs.dictate.customWords,
                icon = Icons.Default.MenuBook,
                title = stringRes(R.string.dictate__custom_words_title),
                placeholder = stringRes(R.string.dictate__custom_words_placeholder),
                multiline = true,
                notSetSummary = stringRes(R.string.dictate__custom_words_summary_empty),
            )
        }

        PreferenceGroup(title = stringRes(R.string.dictate__recording_group)) {
            // Floating dictation button (issue #88): the master toggle lives here on the main page; the
            // dedicated screen below holds the setup (accessibility service, mic) and display options.
            val context = LocalContext.current
            val floatingEnabled by prefs.dictate.floatingButtonEnabled.collectAsState()
            var prevFloatingEnabled by remember { mutableStateOf(floatingEnabled) }
            var showFloatingDisclosure by remember { mutableStateOf(false) }
            LaunchedEffect(floatingEnabled) {
                // Just switched on and the service isn't set up yet → show the disclosure and send the
                // user to the system accessibility settings (same first-run flow as the dedicated screen).
                if (floatingEnabled && !prevFloatingEnabled && !isOverlayServiceEnabled(context)) {
                    showFloatingDisclosure = true
                }
                prevFloatingEnabled = floatingEnabled
            }
            SwitchPreference(
                prefs.dictate.floatingButtonEnabled,
                icon = Icons.Default.Adjust,
                title = stringRes(R.string.dictate__floating_button_enable_title),
                summary = stringRes(R.string.dictate__floating_button_enable_summary),
            )
            if (floatingEnabled) {
                Preference(
                    icon = Icons.Default.Tune,
                    title = stringRes(R.string.dictate__floating_button_title),
                    onClick = { navController.navigate(Routes.Settings.DictateFloatingButton) },
                )
            }
            if (showFloatingDisclosure) {
                AlertDialog(
                    onDismissRequest = { showFloatingDisclosure = false },
                    title = { Text(stringRes(R.string.dictate__floating_button_disclosure_title)) },
                    text = { Text(stringRes(R.string.dictate__floating_button_disclosure_message)) },
                    confirmButton = {
                        TextButton(onClick = {
                            showFloatingDisclosure = false
                            openAccessibilitySettings(context)
                        }) {
                            Text(stringRes(R.string.dictate__floating_button_disclosure_continue))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showFloatingDisclosure = false }) {
                            Text(stringRes(android.R.string.cancel))
                        }
                    },
                )
            }
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
            SwitchPreference(
                prefs.dictate.rememberLastDictation,
                icon = Icons.Default.History,
                title = stringRes(R.string.dictate__remember_last_dictation_title),
                summary = stringRes(R.string.dictate__remember_last_dictation_summary),
            )
        }
    }
}

/**
 * Resolves a provider id to a human-readable name for the providers-row summary: built-ins come from
 * the [ProviderRegistry], user-defined endpoints from their stored display name in the keyring, falling
 * back to the raw id if neither is available.
 */
private fun providerDisplayName(id: String, accounts: ProviderAccounts): String {
    ProviderRegistry.byId(id)?.let { return it.displayName }
    return accounts[id]?.displayName?.takeIf { it.isNotBlank() } ?: id
}
