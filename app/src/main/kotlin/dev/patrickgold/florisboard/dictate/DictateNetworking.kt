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

import dev.patrickgold.florisboard.app.FlorisPreferenceModel
import dev.patrickgold.florisboard.dictate.provider.ProxyConfig

/**
 * Builds the global outbound proxy from the user's network settings, or `null` when the proxy is
 * disabled or incompletely configured (in which case API calls go out directly). Applies to *every*
 * provider call — transcription, rewording, model listing and the connection test — so all traffic to
 * the configured AI endpoints shares one proxy. See [ProxyConfig.of] and `DictateProxyScreen`.
 */
fun FlorisPreferenceModel.Dictate.dictateProxyConfig(): ProxyConfig? = ProxyConfig.of(
    enabled = proxyEnabled.get(),
    type = proxyType.get(),
    host = proxyHost.get(),
    port = proxyPort.get().trim().toIntOrNull() ?: -1,
    username = proxyUsername.get(),
    password = proxyPassword.get(),
)
