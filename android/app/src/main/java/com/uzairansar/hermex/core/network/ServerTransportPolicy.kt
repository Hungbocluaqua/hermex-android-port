package com.uzairansar.hermex.core.network

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

fun requireAllowedServerTransport(url: HttpUrl) {
    if (url.isHttps || isPrivateNetworkHost(url.host)) return
    throw ApiError.InsecureTransport(url.host)
}

internal class ServerTransportPolicyInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        try {
            requireAllowedServerTransport(chain.request().url)
        } catch (error: ApiError.InsecureTransport) {
            throw InsecureTransportIOException(error)
        }
        return chain.proceed(chain.request())
    }
}

internal class InsecureTransportIOException(
    val apiError: ApiError.InsecureTransport,
) : IOException(apiError.message, apiError)

internal fun isPrivateNetworkHost(host: String): Boolean {
    val normalized = host.trim().trimEnd('.').lowercase()
    if (normalized == "localhost" || normalized.endsWith(".localhost") || normalized.endsWith(".local") || normalized.endsWith(".lan") || normalized.endsWith(".test")) {
        return true
    }

    ipv4Octets(normalized)?.let { octets ->
        return when {
            octets[0] == 10 -> true
            octets[0] == 127 -> true
            octets[0] == 169 && octets[1] == 254 -> true
            octets[0] == 172 && octets[1] in 16..31 -> true
            octets[0] == 192 && octets[1] == 168 -> true
            octets[0] == 100 && octets[1] in 64..127 -> true
            else -> false
        }
    }

    if (normalized == "::1") return true
    val firstIpv6Group = normalized.substringBefore(':').toIntOrNull(16) ?: return false
    return firstIpv6Group in 0xfc00..0xfdff || firstIpv6Group in 0xfe80..0xfebf
}

private fun ipv4Octets(host: String): List<Int>? {
    val parts = host.split('.')
    if (parts.size != 4) return null
    return parts.map { it.toIntOrNull()?.takeIf { value -> value in 0..255 } ?: return null }
}
