/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.app

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.patrickgold.florisboard.BuildConfig
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.lib.util.AppVersionUtils
import dev.patrickgold.florisboard.lib.util.launchUrl
import dev.patrickgold.jetpref.material.ui.JetPrefAlertDialog
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes

/**
 * A "What's new" dialog shown once after the app was updated to a new version. It relies on the
 * existing [AppVersionUtils] version bookkeeping: it appears when [AppVersionUtils.shouldShowChangelog]
 * is true (i.e. the user updated rather than freshly installed) and marks the changelog as seen on
 * dismissal so it does not reappear on the next launch.
 *
 * The dialog body shows [R.string.changelog__current] (the per-release summary) and offers a link to
 * the full online changelog/releases page.
 *
 * Note: for debug/CI builds the version name carries a suffix that [AppVersionUtils] cannot parse, so
 * the dialog only surfaces on proper release builds — matching the upstream behavior.
 */
@Composable
fun ChangelogDialog() {
    val context = LocalContext.current
    val prefs by FlorisPreferenceStore
    val scope = rememberCoroutineScope()

    var visible by rememberSaveable {
        mutableStateOf(AppVersionUtils.shouldShowChangelog(context, prefs))
    }
    if (!visible) return

    fun markSeenAndClose() {
        scope.launch { AppVersionUtils.updateVersionLastChangelog(context, prefs) }
        visible = false
    }

    JetPrefAlertDialog(
        title = stringRes(R.string.changelog__dialog_title),
        confirmLabel = stringRes(R.string.action__ok),
        onConfirm = { markSeenAndClose() },
        neutralLabel = stringRes(R.string.changelog__view_all),
        onNeutral = {
            context.launchUrl(R.string.florisboard__changelog_url, "version" to BuildConfig.VERSION_NAME)
            markSeenAndClose()
        },
        onDismiss = { markSeenAndClose() },
    ) {
        Text(text = stringRes(R.string.changelog__current))
    }
}
