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
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.dictate.DictateController
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
    val arrangement = if (state is DictateController.UiState.Recording) {
        Arrangement.SpaceBetween
    } else {
        Arrangement.Center
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
            is DictateController.UiState.Error -> {
                SnyggText(text = state.message)
                LaunchedEffect(state) {
                    delay(4000L)
                    DictateController.clearError()
                }
            }
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

    // Pause/resume button (far right, left of the sticky mic).
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

private fun formatElapsed(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(totalSec / 60L, totalSec % 60L)
}
