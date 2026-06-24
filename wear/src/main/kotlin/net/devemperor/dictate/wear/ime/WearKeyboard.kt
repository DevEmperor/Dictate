/*
 * Copyright (C) 2026 The Dictate Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */

package net.devemperor.dictate.wear.ime

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import kotlinx.coroutines.launch
import net.devemperor.dictate.wear.emoji.WearEmojiData
import net.devemperor.dictate.wear.emoji.WearEmojiRecents

/** The three input pages of the Wear keyboard, swipeable via the pager. */
private enum class WearPage(val label: String) {
    VOICE("🎙"),
    NUMBERS("123"),
    EMOJI("😀"),
}

/**
 * Root of the Wear keyboard input view: a page selector + a swipeable pager over the Voice / Numbers /
 * Emoji surfaces. Numbers and emoji commit directly through [WearImeActions]; the emoji grid is
 * rotary-scrollable.
 */
@Composable
fun WearKeyboard(
    actions: WearImeActions,
    dictationState: WearDictationState,
) {
    MaterialTheme {
        val pages = WearPage.entries
        val pagerState = rememberPagerState(pageCount = { pages.size })
        val scope = rememberCoroutineScope()

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            ) {
                pages.forEachIndexed { index, page ->
                    val selected = pagerState.currentPage == index
                    CompactChip(
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        label = { Text(if (selected) "[${page.label}]" else page.label, textAlign = TextAlign.Center) },
                    )
                }
            }

            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { index ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    when (pages[index]) {
                        WearPage.VOICE -> VoicePage(dictationState, actions.toggleDictation)
                        WearPage.NUMBERS -> NumbersPage(actions)
                        WearPage.EMOJI -> EmojiPage(actions)
                    }
                }
            }
        }
    }
}

@Composable
private fun VoicePage(state: WearDictationState, onToggle: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        Button(onClick = onToggle, modifier = Modifier.size(64.dp)) {
            Text(if (state == WearDictationState.RECORDING) "■" else "●")
        }
        Text(
            text = when (state) {
                WearDictationState.IDLE -> "Tap to dictate"
                WearDictationState.RECORDING -> "Recording…"
                WearDictationState.TRANSCRIBING -> "Transcribing…"
                WearDictationState.ERROR -> "Error — tap to retry"
            },
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun NumbersPage(actions: WearImeActions) {
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "⏎")
    RotaryGrid(items = keys, columns = 3) { key ->
        CompactChip(
            onClick = {
                when (key) {
                    "⌫" -> actions.deleteBackward()
                    "⏎" -> actions.performEnter()
                    else -> actions.commitText(key)
                }
            },
            label = { Text(key, textAlign = TextAlign.Center) },
        )
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
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            tabs.forEachIndexed { index, (label, _) ->
                EmojiTab(label = label, selected = activeTab == index) { selectedTab = index }
            }
        }

        RotaryGrid(items = tabs[activeTab].second, columns = 4) { emoji ->
            CompactChip(
                onClick = {
                    actions.commitText(emoji)
                    WearEmojiRecents.add(context, emoji)
                    recents = WearEmojiRecents.get(context)
                },
                label = { Text(emoji, textAlign = TextAlign.Center) },
            )
        }
    }
}

@Composable
private fun EmojiTab(label: String, selected: Boolean, onClick: () -> Unit) {
    CompactChip(
        onClick = onClick,
        label = { Text(if (selected) "[$label]" else label, textAlign = TextAlign.Center) },
    )
}

/**
 * A [LazyVerticalGrid] wired for the watch's rotary crown/bezel: rotating scrolls the grid. The grid
 * grabs focus so it receives rotary events even though there is no text field on the page.
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
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(items) { item(it) }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}
