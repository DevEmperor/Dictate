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

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShortText
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.dictate.DictateController
import dev.patrickgold.florisboard.dictate.data.prompts.PromptModel
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import org.florisboard.lib.snygg.ui.SnyggChip
import org.florisboard.lib.snygg.ui.SnyggRow

/**
 * The contextual prompt chip strip shown in the Smartbar's center area, in place of the candidates,
 * while there is an active text selection and rewording is enabled (roadmap 4.3). Each chip runs the
 * matching prompt via [DictateController.applyPrompt] on the current selection (or, for free prompts,
 * generates from the instruction and replaces it). Horizontally scrollable so any number of prompts
 * fits. Triggering free prompts without a selection is reserved for the Dictate panel (P4).
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
            val raw = prompt.prompt.orEmpty()
            // A `[snippet]` prompt is inserted literally (no API call); flag it with a distinct icon.
            val isSnippet = raw.length >= 2 && raw.startsWith("[") && raw.endsWith("]")
            val icon = when {
                isSnippet -> Icons.AutoMirrored.Filled.ShortText
                prompt.requiresSelection -> Icons.Default.AutoFixHigh
                else -> Icons.Default.AutoAwesome
            }
            // Reuse the already-themed action-tile element (rounded pill, padding, ripple) so the
            // chips inherit every theme without dedicated dictate-* styling (deferred polish).
            SnyggChip(
                elementName = FlorisImeUi.SmartbarActionTile.elementName,
                onClick = { DictateController.applyPrompt(context, prompt) },
                imageVector = icon,
                text = prompt.name.orEmpty(),
            )
        }
    }
}
