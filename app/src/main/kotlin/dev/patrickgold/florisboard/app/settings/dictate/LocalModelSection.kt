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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.dictate.provider.LocalModelCatalog
import dev.patrickgold.florisboard.dictate.provider.LocalModelManager
import dev.patrickgold.florisboard.dictate.provider.LocalModelSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes

/**
 * Provider-editor body for the on-device (offline) transcription provider (issue #104). Instead of an
 * API key + remote model picker, it lists the downloadable Whisper models with install / delete /
 * cancel actions and a live download progress bar, and lets the user pick which installed model is
 * active. The active model id is reported via [onActiveModelChange] and persisted by the caller when the
 * dialog is confirmed; installs/deletes take effect immediately on disk.
 */
@Composable
fun LocalModelSection(
    activeModelId: String,
    onActiveModelChange: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Bumped after an install/delete to recompute which models are present on disk.
    var refreshTick by remember { mutableStateOf(0) }
    val installed = remember(refreshTick) { LocalModelManager.installedIds(context).toSet() }

    // modelId -> download progress in 0..100 (absent = not downloading). errors: modelId -> message.
    val progress = remember { mutableStateMapOf<String, Int>() }
    val errors = remember { mutableStateMapOf<String, String>() }
    val jobs = remember { mutableMapOf<String, Job>() }
    var pendingDelete by remember { mutableStateOf<LocalModelSpec?>(null) }

    val downloadFailed = stringRes(R.string.dictate__local_model_download_failed)

    Column {
        Text(
            text = stringRes(R.string.dictate__local_models_header),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        LocalModelCatalog.all.forEach { spec ->
            ModelRow(
                spec = spec,
                isInstalled = spec.id in installed,
                isActive = spec.id == activeModelId,
                downloadPercent = progress[spec.id],
                error = errors[spec.id],
                onSelect = { if (spec.id in installed) onActiveModelChange(spec.id) },
                onInstall = {
                    errors.remove(spec.id)
                    progress[spec.id] = 0
                    jobs[spec.id] = scope.launch {
                        try {
                            LocalModelManager.download(context, spec) { done, total ->
                                val pct = if (total > 0) (done * 100 / total).toInt() else 0
                                if (progress[spec.id] != pct) progress[spec.id] = pct
                            }
                            refreshTick++
                            // Auto-select the freshly installed model if nothing usable is active yet.
                            if (activeModelId.isBlank() || activeModelId !in LocalModelManager.installedIds(context)) {
                                onActiveModelChange(spec.id)
                            }
                        } catch (_: CancellationException) {
                            // user cancelled; staging dir already cleaned up by the manager
                        } catch (_: Throwable) {
                            errors[spec.id] = downloadFailed
                        } finally {
                            progress.remove(spec.id)
                            jobs.remove(spec.id)
                        }
                    }
                },
                onCancel = { jobs[spec.id]?.cancel() },
                onDelete = { pendingDelete = spec },
            )
        }
    }

    pendingDelete?.let { spec ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(spec.displayName) },
            text = {
                Text(
                    stringRes(R.string.dictate__local_model_delete_confirm).replace("{model}", spec.displayName),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    LocalModelManager.delete(context, spec.id)
                    if (activeModelId == spec.id) onActiveModelChange("")
                    refreshTick++
                    pendingDelete = null
                }) { Text(stringRes(R.string.dictate__local_model_action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringRes(R.string.action__cancel))
                }
            },
        )
    }
}

@Composable
private fun ModelRow(
    spec: LocalModelSpec,
    isInstalled: Boolean,
    isActive: Boolean,
    downloadPercent: Int?,
    error: String?,
    onSelect: () -> Unit,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    val downloading = downloadPercent != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = isActive,
            enabled = isInstalled && !downloading,
            onClick = onSelect,
        )
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(text = spec.displayName, style = MaterialTheme.typography.titleSmall)
            val status = when {
                downloading -> stringRes(R.string.dictate__local_model_downloading)
                    .replace("{percent}", downloadPercent.toString())
                isActive -> stringRes(R.string.dictate__local_model_status_active)
                isInstalled -> stringRes(R.string.dictate__local_model_status_installed)
                else -> spec.description
            }
            Text(
                text = error ?: status,
                style = MaterialTheme.typography.bodySmall,
                color = if (error != null) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (downloading) {
                LinearProgressIndicator(
                    progress = { (downloadPercent ?: 0) / 100f },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
        when {
            downloading -> TextButton(onClick = onCancel) {
                Text(stringRes(R.string.dictate__local_model_action_cancel))
            }
            isInstalled -> TextButton(onClick = onDelete) {
                Text(stringRes(R.string.dictate__local_model_action_delete))
            }
            else -> TextButton(onClick = onInstall) {
                Text(stringRes(R.string.dictate__local_model_action_install))
            }
        }
    }
}
