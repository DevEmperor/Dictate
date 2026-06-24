/*
 * Copyright (C) 2026 The Dictate Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */

package net.devemperor.dictate.wear.ime

import android.Manifest
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.InputConnection
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.devemperor.dictate.wear.audio.WearAudioRecorder
import net.devemperor.dictate.wear.sync.WearSettingsStore
import net.devemperor.dictate.wear.transcribe.WearTranscription

/**
 * The Dictate Wear OS keyboard: a lightweight [InputMethodService] hosting a Jetpack Compose UI.
 *
 * This is NOT the FlorisBoard engine — on a watch we want a voice-first input with compact
 * number/emoji fallbacks, so the input view is a small Wear Compose surface.
 *
 * An IME window is not backed by a ComponentActivity, so we implement the ViewTree owners
 * ([LifecycleOwner], [ViewModelStoreOwner], [SavedStateRegistryOwner]) ourselves and attach them
 * to the [ComposeView]; otherwise Compose refuses to compose inside the input view.
 *
 * Transcription wiring (record -> provider/tether -> commit) is deferred to P0/P2 of the Wear
 * roadmap; for now [toggleDictation] flips the visual state so the input surface is testable.
 */
class WearImeService :
    InputMethodService(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    private val dictationState = mutableStateOf(WearDictationState.IDLE)

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val recorder by lazy { WearAudioRecorder(applicationContext) }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        savedStateRegistryController.performRestore(null)
        super.onCreate()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        // Warm the cached settings synced from the phone so the voice page is ready to transcribe.
        WearSettingsStore.load(applicationContext)
    }

    override fun onCreateInputView(): View {
        // The input view becomes visible; drive the lifecycle to RESUMED so Compose runs.
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        // Compose resolves its window recomposer from the IME window's decor view (the root), not from
        // our ComposeView — and the IME wraps our view in its own parentPanel. So the ViewTree owners
        // MUST be set on the decor view, otherwise AbstractComposeView.onAttachedToWindow crashes with
        // "ViewTreeLifecycleOwner not found". We set them on both to be safe.
        window?.window?.decorView?.let { decor ->
            decor.setViewTreeLifecycleOwner(this)
            decor.setViewTreeViewModelStoreOwner(this)
            decor.setViewTreeSavedStateRegistryOwner(this)
        }

        return ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@WearImeService)
            setViewTreeViewModelStoreOwner(this@WearImeService)
            setViewTreeSavedStateRegistryOwner(this@WearImeService)
            setContent {
                WearKeyboard(
                    actions = actions,
                    dictationState = dictationState.value,
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
        if (recorder.isRecording) recorder.cancel()
        scope.cancel()
    }

    private val actions = WearImeActions(
        commitText = { text -> ic()?.commitText(text, 1) },
        deleteBackward = { ic()?.deleteSurroundingText(1, 0) },
        performEnter = { ic()?.commitText("\n", 1) },
        toggleDictation = { toggleDictation() },
    )

    private fun ic(): InputConnection? = currentInputConnection

    /**
     * Voice-page record button. Tap once to start recording, tap again to stop; on stop the audio is
     * transcribed (tethered via the phone by default, or standalone when the user opted in) and the
     * resulting text is committed at the cursor.
     */
    private fun toggleDictation() {
        when (dictationState.value) {
            WearDictationState.RECORDING -> stopAndTranscribe()
            WearDictationState.TRANSCRIBING -> Unit // ignore taps while a transcription is in flight
            else -> startRecording()
        }
    }

    private fun startRecording() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            // The IME can't request permissions; the settings activity grants RECORD_AUDIO.
            dictationState.value = WearDictationState.ERROR
            return
        }
        val started = runCatching { recorder.start() }.isSuccess
        dictationState.value = if (started) WearDictationState.RECORDING else WearDictationState.ERROR
    }

    private fun stopAndTranscribe() {
        val audio = runCatching { recorder.stop() }.getOrNull()
        if (audio == null) {
            dictationState.value = WearDictationState.ERROR
            return
        }
        dictationState.value = WearDictationState.TRANSCRIBING
        scope.launch {
            val text = runCatching {
                withContext(Dispatchers.IO) { WearTranscription.transcribe(applicationContext, audio) }
            }.getOrNull()
            audio.delete()
            if (text.isNullOrBlank()) {
                dictationState.value = WearDictationState.ERROR
            } else {
                ic()?.commitText(text, 1)
                dictationState.value = WearDictationState.IDLE
            }
        }
    }
}
