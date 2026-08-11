package com.uzairansar.hermex.ui.kanban

import com.uzairansar.hermex.core.model.KanbanAssigneeHistory
import com.uzairansar.hermex.core.model.KanbanBoardSnapshot
import com.uzairansar.hermex.core.model.KanbanBulkActionEnvelope
import com.uzairansar.hermex.core.model.KanbanBulkActionRequestBody
import com.uzairansar.hermex.core.model.KanbanCardDetailEnvelope
import com.uzairansar.hermex.core.model.KanbanCardSummary
import com.uzairansar.hermex.core.model.KanbanColumn
import com.uzairansar.hermex.core.model.KanbanCompatibilityReport
import com.uzairansar.hermex.core.model.KanbanConfiguration
import com.uzairansar.hermex.core.model.KanbanEventsEnvelope
import com.uzairansar.hermex.core.model.KanbanStats
import com.uzairansar.hermex.core.network.ApiError
import com.uzairansar.hermex.data.repository.KanbanBrowseDataSource
import com.uzairansar.hermex.data.repository.KanbanBrowseFilters
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KanbanBulkActionControllerTest {
    @Test
    fun selectionSurvivesSnapshotChangesButNeverCrossesBoards() = runTest {
        val harness = harness(cards = listOf(card("CARD-1", "todo"), card("CARD-2", "ready")))
        harness.controller.beginSelection()
        harness.controller.toggleSelection(card("CARD-1", "todo"))

        harness.controller.acknowledgeSnapshot(emptyList())
        assertTrue(harness.controller.state.value.isSelectingCards)
        assertEquals(setOf("CARD-1"), harness.controller.state.value.selectedCardIds)

        harness.board = "release"
        harness.controller.resetForBoardChange()
        assertFalse(harness.controller.state.value.isSelectingCards)
        assertTrue(harness.controller.state.value.selectedCardIds.isEmpty())
        assertEquals(null, harness.controller.state.value.summary)
    }

    @Test
    fun availabilityRejectsUnknownStatusRunningDestinationInvalidProfileAndPriority() = runTest {
        val harness = harness(cards = listOf(card("FUTURE-1", "future")))
        harness.controller.beginSelection()
        harness.controller.toggleSelection(card("FUTURE-1", "future"))

        assertEquals(KanbanBulkActionsAvailability.UnknownStatus, harness.controller.availability())
        assertFalse(harness.controller.canSubmit(KanbanBulkAction.ChangeStatus("running")))
        assertFalse(harness.controller.canSubmit(KanbanBulkAction.AssignProfile("missing")))
        assertFalse(harness.controller.canSubmit(KanbanBulkAction.SetPriority(101)))
    }

    @Test
    fun partialResultRefetchesEveryCardAndRetryWritesOnlyConfirmedFailures() = runTest {
        val repository = FakeBulkDataSource().apply {
            detailResults["CARD-1"] = ArrayDeque(listOf(Result.success(detail("CARD-1", "done"))))
            detailResults["CARD-2"] = ArrayDeque(
                listOf(
                    Result.success(detail("CARD-2", "todo")),
                    Result.success(detail("CARD-2", "done")),
                ),
            )
        }
        val harness = harness(repository, listOf(card("CARD-1", "todo"), card("CARD-2", "todo")))
        harness.selectAll()

        harness.controller.perform(KanbanBulkAction.ChangeStatus("done"))
        runCurrent()

        assertEquals(1, harness.controller.state.value.summary?.succeededCount)
        assertEquals(1, harness.controller.state.value.summary?.failedCount)
        assertEquals(setOf("CARD-2"), harness.controller.state.value.selectedCardIds)
        assertTrue(harness.controller.canRetryFailed())
        assertEquals(setOf("CARD-1", "CARD-2"), repository.detailCalls.toSet())

        harness.controller.retryFailed()
        runCurrent()

        assertEquals(1, harness.controller.state.value.summary?.succeededCount)
        assertEquals(0, harness.controller.state.value.summary?.failedCount)
        assertTrue(harness.controller.state.value.selectedCardIds.isEmpty())
        assertEquals(listOf(listOf("CARD-1", "CARD-2"), listOf("CARD-2")), repository.bulkCalls.map { it.ids })
    }

    @Test
    fun transportFailureStillReconcilesAllCardsWithoutBlindRetry() = runTest {
        val repository = FakeBulkDataSource().apply {
            bulkError = IOException("write disconnected")
            detailResults["CARD-1"] = ArrayDeque(listOf(Result.success(detail("CARD-1", "done"))))
            detailResults["CARD-2"] = ArrayDeque(listOf(Result.success(detail("CARD-2", "done"))))
        }
        val harness = harness(repository, listOf(card("CARD-1", "todo"), card("CARD-2", "todo")))
        harness.selectAll()

        harness.controller.perform(KanbanBulkAction.ChangeStatus("done"))
        runCurrent()

        assertEquals(2, harness.controller.state.value.summary?.succeededCount)
        assertEquals(1, repository.bulkCalls.size)
        assertEquals(setOf("CARD-1", "CARD-2"), repository.detailCalls.toSet())
    }

    @Test
    fun uncertainResultRequiresReadOnlyCheckAndNeverRepeatsWrite() = runTest {
        val repository = FakeBulkDataSource().apply {
            detailResults["CARD-1"] = ArrayDeque(
                listOf(
                    Result.failure(IOException("detail disconnected")),
                    Result.success(detail("CARD-1", "done")),
                ),
            )
        }
        val harness = harness(repository, listOf(card("CARD-1", "todo")))
        harness.selectAll()

        harness.controller.perform(KanbanBulkAction.ChangeStatus("done"))
        runCurrent()
        assertEquals(1, harness.controller.state.value.summary?.uncertainCount)
        assertFalse(harness.controller.canRetryFailed())
        assertTrue(harness.controller.canCheckUncertain())

        harness.controller.checkUncertain()
        runCurrent()
        assertEquals(1, harness.controller.state.value.summary?.succeededCount)
        assertEquals(1, repository.bulkCalls.size)
        assertEquals(2, repository.detailCalls.size)
    }

    @Test
    fun missingEndpointAndReadOnlyResponseCloseOnlyTheBulkCapability() = runTest {
        val missingRepository = FakeBulkDataSource().apply {
            bulkError = ApiError.Http(404, "missing")
            detailResults["CARD-1"] = ArrayDeque(listOf(Result.success(detail("CARD-1", "todo"))))
        }
        val missing = harness(missingRepository, listOf(card("CARD-1", "todo")))
        missing.selectAll()
        missing.controller.perform(KanbanBulkAction.ChangeStatus("done"))
        runCurrent()

        assertTrue(missing.controller.state.value.capabilityUnavailable)
        assertEquals(KanbanBulkActionsAvailability.Incompatible, missing.controller.availability())
        assertTrue(missing.baseAllowsMutation)

        val readOnlyRepository = FakeBulkDataSource().apply {
            bulkResponse = KanbanBulkActionEnvelope(readOnly = true)
            detailResults["CARD-1"] = ArrayDeque(listOf(Result.success(detail("CARD-1", "todo"))))
        }
        val readOnly = harness(readOnlyRepository, listOf(card("CARD-1", "todo")))
        readOnly.selectAll()
        readOnly.controller.perform(KanbanBulkAction.ChangeStatus("done"))
        runCurrent()
        assertTrue(readOnly.controller.state.value.capabilityUnavailable)
        assertTrue(readOnly.baseAllowsMutation)
    }

    @Test
    fun runningExitAndArchiveRequireTheirExplicitConfirmations() = runTest {
        val repository = FakeBulkDataSource().apply {
            detailResults["CARD-1"] = ArrayDeque(
                listOf(
                    Result.success(detail("CARD-1", "done")),
                    Result.success(detail("CARD-1", "archived")),
                ),
            )
        }
        val harness = harness(repository, listOf(card("CARD-1", "running")))
        harness.selectAll()

        harness.controller.perform(KanbanBulkAction.ChangeStatus("done"))
        runCurrent()
        assertTrue(repository.bulkCalls.isEmpty())

        harness.controller.perform(KanbanBulkAction.ChangeStatus("done"), confirmedRunningExit = true)
        runCurrent()
        assertEquals(1, repository.bulkCalls.size)

        harness.cards["CARD-1"] = card("CARD-1", "running")
        harness.controller.acknowledgeSnapshot(harness.cards.values.toList())
        harness.controller.toggleSelection(harness.cards.getValue("CARD-1"))
        harness.controller.perform(KanbanBulkAction.ArchiveCards, confirmedArchive = true)
        runCurrent()
        assertEquals(1, repository.bulkCalls.size)

        harness.controller.perform(
            KanbanBulkAction.ArchiveCards,
            confirmedRunningExit = true,
            confirmedArchive = true,
        )
        runCurrent()
        assertEquals(2, repository.bulkCalls.size)
    }

    @Test
    fun canonicalDetailReconciliationRunsConcurrentlyWithACapOfFour() = runTest {
        val gate = CompletableDeferred<Unit>()
        var active = 0
        var maximumActive = 0
        val repository = FakeBulkDataSource().apply {
            detailLoader = { cardId ->
                active += 1
                maximumActive = maxOf(maximumActive, active)
                gate.await()
                active -= 1
                detail(cardId, "done")
            }
        }
        val cards = (1..6).map { card("CARD-$it", "todo") }
        val harness = harness(repository, cards)
        harness.selectAll()

        harness.controller.perform(KanbanBulkAction.ChangeStatus("done"))
        runCurrent()
        assertEquals(4, repository.detailCalls.size)
        assertEquals(4, maximumActive)

        gate.complete(Unit)
        runCurrent()
        assertEquals(6, repository.detailCalls.size)
        assertEquals(6, harness.controller.state.value.summary?.succeededCount)
        assertEquals(4, maximumActive)
    }

    private fun TestScope.harness(
        repository: FakeBulkDataSource = FakeBulkDataSource(),
        cards: List<KanbanCardSummary> = listOf(card("CARD-1", "todo")),
    ): BulkHarness = BulkHarness(this, repository, cards)

    private class BulkHarness(
        scope: TestScope,
        val repository: FakeBulkDataSource,
        initialCards: List<KanbanCardSummary>,
    ) {
        var board = "main"
        var offline = false
        var refreshing = false
        var baseAllowsMutation = true
        var compatible = true
        var readOnly = false
        var otherBoardActivity = false
        var refreshes = 0
        val cards = initialCards.associateByTo(linkedMapOf()) { requireNotNull(it.cardId) }
        val controller = KanbanBulkActionController(
            repository = repository,
            scope = scope,
            selectedBoard = { board },
            configuredColumns = { listOf("triage", "todo", "ready", "blocked", "running", "done") },
            profileOptions = { listOf("builder", "reviewer") },
            isOffline = { offline },
            isRefreshing = { refreshing },
            baseAllowsMutation = { baseAllowsMutation },
            contractCompatible = { compatible },
            isReadOnly = { readOnly },
            hasOtherBoardActivity = { otherBoardActivity },
            cardInSnapshot = cards::get,
            replaceCard = { card -> cards[requireNotNull(card.cardId)] = card },
            refreshBoard = { refreshes += 1 },
        )

        fun selectAll() {
            controller.beginSelection()
            cards.values.toList().forEach(controller::toggleSelection)
        }
    }

    private class FakeBulkDataSource : KanbanBrowseDataSource {
        val bulkCalls = mutableListOf<KanbanBulkActionRequestBody>()
        val detailCalls = mutableListOf<String>()
        val detailResults = mutableMapOf<String, ArrayDeque<Result<KanbanCardDetailEnvelope>>>()
        var detailLoader: (suspend (String) -> KanbanCardDetailEnvelope)? = null
        var bulkResponse = KanbanBulkActionEnvelope(readOnly = false)
        var bulkError: Throwable? = null

        override suspend fun compatibilityHandshake(): KanbanCompatibilityReport = error("unused")
        override suspend fun boardSnapshot(board: String, filters: KanbanBrowseFilters): KanbanBoardSnapshot =
            KanbanBoardSnapshot(columns = listOf(KanbanColumn("todo", emptyList())), changed = true, readOnly = false)
        override suspend fun stats(board: String) = KanbanStats()
        override suspend fun assignees(board: String) = KanbanAssigneeHistory()
        override suspend fun events(board: String, since: Int, limit: Int) = KanbanEventsEnvelope(emptyList(), since)

        override suspend fun performBulkAction(
            board: String,
            body: KanbanBulkActionRequestBody,
        ): KanbanBulkActionEnvelope {
            bulkCalls += body
            bulkError?.let { throw it }
            return bulkResponse
        }

        override suspend fun cardDetail(cardId: String, board: String): KanbanCardDetailEnvelope {
            detailCalls += cardId
            detailLoader?.let { return it(cardId) }
            return detailResults[cardId]?.removeFirstOrNull()?.getOrThrow()
                ?: error("No detail result for $cardId")
        }
    }
}

private fun card(id: String, status: String) = KanbanCardSummary(cardId = id, title = id, status = status)

private fun detail(id: String, status: String) = KanbanCardDetailEnvelope(card = card(id, status), readOnly = false)
