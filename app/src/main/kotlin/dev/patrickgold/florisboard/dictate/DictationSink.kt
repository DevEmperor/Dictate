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
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.text.key.KeyType
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.keyboardManager

/**
 * Where a finished dictation/rewording is written, and how the focused field is read. Abstracts the
 * editor so the same dictation engine ([DictateController]) can output through the keyboard's own
 * InputConnection when Dictate is the active IME, or — for the floating overlay (issue #88) — through
 * an AccessibilityService-injected field, where no InputConnection is available.
 *
 * The IME-backed implementation ([ImeDictationSink]) mirrors the original direct EditorInstance usage
 * exactly, so routing every output through a sink is behavior-neutral for the keyboard path.
 */
interface DictationSink {
    /** Inserts [text] at the cursor, replacing the active selection if any. */
    fun commitText(text: String)

    /** The currently selected text, or empty when nothing is selected. */
    fun selectedText(): String

    /** The full text of the focused field (used by the "rework the whole field" path). */
    fun fullText(): String

    /** Selects the whole field so a subsequent [commitText] replaces its content. */
    fun selectAll()

    /** Presses Enter / triggers the editor action (auto-enter, roadmap 10.1). */
    fun performEnter()

    /**
     * Removes the last inserted [text] from the field again (undo, issue #133). Only deletes when the
     * text immediately before the cursor still matches [text], so the user's own edits are never eaten.
     * Returns true when the field accepted the removal.
     */
    fun deleteLastText(text: String): Boolean
}

/**
 * [DictationSink] backed by the keyboard's own editor — the active-IME path. Resolves the app-wide
 * [dev.patrickgold.florisboard.ime.editor.EditorInstance] and [dev.patrickgold.florisboard.ime.keyboard.KeyboardManager]
 * singletons exactly as the original inline code did, so dictation output is unchanged.
 */
class ImeDictationSink(context: Context) : DictationSink {
    private val appContext = context.applicationContext
    private val editorInstance by appContext.editorInstance()

    override fun commitText(text: String) {
        editorInstance.commitText(text)
    }

    override fun selectedText(): String = editorInstance.activeContent.selectedText

    override fun fullText(): String = editorInstance.activeContent.text

    override fun selectAll() {
        editorInstance.performClipboardSelectAll()
    }

    override fun performEnter() {
        val keyboardManager by appContext.keyboardManager()
        // Dispatches a real Enter key event so it reuses the keyboard's full enter logic (editor action,
        // newline, …) rather than committing a literal "\n".
        keyboardManager.inputEventDispatcher.sendDownUp(EnterKeyData)
    }

    override fun deleteLastText(text: String): Boolean {
        if (text.isEmpty()) return false
        // Only undo when the characters right before the cursor are exactly what we inserted.
        if (!editorInstance.activeContent.textBeforeSelection.endsWith(text)) return false
        val keyboardManager by appContext.keyboardManager()
        // Reuse the keyboard's own delete handling (one backspace per character).
        repeat(text.length) { keyboardManager.inputEventDispatcher.sendDownUp(TextKeyData.DELETE) }
        return true
    }

    private companion object {
        /** Synthetic Enter key dispatched for auto-enter; reuses the keyboard's full enter logic. */
        private val EnterKeyData =
            TextKeyData(type = KeyType.ENTER_EDITING, code = KeyCode.ENTER, label = "enter")
    }
}
