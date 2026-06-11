/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate.data.usage

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Accumulated per-model usage (audio seconds + tokens) used for the in-app cost estimate.
 *
 * IMPORTANT (data contract – see `docs/COMPATIBILITY.md`):
 * Database name (`usage.db`), version (`2`) and the exact `USAGE` schema are FROZEN for seamless
 * data carry-over of existing Dictate users. See [PromptsDatabaseHelper] for why this stays on raw
 * SQLite rather than Room for now.
 */
class UsageDatabaseHelper(
    context: Context,
) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE USAGE (MODEL_NAME TEXT PRIMARY KEY, AUDIO_TIME LONG, INPUT_TOKENS LONG, " +
                "OUTPUT_TOKENS LONG, MODEL_PROVIDER LONG)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion <= 1 && newVersion >= 2) {
            db.execSQL("ALTER TABLE USAGE ADD COLUMN MODEL_PROVIDER LONG DEFAULT 0")
        }
    }

    /** Adds the given deltas to the row for [model], creating it if necessary. */
    fun edit(model: String, timeToAdd: Long, inputTokensToAdd: Long, outputTokensToAdd: Long, provider: Long) {
        val db = writableDatabase
        val existing = db.rawQuery(
            "SELECT AUDIO_TIME, INPUT_TOKENS, OUTPUT_TOKENS FROM USAGE WHERE MODEL_NAME = ?",
            arrayOf(model),
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                Triple(cursor.getLong(0), cursor.getLong(1), cursor.getLong(2))
            } else {
                null
            }
        }

        if (existing == null) {
            val cv = ContentValues().apply {
                put("MODEL_NAME", model)
                put("AUDIO_TIME", timeToAdd)
                put("INPUT_TOKENS", inputTokensToAdd)
                put("OUTPUT_TOKENS", outputTokensToAdd)
                put("MODEL_PROVIDER", provider)
            }
            db.insert("USAGE", null, cv)
        } else {
            val cv = ContentValues().apply {
                put("AUDIO_TIME", existing.first + timeToAdd)
                put("INPUT_TOKENS", existing.second + inputTokensToAdd)
                put("OUTPUT_TOKENS", existing.third + outputTokensToAdd)
            }
            db.update("USAGE", cv, "MODEL_NAME = ?", arrayOf(model))
        }
        db.close()
    }

    fun reset() {
        val db = writableDatabase
        db.execSQL("DELETE FROM USAGE")
        db.close()
    }

    fun getAll(): List<UsageModel> {
        val db = readableDatabase
        val models = ArrayList<UsageModel>()
        db.rawQuery("SELECT * FROM USAGE", null).use { cursor ->
            if (cursor.moveToFirst()) {
                do {
                    models.add(
                        UsageModel(
                            modelName = cursor.getString(0),
                            audioTime = cursor.getLong(1),
                            inputTokens = cursor.getLong(2),
                            outputTokens = cursor.getLong(3),
                            provider = cursor.getLong(4),
                        )
                    )
                } while (cursor.moveToNext())
            }
        }
        db.close()
        return models
    }

    fun getCost(modelName: String): Double {
        val db = readableDatabase
        return db.rawQuery("SELECT * FROM USAGE WHERE MODEL_NAME = ?", arrayOf(modelName)).use { cursor ->
            if (cursor.moveToFirst()) {
                DictatePricing.calcModelCost(cursor.getString(0), cursor.getLong(1), cursor.getLong(2), cursor.getLong(3))
            } else {
                0.0
            }
        }
    }

    fun getTotalCost(): Double {
        val db = readableDatabase
        var total = 0.0
        db.rawQuery("SELECT * FROM USAGE", null).use { cursor ->
            if (cursor.moveToFirst()) {
                do {
                    total += DictatePricing.calcModelCost(
                        cursor.getString(0), cursor.getLong(1), cursor.getLong(2), cursor.getLong(3),
                    )
                } while (cursor.moveToNext())
            }
        }
        return total
    }

    fun getTotalAudioTime(): Long {
        val db = readableDatabase
        var total = 0L
        db.rawQuery("SELECT AUDIO_TIME FROM USAGE", null).use { cursor ->
            if (cursor.moveToFirst()) {
                do { total += cursor.getLong(0) } while (cursor.moveToNext())
            }
        }
        return total
    }

    companion object {
        const val DATABASE_NAME = "usage.db"
        const val DATABASE_VERSION = 2
    }
}
