/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.media.emoji

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.emoji2.text.EmojiCompat
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.ime.input.LocalInputFeedbackController
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.subtypeManager
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.ui.SnyggIcon
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery

/** Hard cap on the number of emoji search results, to keep the horizontal strip snappy. */
private const val MaxSearchResults = 60

/**
 * The in-keyboard emoji search panel (issue #110). Shown in place of the Smartbar while a search is active
 * (see [KeyboardManager.emojiSearchQuery]); the user's own keyboard layout below it is used to type the
 * query, which is intercepted in the input pipeline. Matching emojis are shown in a scrollable strip and
 * tapping one inserts it directly (bypassing the query interception) while keeping the search open.
 */
@Composable
fun EmojiSearchPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val editorInstance by context.editorInstance()
    val subtypeManager by context.subtypeManager()
    val prefs by FlorisPreferenceStore
    val scope = rememberCoroutineScope()
    val inputFeedbackController = LocalInputFeedbackController.current

    val query = keyboardManager.emojiSearchQuery.collectAsState().value ?: return
    val preferredSkinTone by prefs.emoji.preferredSkinTone.collectAsState()

    val activeEditorInfo by editorInstance.activeInfoFlow.collectAsState()
    val emojiCompatInstance by FlorisEmojiCompat
        .getAsFlow(activeEditorInfo.emojiCompatReplaceAll).collectAsState()

    // Load the emoji set for the active subtype's language so the query can match localized names and
    // keywords (e.g. "Herz" for a German layout). Fall back to the English root set for languages that
    // don't ship a dedicated emoji file, so search always returns something.
    val locale = subtypeManager.activeSubtype.primaryLocale
    var emojiData by remember { mutableStateOf(EmojiData.Fallback) }
    LaunchedEffect(locale) {
        val localized = EmojiData.get(context, locale)
        emojiData = if (localized.byCategory.values.any { it.isNotEmpty() }) {
            localized
        } else {
            EmojiData.get(context, "ime/media/emoji/root.txt")
        }
    }
    val systemFontPaint = remember(Typeface.DEFAULT) {
        Paint().apply { typeface = Typeface.DEFAULT }
    }
    val metadataVersion = activeEditorInfo.emojiCompatMetadataVersion
    // Same support test the palette uses: prefer the EmojiCompat match and only fall back to the system
    // font. Using hasGlyph() alone (with the default typeface) rejects most emojis, leaving no results.
    val isSupported: (String) -> Boolean = { value ->
        emojiCompatInstance?.getEmojiMatch(value, metadataVersion) == EmojiCompat.EMOJI_SUPPORTED ||
            systemFontPaint.hasGlyph(value)
    }
    // Scan all ~3700 emojis (incl. a native hasGlyph() check per emoji) off the main thread so typing
    // stays smooth. A null value means "still computing" and lets us avoid flashing the no-results text.
    val results by produceState<List<EmojiSet>?>(null, query, emojiData, emojiCompatInstance, metadataVersion) {
        value = withContext(Dispatchers.Default) { searchEmojis(emojiData, query, isSupported) }
    }

    SnyggRow(
        elementName = FlorisImeUi.SmartbarCandidatesRow.elementName,
        modifier = modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SnyggIcon(
            imageVector = Icons.Default.Search,
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .size(20.dp),
        )
        val rowStyle = rememberSnyggThemeQuery(FlorisImeUi.SmartbarCandidatesRow.elementName)
        Text(
            modifier = Modifier
                .widthIn(min = 48.dp, max = 120.dp)
                .padding(end = 8.dp),
            text = query.ifBlank { stringRes(R.string.emoji__search__hint) },
            color = rowStyle.foreground(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentAlignment = Alignment.CenterStart,
        ) {
            val current = results
            when {
                // Blank query: the placeholder hint is already shown in the query slot. While computing
                // (current == null) we stay empty to avoid a flicker before the first results arrive.
                query.isBlank() || current == null -> Unit
                current.isEmpty() -> {
                    Text(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        text = stringRes(R.string.emoji__search__no_results),
                        color = rowStyle.foreground(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                else -> LazyRow(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items(current) { emojiSet ->
                        val emoji = emojiSet.base(withSkinTone = preferredSkinTone)
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(40.dp)
                                .pointerInput(emoji.value) {
                                    detectTapGestures(
                                        onTap = {
                                            inputFeedbackController.keyPress(TextKeyData.UNSPECIFIED)
                                            // Commit straight to the editor: routing through the dispatcher
                                            // would be swallowed by the active search-query interception.
                                            editorInstance.commitText(emoji.value)
                                            scope.launch { EmojiHistoryHelper.markEmojiUsed(prefs, emoji) }
                                        },
                                    )
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            EmojiText(text = emoji.value, emojiCompatInstance = emojiCompatInstance)
                        }
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            inputFeedbackController.keyPress(TextKeyData.UNSPECIFIED)
                            keyboardManager.closeEmojiSearch()
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            SnyggIcon(
                imageVector = Icons.Default.Close,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .size(20.dp),
            )
        }
    }
}

/**
 * Ranks the supported emojis of [data] against [query] by name (weighted by how much of the name the query
 * covers) and keyword match, mirroring the scoring used by the inline emoji suggestion provider. Only
 * emojis the system font can render are considered, so results match what the grid would actually display.
 */
private fun searchEmojis(data: EmojiData, query: String, isSupported: (String) -> Boolean): List<EmojiSet> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    return data.byCategory.values.asSequence()
        .flatten()
        .filter { isSupported(it.emojis.first().value) }
        .map { set ->
            val emoji = set.emojis.first()
            val nameWeight = emoji.name.containsWeighted(q)
            val keywordWeight = if (emoji.keywords.any { it.contains(q, ignoreCase = true) }) 1.0 else 0.0
            set to (nameWeight * 0.7 + keywordWeight * 0.3)
        }
        .filter { it.second > 0.0 }
        .sortedByDescending { it.second }
        .take(MaxSearchResults)
        .map { it.first }
        .toList()
}

private fun String.containsWeighted(other: String): Double {
    return if (contains(other, ignoreCase = true)) {
        other.length.toDouble() / length.toDouble()
    } else {
        0.0
    }
}
