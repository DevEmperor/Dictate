/*
 * Copyright (C) 2026 The Dictate Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */

package net.devemperor.dictate.wear.emoji

import android.content.Context

/**
 * Recently-used emoji for the watch picker (#106). Most-recent-first, capped, persisted in
 * SharedPreferences. Stored as a single newline-joined string (emoji never contain newlines).
 */
object WearEmojiRecents {
    private const val PREFS = "dictate_wear_emoji"
    private const val KEY = "recents"
    private const val MAX = 24

    fun get(context: Context): List<String> {
        val raw = prefs(context).getString(KEY, "").orEmpty()
        return if (raw.isEmpty()) emptyList() else raw.split('\n').filter { it.isNotEmpty() }
    }

    /** Records [emoji] as the most recent, de-duplicated, capped at [MAX]. */
    fun add(context: Context, emoji: String) {
        val updated = (listOf(emoji) + get(context).filter { it != emoji }).take(MAX)
        prefs(context).edit().putString(KEY, updated.joinToString("\n")).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
