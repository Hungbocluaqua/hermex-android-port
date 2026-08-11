package com.uzairansar.hermex.ui.kanban

import com.uzairansar.hermex.core.model.KanbanAssigneeHistory
import com.uzairansar.hermex.core.model.KanbanBoardSnapshot
import com.uzairansar.hermex.core.model.KanbanCardDetailEnvelope
import com.uzairansar.hermex.core.model.KanbanCardMutationEnvelope
import com.uzairansar.hermex.core.model.KanbanCardSummary
import com.uzairansar.hermex.core.model.KanbanColumn
import com.uzairansar.hermex.core.model.KanbanDependencyLinks
import com.uzairansar.hermex.core.model.KanbanDependencyMutationEnvelope
import com.uzairansar.hermex.core.model.KanbanDependencyRequestBody
import com.uzairansar.hermex.core.model.KanbanStats
import com.uzairansar.hermex.core.network.ApiError
import com.uzairansar.hermex.data.repository.KanbanBrowseDataSource
import com.uzairansar.hermex.data.repository.KanbanBrowseFilters
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KanbanWorkflowControllerTest {
    @Test
    fun mutationsAreOptimisticSerializedPerCardAndConcurrentAcrossCards() = runTest {
        val first = CompletableDeferred<KanbanCardMutationEnvelope>()
        val second = CompletableDeferred<KanbanCardMutationEnvelope>()
        val repository = FakeWorkflowDataSource().apply {
            statusLoader = { cardId, _ -> if (cardId == "CARD-1") first.await() else second.await() }
        }
        val harness = harness(repository, cards = listOf(card("CARD-1", "todo"), card("CARD-2", "ready")))

        harness.controller.moveCard(card("CARD-1", "todo"), "ready")
        harness.controller.completeCard(card("CARD-1", "todo"))
        harness.controller.moveCard(card("CARD-2", "ready"), "todo")
        runCurrent()

        assertEquals(2, repository.statusCalls.size)
        assertEquals("ready", harness.card("CARD-1")?.status)
        assertEquals("todo", harness.card("CARD-2")?.status)
        assertEquals(KanbanCardMutationPhase.Updating, harness.controller.mutationState("CARD-1")?.phase)

        first.complete(mutation("CARD-1", "ready"))
        second.complete(mutation("CARD-2", "todo"))
        runCurrent()

        assertEquals(KanbanCardMutationPhase.Succeeded, harness.controller.mutationState("CARD-1")?.phase)
        assertEquals(KanbanCardMutationPhase.Succeeded, harness.controller.mutationState("CARD-2")?.phase)
    }

    @Test
    fun ambiguousWriteProtectsCardAndExplicitCheckNeverRepeatsWrite() = runTest {
        val repository = FakeWorkflowDataSource().apply {
            statusLoader = { _, _ -> throw IOException("write disconnected") }
            detailResults += Result.failure(IOException("reconciliation disconnected"))
            detailResults += Result.success(detail("CARD-1", "done"))
        }
        val harness = harness(repository)
        val original = card("CARD-1", "todo")

        harness.controller.completeCard(original)
        runCurrent()

        assertEquals(KanbanCardMutationPhase.OutcomeUncertain, harness.controller.mutationState("CARD-1")?.phase)
        assertEquals("todo", harness.card("CARD-1")?.status)
        val emptyRefresh = snapshot(emptyList())
        assertEquals("todo", harness.controller.protectSnapshot(emptyRefresh).allCards().single().status)

        harness.controller.checkUncertainMutation(original)
        runCurrent()

        assertEquals(KanbanCardMutationPhase.Succeeded, harness.controller.mutationState("CARD-1")?.phase)
        assertEquals(1, repository.statusCalls.size)
        assertEquals(2, repository.detailCalls)
    }

    @Test
    fun runningExitRequiresConfirmationAndRunningCanNeverBeDestination() = runTest {
        val repository = FakeWorkflowDataSource()
        val harness = harness(repository, cards = listOf(card("CARD-1", "running")))
        val running = card("CARD-1", "running")

        harness.controller.completeCard(running)
        harness.controller.moveCard(running, "running", confirmingRunningExit = true)
        runCurrent()
        assertTrue(repository.statusCalls.isEmpty())

        harness.controller.completeCard(running, confirmingRunningExit = true)
        runCurrent()
        assertEquals(listOf("CARD-1" to "done"), repository.statusCalls)
    }

    @Test
    fun archiveOffersExpiringUndoAndUndoPrefetchesCanonicalCard() = runTest {
        val repository = FakeWorkflowDataSource().apply {
            detailResults += Result.success(detail("CARD-1", "archived"))
        }
        val harness = harness(repository)

        harness.controller.archiveCard(card("CARD-1", "todo"))
        runCurrent()

        assertNull(harness.card("CARD-1"))
        assertNotNull(harness.controller.state.value.archiveUndo)

        harness.controller.undoArchive()
        runCurrent()

        assertEquals("todo", harness.card("CARD-1")?.status)
        assertEquals(listOf("CARD-1" to "archived", "CARD-1" to "todo"), repository.statusCalls)
        assertEquals(1, repository.detailCalls)
        assertNull(harness.controller.state.value.archiveUndo)

        harness.controller.archiveCard(card("CARD-1", "todo"))
        runCurrent()
        assertNotNull(harness.controller.state.value.archiveUndo)
        advanceTimeBy(8_001)
        runCurrent()
        assertNull(harness.controller.state.value.archiveUndo)
        assertEquals(3, repository.statusCalls.size)
    }

    @Test
    fun dependenciesStayOptimisticUntilFreshDetailAndRefusalsCanRetry() = runTest {
        val repository = FakeWorkflowDataSource().apply {
            detailResults += Result.success(
                detail("CARD-1", "todo", prerequisites = listOf("CARD-2")),
            )
        }
        val harness = harness(repository, cards = listOf(card("CARD-1", "todo"), card("CARD-2", "ready")))
        val target = card("CARD-1", "todo")

        harness.controller.addPrerequisite("CARD-2", target)
        assertEquals(listOf("CARD-2"), harness.controller.displayedPrerequisites("CARD-1", emptyList()))
        runCurrent()

        assertEquals(KanbanCardMutationPhase.Succeeded, harness.controller.mutationState("CARD-1")?.phase)
        assertEquals(listOf("CARD-2"), harness.controller.displayedPrerequisites("CARD-1", emptyList()))
        harness.controller.acknowledgeLoadedCardDetail("CARD-1")
        assertTrue(harness.controller.displayedPrerequisites("CARD-1", emptyList()).isEmpty())

        repository.removeLoader = { throw ApiError.Http(409, "cycle") }
        harness.controller.removePrerequisite("CARD-2", target)
        runCurrent()
        assertEquals(KanbanCardMutationPhase.Failed, harness.controller.mutationState("CARD-1")?.phase)
        assertEquals(1, repository.removeCalls)

        repository.removeLoader = {
            KanbanDependencyMutationEnvelope(ok = true, changed = true, prerequisiteId = "CARD-2", dependentId = "CARD-1")
        }
        repository.detailResults += Result.success(detail("CARD-1", "todo"))
        harness.controller.retryMutation(target)
        runCurrent()
        assertEquals(KanbanCardMutationPhase.Succeeded, harness.controller.mutationState("CARD-1")?.phase)
        assertEquals(2, repository.removeCalls)
    }

    @Test
    fun missingWorkflowEndpointClosesOnlyWorkflowGate() = runTest {
        val repository = FakeWorkflowDataSource().apply {
            statusLoader = { _, _ -> throw ApiError.Http(404, "missing") }
        }
        val harness = harness(repository)

        harness.controller.completeCard(card("CARD-1", "todo"))
        runCurrent()

        assertEquals(1, harness.capabilityClosures)
        assertEquals(KanbanCardMutationPhase.Failed, harness.controller.mutationState("CARD-1")?.phase)
        assertEquals(0, repository.detailCalls)
    }

    @Test
    fun readOnlyMutationResponseClosesGateAndRestoresOptimisticStatus() = runTest {
        val repository = FakeWorkflowDataSource().apply {
            statusLoader = { _, _ -> KanbanCardMutationEnvelope(readOnly = true) }
        }
        val harness = harness(repository)

        harness.controller.completeCard(card("CARD-1", "todo"))
        runCurrent()

        assertEquals("todo", harness.card("CARD-1")?.status)
        assertEquals(1, harness.capabilityClosures)
        assertEquals(KanbanCardMutationPhase.Failed, harness.controller.mutationState("CARD-1")?.phase)
    }

    private fun TestScope.harness(
        repository: FakeWorkflowDataSource,
        cards: List<KanbanCardSummary> = listOf(card("CARD-1", "todo")),
    ): Harness {
        val harness = Harness(snapshot(cards))
        harness.controller = KanbanWorkflowController(
            repository = repository,
            scope = this,
            selectedBoard = { "main" },
            configuredColumns = { listOf("triage", "todo", "ready", "running", "blocked", "done") },
            includesArchived = { false },
            canMutate = { true },
            cardInSnapshot = harness::card,
            replaceCard = { harness.snapshot = harness.snapshot.replacingKanbanCard(it, includeArchived = false) },
            removeCard = { cardId ->
                harness.snapshot = harness.snapshot.copy(
                    columns = harness.snapshot.columns.orEmpty().map { column ->
                        column.copy(cards = column.cards.orEmpty().filterNot { it.cardId == cardId })
                    },
                )
            },
            onStatusSucceeded = { _, _ -> Unit },
            onDetailRefresh = { harness.detailRefreshes += 1 },
            onCapabilityUnavailable = { harness.capabilityClosures += 1 },
        )
        return harness
    }

    private class Harness(var snapshot: KanbanBoardSnapshot) {
        lateinit var controller: KanbanWorkflowController
        var detailRefreshes = 0
        var capabilityClosures = 0
        fun card(cardId: String): KanbanCardSummary? = snapshot.allCards().firstOrNull { it.cardId == cardId }
    }

    private class FakeWorkflowDataSource : KanbanBrowseDataSource {
        var statusLoader: suspend (String, String) -> KanbanCardMutationEnvelope = { cardId, status -> mutation(cardId, status) }
        var blockLoader: suspend (String) -> KanbanCardMutationEnvelope = { mutation(it, "blocked") }
        var unblockLoader: suspend (String) -> KanbanCardMutationEnvelope = { mutation(it, "ready") }
        var addLoader: suspend (KanbanDependencyRequestBody) -> KanbanDependencyMutationEnvelope = {
            KanbanDependencyMutationEnvelope(ok = true, prerequisiteId = it.prerequisiteId, dependentId = it.dependentId)
        }
        var removeLoader: suspend (KanbanDependencyRequestBody) -> KanbanDependencyMutationEnvelope = {
            KanbanDependencyMutationEnvelope(ok = true, changed = true, prerequisiteId = it.prerequisiteId, dependentId = it.dependentId)
        }
        val detailResults = ArrayDeque<Result<KanbanCardDetailEnvelope>>()
        val statusCalls = mutableListOf<Pair<String, String>>()
        var detailCalls = 0
        var removeCalls = 0

        override suspend fun compatibilityHandshake() = error("unused")
        override suspend fun boardSnapshot(board: String, filters: KanbanBrowseFilters) = snapshot(emptyList())
        override suspend fun stats(board: String) = KanbanStats()
        override suspend fun assignees(board: String) = KanbanAssigneeHistory()
        override suspend fun cardDetail(cardId: String, board: String): KanbanCardDetailEnvelope {
            detailCalls += 1
            return detailResults.removeFirst().getOrThrow()
        }
        override suspend fun setCardStatus(cardId: String, board: String, status: String): KanbanCardMutationEnvelope {
            statusCalls += cardId to status
            return statusLoader(cardId, status)
        }
        override suspend fun blockCard(cardId: String, board: String, reason: String?): KanbanCardMutationEnvelope {
            statusCalls += cardId to "blocked"
            return blockLoader(cardId)
        }
        override suspend fun unblockCard(cardId: String, board: String): KanbanCardMutationEnvelope {
            statusCalls += cardId to "ready"
            return unblockLoader(cardId)
        }
        override suspend fun addDependency(
            board: String,
            body: KanbanDependencyRequestBody,
        ): KanbanDependencyMutationEnvelope = addLoader(body)
        override suspend fun removeDependency(
            board: String,
            body: KanbanDependencyRequestBody,
        ): KanbanDependencyMutationEnvelope {
            removeCalls += 1
            return removeLoader(body)
        }
    }

    private companion object {
        fun card(id: String, status: String) = KanbanCardSummary(cardId = id, title = id, status = status)
        fun mutation(id: String, status: String) = KanbanCardMutationEnvelope(card = card(id, status), readOnly = false)
        fun detail(id: String, status: String, prerequisites: List<String> = emptyList()) = KanbanCardDetailEnvelope(
            card = card(id, status),
            links = KanbanDependencyLinks(prerequisites = prerequisites),
            readOnly = false,
        )
        fun snapshot(cards: List<KanbanCardSummary>) = KanbanBoardSnapshot(
            columns = listOf(
                KanbanColumn("triage", cards.filter { it.status == "triage" }),
                KanbanColumn("todo", cards.filter { it.status == "todo" }),
                KanbanColumn("ready", cards.filter { it.status == "ready" }),
                KanbanColumn("running", cards.filter { it.status == "running" }),
                KanbanColumn("blocked", cards.filter { it.status == "blocked" }),
                KanbanColumn("done", cards.filter { it.status == "done" }),
            ),
            changed = true,
            readOnly = false,
        )
    }
}
