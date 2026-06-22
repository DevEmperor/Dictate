/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import androidx.test.platform.app.InstrumentationRegistry
import dev.patrickgold.florisboard.dictate.audio.AudioDecode
import dev.patrickgold.florisboard.dictate.provider.LocalModelCatalog
import dev.patrickgold.florisboard.dictate.provider.LocalModelManager
import dev.patrickgold.florisboard.dictate.provider.LocalTranscriptionProvider
import dev.patrickgold.florisboard.dictate.provider.TranscriptionRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteOrder

/**
 * Issue #104 — Phase 0 feasibility spike for on-device STT via sherpa-onnx.
 *
 * This is NOT a permanent unit test; it is the executable proof that the vendored sherpa-onnx
 * native libraries (arm64-v8a) load through JNI on a real device and produce a transcript from a
 * 16 kHz mono PCM WAV with a Whisper model. It runs only when the model + sample wav have been
 * pushed to the device (otherwise it is skipped via `assumeTrue`), e.g.:
 *
 *   adb shell mkdir -p /data/local/tmp/sherpa-spike
 *   adb push tiny-encoder.int8.onnx tiny-decoder.int8.onnx tiny-tokens.txt /data/local/tmp/sherpa-spike/
 *   adb push test_wavs/0.wav /data/local/tmp/sherpa-spike/0.wav
 *   adb shell "chmod 755 /data/local/tmp/sherpa-spike; chmod 644 /data/local/tmp/sherpa-spike/0.wav ..."
 *
 * Then: ./gradlew :app:connectedDebugAndroidTest \
 *           -Pandroid.testInstrumentationRunnerArguments.class=dev.patrickgold.florisboard.SherpaOnnxSpikeTest
 */
@RunWith(AndroidJUnit4::class)
class SherpaOnnxSpikeTest {

    private val dir = "/data/local/tmp/sherpa-spike"

    /** Phase 0 proof: a pre-made 16 kHz WAV transcribes on-device (no audio decode involved). */
    @Test
    fun transcribesWavWithWhisperTinyOnDevice() {
        val wav = File(dir, "0.wav")
        assumeModelPresent(extra = wav)
        val (samples, sampleRate) = readPcm16Wav(wav)
        Log.i(TAG, "wav: ${samples.size} samples @ ${sampleRate}Hz")
        val text = transcribe(samples, sampleRate, label = "wav")
        assertContainsYellow(text)
    }

    /**
     * Phase 1 proof (issue #104): a recorder-format AAC/m4a (44.1 kHz mono) is decoded by
     * [AudioDecode] to 16 kHz mono float and then transcribed on-device — the full local pipeline.
     */
    @Test
    fun decodesM4aThenTranscribesOnDevice() {
        val m4a = File(dir, "test0.m4a")
        assumeModelPresent(extra = m4a)

        val startDecode = System.currentTimeMillis()
        val samples = AudioDecode.decodeToMono16k(m4a)
        val decodeMs = System.currentTimeMillis() - startDecode
        val seconds = samples.size / AudioDecode.TARGET_SAMPLE_RATE.toFloat()
        val peak = samples.maxOf { kotlin.math.abs(it) }
        Log.i(TAG, "AudioDecode: ${samples.size} samples @ ${AudioDecode.TARGET_SAMPLE_RATE}Hz " +
            "(${seconds}s, peak=$peak) in ${decodeMs}ms")

        // The source clip is ~6.6 s of speech; verify the resample produced a plausible 16 kHz mono
        // track with real energy (not silence / not wrong rate).
        assert(seconds in 6.0f..7.2f) { "Decoded duration $seconds s outside expected ~6.6 s" }
        assert(peak > 0.05f) { "Decoded audio looks silent (peak=$peak)" }

        val text = transcribe(samples, AudioDecode.TARGET_SAMPLE_RATE, label = "m4a")
        assertContainsYellow(text)
    }

    /**
     * Phase 2 proof (issue #104): the full [LocalTranscriptionProvider] path — install the model into
     * the app's filesDir under the fixed encoder/decoder/tokens names, then transcribe a recorded-format
     * m4a through the public TranscriptionProvider interface (decode + cached recognizer included).
     */
    @Test
    fun localProviderTranscribesThroughInterface() {
        val m4a = File(dir, "test0.m4a")
        assumeModelPresent(extra = m4a)

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelId = "whisper-tiny"
        val modelDir = LocalTranscriptionProvider.modelDir(context, modelId).apply { mkdirs() }
        // Install under the provider's fixed file names.
        encoder.copyTo(File(modelDir, LocalTranscriptionProvider.ENCODER), overwrite = true)
        decoder.copyTo(File(modelDir, LocalTranscriptionProvider.DECODER), overwrite = true)
        tokens.copyTo(File(modelDir, LocalTranscriptionProvider.TOKENS), overwrite = true)
        assert(LocalTranscriptionProvider.isInstalled(context, modelId)) { "model install failed" }

        // Copy the m4a into the app sandbox to mimic a real recording file.
        val audio = File(context.cacheDir, "local-provider-test.m4a")
        m4a.copyTo(audio, overwrite = true)

        val provider = LocalTranscriptionProvider(modelDir)
        val start = System.currentTimeMillis()
        val result = runBlocking {
            provider.transcribe(TranscriptionRequest(audioFile = audio, model = modelId))
        }
        Log.i(TAG, "[provider] transcript (${System.currentTimeMillis() - start}ms): \"${result.text}\"")

        modelDir.deleteRecursively()
        audio.delete()
        assertContainsYellow(result.text)
    }

    /**
     * Phase 3a proof (issue #104): the full download → verify → install → transcribe loop via
     * [LocalModelManager]. Gated behind an instrumentation arg because it pulls ~99 MB over the network:
     *
     *   ./gradlew :app:connectedDebugAndroidTest \
     *     -Pandroid.testInstrumentationRunnerArguments.class=dev.patrickgold.florisboard.SherpaOnnxSpikeTest#downloadsInstallsAndTranscribes \
     *     -Pandroid.testInstrumentationRunnerArguments.runDownloadTest=1
     */
    @Test
    fun downloadsInstallsAndTranscribes() {
        val runIt = InstrumentationRegistry.getArguments().getString("runDownloadTest") == "1"
        assumeTrue("set runDownloadTest=1 to run the ~99 MB download test", runIt)
        val m4a = File(dir, "test0.m4a")
        assumeTrue("test0.m4a not pushed to $dir — skipping", m4a.exists())

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val spec = LocalModelCatalog.WHISPER_TINY
        LocalModelManager.delete(context, spec.id) // clean slate

        val start = System.currentTimeMillis()
        var lastLogged = -1
        runBlocking {
            LocalModelManager.download(context, spec) { done, total ->
                val pct = (done * 100 / total).toInt()
                if (pct / 10 != lastLogged / 10) { Log.i(TAG, "download $pct%"); lastLogged = pct }
            }
        }
        Log.i(TAG, "download+install (incl. checksum verify) in ${System.currentTimeMillis() - start}ms")
        assert(LocalModelManager.isInstalled(context, spec.id)) { "model not installed after download" }

        val audio = File(context.cacheDir, "dl-test.m4a").also { m4a.copyTo(it, overwrite = true) }
        val result = runBlocking {
            LocalTranscriptionProvider(LocalTranscriptionProvider.modelDir(context, spec.id))
                .transcribe(TranscriptionRequest(audioFile = audio, model = spec.id))
        }
        Log.i(TAG, "[downloaded] transcript: \"${result.text}\"")

        audio.delete()
        LocalModelManager.delete(context, spec.id) // leave device clean
        assertContainsYellow(result.text)
    }

    /**
     * Regression proof (issue #104): a ~40 s clip whose final words ("…the squalid quarter of the
     * brothels", from the appended 0.wav past the 30 s mark) only survive if VAD segmentation kicked in.
     * Requires a model + silero_vad.onnx + long.m4a pushed to [dir].
     */
    @Test
    fun vadSegmentsLongAudioThroughProvider() {
        val m4a = File(dir, "long.m4a")
        val vad = File(dir, "silero_vad.onnx")
        assumeModelPresent(extra = m4a)
        assumeTrue("silero_vad.onnx not pushed to $dir — skipping", vad.exists())

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelId = "whisper-tiny"
        val modelDir = LocalTranscriptionProvider.modelDir(context, modelId).apply { mkdirs() }
        encoder.copyTo(File(modelDir, LocalTranscriptionProvider.ENCODER), overwrite = true)
        decoder.copyTo(File(modelDir, LocalTranscriptionProvider.DECODER), overwrite = true)
        tokens.copyTo(File(modelDir, LocalTranscriptionProvider.TOKENS), overwrite = true)
        vad.copyTo(File(modelDir, LocalTranscriptionProvider.VAD), overwrite = true)

        val audio = File(context.cacheDir, "vad-test.m4a").also { m4a.copyTo(it, overwrite = true) }
        val start = System.currentTimeMillis()
        val result = runBlocking {
            LocalTranscriptionProvider(modelDir)
                .transcribe(TranscriptionRequest(audioFile = audio, model = modelId))
        }
        Log.i(TAG, "[vad ${System.currentTimeMillis() - start}ms] len=${result.text.length}: \"${result.text}\"")

        modelDir.deleteRecursively()
        audio.delete()
        assert(result.text.contains("brothels", ignoreCase = true)) {
            "Tail beyond 30 s was lost — VAD segmentation didn't cover the end: \"${result.text}\""
        }
    }

    // --- helpers ---

    private val encoder get() = File(dir, "tiny-encoder.int8.onnx")
    private val decoder get() = File(dir, "tiny-decoder.int8.onnx")
    private val tokens get() = File(dir, "tiny-tokens.txt")

    private fun assumeModelPresent(extra: File) {
        val present = encoder.exists() && decoder.exists() && tokens.exists() && extra.exists()
        assumeTrue("sherpa-spike model/${extra.name} not pushed to $dir — skipping", present)
    }

    /** Builds a Whisper-tiny recognizer, transcribes [samples], logs timing, and releases. */
    private fun transcribe(samples: FloatArray, sampleRate: Int, label: String): String {
        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
            modelConfig = OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(
                    encoder = encoder.absolutePath,
                    decoder = decoder.absolutePath,
                    language = "", // "" = let Whisper auto-detect
                    task = "transcribe",
                ),
                tokens = tokens.absolutePath,
                numThreads = 2,
                modelType = "whisper",
                debug = true,
            ),
        )
        // assetManager defaults to null → models are read from the absolute filesystem paths above.
        val recognizer = OfflineRecognizer(config = config)
        try {
            val start = System.currentTimeMillis()
            val stream = recognizer.createStream()
            stream.acceptWaveform(samples, sampleRate)
            recognizer.decode(stream)
            val text = recognizer.getResult(stream).text
            stream.release()
            Log.i(TAG, "[$label] transcript (${System.currentTimeMillis() - start}ms): \"$text\"")
            return text
        } finally {
            recognizer.release()
        }
    }

    private fun assertContainsYellow(text: String) {
        assert(text.isNotBlank()) { "Expected a non-empty transcript from sherpa-onnx" }
        // The source clip says "...THE YELLOW LAMPS WOULD LIGHT UP..."; sanity-check one stable word.
        assert(text.contains("yellow", ignoreCase = true)) {
            "Transcript did not contain the expected word 'yellow': \"$text\""
        }
    }

    /** Minimal 16-bit PCM WAV reader → (FloatArray in [-1,1], sampleRate). Mono assumed. */
    private fun readPcm16Wav(file: File): Pair<FloatArray, Int> {
        RandomAccessFile(file, "r").use { raf ->
            val header = ByteArray(44)
            raf.readFully(header)
            val bb = java.nio.ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            val sampleRate = bb.getInt(24)
            val bitsPerSample = bb.getShort(34).toInt()
            require(bitsPerSample == 16) { "spike WAV reader expects 16-bit PCM, got $bitsPerSample" }
            val dataBytes = (raf.length() - 44).toInt()
            val raw = ByteArray(dataBytes)
            raf.readFully(raw)
            val sb = java.nio.ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            val out = FloatArray(sb.remaining())
            var i = 0
            while (sb.hasRemaining()) { out[i++] = sb.get() / 32768.0f }
            return out to sampleRate
        }
    }

    companion object {
        private const val TAG = "SherpaSpike"
    }
}
