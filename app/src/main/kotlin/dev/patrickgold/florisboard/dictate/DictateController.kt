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
import android.content.Intent
import android.net.Uri
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.SystemClock
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.dictate.audio.BluetoothMicRouter
import dev.patrickgold.florisboard.dictate.audio.RecordingController
import dev.patrickgold.florisboard.dictate.data.prompts.DictatePromptDefaults
import dev.patrickgold.florisboard.dictate.data.prompts.PromptModel
import dev.patrickgold.florisboard.dictate.data.prompts.PromptsDatabaseHelper
import dev.patrickgold.florisboard.dictate.provider.ChatRequest
import dev.patrickgold.florisboard.dictate.provider.DictateApiException
import dev.patrickgold.florisboard.dictate.provider.OpenAiCompatibleClient
import dev.patrickgold.florisboard.dictate.provider.ProviderPreset
import dev.patrickgold.florisboard.dictate.provider.ProviderRegistry
import dev.patrickgold.florisboard.dictate.provider.TranscriptionRequest
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.text.key.KeyType
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.keyboardManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
 * Rewording (GPT) is wired in: [applyPrompt] runs a prompt on the selection/cursor, [startLivePrompt]
 * sends a spoken instruction to the model, and every transcription runs through [postProcessTranscript]
 * (auto-formatting + auto-apply prompts). The prompt chips that drive these come later (UI phase).
 *
 * Not yet ported from the legacy service (later refinement): usage tracking.
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
        /** A rewording/GPT request is in flight (manual prompt, auto-apply, auto-format or live). */
        data class Rewording(val label: String) : UiState
        /** [canResend] is true when the failed audio was kept so the user can retry it (roadmap 10.3). */
        data class Error(val message: String, val canResend: Boolean = false) : UiState
        /** A one-time rate/donate nudge shown in the Smartbar after enough usage (roadmap 9.7/9.8). */
        data class Promo(val kind: PromoKind) : UiState
    }

    /** Which one-time nudge is being shown (see [maybePromptForReview]). */
    enum class PromoKind { RATE, DONATE }

    private val prefs by FlorisPreferenceStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _prompts = MutableStateFlow<List<PromptModel>>(emptyList())
    /** The user's saved prompts (shared `prompts.db`), refreshed via [refreshPrompts]; drives the Smartbar prompt chips. */
    val prompts: StateFlow<List<PromptModel>> = _prompts.asStateFlow()

    private val _pendingPrompts = MutableStateFlow<List<PromptModel>>(emptyList())
    /**
     * Prompts queued by tapping the always-on prompt row while recording (ROW layout). They are applied
     * in tap order to the finished transcript before it is committed (see [applyPendingPrompts]); the UI
     * highlights every queued prompt in the accent color. Empty whenever no recording queue is active.
     */
    val pendingPrompts: StateFlow<List<PromptModel>> = _pendingPrompts.asStateFlow()

    private var recorder: RecordingController? = null
    private var startJob: Job? = null

    /** The in-flight transcription coroutine, cancellable via the stop button (see [cancelTranscription]). */
    private var transcribeJob: Job? = null

    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private var btRouter: BluetoothMicRouter? = null

    /** When true, the next finished recording is fed to the rewording model instead of committed. */
    private var livePromptArmed = false

    /** Audio kept after a failed transcription so the resend button can retry it (roadmap 10.3). */
    private var lastAudioFile: File? = null

    /** Whether [lastAudioFile] was a live-prompt recording, so a resend repeats the same mode. */
    private var lastWasLive = false

    /** Recorded seconds of [lastAudioFile], re-credited towards the nudges if a resend succeeds. */
    private var lastAudioSeconds = 0L

    /** Synthetic Enter key dispatched for auto-enter (10.1); reuses the keyboard's full enter logic. */
    private val EnterKeyData = TextKeyData(type = KeyType.ENTER_EDITING, code = KeyCode.ENTER, label = "enter")

    /** Cumulative recorded audio (seconds) after which the rate / donate nudges appear (roadmap 9.7/9.8). */
    private const val RATE_THRESHOLD_SECONDS = 180L   // 3 min
    private const val DONATE_THRESHOLD_SECONDS = 300L // 5 min (user choice; legacy used 10 min)

    /** Single entry point for the mic button: starts recording, or stops and transcribes. */
    fun onMicClick(context: Context) {
        when (_state.value) {
            is UiState.Recording -> stopAndTranscribe(context)
            // Tapping the mic while transcribing aborts it (the button shows a stop icon, see the
            // ComputingEvaluator). Rewording stays a no-op.
            is UiState.Transcribing -> cancelTranscription()
            is UiState.Rewording -> Unit
            else -> startRecording(context)
        }
    }

    /**
     * Toggles a prompt in the recording-time queue (ROW layout): while a recording/transcription is in
     * flight, tapping a prompt chip enqueues it (or removes it if already queued) instead of applying it
     * immediately. The queue is applied in tap order to the finished transcript (see [applyPendingPrompts]).
     * No-op outside the recording/transcribing states or for non-persisted prompts.
     */
    fun togglePendingPrompt(prompt: PromptModel) {
        if (_state.value !is UiState.Recording && _state.value !is UiState.Transcribing) return
        if (!prompt.isPersisted()) return
        val current = _pendingPrompts.value
        _pendingPrompts.value = if (current.any { it.id == prompt.id }) {
            current.filterNot { it.id == prompt.id }
        } else {
            current + prompt
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

    /** The active dictation language (defaults to auto-detect); read live from the JetPref store. */
    fun activeLanguage(): DictateLanguage = DictateLanguages.of(prefs.dictate.activeInputLanguage.get())

    /** Advances the active language to the next entry in the user's selected subset (no-op if ≤1). */
    fun cycleLanguage() {
        val selection = DictateLanguages.parseSelection(prefs.dictate.inputLanguages.get())
        if (selection.size <= 1) return
        val currentCode = prefs.dictate.activeInputLanguage.get()
        val idx = selection.indexOfFirst { it.code == currentCode }
        val next = selection[(idx + 1) % selection.size] // idx == -1 (unknown) → starts at index 0
        scope.launch { prefs.dictate.activeInputLanguage.set(next.code) }
    }

    /** Sets the active dictation language explicitly (from the recording bar's language picker). */
    fun setLanguage(code: String) {
        scope.launch { prefs.dictate.activeInputLanguage.set(code) }
    }

    /**
     * Reloads the prompt list from the shared `prompts.db` into [prompts]. Cheap and idempotent;
     * called when the keyboard (re-)appears so the chip strip reflects edits made in the settings.
     */
    fun refreshPrompts(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            _prompts.value = withContext(Dispatchers.IO) { promptsDb(appContext).getAll() }
        }
    }

    /** Clears a transient error back to idle (the Smartbar UI calls this after showing it briefly). */
    fun clearError() {
        if (_state.value is UiState.Error) _state.value = UiState.Idle
    }

    /** Dismisses a resendable error: clears it and drops the kept audio (the resend is declined). */
    fun dismissResend() {
        discardLastAudio()
        clearError()
    }

    /** Aborts an in-progress recording and returns to idle (cancel button / leaving the keyboard). */
    fun cancelRecording() {
        startJob?.cancel()
        startJob = null
        recorder?.cancel()
        recorder = null
        cleanupAudioRouting()
        livePromptArmed = false
        _pendingPrompts.value = emptyList()
        if (_state.value is UiState.Recording) {
            _state.value = UiState.Idle
        }
    }

    /** Kept for the legacy in-keyboard panel; identical to [cancelRecording]. */
    fun abortRecording() = cancelRecording()

    /**
     * Aborts an in-flight transcription (stop button shown on the mic while transcribing). Cancels the
     * network coroutine, drops the audio (handled in the job's finally) and returns to idle. No-op
     * outside the transcribing state, so a tap can never interrupt a rewording request.
     */
    fun cancelTranscription() {
        if (_state.value !is UiState.Transcribing) return
        transcribeJob?.cancel()
        transcribeJob = null
        _pendingPrompts.value = emptyList()
        _state.value = UiState.Idle
    }

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
        // Capture the recorded length before leaving the Recording state, to credit the usage counter
        // that gates the rate/donate nudges (roadmap 9.7/9.8).
        val recordedSeconds = recordedSecondsOf(_state.value)
        val audioFile = activeRecorder?.stop()
        cleanupAudioRouting()
        if (audioFile == null || !audioFile.exists() || audioFile.length() == 0L) {
            _state.value = UiState.Error(context.getString(R.string.dictate__error_no_audio))
            return
        }
        transcribe(context, audioFile, recordedSeconds)
    }

    /** Elapsed recorded seconds of a [UiState.Recording] (running + accumulated), else 0. */
    private fun recordedSecondsOf(state: UiState): Long {
        val rec = state as? UiState.Recording ?: return 0L
        val running = if (rec.paused) 0L else SystemClock.elapsedRealtime() - rec.startedAtMs
        return ((rec.accumulatedMs + running) / 1000L).coerceAtLeast(0L)
    }

    /**
     * Long-press entry point for the mic: hands off to [FileTranscriptionActivity] so the user can
     * pick an existing audio/video file to transcribe instead of recording. The activity stashes the
     * picked file and a pref; [consumePendingFileTranscription] finishes the job once the keyboard
     * regains focus. No-op unless we are idle.
     */
    fun startFileTranscription(context: Context) {
        if (_state.value !is UiState.Idle) return
        val intent = Intent(context, FileTranscriptionActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * Cache directory where [FileTranscriptionActivity] drops a picked file for the IME to pick up.
     * A dedicated directory keeps the handoff file-based (survives the IME process being killed while
     * the file picker is foreground) and unambiguous.
     */
    fun pendingTranscriptionDir(context: Context): File = File(context.cacheDir, "dictate_pending")

    /**
     * Called by the IME when the keyboard (re-)appears on a field: if [FileTranscriptionActivity]
     * stashed a picked file, transcribe it now and commit into the focused field. Returns true if a
     * transcription was started, so the caller can skip instant-recording.
     *
     * Safe to call from multiple lifecycle hooks: the pending file is *claimed* (moved out of the
     * pending dir) before transcription starts, so a second call finds nothing and is a no-op.
     */
    fun consumePendingFileTranscription(context: Context): Boolean {
        if (_state.value !is UiState.Idle) return false
        val pending = pendingTranscriptionDir(context).listFiles()?.firstOrNull { it.isFile && it.length() > 0L }
            ?: return false
        // Claim it: move out of the pending dir so it cannot be picked up twice, then clean the dir.
        val claimed = File(context.cacheDir, "dictate_import_${pending.name}")
        claimed.delete()
        if (!pending.renameTo(claimed)) {
            pending.copyTo(claimed, overwrite = true)
            pending.delete()
        }
        pendingTranscriptionDir(context).deleteRecursively()
        if (!claimed.exists() || claimed.length() == 0L) return false
        transcribe(context, claimed)
        return true
    }

    /**
     * Shared transcription path for both recorded and picked audio: resolves provider/key/model,
     * uploads [audioFile], commits the result and deletes the file afterwards.
     */
    private fun transcribe(context: Context, audioFile: File, recordedSeconds: Long = 0L) {
        val apiKey = prefs.dictate.apiKey.get()
        if (apiKey.isBlank()) {
            _state.value = UiState.Error(context.getString(R.string.dictate__error_no_api_key))
            audioFile.delete()
            return
        }

        val preset = resolvePreset()
        val model = prefs.dictate.transcriptionModel.get().takeIf { it.isNotBlank() }
            ?: preset.defaultTranscriptionModel
            ?: "gpt-4o-mini-transcribe"

        _state.value = UiState.Transcribing()
        val appContext = context.applicationContext
        // Live prompt is consumed by this transcription only (the next recording is normal again).
        val live = livePromptArmed
        livePromptArmed = false
        transcribeJob = scope.launch {
            var keepAudio = false
            try {
                val client = OpenAiCompatibleClient.from(preset, apiKey, baseUrlOverride = baseUrlOverrideFor(preset))
                val result = client.transcribe(
                    TranscriptionRequest(
                        audioFile = audioFile,
                        model = model,
                        // Null for "detect" so the provider auto-detects; otherwise the chosen code.
                        language = prefs.dictate.activeInputLanguage.get()
                            .takeIf { it != DictateLanguages.DETECT },
                        // Style/punctuation prompt biases recognition (roadmap 2.4 / 4.11).
                        prompt = transcriptionStylePrompt(),
                    ),
                    onRetry = { attempt -> _state.value = UiState.Transcribing(attempt) },
                )
                val editorInstance by appContext.editorInstance()
                val finalText = if (live) {
                    // The spoken transcript is an instruction; send it to GPT (optionally operating
                    // on the current selection) and insert the answer instead of the transcript.
                    _pendingPrompts.value = emptyList() // a live prompt ignores any queued prompts
                    _state.value = UiState.Rewording(appContext.getString(R.string.dictate__status_rewording))
                    val selection = editorInstance.activeContent.selectedText.takeIf { it.isNotEmpty() }
                    requestReword(result.text, selection)
                } else {
                    // Normal dictation: auto-formatting + auto-apply prompts, then the prompts the user
                    // queued by tapping the prompt row while recording, in tap order; then commit.
                    val processed = postProcessTranscript(appContext, result.text)
                    applyPendingPrompts(appContext, processed)
                }
                // Output behavior (roadmap 10.1/10.2): instant or typed, then optional auto-enter.
                commitOutput(appContext, finalText)
                discardLastAudio()
                // Credit recorded audio towards the rate/donate gating (roadmap 9.7/9.8).
                if (recordedSeconds > 0L) creditAudioSeconds(recordedSeconds)
                _state.value = UiState.Idle
                // Re-assert the rate/donate nudge: it has no auto-timeout and stays until the user
                // accepts/declines, so if recording temporarily replaced a pending nudge, bring it back.
                maybePromptForReview()
            } catch (c: CancellationException) {
                // User aborted via the stop button: discard quietly (state set by cancelTranscription),
                // never show an error. The audio is dropped in the finally block.
                throw c
            } catch (e: DictateApiException) {
                _pendingPrompts.value = emptyList()
                keepAudio = retainForResend(audioFile, live, recordedSeconds)
                _state.value = UiState.Error(
                    e.message ?: appContext.getString(R.string.dictate__error_transcription_failed),
                    canResend = keepAudio,
                )
            } catch (t: Throwable) {
                _pendingPrompts.value = emptyList()
                keepAudio = retainForResend(audioFile, live, recordedSeconds)
                _state.value = UiState.Error(
                    t.message ?: appContext.getString(R.string.dictate__error_unknown),
                    canResend = keepAudio,
                )
            } finally {
                if (!keepAudio) audioFile.delete()
            }
        }
    }

    // --- Output behavior + resend (roadmap section 10) ------------------------------------------

    /**
     * Commits [text] into the focused field honoring the output prefs: either all at once
     * ([prefs.dictate.instantOutput]) or "typed" character by character at the configured speed, then
     * an optional auto-enter (10.1). Runs on the caller's (Main) coroutine, so the typewriter delay
     * suspends rather than blocks.
     */
    private suspend fun commitOutput(context: Context, text: String) {
        val editorInstance by context.editorInstance()
        if (prefs.dictate.instantOutput.get()) {
            editorInstance.commitText(text)
        } else if (text.isNotEmpty()) {
            val perChar = perCharDelayMs(prefs.dictate.outputSpeed.get())
            text.forEach { ch ->
                editorInstance.commitText(ch.toString())
                delay(perChar)
            }
        }
        if (prefs.dictate.autoEnter.get()) {
            performAutoEnter(context)
        }
    }

    /** Per-character delay for the typewriter output: speed 1 → 100 ms … 5 → 20 ms … 10 → 10 ms (legacy mapping). */
    private fun perCharDelayMs(speed: Int): Long = (100L / speed.coerceIn(1, 10)).coerceAtLeast(1L)

    /** Presses Enter / triggers the editor action by dispatching a real [KeyCode.ENTER] key event. */
    private fun performAutoEnter(context: Context) {
        val keyboardManager by context.keyboardManager()
        keyboardManager.inputEventDispatcher.sendDownUp(EnterKeyData)
    }

    /**
     * Retains [audioFile] (and whether it was a live prompt) for a later resend if the resend button is
     * enabled and the file is usable; returns true when kept. Any previously kept audio is discarded.
     */
    private fun retainForResend(audioFile: File, wasLive: Boolean, recordedSeconds: Long): Boolean {
        if (!prefs.dictate.resendButton.get() || !audioFile.exists() || audioFile.length() == 0L) return false
        if (lastAudioFile != audioFile) discardLastAudio()
        lastAudioFile = audioFile
        lastWasLive = wasLive
        lastAudioSeconds = recordedSeconds
        return true
    }

    /** Deletes the kept resend audio (if any) and forgets it. */
    private fun discardLastAudio() {
        lastAudioFile?.takeIf { it.exists() }?.delete()
        lastAudioFile = null
        lastAudioSeconds = 0L
    }

    /**
     * Retries the last failed recording with the same audio (roadmap 10.3). No-op unless we are idle or
     * showing an error and a usable kept audio exists.
     */
    fun resendLastAudio(context: Context) {
        if (_state.value !is UiState.Error && _state.value !is UiState.Idle) return
        val audio = lastAudioFile
        if (audio == null || !audio.exists() || audio.length() == 0L) {
            lastAudioFile = null
            return
        }
        // Repeat the original mode so a failed live prompt resends as a live prompt.
        livePromptArmed = lastWasLive
        transcribe(context, audio, lastAudioSeconds)
    }

    // --- Rate / Donate nudges (roadmap 9.7/9.8) -------------------------------------------------

    /**
     * Shows a one-time rate or donate nudge in the Smartbar once the user has accumulated enough
     * transcribed audio, mirroring the legacy app: rate after [RATE_THRESHOLD_SECONDS], donate after
     * [DONATE_THRESHOLD_SECONDS]. Each is shown until acted on (a flag is then set); accepting or
     * declining donate also marks rate as done, so a donor is never asked to rate. No-op unless idle.
     * Called when the keyboard appears so it never interrupts an in-flight recording/transcription.
     */
    fun maybePromptForReview() {
        if (_state.value !is UiState.Idle) return
        val total = prefs.dictate.totalAudioSeconds.get()
        val kind = when {
            total > DONATE_THRESHOLD_SECONDS && !prefs.dictate.hasDonated.get() -> PromoKind.DONATE
            total > RATE_THRESHOLD_SECONDS && total <= DONATE_THRESHOLD_SECONDS && !prefs.dictate.hasRated.get() -> PromoKind.RATE
            else -> return
        }
        _state.value = UiState.Promo(kind)
    }

    /** Opens the Play Store / PayPal page for the active promo and marks it done. No-op otherwise. */
    fun acceptPromo(context: Context) {
        val kind = (_state.value as? UiState.Promo)?.kind ?: return
        val url = when (kind) {
            PromoKind.RATE -> "https://play.google.com/store/apps/details?id=net.devemperor.dictate"
            PromoKind.DONATE -> "https://paypal.me/DevEmperor"
        }
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        markPromoDone(kind)
        _state.value = UiState.Idle
    }

    /** Dismisses the active promo without opening anything, but still marks it done. No-op otherwise. */
    fun declinePromo() {
        val kind = (_state.value as? UiState.Promo)?.kind ?: return
        markPromoDone(kind)
        _state.value = UiState.Idle
    }

    /** Persists the "handled" flags: declining/accepting donate also marks rate as done. */
    private fun markPromoDone(kind: PromoKind) {
        scope.launch {
            when (kind) {
                PromoKind.RATE -> prefs.dictate.hasRated.set(true)
                PromoKind.DONATE -> {
                    prefs.dictate.hasDonated.set(true)
                    prefs.dictate.hasRated.set(true)
                }
            }
        }
    }

    /**
     * Adds [seconds] to the cumulative audio counter that gates the nudges. Suspends until the new value
     * is written to the in-memory cache, so a [maybePromptForReview] called right after sees the update.
     */
    private suspend fun creditAudioSeconds(seconds: Long) {
        prefs.dictate.totalAudioSeconds.set(prefs.dictate.totalAudioSeconds.get() + seconds)
    }

    // --- Rewording / GPT engine (roadmap section 4) ---------------------------------------------

    /**
     * Applies a rewording [prompt] and commits the result. Used by the prompt chips (Phase 3):
     *  - a `[snippet]` prompt (text wrapped in brackets) is inserted literally, no API call;
     *  - a `requiresSelection` prompt operates on [selectionOverride] (or the current selection) and
     *    replaces it with the reworded result;
     *  - a free prompt generates from the instruction alone and inserts at the cursor.
     * No-op unless idle (or recovering from a transient error).
     */
    fun applyPrompt(context: Context, prompt: PromptModel, selectionOverride: String? = null) {
        if (_state.value !is UiState.Idle && _state.value !is UiState.Error) return
        val appContext = context.applicationContext
        val editorInstance by appContext.editorInstance()
        val raw = prompt.prompt.orEmpty()

        // Snippet shortcut: text wrapped in [...] is inserted literally (no network call).
        if (raw.length >= 2 && raw.startsWith("[") && raw.endsWith("]")) {
            editorInstance.commitText(raw.substring(1, raw.length - 1))
            return
        }

        val input: String? = when {
            selectionOverride != null -> selectionOverride
            !prompt.requiresSelection -> null
            else -> {
                val content = editorInstance.activeContent
                val selected = content.selectedText
                if (selected.isNotEmpty()) {
                    selected
                } else {
                    // Nothing selected: select the whole field (so the user sees what gets reworded)
                    // and operate on its full text. The reworded result then replaces the now-selected
                    // content via commitText. Matches the "tap a prompt with no selection" flow.
                    val whole = content.text
                    if (whole.isBlank()) return // empty field – nothing to operate on
                    editorInstance.performClipboardSelectAll()
                    whole
                }
            }
        }

        if (rewordingApiKey().isBlank()) {
            _state.value = UiState.Error(appContext.getString(R.string.dictate__error_no_api_key))
            return
        }

        _state.value = UiState.Rewording(prompt.name ?: appContext.getString(R.string.dictate__status_rewording))
        scope.launch {
            try {
                val text = requestReword(raw, input)
                // commitText replaces the active selection if any, else inserts at the cursor.
                editorInstance.commitText(text)
                _state.value = UiState.Idle
            } catch (e: DictateApiException) {
                _state.value = UiState.Error(e.message ?: appContext.getString(R.string.dictate__error_rewording_failed))
            } catch (t: Throwable) {
                _state.value = UiState.Error(t.message ?: appContext.getString(R.string.dictate__error_rewording_failed))
            }
        }
    }

    /**
     * Starts (or stops) a *live prompt* recording: the spoken transcript is sent to the rewording
     * model as an instruction instead of being inserted verbatim. Toggles like the mic button.
     */
    fun startLivePrompt(context: Context) {
        when (_state.value) {
            is UiState.Recording -> {
                livePromptArmed = true
                stopAndTranscribe(context)
            }
            is UiState.Transcribing, is UiState.Rewording -> Unit
            else -> {
                livePromptArmed = true
                startRecording(context)
            }
        }
    }

    /**
     * Runs the post-transcription rewording chain on [transcript]: optional auto-formatting, then the
     * user's auto-apply prompts in order. Each step is best-effort – a failing step keeps the text so
     * far so the user never loses their dictation. Returns the text to commit.
     */
    private suspend fun postProcessTranscript(context: Context, transcript: String): String {
        if (!prefs.dictate.rewordingEnabled.get() || transcript.isBlank()) return transcript
        // No rewording key (not even a shared transcription one) → nothing here can run; return the raw
        // transcript instead of flashing "Formatting…" and looping through doomed throw/catch calls.
        if (rewordingApiKey().isBlank()) return transcript
        var text = transcript

        // 1) Auto-formatting (spoken cues → Markdown). Low-level prompt, no be-precise suffix.
        if (prefs.dictate.autoFormattingEnabled.get()) {
            _state.value = UiState.Rewording(context.getString(R.string.dictate__status_formatting))
            // Hint the model with the readable language name ("German"), or "unknown" for auto-detect.
            val languageName = DictateLanguages.englishNameFor(prefs.dictate.activeInputLanguage.get())
            val formatPrompt = DictatePromptDefaults.buildAutoFormattingPrompt(languageName, text)
            text = runCatching { requestRewordRaw(formatPrompt).ifBlank { text } }.getOrDefault(text)
        }

        // 2) Auto-apply prompts, in POS order; each operates on the running text if it needs input.
        val autoApply = withContext(Dispatchers.IO) {
            promptsDb(context).getAll().filter { it.autoApply }
        }
        for (p in autoApply) {
            val instruction = p.prompt.orEmpty()
            if (instruction.isBlank()) continue
            _state.value = UiState.Rewording(p.name ?: context.getString(R.string.dictate__status_rewording))
            text = runCatching {
                requestReword(instruction, if (p.requiresSelection) text else null)
            }.getOrDefault(text)
        }
        return text
    }

    /**
     * Applies the prompts the user queued by tapping the always-on prompt row while recording (ROW
     * layout), in tap order, to the finished [text]. Each step is best-effort (a failing prompt keeps
     * the text so far). `[snippet]` prompts are appended literally; everything else runs through the
     * rewording model (operating on the running text when the prompt requires a selection). Clears the
     * queue (so the highlights disappear) regardless of outcome.
     */
    private suspend fun applyPendingPrompts(context: Context, text: String): String {
        val queued = _pendingPrompts.value
        _pendingPrompts.value = emptyList()
        if (queued.isEmpty()) return text
        if (rewordingApiKey().isBlank()) return text
        var result = text
        for (p in queued) {
            val raw = p.prompt.orEmpty()
            if (raw.isBlank()) continue
            // Snippet shortcut: text wrapped in [...] is appended literally (no network call).
            if (raw.length >= 2 && raw.startsWith("[") && raw.endsWith("]")) {
                result += raw.substring(1, raw.length - 1)
                continue
            }
            _state.value = UiState.Rewording(p.name ?: context.getString(R.string.dictate__status_rewording))
            result = runCatching {
                requestReword(raw, if (p.requiresSelection) result else null)
            }.getOrDefault(result)
        }
        return result
    }

    /**
     * High-level rewording call: builds the user message as `instruction [+ system prompt] [+ input]`
     * (exactly as the legacy app did – the be-precise prompt is tuned for this position) and returns
     * the trimmed model output.
     */
    private suspend fun requestReword(instruction: String, input: String?): String {
        val sys = systemPrompt()
        val content = buildString {
            append(instruction)
            if (sys.isNotBlank()) append("\n\n").append(sys)
            if (!input.isNullOrBlank()) append("\n\n").append(input)
        }
        return requestRewordRaw(content)
    }

    /** Low-level rewording call: sends [userContent] verbatim as a single user message. */
    private suspend fun requestRewordRaw(userContent: String): String {
        val apiKey = rewordingApiKey()
        if (apiKey.isBlank()) {
            throw DictateApiException(DictateApiException.Kind.INVALID_API_KEY, "No API key set")
        }
        val preset = resolveRewordingPreset()
        val model = prefs.dictate.rewordingModel.get().ifBlank { preset.defaultChatModel ?: "gpt-4o-mini" }
        val client = OpenAiCompatibleClient.from(preset, apiKey, baseUrlOverride = rewordingBaseUrlOverrideFor(preset))
        return client.complete(ChatRequest.ofUser(model, userContent)).text.trim()
    }

    private fun systemPrompt(): String = when (prefs.dictate.systemPromptSelection.get()) {
        DictatePromptDefaults.SELECTION_PREDEFINED -> DictatePromptDefaults.REWORDING_BE_PRECISE
        DictatePromptDefaults.SELECTION_CUSTOM -> prefs.dictate.systemPromptCustom.get()
        else -> ""
    }

    /** Style/punctuation prompt sent with the transcription request (independent of rewording). */
    private fun transcriptionStylePrompt(): String? = when (prefs.dictate.stylePromptSelection.get()) {
        DictatePromptDefaults.SELECTION_PREDEFINED ->
            DictatePromptDefaults.punctuationPromptFor(prefs.dictate.activeInputLanguage.get())
        DictatePromptDefaults.SELECTION_CUSTOM ->
            prefs.dictate.stylePromptCustom.get().takeIf { it.isNotBlank() }
        else -> null
    }

    private fun resolveRewordingPreset(): ProviderPreset {
        val id = prefs.dictate.rewordingProviderId.get()
        return if (id == "custom") {
            ProviderRegistry.custom(prefs.dictate.rewordingCustomBaseUrl.get())
        } else {
            ProviderRegistry.byId(id) ?: ProviderRegistry.OPENAI
        }
    }

    /** Rewording key, falling back to the shared transcription key when not set separately. */
    private fun rewordingApiKey(): String =
        prefs.dictate.rewordingApiKey.get().ifBlank { prefs.dictate.apiKey.get() }

    private fun rewordingBaseUrlOverrideFor(preset: ProviderPreset): String? =
        if (preset.isCustom) prefs.dictate.rewordingCustomBaseUrl.get().takeIf { it.isNotBlank() } else null

    private fun promptsDb(context: Context) = PromptsDatabaseHelper(context.applicationContext)

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
