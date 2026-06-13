/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.app.settings.dictate

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.dictate.data.prompts.PromptModel
import dev.patrickgold.florisboard.dictate.data.prompts.PromptsDatabaseHelper
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.material.ui.JetPrefAlertDialog
import dev.patrickgold.jetpref.material.ui.JetPrefListItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.florisboard.lib.compose.florisScrollbar
import org.florisboard.lib.compose.stringRes

/**
 * Manage the user's rewording prompts (roadmap 4.2 / 4.8): list, add, edit and delete. Persisted in
 * the shared `prompts.db` via [PromptsDatabaseHelper] so prompts created here also drive the keyboard
 * chips (Phase 3) and the auto-apply chain. Replaces the legacy `PromptsOverviewActivity` /
 * `PromptEditActivity`.
 */
@Composable
fun DictatePromptsScreen() = FlorisScreen {
    title = stringRes(R.string.dictate__prompts_title)
    scrollable = false

    val context = LocalContext.current
    val db = remember { PromptsDatabaseHelper(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val prompts = remember { mutableStateListOf<PromptModel>() }
    // The prompt currently being added/edited, or null when the editor is closed. Declared here so
    // both the floatingActionButton and content lambdas share the same state.
    var editorTarget by remember { mutableStateOf<PromptModel?>(null) }

    suspend fun reload() {
        val all = withContext(Dispatchers.IO) { db.getAll() }
        prompts.clear()
        prompts.addAll(all)
    }

    floatingActionButton {
        ExtendedFloatingActionButton(
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text(stringRes(R.string.dictate__prompt_add)) },
            onClick = {
                // -1 = a not-yet-persisted prompt; defaults mirror the legacy edit screen.
                editorTarget = PromptModel(-1, 0, "", "", requiresSelection = true, autoApply = false)
            },
        )
    }

    content {
        LaunchedEffect(Unit) { reload() }

        val listState = rememberLazyListState()
        if (prompts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    modifier = Modifier.padding(horizontal = 32.dp),
                    text = stringRes(R.string.dictate__prompts_empty),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .florisScrollbar(listState, isVertical = true),
                state = listState,
            ) {
                items(prompts, key = { it.id }) { prompt ->
                    val tags = buildList {
                        if (prompt.requiresSelection) add(stringRes(R.string.dictate__prompt_badge_selection))
                        if (prompt.autoApply) add(stringRes(R.string.dictate__prompt_badge_auto))
                    }
                    val secondary = buildString {
                        if (tags.isNotEmpty()) append(tags.joinToString(" · ")).append(" — ")
                        append(prompt.prompt.orEmpty())
                    }
                    JetPrefListItem(
                        modifier = Modifier.clickable { editorTarget = prompt.copy() },
                        text = prompt.name.orEmpty(),
                        secondaryText = secondary,
                        singleLineSecondaryText = true,
                    )
                }
            }
        }

        val target = editorTarget
        if (target != null) {
            PromptEditorDialog(
                initial = target,
                onDismiss = { editorTarget = null },
                onSave = { name, text, requiresSelection, autoApply ->
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            if (target.id < 0) {
                                db.add(PromptModel(0, db.count(), name, text, requiresSelection, autoApply))
                            } else {
                                db.update(
                                    target.copy(
                                        name = name,
                                        prompt = text,
                                        requiresSelection = requiresSelection,
                                        autoApply = autoApply,
                                    ),
                                )
                            }
                        }
                        reload()
                    }
                    editorTarget = null
                },
                onDelete = if (target.id < 0) {
                    null
                } else {
                    {
                        scope.launch {
                            withContext(Dispatchers.IO) { db.delete(target.id) }
                            reload()
                        }
                        editorTarget = null
                    }
                },
            )
        }
    }
}

@Composable
private fun PromptEditorDialog(
    initial: PromptModel,
    onDismiss: () -> Unit,
    onSave: (name: String, prompt: String, requiresSelection: Boolean, autoApply: Boolean) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(initial.name.orEmpty()) }
    var text by remember { mutableStateOf(initial.prompt.orEmpty()) }
    var requiresSelection by remember { mutableStateOf(initial.requiresSelection) }
    var autoApply by remember { mutableStateOf(initial.autoApply) }
    var showError by remember { mutableStateOf(false) }

    JetPrefAlertDialog(
        title = stringRes(
            if (initial.id < 0) R.string.dictate__prompt_add else R.string.dictate__prompt_edit,
        ),
        confirmLabel = stringRes(R.string.action__save),
        onConfirm = {
            if (name.isBlank() || text.isBlank()) {
                showError = true
            } else {
                onSave(name.trim(), text.trim(), requiresSelection, autoApply)
            }
        },
        dismissLabel = stringRes(R.string.action__cancel),
        onDismiss = onDismiss,
        neutralLabel = onDelete?.let { stringRes(R.string.action__delete) },
        onNeutral = { onDelete?.invoke() },
        allowOutsideDismissal = true,
    ) {
        Column {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = name,
                onValueChange = { name = it; showError = false },
                label = { Text(stringRes(R.string.dictate__prompt_name_title)) },
                placeholder = { Text(stringRes(R.string.dictate__prompt_name_placeholder)) },
                singleLine = true,
                isError = showError && name.isBlank(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 110.dp),
                value = text,
                onValueChange = { text = it; showError = false },
                label = { Text(stringRes(R.string.dictate__prompt_text_title)) },
                placeholder = { Text(stringRes(R.string.dictate__prompt_text_placeholder)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                isError = showError && text.isBlank(),
            )
            Spacer(Modifier.height(12.dp))
            SwitchRow(
                title = stringRes(R.string.dictate__prompt_requires_selection_title),
                summary = stringRes(R.string.dictate__prompt_requires_selection_summary),
                checked = requiresSelection,
                onCheckedChange = { requiresSelection = it },
            )
            Spacer(Modifier.height(8.dp))
            SwitchRow(
                title = stringRes(R.string.dictate__prompt_auto_apply_title),
                summary = stringRes(R.string.dictate__prompt_auto_apply_summary),
                checked = autoApply,
                onCheckedChange = { autoApply = it },
            )
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title)
            Text(text = summary, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
