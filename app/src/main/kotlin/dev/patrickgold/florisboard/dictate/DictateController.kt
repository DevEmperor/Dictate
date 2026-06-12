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
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.SystemClock
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.dictate.audio.BluetoothMicRouter
import dev.patrickgold.florisboard.dictate.audio.RecordingController
import dev.patrickgold.florisboard.dictate.provider.DictateApiException
import dev.patrickgold.florisboard.dictate.provider.OpenAiCompatibleClient
import dev.patrickgold.florisboard.dictate.provider.ProviderPreset
import dev.patrickgold.florisboard.dictate.provider.ProviderRegistry
import dev.patrickgold.florisboard.dictate.provider.TranscriptionRequest
import dev.patrickgold.florisboard.editorInstance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Orchestrates the dictation flow that fuses the recording, the provider layer and the editor: tap
 * to record, tap again to transcribe the audio and commit the result into the focused text field.
 *
 * Provider, API key and model are read from the unified JetPref store (`prefs.dictate`), which is
 * seeded once from the legacy Dictate settings on first run (see [dev.patrickgold.florisboard.
 * dictate.data.prefs.DictateLegacyMigrator]) and is editable in the in-app Dictate settings screen.
 *
 * Ported comfort features (all toggleable in the Dictate settings): pause/resume, cancel, audio
 * focus (pause other apps while recording), optional Bluetooth-SCO mic and a transcription retry
 * with a visible indicator.
 *
 * Not yet ported from the legacy service (later refinement): rewording + prompt queue, auto-apply,
 * live prompt, usage tracking, per-language style prompt and language selection.
 */
object DictateController {

    sealed interface UiState {
        data object Idle : UiState
        data class Recording(
            /** [SystemClock.elapsedRealtime] when the current (running) segment started. */
            val startedAtMs: Long,
            /** Elapsed time accumulated across previous, already-finished segments (before pauses). */
            val accumulatedMs: Long = 0L,
            val paused: Boolean = false,
        ) : UiState
        /** [attempt] is 1 for the first try, 2/3/… while retrying after a transient failure. */
        data class Transcribing(val attempt: Int = 1) : UiState
        data class Error(val message: String) : UiState
    }

    private val prefs by FlorisPreferenceStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var recorder: RecordingController? = null
    private var startJob: Job? = null

    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private var btRouter: BluetoothMicRouter? = null

    /** Single entry point for the mic button: starts recording, or stops and transcribes. */
    fun onMicClick(context: Context) {
        when (_state.value) {
            is UiState.Recording -> stopAndTranscribe(context)
            is UiState.Transcribing -> Unit // ignore taps while a request is in flight
            else -> startRecording(context)
        }
    }

    /** Toggles pause/resume of the in-progress recording. No-op outside the recording state. */
    fun togglePause() {
        val current = _state.value as? UiState.Recording ?: return
        val rec = recorder ?: return
        if (current.paused) {
            rec.resume()
            _state.value = current.copy(startedAtMs = SystemClock.elapsedRealtime(), paused = false)
        } else {
            rec.pause()
            val segment = SystemClock.elapsedRealtime() - current.startedAtMs
            _state.value = current.copy(accumulatedMs = current.accumulatedMs + segment, paused = true)
        }
    }

    /** Clears a transient error back to idle (the Smartbar UI calls this after showing it briefly). */
    fun clearError() {
        if (_state.value is UiState.Error) _state.value = UiState.Idle
    }

    /** Aborts an in-progress recording and returns to idle (cancel button / leaving the keyboard). */
    fun cancelRecording() {
        startJob?.cancel()
        startJob = null
        recorder?.cancel()
        recorder = null
        cleanupAudioRouting()
        if (_state.value is UiState.Recording) {
            _state.value = UiState.Idle
        }
    }

    /** Kept for the legacy in-keyboard panel; identical to [cancelRecording]. */
    fun abortRecording() = cancelRecording()

    private fun startRecording(context: Context) {
        if (_state.value is UiState.Recording) return
        val appContext = context.applicationContext
        startJob = scope.launch {
            try {
                requestAudioFocusIfEnabled(appContext)
                val audioSource = setupBluetoothIfEnabled(appContext)
                recorder = RecordingController(appContext).also { it.start(audioSource) }
                _state.value = UiState.Recording(SystemClock.elapsedRealtime())
            } catch (t: Throwable) {
                recorder = null
                cleanupAudioRouting()
                _state.value = UiState.Error(
                    // Most common cause is the missing RECORD_AUDIO permission (granted in onboarding).
                    appContext.getString(R.string.dictate__error_recording_failed, t.message ?: ""),
                )
            }
        }
    }

    private fun stopAndTranscribe(context: Context) {
        val activeRecorder = recorder
        recorder = null
        val audioFile = activeRecorder?.stop()
        cleanupAudioRouting()
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

        _state.value = UiState.Transcribing()
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
                    ),
                    onRetry = { attempt -> _state.value = UiState.Transcribing(attempt) },
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

    private fun requestAudioFocusIfEnabled(context: Context) {
        if (!prefs.dictate.audioFocus.get()) return
        val am = (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).also { audioManager = it }
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS) {
                    // Another app permanently took focus: pause the recording so we don't fight it.
                    val current = _state.value
                    if (current is UiState.Recording && !current.paused) togglePause()
                }
            }
            .build()
        focusRequest = request
        am.requestAudioFocus(request)
    }

    private suspend fun setupBluetoothIfEnabled(context: Context): Int {
        if (!prefs.dictate.useBluetoothMic.get()) return MediaRecorder.AudioSource.MIC
        val router = BluetoothMicRouter(context).also { btRouter = it }
        return if (router.activate()) {
            MediaRecorder.AudioSource.VOICE_COMMUNICATION
        } else {
            MediaRecorder.AudioSource.MIC
        }
    }

    private fun cleanupAudioRouting() {
        focusRequest?.let { request -> audioManager?.abandonAudioFocusRequest(request) }
        focusRequest = null
        audioManager = null
        btRouter?.deactivate()
        btRouter = null
    }

    private fun resolvePreset(): ProviderPreset = when (prefs.dictate.transcriptionProviderId.get()) {
        "groq" -> ProviderRegistry.GROQ
        "custom" -> ProviderRegistry.custom(prefs.dictate.customBaseUrl.get())
        else -> ProviderRegistry.OPENAI
    }

    private fun baseUrlOverrideFor(preset: ProviderPreset): String? =
        if (preset.isCustom) prefs.dictate.customBaseUrl.get().takeIf { it.isNotBlank() } else null
}
