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
 * How the rewording prompt chips (including the live-prompt chip) are surfaced on the keyboard
 * (user choice in the Dictate rewording settings):
 *
 *  - [PANEL]: a dedicated full-width panel (own [dev.patrickgold.florisboard.ime.ImeUiMode.DICTATE]),
 *    opened via the magic-wand Smartbar action. Chips flow left-to-right, top-to-bottom.
 *  - [ROW]: an always-on extra row pinned above the Smartbar, so the prompts are permanently visible
 *    without opening a panel. The live-prompt chip is the first (leftmost) entry.
 */
enum class DictatePromptsLayout {
    PANEL,
    ROW;
}
