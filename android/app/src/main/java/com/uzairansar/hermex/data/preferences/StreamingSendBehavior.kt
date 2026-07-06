package com.uzairansar.hermex.data.preferences

enum class StreamingSendBehavior(
    val storageValue: String,
    val title: String,
    val settingsDescription: String,
    val actionLabel: String,
) {
    Steer("steer", "Steer", "Steer active response", "Steer"),
    Interrupt("interrupt", "Interrupt", "Stop and send", "Interrupt"),
    Queue("queue", "Queue", "Send after response", "Queue");

    companion object {
        fun fromStorageValue(value: String?): StreamingSendBehavior =
            entries.firstOrNull { it.storageValue == value } ?: Steer
    }
}
