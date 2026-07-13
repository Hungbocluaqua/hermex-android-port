package com.uzairansar.hermex.core.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerTransportPolicyTest {
    @Test
    fun allowsHttpsAndPrivateHttpButRejectsPublicHttp() {
        requireAllowedServerTransport("https://example.com/".toHttpUrl())
        listOf(
            "http://localhost:3000/",
            "http://127.0.0.1:3000/",
            "http://10.0.2.2:3000/",
            "http://172.16.0.1/",
            "http://192.168.1.20/",
            "http://100.96.1.2/",
            "http://hermes.local/",
        ).forEach { requireAllowedServerTransport(it.toHttpUrl()) }

        assertTrue(runCatching { requireAllowedServerTransport("http://example.com/".toHttpUrl()) }.exceptionOrNull() is ApiError.InsecureTransport)
        assertFalse(isPrivateNetworkHost("8.8.8.8"))
    }
}
