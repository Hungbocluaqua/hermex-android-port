package com.uzairansar.hermex.ui.kanban

import com.uzairansar.hermex.MainDispatcherRule
import com.uzairansar.hermex.core.model.KanbanAssigneeHistory
import com.uzairansar.hermex.core.model.KanbanBoardSnapshot
import com.uzairansar.hermex.core.model.KanbanBoardSummary
import com.uzairansar.hermex.core.model.KanbanCardSummary
import com.uzairansar.hermex.core.model.KanbanColumn
import com.uzairansar.hermex.core.model.KanbanCompatibilityReport
import com.uzairansar.hermex.core.model.KanbanConfiguration
import com.uzairansar.hermex.core.model.KanbanEvent
import com.uzairansar.hermex.core.model.KanbanEventsEnvelope
import com.uzairansar.hermex.core.model.KanbanStats
import com.uzairansar.hermex.core.network.ApiError
import com.uzairansar.hermex.core.network.KanbanStreamFrame
import com.uzairansar.hermex.data.repository.KanbanBrowseDataSource
import com.uzairansar.hermex.data.repository.KanbanBrowseFilters
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KanbanLiveUpdateViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun sseEventBurstCoalescesIntoOneCanonicalBoardRefresh() = runTest(mainDispatcherRule.dispatcher) {
        val stream = MutableSharedFlow<KanbanStreamFrame>(extraBufferCapacity = 8)
        val repository = LiveFakeDataSource(streamFactory = { _, _ -> stream })
        val viewModel = KanbanLabViewModel(
            repository,
            KanbanLiveTiming(coalescingDelayMillis = 100),
        )
        runCurrent()
        viewModel.setVisible(true)
        viewModel.setLifecycleActive(true)
        runCurrent()

        stream.emit(KanbanStreamFrame.Hello(10, "main"))
        stream.emit(KanbanStreamFrame.Events(listOf(event(11)), cursor = 11, frameId = 11))
        stream.emit(KanbanStreamFrame.Events(listOf(event(12)), cursor = 12, frameId = 12))
        runCurrent()
        advanceTimeBy(99)
        runCurrent()
        assertEquals(0, repository.boardRefreshCount)

        advanceTimeBy(1)
        runCurrent()

        assertEquals(1, repository.boardRefreshCount)
        assertEquals(listOf("main" to 10), repository.streamRequests)
        assertEquals("refresh-1", viewModel.state.value.visibleCards.single().title)
        viewModel.setVisible(false)
    }

    @Test
    fun repeatedStreamFailuresFallBackToPollingAndShowDelayedNotice() = runTest(mainDispatcherRule.dispatcher) {
        val repository = LiveFakeDataSource(
            streamFactory = { _, _ -> flow { throw IOException("stream unavailable") } },
        )
        val viewModel = KanbanLabViewModel(
            repository,
            KanbanLiveTiming(
                reconnectDelaysMillis = listOf(10),
                failuresBeforePolling = 3,
                pollingIntervalMillis = 30_000,
            ),
        )
        runCurrent()
        viewModel.setVisible(true)
        viewModel.setLifecycleActive(true)
        runCurrent()
        advanceTimeBy(10)
        runCurrent()
        advanceTimeBy(10)
        runCurrent()

        assertEquals(3, repository.streamRequests.size)
        assertTrue(viewModel.state.value.liveUpdatesDelayed)
        assertFalse(viewModel.state.value.isOffline)
        assertEquals(0, repository.eventPollRequests.size)
        viewModel.setVisible(false)
    }

    @Test
    fun pollingUsesCurrentCursorAndCoalescesAChangedEnvelope() = runTest(mainDispatcherRule.dispatcher) {
        val repository = LiveFakeDataSource(
            streamFactory = { _, _ -> flow { throw IOException("stream unavailable") } },
        ).apply {
            eventsEnvelope = KanbanEventsEnvelope(events = listOf(event(11)), cursor = 11)
        }
        val viewModel = KanbanLabViewModel(
            repository,
            KanbanLiveTiming(
                failuresBeforePolling = 1,
                pollingIntervalMillis = 100,
                coalescingDelayMillis = 20,
            ),
        )
        runCurrent()
        viewModel.setVisible(true)
        viewModel.setLifecycleActive(true)
        runCurrent()
        advanceTimeBy(100)
        runCurrent()

        assertEquals(listOf("main" to 10), repository.eventPollRequests)
        assertEquals(0, repository.boardRefreshCount)
        advanceTimeBy(20)
        runCurrent()

        assertEquals(1, repository.boardRefreshCount)
        assertTrue(viewModel.state.value.liveUpdatesDelayed)
        viewModel.setVisible(false)
    }

    @Test
    fun networkFailurePreservesSnapshotUntilFullReconciliationSucceeds() = runTest(mainDispatcherRule.dispatcher) {
        val repository = LiveFakeDataSource()
        val viewModel = KanbanLabViewModel(repository)
        runCurrent()
        val loadedCard = viewModel.state.value.visibleCards.single()
        viewModel.setVisible(true)
        viewModel.setLifecycleActive(true)
        runCurrent()
        repository.handshakeFailure = ApiError.Network(IOException("offline"))

        viewModel.load()
        runCurrent()

        assertTrue(viewModel.state.value.isOffline)
        assertFalse(viewModel.state.value.refreshFailed)
        assertEquals(loadedCard, viewModel.state.value.visibleCards.single())

        repository.handshakeFailure = null
        viewModel.load()
        runCurrent()

        assertFalse(viewModel.state.value.isOffline)
        assertEquals(KanbanAvailability.Content, viewModel.state.value.availability)
        viewModel.setVisible(false)
    }

    @Test
    fun backgroundSuspendsLiveWorkAndForegroundPerformsFullReconciliation() = runTest(mainDispatcherRule.dispatcher) {
        val repository = LiveFakeDataSource()
        val viewModel = KanbanLabViewModel(repository)
        runCurrent()
        viewModel.setVisible(true)
        viewModel.setLifecycleActive(true)
        runCurrent()
        assertEquals(1, repository.handshakeCount)
        assertEquals(1, repository.streamRequests.size)

        viewModel.setLifecycleActive(false)
        runCurrent()
        viewModel.setLifecycleActive(true)
        runCurrent()

        assertEquals(2, repository.handshakeCount)
        assertEquals(2, repository.streamRequests.size)
        viewModel.setVisible(false)
    }

    @Test
    fun callbacksFromPreviousBoardBecomeInertAfterLocalBoardSwitch() = runTest(mainDispatcherRule.dispatcher) {
        val mainStream = MutableSharedFlow<KanbanStreamFrame>(extraBufferCapacity = 4)
        val otherStream = MutableSharedFlow<KanbanStreamFrame>(extraBufferCapacity = 4)
        val repository = LiveFakeDataSource(
            streamFactory = { board, _ -> if (board == "main") mainStream else otherStream },
        )
        val viewModel = KanbanLabViewModel(
            repository,
            KanbanLiveTiming(coalescingDelayMillis = 10),
        )
        runCurrent()
        viewModel.setVisible(true)
        viewModel.setLifecycleActive(true)
        runCurrent()

        viewModel.selectBoard("other")
        runCurrent()
        assertEquals("other", viewModel.state.value.selectedBoardSlug)
        assertEquals(1, repository.boardRefreshCount)

        mainStream.emit(KanbanStreamFrame.Events(listOf(event(13)), cursor = 13, frameId = 13))
        advanceTimeBy(10)
        runCurrent()

        assertEquals(1, repository.boardRefreshCount)
        assertEquals(listOf("main" to 10, "other" to 12), repository.streamRequests)
        viewModel.setVisible(false)
    }

    @Test
    fun malformedStreamFrameCannotMutateSnapshotAndTriggersFallback() = runTest(mainDispatcherRule.dispatcher) {
        val repository = LiveFakeDataSource(
            streamFactory = { _, _ -> flow { emit(KanbanStreamFrame.Malformed) } },
        )
        val viewModel = KanbanLabViewModel(
            repository,
            KanbanLiveTiming(failuresBeforePolling = 1, pollingIntervalMillis = 30_000),
        )
        runCurrent()
        val initial = viewModel.state.value.snapshot
        viewModel.setVisible(true)
        viewModel.setLifecycleActive(true)
        runCurrent()

        assertEquals(initial, viewModel.state.value.snapshot)
        assertTrue(viewModel.state.value.liveUpdatesDelayed)
        assertEquals(0, repository.boardRefreshCount)
        viewModel.setVisible(false)
    }

    @Test
    fun liveReconciliationDoesNotOverwriteLocalSearchChangedWhileRequestIsInFlight() = runTest(mainDispatcherRule.dispatcher) {
        val stream = MutableSharedFlow<KanbanStreamFrame>(extraBufferCapacity = 4)
        val pending = CompletableDeferred<KanbanBoardSnapshot>()
        val repository = LiveFakeDataSource(streamFactory = { _, _ -> stream }).apply {
            boardSnapshotLoader = { pending.await() }
        }
        val viewModel = KanbanLabViewModel(
            repository,
            KanbanLiveTiming(coalescingDelayMillis = 0),
        )
        runCurrent()
        viewModel.setVisible(true)
        viewModel.setLifecycleActive(true)
        runCurrent()
        stream.emit(KanbanStreamFrame.Events(listOf(event(11)), cursor = 11, frameId = 11))
        runCurrent()

        viewModel.setSearchQuery("CARD-1")
        pending.complete(snapshot("refreshed", latestEventId = 11))
        runCurrent()

        assertEquals("CARD-1", viewModel.state.value.searchQuery)
        assertEquals("refreshed", viewModel.state.value.visibleCards.single().title)
        viewModel.setVisible(false)
    }

    private class LiveFakeDataSource(
        var streamFactory: (String, Int) -> Flow<KanbanStreamFrame> = { _, _ -> flow { awaitCancellation() } },
    ) : KanbanBrowseDataSource {
        var handshakeFailure: Throwable? = null
        var handshakeCount = 0
        var boardRefreshCount = 0
        var eventsEnvelope = KanbanEventsEnvelope(events = emptyList(), cursor = 10)
        var boardSnapshotLoader: suspend () -> KanbanBoardSnapshot = {
            snapshot("refresh-${boardRefreshCount}", latestEventId = 12)
        }
        val streamRequests = mutableListOf<Pair<String, Int>>()
        val eventPollRequests = mutableListOf<Pair<String, Int>>()

        override suspend fun compatibilityHandshake(): KanbanCompatibilityReport {
            handshakeCount += 1
            handshakeFailure?.let { throw it }
            val board = KanbanBoardSummary(slug = "main", name = "Main", readOnly = false)
            val other = KanbanBoardSummary(slug = "other", name = "Other", readOnly = false)
            return KanbanCompatibilityReport(
                configuration = KanbanConfiguration(
                    columns = listOf("triage", "todo", "blocked", "ready", "running", "done"),
                    readOnly = false,
                ),
                boards = listOf(board, other),
                currentBoard = board,
                snapshot = snapshot("initial", latestEventId = 10),
                warnings = emptyList(),
            )
        }

        override suspend fun boardSnapshot(board: String, filters: KanbanBrowseFilters): KanbanBoardSnapshot {
            boardRefreshCount += 1
            return boardSnapshotLoader()
        }

        override suspend fun stats(board: String): KanbanStats = KanbanStats(total = 1)
        override suspend fun assignees(board: String): KanbanAssigneeHistory = KanbanAssigneeHistory()

        override suspend fun events(board: String, since: Int, limit: Int): KanbanEventsEnvelope {
            eventPollRequests += board to since
            return eventsEnvelope
        }

        override fun eventStream(board: String, since: Int): Flow<KanbanStreamFrame> {
            streamRequests += board to since
            return streamFactory(board, since)
        }
    }

    private companion object {
        fun event(id: Int) = KanbanEvent(eventId = id, cardId = "CARD-1", kind = "updated")

        fun snapshot(title: String, latestEventId: Int) = KanbanBoardSnapshot(
            columns = listOf(
                KanbanColumn(
                    name = "triage",
                    cards = listOf(KanbanCardSummary(cardId = "CARD-1", title = title, status = "triage")),
                ),
            ),
            changed = true,
            latestEventId = latestEventId,
            readOnly = false,
        )
    }
}
