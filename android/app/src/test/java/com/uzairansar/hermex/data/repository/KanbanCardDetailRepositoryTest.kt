package com.uzairansar.hermex.data.repository

import com.uzairansar.hermex.core.model.KanbanContractViolation
import com.uzairansar.hermex.core.network.HermesApiClient
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KanbanCardDetailRepositoryTest {
    @Test
    fun detailUsesVerifiedRouteAndToleratesFutureAndLossyOptionalFields() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(
                json(
                    """
                    {
                      "task": {
                        "id": "CARD/1", "status": "running", "title": "Detail",
                        "current_run_id": 77, "claim_lock": 88,
                        "claim_expires": 99, "worker_pid": 4321
                      },
                      "comments": [
                        {"id": 3, "task_id": 4, "author": 5, "body": 6, "created_at": 7},
                        "future"
                      ],
                      "events": [
                        {"id": 8, "task_id": 9, "run_id": 10, "kind": 11,
                         "payload":{"summary":12,"fields":["status",13],"raw_secret":"discard"}},
                        false
                      ],
                      "links": {"parents":["P-1",2],"children":["C-2"]},
                      "runs": [{"id":14,"status":15,"worker_pid":16,"future":true}, null],
                      "read_only": "false",
                      "future_envelope": {"ignored": true}
                    }
                    """.trimIndent(),
                ),
            )
            val repository = repository(server)

            val detail = repository.cardDetail("CARD/1", "release candidate")

            assertEquals("77", detail.card?.currentRunId)
            assertEquals("4321", detail.card?.workerId)
            assertEquals("3", detail.comments?.single()?.commentId)
            assertEquals("6", detail.comments?.single()?.body)
            assertEquals(listOf("P-1"), detail.links?.prerequisites)
            assertEquals("14", detail.runs?.single()?.stableRunId)
            assertFalse(detail.readOnly ?: true)
            val request = server.takeRequest()
            assertEquals("/api/kanban/tasks/CARD%2F1", request.url.encodedPath)
            assertEquals("release candidate", request.url.queryParameter("board"))
        } finally {
            server.close()
        }
    }

    @Test
    fun workerLogUsesExplicitBoundedRouteAndNeverModelsServerPath() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(
                json(
                    """{"task_id":"CARD-1","path":"/private/worker.log","exists":"true","size_bytes":"42","content":"safe tail","truncated":"false"}""",
                ),
            )
            val repository = repository(server)

            val log = repository.workerLog("CARD-1", "main", Int.MAX_VALUE)

            assertEquals("safe tail", log.content)
            assertEquals(42, log.sizeBytes)
            assertTrue(log.exists == true)
            val request = server.takeRequest()
            assertEquals("/api/kanban/tasks/CARD-1/log", request.url.encodedPath)
            assertEquals("main", request.url.queryParameter("board"))
            assertEquals("2000000", request.url.queryParameter("tail"))
        } finally {
            server.close()
        }
    }

    @Test
    fun commentUsesVerifiedBodyAndDecodesLossyIdentifier() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(json("""{"ok":"true","comment_id":27,"read_only":"false"}"""))
            val repository = repository(server)

            val response = repository.addComment("CARD-1", "release", "A precise comment")

            assertTrue(response.ok == true)
            assertEquals("27", response.commentId)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/kanban/tasks/CARD-1/comments", request.url.encodedPath)
            assertEquals("release", request.url.queryParameter("board"))
            assertEquals("""{"body":"A precise comment"}""", request.body?.utf8())
        } finally {
            server.close()
        }
    }

    @Test
    fun detailRejectsMismatchedIdentityAndMissingStatus() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(json("""{"task":{"id":"OTHER","status":"todo"},"read_only":false}"""))
            server.enqueue(json("""{"task":{"id":"CARD-1"},"read_only":false}"""))
            val repository = repository(server)

            val mismatch = runCatching { repository.cardDetail("CARD-1", "main") }.exceptionOrNull()
            val missingStatus = runCatching { repository.cardDetail("CARD-1", "main") }.exceptionOrNull()

            assertTrue(mismatch is KanbanContractViolation.MissingCardIdentity)
            assertTrue(missingStatus is KanbanContractViolation.MissingCardStatus)
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
