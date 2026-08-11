package com.uzairansar.hermex.core.network

import com.uzairansar.hermex.core.model.KanbanCreateBoardRequest
import com.uzairansar.hermex.core.model.KanbanEditBoardRequest
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HermesApiClientKanbanBoardTest {
    @Test
    fun boardManagementRequestsMatchTheIosContract() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            repeat(4) {
                server.enqueue(
                    MockResponse.Builder()
                        .code(200)
                        .addHeader("Content-Type", "application/json")
                        .body("{\"board\":{\"slug\":\"release\"},\"current\":\"default\",\"read_only\":false}")
                        .build(),
                )
            }
            val client = HermesApiClient(server.url("/"), OkHttpClient())

            client.createKanbanBoard(
                KanbanCreateBoardRequest("release", "Release", "Shipping", "🚀", "#ff8800"),
            )
            client.editKanbanBoard(
                KanbanEditBoardRequest("release", "Release 2", "Ready", "✓", "#00aa00"),
            )
            client.archiveKanbanBoard("../release")
            client.makeKanbanBoardActive("../release")

            val create = server.takeRequest()
            assertEquals("POST", create.method)
            assertEquals("/api/kanban/boards", create.url.encodedPath)
            assertEquals(
                "{\"slug\":\"release\",\"name\":\"Release\",\"description\":\"Shipping\",\"icon\":\"🚀\",\"color\":\"#ff8800\"}",
                create.body?.utf8(),
            )

            val edit = server.takeRequest()
            assertEquals("PATCH", edit.method)
            assertEquals("/api/kanban/boards/release", edit.url.encodedPath)
            assertEquals(
                "{\"name\":\"Release 2\",\"description\":\"Ready\",\"icon\":\"✓\",\"color\":\"#00aa00\"}",
                edit.body?.utf8(),
            )

            val archive = server.takeRequest()
            assertEquals("DELETE", archive.method)
            assertEquals("/api/kanban/boards/..%2Frelease", archive.url.encodedPath)
            assertNull(archive.url.query)
            assertEquals(0L, archive.bodySize)

            val activate = server.takeRequest()
            assertEquals("POST", activate.method)
            assertEquals("/api/kanban/boards/..%2Frelease/switch", activate.url.encodedPath)
            assertNull(activate.url.query)
            assertEquals(0L, activate.bodySize)

            listOf(create, edit, archive, activate).forEach { request ->
                assertNull(request.headers["Authorization"])
                assertNull(request.headers["Origin"])
                assertNull(request.headers["Referer"])
            }
        } finally {
            server.close()
        }
    }
}
