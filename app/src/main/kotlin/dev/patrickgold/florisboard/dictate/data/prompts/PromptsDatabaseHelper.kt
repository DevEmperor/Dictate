/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate.data.prompts

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Storage for user-defined rewording prompts.
 *
 * IMPORTANT (data contract – see `docs/COMPATIBILITY.md`):
 * Database name (`prompts.db`), version (`2`) and the exact `PROMPTS` schema are FROZEN so that
 * existing Dictate users keep their prompts after the in-place app update. Do not migrate this to
 * Room without a deliberately tested migration – Room's strict type-affinity validation would
 * reject the legacy `BOOLEAN`/`INTEGER` columns of an existing user database.
 */
class PromptsDatabaseHelper(
    private val context: Context,
) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE PROMPTS (ID INTEGER PRIMARY KEY, POS INTEGER, NAME TEXT, PROMPT TEXT, " +
                "REQUIRES_SELECTION BOOLEAN, AUTO_APPLY BOOLEAN DEFAULT 0)"
        )
        // Seed a few example prompts for fresh installs only (existing users skip onCreate).
        DEFAULT_PROMPTS.forEachIndexed { index, seed ->
            val cv = ContentValues().apply {
                put("POS", index)
                put("NAME", seed.name)
                put("PROMPT", seed.prompt)
                put("REQUIRES_SELECTION", if (seed.requiresSelection) 1 else 0)
                put("AUTO_APPLY", 0)
            }
            db.insert("PROMPTS", null, cv)
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE PROMPTS ADD COLUMN AUTO_APPLY BOOLEAN DEFAULT 0")
        }
    }

    fun add(model: PromptModel): Int {
        val db = writableDatabase
        val cv = model.toContentValues()
        return db.insert("PROMPTS", null, cv).toInt()
    }

    fun addAll(models: List<PromptModel>?) {
        if (models.isNullOrEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            models.forEach { db.insert("PROMPTS", null, it.toContentValues()) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            db.close()
        }
    }

    fun replaceAll(models: List<PromptModel>?) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("PROMPTS", null, null)
            models?.forEach { db.insert("PROMPTS", null, it.toContentValues()) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            db.close()
        }
    }

    fun update(model: PromptModel) {
        val db = writableDatabase
        db.update("PROMPTS", model.toContentValues(), "ID = ?", arrayOf(model.id.toString()))
        db.close()
    }

    fun delete(id: Int) {
        val db = writableDatabase
        db.delete("PROMPTS", "ID = ?", arrayOf(id.toString()))
        db.close()
    }

    fun get(id: Int): PromptModel? {
        val db = readableDatabase
        val model = db.rawQuery("SELECT * FROM PROMPTS WHERE ID = ?", arrayOf(id.toString())).use { cursor ->
            if (cursor.moveToFirst()) cursor.toPromptModel() else null
        }
        db.close()
        return model
    }

    fun getAll(): List<PromptModel> {
        val db = readableDatabase
        val models = ArrayList<PromptModel>()
        db.rawQuery("SELECT * FROM PROMPTS ORDER BY POS ASC", null).use { cursor ->
            if (cursor.moveToFirst()) {
                do { models.add(cursor.toPromptModel()) } while (cursor.moveToNext())
            }
        }
        db.close()
        return models
    }

    /**
     * Returns all persisted prompts plus the synthetic UI buttons in display order:
     * instant prompt, select-all, [persisted prompts…], add prompt.
     */
    fun getAllForKeyboard(): List<PromptModel> {
        val persisted = getAll()
        val result = ArrayList<PromptModel>(persisted.size + 3)
        result.add(PromptModel(PromptModel.ID_INSTANT_PROMPT, Int.MIN_VALUE, null, null, false, false))
        result.add(PromptModel(PromptModel.ID_SELECT_ALL, Int.MIN_VALUE + 1, null, null, false, false))
        result.addAll(persisted)
        result.add(PromptModel(PromptModel.ID_ADD_PROMPT, Int.MAX_VALUE, null, null, false, false))
        return result
    }

    fun getAutoApplyIds(): List<Int> {
        val db = readableDatabase
        val ids = ArrayList<Int>()
        db.rawQuery("SELECT ID FROM PROMPTS WHERE AUTO_APPLY = 1 ORDER BY POS ASC", null).use { cursor ->
            if (cursor.moveToFirst()) {
                do { ids.add(cursor.getInt(0)) } while (cursor.moveToNext())
            }
        }
        db.close()
        return ids
    }

    fun count(): Int {
        val db = readableDatabase
        val total = db.rawQuery("SELECT COUNT(*) FROM PROMPTS", null).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
        db.close()
        return total
    }

    private fun PromptModel.toContentValues() = ContentValues().apply {
        put("POS", pos)
        put("NAME", name)
        put("PROMPT", prompt)
        put("REQUIRES_SELECTION", if (requiresSelection) 1 else 0)
        put("AUTO_APPLY", if (autoApply) 1 else 0)
    }

    private fun android.database.Cursor.toPromptModel() = PromptModel(
        id = getInt(getColumnIndexOrThrow("ID")),
        pos = getInt(getColumnIndexOrThrow("POS")),
        name = getString(getColumnIndexOrThrow("NAME")),
        prompt = getString(getColumnIndexOrThrow("PROMPT")),
        requiresSelection = getInt(getColumnIndexOrThrow("REQUIRES_SELECTION")) == 1,
        autoApply = getInt(getColumnIndexOrThrow("AUTO_APPLY")) == 1,
    )

    private data class Seed(val name: String, val prompt: String, val requiresSelection: Boolean)

    companion object {
        const val DATABASE_NAME = "prompts.db"
        const val DATABASE_VERSION = 2

        // TODO(localization): move these defaults to string resources once branding is finalized.
        private val DEFAULT_PROMPTS = listOf(
            Seed("Translate to English", "Translate the following text to English:", true),
            Seed("Fix grammar", "Fix the grammar and spelling of the following text:", true),
            Seed("More formal", "Rewrite the following text in a more formal tone:", true),
            Seed("Summarize", "Summarize the following text:", true),
            Seed("Write an email", "Write a professional email about the following:", false),
        )
    }
}
