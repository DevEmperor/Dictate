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

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Thin wrapper around [MediaRecorder] that records microphone audio into an m4a file in the app
 * cache, matching the original Dictate encoding (MPEG-4 / AAC, 64 kbps, 44.1 kHz).
 *
 * This is the rudimentary version: it records from the local microphone only. Bluetooth-SCO
 * routing, audio-focus handling and recording from VOICE_COMMUNICATION (all present in the legacy
 * service) are intentionally left for a later refinement step.
 *
 * Requires the RECORD_AUDIO runtime permission; [start] throws if it has not been granted.
 */
class RecordingController(private val context: Context) {

    private var recorder: MediaRecorder? = null

    /** The file the current/last recording was written to, or null if nothing was recorded yet. */
    var outputFile: File? = null
        private set

    val isRecording: Boolean
        get() = recorder != null

    /**
     * Starts a new recording. Throws if the microphone cannot be acquired (e.g. permission missing
     * or another app holds it). On failure the recorder is released so the controller stays usable.
     */
    fun start() {
        if (recorder != null) return
        val file = File(context.cacheDir, AUDIO_FILE_NAME)
        @Suppress("DEPRECATION")
        val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }
        try {
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setAudioEncodingBitRate(64_000)
            mr.setAudioSamplingRate(44_100)
            mr.setOutputFile(file.absolutePath)
            mr.prepare()
            mr.start()
        } catch (t: Throwable) {
            runCatching { mr.release() }
            throw t
        }
        recorder = mr
        outputFile = file
    }

    /**
     * Stops the recording and returns the written file, or null if stopping failed (e.g. the
     * recording was too short to produce a valid file). The recorder is always released.
     */
    fun stop(): File? {
        val mr = recorder ?: return null
        recorder = null
        return try {
            mr.stop()
            outputFile
        } catch (_: RuntimeException) {
            // MediaRecorder.stop() throws if it was stopped immediately after start (no data).
            outputFile?.delete()
            null
        } finally {
            runCatching { mr.release() }
        }
    }

    /** Stops and discards the current recording without returning it. */
    fun cancel() {
        stop()
        outputFile?.delete()
    }

    companion object {
        private const val AUDIO_FILE_NAME = "dictate_audio.m4a"
    }
}
