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
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.dictate.audio.RecordingController
import dev.patrickgold.florisboard.dictate.provider.DictateApiException
import dev.patrickgold.florisboard.dictate.provider.OpenAiCompatibleClient
import dev.patrickgold.florisboard.dictate.provider.ProviderPreset
import dev.patrickgold.florisboard.dictate.provider.ProviderRegistry
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
 * Provider, API key and model are read from the unified JetPref store (`prefs.dictate`), which is
 * seeded once from the legacy Dictate settings on first run (see [dev.patrickgold.florisboard.
 * dictate.data.prefs.DictateLegacyMigrator]) and is editable in the in-app Dictate settings screen.
 *
 * Not yet ported from the legacy service (intentionally, later refinement): rewording + prompt
 * queue, auto-apply, live prompt, usage tracking, per-language style prompt, language selection,
 * proxy, Bluetooth mic and audio focus.
 */
object DictateController {

    sealed interface UiState {
        data object Idle : UiState
        data object Recording : UiState
        data object Transcribing : UiState
        data class Error(val message: String) : UiState
    }

    private val prefs by FlorisPreferenceStore

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
                context.getString(R.string.dictate__error_recording_failed, t.message ?: ""),
            )
        }
    }

    private fun stopAndTranscribe(context: Context) {
        val activeRecorder = recorder
        recorder = null
        val audioFile = activeRecorder?.stop()
        if (audioFile == null || !audioFile.exists() || audioFile.length() == 0L) {
            _state.value = UiState.Error(context.getString(R.string.dictate__error_no_audio))
            return
        }

        val apiKey = prefs.dictate.apiKey.get()
        if (apiKey.isBlank()) {
            _state.value = UiState.Error(context.getString(R.string.dictate__error_no_api_key))
            return
        }

        val preset = resolvePreset()
        val model = prefs.dictate.transcriptionModel.get().takeIf { it.isNotBlank() }
            ?: preset.defaultTranscriptionModel
            ?: "gpt-4o-mini-transcribe"

        _state.value = UiState.Transcribing
        val appContext = context.applicationContext
        scope.launch {
            try {
                val client = OpenAiCompatibleClient.from(preset, apiKey, baseUrlOverride = baseUrlOverrideFor(preset))
                val result = client.transcribe(
                    TranscriptionRequest(
                        audioFile = audioFile,
                        model = model,
                        language = null, // auto-detect for now; explicit language selection comes later
                        prompt = null,
                    )
                )
                val editorInstance by appContext.editorInstance()
                editorInstance.commitText(result.text)
                _state.value = UiState.Idle
            } catch (e: DictateApiException) {
                _state.value = UiState.Error(e.message ?: appContext.getString(R.string.dictate__error_transcription_failed))
            } catch (t: Throwable) {
                _state.value = UiState.Error(t.message ?: appContext.getString(R.string.dictate__error_unknown))
            } finally {
                audioFile.delete()
            }
        }
    }

    private fun resolvePreset(): ProviderPreset = when (prefs.dictate.transcriptionProviderId.get()) {
        "groq" -> ProviderRegistry.GROQ
        "custom" -> ProviderRegistry.custom(prefs.dictate.customBaseUrl.get())
        else -> ProviderRegistry.OPENAI
    }

    private fun baseUrlOverrideFor(preset: ProviderPreset): String? =
        if (preset.isCustom) prefs.dictate.customBaseUrl.get().takeIf { it.isNotBlank() } else null
}
