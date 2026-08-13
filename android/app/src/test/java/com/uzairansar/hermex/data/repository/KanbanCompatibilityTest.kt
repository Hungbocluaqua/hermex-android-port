package com.uzairansar.hermex.data.repository

import com.uzairansar.hermex.core.model.KanbanCompatibilityWarning
import com.uzairansar.hermex.core.model.KanbanConfiguration
import com.uzairansar.hermex.core.model.KanbanBoardsResponse
import com.uzairansar.hermex.core.model.KanbanContractViolation
import com.uzairansar.hermex.core.network.HermesApiClient
import com.uzairansar.hermex.core.network.HermesJson
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KanbanCompatibilityTest {
    @Test
    fun handshakeUsesServerCurrentBoardAndRetainsUnknownStatusesAsWarnings() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(json("""{"columns":["triage"],"assignees":["one",{"name":"two"}],"read_only":false}"""))
            server.enqueue(json("""{"current":"main","boards":[{"slug":"main","name":"Main","read_only":false}],"read_only":false}"""))
            server.enqueue(
                json(
                    """
                    {
                      "changed": true,
                      "columns": [
                        {"name":"triage","tasks":[{"id":"c1","title":"Known","status":"triage"}]},
                        {"name":"future","tasks":[{"id":"c2","title":"Future","status":"future"}]}
                      ],
                      "read_only": true,
                      "future_envelope": "ignored"
                    }
                    """.trimIndent(),
                ),
            )
            val repository = KanbanRepository(HermesApiClient(server.url("/"), OkHttpClient()))

            val report = repository.compatibilityHandshake()

            assertEquals("main", report.currentBoard.slug)
            assertEquals(listOf("one", "two"), report.configuration.assigneeNames)
            assertTrue(report.warnings.contains(KanbanCompatibilityWarning.ReadOnly))
            assertTrue(report.warnings.contains(KanbanCompatibilityWarning.UnsupportedStatus("future")))
            assertEquals("/api/kanban/config", server.takeRequest().url.encodedPath)
            assertEquals("/api/kanban/boards", server.takeRequest().url.encodedPath)
            val boardRequest = server.takeRequest()
            assertEquals("/api/kanban/board", boardRequest.url.encodedPath)
            assertEquals("main", boardRequest.url.queryParameter("board"))
        } finally {
            server.close()
        }
    }

    @Test
    fun handshakeRejectsCardsWithoutIdentity() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(json("""{"columns":["todo"],"read_only":false}"""))
            server.enqueue(json("""{"current":"main","boards":[{"slug":"main","read_only":false}],"read_only":false}"""))
            server.enqueue(json("""{"changed":true,"read_only":false,"columns":[{"name":"todo","tasks":[{"status":"todo"}]}]}"""))
            val repository = KanbanRepository(HermesApiClient(server.url("/"), OkHttpClient()))

            val error = runCatching { repository.compatibilityHandshake() }.exceptionOrNull()

            assertTrue(error is KanbanContractViolation.MissingCardIdentity)
        } finally {
            server.close()
        }
    }

    @Test
    fun handshakeRejectsMinimalUnchangedSnapshotForInitialBrowse() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(json("""{"columns":["todo"],"read_only":false}"""))
            server.enqueue(json("""{"current":"main","boards":[{"slug":"main","read_only":false}],"read_only":false}"""))
            server.enqueue(json("""{"changed":false,"latest_event_id":42,"read_only":false}"""))
            val repository = KanbanRepository(HermesApiClient(server.url("/"), OkHttpClient()))

            val error = runCatching { repository.compatibilityHandshake() }.exceptionOrNull()

            assertTrue(error is KanbanContractViolation.MissingBoardSnapshot)
        } finally {
            server.close()
        }
    }

    @Test
    fun filteredBoardLoadUsesVerifiedQueryAndRetainsUnknownStatus() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(
                json(
                    """{"changed":true,"read_only":false,"columns":[{"name":"future","tasks":[{"id":"c1","status":"future"}]}]}""",
                ),
            )
            val repository = KanbanRepository(HermesApiClient(server.url("/"), OkHttpClient()))

            val snapshot = repository.boardSnapshot(
                board = "release",
                filters = KanbanBrowseFilters(
                    profile = "reviewer",
                    tenant = "mobile",
                    includeArchived = true,
                    onlyMine = false,
                ),
            )

            assertEquals("future", snapshot.columns?.single()?.cards?.single()?.status)
            val request = server.takeRequest()
            assertEquals("/api/kanban/board", request.url.encodedPath)
            assertEquals("release", request.url.queryParameter("board"))
            assertEquals("reviewer", request.url.queryParameter("assignee"))
            assertEquals("mobile", request.url.queryParameter("tenant"))
            assertEquals("true", request.url.queryParameter("include_archived"))
            assertEquals(null, request.url.queryParameter("only_mine"))
        } finally {
            server.close()
        }
    }

    @Test
    fun missingReadOnlySignalsDisableWritesWithoutBlockingBrowse() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(json("""{"columns":["todo"]}"""))
            server.enqueue(json("""{"current":"main","boards":[{"slug":"main"}]}"""))
            server.enqueue(json("""{"changed":true,"columns":[{"name":"todo","tasks":[]}]}"""))
            val repository = KanbanRepository(HermesApiClient(server.url("/"), OkHttpClient()))

            val report = repository.compatibilityHandshake()

            assertTrue(report.warnings.contains(KanbanCompatibilityWarning.WriteCapabilityUnavailable))
        } finally {
            server.close()
        }
    }

    @Test
    fun eventPollingUsesExactVerifiedQueryAndToleratesFutureFields() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(
                json(
                    """{"events":[{"id":43,"task_id":"CARD-1","kind":"updated","future":true}],"cursor":43,"latest_event_id":43,"future_envelope":{}}""",
                ),
            )
            val repository = KanbanRepository(HermesApiClient(server.url("/"), OkHttpClient()))

            val envelope = repository.events(board = "release", since = 42, limit = 75)

            assertEquals(43, envelope.cursor)
            assertEquals("CARD-1", envelope.events?.single()?.cardId)
            val request = server.takeRequest()
            assertEquals("/api/kanban/events", request.url.encodedPath)
            assertEquals("release", request.url.queryParameter("board"))
            assertEquals("42", request.url.queryParameter("since"))
            assertEquals("75", request.url.queryParameter("limit"))
        } finally {
            server.close()
        }
    }

    @Test
    fun kanbanConfigurationDropsOversizedOptionalCountsWithoutRejectingResponse() {
        val decoded = HermesJson.decodeFromString<KanbanConfiguration>(
            """{"columns":["todo"],"assignees":["one"],"future":999999999999}""",
        )

        assertEquals(listOf("todo"), decoded.columns)
        assertEquals(listOf("one"), decoded.assigneeNames)
    }

    @Test
    fun malformedOptionalKanbanMembersDoNotRejectOtherwiseUsableEnvelopes() {
        val config = HermesJson.decodeFromString<KanbanConfiguration>(
            """{"columns":["todo",42,null],"assignees":["one",42,{"name":"two"}]}""",
        )
        val boards = HermesJson.decodeFromString<KanbanBoardsResponse>(
            """{"current":"main","boards":["bad",{"slug":"main","counts":{"todo":2,"future":999999999999}}]}""",
        )

        assertEquals(listOf("todo"), config.columns)
        assertEquals(listOf("one", "two"), config.assigneeNames)
        assertEquals(listOf("main"), boards.boards.orEmpty().map { it.slug })
        assertEquals(mapOf("todo" to 2), boards.boards?.single()?.counts)
    }

    private fun json(body: String): MockResponse = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()
}
