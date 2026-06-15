/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate.data.prefs

import android.content.Context
import androidx.compose.ui.graphics.Color
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.dictate.DictateLanguages
import dev.patrickgold.florisboard.dictate.provider.DictateProxyType
import dev.patrickgold.florisboard.dictate.provider.ProviderAccount
import dev.patrickgold.florisboard.dictate.provider.ProxyConfig
import java.net.Proxy
import java.util.Locale
import dev.patrickgold.florisboard.ime.smartbar.quickaction.QuickAction
import dev.patrickgold.florisboard.ime.smartbar.quickaction.keyData
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData

/**
 * One-time import of the legacy Dictate transcription settings (provider, API key, model) into the
 * unified JetPref store, so upgrading users keep their configuration after the in-place update while
 * everything is edited in one place going forward (see `docs/COMPATIBILITY.md`).
 *
 * Idempotent: guarded by `prefs.dictate.legacyImported`, which is set on the first run regardless of
 * whether legacy data was present (fresh installs simply mark it done).
 *
 * Must be called only after [FlorisPreferenceStore] has finished loading.
 */
object DictateLegacyMigrator {

    @Suppress("DEPRECATION") // writes the deprecated flat prefs that migrateProviderKeyringIfNeeded folds in
    suspend fun migrateIfNeeded(context: Context) {
        val prefs by FlorisPreferenceStore
        if (prefs.dictate.legacyImported.get()) return

        val legacy = DictateLegacyPreferences(context.applicationContext)
        if (legacy.isPresent()) {
            val s = legacy.readSnapshot()

            // Legacy provider index: 0 = OpenAI, 1 = Groq, 2 = Custom.
            val providerId = when (s.transcriptionProvider) {
                1 -> "groq"
                2 -> "custom"
                else -> "openai"
            }
            prefs.dictate.transcriptionProviderId.set(providerId)

            s.effectiveTranscriptionApiKey()
                ?.takeIf { it.isNotBlank() && it != "NO_API_KEY" }
                ?.let { prefs.dictate.apiKey.set(it) }

            val model = when (s.transcriptionProvider) {
                1 -> s.transcriptionGroqModel
                2 -> s.transcriptionCustomModel
                else -> s.transcriptionOpenaiModel
            }
            model?.takeIf { it.isNotBlank() }?.let { prefs.dictate.transcriptionModel.set(it) }

            s.transcriptionCustomHost?.takeIf { it.isNotBlank() }
                ?.let { prefs.dictate.customBaseUrl.set(it) }

            // --- Rewording / GPT settings (roadmap section 4). The prompts themselves live in the
            // shared prompts.db and carry over automatically; only these settings need importing. ---
            prefs.dictate.rewordingEnabled.set(s.rewordingEnabled)
            prefs.dictate.autoFormattingEnabled.set(s.autoFormattingEnabled)

            val rewordingProviderId = when (s.rewordingProvider) {
                1 -> "groq"
                2 -> "custom"
                else -> "openai"
            }
            prefs.dictate.rewordingProviderId.set(rewordingProviderId)

            s.effectiveRewordingApiKey()
                ?.takeIf { it.isNotBlank() && it != "NO_API_KEY" }
                ?.let { prefs.dictate.rewordingApiKey.set(it) }

            val rewordingModel = when (s.rewordingProvider) {
                1 -> s.rewordingGroqModel
                2 -> s.rewordingCustomModel
                else -> s.rewordingOpenaiModel
            }
            rewordingModel?.takeIf { it.isNotBlank() }?.let { prefs.dictate.rewordingModel.set(it) }

            s.rewordingCustomHost?.takeIf { it.isNotBlank() }
                ?.let { prefs.dictate.rewordingCustomBaseUrl.set(it) }

            prefs.dictate.systemPromptSelection.set(s.systemPromptSelection)
            s.systemPromptCustomText?.let { prefs.dictate.systemPromptCustom.set(it) }
            prefs.dictate.stylePromptSelection.set(s.stylePromptSelection)
            s.stylePromptCustomText?.let { prefs.dictate.stylePromptCustom.set(it) }

            // --- Output behavior (roadmap section 10) ---
            prefs.dictate.autoEnter.set(s.autoEnter)
            prefs.dictate.instantOutput.set(s.instantOutput)
            prefs.dictate.outputSpeed.set(s.outputSpeed)
            prefs.dictate.resendButton.set(s.resendButton)

            // --- Recording capture toggles (roadmap 11.7). These were read from the legacy snapshot
            // but previously never written, so an upgrading user silently lost them. ---
            prefs.dictate.audioFocus.set(s.audioFocus)
            prefs.dictate.useBluetoothMic.set(s.useBluetoothMic)
            prefs.dictate.instantRecording.set(s.instantRecording)

            // --- Dictation languages (roadmap 11.7). The legacy value is an *unordered* StringSet, so
            // rebuild a stable order ("detect" first, then the rest sorted) and map the legacy active
            // index onto it as a best effort. Previously neither the selection nor the active language
            // was migrated, resetting the user back to the default {detect,en}. ---
            val orderedLanguages = buildList {
                if (s.inputLanguages.contains("detect")) add("detect")
                addAll(s.inputLanguages.filter { it != "detect" }.sorted())
            }
            if (orderedLanguages.isNotEmpty()) {
                prefs.dictate.inputLanguages.set(orderedLanguages.joinToString(","))
                prefs.dictate.activeInputLanguage.set(
                    orderedLanguages.getOrNull(s.inputLanguagePos) ?: orderedLanguages.first(),
                )
            }

            // --- App UI language (roadmap 11.7): legacy "system" maps to FlorisBoard's "auto". ---
            prefs.other.settingsLanguage.set(if (s.appLanguage == "system") "auto" else s.appLanguage)

            // --- Accent color: the legacy app had a single user-pickable accent (ARGB int, key
            // "net.devemperor.dictate.accent_color") that tinted the keyboard prompt UI. It was read
            // into the snapshot but never applied, so upgraders lost their familiar color. Carry it
            // over to both new accent prefs so the look stays identical: theme.accentColor drives the
            // keyboard (FlorisImeTheme), other.accentColor the settings app. ---
            val legacyAccent = Color(s.accentColor)
            prefs.theme.accentColor.set(legacyAccent)
            prefs.other.accentColor.set(legacyAccent)

            // --- Network proxy (roadmap 5.6): the legacy app stored one combined spec string
            // ("socks5|http://user:pass@host:port"); split it into the new structured fields. ---
            prefs.dictate.proxyEnabled.set(s.proxyEnabled)
            ProxyConfig.parse(s.proxyHost)?.let { proxy ->
                prefs.dictate.proxyType.set(
                    if (proxy.type == Proxy.Type.SOCKS) DictateProxyType.SOCKS5 else DictateProxyType.HTTP,
                )
                prefs.dictate.proxyHost.set(proxy.host)
                prefs.dictate.proxyPort.set(proxy.port.toString())
                proxy.username?.let { prefs.dictate.proxyUsername.set(it) }
                proxy.password?.let { prefs.dictate.proxyPassword.set(it) }
            }

            // --- Rate/donate nudges (roadmap 9.7/9.8): carry over the "handled" flags so users who
            // already rated/donated in the legacy app are never asked again. The old usage DB that
            // tracked total audio time was dropped, so the new counter simply starts at 0. ---
            prefs.dictate.hasRated.set(s.hasRatedInPlaystore)
            prefs.dictate.hasDonated.set(s.hasDonated)
        }

        prefs.dictate.legacyImported.set(true)
    }

    /**
     * On the first run of a fresh install, adds the device's system language to the dictation language
     * selection (on top of the default `{detect, en}`) so a non-English user can dictate in their own
     * language straight away without digging through settings. Only an *untouched* default selection is
     * augmented, so legacy upgraders (whose languages were already imported above) and users who have
     * customised the list are left alone. Idempotent via `prefs.dictate.inputLanguagesSeeded`.
     */
    suspend fun seedDeviceLanguageIfNeeded() {
        val prefs by FlorisPreferenceStore
        if (prefs.dictate.inputLanguagesSeeded.get()) return
        if (prefs.dictate.inputLanguages.get() == "detect,en") {
            val device = DictateLanguages.matchDevice(Locale.getDefault())
            if (device != null && device.code != "en" && device.code != DictateLanguages.DETECT) {
                prefs.dictate.inputLanguages.set("detect,en,${device.code}")
            }
        }
        prefs.dictate.inputLanguagesSeeded.set(true)
    }

    /**
     * One-time fold of the deprecated flat credential prefs (api key, models, custom base URLs) into
     * the per-provider keyring ([ProviderAccounts]). Runs after [migrateIfNeeded], so it covers both
     * legacy-Java upgraders (whose flat prefs were just populated above) and existing fork users (who
     * already had flat prefs from an earlier build). Idempotent via `providerAccountsMigrated`.
     *
     * Each provider keeps one account holding its key plus separate transcription/chat models. If the
     * rewording side used a *different* custom host than the transcription side, it gets its own
     * `custom:<uuid>` account so the two base URLs don't collide.
     */
    @Suppress("DEPRECATION")
    suspend fun migrateProviderKeyringIfNeeded() {
        val prefs by FlorisPreferenceStore
        if (prefs.dictate.providerAccountsMigrated.get()) return

        var keyring = prefs.dictate.providerAccounts.get()

        // --- Transcription side -> its active provider id ---
        val tProviderId = prefs.dictate.transcriptionProviderId.get()
        val tKey = prefs.dictate.apiKey.get()
        val tModel = prefs.dictate.transcriptionModel.get()
        val tBaseUrl = prefs.dictate.customBaseUrl.get()
        keyring = keyring.edit(tProviderId) { account ->
            account.copy(
                apiKey = tKey.ifBlank { account.apiKey },
                transcriptionModel = tModel.ifBlank { account.transcriptionModel },
                customBaseUrl = tBaseUrl.ifBlank { account.customBaseUrl },
            )
        }

        // --- Rewording side -> its active provider id (may equal the transcription one) ---
        var rProviderId = prefs.dictate.rewordingProviderId.get()
        val rKey = prefs.dictate.rewordingApiKey.get()
        val rModel = prefs.dictate.rewordingModel.get()
        val rBaseUrl = prefs.dictate.rewordingCustomBaseUrl.get()

        // If both sides are "custom" but point at different hosts, split the rewording one off into its
        // own custom account so each keeps its correct base URL.
        if (rProviderId == "custom" && tProviderId == "custom" &&
            rBaseUrl.isNotBlank() && tBaseUrl.isNotBlank() && rBaseUrl != tBaseUrl
        ) {
            val splitId = ProviderAccount.newCustomId()
            keyring = keyring.edit(splitId) { account ->
                account.copy(
                    apiKey = rKey.ifBlank { keyring.getOrEmpty("custom").apiKey },
                    chatModel = rModel.ifBlank { account.chatModel },
                    customBaseUrl = rBaseUrl,
                )
            }
            rProviderId = splitId
            prefs.dictate.rewordingProviderId.set(splitId)
        } else {
            keyring = keyring.edit(rProviderId) { account ->
                account.copy(
                    // Blank legacy rewording key historically meant "reuse the transcription key".
                    apiKey = rKey.ifBlank { account.apiKey.ifBlank { if (rProviderId == tProviderId) tKey else account.apiKey } },
                    chatModel = rModel.ifBlank { account.chatModel },
                    customBaseUrl = rBaseUrl.ifBlank { account.customBaseUrl },
                )
            }
        }

        prefs.dictate.providerAccounts.set(keyring)
        prefs.dictate.providerAccountsMigrated.set(true)
    }

    /**
     * Removes the live-prompt Smartbar action ([KeyCode.DICTATE_LIVE_PROMPT]) from the saved action
     * arrangement. The live prompt is now a chip inside the prompt panel/row, so it no longer ships as a
     * separate Smartbar button; this strips the action that the earlier injection added (and that the
     * old default placed). Idempotent via `prefs.dictate.livePromptActionRemoved`. Power users can still
     * re-add it manually from the Smartbar editor – the action itself is left intact.
     */
    suspend fun removeLivePromptActionIfNeeded(context: Context) {
        val prefs by FlorisPreferenceStore
        if (prefs.dictate.livePromptActionRemoved.get()) return
        removeActionIfPresent(KeyCode.DICTATE_LIVE_PROMPT)
        prefs.dictate.livePromptActionRemoved.set(true)
    }

    /**
     * Ensures the AI prompt-panel action ([KeyCode.DICTATE_PROMPTS]) is present in the saved arrangement.
     * Injected separately (own guard) so users who already ran the live-prompt migration still receive it.
     */
    suspend fun migratePromptsActionIfNeeded(context: Context) {
        val prefs by FlorisPreferenceStore
        if (prefs.dictate.promptsActionMigrated.get()) return
        ensureActionPresent(TextKeyData.DICTATE_PROMPTS, KeyCode.DICTATE_PROMPTS)
        prefs.dictate.promptsActionMigrated.set(true)
    }

    /**
     * Injects [keyData] at the front of the saved dynamic action row unless an action with [code] is
     * already present anywhere in the arrangement. New defaults do not retroactively merge into a
     * persisted arrangement, so without this an upgrading user could never see/place the action.
     */
    private suspend fun ensureActionPresent(keyData: TextKeyData, code: Int) {
        val prefs by FlorisPreferenceStore
        val arrangement = prefs.smartbar.actionArrangement.get()
        val alreadyPresent = arrangement.run { dynamicActions + hiddenActions + listOfNotNull(stickyAction) }
            .any { it.keyData().code == code }
        if (!alreadyPresent) {
            val action = QuickAction.InsertKey(keyData)
            prefs.smartbar.actionArrangement.set(
                arrangement.copy(dynamicActions = listOf(action) + arrangement.dynamicActions),
            )
        }
    }

    /**
     * Strips every action with [code] from the saved arrangement (sticky/dynamic/hidden). No-op if it is
     * not present, so it is safe to run unconditionally behind a one-time guard.
     */
    private suspend fun removeActionIfPresent(code: Int) {
        val prefs by FlorisPreferenceStore
        val arrangement = prefs.smartbar.actionArrangement.get()
        val matches = { action: QuickAction -> action.keyData().code == code }
        val present = (arrangement.dynamicActions + arrangement.hiddenActions +
            listOfNotNull(arrangement.stickyAction)).any(matches)
        if (!present) return
        prefs.smartbar.actionArrangement.set(
            arrangement.copy(
                stickyAction = arrangement.stickyAction?.takeUnless(matches),
                dynamicActions = arrangement.dynamicActions.filterNot(matches),
                hiddenActions = arrangement.hiddenActions.filterNot(matches),
            ),
        )
    }
}
