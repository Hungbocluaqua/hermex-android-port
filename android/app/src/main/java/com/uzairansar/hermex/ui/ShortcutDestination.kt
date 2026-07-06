package com.uzairansar.hermex.ui

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

    fun supportedAction(value: String?): String? =
        value?.takeIf {
            it == NewSessionAction ||
                it == NewVoiceSessionAction ||
                it == NewProfileSessionAction ||
                it == ShareAction
        }
}
