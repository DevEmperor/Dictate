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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.dictate.DictateController
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import kotlinx.coroutines.delay
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.ui.SnyggIcon
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.SnyggText

/**
 * Gboard-style in-Smartbar dictation indicator. Rendered in the Smartbar's center area (left of the
 * sticky mic button) while [DictateController] is recording or transcribing, so the keyboard itself
 * stays fully visible instead of being replaced by a separate panel.
 *
 * - Recording: a pulsing red dot + an elapsed `m:ss` timer.
 * - Transcribing: a spinning icon + the "transcribing" label.
 * - Error: the error message, auto-cleared after a few seconds.
 */
@Composable
fun DictateSmartbarUi(state: DictateController.UiState, modifier: Modifier = Modifier) {
    SnyggRow(
        elementName = FlorisImeUi.SmartbarSharedActionsRow.elementName,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (state) {
            is DictateController.UiState.Recording -> {
                var elapsedMs by remember { mutableLongStateOf(0L) }
                LaunchedEffect(state.startedAtMs) {
                    while (true) {
                        elapsedMs = SystemClock.elapsedRealtime() - state.startedAtMs
                        delay(200L)
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
                        .scale(pulse)
                        .clip(CircleShape)
                        .background(Color(0xFFE53935)),
                )
                Spacer(modifier = Modifier.width(10.dp))
                SnyggText(text = formatElapsed(elapsedMs))
            }

            is DictateController.UiState.Transcribing -> {
                val transition = rememberInfiniteTransition(label = "transcribing")
                val rotation by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(tween(900)),
                    label = "spin",
                )
                SnyggIcon(
                    imageVector = Icons.Default.Sync,
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(rotation),
                )
                Spacer(modifier = Modifier.width(10.dp))
                SnyggText(text = stringRes(R.string.dictate__status_transcribing))
            }

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

private fun formatElapsed(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(totalSec / 60L, totalSec % 60L)
}
