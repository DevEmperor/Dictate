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

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Concatenates several PCM **WAV** segments (as produced by [RecordingController]) into a single WAV
 * file by joining their raw PCM data under one fresh header — no re-encoding. Used to stitch a recording
 * that was interrupted by the keyboard closing together with the segment recorded after the user chose to
 * *continue* it (see [DictateController]): the two finalized segments are merged here before
 * transcription, yielding one coherent audio (and thus one transcription).
 *
 * All segments are expected to share the same format (they are all produced by [RecordingController] with
 * identical 16 kHz mono PCM16 settings), so the `fmt ` chunk of the first usable segment defines the
 * output. (Previously the segments were AAC/m4a and were remuxed via MediaMuxer; the switch to WAV for
 * issue #130 makes a plain PCM splice both correct and far simpler.)
 */
object AudioConcat {

    private const val WAV_HEADER_SIZE = 44

    private class WavFmt(val sampleRate: Int, val channels: Int, val bitsPerSample: Int)

    /**
     * Merges [segments] (in order) into [output], returning true on success. On any failure the partial
     * [output] is removed and false is returned, so the caller can fall back to a single segment.
     */
    fun concat(segments: List<File>, output: File): Boolean {
        val usable = segments.filter { it.exists() && it.length() > WAV_HEADER_SIZE }
        if (usable.isEmpty()) return false
        output.delete()
        var fmt: WavFmt? = null
        var dataBytes = 0L
        try {
            RandomAccessFile(output, "rw").use { out ->
                out.setLength(0)
                out.write(ByteArray(WAV_HEADER_SIZE)) // placeholder; patched once totals are known
                for (segment in usable) {
                    val bytes = segment.readBytes()
                    val parsed = parseWav(bytes) ?: continue
                    if (fmt == null) fmt = parsed.fmt
                    out.write(bytes, parsed.dataOffset, parsed.dataLength)
                    dataBytes += parsed.dataLength
                }
                val format = fmt
                if (format == null || dataBytes <= 0L) {
                    output.delete()
                    return false
                }
                out.seek(0)
                out.write(wavHeader(format, dataBytes))
            }
        } catch (_: Throwable) {
            output.delete()
            return false
        }
        return output.exists() && output.length() > WAV_HEADER_SIZE
    }

    private class ParsedWav(val fmt: WavFmt, val dataOffset: Int, val dataLength: Int)

    /** Parses a PCM WAV's `fmt `/`data` chunks, or returns null if [bytes] is not a usable WAV. */
    private fun parseWav(bytes: ByteArray): ParsedWav? {
        if (bytes.size < WAV_HEADER_SIZE) return null
        fun tag(off: Int) = String(bytes, off, 4, Charsets.US_ASCII)
        if (tag(0) != "RIFF" || tag(8) != "WAVE") return null
        fun le16(off: Int) = (bytes[off].toInt() and 0xff) or ((bytes[off + 1].toInt() and 0xff) shl 8)
        fun le32(off: Int) = (bytes[off].toInt() and 0xff) or ((bytes[off + 1].toInt() and 0xff) shl 8) or
            ((bytes[off + 2].toInt() and 0xff) shl 16) or ((bytes[off + 3].toInt() and 0xff) shl 24)

        var fmt: WavFmt? = null
        var p = 12 // after "RIFF"<size>"WAVE"
        while (p + 8 <= bytes.size) {
            val id = tag(p)
            val size = le32(p + 4)
            val body = p + 8
            when (id) {
                "fmt " -> fmt = WavFmt(
                    sampleRate = le32(body + 4),
                    channels = le16(body + 2).coerceAtLeast(1),
                    bitsPerSample = le16(body + 14),
                )
                "data" -> {
                    val len = size.coerceAtMost(bytes.size - body)
                    val f = fmt ?: return null
                    if (len <= 0) return null
                    return ParsedWav(f, body, len)
                }
            }
            p = body + size + (size and 1) // chunks are word-aligned
        }
        return null
    }

    private fun wavHeader(fmt: WavFmt, dataLen: Long): ByteArray {
        val byteRate = fmt.sampleRate * fmt.channels * fmt.bitsPerSample / 8
        return ByteBuffer.allocate(WAV_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt((36 + dataLen).toInt())
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)                  // PCM subchunk size
            putShort(1)                 // audio format = PCM
            putShort(fmt.channels.toShort())
            putInt(fmt.sampleRate)
            putInt(byteRate)
            putShort((fmt.channels * fmt.bitsPerSample / 8).toShort()) // block align
            putShort(fmt.bitsPerSample.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataLen.toInt())
        }.array()
    }
}
