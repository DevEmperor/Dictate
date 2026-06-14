/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate.provider

import dev.patrickgold.florisboard.lib.devtools.flogError
import dev.patrickgold.jetpref.datastore.model.PreferenceSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * The per-provider credential record of the keyring (see [ProviderAccounts]).
 *
 * One [ProviderAccount] holds everything needed to talk to a single provider: its API key, the chosen
 * transcription/chat models and – for user-defined endpoints – a base URL and display name. Built-in
 * providers are keyed by their [ProviderRegistry] id ("openai", "groq", …); custom endpoints get a
 * generated `custom:<uuid>` id so the user can keep several of them side by side.
 *
 * Empty model strings mean "use the provider preset's default model". [cachedModels] is the last
 * fetched `/models` catalog (see [LlmProvider.listModels]) so the model picker is instant and works
 * offline; [cachedModelsAt] is its epoch-millis timestamp for staleness checks.
 */
@Serializable
data class ProviderAccount(
    val providerId: String,
    val displayName: String = "",
    val apiKey: String = "",
    val customBaseUrl: String = "",
    val transcriptionModel: String = "",
    val chatModel: String = "",
    val cachedModels: List<String> = emptyList(),
    val cachedModelsAt: Long = 0L,
) {
    /** True once the user has supplied a usable key (or this is a keyless endpoint like Ollama). */
    val hasKey: Boolean
        get() = apiKey.isNotBlank()

    /** True for user-defined endpoints (the legacy singular "custom" id or a "custom:<uuid>" one). */
    val isCustom: Boolean
        get() = providerId == LEGACY_CUSTOM_ID || providerId.startsWith(CUSTOM_PREFIX)

    companion object {
        const val CUSTOM_PREFIX = "custom:"

        /** The single custom-endpoint id the legacy app / early fork used (pre-keyring). */
        const val LEGACY_CUSTOM_ID = "custom"

        /** Mints a fresh, unique id for a user-defined endpoint. */
        fun newCustomId(): String = CUSTOM_PREFIX + UUID.randomUUID().toString().take(8)
    }
}

/**
 * The provider keyring: every configured [ProviderAccount] keyed by provider id. Persisted as a single
 * JetPref `custom` preference (see `AppPrefs.dictate.providerAccounts`) using [Serializer], mirroring
 * the `EmojiHistory` pattern. Switching the active transcription/rewording provider is just a change of
 * the `active*ProviderId` pointer – each provider keeps its own key and models here.
 */
@Serializable
data class ProviderAccounts(
    val accounts: Map<String, ProviderAccount> = emptyMap(),
) {
    operator fun get(providerId: String): ProviderAccount? = accounts[providerId]

    /** Returns the stored account or a fresh empty one for [providerId] (never null). */
    fun getOrEmpty(providerId: String): ProviderAccount =
        accounts[providerId] ?: ProviderAccount(providerId = providerId)

    /** Returns a copy with [account] inserted/replaced under its own id. */
    fun put(account: ProviderAccount): ProviderAccounts =
        copy(accounts = accounts + (account.providerId to account))

    /** Returns a copy with [providerId] removed (used to delete a custom endpoint). */
    fun remove(providerId: String): ProviderAccounts =
        copy(accounts = accounts - providerId)

    /** In-place style edit helper: apply [block] to the account (existing or empty) and store it. */
    fun edit(providerId: String, block: (ProviderAccount) -> ProviderAccount): ProviderAccounts =
        put(block(getOrEmpty(providerId)))

    object Serializer : PreferenceSerializer<ProviderAccounts> {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        override fun serialize(value: ProviderAccounts): String =
            json.encodeToString(value)

        override fun deserialize(value: String): ProviderAccounts = try {
            json.decodeFromString(value)
        } catch (e: Exception) {
            flogError { "Failed to deserialize ProviderAccounts: $e" }
            Empty
        }
    }

    companion object {
        val Empty = ProviderAccounts(emptyMap())
    }
}
