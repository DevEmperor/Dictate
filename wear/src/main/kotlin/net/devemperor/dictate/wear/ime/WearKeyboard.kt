/*
 * Copyright (C) 2026 The Dictate Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */

package net.devemperor.dictate.wear.ime

import android.os.SystemClock
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.devemperor.dictate.wear.emoji.WearEmojiData
import net.devemperor.dictate.wear.emoji.WearEmojiRecents
import net.devemperor.dictate.wear.ui.WearDictateTheme

/** The three input pages of the Wear keyboard, swipeable via the pager. */
private enum class WearPage(val icon: ImageVector, val label: String) {
    VOICE(Icons.Filled.Mic, "Voice"),
    NUMBERS(Icons.Filled.Dialpad, "123"),
    EMOJI(Icons.Filled.Mood, "Emoji"),
}

/**
 * Root of the Wear keyboard input view: a compact segmented page switcher + a swipeable pager over the
 * Voice / Numbers / Emoji surfaces, themed with the phone's accent color (synced).
 *
 * Layout is tuned for round watches: the switcher sits below the top arc and the Voice/Numbers pages
 * fit entirely on screen with no scrolling (only the much larger Emoji grid scrolls).
 */
@Composable
fun WearKeyboard(
    actions: WearImeActions,
    dictationState: WearDictationState,
    recordingInfo: WearRecordingInfo,
    errorMessage: String?,
) {
    WearDictateTheme {
        val pages = WearPage.entries
        val pagerState = rememberPagerState(pageCount = { pages.size })
        val scope = rememberCoroutineScope()

        Column(
            // A solid background so the keyboard never shows the app behind it (uniform, not transparent).
            // Top padding keeps the switcher clear of the round bezel; horizontal padding insets the grids.
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background)
                .padding(start = 10.dp, end = 10.dp, top = 18.dp, bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PageSwitcher(
                pages = pages,
                current = pagerState.currentPage,
                onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
            )

            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { index ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    when (pages[index]) {
                        WearPage.VOICE -> VoicePage(dictationState, recordingInfo, errorMessage, actions)
                        WearPage.NUMBERS -> NumbersPage(actions)
                        WearPage.EMOJI -> EmojiPage(actions)
                    }
                }
            }
        }
    }
}

/** A small segmented control of round icon buttons; the active page is filled with the accent color. */
@Composable
private fun PageSwitcher(
    pages: List<WearPage>,
    current: Int,
    onSelect: (Int) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        pages.forEachIndexed { index, page ->
            val selected = current == index
            Button(
                onClick = { onSelect(index) },
                modifier = Modifier.size(28.dp),
                colors = if (selected) ButtonDefaults.primaryButtonColors()
                else ButtonDefaults.secondaryButtonColors(),
            ) {
                Icon(
                    imageVector = page.icon,
                    contentDescription = page.label,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun VoicePage(
    state: WearDictationState,
    recordingInfo: WearRecordingInfo,
    errorMessage: String?,
    actions: WearImeActions,
) {
    val recording = state == WearDictationState.RECORDING
    val transcribing = state == WearDictationState.TRANSCRIBING
    val paused = recordingInfo.paused

    // Gentle breathing pulse on the record button while actively recording (mirrors the phone's pulsing
    // recording indicator); frozen while paused / idle.
    val pulse by rememberInfiniteTransition(label = "rec").animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "pulse",
    )
    val scale = if (recording && !paused) pulse else 1f

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (recording) ElapsedTimer(recordingInfo)

        Button(
            onClick = actions.toggleDictation,
            enabled = !transcribing,
            modifier = Modifier.size(64.dp).scale(scale),
            colors = ButtonDefaults.primaryButtonColors(),
        ) {
            when {
                transcribing -> CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 3.dp)
                recording -> Icon(Icons.Filled.Stop, contentDescription = "Stop", modifier = Modifier.size(28.dp))
                else -> Icon(Icons.Filled.Mic, contentDescription = "Dictate", modifier = Modifier.size(28.dp))
            }
        }

        when {
            recording -> {
                // Cancel + pause/resume, mirroring the phone smartbar controls.
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    SmallAction(Icons.Filled.Close, "Cancel", actions.cancelDictation)
                    SmallAction(
                        if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        if (paused) "Resume" else "Pause",
                        actions.togglePause,
                    )
                }
            }
            else -> {
                Text(
                    text = when (state) {
                        WearDictationState.TRANSCRIBING -> "Transcribing…"
                        WearDictationState.ERROR -> errorMessage ?: "Error — tap to retry"
                        else -> "Tap to dictate"
                    },
                    style = MaterialTheme.typography.caption2,
                    color = if (state == WearDictationState.ERROR) MaterialTheme.colors.error
                    else MaterialTheme.colors.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
        }
    }
}

/** m:ss elapsed counter, ticking every 200ms while running and frozen while paused (phone parity). */
@Composable
private fun ElapsedTimer(info: WearRecordingInfo) {
    var elapsedMs by remember { mutableLongStateOf(info.accumulatedMs) }
    LaunchedEffect(info.startedAtMs, info.accumulatedMs, info.paused) {
        if (info.paused) {
            elapsedMs = info.accumulatedMs
        } else {
            while (true) {
                elapsedMs = info.accumulatedMs + (SystemClock.elapsedRealtime() - info.startedAtMs)
                delay(200L)
            }
        }
    }
    val totalSec = elapsedMs / 1000L
    Text(
        text = "%d:%02d".format(totalSec / 60L, totalSec % 60L),
        style = MaterialTheme.typography.title3,
        color = if (info.paused) MaterialTheme.colors.onSurfaceVariant else MaterialTheme.colors.primary,
    )
}

@Composable
private fun SmallAction(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(32.dp),
        colors = ButtonDefaults.secondaryButtonColors(),
    ) {
        Icon(icon, contentDescription = desc, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun NumbersPage(actions: WearImeActions) {
    // A 3x4 keypad whose keys expand (via weight) to fill the whole input area — big, easy targets,
    // no scrolling. Rounded-rectangle keys look like a proper keypad and work on round and square.
    // Bottom row is backspace / 0 / enter.
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("⌫", "0", "⏎"),
    )
    Column(
        modifier = Modifier.fillMaxSize().padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colors.surface)
                            .clickable {
                                when (key) {
                                    "⌫" -> actions.deleteBackward()
                                    "⏎" -> actions.performEnter()
                                    else -> actions.commitText(key)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        when (key) {
                            "⌫" -> Icon(Icons.AutoMirrored.Filled.Backspace, "Backspace", modifier = Modifier.size(22.dp))
                            "⏎" -> Icon(Icons.AutoMirrored.Filled.KeyboardReturn, "Enter", modifier = Modifier.size(22.dp))
                            else -> Text(key, style = MaterialTheme.typography.title2, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmojiPage(actions: WearImeActions) {
    val context = LocalContext.current
    val categories = remember { WearEmojiData.categories(context) }
    var recents by remember { mutableStateOf(WearEmojiRecents.get(context)) }
    var selectedTab by remember { mutableIntStateOf(0) }

    // Tabs the user can pick from: a recents tab first (only when non-empty), then each category.
    // selectedTab indexes straight into this list (clamped) so adding/removing the recents tab is safe.
    val tabs = buildList {
        if (recents.isNotEmpty()) add("🕘" to recents)
        categories.forEach { add(it.label to it.emojis) }
    }
    val activeTab = selectedTab.coerceIn(0, tabs.lastIndex)

    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        ) {
            tabs.forEachIndexed { index, (label, _) ->
                EmojiTab(label = label, selected = activeTab == index) { selectedTab = index }
            }
        }

        RotaryGrid(items = tabs[activeTab].second, columns = 4) { emoji ->
            Button(
                onClick = {
                    actions.commitText(emoji)
                    WearEmojiRecents.add(context, emoji)
                    recents = WearEmojiRecents.get(context)
                },
                modifier = Modifier.size(36.dp),
                colors = ButtonDefaults.secondaryButtonColors(),
            ) {
                Text(emoji, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun EmojiTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(28.dp),
        colors = if (selected) ButtonDefaults.primaryButtonColors() else ButtonDefaults.secondaryButtonColors(),
    ) {
        Text(label, textAlign = TextAlign.Center, style = MaterialTheme.typography.caption2)
    }
}

/**
 * A [LazyVerticalGrid] wired for the watch's rotary crown/bezel: rotating scrolls the grid. The grid
 * grabs focus so it receives rotary events even though there is no text field on the page. Used for the
 * emoji picker, which is too large to fit on screen.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun <T> RotaryGrid(
    items: List<T>,
    columns: Int,
    item: @Composable (T) -> Unit,
) {
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = gridState,
        modifier = Modifier
            .fillMaxSize()
            .onRotaryScrollEvent { event ->
                scope.launch { gridState.scrollBy(event.verticalScrollPixels) }
                true
            }
            .focusRequester(focusRequester)
            .focusable(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(items) { item(it) }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}
