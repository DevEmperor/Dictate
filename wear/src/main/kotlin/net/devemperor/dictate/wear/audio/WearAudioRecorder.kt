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
    private val pcm = ByteArrayOutputStream()

    val isRecording: Boolean get() = recording

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
        record = recorder
        recording = true
        recorder.startRecording()
        thread = Thread {
            val buf = ByteArray(bufferSize)
            while (recording) {
                val read = recorder.read(buf, 0, buf.size)
                if (read > 0) pcm.write(buf, 0, read)
            }
        }.also { it.start() }
    }

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
