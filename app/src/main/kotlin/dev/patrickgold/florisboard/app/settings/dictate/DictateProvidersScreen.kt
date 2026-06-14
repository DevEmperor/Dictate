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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.dictate.dictateProxyConfig
import dev.patrickgold.florisboard.dictate.provider.OpenAiCompatibleClient
import dev.patrickgold.florisboard.dictate.provider.ProviderAccount
import dev.patrickgold.florisboard.dictate.provider.ProviderAccounts
import dev.patrickgold.florisboard.dictate.provider.ProviderPreset
import dev.patrickgold.florisboard.dictate.provider.ProviderRegistry
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.datastore.ui.ListPreference
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import dev.patrickgold.jetpref.datastore.ui.listPrefEntries
import dev.patrickgold.jetpref.material.ui.JetPrefAlertDialog
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes

/**
 * The central "AI providers" manager: configure an API key and model(s) for any number of providers
 * (the built-in [ProviderRegistry] presets plus user-defined custom endpoints) and choose which one is
 * active for transcription and which for rewording. Each provider keeps its own credentials in the
 * keyring ([ProviderAccounts]), so switching the active provider never loses another's key.
 */
@Composable
fun DictateProvidersScreen() = FlorisScreen {
    title = stringRes(R.string.dictate__providers_title)
    previewFieldVisible = false
    iconSpaceReserved = true

    val prefs by FlorisPreferenceStore

    content {
        val navController = LocalNavController.current
        val accounts by prefs.dictate.providerAccounts.collectAsState()
        val scope = rememberCoroutineScope()

        // The provider currently being edited in the dialog (null = closed).
        var editingId by remember { mutableStateOf<String?>(null) }

        fun writeKeyring(updated: ProviderAccounts) {
            scope.launch { prefs.dictate.providerAccounts.set(updated) }
        }

        // All custom endpoints stored in the keyring (built-ins are taken from the registry).
        val customAccounts = accounts.accounts.values
            .filter { it.isCustom }
            .sortedBy { it.displayName.lowercase() }

        PreferenceGroup(title = stringRes(R.string.dictate__providers_active_group)) {
            ListPreference(
                prefs.dictate.transcriptionProviderId,
                icon = Icons.Default.Mic,
                title = stringRes(R.string.dictate__providers_active_transcription),
                entries = listPrefEntries {
                    ProviderRegistry.presets
                        .filter { it.capabilities.transcription }
                        .forEach { entry(key = it.id, label = it.displayName) }
                    customAccounts.forEach { entry(key = it.providerId, label = customLabel(it)) }
                },
            )
            ListPreference(
                prefs.dictate.rewordingProviderId,
                icon = Icons.Default.SmartToy,
                title = stringRes(R.string.dictate__providers_active_rewording),
                entries = listPrefEntries {
                    ProviderRegistry.presets
                        .filter { it.capabilities.chat }
                        .forEach { entry(key = it.id, label = it.displayName) }
                    customAccounts.forEach { entry(key = it.providerId, label = customLabel(it)) }
                },
            )
        }

        PreferenceGroup(title = stringRes(R.string.dictate__providers_manage_group)) {
            val keySet = stringRes(R.string.dictate__providers_status_key_set)
            val noKey = stringRes(R.string.dictate__providers_status_no_key)

            ProviderRegistry.presets.forEach { preset ->
                val account = accounts[preset.id]
                Preference(
                    icon = Icons.Default.Cloud,
                    title = preset.displayName,
                    summary = providerSummary(preset, account, keySet, noKey),
                    onClick = { editingId = preset.id },
                )
            }

            customAccounts.forEach { account ->
                Preference(
                    icon = Icons.Default.Dns,
                    title = customLabel(account),
                    summary = if (account.hasKey || account.customBaseUrl.isNotBlank()) {
                        account.customBaseUrl.ifBlank { keySet }
                    } else {
                        stringRes(R.string.dictate__providers_status_unconfigured)
                    },
                    onClick = { editingId = account.providerId },
                )
            }

            Preference(
                icon = Icons.Default.Add,
                title = stringRes(R.string.dictate__providers_add_custom),
                summary = stringRes(R.string.dictate__providers_add_custom_summary),
                onClick = { editingId = ProviderAccount.newCustomId() },
            )
        }

        PreferenceGroup(title = stringRes(R.string.dictate__providers_network_group)) {
            val proxyEnabled by prefs.dictate.proxyEnabled.collectAsState()
            val proxyHost by prefs.dictate.proxyHost.collectAsState()
            val proxyPort by prefs.dictate.proxyPort.collectAsState()
            val proxyOff = stringRes(R.string.dictate__proxy_summary_off)
            Preference(
                icon = Icons.Default.Lan,
                title = stringRes(R.string.dictate__proxy_title),
                summary = if (proxyEnabled && proxyHost.isNotBlank()) {
                    "$proxyHost:$proxyPort"
                } else {
                    proxyOff
                },
                onClick = { navController.navigate(Routes.Settings.DictateProxy) },
            )
        }

        editingId?.let { id ->
            val preset = ProviderRegistry.byId(id)
            ProviderEditorDialog(
                preset = preset,
                account = accounts.getOrEmpty(id),
                onDismiss = { editingId = null },
                onSave = { updated ->
                    writeKeyring(accounts.put(updated))
                    editingId = null
                },
                onDelete = if (preset == null) {
                    {
                        writeKeyring(accounts.remove(id))
                        editingId = null
                    }
                } else {
                    null
                },
            )
        }
    }
}

/** Label for a custom endpoint: its user-given name, or a generic fallback. */
private fun customLabel(account: ProviderAccount): String =
    account.displayName.ifBlank { "Custom server" }

/** One-line status for a built-in provider row: key state + its capabilities. */
@Composable
private fun providerSummary(
    preset: ProviderPreset,
    account: ProviderAccount?,
    keySet: String,
    noKey: String,
): String {
    val caps = buildList {
        if (preset.capabilities.transcription) add(stringRes(R.string.dictate__providers_cap_stt))
        if (preset.capabilities.chat) add(stringRes(R.string.dictate__providers_cap_chat))
    }.joinToString(", ")
    val keyState = if (account?.hasKey == true) keySet else noKey
    return "$keyState · $caps"
}

/**
 * Multi-field editor for a single provider. Built-in providers ([preset] != null) expose only the key
 * and the relevant model fields; custom endpoints additionally edit a display name and base URL and can
 * be deleted ([onDelete] != null). All fields are committed together on confirm.
 */
@Composable
private fun ProviderEditorDialog(
    preset: ProviderPreset?,
    account: ProviderAccount,
    onDismiss: () -> Unit,
    onSave: (ProviderAccount) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val isCustom = preset == null
    val showTranscription = preset?.capabilities?.transcription ?: true
    val showChat = preset?.capabilities?.chat ?: true

    var displayName by remember { mutableStateOf(account.displayName) }
    var apiKey by remember { mutableStateOf(account.apiKey) }
    var baseUrl by remember { mutableStateOf(account.customBaseUrl) }
    var transcriptionModel by remember { mutableStateOf(account.transcriptionModel) }
    var chatModel by remember { mutableStateOf(account.chatModel) }
    // Live catalog cache, updated when the picker fetches; persisted together with the rest on confirm.
    var cachedModels by remember { mutableStateOf(account.cachedModels) }
    var pickerKind by remember { mutableStateOf<ModelKind?>(null) }

    // Effective preset to drive the model picker (custom endpoints get a base-URL-only preset).
    val effectivePreset = preset ?: ProviderRegistry.custom(baseUrl)

    JetPrefAlertDialog(
        title = preset?.displayName ?: stringRes(R.string.dictate__providers_custom_title),
        confirmLabel = stringRes(R.string.action__ok),
        dismissLabel = stringRes(R.string.action__cancel),
        neutralLabel = if (onDelete != null) stringRes(R.string.action__delete) else null,
        onConfirm = {
            onSave(
                account.copy(
                    displayName = displayName.trim(),
                    apiKey = apiKey.trim(),
                    customBaseUrl = baseUrl.trim(),
                    transcriptionModel = transcriptionModel.trim(),
                    chatModel = chatModel.trim(),
                    cachedModels = cachedModels,
                    cachedModelsAt = if (cachedModels != account.cachedModels) {
                        System.currentTimeMillis()
                    } else {
                        account.cachedModelsAt
                    },
                )
            )
        },
        onDismiss = onDismiss,
        onNeutral = { onDelete?.invoke() },
    ) {
        Column {
            if (isCustom) {
                EditorField(
                    label = stringRes(R.string.dictate__providers_field_name),
                    value = displayName,
                    onValueChange = { displayName = it },
                )
                EditorField(
                    label = stringRes(R.string.dictate__base_url_title),
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    placeholder = stringRes(R.string.dictate__base_url_placeholder),
                    keyboardType = KeyboardType.Uri,
                )
            }
            EditorField(
                label = stringRes(R.string.dictate__api_key_title),
                value = apiKey,
                onValueChange = { apiKey = it },
                placeholder = stringRes(R.string.dictate__api_key_placeholder),
                isSecret = true,
            )
            ConnectionTestRow(preset = effectivePreset, apiKey = apiKey)
            if (showTranscription) {
                EditorField(
                    label = stringRes(R.string.dictate__providers_field_transcription_model),
                    value = transcriptionModel,
                    onValueChange = { transcriptionModel = it },
                    placeholder = preset?.defaultTranscriptionModel
                        ?: stringRes(R.string.dictate__model_placeholder),
                    onBrowse = { pickerKind = ModelKind.TRANSCRIPTION },
                )
            }
            if (showChat) {
                EditorField(
                    label = stringRes(R.string.dictate__providers_field_chat_model),
                    value = chatModel,
                    onValueChange = { chatModel = it },
                    placeholder = preset?.defaultChatModel
                        ?: stringRes(R.string.dictate__model_placeholder),
                    onBrowse = { pickerKind = ModelKind.CHAT },
                )
            }
        }
    }

    pickerKind?.let { kind ->
        ModelPickerDialog(
            kind = kind,
            preset = effectivePreset,
            apiKey = apiKey,
            current = if (kind == ModelKind.TRANSCRIPTION) transcriptionModel else chatModel,
            cachedModels = cachedModels,
            onModelsFetched = { cachedModels = it },
            onPick = { picked ->
                if (kind == ModelKind.TRANSCRIPTION) transcriptionModel = picked else chatModel = picked
            },
            onDismiss = { pickerKind = null },
        )
    }
}

/**
 * A "Test connection" action with an inline result. Performs a lightweight `listModels()` call against
 * the provider's base URL with the currently entered key, so the user can verify the endpoint + key are
 * reachable before saving. A model count on success doubles as proof the catalog loads.
 */
@Composable
private fun ConnectionTestRow(preset: ProviderPreset, apiKey: String) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val prefs by FlorisPreferenceStore
    var testing by remember { mutableStateOf(false) }
    // null = not run yet; Pair(ok, message) once a test finished.
    var result by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    val okColor = MaterialTheme.colorScheme.primary
    val errColor = MaterialTheme.colorScheme.error
    val failedFallback = stringRes(R.string.dictate__providers_test_failed)
    // Resolved here (composable scope) so the background coroutine can format without touching Compose.
    val successTemplate = context.getString(R.string.dictate__providers_test_success)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        result?.let { (ok, message) ->
            Text(
                text = message,
                color = if (ok) okColor else errColor,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
        } ?: Spacer(Modifier.weight(1f))
        TextButton(
            enabled = !testing,
            onClick = {
                testing = true
                result = null
                scope.launch {
                    result = try {
                        val count = OpenAiCompatibleClient
                            .from(
                                preset, apiKey.trim(),
                                baseUrlOverride = preset.baseUrl,
                                proxy = prefs.dictate.dictateProxyConfig(),
                            )
                            .listModels()
                            .size
                        true to successTemplate.replace("{count}", count.toString())
                    } catch (e: Exception) {
                        false to (e.message ?: failedFallback)
                    } finally {
                        testing = false
                    }
                }
            },
        ) {
            if (testing) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp).size(16.dp))
            }
            Text(stringRes(R.string.dictate__providers_test))
        }
    }
}

/**
 * A single labeled text field inside the provider editor dialog. When [onBrowse] is set, a trailing
 * button opens the model picker (the field still accepts free-text input).
 */
@Composable
private fun EditorField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    isSecret: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    onBrowse: (() -> Unit)? = null,
) {
    OutlinedTextField(
        modifier = Modifier.padding(top = 8.dp),
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        label = { Text(label) },
        placeholder = { if (placeholder.isNotEmpty()) Text(placeholder) },
        visualTransformation = if (isSecret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isSecret) KeyboardType.Password else keyboardType,
        ),
        trailingIcon = onBrowse?.let {
            {
                IconButton(onClick = it) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringRes(R.string.dictate__model_picker_title),
                    )
                }
            }
        },
    )
}
