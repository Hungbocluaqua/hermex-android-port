package com.uzairansar.hermex.core.network

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HermesApiClientProjectTest {
    @Test
    fun createProjectPostsSelectedColorLikeIos() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .addHeader("Content-Type", "application/json")
                    .body("""{"ok":true,"project":{"project_id":"p1","name":"Android","color":"#7cb9ff"}}""")
                    .build(),
            )

            val client = HermesApiClient(server.url("/"), OkHttpClient())
            val response = client.createProject(name = "Android", color = "#7cb9ff")

            val request = server.takeRequest()
            assertEquals("p1", response.project?.projectId)
            assertEquals("#7cb9ff", response.project?.color)
            assertEquals("/api/projects/create", request.url.encodedPath)
            assertEquals("POST", request.method)
            assertEquals("""{"name":"Android","color":"#7cb9ff"}""", request.body?.utf8())
            assertNull(request.headers["Origin"])
            assertNull(request.headers["Referer"])
        } finally {
            server.close()
        }
    }

    @Test
    fun createProjectPostsExplicitNullForDefaultColor() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .addHeader("Content-Type", "application/json")
                    .body("""{"ok":true,"project":{"project_id":"p1","name":"Default","color":null}}""")
                    .build(),
            )

            val client = HermesApiClient(server.url("/"), OkHttpClient())
            client.createProject(name = "Default", color = null)

            val request = server.takeRequest()
            assertEquals("""{"name":"Default","color":null}""", request.body?.utf8())
        } finally {
            server.close()
        }
    }

    @Test
    fun renameProjectPostsExplicitNullToClearColor() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .addHeader("Content-Type", "application/json")
                    .body("""{"ok":true,"project":{"project_id":"p1","name":"Renamed","color":null}}""")
                    .build(),
            )

            val client = HermesApiClient(server.url("/"), OkHttpClient())
            val response = client.renameProject(projectId = "p1", name = "Renamed", color = null)

            val request = server.takeRequest()
            assertNull(response.project?.color)
            assertEquals("/api/projects/rename", request.url.encodedPath)
            assertEquals("POST", request.method)
            assertEquals(
                """{"project_id":"p1","name":"Renamed","color":null}""",
                request.body?.utf8(),
            )
            assertNull(request.headers["Origin"])
            assertNull(request.headers["Referer"])
        } finally {
            server.close()
        }
    }
}
