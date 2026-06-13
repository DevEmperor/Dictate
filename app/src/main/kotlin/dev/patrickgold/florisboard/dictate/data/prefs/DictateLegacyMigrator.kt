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
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
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

            // --- Rate/donate nudges (roadmap 9.7/9.8): carry over the "handled" flags so users who
            // already rated/donated in the legacy app are never asked again. The old usage DB that
            // tracked total audio time was dropped, so the new counter simply starts at 0. ---
            prefs.dictate.hasRated.set(s.hasRatedInPlaystore)
            prefs.dictate.hasDonated.set(s.hasDonated)
        }

        prefs.dictate.legacyImported.set(true)
    }

    /**
     * Ensures the live-prompt Smartbar action ([KeyCode.DICTATE_LIVE_PROMPT]) is present in the saved
     * action arrangement. New entries in [QuickActionArrangement.Default] do not retroactively appear
     * for users who already persisted an arrangement (e.g. by opening the Smartbar editor) before the
     * action shipped, so without this they could never find or place it. Idempotent via
     * `prefs.dictate.livePromptActionMigrated`; injects the action at the front of the dynamic row so
     * it is discoverable, exactly where the default puts it.
     */
    suspend fun migrateLivePromptActionIfNeeded(context: Context) {
        val prefs by FlorisPreferenceStore
        if (prefs.dictate.livePromptActionMigrated.get()) return
        ensureActionPresent(TextKeyData.DICTATE_LIVE_PROMPT, KeyCode.DICTATE_LIVE_PROMPT)
        prefs.dictate.livePromptActionMigrated.set(true)
    }

    /**
     * Like [migrateLivePromptActionIfNeeded], but for the AI prompt-panel action ([KeyCode.DICTATE_PROMPTS]).
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
}
