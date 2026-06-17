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

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * Concatenates several m4a audio segments into a single MPEG-4 file by remuxing their AAC samples in
 * order (no re-encoding). Used to stitch a recording that was interrupted by the keyboard closing
 * together with the segment recorded after the user chose to *continue* it (see [DictateController]):
 * [android.media.MediaRecorder] cannot append to a finalized m4a, so the two finalized segments are
 * merged here before transcription, yielding one coherent audio (and thus one transcription).
 *
 * All segments are expected to share the same encoding (they are all produced by [RecordingController]
 * with identical settings), so the muxer track format from the first segment applies to the rest.
 */
object AudioConcat {

    /** ~one AAC frame (1024 samples @ 44.1 kHz ≈ 23 ms), used as the gap between joined segments. */
    private const val FrameGapUs = 23_000L

    /** Generous per-sample buffer; a single AAC access unit is far smaller than this. */
    private const val SampleBufferSize = 1 shl 20

    /**
     * Merges [segments] (in order) into [output], returning true on success. On any failure the partial
     * [output] is removed and false is returned, so the caller can fall back to a single segment.
     */
    fun concat(segments: List<File>, output: File): Boolean {
        val usable = segments.filter { it.exists() && it.length() > 0L }
        if (usable.isEmpty()) return false
        output.delete()
        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val buffer = ByteBuffer.allocate(SampleBufferSize)
        val info = MediaCodec.BufferInfo()
        var muxerTrack = -1
        var started = false
        var ptsOffsetUs = 0L
        try {
            for (segment in usable) {
                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(segment.absolutePath)
                    val (audioTrack, format) = findAudioTrack(extractor) ?: continue
                    extractor.selectTrack(audioTrack)
                    if (muxerTrack < 0) {
                        muxerTrack = muxer.addTrack(format)
                        muxer.start()
                        started = true
                    }
                    var lastPtsUs = ptsOffsetUs
                    while (true) {
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) break
                        info.offset = 0
                        info.size = size
                        info.presentationTimeUs = ptsOffsetUs + extractor.sampleTime
                        info.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                            MediaCodec.BUFFER_FLAG_KEY_FRAME
                        } else {
                            0
                        }
                        muxer.writeSampleData(muxerTrack, buffer, info)
                        lastPtsUs = info.presentationTimeUs
                        extractor.advance()
                    }
                    // Continue the timeline after this segment so the next one's samples don't overlap.
                    ptsOffsetUs = lastPtsUs + FrameGapUs
                } finally {
                    extractor.release()
                }
            }
        } catch (_: Throwable) {
            runCatching { if (started) muxer.stop() }
            runCatching { muxer.release() }
            output.delete()
            return false
        }
        return try {
            if (started) muxer.stop()
            muxer.release()
            started && output.length() > 0L
        } catch (_: Throwable) {
            runCatching { muxer.release() }
            output.delete()
            false
        }
    }

    /** Returns the first audio track index and its format, or null if the file has no audio track. */
    private fun findAudioTrack(extractor: MediaExtractor): Pair<Int, MediaFormat>? {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                return i to format
            }
        }
        return null
    }
}
