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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShortText
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.dictate.DictateController
import dev.patrickgold.florisboard.dictate.data.prompts.PromptModel
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.ui.SnyggIcon
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.SnyggText

/**
 * The contextual prompt chip strip shown in the Smartbar's center area, in place of the candidates,
 * while there is an active text selection and rewording is enabled (roadmap 4.3). Each chip runs the
 * matching prompt via [DictateController.applyPrompt] on the current selection (or, for free prompts,
 * generates from the instruction and replaces it). Horizontally scrollable so any number of prompts
 * fits. Used only in the PANEL layout mode; the ROW mode shows [DictatePromptRow] above the Smartbar.
 */
@Composable
fun DictatePromptStrip(
    prompts: List<PromptModel>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    SnyggRow(
        elementName = FlorisImeUi.SmartbarSharedActionsRow.elementName,
        modifier = modifier
            .fillMaxSize()
            .horizontalScroll(scrollState)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        prompts.forEach { prompt ->
            DictatePromptChip(
                icon = dictatePromptIcon(prompt),
                text = prompt.name.orEmpty(),
                onClick = { DictateController.applyPrompt(context, prompt) },
                modifier = Modifier.padding(horizontal = 3.dp),
            )
        }
    }
}

/**
 * The always-on rewording prompt row (ROW layout mode): a horizontally scrollable strip pinned above
 * the Smartbar, so every prompt is one tap away without opening a panel. The live-prompt chip is the
 * first (leftmost) entry, followed by the user's saved prompts. Tapping a prompt reword-applies it to
 * the current selection (or selects the whole field first); tapping the live chip records a spoken
 * instruction.
 */
@Composable
fun DictatePromptRow(
    prompts: List<PromptModel>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    SnyggRow(
        elementName = FlorisImeUi.SmartbarSharedActionsRow.elementName,
        modifier = modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.smartbarHeight)
            .horizontalScroll(scrollState)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DictateLivePromptChip(
            onClick = { DictateController.startLivePrompt(context) },
            modifier = Modifier.padding(horizontal = 3.dp),
        )
        prompts.forEach { prompt ->
            DictatePromptChip(
                icon = dictatePromptIcon(prompt),
                text = prompt.name.orEmpty(),
                onClick = { DictateController.applyPrompt(context, prompt) },
                modifier = Modifier.padding(horizontal = 3.dp),
            )
        }
    }
}

/**
 * A single rewording prompt chip: a themed rounded pill (reusing the Smartbar action-tile element so
 * it inherits the active theme) with a small leading icon, a gap, the label and some trailing room.
 * Shared by the contextual strip, the always-on row and the prompt panel so the styling stays in sync.
 */
@Composable
internal fun DictatePromptChip(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SnyggRow(
        elementName = FlorisImeUi.SmartbarActionTile.elementName,
        modifier = modifier,
        clickAndSemanticsModifier = Modifier.clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SnyggIcon(
            elementName = "${FlorisImeUi.SmartbarActionTile.elementName}-icon",
            imageVector = icon,
            // Slightly smaller than the theme default so the icon reads as a hint, not the focus.
            modifier = Modifier.size(18.dp),
        )
        // Clear gap between the icon and the label.
        Spacer(modifier = Modifier.width(6.dp))
        SnyggText(
            elementName = "${FlorisImeUi.SmartbarActionTile.elementName}-text",
            // A touch of trailing room so the label never hugs the pill's right edge.
            modifier = Modifier.padding(end = 6.dp),
            text = text,
        )
    }
}

/**
 * The live-prompt chip (sparkle + "Live prompt"): records a spoken instruction and hands it to the
 * rewording model. Rendered alongside the saved-prompt chips in the panel/row so the live prompt lives
 * with the other prompts instead of as a separate Smartbar button.
 */
@Composable
internal fun DictateLivePromptChip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DictatePromptChip(
        icon = Icons.Default.AutoAwesome,
        text = stringRes(R.string.quick_action__dictate_live_prompt),
        onClick = onClick,
        modifier = modifier,
    )
}

/**
 * Distinct icon per prompt type, matching the management screen so users learn one set:
 *  - `[snippet]` (inserted literally, no API call) → short-text glyph
 *  - "requires selection" (rewrites the highlighted/whole text) → select-all glyph
 *  - free prompt (generates from the instruction) → sparkle glyph
 */
internal fun dictatePromptIcon(prompt: PromptModel): ImageVector {
    val raw = prompt.prompt.orEmpty()
    val isSnippet = raw.length >= 2 && raw.startsWith("[") && raw.endsWith("]")
    return when {
        isSnippet -> Icons.AutoMirrored.Filled.ShortText
        prompt.requiresSelection -> Icons.Default.SelectAll
        else -> Icons.Default.AutoAwesome
    }
}
