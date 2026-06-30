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

import dev.patrickgold.florisboard.dictate.data.prompts.DictatePromptDefaults

/**
 * The post-transcription rewording chain, shared by the phone (`DictateController`, and the tethered
 * watch path `PhoneTranscriber`) and the Wear app (standalone path). Mirrors
 * `DictateController.postProcessTranscript`: optional auto-formatting first, then the user's auto-apply
 * prompts in order. Each step is best-effort — a failing step keeps the text so far so a dictation is
 * never lost. Lives in `:lib:dictate-core` so the watch can run the exact same chain when it transcribes
 * standalone (issue #130 auto-rewording on the watch).
 */
object DictateRewording {

    /** One auto-apply prompt: its [instruction], and whether it operates on the running [text]. */
    data class Prompt(val instruction: String, val requiresSelection: Boolean)

    /**
     * Runs auto-formatting (when [autoFormatting]) and then [autoApplyPrompts] over [transcript], using
     * [client]'s chat model [chatModel]. [languageName] is the readable language for the auto-formatting
     * hint; [systemPrompt] is the rewording system prompt (be-precise / custom) appended to each prompt.
     * Returns the final text (or [transcript] unchanged when nothing applies / everything fails).
     */
    suspend fun apply(
        client: LlmProvider,
        chatModel: String,
        transcript: String,
        autoFormatting: Boolean,
        languageName: String?,
        systemPrompt: String?,
        autoApplyPrompts: List<Prompt>,
    ): String {
        if (transcript.isBlank()) return transcript
        var text = transcript

        if (autoFormatting) {
            val formatPrompt = DictatePromptDefaults.buildAutoFormattingPrompt(languageName, text)
            text = runCatching {
                client.complete(ChatRequest.ofUser(chatModel, formatPrompt)).text.trim().ifBlank { text }
            }.getOrDefault(text)
        }

        for (prompt in autoApplyPrompts) {
            if (prompt.instruction.isBlank()) continue
            val content = buildString {
                append(prompt.instruction)
                if (!systemPrompt.isNullOrBlank()) append("\n\n").append(systemPrompt)
                // Only feed the running text to prompts that operate on a selection (matches the phone).
                if (prompt.requiresSelection && text.isNotBlank()) append("\n\n").append(text)
            }
            text = runCatching {
                client.complete(ChatRequest.ofUser(chatModel, content)).text.trim().ifBlank { text }
            }.getOrDefault(text)
        }
        return text
    }
}
