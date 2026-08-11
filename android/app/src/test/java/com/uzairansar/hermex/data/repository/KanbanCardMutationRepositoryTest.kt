package com.uzairansar.hermex.data.repository

import com.uzairansar.hermex.core.model.KanbanContractViolation
import com.uzairansar.hermex.core.model.KanbanCreateCardRequestBody
import com.uzairansar.hermex.core.model.KanbanEditCardRequestBody
import com.uzairansar.hermex.core.network.HermesApiClient
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KanbanCardMutationRepositoryTest {
    @Test
    fun createUsesVerifiedRouteAndExactIdempotentBody() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(json("""{"task":{"id":"CARD-NEW","title":"New","status":"ready"},"read_only":false}"""))
            val repository = repository(server)
            val body = KanbanCreateCardRequestBody(
                title = "New",
                body = "Body",
                status = "ready",
                priority = 2,
                assignee = "builder",
                tenant = "app",
                workspaceKind = "worktree",
                workspacePath = "/tmp/work",
                skills = listOf("android", "review"),
                maxRuntimeSeconds = 3_600,
                parents = listOf("CARD-P"),
                idempotencyKey = "idem-1",
            )

            val response = repository.createCard("release candidate", body)

            assertEquals("CARD-NEW", response.card?.cardId)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/kanban/tasks", request.url.encodedPath)
            assertEquals("release candidate", request.url.queryParameter("board"))
            assertEquals(
                """{"title":"New","body":"Body","status":"ready","priority":2,"assignee":"builder","tenant":"app","workspace_kind":"worktree","workspace_path":"/tmp/work","skills":["android","review"],"max_runtime_seconds":3600,"parents":["CARD-P"],"idempotency_key":"idem-1"}""",
                request.body?.utf8(),
            )
        } finally {
            server.close()
        }
    }

    @Test
    fun editUsesVerifiedPatchAndExplicitlyClearsNullableFields() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(json("""{"task":{"id":"CARD/1","title":"Edited","status":"todo"},"read_only":false}"""))
            val repository = repository(server)

            val response = repository.editCard(
                cardId = "CARD/1",
                board = "main",
                body = KanbanEditCardRequestBody(
                    title = "Edited",
                    body = "",
                    tenant = null,
                    priority = 0,
                    assignee = null,
                    status = null,
                ),
            )

            assertEquals("CARD/1", response.card?.cardId)
            val request = server.takeRequest()
            assertEquals("PATCH", request.method)
            assertEquals("/api/kanban/tasks/CARD%2F1", request.url.encodedPath)
            assertEquals("main", request.url.queryParameter("board"))
            assertEquals(
                """{"title":"Edited","body":"","tenant":null,"priority":0,"assignee":null}""",
                request.body?.utf8(),
            )
        } finally {
            server.close()
        }
    }

    @Test
    fun editIncludesStatusOnlyWhenTheUserChangedIt() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(json("""{"task":{"id":"CARD-1","status":"ready"},"read_only":false}"""))
            val repository = repository(server)

            repository.editCard(
                "CARD-1",
                "main",
                KanbanEditCardRequestBody("Edited", "Body", "app", 1, "builder", "ready"),
            )

            assertEquals(
                """{"title":"Edited","body":"Body","tenant":"app","priority":1,"assignee":"builder","status":"ready"}""",
                server.takeRequest().body?.utf8(),
            )
        } finally {
            server.close()
        }
    }

    @Test
    fun mutationResponsesRejectMissingStatusAndMismatchedEditIdentity() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(json("""{"task":{"id":"CARD-NEW"},"read_only":false}"""))
            server.enqueue(json("""{"task":{"id":"OTHER","status":"todo"},"read_only":false}"""))
            val repository = repository(server)

            val createError = runCatching {
                repository.createCard(
                    "main",
                    KanbanCreateCardRequestBody(
                        title = "New",
                        status = "triage",
                        workspaceKind = "scratch",
                        idempotencyKey = "idem",
                    ),
                )
            }.exceptionOrNull()
            val editError = runCatching {
                repository.editCard(
                    "CARD-1",
                    "main",
                    KanbanEditCardRequestBody("Edit", "", null, 0, null, null),
                )
            }.exceptionOrNull()

            assertTrue(createError is KanbanContractViolation.MissingCardStatus)
            assertTrue(editError is KanbanContractViolation.MissingCardIdentity)
        } finally {
            server.close()
        }
    }

    private fun repository(server: MockWebServer) =
        KanbanRepository(HermesApiClient(server.url("/"), OkHttpClient()))

    private fun json(body: String): MockResponse = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()
}
