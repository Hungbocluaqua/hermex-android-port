package com.uzairansar.hermex.core.network

sealed class ApiError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Network(cause: Throwable) : ApiError(cause.message ?: "Network request failed.", cause)
    class Http(val statusCode: Int, val body: String?) : ApiError("HTTP $statusCode${body?.let { ": $it" }.orEmpty()}")
    data object Unauthorized : ApiError("Unauthorized.")
    class Decoding(cause: Throwable) : ApiError(cause.message ?: "Failed to decode response.", cause)
}
