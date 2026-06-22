/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate.provider

import android.content.Context
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import dev.patrickgold.florisboard.dictate.audio.AudioDecode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * On-device speech-to-text (issue #104), powered by a bundled Whisper model running through sherpa-onnx.
 * No audio ever leaves the device. This is the offline counterpart to [OpenAiCompatibleClient] and plugs
 * into the same [TranscriptionProvider] seam the dictation flow already uses.
 *
 * A model is a directory under [modelsRoot] containing three fixed-name files (so this class stays
 * agnostic of the specific Whisper variant):
 *
 *   <filesDir>/dictate-models/<modelId>/encoder.onnx
 *                                       /decoder.onnx
 *                                       /tokens.txt
 *
 * The native [OfflineRecognizer] is expensive to construct (it loads the model into memory), so it is
 * cached process-wide in [RecognizerCache] and reused across transcriptions; switching models releases
 * the previous one. Decoding is CPU-bound and runs on [Dispatchers.Default].
 */
class LocalTranscriptionProvider(
    private val modelDir: File,
    private val numThreads: Int = 2,
) : TranscriptionProvider {

    override suspend fun transcribe(request: TranscriptionRequest): TranscriptionResult =
        withContext(Dispatchers.Default) {
            val encoder = File(modelDir, ENCODER)
            val decoder = File(modelDir, DECODER)
            val tokens = File(modelDir, TOKENS)
            if (!encoder.exists() || !decoder.exists() || !tokens.exists()) {
                throw DictateApiException(
                    DictateApiException.Kind.UNKNOWN,
                    "On-device model '${modelDir.name}' is not installed",
                )
            }

            val samples = try {
                AudioDecode.decodeToMono16k(request.audioFile)
            } catch (t: Throwable) {
                throw DictateApiException(
                    DictateApiException.Kind.FORMAT_NOT_SUPPORTED,
                    "Could not decode the recorded audio for on-device transcription",
                    t,
                )
            }

            val text = try {
                val recognizer = RecognizerCache.acquire(encoder, decoder, tokens, numThreads)
                val stream = recognizer.createStream()
                try {
                    stream.acceptWaveform(samples, AudioDecode.TARGET_SAMPLE_RATE)
                    recognizer.decode(stream)
                    recognizer.getResult(stream).text
                } finally {
                    stream.release()
                }
            } catch (e: DictateApiException) {
                throw e
            } catch (t: Throwable) {
                throw DictateApiException(
                    DictateApiException.Kind.UNKNOWN,
                    "On-device transcription failed",
                    t,
                )
            }

            TranscriptionResult(text.trim())
        }

    companion object {
        const val MODELS_SUBDIR = "dictate-models"
        const val ENCODER = "encoder.onnx"
        const val DECODER = "decoder.onnx"
        const val TOKENS = "tokens.txt"

        /** Root directory that holds all installed on-device models. */
        fun modelsRoot(context: Context): File = File(context.filesDir, MODELS_SUBDIR)

        /** Directory for a single model id (its `encoder.onnx`/`decoder.onnx`/`tokens.txt` live here). */
        fun modelDir(context: Context, modelId: String): File = File(modelsRoot(context), modelId)

        /** True if [modelId] has all required files present on disk. */
        fun isInstalled(context: Context, modelId: String): Boolean {
            val dir = modelDir(context, modelId)
            return File(dir, ENCODER).exists() && File(dir, DECODER).exists() && File(dir, TOKENS).exists()
        }
    }
}

/**
 * Process-wide cache of the single most-recently-used [OfflineRecognizer]. Building one loads the model
 * into native memory (~1s+ for Whisper tiny), so we keep it alive between transcriptions and only rebuild
 * when the model directory changes. Access is serialized; the app transcribes one clip at a time.
 */
private object RecognizerCache {
    private var key: String? = null
    private var recognizer: OfflineRecognizer? = null

    @Synchronized
    fun acquire(encoder: File, decoder: File, tokens: File, numThreads: Int): OfflineRecognizer {
        val cacheKey = encoder.parentFile?.absolutePath ?: encoder.absolutePath
        val existing = recognizer
        if (existing != null && cacheKey == key) return existing

        existing?.release()
        // Whisper auto-detects the language (language = ""), so the recognizer is reusable regardless of
        // the user's selected input language. Honoring a forced language is a later refinement (#104).
        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = AudioDecode.TARGET_SAMPLE_RATE, featureDim = 80),
            modelConfig = OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(
                    encoder = encoder.absolutePath,
                    decoder = decoder.absolutePath,
                    language = "",
                    task = "transcribe",
                ),
                tokens = tokens.absolutePath,
                numThreads = numThreads,
                modelType = "whisper",
            ),
        )
        // assetManager defaults to null → the model is read from the absolute file paths above.
        return OfflineRecognizer(config = config).also {
            recognizer = it
            key = cacheKey
        }
    }
}
