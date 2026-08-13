package com.uzairansar.hermex.core.network

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkspaceRegistryApiTest {
    @Test
    fun workspaceMutationsUseVerifiedPathsAndBodies() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            repeat(4) {
                server.enqueue(
                    MockResponse.Builder()
                        .code(200)
                        .addHeader("Content-Type", "application/json")
                        .body("""{"ok":true,"workspaces":[{"path":"/work/a","name":"A"}]}""")
                        .build(),
                )
            }
            val client = HermesApiClient(server.url("/"), OkHttpClient())

            client.addWorkspace("/work/a", "A", true)
            client.removeWorkspace("/work/a")
            client.renameWorkspace("/work/a", "Renamed")
            client.reorderWorkspaces(listOf("/work/b", "/work/a"))

            val add = server.takeRequest()
            assertEquals("/api/workspaces/add", add.url.encodedPath)
            assertEquals("""{"path":"/work/a","name":"A","create":true}""", add.body?.utf8())
            assertNull(add.headers["Origin"])
            assertNull(add.headers["Referer"])

            val remove = server.takeRequest()
            assertEquals("/api/workspaces/remove", remove.url.encodedPath)
            assertEquals("""{"path":"/work/a"}""", remove.body?.utf8())

            val rename = server.takeRequest()
            assertEquals("/api/workspaces/rename", rename.url.encodedPath)
            assertEquals("""{"path":"/work/a","name":"Renamed"}""", rename.body?.utf8())

            val reorder = server.takeRequest()
            assertEquals("/api/workspaces/reorder", reorder.url.encodedPath)
            assertEquals("""{"paths":["/work/b","/work/a"]}""", reorder.body?.utf8())
        } finally {
            server.close()
        }
    }

    @Test
    fun addWorkspaceOmitsOptInFieldsWhenAbsent() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .addHeader("Content-Type", "application/json")
                    .body("""{"ok":true}""")
                    .build(),
            )

            HermesApiClient(server.url("/"), OkHttpClient()).addWorkspace("/work/a")

            assertEquals("""{"path":"/work/a"}""", server.takeRequest().body?.utf8())
        } finally {
            server.close()
        }
    }
}
