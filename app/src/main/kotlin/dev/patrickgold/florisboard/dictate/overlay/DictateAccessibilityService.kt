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
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import dev.patrickgold.florisboard.R
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
 * It also owns the floating bubble ([DictateBubbleController]) and promotes itself to a microphone
 * foreground service while a bubble-driven dictation records, so background mic capture is allowed.
 */
class DictateAccessibilityService : AccessibilityService() {

    private var bubble: DictateBubbleController? = null
    private var isForeground = false
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        flogDebug { "DictateAccessibilityService connected" }
        createNotificationChannel()
        bubble = DictateBubbleController(this).also { it.start() }
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
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            -> updateEditableFocus()
        }
    }

    /** The currently input-focused node if it is an editable text field, else null. */
    private fun focusedEditableNode(): AccessibilityNodeInfo? {
        val node = findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return null
        if (node.isLikelyEditable()) return node
        // findFocus sometimes returns a container that merely *holds* the editable view (common in
        // wrapped/cross-platform UIs); descend to the first editable descendant.
        return findEditableDescendant(node, 0)
    }

    /** Depth-first search under [node] for the first editable descendant, bounded to avoid deep trees. */
    private fun findEditableDescendant(node: AccessibilityNodeInfo, depth: Int): AccessibilityNodeInfo? {
        if (depth >= MAX_EDITABLE_SEARCH_DEPTH) return null
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (child.isLikelyEditable()) return child
            findEditableDescendant(child, depth + 1)?.let { return it }
        }
        return null
    }

    /**
     * A node we should treat as a dictation target. [isEditable] is the canonical flag, but several apps
     * never set it on otherwise-editable fields; fall back to the EditText class hierarchy and to the
     * field advertising the text-editing actions, so detection is not limited to the few well-behaved apps.
     */
    private fun AccessibilityNodeInfo.isLikelyEditable(): Boolean {
        if (isEditable) return true
        if (className?.toString()?.contains("EditText") == true) return true
        val actions = actionList
        return actions.contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_TEXT) &&
            actions.contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_SELECTION)
    }

    /**
     * Whether a soft keyboard (any IME) is currently shown on screen. This is the most reliable proxy for
     * "a keyboard would normally be extended here", independent of whether the focused field reports itself
     * as editable, so the bubble appears in the same situations a keyboard does. Requires
     * `flagRetrieveInteractiveWindows`, which the service config sets.
     */
    private fun isImeWindowShown(): Boolean = runCatching {
        windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
    }.getOrDefault(false)

    private fun updateEditableFocus() {
        // Show the bubble whenever there is somewhere to dictate: either an editable field holds focus, or a
        // soft keyboard is physically out (covers apps whose fields don't report an accessible editable focus).
        val imeShown = isImeWindowShown()
        val focused = focusedEditableNode() != null || imeShown
        if (_editableFocused.value != focused) {
            _editableFocused.value = focused
            flogDebug { "editable field focused = $focused" }
        }
        if (_imeVisible.value != imeShown) {
            _imeVisible.value = imeShown
            flogDebug { "IME window visible = $imeShown" }
        }
        val dictateKeyboard = isDictateKeyboardActive()
        if (_dictateKeyboardActive.value != dictateKeyboard) {
            _dictateKeyboardActive.value = dictateKeyboard
            flogDebug { "Dictate keyboard active = $dictateKeyboard" }
        }
        val pkg = currentAppPackage()
        if (!pkg.isNullOrEmpty() && pkg != packageName && _foregroundPackage.value != pkg) {
            _foregroundPackage.value = pkg
            flogDebug { "foreground app = $pkg" }
        }
    }

    /**
     * The package of the foreground *application* window (ignoring IME/system windows), for per-app bubble
     * positioning. Reading it from the focused application window avoids the churn of TYPE_WINDOW_STATE_CHANGED
     * events that fire for the keyboard and transient popups with their own package names.
     */
    private fun currentAppPackage(): String? = runCatching {
        val fromAppWindow = windows
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .sortedByDescending { it.isFocused }
            .firstOrNull()
            ?.root?.packageName?.toString()
        fromAppWindow ?: rootInActiveWindow?.packageName?.toString()
    }.getOrNull()

    /** Whether the Dictate keyboard itself is the currently selected input method (handles .debug). */
    private fun isDictateKeyboardActive(): Boolean {
        val current = Settings.Secure.getString(
            contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD,
        ) ?: return false
        // DEFAULT_INPUT_METHOD is "<package>/<service-class>"; the package is our applicationId.
        return current.substringBefore('/') == packageName
    }

    /**
     * Inserts [text] into the focused editable field at the cursor, replacing the active selection
     * (matching the IME `commitText` semantics) and placing the cursor right after the inserted text.
     * Falls back to appending at the end when the field reports no usable selection. Returns true when
     * the field accepted the change — some custom/legacy views do not support `ACTION_SET_TEXT`.
     */
    private fun commitTextIntoFocused(text: String): Boolean {
        // Resolve the target field freshly. The accessibility input focus (and its input connection) can
        // go stale when the host app recreates its field — e.g. WhatsApp clearing the composer on send —
        // and a stale binding silently swallows the commit, so the green check showed but no text appeared
        // (#132 follow-up). We therefore trust the input-connection path only while the focused node is
        // still a *live* node; otherwise we re-locate the field from the currently visible window and write
        // straight into that, so the text always lands in the field the user actually sees, even after the
        // service has been running a long time.
        val focused = focusedEditableNode()
        val focusLive = focused != null && runCatching { focused.refresh() }.getOrDefault(false)

        // 1. Live focus → the accessibility input connection (cleanest: WebView-safe, no clipboard toast).
        if (focusLive && commitViaInputConnection(text)) {
            flogDebug { "commit via inputConnection (live focus) len=${text.length}" }
            return true
        }

        // 2. Node-based write into the actual on-screen field. Prefer the live focused node; if it was
        //    stale or missing, take the editable field from the currently active window (the real one).
        val target = (if (focusLive) focused else null) ?: activeWindowEditable()
        if (target != null) {
            runCatching { target.refresh() }
            if (setTextOnFocused(target, text)) {
                flogDebug { "commit via setText len=${text.length}" }
                return true
            }
            if (pasteIntoFocused(target, text)) {
                flogDebug { "commit via paste len=${text.length}" }
                return true
            }
        }

        // 3. Last resort: the input connection even when no live focused node was found — covers apps that
        //    expose an editor connection without an accessible focused node.
        if (!focusLive && commitViaInputConnection(text)) {
            flogDebug { "commit via inputConnection (fallback) len=${text.length}" }
            return true
        }
        return false
    }

    /**
     * The editable field in the currently active (visible) window, located fresh from the live node tree
     * via [rootInActiveWindow]. Used when the cached input focus is stale — e.g. the host app recreated
     * its input after sending a message — so dictation lands in the field the user actually sees rather
     * than a detached one (#132 follow-up).
     */
    private fun activeWindowEditable(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.let { focused ->
            if (focused.isLikelyEditable()) return focused
            findEditableDescendant(focused, 0)?.let { return it }
        }
        if (root.isLikelyEditable()) return root
        return findEditableDescendant(root, 0)
    }

    /**
     * Commits [text] through the accessibility [android.accessibilityservice.InputMethod] input connection
     * (API 33+). Requires the `flagInputMethodEditor` accessibility flag. Returns false when unavailable
     * (older OS, or no editor currently bound), so the caller falls back to the node-based methods.
     */
    private fun commitViaInputConnection(text: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val connection = inputMethod?.currentInputConnection ?: return false
        return runCatching {
            connection.commitText(text, 1, null)
            true
        }.getOrDefault(false)
    }

    /**
     * Inserts [text] via [AccessibilityNodeInfo.ACTION_SET_TEXT], reconstructing the field content around the
     * cursor/selection. A shown placeholder is treated as empty (see [editableText]) so it is not prepended.
     * Returns false when the field does not accept the action, so the caller can fall back to pasting.
     */
    private fun setTextOnFocused(node: AccessibilityNodeInfo, text: String): Boolean {
        val existing = node.editableText()
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
        return ok
    }

    /**
     * Inserts [text] by putting it on the clipboard and performing [AccessibilityNodeInfo.ACTION_PASTE] on
     * the focused field, then restoring the user's previous clipboard shortly after. Returns false when the
     * field does not advertise the paste action, so the caller can fall back to ACTION_SET_TEXT.
     *
     * Pasting inserts into the field's real content (so a shown placeholder is never prepended) and works in
     * WebView/browser inputs that ignore ACTION_SET_TEXT. We cannot verify the write by reading the clipboard
     * back (a background app's clipboard read is blocked on Android 10+ and returns null), so we trust the
     * write; if it were blocked the field simply would not receive our text and the user would re-try.
     */
    private fun pasteIntoFocused(node: AccessibilityNodeInfo, text: String): Boolean {
        if (!node.actionList.contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_PASTE)) return false
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return false
        val previous = runCatching { clipboard.primaryClip }.getOrNull()
        runCatching { clipboard.setPrimaryClip(ClipData.newPlainText("dictate", text)) }
        val ok = node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        if (ok) {
            // Restore the previous clipboard once the target app has consumed the paste, so we do not
            // clobber whatever the user had copied.
            mainHandler.postDelayed({
                runCatching {
                    clipboard.setPrimaryClip(previous ?: ClipData.newPlainText("", ""))
                }
            }, CLIPBOARD_RESTORE_DELAY_MS)
        }
        return ok
    }

    /**
     * Removes the last inserted [text] from the focused field again (undo, issue #133). Prefers the
     * accessibility input connection (API 33+) when the characters right before the cursor are exactly
     * [text]; otherwise removes the matching region — the window ending at the cursor if it matches,
     * else the last occurrence — via [AccessibilityNodeInfo.ACTION_SET_TEXT]. Returns true on success.
     */
    private fun deleteLastTextFromFocused(text: String): Boolean {
        if (text.isEmpty()) return false
        // Reconstruct the field without the inserted text via ACTION_SET_TEXT (works pre-API-33 too and
        // lets us verify the match first, so the user's own edits are never eaten).
        val node = focusedEditableNode() ?: return false
        node.refresh()
        val existing = node.editableText()
        val cursor = node.textSelectionEnd.coerceForText(existing)
        val start = when {
            cursor >= text.length && existing.regionMatches(cursor - text.length, text, 0, text.length) ->
                cursor - text.length
            existing.contains(text) -> existing.lastIndexOf(text)
            else -> return false
        }
        val newText = existing.removeRange(start, start + text.length)
        val setArgs = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
        }
        if (!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setArgs)) return false
        val selArgs = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, start)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, start)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selArgs)
        flogDebug { "deleteLastText via setText len=${text.length}" }
        return true
    }

    /** The selected text in the focused editable field, or empty when nothing is selected. */
    private fun selectedTextOfFocused(): String {
        val node = focusedEditableNode() ?: return ""
        node.refresh()
        val text = node.editableText()
        if (text.isEmpty()) return ""
        val from = node.textSelectionStart
        val to = node.textSelectionEnd
        if (from < 0 || to < 0 || from == to) return ""
        val start = minOf(from, to).coerceIn(0, text.length)
        val end = maxOf(from, to).coerceIn(0, text.length)
        return text.substring(start, end)
    }

    /** The full text of the focused editable field, or empty when there is none. */
    private fun fullTextOfFocused(): String {
        val node = focusedEditableNode() ?: return ""
        node.refresh()
        return node.editableText()
    }

    /** Selects the whole field so a subsequent inject replaces it. Returns true on success. */
    private fun selectAllInFocused(): Boolean {
        val node = focusedEditableNode() ?: return false
        node.refresh()
        val len = node.editableText().length
        val args = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, len)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, args)
    }

    /**
     * The field's real text, treating a shown hint/placeholder (e.g. WhatsApp's "Message") as empty so
     * the injected text never gets prepended to the placeholder.
     *
     * [AccessibilityNodeInfo.getText] returns the hint verbatim for an empty field. The documented way to
     * tell them apart is [isShowingHintText], but it is not reliable across apps — WhatsApp's compose field
     * returns its "Message" placeholder as `text` *without* setting the flag. So we additionally treat the
     * text as empty when it is identical to the node's declared [getHintText]; the (self-correcting) cost
     * is that a field whose real content exactly equals its placeholder is seen as empty.
     */
    private fun AccessibilityNodeInfo.editableText(): String {
        if (isShowingHintText) return ""
        val raw = text?.toString() ?: ""
        if (raw.isEmpty()) return ""
        // Treat the text as empty when it merely echoes the declared hint/placeholder (some apps return the
        // placeholder as text without setting isShowingHintText). Only hintText is used here — matching
        // against contentDescription is unsafe because some apps mirror the real content there, which would
        // make us drop existing text when appending.
        val hint = hintText?.toString()?.trim()
        if (!hint.isNullOrEmpty() && hint == raw.trim()) return ""
        return raw
    }

    /**
     * Presses the editor action / Enter on the focused field (auto-enter). Uses the proper IME-enter
     * action on Android 11+; on older releases there is no editor-action equivalent, so it falls back
     * to inserting a newline.
     */
    private fun performEnterOnFocused(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val node = focusedEditableNode() ?: return false
            node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
        } else {
            commitTextIntoFocused("\n")
        }
    }

    /** Clamps a selection index into [0, length]; a missing index (-1) maps to the end (append). */
    private fun Int.coerceForText(text: String): Int =
        if (this in 0..text.length) this else text.length

    private fun clearInstance() {
        if (instance === this) {
            instance = null
            _editableFocused.value = false
            _dictateKeyboardActive.value = false
            _imeVisible.value = false
            bubble?.destroy()
            bubble = null
            mainHandler.removeCallbacksAndMessages(null)
            stopMicForeground()
            flogDebug { "DictateAccessibilityService disconnected" }
        }
    }

    // --- Microphone foreground (while-in-background recording, Android 14+) ----------------------

    /**
     * Promotes the (already running, system-bound) service to a microphone foreground service so the
     * recording started from the floating button is allowed while the app is in the background. Promoting
     * an existing service sidesteps the "start a foreground service from the background" restriction.
     */
    fun startMicForeground() {
        if (isForeground) return
        val notification = buildNotification(getString(R.string.dictate__overlay_notification_recording))
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIF_ID, notification)
            }
            isForeground = true
        }
    }

    /** Drops the microphone foreground state once the dictation has finished. */
    fun stopMicForeground() {
        if (!isForeground) return
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        isForeground = false
    }

    private fun buildNotification(text: String): Notification {
        return Notification.Builder(this, NOTIF_CHANNEL)
            .setSmallIcon(R.drawable.ic_dictate_overlay_mic)
            .setContentTitle(getString(R.string.floris_app_name))
            .setContentText(text)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(NOTIF_CHANNEL) != null) return
        val channel = NotificationChannel(
            NOTIF_CHANNEL,
            getString(R.string.dictate__overlay_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val NOTIF_ID = 0xD1C7
        private const val NOTIF_CHANNEL = "dictate_overlay_recording"
        private const val CLIPBOARD_RESTORE_DELAY_MS = 400L
        private const val MAX_EDITABLE_SEARCH_DEPTH = 6

        @Volatile
        private var instance: DictateAccessibilityService? = null

        /** Whether the service is connected and able to detect focus / inject text. */
        val isRunning: Boolean
            get() = instance != null

        private val _editableFocused = MutableStateFlow(false)

        /** Whether an editable text field currently holds input focus anywhere on screen. */
        val editableFocused: StateFlow<Boolean> = _editableFocused.asStateFlow()

        private val _dictateKeyboardActive = MutableStateFlow(false)

        /** Whether the Dictate keyboard is the currently selected input method. */
        val dictateKeyboardActive: StateFlow<Boolean> = _dictateKeyboardActive.asStateFlow()

        private val _imeVisible = MutableStateFlow(false)

        /** Whether a soft-keyboard (IME) window is currently shown on screen. */
        val imeVisible: StateFlow<Boolean> = _imeVisible.asStateFlow()

        private val _foregroundPackage = MutableStateFlow<String?>(null)

        /** Package name of the current foreground app, for per-app bubble positioning. */
        val foregroundPackage: StateFlow<String?> = _foregroundPackage.asStateFlow()

        /**
         * Inserts [text] into the focused editable field via the running service, returning true on
         * success. Returns false when the service is not running or no editable field is focused.
         */
        fun injectText(text: String): Boolean = instance?.commitTextIntoFocused(text) ?: false

        /** The selection in the focused field, or empty when the service is unavailable. */
        fun selectedText(): String = instance?.selectedTextOfFocused() ?: ""

        /** The full text of the focused field, or empty when the service is unavailable. */
        fun fullText(): String = instance?.fullTextOfFocused() ?: ""

        /** Selects the whole focused field; false when the service is unavailable. */
        fun selectAll(): Boolean = instance?.selectAllInFocused() ?: false

        /** Presses Enter / the editor action on the focused field; false when unavailable. */
        fun performEnter(): Boolean = instance?.performEnterOnFocused() ?: false

        /** Removes the last inserted [text] from the focused field (undo, #133); false when unavailable. */
        fun deleteLastText(text: String): Boolean = instance?.deleteLastTextFromFocused(text) ?: false
    }
}
