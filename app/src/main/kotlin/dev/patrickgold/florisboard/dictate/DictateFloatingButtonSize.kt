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
 * Overall size of the floating dictation button (issue #88), selectable in the floating-button settings.
 * [scale] multiplies the skin's base dimensions.
 */
enum class DictateFloatingButtonSize(val scale: Float) {
    SMALL(0.82f),
    MEDIUM(1.0f),
    LARGE(1.22f);
}
