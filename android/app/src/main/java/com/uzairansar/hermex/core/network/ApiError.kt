package com.uzairansar.hermex.core.network

import kotlinx.serialization.Serializable

sealed class ApiError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Network(cause: Throwable) : ApiError(cause.message ?: "Network request failed.", cause)
    class Http(val statusCode: Int, val body: String?) : ApiError(httpErrorMessage(statusCode, body))
    data object Unauthorized : ApiError("Unauthorized.")
    class Decoding(cause: Throwable) : ApiError(cause.message ?: "Failed to decode response.", cause)
    class InvalidResponse(message: String) : ApiError(message)
    class ResponseTooLarge(val limitBytes: Long) : ApiError("Response exceeded the ${limitBytes / (1024 * 1024)} MB safety limit.")
    class InsecureTransport(host: String) : ApiError("Plain HTTP is only allowed for local or private-network servers, not $host.")
}

private fun httpErrorMessage(statusCode: Int, body: String?): String {
    val serverMessage = body
        ?.let { runCatching { HermesJson.decodeFromString<HttpErrorPayload>(it) }.getOrNull() }
        ?.let { it.error ?: it.message ?: it.detail }
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.take(MAXIMUM_HTTP_ERROR_MESSAGE_CHARACTERS)
        ?.takeIf { it.isNotEmpty() }
    return when (statusCode) {
        400 -> serverMessage?.let { "The server rejected the request: $it" } ?: "The server rejected the request."
        403 -> "The server refused access. Check the server password and permissions."
        404 -> "The requested server endpoint was not found."
        408 -> "The server took too long to respond."
        429 -> "The server is receiving too many requests. Wait a moment, then try again."
        500 -> "The Hermes server hit an internal error. Check the server logs, then try again."
        502, 503, 504 -> "The server or tunnel is unavailable. Check that Hermes Web UI is running and reachable."
        else -> serverMessage?.let { "HTTP $statusCode: $it" } ?: "HTTP $statusCode request failed."
    }
}

@Serializable
private data class HttpErrorPayload(
    val error: String? = null,
    val message: String? = null,
    val detail: String? = null,
)

private const val MAXIMUM_HTTP_ERROR_MESSAGE_CHARACTERS = 500
