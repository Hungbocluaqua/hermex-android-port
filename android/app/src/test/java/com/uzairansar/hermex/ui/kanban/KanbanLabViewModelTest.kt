package com.uzairansar.hermex.ui.kanban

import com.uzairansar.hermex.MainDispatcherRule
import com.uzairansar.hermex.core.model.KanbanAssigneeHistory
import com.uzairansar.hermex.core.model.KanbanBoardSnapshot
import com.uzairansar.hermex.core.model.KanbanBoardSummary
import com.uzairansar.hermex.core.model.KanbanBoardsResponse
import com.uzairansar.hermex.core.model.KanbanBoardMutationEnvelope
import com.uzairansar.hermex.core.model.KanbanCreateBoardRequest
import com.uzairansar.hermex.core.model.KanbanCardSummary
import com.uzairansar.hermex.core.model.KanbanCardDetailEnvelope
import com.uzairansar.hermex.core.model.KanbanBulkActionEnvelope
import com.uzairansar.hermex.core.model.KanbanBulkActionRequestBody
import com.uzairansar.hermex.core.model.KanbanCardMutationEnvelope
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
        assertTrue(viewModel.state.value.canUseCardWorkflow)
        assertTrue(viewModel.canUseBulkActions())
    }

    @Test
    fun missingWorkflowEndpointLeavesCardEditorCapabilityAvailable() = runTest {
        val repository = FakeKanbanBrowseDataSource().apply {
            statusFailure = ApiError.Http(404, "{\"error\":\"Unknown Kanban endpoint; refresh the client\"}")
        }
        val viewModel = KanbanLabViewModel(repository)
        val card = requireNotNull(viewModel.state.value.snapshot?.allCards()?.first())

        viewModel.completeCard(card)

        assertFalse(viewModel.state.value.canUseCardWorkflow)
        assertTrue(viewModel.state.value.canMutateCards)
        assertEquals(KanbanCardMutationPhase.Failed, viewModel.workflowState.value.mutations[card.cardId]?.phase)

        viewModel.load()
        assertTrue(viewModel.state.value.canUseCardWorkflow)
    }

    @Test
    fun selectionSurvivesFiltersAndRefreshButClearsBeforeBoardSwitch() = runTest {
        val repository = FakeKanbanBrowseDataSource()
        val viewModel = KanbanLabViewModel(repository)
        val card = requireNotNull(viewModel.state.value.snapshot?.allCards()?.first())

        viewModel.beginSelectingCards()
        viewModel.toggleCardSelection(card)
        viewModel.applyFilters(KanbanFilterState(tenant = "app"))
        viewModel.load()

        assertTrue(viewModel.bulkState.value.isSelectingCards)
        assertEquals(setOf("CARD-main"), viewModel.bulkState.value.selectedCardIds)

        viewModel.selectBoard("fast")
        assertFalse(viewModel.bulkState.value.isSelectingCards)
        assertTrue(viewModel.bulkState.value.selectedCardIds.isEmpty())
    }

    @Test
    fun missingBulkEndpointClosesOnlyBulkActionsAfterCanonicalReconciliation() = runTest {
        val repository = FakeKanbanBrowseDataSource().apply {
            bulkFailure = ApiError.Http(404, "{\"error\":\"Unknown Kanban endpoint; refresh the client\"}")
        }
        val viewModel = KanbanLabViewModel(repository)
        val card = requireNotNull(viewModel.state.value.snapshot?.allCards()?.first())
        viewModel.beginSelectingCards()
        viewModel.toggleCardSelection(card)

        viewModel.performBulkAction(KanbanBulkAction.ChangeStatus("done"))

        assertTrue(viewModel.bulkState.value.capabilityUnavailable)
        assertFalse(viewModel.canUseBulkActions())
        assertTrue(viewModel.state.value.canMutateCards)
        assertTrue(viewModel.state.value.canUseCardWorkflow)
        assertEquals(1, viewModel.bulkState.value.summary?.failedCount)

        viewModel.load()
        assertFalse(viewModel.bulkState.value.capabilityUnavailable)
        assertTrue(viewModel.canUseBulkActions())
    }

    @Test
    fun bulkSubmissionBlocksCardWorkflowUntilReconciliationFinishes() = runTest {
        val deferred = CompletableDeferred<KanbanBulkActionEnvelope>()
        val repository = FakeKanbanBrowseDataSource().apply {
            bulkLoader = { deferred.await() }
        }
        val viewModel = KanbanLabViewModel(repository)
        val card = requireNotNull(viewModel.state.value.snapshot?.allCards()?.first())
        viewModel.beginSelectingCards()
        viewModel.toggleCardSelection(card)

        viewModel.performBulkAction(KanbanBulkAction.ChangeStatus("done"))
        assertEquals(KanbanBulkActionPhase.Submitting, viewModel.bulkState.value.phase)
        assertFalse(viewModel.canMutateCard(card))
        viewModel.completeCard(card)
        assertEquals(0, repository.statusCalls)

        deferred.complete(KanbanBulkActionEnvelope(readOnly = false))
        assertEquals(null, viewModel.bulkState.value.phase)
        assertEquals(1, repository.bulkCalls.size)
    }

    @Test
    fun makingBoardActiveChangesSharedStateWithoutNavigatingTheLocalBoard() = runTest {
        val repository = FakeKanbanBrowseDataSource()
        val viewModel = KanbanLabViewModel(repository)
        viewModel.selectBoard("fast")
        repository.sharedActive = "beta"

        viewModel.makeBoardActive("beta")

        assertEquals("fast", viewModel.state.value.selectedBoardSlug)
        assertEquals("beta", viewModel.state.value.sharedActiveBoardSlug)
        assertEquals(listOf("beta"), repository.activatedBoards)
        assertEquals(KanbanCardMutationPhase.Succeeded, viewModel.boardState.value.mutation?.phase)
    }

    @Test
    fun archivingTheBrowsedBoardTearsDownBoardStateAndRequiresASelection() = runTest {
        val repository = FakeKanbanBrowseDataSource()
        val viewModel = KanbanLabViewModel(repository)
        viewModel.selectBoard("fast")
        val card = requireNotNull(viewModel.state.value.snapshot?.allCards()?.first())
        viewModel.beginSelectingCards()
        viewModel.toggleCardSelection(card)
        repository.boardSlugs.remove("fast")

        viewModel.archiveBoard("fast")

        assertEquals(null, viewModel.state.value.selectedBoardSlug)
        assertEquals(null, viewModel.state.value.snapshot)
        assertEquals("fast", viewModel.state.value.boardSelectionNotice)
        assertTrue(viewModel.bulkState.value.selectedCardIds.isEmpty())
        assertEquals(KanbanCardMutationPhase.Succeeded, viewModel.boardState.value.mutation?.phase)
    }

    @Test
    fun boardMutationBlocksCardWorkflowUntilCanonicalReconciliation() = runTest {
        val pending = CompletableDeferred<KanbanBoardMutationEnvelope>()
        val repository = FakeKanbanBrowseDataSource().apply {
            createBoardLoader = { pending.await() }
            boardSlugs += "release"
        }
        val viewModel = KanbanLabViewModel(repository)
        val card = requireNotNull(viewModel.state.value.snapshot?.allCards()?.first())

        viewModel.createBoard("release", "Release", "", "", "")
        assertTrue(viewModel.boardState.value.blocksWrites)
        viewModel.completeCard(card)
        assertEquals(0, repository.statusCalls)

        pending.complete(KanbanBoardMutationEnvelope(readOnly = false))
        assertEquals(KanbanCardMutationPhase.Succeeded, viewModel.boardState.value.mutation?.phase)
    }

    @Test
    fun fullReloadInvalidatesAnInFlightBoardMutation() = runTest {
        val pending = CompletableDeferred<KanbanBoardMutationEnvelope>()
        val repository = FakeKanbanBrowseDataSource().apply {
            createBoardLoader = { pending.await() }
            boardSlugs += "release"
        }
        val viewModel = KanbanLabViewModel(repository)

        viewModel.createBoard("release", "Release", "", "", "")
        assertEquals(KanbanCardMutationPhase.Updating, viewModel.boardState.value.mutation?.phase)
        viewModel.load()
        assertEquals(null, viewModel.boardState.value.mutation)

        pending.complete(KanbanBoardMutationEnvelope(readOnly = false))
        assertEquals(null, viewModel.boardState.value.mutation)
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
    fun boardResultPreservesStatusChosenWhileRefreshWasInFlight() = runTest {
        val repository = FakeKanbanBrowseDataSource()
        val slow = CompletableDeferred<KanbanBoardSnapshot>()
        repository.boardLoader = { board, _ -> if (board == "slow") slow.await() else snapshot(board) }
        val viewModel = KanbanLabViewModel(repository)

        viewModel.selectBoard("slow")
        viewModel.selectStatus("todo")
        slow.complete(snapshot("slow", status = "todo"))

        assertEquals("slow", viewModel.state.value.selectedBoardSlug)
        assertEquals("todo", viewModel.state.value.selectedStatus)
        assertEquals(listOf("CARD-slow"), viewModel.state.value.visibleCards.map { it.cardId })
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
        var statusFailure: Throwable? = null
        var bulkFailure: Throwable? = null
        var bulkLoader: suspend () -> KanbanBulkActionEnvelope = { KanbanBulkActionEnvelope(readOnly = false) }
        val bulkCalls = mutableListOf<KanbanBulkActionRequestBody>()
        var statusCalls = 0
        var boardLoader: suspend (String, KanbanBrowseFilters) -> KanbanBoardSnapshot = { board, _ -> snapshot(board) }
        val boardSlugs = mutableListOf(current, "main", "slow", "fast", "alpha", "beta").distinct().toMutableList()
        var sharedActive: String = current
        var createBoardLoader: suspend (KanbanCreateBoardRequest) -> KanbanBoardMutationEnvelope = {
            KanbanBoardMutationEnvelope(readOnly = false)
        }
        val activatedBoards = mutableListOf<String>()

        override suspend fun compatibilityHandshake(): KanbanCompatibilityReport {
            handshakeFailure?.let { throw it }
            val boards = boardSlugs
                .map { KanbanBoardSummary(slug = it, name = it, readOnly = false) }
            return KanbanCompatibilityReport(
                configuration = KanbanConfiguration(columns = listOf("triage", "todo", "blocked", "ready", "running", "done"), readOnly = false),
                boards = boards,
                currentBoard = requireNotNull(boards.firstOrNull { it.slug == sharedActive }),
                snapshot = snapshot(sharedActive),
                warnings = emptyList(),
                boardsReadOnly = false,
            )
        }

        override suspend fun boards(): KanbanBoardsResponse = KanbanBoardsResponse(
            boards = boardSlugs.map { KanbanBoardSummary(slug = it, name = it, readOnly = false) },
            current = sharedActive,
            readOnly = false,
        )

        override suspend fun createBoard(body: KanbanCreateBoardRequest): KanbanBoardMutationEnvelope =
            createBoardLoader(body)

        override suspend fun archiveBoard(slug: String): KanbanBoardMutationEnvelope =
            KanbanBoardMutationEnvelope(readOnly = false)

        override suspend fun makeBoardActive(slug: String): KanbanBoardMutationEnvelope {
            activatedBoards += slug
            sharedActive = slug
            return KanbanBoardMutationEnvelope(readOnly = false)
        }

        override suspend fun boardSnapshot(board: String, filters: KanbanBrowseFilters): KanbanBoardSnapshot {
            lastFilters = filters
            return boardLoader(board, filters)
        }

        override suspend fun stats(board: String): KanbanStats = KanbanStats(total = 1, byStatus = mapOf("triage" to 1))
        override suspend fun assignees(board: String): KanbanAssigneeHistory = KanbanAssigneeHistory()
        override suspend fun setCardStatus(
            cardId: String,
            board: String,
            status: String,
        ): KanbanCardMutationEnvelope {
            statusCalls += 1
            statusFailure?.let { throw it }
            return KanbanCardMutationEnvelope(
                card = KanbanCardSummary(cardId = cardId, title = cardId, status = status),
                readOnly = false,
            )
        }

        override suspend fun performBulkAction(
            board: String,
            body: KanbanBulkActionRequestBody,
        ): KanbanBulkActionEnvelope {
            bulkCalls += body
            bulkFailure?.let { throw it }
            return bulkLoader()
        }

        override suspend fun cardDetail(cardId: String, board: String): KanbanCardDetailEnvelope =
            KanbanCardDetailEnvelope(
                card = snapshot(board).allCards().first().copy(cardId = cardId),
                readOnly = false,
            )
    }

    private companion object {
        fun snapshot(board: String, status: String = "triage") = KanbanBoardSnapshot(
            columns = listOf(
                KanbanColumn(
                    name = status,
                    cards = listOf(KanbanCardSummary(cardId = "CARD-$board", title = board, status = status)),
                ),
            ),
            changed = true,
            readOnly = false,
        )
    }
}
