/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate.ui

import android.os.SystemClock
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.dictate.DictateController
import dev.patrickgold.florisboard.dictate.DictateLanguages
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.delay
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.ui.SnyggIcon
import org.florisboard.lib.snygg.ui.SnyggIconButton
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.SnyggText

/**
 * Gboard-style in-Smartbar dictation indicator. Rendered in the Smartbar's center area (left of the
 * sticky mic button) while [DictateController] is recording or transcribing, so the keyboard itself
 * stays fully visible instead of being replaced by a separate panel.
 *
 * - Recording: a cancel button, a pulsing red dot + an elapsed `m:ss` timer, and a pause/resume
 *   button. The sticky mic (rendered by the Smartbar) stops the recording and starts transcribing.
 * - Transcribing: a spinning icon + label, or a retry indicator while a transient failure is retried.
 * - Error: the error message, auto-cleared after a few seconds.
 */
@Composable
fun DictateSmartbarUi(state: DictateController.UiState, modifier: Modifier = Modifier) {
    val arrangement = when {
        state is DictateController.UiState.Recording -> Arrangement.SpaceBetween
        state is DictateController.UiState.Error && state.canResend -> Arrangement.SpaceBetween
        else -> Arrangement.Center
    }
    SnyggRow(
        elementName = FlorisImeUi.SmartbarSharedActionsRow.elementName,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 6.dp),
        horizontalArrangement = arrangement,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (state) {
            is DictateController.UiState.Recording -> RecordingContent(state)
            is DictateController.UiState.Transcribing -> TranscribingContent(state)
            is DictateController.UiState.Rewording -> RewordingContent(state)
            is DictateController.UiState.Error -> {
                if (state.canResend) {
                    ResendErrorContent(state.message)
                } else {
                    SnyggText(text = state.message)
                    LaunchedEffect(state) {
                        delay(4000L)
                        DictateController.clearError()
                    }
                }
            }
            is DictateController.UiState.Promo -> PromoContent(state.kind)
            else -> {}
        }
    }
}

@Composable
private fun RecordingContent(state: DictateController.UiState.Recording) {
    val prefs by FlorisPreferenceStore
    val keepScreenAwake by prefs.dictate.keepScreenAwake.collectAsState()

    // Keep the screen on while the recording indicator is visible, if the user enabled it.
    val view = LocalView.current
    DisposableEffect(keepScreenAwake) {
        if (keepScreenAwake) view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // Cancel button (far left) – discards the recording.
    SnyggIconButton(
        elementName = FlorisImeUi.SmartbarActionKey.elementName,
        onClick = { DictateController.cancelRecording() },
        modifier = Modifier.fillMaxHeight().aspectRatio(1f),
    ) {
        SnyggIcon(
            imageVector = Icons.Default.Delete,
            contentDescription = stringRes(R.string.dictate__action_cancel),
        )
    }

    // Center: pulsing dot + elapsed timer.
    Row(verticalAlignment = Alignment.CenterVertically) {
        var elapsedMs by remember { mutableLongStateOf(state.accumulatedMs) }
        LaunchedEffect(state.startedAtMs, state.accumulatedMs, state.paused) {
            if (state.paused) {
                elapsedMs = state.accumulatedMs
            } else {
                while (true) {
                    elapsedMs = state.accumulatedMs + (SystemClock.elapsedRealtime() - state.startedAtMs)
                    delay(200L)
                }
            }
        }
        val transition = rememberInfiniteTransition(label = "recording")
        val pulse by transition.animateFloat(
            initialValue = 0.65f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
            label = "pulse",
        )
        Spacer(
            modifier = Modifier
                .size(12.dp)
                .scale(if (state.paused) 1f else pulse)
                .alpha(if (state.paused) 0.4f else 1f)
                .clip(CircleShape)
                .background(Color(0xFFE53935)),
        )
        Spacer(modifier = Modifier.width(10.dp))
        SnyggText(text = formatElapsed(elapsedMs))
    }

    // Right group: language chip + pause/resume button (left of the sticky mic).
    Row(verticalAlignment = Alignment.CenterVertically) {
        LanguageChip()
        SnyggIconButton(
            elementName = FlorisImeUi.SmartbarActionKey.elementName,
            onClick = { DictateController.togglePause() },
            modifier = Modifier.fillMaxHeight().aspectRatio(1f),
        ) {
            SnyggIcon(
                imageVector = if (state.paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                contentDescription = stringRes(
                    if (state.paused) R.string.dictate__action_resume else R.string.dictate__action_pause,
                ),
            )
        }
    }
}

/**
 * Compact dictation-language selector shown on the recording bar. Tap cycles through the user's
 * selected languages ([prefs.dictate.inputLanguages]); long-press opens a quick picker of that same
 * subset. Auto-detect is shown as a globe; any other language as its short code (DE, EN, …). The
 * choice only matters at transcription time, so switching mid-recording is fine.
 */
@Composable
private fun LanguageChip() {
    val prefs by FlorisPreferenceStore
    val activeCode by prefs.dictate.activeInputLanguage.collectAsState()
    val selectionRaw by prefs.dictate.inputLanguages.collectAsState()
    val selection = remember(selectionRaw) { DictateLanguages.parseSelection(selectionRaw) }
    val active = remember(activeCode) { DictateLanguages.of(activeCode) }
    var menuOpen by remember { mutableStateOf(false) }

    // Use the same SnyggIconButton as the cancel/pause buttons so the press ripple and footprint
    // are identical; the dropdown is anchored to the wrapping box.
    Box(modifier = Modifier.fillMaxHeight()) {
        SnyggIconButton(
            elementName = FlorisImeUi.SmartbarActionKey.elementName,
            onClick = { DictateController.cycleLanguage() },
            onLongClick = { if (selection.size > 1) menuOpen = true },
            modifier = Modifier.fillMaxHeight().aspectRatio(1f),
        ) {
            if (active.code == DictateLanguages.DETECT) {
                SnyggIcon(
                    imageVector = Icons.Default.Language,
                    contentDescription = stringRes(R.string.dictate__language_detect),
                    modifier = Modifier.size(20.dp),
                )
            } else {
                SnyggText(text = active.shortCode)
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            selection.forEach { lang ->
                DropdownMenuItem(
                    text = {
                        Text(
                            if (lang.code == DictateLanguages.DETECT) {
                                stringRes(R.string.dictate__language_detect)
                            } else {
                                lang.displayName()
                            }
                        )
                    },
                    onClick = {
                        DictateController.setLanguage(lang.code)
                        menuOpen = false
                    },
                )
            }
        }
    }
}

@Composable
private fun TranscribingContent(state: DictateController.UiState.Transcribing) {
    val transition = rememberInfiniteTransition(label = "transcribing")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900)),
        label = "spin",
    )
    val retrying = state.attempt > 1
    SnyggIcon(
        imageVector = if (retrying) Icons.Default.CloudOff else Icons.Default.Sync,
        modifier = Modifier
            .size(18.dp)
            .then(if (retrying) Modifier else Modifier.rotate(rotation)),
    )
    Spacer(modifier = Modifier.width(10.dp))
    SnyggText(
        text = if (retrying) {
            stringRes(R.string.dictate__status_retrying, "attempt" to state.attempt)
        } else {
            stringRes(R.string.dictate__status_transcribing)
        },
    )
}

/**
 * Shown while a rewording/GPT request runs (auto-formatting, auto-apply or live prompt): a spinning
 * icon plus the active prompt's label. Mirrors [TranscribingContent] visually.
 */
@Composable
private fun RewordingContent(state: DictateController.UiState.Rewording) {
    val transition = rememberInfiniteTransition(label = "rewording")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900)),
        label = "spin",
    )
    SnyggIcon(
        imageVector = Icons.Default.AutoAwesome,
        modifier = Modifier
            .size(18.dp)
            .rotate(rotation),
    )
    Spacer(modifier = Modifier.width(10.dp))
    SnyggText(text = state.label.ifBlank { stringRes(R.string.dictate__status_rewording) })
}

/**
 * Error variant shown when the failed audio was kept (roadmap 10.3): the message plus a resend button
 * that retries the same audio and a dismiss button that drops it. Unlike the transient error, this one
 * does not auto-clear so the user has time to react.
 */
@Composable
private fun RowScope.ResendErrorContent(message: String) {
    val context = LocalContext.current
    SnyggIcon(
        imageVector = Icons.Default.CloudOff,
        modifier = Modifier.size(18.dp),
    )
    Spacer(modifier = Modifier.width(8.dp))
    SnyggText(text = message, modifier = Modifier.weight(1f))
    SnyggIconButton(
        elementName = FlorisImeUi.SmartbarActionKey.elementName,
        onClick = { DictateController.resendLastAudio(context) },
        modifier = Modifier.fillMaxHeight().aspectRatio(1f),
    ) {
        SnyggIcon(
            imageVector = Icons.Default.Refresh,
            contentDescription = stringRes(R.string.dictate__action_resend),
        )
    }
    SnyggIconButton(
        elementName = FlorisImeUi.SmartbarActionKey.elementName,
        onClick = { DictateController.dismissResend() },
        modifier = Modifier.fillMaxHeight().aspectRatio(1f),
    ) {
        SnyggIcon(
            imageVector = Icons.Default.Close,
            contentDescription = stringRes(R.string.dictate__action_dismiss),
        )
    }
}

/**
 * One-time rate/donate nudge (roadmap 9.7/9.8): a star/heart icon, a short message and accept (✓) /
 * decline (✗) buttons. Accepting opens the Play Store (rate) or PayPal (donate); both buttons mark the
 * nudge as handled so it never reappears. Shown only when idle, replacing the normal Smartbar.
 */
@Composable
private fun PromoContent(kind: DictateController.PromoKind) {
    val context = LocalContext.current
    val isRate = kind == DictateController.PromoKind.RATE
    val accent = Color(0xFF30B7E6) // Dictate light blue (theme accent default).

    // Gentle pop-in (fade + slight scale) on top of the Smartbar's own slide transition.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val appear by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(220),
        label = "promoAppear",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxHeight()
            .padding(horizontal = 4.dp)
            .graphicsLayer {
                alpha = appear
                val s = 0.92f + 0.08f * appear
                scaleX = s
                scaleY = s
            },
    ) {
        // Tinted leading icon (star = rate, heart = donate).
        Icon(
            imageVector = if (isRate) Icons.Default.Star else Icons.Default.Favorite,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        SnyggText(
            text = stringRes(if (isRate) R.string.dictate__promo_rate_message else R.string.dictate__promo_donate_message),
        )
        Spacer(modifier = Modifier.width(10.dp))
        // Filled accent pill = primary action (opens Play Store / PayPal).
        Text(
            text = stringRes(if (isRate) R.string.dictate__promo_rate_action else R.string.dictate__promo_donate_action),
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(accent)
                .clickable { DictateController.acceptPromo(context) }
                .padding(horizontal = 14.dp, vertical = 6.dp),
        )
        Spacer(modifier = Modifier.width(2.dp))
        // Subtle dismiss.
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .clickable { DictateController.declinePromo() }
                .padding(6.dp),
        ) {
            SnyggIcon(
                imageVector = Icons.Default.Close,
                contentDescription = stringRes(R.string.dictate__action_no),
                modifier = Modifier.size(18.dp).alpha(0.6f),
            )
        }
    }
}

private fun formatElapsed(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(totalSec / 60L, totalSec % 60L)
}
