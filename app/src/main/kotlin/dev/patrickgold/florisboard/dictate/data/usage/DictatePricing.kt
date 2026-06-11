/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate.data.usage

/**
 * Cost estimation and display-name mapping for known transcription/rewording models.
 *
 * Ported from the original Dictate `DictateUtils`. Prices are USD and approximate; they are only
 * used for the in-app usage estimate. New providers/models are added here.
 *
 * NOTE: prices/model ids should be re-verified against the providers' current pricing whenever the
 * provider catalog is extended – do not guess. Unknown models return cost 0 and their own name.
 */
object DictatePricing {

    /** @param audioTime seconds of transcribed audio. */
    fun calcModelCost(modelName: String, audioTime: Long, inputTokens: Long, outputTokens: Long): Double {
        val audio = audioTime.toDouble()
        val input = inputTokens.toDouble()
        val output = outputTokens.toDouble()
        return when (modelName) {
            // OpenAI transcription models
            "whisper-1", "gpt-4o-transcribe" -> audio * 0.0001
            "gpt-4o-mini-transcribe" -> audio * 0.00005

            // OpenAI rewording models
            "o4-mini", "o3-mini", "o1-mini" -> input * 0.0000011 + output * 0.0000044
            "o1" -> input * 0.000015 + output * 0.00006
            "gpt-5.2" -> input * 0.00000175 + output * 0.000014
            "gpt-5" -> input * 0.00000125 + output * 0.00001
            "gpt-5-mini" -> input * 0.00000025 + output * 0.000002
            "gpt-4o-mini" -> input * 0.00000015 + output * 0.0000006
            "gpt-4o" -> input * 0.0000025 + output * 0.00001
            "gpt-4-turbo" -> input * 0.00001 + output * 0.00003
            "gpt-4" -> input * 0.00003 + output * 0.00006
            "gpt-3.5-turbo" -> input * 0.0000005 + output * 0.0000015

            // Groq transcription models
            "whisper-large-v3-turbo" -> audio * 0.000011
            "whisper-large-v3" -> audio * 0.000031

            // Groq rewording models
            "llama-3.1-8b-instant" -> input * 0.00000005 + output * 0.00000008
            "llama-3.3-70b-versatile" -> input * 0.00000059 + output * 0.00000079
            "meta-llama/llama-guard-4-12b" -> input * 0.00000020 + output * 0.00000020
            "openai/gpt-oss-120b" -> input * 0.00000015 + output * 0.00000075
            "openai/gpt-oss-20b" -> input * 0.00000010 + output * 0.00000050

            else -> 0.0
        }
    }

    fun translateModelName(modelName: String): String = when (modelName) {
        "whisper-1" -> "Whisper V2"
        "gpt-4o-transcribe" -> "GPT-4o transcribe"
        "gpt-4o-mini-transcribe" -> "GPT-4o mini transcribe"
        "o4-mini" -> "OpenAI o4 mini"
        "o3-mini" -> "OpenAI o3 mini"
        "o1-mini" -> "OpenAI o1 mini"
        "o1" -> "OpenAI o1"
        "gpt-5.2" -> "GPT-5.2"
        "gpt-5" -> "GPT-5"
        "gpt-5-mini" -> "GPT-5 mini"
        "gpt-4o-mini" -> "GPT-4o mini"
        "gpt-4o" -> "GPT-4o"
        "gpt-4-turbo" -> "GPT-4 Turbo"
        "gpt-4" -> "GPT-4"
        "gpt-3.5-turbo" -> "GPT-3.5 Turbo"
        "whisper-large-v3-turbo" -> "Whisper Large V3 Turbo"
        "whisper-large-v3" -> "Whisper Large V3"
        "llama-3.1-8b-instant" -> "LLaMA 3.1 8B Instant"
        "llama-3.3-70b-versatile" -> "LLaMA 3.3 70B Versatile"
        "meta-llama/llama-guard-4-12b" -> "LLaMA Guard 4 12B"
        "openai/gpt-oss-120b" -> "GPT-OSS 120B"
        "openai/gpt-oss-20b" -> "GPT-OSS 20B"
        else -> modelName // custom models: return as-is
    }
}
