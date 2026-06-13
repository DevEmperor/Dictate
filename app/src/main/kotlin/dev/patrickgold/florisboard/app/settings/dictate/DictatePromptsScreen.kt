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

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.dictate.data.prompts.PromptModel
import dev.patrickgold.florisboard.dictate.data.prompts.PromptsDatabaseHelper
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import org.florisboard.lib.compose.FlorisIconButton
import dev.patrickgold.jetpref.material.ui.JetPrefAlertDialog
import dev.patrickgold.jetpref.material.ui.JetPrefListItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.florisboard.lib.compose.florisScrollbar
import org.florisboard.lib.compose.stringRes
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manage the user's rewording prompts (roadmap 4.2 / 4.8): list, add, edit, delete, reorder by
 * drag, and JSON export/import. Persisted in the shared `prompts.db` via [PromptsDatabaseHelper] so
 * prompts created here also drive the keyboard chips (Phase 3) and the auto-apply chain. Replaces the
 * legacy `PromptsOverviewActivity` / `PromptEditActivity`; the export/import file format
 * (`{"version":1,"prompts":[…]}`) is kept byte-compatible with the legacy app so users can carry
 * their prompt collections across.
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
    // every builder lambda (actions, fab, content) shares the same state.
    var editorTarget by remember { mutableStateOf<PromptModel?>(null) }
    // Prompts parsed from an import file, awaiting the user's replace-vs-add choice.
    var pendingImport by remember { mutableStateOf<List<PromptModel>?>(null) }

    fun toast(resId: Int) = Toast.makeText(context, resId, Toast.LENGTH_SHORT).show()

    suspend fun reload() {
        val all = withContext(Dispatchers.IO) { db.getAll() }
        prompts.clear()
        prompts.addAll(all)
    }

    // Persist the current in-memory order (POS = list index), preserving ids so auto-apply and the
    // keyboard chips keep working. Only rows whose position actually changed are written.
    suspend fun persistOrder() {
        withContext(Dispatchers.IO) {
            prompts.forEachIndexed { index, prompt ->
                if (prompt.pos != index) db.update(prompt.copy(pos = index))
            }
        }
        reload()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) { exportPrompts(context, uri, db.getAll()) }
            toast(if (ok) R.string.dictate__prompts_export_success else R.string.dictate__prompts_export_failed)
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val parsed = withContext(Dispatchers.IO) { importPrompts(context, uri) }
            when {
                parsed == null -> toast(R.string.dictate__prompts_import_failed)
                parsed.isEmpty() -> toast(R.string.dictate__prompts_import_no_prompts)
                else -> pendingImport = parsed
            }
        }
    }

    actions {
        var menuExpanded by remember { mutableStateOf(false) }
        FlorisIconButton(
            onClick = { menuExpanded = true },
            icon = Icons.Default.MoreVert,
        )
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Default.FileUpload, contentDescription = null) },
                text = { Text(stringRes(R.string.dictate__prompts_export)) },
                onClick = {
                    menuExpanded = false
                    exportLauncher.launch(context.getString(R.string.dictate__prompts_export_filename))
                },
            )
            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                text = { Text(stringRes(R.string.dictate__prompts_import)) },
                onClick = {
                    menuExpanded = false
                    importLauncher.launch(arrayOf("application/json"))
                },
            )
        }
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
        // Id of the prompt being dragged, plus its current finger offset (px) for the lift effect.
        var draggingId by remember { mutableStateOf<Int?>(null) }
        var dragOffset by remember { mutableStateOf(0f) }

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
                itemsIndexed(prompts, key = { _, it -> it.id }) { index, prompt ->
                    val isDragging = draggingId == prompt.id
                    val tags = buildList {
                        if (prompt.requiresSelection) add(stringRes(R.string.dictate__prompt_badge_selection))
                        if (prompt.autoApply) add(stringRes(R.string.dictate__prompt_badge_auto))
                    }
                    val secondary = buildString {
                        if (tags.isNotEmpty()) append(tags.joinToString(" · ")).append(" — ")
                        append(prompt.prompt.orEmpty())
                    }
                    // The whole row both edits (tap) and reorders (long-press drag); the handle is a
                    // visual affordance. Lift the dragged row above the rest via zIndex + translation.
                    Surface(
                        tonalElevation = if (isDragging) 4.dp else 0.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer { translationY = if (isDragging) dragOffset else 0f }
                            .pointerInput(prompt.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { draggingId = prompt.id; dragOffset = 0f },
                                    onDragEnd = {
                                        draggingId = null
                                        dragOffset = 0f
                                        scope.launch { persistOrder() }
                                    },
                                    onDragCancel = {
                                        draggingId = null
                                        dragOffset = 0f
                                        scope.launch { reload() }
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount.y
                                        val curIndex = prompts.indexOfFirst { it.id == draggingId }
                                        if (curIndex < 0) return@detectDragGesturesAfterLongPress
                                        val itemHeight = listState.layoutInfo.visibleItemsInfo
                                            .firstOrNull { it.index == curIndex }?.size
                                            ?: return@detectDragGesturesAfterLongPress
                                        // Cross the half-item threshold → swap with the neighbour and
                                        // carry the offset over so the row stays under the finger.
                                        if (dragOffset > itemHeight / 2 && curIndex < prompts.lastIndex) {
                                            prompts.add(curIndex + 1, prompts.removeAt(curIndex))
                                            dragOffset -= itemHeight
                                        } else if (dragOffset < -itemHeight / 2 && curIndex > 0) {
                                            prompts.add(curIndex - 1, prompts.removeAt(curIndex))
                                            dragOffset += itemHeight
                                        }
                                    },
                                )
                            },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            JetPrefListItem(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { editorTarget = prompt.copy() },
                                text = prompt.name.orEmpty(),
                                secondaryText = secondary,
                                singleLineSecondaryText = true,
                            )
                            Icon(
                                modifier = Modifier.padding(end = 16.dp),
                                imageVector = Icons.Default.DragHandle,
                                contentDescription = null,
                            )
                        }
                    }
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

        val imported = pendingImport
        if (imported != null) {
            ImportModeDialog(
                onDismiss = { pendingImport = null },
                onReplace = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            db.replaceAll(imported.mapIndexed { i, p -> p.copy(id = 0, pos = i) })
                        }
                        reload()
                        toast(R.string.dictate__prompts_import_success)
                    }
                    pendingImport = null
                },
                onAdd = {
                    scope.launch {
                        val start = withContext(Dispatchers.IO) { db.count() }
                        withContext(Dispatchers.IO) {
                            db.addAll(imported.mapIndexed { i, p -> p.copy(id = 0, pos = start + i) })
                        }
                        reload()
                        toast(R.string.dictate__prompts_import_success)
                    }
                    pendingImport = null
                },
            )
        }
    }
}

/** Serialises [prompts] as `{"version":1,"prompts":[…]}` (legacy-compatible). Returns success. */
private fun exportPrompts(context: android.content.Context, uri: Uri, prompts: List<PromptModel>): Boolean {
    return runCatching {
        val array = JSONArray()
        prompts.forEach { p ->
            array.put(
                JSONObject()
                    .put("name", p.name.orEmpty())
                    .put("prompt", p.prompt.orEmpty())
                    .put("requiresSelection", p.requiresSelection)
                    .put("autoApply", p.autoApply),
            )
        }
        val root = JSONObject().put("version", 1).put("prompts", array)
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(root.toString(2).toByteArray(Charsets.UTF_8))
        } ?: return false
        true
    }.getOrDefault(false)
}

/**
 * Reads prompts from [uri]. Accepts both the wrapped form (`{"version":…,"prompts":[…]}`) and a bare
 * top-level array. Returns null on read/parse error, or the (possibly empty) list of valid prompts.
 */
private fun importPrompts(context: android.content.Context, uri: Uri): List<PromptModel>? {
    return runCatching {
        val json = context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        } ?: return null
        val array = runCatching { JSONObject(json).optJSONArray("prompts") }.getOrNull()
            ?: JSONArray(json)
        val result = ArrayList<PromptModel>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val name = obj.optString("name", "")
            val prompt = obj.optString("prompt", "")
            if (name.isEmpty() || prompt.isEmpty()) continue
            result.add(
                PromptModel(
                    id = 0,
                    pos = result.size,
                    name = name,
                    prompt = prompt,
                    requiresSelection = obj.optBoolean("requiresSelection", false),
                    autoApply = obj.optBoolean("autoApply", false),
                ),
            )
        }
        result
    }.getOrNull()
}

@Composable
private fun ImportModeDialog(
    onDismiss: () -> Unit,
    onReplace: () -> Unit,
    onAdd: () -> Unit,
) {
    JetPrefAlertDialog(
        title = stringRes(R.string.dictate__prompts_import_mode_title),
        confirmLabel = stringRes(R.string.dictate__prompts_import_mode_replace),
        onConfirm = onReplace,
        neutralLabel = stringRes(R.string.dictate__prompts_import_mode_add),
        onNeutral = onAdd,
        dismissLabel = stringRes(R.string.action__cancel),
        onDismiss = onDismiss,
        allowOutsideDismissal = true,
    ) {
        Text(text = stringRes(R.string.dictate__prompts_import_mode_message))
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
