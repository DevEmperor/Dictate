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

/**
 * One downloadable file of an on-device model. [destName] is the fixed name it is stored under (so the
 * runtime stays variant-agnostic — see [LocalTranscriptionProvider]); [sizeBytes] and [sha256] are
 * verified after download to guarantee integrity.
 */
data class LocalModelFile(
    val url: String,
    val destName: String,
    val sizeBytes: Long,
    val sha256: String? = null,
)

/**
 * A selectable on-device model (issue #104). [id] doubles as the install directory name and the value
 * stored in [ProviderAccount.transcriptionModel] for the local provider.
 */
data class LocalModelSpec(
    val id: String,
    val displayName: String,
    /** Short note for the picker, e.g. languages / accuracy/speed trade-off. */
    val description: String,
    val files: List<LocalModelFile>,
) {
    val totalBytes: Long get() = files.sumOf { it.sizeBytes }
}

/**
 * The fixed catalog of on-device Whisper models offered for download. Multilingual (99 languages),
 * int8-quantised sherpa-onnx builds. Sizes/checksums verified 2026-06-22.
 *
 * Hosted on Hugging Face (csukuangfj) for now; re-point [LocalModelFile.url] here if the project later
 * mirrors them. The runtime never fetches this list — it is shipped in the app.
 */
object LocalModelCatalog {

    private const val HF = "https://huggingface.co/csukuangfj"

    /** ~99 MB. Fastest, lowest accuracy — good for low-end devices / quick notes. */
    val WHISPER_TINY = LocalModelSpec(
        id = "whisper-tiny",
        displayName = "Whisper Tiny",
        description = "Multilingual · fastest · ~99 MB",
        files = listOf(
            LocalModelFile(
                url = "$HF/sherpa-onnx-whisper-tiny/resolve/main/tiny-encoder.int8.onnx",
                destName = LocalTranscriptionProvider.ENCODER,
                sizeBytes = 12_937_772,
                sha256 = "d24fb083ae3b1041fc24e97971d60e280c9342201fbb67b0ab428a8b4a51a434",
            ),
            LocalModelFile(
                url = "$HF/sherpa-onnx-whisper-tiny/resolve/main/tiny-decoder.int8.onnx",
                destName = LocalTranscriptionProvider.DECODER,
                sizeBytes = 89_855_401,
                sha256 = "d2fece8dd42771f1df975c6c0445770d0c292bf7547c2cae04a6c0cc57540925",
            ),
            LocalModelFile(
                url = "$HF/sherpa-onnx-whisper-tiny/resolve/main/tiny-tokens.txt",
                destName = LocalTranscriptionProvider.TOKENS,
                sizeBytes = 816_730,
                sha256 = "b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126",
            ),
        ),
    )

    /** ~153 MB. The recommended default — noticeably better accuracy, still usable on mid-range. */
    val WHISPER_BASE = LocalModelSpec(
        id = "whisper-base",
        displayName = "Whisper Base",
        description = "Multilingual · balanced · ~153 MB",
        files = listOf(
            LocalModelFile(
                url = "$HF/sherpa-onnx-whisper-base/resolve/main/base-encoder.int8.onnx",
                destName = LocalTranscriptionProvider.ENCODER,
                sizeBytes = 29_120_534,
                sha256 = "0b8fb1304b6109976038efff5ace81720e00386f3ff6b54ee8c75291ca0a1e11",
            ),
            LocalModelFile(
                url = "$HF/sherpa-onnx-whisper-base/resolve/main/base-decoder.int8.onnx",
                destName = LocalTranscriptionProvider.DECODER,
                sizeBytes = 130_672_026,
                sha256 = "9759d217388a01b3a4c7c15533201067b48ae819c4daafc8624e64b9409dc02d",
            ),
            LocalModelFile(
                url = "$HF/sherpa-onnx-whisper-base/resolve/main/base-tokens.txt",
                destName = LocalTranscriptionProvider.TOKENS,
                sizeBytes = 816_730,
                sha256 = "b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126",
            ),
        ),
    )

    /** All catalog models in display order (smallest first). */
    val all: List<LocalModelSpec> = listOf(WHISPER_TINY, WHISPER_BASE)

    fun byId(id: String): LocalModelSpec? = all.firstOrNull { it.id == id }
}
