/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate.audio

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteOrder

/**
 * Decodes a recorded audio file (the AAC/m4a produced by [RecordingController]) into the raw waveform
 * that on-device speech recognition expects: a single channel of 32-bit float samples in `[-1, 1]` at
 * [TARGET_SAMPLE_RATE].
 *
 * On-device engines (sherpa-onnx for issue #104) consume `FloatArray` PCM directly via
 * `acceptWaveform(samples, sampleRate)` — they do not take compressed files like the cloud upload
 * endpoints do. The recorder, however, captures compressed AAC at 44.1 kHz (stereo on some devices),
 * so this is the bridge: demux → decode to PCM16 → down-mix to mono → resample to 16 kHz → to float.
 *
 * Decoding is CPU-only and synchronous; run it off the main thread.
 */
object AudioDecode {

    /** Sample rate every on-device model in this app is fed at (Whisper/Zipformer all use 16 kHz). */
    const val TARGET_SAMPLE_RATE = 16_000

    private const val DEQUEUE_TIMEOUT_US = 10_000L

    /**
     * Decodes [file] to mono float PCM at [TARGET_SAMPLE_RATE].
     *
     * @throws IllegalStateException if the file has no decodable audio track or decoding fails.
     */
    fun decodeToMono16k(file: File): FloatArray {
        require(file.exists() && file.length() > 0L) { "audio file missing or empty: ${file.absolutePath}" }
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("no audio track in ${file.absolutePath}")
            extractor.selectTrack(trackIndex)
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: error("audio track has no MIME type")

            val codec = MediaCodec.createDecoderByType(mime)
            try {
                codec.configure(inputFormat, null, null, 0)
                codec.start()
                val mono = drainToMonoFloat(extractor, codec, inputFormat)
                val srcRate = mono.sampleRate
                return if (srcRate == TARGET_SAMPLE_RATE) mono.samples
                else resampleLinear(mono.samples, srcRate, TARGET_SAMPLE_RATE)
            } finally {
                runCatching { codec.stop() }
                codec.release()
            }
        } finally {
            extractor.release()
        }
    }

    private class MonoPcm(val samples: FloatArray, val sampleRate: Int)

    /** Runs the decode loop, collecting all output as mono float samples at the decoder's sample rate. */
    private fun drainToMonoFloat(
        extractor: MediaExtractor,
        codec: MediaCodec,
        inputFormat: MediaFormat,
    ): MonoPcm {
        val out = FloatArrayBuilder()
        val bufferInfo = MediaCodec.BufferInfo()
        var sawInputEos = false
        var sawOutputEos = false

        // Fall back to the demuxed track format; corrected from the real output format once it arrives.
        var channelCount = inputFormat.getIntOr(MediaFormat.KEY_CHANNEL_COUNT, 1)
        var sampleRate = inputFormat.getIntOr(MediaFormat.KEY_SAMPLE_RATE, TARGET_SAMPLE_RATE)
        var pcmEncoding = inputFormat.getIntOr(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)

        while (!sawOutputEos) {
            if (!sawInputEos) {
                val inIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                if (inIndex >= 0) {
                    val inBuf = codec.getInputBuffer(inIndex)!!
                    val size = extractor.readSampleData(inBuf, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        sawInputEos = true
                    } else {
                        codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            when (val outIndex = codec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)) {
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val f = codec.outputFormat
                    channelCount = f.getIntOr(MediaFormat.KEY_CHANNEL_COUNT, channelCount)
                    sampleRate = f.getIntOr(MediaFormat.KEY_SAMPLE_RATE, sampleRate)
                    pcmEncoding = f.getIntOr(MediaFormat.KEY_PCM_ENCODING, pcmEncoding)
                }
                MediaCodec.INFO_TRY_AGAIN_LATER -> Unit // no output yet; loop and feed more input
                else -> if (outIndex >= 0) {
                    val outBuf = codec.getOutputBuffer(outIndex)
                    if (outBuf != null && bufferInfo.size > 0) {
                        outBuf.position(bufferInfo.offset)
                        outBuf.limit(bufferInfo.offset + bufferInfo.size)
                        appendMono(outBuf, channelCount, pcmEncoding, out)
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) sawOutputEos = true
                }
            }
        }
        return MonoPcm(out.toArray(), sampleRate)
    }

    /** Reads one decoded buffer, down-mixing interleaved channels to a single mono float track. */
    private fun appendMono(
        buf: java.nio.ByteBuffer,
        channelCount: Int,
        pcmEncoding: Int,
        out: FloatArrayBuilder,
    ) {
        val channels = channelCount.coerceAtLeast(1)
        when (pcmEncoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> {
                val fb = buf.order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
                val frames = fb.remaining() / channels
                repeat(frames) {
                    var sum = 0f
                    repeat(channels) { sum += fb.get() }
                    out.add(sum / channels)
                }
            }
            else -> { // ENCODING_PCM_16BIT (the AAC decoder default)
                val sb = buf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                val frames = sb.remaining() / channels
                repeat(frames) {
                    var sum = 0
                    repeat(channels) { sum += sb.get().toInt() }
                    out.add((sum / channels) / 32768.0f)
                }
            }
        }
    }

    /** Linear-interpolation resampler. Adequate for speech STT; avoids pulling in a DSP dependency. */
    private fun resampleLinear(input: FloatArray, srcRate: Int, dstRate: Int): FloatArray {
        if (input.isEmpty() || srcRate == dstRate) return input
        val outLen = ((input.size.toLong() * dstRate) / srcRate).toInt()
        if (outLen <= 0) return FloatArray(0)
        val output = FloatArray(outLen)
        val step = srcRate.toDouble() / dstRate
        for (i in 0 until outLen) {
            val srcPos = i * step
            val idx = srcPos.toInt()
            val frac = (srcPos - idx).toFloat()
            val a = input[idx]
            val b = if (idx + 1 < input.size) input[idx + 1] else a
            output[i] = a + (b - a) * frac
        }
        return output
    }

    private fun MediaFormat.getIntOr(key: String, default: Int): Int =
        if (containsKey(key)) getInteger(key) else default

    /** Minimal growable float buffer to avoid boxing into a `MutableList<Float>`. */
    private class FloatArrayBuilder(initial: Int = 16_384) {
        private var buf = FloatArray(initial)
        private var size = 0

        fun add(value: Float) {
            if (size == buf.size) buf = buf.copyOf(buf.size * 2)
            buf[size++] = value
        }

        fun toArray(): FloatArray = buf.copyOf(size)
    }
}
