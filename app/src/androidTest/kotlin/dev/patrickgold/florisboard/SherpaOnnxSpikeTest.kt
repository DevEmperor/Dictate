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

    @Test
    fun transcribesWavWithWhisperTinyOnDevice() {
        val encoder = File(dir, "tiny-encoder.int8.onnx")
        val decoder = File(dir, "tiny-decoder.int8.onnx")
        val tokens = File(dir, "tiny-tokens.txt")
        val wav = File(dir, "0.wav")
        val present = encoder.exists() && decoder.exists() && tokens.exists() && wav.exists()
        assumeTrue("sherpa-spike model/wav not pushed to $dir — skipping", present)

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

        val startCreate = System.currentTimeMillis()
        // assetManager defaults to null → models are read from the absolute filesystem paths above.
        val recognizer = OfflineRecognizer(config = config)
        val createMs = System.currentTimeMillis() - startCreate

        val (samples, sampleRate) = readPcm16Wav(wav)
        Log.i(TAG, "loaded ${samples.size} samples @ ${sampleRate}Hz (${samples.size / sampleRate.toFloat()}s)")

        val startDecode = System.currentTimeMillis()
        val stream = recognizer.createStream()
        stream.acceptWaveform(samples, sampleRate)
        recognizer.decode(stream)
        val text = recognizer.getResult(stream).text
        val decodeMs = System.currentTimeMillis() - startDecode

        stream.release()
        recognizer.release()

        Log.i(TAG, "=== SHERPA SPIKE RESULT ===")
        Log.i(TAG, "model load: ${createMs}ms · decode: ${decodeMs}ms")
        Log.i(TAG, "transcript: \"$text\"")

        assert(text.isNotBlank()) { "Expected a non-empty transcript from sherpa-onnx" }
        // The bundled 0.wav says "...THE YELLOW LAMPS WOULD LIGHT UP..."; sanity-check one stable word.
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
