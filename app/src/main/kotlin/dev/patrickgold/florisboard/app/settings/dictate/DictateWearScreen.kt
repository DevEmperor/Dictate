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

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Key
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.ui.SwitchPreference
import org.florisboard.lib.compose.stringRes

/**
 * Wear OS companion settings (#106/#130): the two watch-related options that are also mirrored on the
 * watch's own Dictate app. Changing either here re-publishes the settings snapshot to the watch, and
 * the watch can flip the same toggles back (the change syncs to the phone via the Data Layer).
 *
 *  - **Standalone / sync key**: ship the API key to the watch so it can dictate when the phone is out of
 *    range (off → the watch is tether-only and the key never leaves the phone).
 *  - **Auto-rewording on the watch**: apply the same auto-rewording to watch dictations as on the phone
 *    (tethered ones are reworded by the phone, standalone ones by the watch).
 */
@Composable
fun DictateWearScreen() = FlorisScreen {
    title = stringRes(R.string.dictate__wear_title)
    previewFieldVisible = true
    iconSpaceReserved = true

    val prefs by FlorisPreferenceStore

    content {
        SwitchPreference(
            prefs.dictate.wearStandaloneEnabled,
            icon = Icons.Default.Key,
            title = stringRes(R.string.dictate__wear_standalone_title),
            summary = stringRes(R.string.dictate__wear_standalone_summary),
        )
        SwitchPreference(
            prefs.dictate.wearAutoRewordingEnabled,
            icon = Icons.Default.AutoFixHigh,
            title = stringRes(R.string.dictate__wear_auto_rewording_title),
            summary = stringRes(R.string.dictate__wear_auto_rewording_summary),
        )
    }
}
