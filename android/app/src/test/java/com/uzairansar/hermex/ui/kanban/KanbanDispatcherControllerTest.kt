package com.uzairansar.hermex.ui.kanban

import com.uzairansar.hermex.core.model.*
import com.uzairansar.hermex.core.network.ApiError
import com.uzairansar.hermex.data.repository.*
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KanbanDispatcherControllerTest {
    @Test fun previewIsOptionalSingleFlightAndBecomesStale() = runTest {
        val h = Harness(this)
        h.controller.preview(); advanceUntilIdle()
        assertEquals(listOf(true), h.repo.requests)
        assertEquals(KanbanDispatchPhase.Succeeded, h.controller.state.value?.phase)
        assertFalse(h.controller.isPreviewStale())
        h.activity += 1
        assertTrue(h.controller.isPreviewStale())
    }

    @Test fun runUsesEightContractThenReconcilesAndPersistsResult() = runTest {
        val h = Harness(this)
        h.controller.run(); advanceUntilIdle()
        assertEquals(listOf(false), h.repo.requests)
        assertEquals(KanbanDispatchPhase.Succeeded, h.controller.state.value?.phase)
        assertEquals(2, h.controller.state.value?.result?.spawned)
        assertEquals(1, h.boardCollections)
        assertEquals(1, h.boardRefreshes)
    }

    @Test fun ambiguousRunNeverRetriesAndRequiresASecondReadOnlyReview() = runTest {
        val h = Harness(this)
        h.repo.failure = ApiError.Network(IOException("lost"))
        h.controller.run(); advanceUntilIdle()
        assertEquals(KanbanDispatchPhase.OutcomeUncertain, h.controller.state.value?.phase)
        assertFalse(h.controller.state.value?.canAcknowledgeUncertainOutcome == true)
        h.repo.failure = null
        h.availability = KanbanDispatcherAvailability.OutcomeUncertain
        h.controller.refreshUncertain(); advanceUntilIdle()
        assertEquals(1, h.repo.requests.size)
        assertTrue(h.controller.state.value?.canAcknowledgeUncertainOutcome == true)
        h.controller.dismiss()
        assertNull(h.controller.state.value)
    }

    @Test fun refusalAndMissingEndpointAreNotRetried() = runTest {
        val h = Harness(this)
        h.repo.failure = ApiError.Http(404, null)
        h.controller.run(); advanceUntilIdle()
        assertEquals(KanbanDispatchPhase.Refused, h.controller.state.value?.phase)
        assertTrue(h.controller.capabilityIncompatible)
        assertEquals(1, h.repo.requests.size)
    }

    private class Harness(scope: kotlinx.coroutines.CoroutineScope) {
        val repo = Repo(); var board = "main"; var activity = 0; var boardCollections = 0; var boardRefreshes = 0
        var availability = KanbanDispatcherAvailability.Available
        val controller: KanbanDispatcherController = KanbanDispatcherController(repo, scope, { board }, { availability }, { activity }, { activity++ },
            { boardCollections++; true }, { boardRefreshes++; true })
    }

    private class Repo : KanbanBrowseDataSource {
        val requests = mutableListOf<Boolean>(); var failure: Throwable? = null
        override suspend fun compatibilityHandshake(): KanbanCompatibilityReport = error("unused")
        override suspend fun boardSnapshot(board: String, filters: KanbanBrowseFilters) = error("unused")
        override suspend fun stats(board: String) = KanbanStats()
        override suspend fun assignees(board: String) = KanbanAssigneeHistory()
        override suspend fun dispatch(board: String, dryRun: Boolean): KanbanDispatchResult {
            requests += dryRun; failure?.let { throw it }; return KanbanDispatchResult(spawned = 2)
        }
    }
}
