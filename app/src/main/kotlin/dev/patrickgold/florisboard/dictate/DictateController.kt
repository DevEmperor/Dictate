/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate

import android.content.Context
import dev.patrickgold.florisboard.dictate.audio.RecordingController
import dev.patrickgold.florisboard.dictate.data.prefs.DictateLegacyPreferences
import dev.patrickgold.florisboard.dictate.data.prefs.DictateLegacySettings
import dev.patrickgold.florisboard.dictate.provider.DictateApiException
import dev.patrickgold.florisboard.dictate.provider.OpenAiCompatibleClient
import dev.patrickgold.florisboard.dictate.provider.ProviderRegistry
import dev.patrickgold.florisboard.dictate.provider.ProxyConfig
import dev.patrickgold.florisboard.dictate.provider.TranscriptionRequest
import dev.patrickgold.florisboard.editorInstance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Orchestrates the rudimentary dictation flow that fuses the recording, the provider layer and the
 * editor: tap to record, tap again to transcribe the audio and commit the result into the focused
 * text field.
 *
 * Provider, API key and model are taken from the LEGACY Dictate settings for now (see
 * [DictateLegacyPreferences]), so upgrading users dictate with their existing configuration out of
 * the box. The new settings/onboarding layer (roadmap step 6) will replace this source.
 *
 * Not yet ported from the legacy service (intentionally, later refinement): rewording + prompt
 * queue, auto-apply, live prompt, usage tracking, per-language style prompt, language selection,
 * Bluetooth mic and audio focus.
 */
object DictateController {

    sealed interface UiState {
        data object Idle : UiState
        data object Recording : UiState
        data object Transcribing : UiState
        data class Error(val message: String) : UiState
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var recorder: RecordingController? = null

    /** Single entry point for the mic button: starts recording, or stops and transcribes. */
    fun onMicClick(context: Context) {
        when (_state.value) {
            is UiState.Recording -> stopAndTranscribe(context)
            is UiState.Transcribing -> Unit // ignore taps while a request is in flight
            else -> startRecording(context)
        }
    }

    /** Aborts an in-progress recording and returns to idle (e.g. when leaving the panel). */
    fun abortRecording() {
        recorder?.cancel()
        recorder = null
        if (_state.value is UiState.Recording) {
            _state.value = UiState.Idle
        }
    }

    private fun startRecording(context: Context) {
        try {
            recorder = RecordingController(context.applicationContext).also { it.start() }
            _state.value = UiState.Recording
        } catch (t: Throwable) {
            recorder = null
            _state.value = UiState.Error(
                // Most common cause is the missing RECORD_AUDIO permission (granted in onboarding later).
                "Aufnahme fehlgeschlagen – Mikrofon-Berechtigung erteilen. (${t.message})",
            )
        }
    }

    private fun stopAndTranscribe(context: Context) {
        val activeRecorder = recorder
        recorder = null
        val audioFile = activeRecorder?.stop()
        if (audioFile == null || !audioFile.exists() || audioFile.length() == 0L) {
            _state.value = UiState.Error("Keine Aufnahme erkannt – bitte etwas länger sprechen.")
            return
        }

        val settings = DictateLegacyPreferences(context.applicationContext).readSnapshot()
        val apiKey = settings.effectiveTranscriptionApiKey()
        if (apiKey.isNullOrBlank() || apiKey == "NO_API_KEY") {
            _state.value = UiState.Error("Kein API-Schlüssel hinterlegt – in den Einstellungen konfigurieren.")
            return
        }

        _state.value = UiState.Transcribing
        val appContext = context.applicationContext
        scope.launch {
            try {
                val client = buildTranscriptionClient(settings, apiKey)
                val request = TranscriptionRequest(
                    audioFile = audioFile,
                    model = transcriptionModelFor(settings),
                    language = null, // auto-detect for now; explicit language selection comes later
                    prompt = stylePromptFor(settings),
                )
                val result = client.transcribe(request)
                val editorInstance by appContext.editorInstance()
                editorInstance.commitText(result.text)
                _state.value = UiState.Idle
            } catch (e: DictateApiException) {
                _state.value = UiState.Error(e.message ?: "Transkription fehlgeschlagen.")
            } catch (t: Throwable) {
                _state.value = UiState.Error(t.message ?: "Unbekannter Fehler bei der Transkription.")
            } finally {
                audioFile.delete()
            }
        }
    }

    private fun buildTranscriptionClient(
        settings: DictateLegacySettings,
        apiKey: String,
    ): OpenAiCompatibleClient {
        val proxy = if (settings.proxyEnabled) ProxyConfig.parse(settings.proxyHost) else null
        return when (settings.transcriptionProvider) {
            1 -> OpenAiCompatibleClient.from(ProviderRegistry.GROQ, apiKey, proxy = proxy)
            2 -> {
                val host = settings.transcriptionCustomHost.orEmpty()
                OpenAiCompatibleClient.from(ProviderRegistry.custom(host), apiKey, baseUrlOverride = host, proxy = proxy)
            }
            else -> OpenAiCompatibleClient.from(ProviderRegistry.OPENAI, apiKey, proxy = proxy)
        }
    }

    private fun transcriptionModelFor(settings: DictateLegacySettings): String = when (settings.transcriptionProvider) {
        1 -> settings.transcriptionGroqModel?.takeUnless { it.isBlank() } ?: "whisper-large-v3-turbo"
        2 -> settings.transcriptionCustomModel?.takeUnless { it.isBlank() } ?: "whisper-1"
        else -> settings.transcriptionOpenaiModel?.takeUnless { it.isBlank() } ?: "gpt-4o-mini-transcribe"
    }

    /** Style prompt sent to the transcription API. Only the custom-text option is wired up for now. */
    private fun stylePromptFor(settings: DictateLegacySettings): String? =
        settings.stylePromptCustomText?.takeIf { settings.stylePromptSelection == 2 && it.isNotBlank() }
}
