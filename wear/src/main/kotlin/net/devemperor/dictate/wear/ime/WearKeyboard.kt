/*
 * Copyright (C) 2026 The Dictate Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */

package net.devemperor.dictate.wear.ime

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

/** The three input pages of the Wear keyboard. */
private enum class WearPage { VOICE, NUMBERS, EMOJI }

/**
 * Root of the Wear keyboard input view: a page selector plus the Voice / Numbers / Emoji surfaces.
 *
 * A real horizontal pager + rotary scrolling lands in P3; this version switches pages via the top
 * selector chips so the structure (and number/emoji commit path) is exercised end to end.
 */
@Composable
fun WearKeyboard(
    actions: WearImeActions,
    dictationState: WearDictationState,
) {
    MaterialTheme {
        var page by remember { mutableStateOf(WearPage.VOICE) }

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PageSelector(current = page, onSelect = { page = it })

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when (page) {
                    WearPage.VOICE -> VoicePage(dictationState, actions.toggleDictation)
                    WearPage.NUMBERS -> NumbersPage(actions)
                    WearPage.EMOJI -> EmojiPage(actions)
                }
            }
        }
    }
}

@Composable
private fun PageSelector(current: WearPage, onSelect: (WearPage) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
    ) {
        SelectorChip("🎙", current == WearPage.VOICE) { onSelect(WearPage.VOICE) }
        SelectorChip("123", current == WearPage.NUMBERS) { onSelect(WearPage.NUMBERS) }
        SelectorChip("😀", current == WearPage.EMOJI) { onSelect(WearPage.EMOJI) }
    }
}

@Composable
private fun SelectorChip(label: String, selected: Boolean, onClick: () -> Unit) {
    CompactChip(
        onClick = onClick,
        label = {
            Text(
                text = if (selected) "[$label]" else label,
                textAlign = TextAlign.Center,
            )
        },
    )
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
    // 1-9, 0, plus backspace and enter as the bottom action row.
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "⏎")
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(keys) { key ->
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
}

@Composable
private fun EmojiPage(actions: WearImeActions) {
    // Placeholder recents set. The full categorized picker (data reused from the phone app's
    // ime/media/emoji assets) lands in P3 of the Wear roadmap.
    val emojis = listOf("😀", "😂", "❤️", "👍", "🙏", "🎉", "😍", "😢", "🔥", "✅", "👋", "🤔")
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(emojis) { emoji ->
            CompactChip(
                onClick = { actions.commitText(emoji) },
                label = { Text(emoji, textAlign = TextAlign.Center) },
            )
        }
    }
}
