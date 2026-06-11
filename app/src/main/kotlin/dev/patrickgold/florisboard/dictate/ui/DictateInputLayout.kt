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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.ime.ImeUiMode
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.keyboardManager
import org.florisboard.lib.snygg.ui.SnyggColumn
import org.florisboard.lib.snygg.ui.SnyggIcon
import org.florisboard.lib.snygg.ui.SnyggIconButton
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.SnyggText

/**
 * The Dictate AI voice panel, rendered as its own [ImeUiMode.DICTATE] next to the regular typing
 * keyboard (see `ImeWindow`). Reached via the microphone QuickAction in the Smartbar.
 *
 * This is the UI scaffold: it owns the back-to-keyboard action and a record toggle with status
 * feedback. The actual recording / transcription / rewording wiring is added in a later step
 * (RecordingController + the provider layer in `dictate/provider`).
 *
 * NOTE: it currently reuses the already-themed `media-*` Snygg elements so it inherits the active
 * theme with zero stylesheet changes. Dedicated `dictate-*` theme elements will be introduced
 * together with the custom onboarding/settings work.
 */
@Composable
fun DictateInputLayout(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()

    // Placeholder local state until the RecordingController is wired up.
    var isRecording by remember { mutableStateOf(false) }

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
                // TODO: move to strings.xml (R.string.dictate__panel_title) once the Dictate
                //  resource set is introduced with the custom onboarding.
                text = "Dictate",
            )
        }

        // Center: the record toggle and its status line.
        SnyggColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SnyggIconButton(
                elementName = FlorisImeUi.MediaBottomRowButton.elementName,
                onClick = { isRecording = !isRecording },
                modifier = Modifier.size(72.dp),
            ) {
                SnyggIcon(
                    imageVector = if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                    modifier = Modifier.size(40.dp),
                )
            }
            SnyggText(
                elementName = FlorisImeUi.MediaEmojiSubheader.elementName,
                modifier = Modifier.padding(top = 12.dp),
                // TODO: localize via strings.xml together with the Dictate resource set.
                text = if (isRecording) "Aufnahme läuft … (Logik folgt)" else "Tippen zum Diktieren",
            )
        }
    }
}
