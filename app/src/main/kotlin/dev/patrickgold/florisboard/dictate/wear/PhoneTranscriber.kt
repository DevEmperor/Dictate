/*
 * Copyright (C) 2026 The Dictate Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */

package dev.patrickgold.florisboard.dictate.wear

import android.content.Context
import dev.patrickgold.florisboard.app.FlorisPreferenceModel
import dev.patrickgold.florisboard.dictate.DictateLanguages
import dev.patrickgold.florisboard.dictate.dictateProxyConfig
import dev.patrickgold.florisboard.dictate.provider.LocalTranscriptionProvider
import dev.patrickgold.florisboard.dictate.provider.OpenAiCompatibleClient
import dev.patrickgold.florisboard.dictate.provider.ProviderAccount
import dev.patrickgold.florisboard.dictate.provider.ProviderRegistry
import dev.patrickgold.florisboard.dictate.provider.TranscriptionApi
import dev.patrickgold.florisboard.dictate.provider.TranscriptionRequest
import java.io.File

/**
 * Phone-side transcription used by the tethered watch flow (#106): the watch streams audio over the
 * Data Layer and the phone transcribes it with its own active provider/credentials, mirroring the
 * normal in-app transcription path (including the on-device fallback provider).
 */
object PhoneTranscriber {

    suspend fun transcribe(context: Context, prefs: FlorisPreferenceModel, audio: File): String {
        val id = prefs.dictate.transcriptionProviderId.get()
        val account = prefs.dictate.providerAccounts.get().getOrEmpty(id)
        val preset = presetFor(account)
        val model = account.transcriptionModel.ifBlank { preset.defaultTranscriptionModel ?: "" }
        val language = prefs.dictate.activeInputLanguage.get().takeIf { it != DictateLanguages.DETECT }

        val request = TranscriptionRequest(audioFile = audio, model = model, language = language)

        return if (preset.transcriptionApi == TranscriptionApi.LOCAL_ONDEVICE) {
            // The phone is configured for on-device STT: transcribe locally, no network/key needed.
            LocalTranscriptionProvider(LocalTranscriptionProvider.modelDir(context.applicationContext, model))
                .transcribe(request).text.trim()
        } else {
            val client = OpenAiCompatibleClient.from(
                preset,
                account.apiKey,
                baseUrlOverride = if (account.isCustom) account.customBaseUrl.takeIf { it.isNotBlank() } else null,
                proxy = prefs.dictate.dictateProxyConfig(),
            )
            client.transcribe(request).text.trim()
        }
    }

    private fun presetFor(account: ProviderAccount) = when {
        account.isCustom -> ProviderRegistry.custom(account.customBaseUrl)
        else -> ProviderRegistry.byId(account.providerId) ?: ProviderRegistry.OPENAI
    }
}
