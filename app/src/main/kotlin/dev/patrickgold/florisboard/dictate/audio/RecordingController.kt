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

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Records microphone audio into a 16 kHz mono PCM16 **WAV** file in the app cache.
 *
 * WAV is used (rather than the previous AAC/m4a) because it is universally accepted by every
 * transcription path — including the multimodal `chat/completions` `input_audio` endpoint (issue #130),
 * which only takes wav/mp3 — and 16 kHz mono is exactly what speech models consume, so there is no
 * quality loss and the payload stays small (~1.9 MB/min). Captured via [AudioRecord]; raw PCM is streamed
 * to the file behind a placeholder header that is patched with the real sizes on [stop], so long
 * recordings never have to be held in memory.
 *
 * Requires the RECORD_AUDIO runtime permission; [start] throws if the microphone cannot be acquired.
 */
class RecordingController(private val context: Context) {

    private var record: AudioRecord? = null
    private var thread: Thread? = null
    private var raf: RandomAccessFile? = null
    @Volatile private var recording = false
    @Volatile private var paused = false
    @Volatile private var pcmBytes = 0L
    /** Peak |sample| (0..32767) seen since the last [maxAmplitude] call; drives the waveform. */
    @Volatile private var peak = 0

    /** The file the current/last recording was written to, or null if nothing was recorded yet. */
    var outputFile: File? = null
        private set

    val isRecording: Boolean
        get() = recording

    /**
     * Starts a new recording. Throws if the microphone cannot be acquired (e.g. permission missing or
     * another app holds it). On failure everything is released so the controller stays usable.
     *
     * [audioSource] defaults to the local mic; pass [MediaRecorder.AudioSource.VOICE_COMMUNICATION]
     * when recording is routed through a Bluetooth SCO headset.
     */
    @SuppressLint("MissingPermission") // caller holds RECORD_AUDIO; an init failure is handled below.
    fun start(audioSource: Int = MediaRecorder.AudioSource.MIC) {
        if (recording) return
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        require(minBuf > 0) { "AudioRecord unavailable on this device" }
        val bufferSize = minBuf * 2
        val rec = AudioRecord(audioSource, SAMPLE_RATE, CHANNEL, ENCODING, bufferSize)
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            runCatching { rec.release() }
            error("AudioRecord failed to initialize")
        }
        val file = File(context.cacheDir, AUDIO_FILE_NAME)
        val out = try {
            RandomAccessFile(file, "rw").apply {
                setLength(0)
                write(ByteArray(WAV_HEADER_SIZE)) // placeholder; patched in stop()
            }
        } catch (t: Throwable) {
            runCatching { rec.release() }
            throw t
        }
        record = rec
        raf = out
        outputFile = file
        pcmBytes = 0
        peak = 0
        paused = false
        recording = true
        rec.startRecording()
        thread = Thread {
            val buf = ByteArray(bufferSize)
            while (recording) {
                val n = rec.read(buf, 0, buf.size)
                // Keep reading while paused (so the mic buffer never overflows) but drop the samples.
                if (n > 0 && !paused) {
                    runCatching { out.write(buf, 0, n) }
                    pcmBytes += n
                    updatePeak(buf, n)
                }
            }
        }.also { it.start() }
    }

    /**
     * Stops the recording and returns the finished WAV file, or null if nothing usable was captured. The
     * recorder is always released.
     */
    fun stop(): File? {
        if (!recording) return null
        recording = false
        runCatching { thread?.join() }
        thread = null
        runCatching { record?.run { stop(); release() } }
        record = null
        val out = raf ?: return null
        raf = null
        val bytes = pcmBytes
        val file = outputFile
        return try {
            out.seek(0)
            out.write(wavHeader(bytes))
            out.close()
            if (bytes > 0) file else { file?.delete(); null }
        } catch (_: Throwable) {
            runCatching { out.close() }
            file?.delete()
            null
        }
    }

    /** The peak microphone amplitude (0..32767) since the previous call, or 0 when not recording. */
    fun maxAmplitude(): Int {
        val p = peak
        peak = 0
        return p
    }

    /** Pauses the in-progress recording (samples are dropped until [resume]). No-op if not recording. */
    fun pause() {
        paused = true
    }

    /** Resumes a previously paused recording. No-op if not recording. */
    fun resume() {
        paused = false
    }

    /** Stops and discards the current recording without returning it. */
    fun cancel() {
        stop()
        outputFile?.delete()
    }

    private fun updatePeak(buf: ByteArray, length: Int) {
        var max = peak
        var i = 0
        while (i + 1 < length) {
            val sample = (buf[i].toInt() and 0xff) or (buf[i + 1].toInt() shl 8) // little-endian PCM16
            val abs = if (sample < 0) -sample else sample
            if (abs > max) max = abs
            i += 2
        }
        peak = max.coerceAtMost(32767)
    }

    private fun wavHeader(dataLen: Long): ByteArray {
        val byteRate = SAMPLE_RATE * CHANNELS * BITS_PER_SAMPLE / 8
        return ByteBuffer.allocate(WAV_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt((36 + dataLen).toInt())
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)                                  // PCM subchunk size
            putShort(1)                                 // audio format = PCM
            putShort(CHANNELS.toShort())
            putInt(SAMPLE_RATE)
            putInt(byteRate)
            putShort((CHANNELS * BITS_PER_SAMPLE / 8).toShort()) // block align
            putShort(BITS_PER_SAMPLE.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataLen.toInt())
        }.array()
    }

    companion object {
        private const val AUDIO_FILE_NAME = "dictate_audio.wav"
        private const val SAMPLE_RATE = 16_000
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val CHANNELS = 1
        private const val BITS_PER_SAMPLE = 16
        private const val WAV_HEADER_SIZE = 44
    }
}
