/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.florisboard.lib.compose

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

data class FlorisStep(
    val id: Int,
    val title: String,
    val content: @Composable FlorisStepLayoutScope.() -> Unit,
)

class FlorisStepLayoutScope(
    columnScope: ColumnScope,
    private val primaryColor: Color,
) : ColumnScope by columnScope {

    @Composable
    fun StepText(
        text: String,
        modifier: Modifier = Modifier,
        fontStyle: FontStyle = FontStyle.Normal,
    ) {
        Text(
            modifier = modifier,
            text = text,
            textAlign = TextAlign.Justify,
            fontStyle = fontStyle,
        )
    }

    @Composable
    fun StepButton(
        label: String,
        modifier: Modifier = Modifier,
        onClick: () -> Unit,
    ) {
        Button(
            modifier = modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = primaryColor,
            ),
            onClick = onClick,
        ) {
            Text(text = label)
        }
    }
}

@Suppress("unused")
class FlorisStepState private constructor(
    private val currentAuto: MutableState<Int>,
    private val currentManual: MutableState<Int> = mutableIntStateOf(-1),
) {
    companion object {
        fun new(init: Int) = FlorisStepState(mutableIntStateOf(init))

        val Saver = Saver<FlorisStepState, ArrayList<Int>>(
            save = {
                arrayListOf(it.currentAuto.value, it.currentManual.value)
            },
            restore = {
                FlorisStepState(mutableIntStateOf(it[0]), mutableIntStateOf(it[1]))
            },
        )
    }

    fun getCurrent(): State<Int> {
        return if (currentManual.value >= 0 && currentAuto.value >= currentManual.value) {
            currentManual
        } else {
            currentAuto
        }
    }

    fun getCurrentAuto(): State<Int> = currentAuto

    fun getCurrentManual(): State<Int> = currentManual

    fun setCurrentAuto(value: Int) {
        currentAuto.value = value
    }

    fun setCurrentManual(value: Int) {
        if (currentAuto.value == value) {
            currentManual.value = -1
        } else {
            currentManual.value = value
        }
    }
}

/**
 * A paged setup wizard: each step gets a full page of its own instead of being crammed into a shared
 * accordion. A segmented progress bar at the top shows where the user is, the current step slides in
 * horizontally, and a bottom bar lets the user move back (and forward through already-reached steps).
 * Forward progress is still driven automatically by [FlorisStepState.setCurrentAuto] as prerequisites
 * are fulfilled, so the wizard advances on its own once a permission is granted etc.
 *
 * [header] is shown once, above the first step's content (e.g. an intro line); [footer] stays pinned at
 * the bottom on every page (e.g. privacy/repository links that must remain reachable during setup).
 */
@Composable
fun FlorisStepLayout(
    stepState: FlorisStepState,
    steps: List<FlorisStep>,
    backLabel: String,
    nextLabel: String,
    modifier: Modifier = Modifier,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    header: @Composable FlorisStepLayoutScope.() -> Unit = { },
    footer: @Composable FlorisStepLayoutScope.() -> Unit = { },
) {
    val currentStepId by stepState.getCurrent()
    val autoStepId by stepState.getCurrentAuto()

    fun indexOfId(id: Int): Int = steps.indexOfFirst { it.id == id }.coerceAtLeast(0)
    val currentIndex = indexOfId(currentStepId)
    val autoIndex = indexOfId(autoStepId)
    val canGoBack = currentIndex > 0
    val canGoForward = currentIndex < autoIndex

    Column(modifier = modifier.fillMaxSize()) {
        // Progress: one segment per step, filled up to and including the current one.
        StepProgressBar(
            stepCount = steps.size,
            currentIndex = currentIndex,
            primaryColor = primaryColor,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "${currentIndex + 1} / ${steps.size}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))

        val animSpec = spring<Float>(stiffness = Spring.StiffnessMediumLow)
        AnimatedContent(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            targetState = currentStepId,
            transitionSpec = {
                val forward = indexOfId(targetState) >= indexOfId(initialState)
                val dir = if (forward) 1 else -1
                val slideSpec = spring(
                    stiffness = Spring.StiffnessMediumLow,
                    visibilityThreshold = IntOffset.VisibilityThreshold,
                )
                (slideInHorizontally(slideSpec) { w -> dir * w } + fadeIn(animSpec)) togetherWith
                    (slideOutHorizontally(slideSpec) { w -> -dir * w } + fadeOut(animSpec))
            },
            label = "setup-step",
        ) { stepId ->
            val step = steps.firstOrNull { it.id == stepId } ?: return@AnimatedContent
            val isFirst = indexOfId(stepId) == 0
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .florisVerticalScroll(),
            ) {
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(16.dp))
                val scope = FlorisStepLayoutScope(this, primaryColor)
                if (isFirst) {
                    header(scope)
                }
                step.content(scope)
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Bottom navigation: back to a previous step, or forward through already-reached ones. The
        // step's own primary button (and auto-advance) handles getting past the current requirement.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (canGoBack) {
                TextButton(onClick = { stepState.setCurrentManual(steps[currentIndex - 1].id) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(backLabel)
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            if (canGoForward) {
                TextButton(onClick = { stepState.setCurrentManual(steps[currentIndex + 1].id) }) {
                    Text(nextLabel)
                    Spacer(modifier = Modifier.size(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        footer(FlorisStepLayoutScope(this, primaryColor))
    }
}

@Composable
private fun StepProgressBar(
    stepCount: Int,
    currentIndex: Int,
    primaryColor: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (i in 0 until stepCount) {
            val reached = i <= currentIndex
            val color by animateColorAsState(
                targetValue = if (reached) primaryColor else primaryColor.copy(alpha = 0.18f),
                label = "step-segment",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}
