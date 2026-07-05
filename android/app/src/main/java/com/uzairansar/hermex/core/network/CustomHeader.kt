package com.uzairansar.hermex.core.network

import kotlinx.serialization.Serializable
import okhttp3.Request

@Serializable
data class CustomHeader(
    val name: String,
    val value: String,
) {
    val isSafeForClient: Boolean
        get() {
            val normalized = name.trim().lowercase()
            return normalized.isNotEmpty() &&
                value.isNotBlank() &&
                normalized != "origin" &&
                normalized != "referer" &&
                normalized != "host" &&
                normalized != "content-length"
        }
}

fun List<CustomHeader>.sanitized(): List<CustomHeader> = filter { it.isSafeForClient }

fun List<CustomHeader>.applyTo(builder: Request.Builder) {
    sanitized().forEach { header ->
        builder.header(header.name.trim(), header.value)
    }
}
