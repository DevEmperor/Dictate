/*
 * Copyright (C) 2026 The Dictate Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */

package net.devemperor.dictate.wear.ime

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.InputConnection
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
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

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        savedStateRegistryController.performRestore(null)
        super.onCreate()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onCreateInputView(): View {
        // The input view becomes visible; drive the lifecycle to RESUMED so Compose runs.
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
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
    }

    private val actions = WearImeActions(
        commitText = { text -> ic()?.commitText(text, 1) },
        deleteBackward = { ic()?.deleteSurroundingText(1, 0) },
        performEnter = { ic()?.commitText("\n", 1) },
        toggleDictation = { toggleDictation() },
    )

    private fun ic(): InputConnection? = currentInputConnection

    /**
     * Placeholder dictation toggle. Real recording + transcription (tethered via the phone, or
     * standalone when opted in) is wired in P2 of the Wear roadmap. For now this just lets the
     * record button and status line be exercised end to end.
     */
    private fun toggleDictation() {
        dictationState.value = when (dictationState.value) {
            WearDictationState.IDLE -> WearDictationState.RECORDING
            WearDictationState.RECORDING -> {
                // TODO(P2): hand the recorded audio to the tether/standalone transcriber and
                //  commit the returned text instead of this placeholder.
                ic()?.commitText("", 1)
                WearDictationState.IDLE
            }
            else -> WearDictationState.IDLE
        }
    }
}
