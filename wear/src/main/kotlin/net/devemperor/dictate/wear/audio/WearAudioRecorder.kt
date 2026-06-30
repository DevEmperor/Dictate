/*
 * Copyright (C) 2026 The Dictate Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */

package net.devemperor.dictate.wear.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Minimal microphone recorder for the watch (#106). Captures 16 kHz mono 16-bit PCM via [AudioRecord]
 * and writes a `.wav` file — the format the OpenAI-compatible transcription endpoints accept directly
 * (see `guessAudioMediaType`) and the smallest practical payload to ship to the phone in tether mode.
 *
 * Caller must hold RECORD_AUDIO (the watch settings screen requests it). [start] throws if the mic is
 * unavailable; [stop] always releases the recorder.
 */
class WearAudioRecorder(private val context: Context) {

    private var record: AudioRecord? = null
    private var thread: Thread? = null
    @Volatile private var recording = false
    @Volatile private var paused = false
    private val pcm = ByteArrayOutputStream()
    /** Peak |sample| (0..32767) seen since the last [maxAmplitude] call; drives the live waveform. */
    @Volatile private var peak = 0

    val isRecording: Boolean get() = recording

    /** Peak microphone amplitude (0..32767) since the previous call, then resets. 0 when not recording. */
    fun maxAmplitude(): Int {
        val p = peak
        peak = 0
        return p
    }

    @SuppressLint("MissingPermission") // caller guarantees RECORD_AUDIO; init failure is handled below.
    fun start() {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        require(minBuf > 0) { "AudioRecord unavailable on this device" }
        val bufferSize = minBuf * 2
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL,
            ENCODING,
            bufferSize,
        )
        check(recorder.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord failed to initialize" }
        pcm.reset()
        peak = 0
        record = recorder
        recording = true
        paused = false
        recorder.startRecording()
        thread = Thread {
            val buf = ByteArray(bufferSize)
            while (recording) {
                val read = recorder.read(buf, 0, buf.size)
                // Keep draining the mic while paused (so the buffer never overflows) but drop the audio,
                // so paused time contributes no samples — matching the phone's pause behavior.
                if (read > 0 && !paused) {
                    pcm.write(buf, 0, read)
                    updatePeak(buf, read)
                }
            }
        }.also { it.start() }
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

    /** Pause capture: the mic keeps running but recorded samples are dropped until [resume]. */
    fun pause() { paused = true }

    /** Resume capture after a [pause]. */
    fun resume() { paused = false }

    /** Stops capture, releases the recorder and returns the recorded audio as a `.wav` file. */
    fun stop(): File {
        recording = false
        thread?.join()
        thread = null
        record?.run { stop(); release() }
        record = null

        val pcmBytes = pcm.toByteArray()
        val out = File(context.cacheDir, "wear_dictation_${pcmBytes.size}.wav")
        writeWav(out, pcmBytes)
        return out
    }

    fun cancel() {
        recording = false
        thread?.join()
        thread = null
        record?.run { stop(); release() }
        record = null
        pcm.reset()
    }

    private fun writeWav(file: File, pcmData: ByteArray) {
        val totalDataLen = pcmData.size + 36
        val byteRate = SAMPLE_RATE * CHANNELS * BITS_PER_SAMPLE / 8
        DataOutputStream(FileOutputStream(file)).use { out ->
            out.writeBytes("RIFF")
            out.writeIntLe(totalDataLen)
            out.writeBytes("WAVE")
            out.writeBytes("fmt ")
            out.writeIntLe(16)                 // PCM subchunk size
            out.writeShortLe(1)                // audio format = PCM
            out.writeShortLe(CHANNELS)
            out.writeIntLe(SAMPLE_RATE)
            out.writeIntLe(byteRate)
            out.writeShortLe(CHANNELS * BITS_PER_SAMPLE / 8) // block align
            out.writeShortLe(BITS_PER_SAMPLE)
            out.writeBytes("data")
            out.writeIntLe(pcmData.size)
            out.write(pcmData)
        }
    }

    private fun DataOutputStream.writeIntLe(value: Int) {
        write(value and 0xff)
        write((value shr 8) and 0xff)
        write((value shr 16) and 0xff)
        write((value shr 24) and 0xff)
    }

    private fun DataOutputStream.writeShortLe(value: Int) {
        write(value and 0xff)
        write((value shr 8) and 0xff)
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        const val CHANNELS = 1
        const val BITS_PER_SAMPLE = 16
    }
}
