package com.uzairansar.hermex.ui.kanban

import com.uzairansar.hermex.MainDispatcherRule
import com.uzairansar.hermex.core.model.KanbanAssigneeHistory
import com.uzairansar.hermex.core.model.KanbanBoardSnapshot
import com.uzairansar.hermex.core.model.KanbanBoardSummary
import com.uzairansar.hermex.core.model.KanbanCardSummary
import com.uzairansar.hermex.core.model.KanbanColumn
import com.uzairansar.hermex.core.model.KanbanCompatibilityReport
import com.uzairansar.hermex.core.model.KanbanConfiguration
import com.uzairansar.hermex.core.model.KanbanContractViolation
import com.uzairansar.hermex.core.model.KanbanStats
import com.uzairansar.hermex.core.network.ApiError
import com.uzairansar.hermex.data.repository.KanbanBrowseDataSource
import com.uzairansar.hermex.data.repository.KanbanBrowseFilters
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KanbanLabViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun initialHandshakePublishesStatusFocusContent() = runTest {
        val repository = FakeKanbanBrowseDataSource()

        val viewModel = KanbanLabViewModel(repository)

        assertEquals(KanbanAvailability.Content, viewModel.state.value.availability)
        assertEquals("main", viewModel.state.value.selectedBoardSlug)
        assertEquals(listOf("CARD-main"), viewModel.state.value.visibleCards.map { it.cardId })
        assertEquals(listOf("triage", "todo", "blocked", "ready", "running", "done"), viewModel.state.value.availableStatuses)
        assertTrue(viewModel.state.value.canMutateCards)
    }

    @Test
    fun staleBoardResponseCannotOverwriteNewerSelection() = runTest {
        val repository = FakeKanbanBrowseDataSource()
        val slow = CompletableDeferred<KanbanBoardSnapshot>()
        repository.boardLoader = { board, _ ->
            when (board) {
                "slow" -> slow.await()
                else -> snapshot(board)
            }
        }
        val viewModel = KanbanLabViewModel(repository)

        viewModel.selectBoard("slow")
        viewModel.selectBoard("fast")
        slow.complete(snapshot("slow"))

        assertEquals("fast", viewModel.state.value.selectedBoardSlug)
        assertEquals(listOf("CARD-fast"), viewModel.state.value.visibleCards.map { it.cardId })
    }

    @Test
    fun onlyMineFilterNeverTransportsAProfileAtTheSameTime() = runTest {
        val repository = FakeKanbanBrowseDataSource()
        val viewModel = KanbanLabViewModel(repository)

        viewModel.applyFilters(
            KanbanFilterState(
                profile = " reviewer ",
                tenant = " app ",
                includeArchived = true,
                onlyMine = true,
                groupByProfile = true,
            ),
        )

        assertEquals(
            KanbanBrowseFilters(profile = null, tenant = "app", includeArchived = true, onlyMine = true),
            repository.lastFilters,
        )
        assertEquals(null, viewModel.state.value.filters.profile)
        assertTrue(viewModel.state.value.filters.groupByProfile)
    }

    @Test
    fun networkRefreshFailurePreservesLoadedBoardAndMarksItOffline() = runTest {
        val repository = FakeKanbanBrowseDataSource()
        val viewModel = KanbanLabViewModel(repository)
        repository.handshakeFailure = IOException("offline")

        viewModel.load()

        assertEquals(KanbanAvailability.Content, viewModel.state.value.availability)
        assertEquals("main", viewModel.state.value.selectedBoardSlug)
        assertFalse(viewModel.state.value.refreshFailed)
        assertTrue(viewModel.state.value.isOffline)
        assertFalse(viewModel.state.value.canMutateCards)
        assertFalse(viewModel.state.value.isRefreshing)
    }

    @Test
    fun separateViewModelsNeverCrossServerBoards() = runTest {
        val first = KanbanLabViewModel(FakeKanbanBrowseDataSource(current = "alpha"))
        val second = KanbanLabViewModel(FakeKanbanBrowseDataSource(current = "beta"))

        assertEquals("alpha", first.state.value.selectedBoardSlug)
        assertEquals("beta", second.state.value.selectedBoardSlug)
        assertEquals(listOf("CARD-alpha"), first.state.value.visibleCards.map { it.cardId })
        assertEquals(listOf("CARD-beta"), second.state.value.visibleCards.map { it.cardId })
    }

    @Test
    fun failuresRemainDistinguishable() {
        assertEquals(KanbanAvailability.AuthenticationRequired, kanbanAvailabilityFor(ApiError.Unauthorized))
        assertEquals(KanbanAvailability.NetworkUnavailable, kanbanAvailabilityFor(IOException()))
        assertEquals(KanbanAvailability.ServerUnavailable, kanbanAvailabilityFor(ApiError.Http(503, null)))
        assertEquals(
            KanbanAvailability.IncompatibleContract,
            kanbanAvailabilityFor(KanbanContractViolation.MissingBoardSnapshot),
        )
    }

    private class FakeKanbanBrowseDataSource(
        private val current: String = "main",
    ) : KanbanBrowseDataSource {
        var handshakeFailure: Throwable? = null
        var lastFilters: KanbanBrowseFilters? = null
        var boardLoader: suspend (String, KanbanBrowseFilters) -> KanbanBoardSnapshot = { board, _ -> snapshot(board) }

        override suspend fun compatibilityHandshake(): KanbanCompatibilityReport {
            handshakeFailure?.let { throw it }
            val boards = listOf(current, "main", "slow", "fast", "alpha", "beta")
                .distinct()
                .map { KanbanBoardSummary(slug = it, name = it, readOnly = false) }
            return KanbanCompatibilityReport(
                configuration = KanbanConfiguration(columns = listOf("triage", "todo", "blocked", "ready", "running", "done"), readOnly = false),
                boards = boards,
                currentBoard = requireNotNull(boards.firstOrNull { it.slug == current }),
                snapshot = snapshot(current),
                warnings = emptyList(),
            )
        }

        override suspend fun boardSnapshot(board: String, filters: KanbanBrowseFilters): KanbanBoardSnapshot {
            lastFilters = filters
            return boardLoader(board, filters)
        }

        override suspend fun stats(board: String): KanbanStats = KanbanStats(total = 1, byStatus = mapOf("triage" to 1))
        override suspend fun assignees(board: String): KanbanAssigneeHistory = KanbanAssigneeHistory()
    }

    private companion object {
        fun snapshot(board: String) = KanbanBoardSnapshot(
            columns = listOf(
                KanbanColumn(
                    name = "triage",
                    cards = listOf(KanbanCardSummary(cardId = "CARD-$board", title = board, status = "triage")),
                ),
            ),
            changed = true,
            readOnly = false,
        )
    }
}
