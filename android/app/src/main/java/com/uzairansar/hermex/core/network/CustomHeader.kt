package com.uzairansar.hermex.core.network

import kotlinx.serialization.Serializable
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

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
                hasValidHttpSyntax &&
                normalized != "origin" &&
                normalized != "referer" &&
                normalized != "host" &&
                normalized != "content-length"
        }

    internal val hasValidHttpSyntax: Boolean
        get() = runCatching {
            Headers.Builder().add(name.trim(), value).build()
        }.isSuccess
}

fun List<CustomHeader>.sanitized(): List<CustomHeader> = filter { it.isSafeForClient }

fun List<CustomHeader>.applyTo(builder: Request.Builder) {
    sanitized().forEach { header ->
        builder.header(header.name.trim(), header.value)
    }
}

fun parseCustomHeaderLines(text: String): List<CustomHeader> {
    val headers = text
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .mapIndexed { index, line ->
            val delimiterIndex = listOf(line.indexOf(":"), line.indexOf("="))
                .filter { it >= 0 }
                .minOrNull()
                ?: throw IllegalArgumentException("Line ${index + 1}: use Name: Value.")
            val name = line.substring(0, delimiterIndex).trim()
            val value = line.substring(delimiterIndex + 1).trim()
            if (name.isBlank() || value.isBlank()) throw IllegalArgumentException("Line ${index + 1}: header name and value are required.")
            val header = CustomHeader(name, value)
            if (!header.hasValidHttpSyntax) {
                throw IllegalArgumentException("Line ${index + 1}: invalid HTTP header name or value.")
            }
            header
        }
        .toList()
    val sanitized = headers.sanitized()
    if (sanitized.size != headers.size) {
        throw IllegalArgumentException("Origin, Referer, Host, and Content-Length are not allowed.")
    }
    return sanitized
}

class SameOriginCustomHeaderInterceptor(
    private val baseUrl: HttpUrl,
    private val customHeaders: () -> List<CustomHeader>,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val headers = customHeaders().sanitized()
        val controlledNames = headers
            .map { it.name.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }

        if (controlledNames.isEmpty()) return chain.proceed(request)

        val builder = request.newBuilder()
        controlledNames.forEach { name -> builder.removeHeader(name) }

        if (request.url.isSameOriginAs(baseUrl)) {
            headers.forEach { header ->
                builder.header(header.name.trim(), header.value)
            }
        }

        return chain.proceed(builder.build())
    }
}

private fun HttpUrl.isSameOriginAs(other: HttpUrl): Boolean =
    scheme == other.scheme && host.equals(other.host, ignoreCase = true) && port == other.port
