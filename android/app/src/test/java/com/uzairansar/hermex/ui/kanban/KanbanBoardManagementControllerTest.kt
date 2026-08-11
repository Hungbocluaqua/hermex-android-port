package com.uzairansar.hermex.ui.kanban

import com.uzairansar.hermex.core.model.KanbanAssigneeHistory
import com.uzairansar.hermex.core.model.KanbanBoardMutationEnvelope
import com.uzairansar.hermex.core.model.KanbanBoardSnapshot
import com.uzairansar.hermex.core.model.KanbanBoardSummary
import com.uzairansar.hermex.core.model.KanbanBoardsResponse
import com.uzairansar.hermex.core.model.KanbanColumn
import com.uzairansar.hermex.core.model.KanbanCompatibilityReport
import com.uzairansar.hermex.core.model.KanbanConfiguration
import com.uzairansar.hermex.core.model.KanbanCreateBoardRequest
import com.uzairansar.hermex.core.model.KanbanEditBoardRequest
import com.uzairansar.hermex.core.model.KanbanStats
import com.uzairansar.hermex.core.network.ApiError
import com.uzairansar.hermex.data.repository.KanbanBrowseDataSource
import com.uzairansar.hermex.data.repository.KanbanBrowseFilters
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KanbanBoardManagementControllerTest {
    @Test
    fun createTrimsAllFieldsAndReconcilesWithoutChangingSharedActiveBoard() = runTest {
        val harness = Harness(this)
        harness.repository.boardsResponse = boards("default", "default", "release")

        harness.controller.create(" release ", " Release ", " Shipping ", " 🚀 ", " #f80 ")
        advanceUntilIdle()

        assertEquals(
            KanbanCreateBoardRequest("release", "Release", "Shipping", "🚀", "#f80"),
            harness.repository.created.single(),
        )
        assertEquals(KanbanCardMutationPhase.Succeeded, harness.controller.state.value.mutation?.phase)
        assertEquals("default", harness.applied.single().current)
    }

    @Test
    fun editRequiresExactCanonicalFieldsAndNeverSendsTheSlugInTheBodyModel() = runTest {
        val harness = Harness(this)
        harness.repository.boardsResponse = KanbanBoardsResponse(
            boards = listOf(
                board("default"),
                KanbanBoardSummary("release", "Release 2", "Ready", "✓", "#0a0", readOnly = false),
            ),
            current = "default",
            readOnly = false,
        )

        harness.controller.edit("release", " Release 2 ", " Ready ", " ✓ ", " #0a0 ")
        advanceUntilIdle()

        assertEquals(
            KanbanEditBoardRequest("release", "Release 2", "Ready", "✓", "#0a0"),
            harness.repository.edited.single(),
        )
        assertEquals(KanbanCardMutationPhase.Succeeded, harness.controller.state.value.mutation?.phase)
    }

    @Test
    fun defaultBoardCannotBeArchivedAndActivationOnlyChangesSharedState() = runTest {
        val harness = Harness(this)
        harness.controller.archive("default")
        advanceUntilIdle()
        assertEquals(0, harness.repository.archiveCalls)
        assertNull(harness.controller.state.value.mutation)

        harness.repository.boardsResponse = boards("release", "default", "release")
        harness.controller.makeActive("release")
        advanceUntilIdle()

        assertEquals(listOf("release"), harness.repository.activated)
        assertEquals(KanbanCardMutationPhase.Succeeded, harness.controller.state.value.mutation?.phase)
        assertEquals("release", harness.applied.single().current)
    }

    @Test
    fun ambiguousWriteUsesReadOnlyChecksAndNeverBlindlyRetries() = runTest {
        val harness = Harness(this)
        harness.repository.createFailure = ApiError.Network(IOException("lost response"))
        harness.repository.boardsFailure = ApiError.Network(IOException("offline"))

        harness.controller.create("release", "Release", "", "", "")
        advanceUntilIdle()

        assertEquals(1, harness.repository.createCalls)
        assertEquals(KanbanCardMutationPhase.OutcomeUncertain, harness.controller.state.value.mutation?.phase)
        assertTrue(harness.controller.state.value.blocksWrites)

        harness.repository.boardsFailure = null
        harness.repository.boardsResponse = boards("default", "default", "release")
        harness.controller.checkResult()
        advanceUntilIdle()

        assertEquals(1, harness.repository.createCalls)
        assertEquals(KanbanCardMutationPhase.Succeeded, harness.controller.state.value.mutation?.phase)
        assertFalse(harness.controller.state.value.blocksWrites)
    }

    @Test
    fun missingEndpointClosesOnlyBoardManagementUntilAFullReload() = runTest {
        val harness = Harness(this)
        harness.repository.createFailure = ApiError.Http(
            404,
            "{\"error\":\"Unknown Kanban endpoint; refresh the client\"}",
        )

        harness.controller.create("release", "Release", "", "", "")
        advanceUntilIdle()

        assertTrue(harness.controller.state.value.capabilityUnavailable)
        assertFalse(harness.controller.canManageBoards())
        assertEquals(KanbanCardMutationPhase.Failed, harness.controller.state.value.mutation?.phase)

        harness.controller.acknowledgeFullReload()
        assertFalse(harness.controller.state.value.capabilityUnavailable)
        assertTrue(harness.controller.canManageBoards())
    }

    @Test
    fun resourceNotFoundDoesNotMisclassifyTheWholeBoardCapability() = runTest {
        val harness = Harness(this)
        harness.repository.createFailure = ApiError.Http(404, "{\"error\":\"board not found\"}")

        harness.controller.create("release", "Release", "", "", "")
        advanceUntilIdle()

        assertFalse(harness.controller.state.value.capabilityUnavailable)
        assertEquals(KanbanCardMutationPhase.Failed, harness.controller.state.value.mutation?.phase)
    }

    @Test
    fun malformedCanonicalBoardCollectionLeavesTheOutcomeUncertain() = runTest {
        val harness = Harness(this)
        harness.repository.boardsResponse = KanbanBoardsResponse(
            boards = listOf(board("release")),
            current = null,
            readOnly = false,
        )

        harness.controller.create("release", "Release", "", "", "")
        advanceUntilIdle()

        assertEquals(KanbanCardMutationPhase.OutcomeUncertain, harness.controller.state.value.mutation?.phase)
        assertTrue(harness.controller.state.value.blocksWrites)
        assertTrue(harness.applied.isEmpty())
    }

    @Test
    fun unrelatedBoardActivitySerializesBoardMutations() = runTest {
        val harness = Harness(this)
        harness.otherActivity = true

        harness.controller.create("release", "Release", "", "", "")
        advanceUntilIdle()

        assertEquals(0, harness.repository.createCalls)
        assertFalse(harness.controller.canManageBoards())
    }

    private class Harness(scope: kotlinx.coroutines.CoroutineScope) {
        val repository = FakeRepository()
        val applied = mutableListOf<KanbanBoardsResponse>()
        var baseAllows = true
        var otherActivity = false
        val controller = KanbanBoardManagementController(
            repository = repository,
            scope = scope,
            baseAllowsMutation = { baseAllows },
            hasOtherBoardActivity = { otherActivity },
            boardExists = { it in setOf("default", "release") },
            applyBoardsResponse = applied::add,
        )
    }

    private class FakeRepository : KanbanBrowseDataSource {
        var boardsResponse = boards("default", "default", "release")
        var boardsFailure: Throwable? = null
        var createFailure: Throwable? = null
        var createCalls = 0
        var archiveCalls = 0
        val created = mutableListOf<KanbanCreateBoardRequest>()
        val edited = mutableListOf<KanbanEditBoardRequest>()
        val activated = mutableListOf<String>()

        override suspend fun compatibilityHandshake(): KanbanCompatibilityReport = error("unused")
        override suspend fun boardSnapshot(board: String, filters: KanbanBrowseFilters): KanbanBoardSnapshot =
            KanbanBoardSnapshot(listOf(KanbanColumn("todo", emptyList())), changed = true, readOnly = false)
        override suspend fun stats(board: String) = KanbanStats()
        override suspend fun assignees(board: String) = KanbanAssigneeHistory()
        override suspend fun boards(): KanbanBoardsResponse {
            boardsFailure?.let { throw it }
            return boardsResponse
        }
        override suspend fun createBoard(body: KanbanCreateBoardRequest): KanbanBoardMutationEnvelope {
            createCalls += 1
            created += body
            createFailure?.let { throw it }
            return KanbanBoardMutationEnvelope(readOnly = false)
        }
        override suspend fun editBoard(body: KanbanEditBoardRequest): KanbanBoardMutationEnvelope {
            edited += body
            return KanbanBoardMutationEnvelope(readOnly = false)
        }
        override suspend fun archiveBoard(slug: String): KanbanBoardMutationEnvelope {
            archiveCalls += 1
            return KanbanBoardMutationEnvelope(readOnly = false)
        }
        override suspend fun makeBoardActive(slug: String): KanbanBoardMutationEnvelope {
            activated += slug
            return KanbanBoardMutationEnvelope(readOnly = false)
        }
    }

    companion object {
        private fun board(slug: String) = KanbanBoardSummary(slug = slug, name = slug, readOnly = false)
        private fun boards(current: String, vararg slugs: String) = KanbanBoardsResponse(
            boards = slugs.map(::board),
            current = current,
            readOnly = false,
        )
    }
}
