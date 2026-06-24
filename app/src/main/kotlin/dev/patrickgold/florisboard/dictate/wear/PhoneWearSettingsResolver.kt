/*
 * Copyright (C) 2026 The Dictate Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */

package dev.patrickgold.florisboard.dictate.wear

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
 * The API key is intentionally NOT included here: the default flow is tethered (the phone transcribes),
 * so the secret never has to leave the phone. Standalone key delivery is handled separately (P2b) and
 * gated on an explicit opt-in.
 */
object PhoneWearSettingsResolver {

    fun resolve(prefs: FlorisPreferenceModel): DictateSyncedSettings {
        val id = prefs.dictate.transcriptionProviderId.get()
        val account = prefs.dictate.providerAccounts.get().getOrEmpty(id)
        val preset = presetFor(account)

        val model = account.transcriptionModel.ifBlank { preset.defaultTranscriptionModel ?: "" }
        val baseUrl = if (account.isCustom) account.customBaseUrl else preset.baseUrl
        val language = prefs.dictate.activeInputLanguage.get().takeIf { it != DictateLanguages.DETECT }

        // Only ship the key when the user opted the watch into standalone; otherwise it stays on the
        // phone and the watch tethers (the phone transcribes).
        val standalone = prefs.dictate.wearStandaloneEnabled.get()

        return DictateSyncedSettings(
            transcriptionProviderId = id,
            transcriptionApi = preset.transcriptionApi,
            baseUrl = baseUrl,
            model = model,
            apiKey = if (standalone) account.apiKey else "",
            language = language,
            stylePrompt = stylePrompt(prefs),
            standaloneEnabled = standalone,
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

