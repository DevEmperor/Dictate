/*
 * Copyright (C) 2026 The Dictate Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */

package net.devemperor.dictate.wear.ime

/**
 * The set of editing actions the Wear keyboard UI can trigger. Implemented by [WearImeService]
 * against the active [android.view.inputmethod.InputConnection]. Keeping this as a plain holder of
 * lambdas keeps the Compose layer free of any IME/InputConnection knowledge.
 */
class WearImeActions(
    val commitText: (String) -> Unit,
    val deleteBackward: () -> Unit,
    val performEnter: () -> Unit,
    /** Toggle dictation recording on/off. The result is committed via [commitText] when it lands. */
    val toggleDictation: () -> Unit,
)

/** High-level state of the voice page, surfaced to the UI for the record button + status line. */
enum class WearDictationState {
    IDLE,
    RECORDING,
    TRANSCRIBING,
    ERROR,
}
