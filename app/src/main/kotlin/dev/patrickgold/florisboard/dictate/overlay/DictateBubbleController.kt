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

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.ContextCompat
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.dictate.DictateController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.hypot

/**
 * Owns the floating dictation button (issue #88): a small draggable bubble shown over other apps via a
 * [WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY] window (no draw-over-apps permission needed —
 * the window is hosted by the accessibility service). The bubble appears whenever an editable field is
 * focused (or a dictation is in flight), toggles recording on tap, animates while recording/transcribing,
 * and routes the result through [DictateController] with [DictateController.OutputTarget.OVERLAY] so the
 * text is injected into the focused field.
 *
 * Created and owned by [DictateAccessibilityService], which also provides the foreground-microphone
 * promotion the recording needs while the app is in the background.
 */
class DictateBubbleController(private val service: DictateAccessibilityService) {

    private val context: Context get() = service
    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val prefs by FlorisPreferenceStore

    private var rootView: FrameLayout? = null
    private var iconView: ImageView? = null
    private var params: WindowManager.LayoutParams? = null
    private var pulse: ValueAnimator? = null
    private var added = false

    /**
     * Whether the bubble started the in-flight dictation. Used to attribute a terminal Error state to the
     * overlay (so its message is surfaced via a toast) without reacting to errors from a keyboard-driven
     * dictation that happens while the bubble is also visible.
     */
    private var weStartedDictation = false

    private val bubbleSize = dp(56)
    private val iconInset = dp(15)

    /** Inputs that decide whether the bubble is shown and how it looks, combined from prefs + service. */
    private data class Inputs(
        val enabled: Boolean,
        val showWithDictateKeyboard: Boolean,
        val focused: Boolean,
        val dictateKeyboardActive: Boolean,
        val state: DictateController.UiState,
    )

    /** Starts observing the feature toggle + focus + dictation state to show/hide and animate the bubble. */
    fun start() {
        scope.launch {
            combine(
                prefs.dictate.floatingButtonEnabled.asFlow(),
                prefs.dictate.floatingButtonShowWithDictateKeyboard.asFlow(),
                DictateAccessibilityService.editableFocused,
                DictateAccessibilityService.dictateKeyboardActive,
                DictateController.state,
            ) { enabled, showWithKeyboard, focused, dictateKeyboard, state ->
                Inputs(enabled, showWithKeyboard, focused, dictateKeyboard, state)
            }
                .collect { (enabled, showWithKeyboard, focused, dictateKeyboard, state) ->
                    val active = state !is DictateController.UiState.Idle
                    // When our own keyboard is up it already has a mic key, so hide the bubble unless the
                    // user opted to show it everywhere — but never hide mid-dictation.
                    val hiddenByOwnKeyboard = dictateKeyboard && !showWithKeyboard && !active
                    val show = enabled && (focused || active) && !hiddenByOwnKeyboard
                    if (show) ensureShown() else hide()
                    applyState(state)
                    manageForeground(state)
                    reportTerminalState(state)
                }
        }
    }

    /** Tears everything down: stops observing, removes the window and drops the foreground state. */
    fun destroy() {
        scope.cancel()
        stopPulse()
        hide()
        service.stopMicForeground()
    }

    // --- Window add/remove -----------------------------------------------------------------------

    private fun ensureShown() {
        if (added) return
        val view = rootView ?: createView().also { rootView = it }
        val lp = params ?: createParams().also { params = it }
        runCatching {
            windowManager.addView(view, lp)
            added = true
        }
    }

    private fun hide() {
        val view = rootView
        if (added && view != null) runCatching { windowManager.removeView(view) }
        added = false
    }

    private fun createView(): FrameLayout {
        val icon = ImageView(context).apply {
            setImageResource(R.drawable.ic_dictate_overlay_mic)
            setPadding(iconInset, iconInset, iconInset, iconInset)
            background = circle(R.color.dictate_overlay_accent)
            elevation = dp(6).toFloat()
        }
        iconView = icon
        val root = FrameLayout(context)
        root.addView(icon, FrameLayout.LayoutParams(bubbleSize, bubbleSize))
        attachTouch(root)
        return root
    }

    private fun createParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(220)
        }
    }

    // --- Touch: tap toggles dictation, drag repositions ------------------------------------------

    private fun attachTouch(view: View) {
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        view.setOnTouchListener { _, e ->
            val lp = params ?: return@setOnTouchListener false
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startX = lp.x
                    startY = lp.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!moved && hypot(dx, dy) > slop) moved = true
                    if (moved) {
                        lp.x = startX + dx.toInt()
                        lp.y = startY + dy.toInt()
                        runCatching { windowManager.updateViewLayout(view, lp) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) onTap()
                    true
                }
                else -> false
            }
        }
    }

    private fun onTap() {
        // Promote the service to a microphone foreground service *before* recording starts, so the mic
        // capture is allowed while the app is in the background (Android 14+). Demoted again when the
        // dictation finishes (see manageForeground).
        val starting = DictateController.state.value is DictateController.UiState.Idle
        if (starting) {
            service.startMicForeground()
            weStartedDictation = true
        }
        DictateController.onMicClick(context, DictateController.OutputTarget.OVERLAY)
    }

    /**
     * Surfaces a failed bubble dictation: the overlay has no inline error UI, so when an overlay-started
     * dictation ends in an [DictateController.UiState.Error], show its message as a toast and clear the
     * error so the keyboard's own chip does not also fire. Successful/aborted ends just reset the flag.
     */
    private fun reportTerminalState(state: DictateController.UiState) {
        when (state) {
            is DictateController.UiState.Error -> if (weStartedDictation) {
                weStartedDictation = false
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                DictateController.clearError()
            }
            is DictateController.UiState.Idle -> weStartedDictation = false
            else -> Unit // recording / transcribing / rewording still in flight
        }
    }

    // --- Appearance per state --------------------------------------------------------------------

    private fun applyState(state: DictateController.UiState) {
        val icon = iconView ?: return
        when (state) {
            is DictateController.UiState.Recording -> {
                icon.alpha = 1f
                icon.setImageResource(R.drawable.ic_dictate_overlay_stop)
                icon.background = circle(R.color.dictate_overlay_recording)
                startPulse()
            }
            is DictateController.UiState.Transcribing, is DictateController.UiState.Rewording -> {
                stopPulse()
                icon.setImageResource(R.drawable.ic_dictate_overlay_mic)
                icon.background = circle(R.color.dictate_overlay_accent)
                icon.alpha = 0.55f // simple "busy" hint until the transcription returns
            }
            else -> {
                stopPulse()
                icon.alpha = 1f
                icon.setImageResource(R.drawable.ic_dictate_overlay_mic)
                icon.background = circle(R.color.dictate_overlay_accent)
            }
        }
    }

    private fun startPulse() {
        if (pulse != null) return
        val icon = iconView ?: return
        pulse = ValueAnimator.ofFloat(1f, 1.18f).apply {
            duration = 600
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                val s = it.animatedValue as Float
                icon.scaleX = s
                icon.scaleY = s
            }
            start()
        }
    }

    private fun stopPulse() {
        pulse?.cancel()
        pulse = null
        iconView?.apply {
            scaleX = 1f
            scaleY = 1f
        }
    }

    private fun manageForeground(state: DictateController.UiState) {
        when (state) {
            is DictateController.UiState.Recording,
            is DictateController.UiState.Transcribing,
            is DictateController.UiState.Rewording,
            -> Unit // keep the microphone foreground service running
            else -> service.stopMicForeground()
        }
    }

    private fun circle(colorRes: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(ContextCompat.getColor(context, colorRes))
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
