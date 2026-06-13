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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.dictate.DictateController
import dev.patrickgold.florisboard.ime.ImeUiMode
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.keyboardManager
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggChip
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

        // Body: all saved prompts as tappable chips, or an empty-state hint.
        if (prompts.isEmpty()) {
            SnyggBox(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 24.dp),
            ) {
                SnyggText(
                    elementName = FlorisImeUi.MediaEmojiSubheader.elementName,
                    text = stringRes(R.string.dictate__panel_no_prompts),
                )
            }
        } else {
            FlowRow(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(8.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                prompts.forEach { prompt ->
                    SnyggChip(
                        elementName = FlorisImeUi.SmartbarActionTile.elementName,
                        modifier = Modifier.padding(4.dp),
                        onClick = {
                            DictateController.applyPrompt(context, prompt)
                            // Return to the keyboard so the field + the Smartbar progress are visible.
                            keyboardManager.activeState.imeUiMode = ImeUiMode.TEXT
                        },
                        imageVector = dictatePromptIcon(prompt),
                        text = prompt.name.orEmpty(),
                    )
                }
            }
        }
    }
}
