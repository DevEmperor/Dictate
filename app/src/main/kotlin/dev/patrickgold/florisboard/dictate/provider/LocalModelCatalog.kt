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
 * The fixed catalog of on-device Whisper models offered for download. int8-quantised sherpa-onnx
 * builds; sizes/checksums verified 2026-06-22.
 *
 * **Attribution / licensing:** these are OpenAI Whisper models (MIT) exported to ONNX by the sherpa-onnx
 * project (Apache-2.0). Both licenses permit redistribution, so the files are mirrored on the project's
 * own GitHub release ([REL]) for a stable, project-controlled source instead of depending on a third
 * party at runtime. To re-point hosting, change [REL] only. The runtime never fetches this list — it is
 * shipped in the app.
 */
object LocalModelCatalog {

    /** Project-hosted mirror of the model files (GitHub release assets). Single re-point for hosting. */
    private const val REL = "https://github.com/DevEmperor/DictateKeyboard/releases/download/whisper-models-v1"

    /**
     * Silero VAD model, downloaded into every model dir so [LocalTranscriptionProvider] can segment
     * long audio at speech pauses (Whisper itself only handles ~30 s per pass). Same file for all models.
     */
    private val VAD_FILE = LocalModelFile(
        "$REL/silero_vad.onnx", LocalTranscriptionProvider.VAD, 643_854,
        "9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6",
    )

    /** ~99 MB. Fastest, lowest accuracy — good for low-end devices / quick notes. */
    val WHISPER_TINY = LocalModelSpec(
        id = "whisper-tiny",
        displayName = "Whisper Tiny",
        description = "Multilingual · fastest · ~99 MB",
        files = listOf(
            LocalModelFile("$REL/tiny-encoder.int8.onnx", LocalTranscriptionProvider.ENCODER, 12_937_772, "d24fb083ae3b1041fc24e97971d60e280c9342201fbb67b0ab428a8b4a51a434"),
            LocalModelFile("$REL/tiny-decoder.int8.onnx", LocalTranscriptionProvider.DECODER, 89_855_401, "d2fece8dd42771f1df975c6c0445770d0c292bf7547c2cae04a6c0cc57540925"),
            LocalModelFile("$REL/tiny-tokens.txt", LocalTranscriptionProvider.TOKENS, 816_730, "b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126"),
            VAD_FILE,
        ),
    )

    /** ~153 MB. The recommended default — noticeably better accuracy, still usable on mid-range. */
    val WHISPER_BASE = LocalModelSpec(
        id = "whisper-base",
        displayName = "Whisper Base",
        description = "Multilingual · recommended · ~153 MB",
        files = listOf(
            LocalModelFile("$REL/base-encoder.int8.onnx", LocalTranscriptionProvider.ENCODER, 29_120_534, "0b8fb1304b6109976038efff5ace81720e00386f3ff6b54ee8c75291ca0a1e11"),
            LocalModelFile("$REL/base-decoder.int8.onnx", LocalTranscriptionProvider.DECODER, 130_672_026, "9759d217388a01b3a4c7c15533201067b48ae819c4daafc8624e64b9409dc02d"),
            LocalModelFile("$REL/base-tokens.txt", LocalTranscriptionProvider.TOKENS, 816_730, "b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126"),
            VAD_FILE,
        ),
    )

    /** ~358 MB. Most accurate, but large and slower on mid-range — Base is recommended for most users. */
    val WHISPER_SMALL = LocalModelSpec(
        id = "whisper-small",
        displayName = "Whisper Small",
        description = "Multilingual · most accurate, large · ~358 MB (Base recommended)",
        files = listOf(
            LocalModelFile("$REL/small-encoder.int8.onnx", LocalTranscriptionProvider.ENCODER, 112_442_483, "4cbe7b22fa9026b843b60a68640c747de05bafb1a11b57edc0e66c232d9f33a9"),
            LocalModelFile("$REL/small-decoder.int8.onnx", LocalTranscriptionProvider.DECODER, 262_226_114, "acad50b5c782696e91b55914cc5ab4f756f1532f76e22aa6fc615f39fb69a8ee"),
            LocalModelFile("$REL/small-tokens.txt", LocalTranscriptionProvider.TOKENS, 816_730, "b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126"),
            VAD_FILE,
        ),
    )

    /** ~99 MB. English-only — faster/leaner than the multilingual tiny when you only need English. */
    val WHISPER_TINY_EN = LocalModelSpec(
        id = "whisper-tiny.en",
        displayName = "Whisper Tiny (English)",
        description = "English only · fastest · ~99 MB",
        files = listOf(
            LocalModelFile("$REL/tiny.en-encoder.int8.onnx", LocalTranscriptionProvider.ENCODER, 12_937_772, "0ce578b827c94a961aacb8fa14b02f096504b337e5c94be37c36238cbe3e8bc6"),
            LocalModelFile("$REL/tiny.en-decoder.int8.onnx", LocalTranscriptionProvider.DECODER, 89_853_865, "06c0e6ff6348d427e51839219d1c886c18cfdf411e629e33f5e1679bff9c1527"),
            LocalModelFile("$REL/tiny.en-tokens.txt", LocalTranscriptionProvider.TOKENS, 835_554, "306cd27f03c1a714eca7108e03d66b7dc042abe8c258b44c199a7ed9838dd930"),
            VAD_FILE,
        ),
    )

    /** ~153 MB. English-only — the recommended English model: good accuracy without the multilingual cost. */
    val WHISPER_BASE_EN = LocalModelSpec(
        id = "whisper-base.en",
        displayName = "Whisper Base (English)",
        description = "English only · recommended for English · ~153 MB",
        files = listOf(
            LocalModelFile("$REL/base.en-encoder.int8.onnx", LocalTranscriptionProvider.ENCODER, 29_120_534, "ef6b936f4c9b1d90a3b68634b60c4ed8576b26172b33c2535ec0e933c9edb823"),
            LocalModelFile("$REL/base.en-decoder.int8.onnx", LocalTranscriptionProvider.DECODER, 130_669_978, "f7162ad6db2dbef16cfaeaa7f945b9d7dd9c1b8d472f6aca82f2273d185e4d41"),
            LocalModelFile("$REL/base.en-tokens.txt", LocalTranscriptionProvider.TOKENS, 835_554, "306cd27f03c1a714eca7108e03d66b7dc042abe8c258b44c199a7ed9838dd930"),
            VAD_FILE,
        ),
    )

    /** ~358 MB. English-only, most accurate English model — large and slower on mid-range. */
    val WHISPER_SMALL_EN = LocalModelSpec(
        id = "whisper-small.en",
        displayName = "Whisper Small (English)",
        description = "English only · most accurate · ~358 MB",
        files = listOf(
            LocalModelFile("$REL/small.en-encoder.int8.onnx", LocalTranscriptionProvider.ENCODER, 112_442_483, "8bdac288f369aa94ee2194059238c465ed82ea9d47ee8fa4a8c0a891873e462f"),
            LocalModelFile("$REL/small.en-decoder.int8.onnx", LocalTranscriptionProvider.DECODER, 262_223_042, "710ccf890e10f3faa15f51ec346081a2723c9f3adb6e4da81c6573a5a6f877fb"),
            LocalModelFile("$REL/small.en-tokens.txt", LocalTranscriptionProvider.TOKENS, 835_554, "306cd27f03c1a714eca7108e03d66b7dc042abe8c258b44c199a7ed9838dd930"),
            VAD_FILE,
        ),
    )

    /** All catalog models in display order: multilingual (tiny→small), then the English-only variants. */
    val all: List<LocalModelSpec> = listOf(
        WHISPER_TINY, WHISPER_BASE, WHISPER_SMALL,
        WHISPER_TINY_EN, WHISPER_BASE_EN, WHISPER_SMALL_EN,
    )

    fun byId(id: String): LocalModelSpec? = all.firstOrNull { it.id == id }
}
