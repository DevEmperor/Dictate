/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate

/**
 * How much a reasoning model should "think" on rewording/command chat calls (issue #141), mapped to the
 * OpenAI-compatible `reasoning_effort` field. [OFF] omits the field entirely — the provider default is
 * used and non-reasoning models are unaffected. Applies only to chat/rewording, never to transcription.
 */
enum class DictateReasoningEffort(val wire: String?) {
    OFF(null),
    MINIMAL("minimal"),
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high");
}
