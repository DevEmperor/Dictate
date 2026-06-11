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

/**
 * Accumulated usage for a single model, ported 1:1 from the original Dictate `UsageModel`.
 * Backed by `usage.db` (see [UsageDatabaseHelper] and `docs/COMPATIBILITY.md`).
 */
data class UsageModel(
    val modelName: String,
    val audioTime: Long,
    val inputTokens: Long,
    val outputTokens: Long,
    val provider: Long,
)
