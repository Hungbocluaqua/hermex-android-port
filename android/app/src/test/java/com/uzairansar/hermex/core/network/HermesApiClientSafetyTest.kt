package com.uzairansar.hermex.core.network

import com.uzairansar.hermex.core.model.CronUpdateRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class HermesApiClientSafetyTest {
    @Test
    fun cancellingCoroutineCancelsInFlightCall() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .addHeader("Content-Type", "application/json")
                    .bodyDelay(30, TimeUnit.SECONDS)
                    .body("{\"status\":\"ok\"}")
                    .build(),
            )
            val call = async { HermesApiClient(server.url("/"), OkHttpClient()).health() }
            yield()
            check(server.takeRequest(5, TimeUnit.SECONDS) != null) { "The request did not start." }
            call.cancel()
            withTimeout(2_000) { call.join() }
            assertTrue(call.isCancelled)
        } finally {
            server.close()
        }
    }

    @Test
    fun rejectsDeclaredJsonBodiesOverSafetyLimit() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .setHeader("Content-Length", 17L * 1024L * 1024L)
                    .build(),
            )
            val error = runCatching { HermesApiClient(server.url("/"), OkHttpClient()).health() }.exceptionOrNull()
            assertTrue(error is ApiError.ResponseTooLarge)
        } finally {
            server.close()
        }
    }

    @Test
    fun rejectsWorkspacePreviewsOverSafetyLimit() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .setHeader("Content-Length", 17L * 1024L * 1024L)
                    .build(),
            )
            val error = runCatching {
                HermesApiClient(server.url("/"), OkHttpClient()).rawFile("session-1", "large.png")
            }.exceptionOrNull()
            assertTrue(error is ApiError.ResponseTooLarge)
        } finally {
            server.close()
        }
    }

    @Test
    fun cronUpdateSendsExplicitNullsForClearableFields() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .addHeader("Content-Type", "application/json")
                    .body("{\"ok\":true}")
                    .build(),
            )
            HermesApiClient(server.url("/"), OkHttpClient()).updateCron(
                CronUpdateRequest(jobId = "job-1", model = null, profile = null),
            )
            val body = server.takeRequest().body?.utf8().orEmpty()
            assertTrue(body.contains("\"model\":null"))
            assertTrue(body.contains("\"profile\":null"))
        } finally {
            server.close()
        }
    }

    @Test
    fun unauthorizedResponseNotifiesClientOwner() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(MockResponse.Builder().code(401).body("{\"error\":\"expired\"}").build())
            var unauthorizedUrl: okhttp3.HttpUrl? = null
            val client = HermesApiClient(
                baseUrl = server.url("/"),
                client = OkHttpClient(),
                onUnauthorized = { unauthorizedUrl = it },
            )

            val error = runCatching { client.sessions() }.exceptionOrNull()

            assertTrue(error is ApiError.Unauthorized)
            assertTrue(unauthorizedUrl == server.url("/"))
        } finally {
            server.close()
        }
    }

    @Test
    fun crossOriginMediaUnauthorizedDoesNotInvalidateServerAuthentication() = runBlocking {
        val server = MockWebServer()
        val mediaServer = MockWebServer()
        try {
            server.start()
            mediaServer.start()
            mediaServer.enqueue(MockResponse.Builder().code(401).body("denied").build())
            var invalidations = 0
            val client = HermesApiClient(
                baseUrl = server.url("/"),
                client = OkHttpClient(),
                onUnauthorized = { invalidations += 1 },
            )

            val error = runCatching { client.remoteTranscriptMediaData(mediaServer.url("/private.png")) }.exceptionOrNull()

            assertTrue(error is ApiError.Http && error.statusCode == 401)
            assertEquals(0, invalidations)
        } finally {
            server.close()
            mediaServer.close()
        }
    }

    @Test
    fun rejectsRedirectFromAllowedServerToPublicPlainHttp() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .code(307)
                    .addHeader("Location", "http://example.com/api/auth/login")
                    .build(),
            )

            val error = runCatching {
                HermesApiClient(server.url("/"), OkHttpClient()).login("secret")
            }.exceptionOrNull()

            assertTrue(error is ApiError.InsecureTransport)
            assertEquals("/api/auth/login", server.takeRequest().url.encodedPath)
        } finally {
            server.close()
        }
    }

    @Test
    fun successfulProfileSwitchClearsProfileScopedOwnerState() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .addHeader("Content-Type", "application/json")
                    .body("{\"ok\":true}")
                    .build(),
            )
            val changes = mutableListOf<Pair<String, String>>()
            val client = HermesApiClient(
                baseUrl = server.url("/"),
                client = OkHttpClient(),
                onProfileChanged = { url, profile -> changes += url.toString() to profile },
            )

            client.switchProfile("work")

            assertEquals(listOf(server.url("/").toString() to "work"), changes)
        } finally {
            server.close()
        }
    }

    @Test
    fun failedProfileSwitchKeepsProfileScopedOwnerState() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .addHeader("Content-Type", "application/json")
                    .body("{\"error\":\"unknown profile\"}")
                    .build(),
            )
            val changes = mutableListOf<Pair<String, String>>()
            val client = HermesApiClient(
                baseUrl = server.url("/"),
                client = OkHttpClient(),
                onProfileChanged = { url, profile -> changes += url.toString() to profile },
            )

            client.switchProfile("missing")

            assertTrue(changes.isEmpty())
        } finally {
            server.close()
        }
    }

    @Test
    fun rejectsRemoteTranscriptMediaOverSafetyLimit() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .setHeader("Content-Length", 17L * 1024L * 1024L)
                    .build(),
            )

            val error = runCatching {
                HermesApiClient(server.url("/"), OkHttpClient())
                    .remoteTranscriptMediaData(server.url("/large.png"))
            }.exceptionOrNull()

            assertTrue(error is ApiError.ResponseTooLarge)
        } finally {
            server.close()
        }
    }


    @Test
    fun rejectsServerMediaOverAttachmentLimit() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .setHeader("Content-Length", 21L * 1024L * 1024L)
                    .build(),
            )

            val error = runCatching {
                HermesApiClient(server.url("/"), OkHttpClient()).media("large.bin")
            }.exceptionOrNull()

            assertTrue(error is ApiError.ResponseTooLarge)
        } finally {
            server.close()
        }
    }
}
