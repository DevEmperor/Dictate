/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import dev.patrickgold.florisboard.lib.devtools.flogDebug
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Optional accessibility service that powers the floating dictation button (issue #88). It does two
 * things the keyboard cannot do from outside an active IME:
 *
 *  1. **Detect** when an editable text field holds input focus in *any* app, so the floating button can
 *     appear only when there is somewhere to dictate into ([editableFocused]).
 *  2. **Inject** the transcribed text into that focused field ([injectText]) — the equivalent of the
 *     IME's `commitText`, but driven from the overlay where no InputConnection exists.
 *
 * The service is entirely opt-in: it does nothing until the user enables both the floating-button
 * feature and this service in the system accessibility settings. It only ever reads the *focused*
 * field (to know it is editable and to place text at the cursor); it does not collect screen content.
 *
 * Slice 2 of the overlay work: detection + injection only. The bubble UI, the recording/transcription
 * wiring (via a [dev.patrickgold.florisboard.dictate.DictationSink]) and the settings come in later slices.
 */
class DictateAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        flogDebug { "DictateAccessibilityService connected" }
        updateEditableFocus()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        clearInstance()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        clearInstance()
        super.onDestroy()
    }

    override fun onInterrupt() {
        // No ongoing feedback to interrupt.
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        when (event?.eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            -> updateEditableFocus()
        }
    }

    /** The currently input-focused node if it is an editable text field, else null. */
    private fun focusedEditableNode(): AccessibilityNodeInfo? {
        val node = findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return null
        return node.takeIf { it.isEditable }
    }

    private fun updateEditableFocus() {
        val focused = focusedEditableNode() != null
        if (_editableFocused.value != focused) {
            _editableFocused.value = focused
            flogDebug { "editable field focused = $focused" }
        }
    }

    /**
     * Inserts [text] into the focused editable field at the cursor, replacing the active selection
     * (matching the IME `commitText` semantics) and placing the cursor right after the inserted text.
     * Falls back to appending at the end when the field reports no usable selection. Returns true when
     * the field accepted the change — some custom/legacy views do not support `ACTION_SET_TEXT`.
     */
    private fun commitTextIntoFocused(text: String): Boolean {
        val node = focusedEditableNode() ?: return false
        node.refresh()
        val existing = node.text?.toString() ?: ""
        val from = node.textSelectionStart.coerceForText(existing)
        val to = node.textSelectionEnd.coerceForText(existing)
        val start = minOf(from, to)
        val end = maxOf(from, to)
        val newText = existing.substring(0, start) + text + existing.substring(end)
        val setArgs = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
        }
        val ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setArgs)
        if (ok) {
            val cursor = start + text.length
            val selArgs = Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, cursor)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, cursor)
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selArgs)
        }
        flogDebug { "commitTextIntoFocused len=${text.length} ok=$ok" }
        return ok
    }

    /** Clamps a selection index into [0, length]; a missing index (-1) maps to the end (append). */
    private fun Int.coerceForText(text: String): Int =
        if (this in 0..text.length) this else text.length

    private fun clearInstance() {
        if (instance === this) {
            instance = null
            _editableFocused.value = false
            flogDebug { "DictateAccessibilityService disconnected" }
        }
    }

    companion object {
        @Volatile
        private var instance: DictateAccessibilityService? = null

        /** Whether the service is connected and able to detect focus / inject text. */
        val isRunning: Boolean
            get() = instance != null

        private val _editableFocused = MutableStateFlow(false)

        /** Whether an editable text field currently holds input focus anywhere on screen. */
        val editableFocused: StateFlow<Boolean> = _editableFocused.asStateFlow()

        /**
         * Inserts [text] into the focused editable field via the running service, returning true on
         * success. Returns false when the service is not running or no editable field is focused.
         */
        fun injectText(text: String): Boolean = instance?.commitTextIntoFocused(text) ?: false
    }
}
