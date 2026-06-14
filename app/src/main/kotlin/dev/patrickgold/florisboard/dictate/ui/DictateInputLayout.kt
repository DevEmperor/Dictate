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

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.dictate.DictateController
import dev.patrickgold.florisboard.ime.ImeUiMode
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.keyboardManager
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggColumn
import org.florisboard.lib.snygg.ui.SnyggIcon
import org.florisboard.lib.snygg.ui.SnyggIconButton
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.SnyggText

/**
 * The Dictate AI prompt panel, rendered as its own [ImeUiMode.DICTATE] next to the regular typing
 * keyboard (see `ImeWindow`). Opened via the prompt-panel QuickAction (magic-wand) in the Smartbar so
 * the rewording prompts are always one tap away – independent of whether anything is selected.
 *
 * Each prompt chip runs [DictateController.applyPrompt] and returns to the typing keyboard; the
 * Smartbar then shows the rewording progress. A `requiresSelection` prompt tapped with no active
 * selection first selects the whole field (see the controller), so users can reword everything
 * without manually selecting first.
 *
 * Reuses the already-themed `media-*` Snygg elements so it inherits the active theme with no
 * stylesheet changes (dedicated `dictate-*` theming is deferred polish).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DictateInputLayout(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val prompts by DictateController.prompts.collectAsState()
    val scrollState = rememberScrollState()

    SnyggColumn(
        elementName = FlorisImeUi.Media.elementName,
        modifier = modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.imeUiHeight()),
    ) {
        // Header: back to the typing keyboard + panel title.
        SnyggRow(
            elementName = FlorisImeUi.MediaBottomRow.elementName,
            modifier = Modifier
                .fillMaxWidth()
                .height(FlorisImeSizing.smartbarHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SnyggIconButton(
                elementName = FlorisImeUi.MediaBottomRowButton.elementName,
                onClick = { keyboardManager.activeState.imeUiMode = ImeUiMode.TEXT },
                modifier = Modifier.size(FlorisImeSizing.smartbarHeight),
            ) {
                SnyggIcon(imageVector = Icons.AutoMirrored.Filled.ArrowBack)
            }
            SnyggText(
                elementName = FlorisImeUi.MediaEmojiSubheader.elementName,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp),
                text = stringRes(R.string.dictate__panel_title),
            )
        }

        // Body: the live-prompt chip plus all saved prompts, flowing from the top-left to the right and
        // wrapping onto further lines (left-aligned, not centered). The live chip is always present, so
        // the panel is never truly empty; a hint is shown underneath only when no saved prompts exist.
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState)
                // Persistent (non-fading) scrollbar pinned to the right edge, drawn only when the
                // prompt list actually overflows the panel – a clear, always-visible affordance.
                .dictatePanelScrollbar(scrollState)
                .padding(8.dp),
            horizontalArrangement = Arrangement.Start,
        ) {
            // In the panel the chips are roomier and sit closer together than in the compact row, so
            // they are easier to hit (larger icon + extra tap padding, tighter inter-chip spacing).
            val panelChipSpacing = Modifier.padding(2.dp)
            val panelIconSize = 22.dp
            val panelTapPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
            DictateLivePromptChip(
                modifier = panelChipSpacing,
                iconSize = panelIconSize,
                tapPadding = panelTapPadding,
                onClick = {
                    DictateController.startLivePrompt(context)
                    // Return to the keyboard so the recording indicator + field stay visible.
                    keyboardManager.activeState.imeUiMode = ImeUiMode.TEXT
                },
            )
            prompts.forEach { prompt ->
                DictatePromptChip(
                    icon = dictatePromptIcon(prompt),
                    text = prompt.name.orEmpty(),
                    modifier = panelChipSpacing,
                    iconSize = panelIconSize,
                    tapPadding = panelTapPadding,
                    onClick = {
                        DictateController.applyPrompt(context, prompt)
                        // Return to the keyboard so the field + the Smartbar progress are visible.
                        keyboardManager.activeState.imeUiMode = ImeUiMode.TEXT
                    },
                )
            }
        }
        if (prompts.isEmpty()) {
            SnyggBox(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            ) {
                SnyggText(
                    elementName = FlorisImeUi.MediaEmojiSubheader.elementName,
                    text = stringRes(R.string.dictate__panel_no_prompts),
                )
            }
        }
    }
}

/** Dictate accent (theme accent default), reused for the panel scrollbar thumb. */
private val DictateScrollbarAccent = Color(0xFF30B7E6)

/**
 * Draws a slim, always-visible scrollbar at the right edge of a [verticalScroll]ed container, but only
 * when the content actually overflows ([ScrollState.maxValue] > 0). Unlike the shared
 * `florisVerticalScroll` helper this does not fade out, so it stays as a reliable affordance while the
 * prompt list is scrollable. Apply directly after the `verticalScroll(state)` modifier (and before any
 * inner padding) so it spans the full container width.
 */
private fun Modifier.dictatePanelScrollbar(
    state: ScrollState,
    width: Dp = 5.dp,
): Modifier = drawWithContent {
    drawContent()
    val max = state.maxValue
    if (max <= 0) return@drawWithContent
    val barWidth = width.toPx()
    val viewport = size.height
    val contentHeight = viewport + max
    // Thumb height proportional to the visible fraction, with a sensible minimum so it stays grabbable.
    val thumbHeight = (viewport * viewport / contentHeight).coerceAtLeast(barWidth * 5f)
    val thumbY = (viewport - thumbHeight) * (state.value.toFloat() / max)
    val x = size.width - barWidth
    val radius = CornerRadius(barWidth / 2f, barWidth / 2f)
    // Faint full-height track + solid accent thumb.
    drawRoundRect(
        color = DictateScrollbarAccent.copy(alpha = 0.12f),
        topLeft = Offset(x, 0f),
        size = Size(barWidth, viewport),
        cornerRadius = radius,
    )
    drawRoundRect(
        color = DictateScrollbarAccent.copy(alpha = 0.85f),
        topLeft = Offset(x, thumbY),
        size = Size(barWidth, thumbHeight),
        cornerRadius = radius,
    )
}
