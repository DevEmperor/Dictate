/*
 * Copyright (C) 2026 The Dictate Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */

package dev.patrickgold.florisboard.dictate.wear

import androidx.compose.ui.graphics.toArgb
import dev.patrickgold.florisboard.app.FlorisPreferenceModel
import dev.patrickgold.florisboard.dictate.DictateLanguages
import dev.patrickgold.florisboard.dictate.data.prompts.DictatePromptDefaults
import dev.patrickgold.florisboard.dictate.provider.ProviderAccount
import dev.patrickgold.florisboard.dictate.provider.ProviderRegistry
import dev.patrickgold.florisboard.dictate.sync.DictateSyncedSettings

/**
 * Translates the phone's live Dictate settings into the [DictateSyncedSettings] snapshot pushed to the
 * watch (#106). Mirrors the active-account resolution in `DictateController` (transcriptionProviderId ->
 * keyring account -> registry preset) but only exposes what the watch needs.
 *
 * The watch tethers through the phone whenever it is reachable; the API key is also shipped (unless the
 * user turned the [wearStandaloneEnabled] master toggle off) so the watch can still transcribe on its
 * own when the phone is out of range. The phone accent color is shipped so the watch UI matches.
 */
object PhoneWearSettingsResolver {

    fun resolve(prefs: FlorisPreferenceModel): DictateSyncedSettings {
        val id = prefs.dictate.transcriptionProviderId.get()
        val account = prefs.dictate.providerAccounts.get().getOrEmpty(id)
        val preset = presetFor(account)

        val model = account.transcriptionModel.ifBlank { preset.defaultTranscriptionModel ?: "" }
        val baseUrl = if (account.isCustom) account.customBaseUrl else preset.baseUrl
        val language = prefs.dictate.activeInputLanguage.get().takeIf { it != DictateLanguages.DETECT }

        // Ship the key so the watch can work standalone when the phone is away, unless the user opted out
        // of syncing the secret (then the watch is tether-only and won't dictate without the phone).
        val syncKey = prefs.dictate.wearStandaloneEnabled.get()

        return DictateSyncedSettings(
            transcriptionProviderId = id,
            providerLabel = preset.displayName,
            transcriptionApi = preset.transcriptionApi,
            baseUrl = baseUrl,
            model = model,
            apiKey = if (syncKey) account.apiKey else "",
            language = language,
            stylePrompt = stylePrompt(prefs),
            accentColorArgb = prefs.other.accentColor.get().toArgb(),
        )
    }

    private fun presetFor(account: ProviderAccount) = when {
        account.isCustom -> ProviderRegistry.custom(account.customBaseUrl)
        else -> ProviderRegistry.byId(account.providerId) ?: ProviderRegistry.OPENAI
    }

    /**
     * The style/punctuation prompt the watch should send with a standalone transcription. Mirrors
     * `DictateController.transcriptionStylePrompt()` so the watch biases recognition exactly like the
     * phone (predefined punctuation prompt or the user's custom one, plus the custom-words list).
     */
    private fun stylePrompt(prefs: FlorisPreferenceModel): String? {
        val base = when (prefs.dictate.stylePromptSelection.get()) {
            DictatePromptDefaults.SELECTION_PREDEFINED ->
                DictatePromptDefaults.punctuationPromptFor(prefs.dictate.activeInputLanguage.get())
            DictatePromptDefaults.SELECTION_CUSTOM ->
                prefs.dictate.stylePromptCustom.get().takeIf { it.isNotBlank() }
            else -> null
        }
        return DictatePromptDefaults.appendCustomWords(base, prefs.dictate.customWords.get())
    }
}

