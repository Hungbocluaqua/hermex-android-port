package com.uzairansar.hermex.ui

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object ShortcutDestination {
    const val NewSessionAction = "new"
    const val NewVoiceSessionAction = "newVoice"
    const val NewProfileSessionAction = "newProfile"
    const val ShareAction = "share"
    const val SessionsUri = "hermes-agent://sessions"
    const val NewSessionUriPattern = "hermes-agent://sessions?shortcutAction={shortcutAction}"
    const val NewSessionUri = "hermes-agent://sessions?shortcutAction=$NewSessionAction"
    const val NewChatUri = "hermes-agent://new-chat"
    const val NewVoiceSessionUri = "hermes-agent://new-chat-voice"
    const val NewProfileSessionUri = "hermes-agent://new-chat-profile"
    const val ShareUri = "hermes-agent://share"
    const val SettingsUri = "hermes-agent://settings"
    const val PanelsUri = "hermes-agent://panels"

    fun sessionUri(sessionId: String?): String? {
        val normalized = sessionId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val encoded = URLEncoder.encode(normalized, StandardCharsets.UTF_8.name()).replace("+", "%20")
        return "hermes-agent://session?id=$encoded"
    }

    fun supportedAction(value: String?): String? =
        value?.takeIf {
            it == NewSessionAction ||
                it == NewVoiceSessionAction ||
                it == NewProfileSessionAction ||
                it == ShareAction
        }
}
