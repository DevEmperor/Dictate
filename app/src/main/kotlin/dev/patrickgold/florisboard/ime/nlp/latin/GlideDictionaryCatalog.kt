/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.nlp.latin

/**
 * One downloadable glide-typing dictionary: a `<lang>.json` word→frequency map used by the glide
 * classifier (issue #127, phase 2). Stored under the language code so the runtime stays layout-agnostic;
 * [sizeBytes] and [sha256] are verified after download (see [GlideDictionaryManager]).
 */
data class GlideDict(
    /** Language code matching a subtype's primary locale language, e.g. "de". */
    val lang: String,
    /** Human-readable name shown in the picker, e.g. "German · Deutsch". */
    val displayName: String,
    val url: String,
    val sizeBytes: Long,
    val sha256: String,
)

/**
 * Catalog of downloadable glide-typing dictionaries, mirrored on the project's own GitHub release ([REL])
 * — same hosting pattern as the on-device Whisper models ([dev.patrickgold.florisboard.dictate.provider.LocalModelCatalog]).
 * English ships bundled in the APK ([ime/dict/en.json]) and is always available; every other language is
 * downloaded on demand when its subtype is selected.
 *
 * The dictionaries are generated with `tools/glide-dict/generate.py` from the Leipzig Corpora Collection
 * (Universität Leipzig), which preserves correct casing and diacritics. To (re-)host, upload the generated
 * `<lang>.json` files as assets of the release named below and paste each script-printed catalog line here.
 */
object GlideDictionaryCatalog {
    /** Project-hosted release holding the dictionary files. Single re-point for hosting. */
    const val REL = "https://github.com/DevEmperor/DictateKeyboard/releases/download/glide-dicts-v1"

    /**
     * The downloadable dictionaries. Populated from `generate.py` output once the files are hosted.
     * (English is intentionally absent — it is bundled in the APK.)
     */
    val all: List<GlideDict> = listOf(
        // e.g. GlideDict("de", "German · Deutsch", "$REL/de.json", 812345, "<sha256>"),
    )

    fun forLang(lang: String): GlideDict? =
        all.firstOrNull { it.lang.equals(lang, ignoreCase = true) }

    /** Language codes that have a downloadable dictionary (excludes the bundled English). */
    val downloadableLangs: Set<String> = all.map { it.lang.lowercase() }.toSet()
}
