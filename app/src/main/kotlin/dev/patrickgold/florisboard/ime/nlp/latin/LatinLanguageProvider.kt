/*
 * Copyright (C) 2022-2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.nlp.latin

import android.content.Context
import dev.patrickgold.florisboard.appContext
import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.editor.EditorContent
import dev.patrickgold.florisboard.ime.nlp.SpellingProvider
import dev.patrickgold.florisboard.ime.nlp.SpellingResult
import dev.patrickgold.florisboard.ime.nlp.SuggestionCandidate
import dev.patrickgold.florisboard.ime.nlp.SuggestionProvider
import dev.patrickgold.florisboard.lib.devtools.flogDebug
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.florisboard.lib.android.readText
import org.florisboard.lib.kotlin.guardedByLock
import java.util.concurrent.ConcurrentHashMap

class LatinLanguageProvider(context: Context) : SpellingProvider, SuggestionProvider {
    companion object {
        // Default user ID used for all subtypes, unless otherwise specified.
        // See `ime/core/Subtype.kt` Line 210 and 211 for the default usage
        const val ProviderId = "org.florisboard.nlp.providers.latin"

        // Language used when the active subtype has no bundled dictionary of its own.
        private const val FALLBACK_LANG = "en"

        // Legacy ISO-639 codes that java.util.Locale still reports; map them to the modern code the
        // dictionary files use.
        private val LANG_ALIASES = mapOf("iw" to "he", "in" to "id", "ji" to "yi")

        fun normalizeLang(language: String): String {
            val l = language.lowercase()
            return LANG_ALIASES[l] ?: l
        }
    }

    private val appContext by context.appContext()

    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        // When a dictionary finishes downloading, drop the resolved-language cache so the active subtype
        // starts using it immediately (issue #127).
        ioScope.launch {
            GlideDictionaryManager.installedVersion.collect { resolvedDictLang.clear() }
        }
    }

    // Word→frequency dictionaries cached per language (issue #127, glide typing phase 2). Each bundled
    // ime/dict/<lang>.json maps a word to a frequency in [128,255]; languages without a bundled file fall
    // back to English.
    private val wordDataByLang = guardedByLock { mutableMapOf<String, Map<String, Int>>() }
    private val wordDataSerializer = MapSerializer(String.serializer(), Int.serializer())

    // Languages that ship a bundled ime/dict/<lang>.json (currently just English), listed once.
    private val bundledDictLangs: Set<String> by lazy {
        runCatching {
            appContext.assets.list("ime/dict")
                ?.mapNotNull { name -> name.takeIf { it.endsWith(".json") }?.removeSuffix(".json") }
                ?.toSet()
        }.getOrNull().orEmpty().ifEmpty { setOf(FALLBACK_LANG) }
    }

    // Resolved (subtype language → dictionary language) cache, so the glide classifier's per-word
    // frequency lookups don't hit the filesystem. Cleared on preload so a newly downloaded dictionary is
    // picked up on the next subtype activation.
    private val resolvedDictLang = ConcurrentHashMap<String, String>()

    /** Whether a dictionary (downloaded or bundled) exists for [lang]. */
    private fun hasDict(lang: String): Boolean =
        GlideDictionaryManager.isInstalled(appContext, lang) || lang in bundledDictLangs

    /** The dictionary language to use for [subtype] — its own if available, else English. */
    private fun dictLangFor(subtype: Subtype): String {
        val subLang = normalizeLang(subtype.primaryLocale.language)
        return resolvedDictLang.getOrPut(subLang) {
            if (subLang.isNotBlank() && hasDict(subLang)) subLang else FALLBACK_LANG
        }
    }

    /** Ensures the glide dictionary for [subtype]'s language downloads on first use (issue #127). */
    private fun maybeDownloadDict(subtype: Subtype) {
        val lang = normalizeLang(subtype.primaryLocale.language)
        if (lang.isBlank() || lang in bundledDictLangs) return
        GlideDictionaryManager.ensureDownloaded(appContext, lang)
    }

    /** Raw JSON for [lang]: a downloaded dictionary takes precedence over the bundled asset. */
    private fun readDict(lang: String): String {
        val downloaded = GlideDictionaryManager.dictFile(appContext, lang)
        return if (downloaded.isFile && downloaded.length() > 0) {
            downloaded.readText()
        } else {
            appContext.assets.readText("ime/dict/$lang.json")
        }
    }

    /** Loads (and caches) the word→frequency map for [subtype]'s resolved dictionary language. */
    private suspend fun wordDataFor(subtype: Subtype): Map<String, Int> {
        val lang = dictLangFor(subtype)
        return wordDataByLang.withLock { cache ->
            cache[lang] ?: run {
                val loaded = Json.decodeFromString(wordDataSerializer, readDict(lang))
                cache[lang] = loaded
                loaded
            }
        }
    }

    override val providerId = ProviderId

    override suspend fun create() {
        // Here we initialize our provider, set up all things which are not language dependent.
    }

    override suspend fun preload(subtype: Subtype) = withContext(Dispatchers.IO) {
        // Here we have the chance to preload dictionaries and prepare a neural network for a specific language.
        // Is kept in sync with the active keyboard subtype of the user, however a new preload does not necessary mean
        // the previous language is not needed anymore (e.g. if the user constantly switches between two subtypes)

        // To read a file from the APK assets the following methods can be used:
        // appContext.assets.open()
        // appContext.assets.reader()
        // appContext.assets.bufferedReader()
        // appContext.assets.readText()
        // To copy an APK file/dir to the file system cache (appContext.cacheDir), the following methods are available:
        // appContext.assets.copy()
        // appContext.assets.copyRecursively()

        // The subtype we get here contains a lot of data, however we are only interested in subtype.primaryLocale and
        // subtype.secondaryLocales.

        // Re-resolve languages so a dictionary downloaded since the last activation is picked up, then
        // warm the cache for this subtype's dictionary language (used by glide typing / word lookups).
        resolvedDictLang.clear()
        maybeDownloadDict(subtype)
        wordDataFor(subtype)
        Unit
    }

    override suspend fun spell(
        subtype: Subtype,
        word: String,
        precedingWords: List<String>,
        followingWords: List<String>,
        maxSuggestionCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean,
    ): SpellingResult {
        return when (word.lowercase()) {
            // Use typo for typing errors
            "typo" -> SpellingResult.typo(arrayOf("typo1", "typo2", "typo3"))
            // Use grammar error if the algorithm can detect this. On Android 11 and lower grammar errors are visually
            // marked as typos due to a lack of support
            "gerror" -> SpellingResult.grammarError(arrayOf("grammar1", "grammar2", "grammar3"))
            // Use valid word for valid input
            else -> SpellingResult.validWord()
        }
    }

    override suspend fun suggest(
        subtype: Subtype,
        content: EditorContent,
        maxCandidateCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean,
    ): List<SuggestionCandidate> {
        return emptyList()
        /*val word = content.composingText.ifBlank { "next" }
        val suggestions = buildList {
            for (n in 0 until maxCandidateCount) {
                add(WordSuggestionCandidate(
                    text = "$word$n",
                    secondaryText = if (n % 2 == 1) "secondary" else null,
                    confidence = 0.5,
                    isEligibleForAutoCommit = false,//n == 0 && word.startsWith("auto"),
                    // We set ourselves as the source provider so we can get notify events for our candidate
                    sourceProvider = this@LatinLanguageProvider,
                ))
            }
        }
        return suggestions*/
    }

    override suspend fun notifySuggestionAccepted(subtype: Subtype, candidate: SuggestionCandidate) {
        // We can use flogDebug, flogInfo, flogWarning and flogError for debug logging, which is a wrapper for Logcat
        flogDebug { candidate.toString() }
    }

    override suspend fun notifySuggestionReverted(subtype: Subtype, candidate: SuggestionCandidate) {
        flogDebug { candidate.toString() }
    }

    override suspend fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate): Boolean {
        flogDebug { candidate.toString() }
        return false
    }

    override suspend fun getListOfWords(subtype: Subtype): List<String> {
        return wordDataFor(subtype).keys.toList()
    }

    override suspend fun getFrequencyForWord(subtype: Subtype, word: String): Double {
        return (wordDataFor(subtype)[word] ?: 0) / 255.0
    }

    override suspend fun destroy() {
        // Here we have the chance to de-allocate memory and finish our work. However this might never be called if
        // the app process is killed (which will most likely always be the case).
    }
}
