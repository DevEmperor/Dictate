/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate

import java.util.Locale

/**
 * A dictation language the user can transcribe in. [code] is the language hint sent to the speech
 * provider (ISO-639-1, or a BCP-47 tag for regional variants such as `zh-CN`); the special value
 * [DictateLanguages.DETECT] means "let the model auto-detect" and is never sent to the API.
 */
data class DictateLanguage(val code: String, val englishName: String) {
    /** Short uppercase badge for the smartbar chip, e.g. `DE`, `EN`, `ZH`, `YUE`. */
    val shortCode: String
        get() = code.substringBefore('-').uppercase(Locale.ROOT)

    /**
     * Human-readable name, localized to the device language when possible and falling back to the
     * bundled English name. [DictateLanguages.DETECT] is special-cased by callers (globe icon), so
     * this returns its English label.
     */
    fun displayName(): String {
        if (code == DictateLanguages.DETECT) return englishName
        val localized = Locale.forLanguageTag(code).getDisplayName(Locale.getDefault())
        return localized.ifBlank { englishName }.replaceFirstChar { it.uppercase(Locale.getDefault()) }
    }
}

/**
 * Catalog of the dictation languages ported 1:1 from the legacy Dictate app's
 * `dictate_input_languages` arrays. Users pick a subset in settings (stored comma-separated in
 * `prefs.dictate.inputLanguages`) and cycle through it on the recording bar.
 */
object DictateLanguages {
    const val DETECT = "detect"

    val all: List<DictateLanguage> = listOf(
        DictateLanguage(DETECT, "Detect automatically"),
        DictateLanguage("af", "Afrikaans"),
        DictateLanguage("sq", "Albanian"),
        DictateLanguage("ar", "Arabic"),
        DictateLanguage("hy", "Armenian"),
        DictateLanguage("az", "Azerbaijani"),
        DictateLanguage("eu", "Basque"),
        DictateLanguage("be", "Belarusian"),
        DictateLanguage("bn", "Bengali"),
        DictateLanguage("bg", "Bulgarian"),
        DictateLanguage("yue-CN", "Cantonese (CN)"),
        DictateLanguage("yue-HK", "Cantonese (HK)"),
        DictateLanguage("ca", "Catalan"),
        DictateLanguage("cs", "Czech"),
        DictateLanguage("da", "Danish"),
        DictateLanguage("nl", "Dutch"),
        DictateLanguage("en", "English"),
        DictateLanguage("et", "Estonian"),
        DictateLanguage("fi", "Finnish"),
        DictateLanguage("fr", "French"),
        DictateLanguage("gl", "Galician"),
        DictateLanguage("de", "German"),
        DictateLanguage("el", "Greek"),
        DictateLanguage("he", "Hebrew"),
        DictateLanguage("hi", "Hindi"),
        DictateLanguage("hu", "Hungarian"),
        DictateLanguage("id", "Indonesian"),
        DictateLanguage("it", "Italian"),
        DictateLanguage("ja", "Japanese"),
        DictateLanguage("kk", "Kazakh"),
        DictateLanguage("ko", "Korean"),
        DictateLanguage("lv", "Latvian"),
        DictateLanguage("lt", "Lithuanian"),
        DictateLanguage("mk", "Macedonian"),
        DictateLanguage("zh-CN", "Mandarin (CN)"),
        DictateLanguage("zh-TW", "Mandarin (TW)"),
        DictateLanguage("mr", "Marathi"),
        DictateLanguage("ne", "Nepali"),
        DictateLanguage("nn", "Nynorsk"),
        DictateLanguage("fa", "Persian"),
        DictateLanguage("pl", "Polish"),
        DictateLanguage("pt", "Portuguese"),
        DictateLanguage("pa", "Punjabi"),
        DictateLanguage("ro", "Romanian"),
        DictateLanguage("ru", "Russian"),
        DictateLanguage("sr", "Serbian"),
        DictateLanguage("sk", "Slovak"),
        DictateLanguage("sl", "Slovenian"),
        DictateLanguage("es", "Spanish"),
        DictateLanguage("sw", "Swahili"),
        DictateLanguage("sv", "Swedish"),
        DictateLanguage("ta", "Tamil"),
        DictateLanguage("th", "Thai"),
        DictateLanguage("tr", "Turkish"),
        DictateLanguage("uk", "Ukrainian"),
        DictateLanguage("ur", "Urdu"),
        DictateLanguage("vi", "Vietnamese"),
        DictateLanguage("cy", "Welsh"),
    )

    private val byCode: Map<String, DictateLanguage> = all.associateBy { it.code }

    /** Resolves a code to its [DictateLanguage], falling back to "detect" for unknown codes. */
    fun of(code: String): DictateLanguage = byCode[code] ?: all.first()

    /**
     * Finds the dictation language matching a device [locale] (e.g. the system language), or `null`
     * when none of the supported languages correspond to it. The full BCP-47 tag is tried first so
     * regional variants such as `zh-CN` / `zh-TW` resolve correctly, then the base language is used
     * as a fallback. [DETECT] is never returned.
     */
    fun matchDevice(locale: Locale): DictateLanguage? {
        val tag = locale.toLanguageTag().lowercase(Locale.ROOT)
        all.firstOrNull { it.code != DETECT && it.code.lowercase(Locale.ROOT) == tag }?.let { return it }
        val base = locale.language.lowercase(Locale.ROOT)
        if (base.isEmpty()) return null
        return all.firstOrNull {
            it.code != DETECT && it.code.substringBefore('-').lowercase(Locale.ROOT) == base
        }
    }

    /**
     * English language name for [code] (e.g. `"German"`), used as the auto-formatting *language hint*.
     * Returns `null` for [DETECT], blank, or unknown codes so the caller can substitute "unknown" – a
     * readable name guides the model far better than the bare ISO code.
     */
    fun englishNameFor(code: String?): String? {
        if (code.isNullOrEmpty() || code == DETECT) return null
        return byCode[code]?.englishName
    }

    /** Parses the comma-separated [prefs.dictate.inputLanguages] value into a sanitized subset. */
    fun parseSelection(raw: String): List<DictateLanguage> {
        val parsed = raw.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { byCode[it] }
            .distinct()
        return parsed.ifEmpty { listOf(all.first()) }
    }

    /** Serializes a subset back into the comma-separated pref format. */
    fun serializeSelection(languages: List<DictateLanguage>): String =
        languages.joinToString(",") { it.code }
}
