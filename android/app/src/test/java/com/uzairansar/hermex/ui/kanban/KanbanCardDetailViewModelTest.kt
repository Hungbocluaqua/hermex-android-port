package com.uzairansar.hermex.ui.kanban

import com.uzairansar.hermex.MainDispatcherRule
import com.uzairansar.hermex.core.model.KanbanAddCommentResponse
import com.uzairansar.hermex.core.model.KanbanAssigneeHistory
import com.uzairansar.hermex.core.model.KanbanBoardSnapshot
import com.uzairansar.hermex.core.model.KanbanCardDetailEnvelope
import com.uzairansar.hermex.core.model.KanbanCardSummary
import com.uzairansar.hermex.core.model.KanbanComment
import com.uzairansar.hermex.core.model.KanbanWorkerLog
import com.uzairansar.hermex.data.repository.KanbanBrowseDataSource
import com.uzairansar.hermex.data.repository.KanbanBrowseFilters
import com.uzairansar.hermex.core.model.KanbanStats
import com.uzairansar.hermex.core.network.ApiError
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
class KanbanCardDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun initialLoadPublishesCanonicalDetailWithoutFetchingWorkerLog() = runTest {
        val repository = FakeDetailDataSource()

        val viewModel = model(repository)

        assertEquals(KanbanCardDetailAvailability.Content, viewModel.state.value.availability)
        assertEquals("Original", viewModel.state.value.detail?.card?.title)
        assertEquals(1, repository.detailCalls)
        assertEquals(0, repository.workerLogCalls)
    }

    @Test
    fun workerLogLoadsOnlyAfterExplicitRequestAndOfflineDisablesIt() = runTest {
        val repository = FakeDetailDataSource()
        val viewModel = model(repository)

        viewModel.updateParentState(isOffline = true, allowsWrites = true, refreshRevision = 0)
        viewModel.loadWorkerLog()

        assertEquals(0, repository.workerLogCalls)
        assertTrue(viewModel.state.value.isStale)

        val onlineRepository = FakeDetailDataSource()
        val online = model(onlineRepository)
        online.loadWorkerLog()

        assertEquals(1, onlineRepository.workerLogCalls)
        assertTrue(online.state.value.workerLog is KanbanWorkerLogState.Loaded)
    }

    @Test
    fun successfulCommentAppendsAuthoritativeIdentifierAndClearsDraft() = runTest {
        val repository = FakeDetailDataSource()
        repository.commentLoader = { _, _, _ -> KanbanAddCommentResponse(ok = true, commentId = "C-2", readOnly = false) }
        val viewModel = model(repository)

        viewModel.updateCommentDraft("  A comment  ")
        viewModel.submitComment()

        assertEquals(KanbanCommentSubmissionState.Succeeded, viewModel.state.value.commentSubmission)
        assertEquals("", viewModel.state.value.commentDraft)
        assertEquals("C-2", viewModel.state.value.detail?.comments?.last()?.commentId)
        assertEquals("A comment", viewModel.state.value.detail?.comments?.last()?.body)
    }

    @Test
    fun failedWriteReconcilesCanonicalCommentBeforeReportingSuccess() = runTest {
        val repository = FakeDetailDataSource()
        repository.commentLoader = { _, _, _ -> throw IOException("connection lost") }
        val canonical = detail(comments = listOf(KanbanComment(commentId = "C-9", body = "Canonical")))
        val reconciling = model(repository)
        repository.detailLoader = { _, _ -> canonical }

        reconciling.updateCommentDraft("Canonical")
        reconciling.submitComment()

        assertEquals(KanbanCommentSubmissionState.Succeeded, reconciling.state.value.commentSubmission)
        assertEquals("C-9", reconciling.state.value.detail?.comments?.single()?.commentId)
    }

    @Test
    fun failedWriteAllowsRetryOnlyAfterCanonicalAbsenceIsConfirmed() = runTest {
        val repository = FakeDetailDataSource()
        repository.commentLoader = { _, _, _ -> throw IOException("connection lost") }
        val viewModel = model(repository)

        viewModel.updateCommentDraft("Not stored")
        viewModel.submitComment()

        assertEquals(KanbanCommentSubmissionState.RetryAllowed, viewModel.state.value.commentSubmission)
        assertEquals("Not stored", viewModel.state.value.commentDraft)
        assertFalse(viewModel.state.value.isStale)
    }

    @Test
    fun failedWriteWithFailedReconciliationReportsUncertainAndNeverRetriesBlindly() = runTest {
        val repository = FakeDetailDataSource()
        val viewModel = model(repository)
        repository.commentLoader = { _, _, _ -> throw IOException("connection lost") }
        repository.detailLoader = { _, _ -> throw IOException("still offline") }

        viewModel.updateCommentDraft("Maybe stored")
        viewModel.submitComment()

        assertEquals(KanbanCommentSubmissionState.OutcomeUncertain, viewModel.state.value.commentSubmission)
        assertTrue(viewModel.state.value.isStale)
        assertEquals(1, repository.commentCalls)
        viewModel.retryComment()
        assertEquals(1, repository.commentCalls)
    }

    @Test
    fun readOnlyMutationResponseClosesWriteGate() = runTest {
        val repository = FakeDetailDataSource()
        repository.commentLoader = { _, _, _ -> KanbanAddCommentResponse(ok = false, readOnly = true) }
        val viewModel = model(repository)

        viewModel.updateCommentDraft("Blocked")
        viewModel.submitComment()

        assertEquals(KanbanCommentSubmissionState.Failed, viewModel.state.value.commentSubmission)
        assertFalse(viewModel.state.value.parentAllowsWrites)
        assertFalse(viewModel.state.value.canComment)
    }

    @Test
    fun missingCommentEndpointClosesOnlyCommentsUntilCanonicalReload() = runTest {
        val repository = FakeDetailDataSource()
        repository.commentLoader = { _, _, _ ->
            throw ApiError.Http(404, "{\"error\":\"Unknown Kanban endpoint; refresh the client\"}")
        }
        val viewModel = model(repository)

        viewModel.updateCommentDraft("Blocked")
        viewModel.submitComment()

        assertTrue(viewModel.state.value.commentCapabilityUnavailable)
        assertFalse(viewModel.state.value.canComment)
        assertTrue(viewModel.state.value.parentAllowsWrites)
        assertEquals(KanbanCommentSubmissionState.Failed, viewModel.state.value.commentSubmission)
        assertEquals(1, repository.commentCalls)
        assertEquals(1, repository.detailCalls)

        viewModel.load()
        assertFalse(viewModel.state.value.commentCapabilityUnavailable)
        assertTrue(viewModel.state.value.canComment)
    }

    @Test
    fun taskNotFoundDoesNotMisclassifyTheCommentsCapability() = runTest {
        val repository = FakeDetailDataSource()
        repository.commentLoader = { _, _, _ -> throw ApiError.Http(404, "{\"error\":\"task not found\"}") }
        val viewModel = model(repository)

        viewModel.updateCommentDraft("Maybe")
        viewModel.submitComment()

        assertFalse(viewModel.state.value.commentCapabilityUnavailable)
        assertEquals(2, repository.detailCalls)
    }

    @Test
    fun networkRefreshPreservesLoadedDetailAndMarksItStale() = runTest {
        val repository = FakeDetailDataSource()
        val viewModel = model(repository)
        repository.detailLoader = { _, _ -> throw IOException("offline") }

        viewModel.load()

        assertEquals(KanbanCardDetailAvailability.Content, viewModel.state.value.availability)
        assertEquals("Original", viewModel.state.value.detail?.card?.title)
        assertTrue(viewModel.state.value.isStale)
        assertFalse(viewModel.state.value.isRefreshing)
    }

    @Test
    fun olderDetailResponseCannotOverwriteNewerRefresh() = runTest {
        val repository = FakeDetailDataSource()
        val viewModel = model(repository)
        val slow = CompletableDeferred<KanbanCardDetailEnvelope>()
        repository.detailLoader = { _, _ -> slow.await() }
        viewModel.load()
        repository.detailLoader = { _, _ -> detail(title = "Newest") }

        viewModel.load()
        slow.complete(detail(title = "Old"))

        assertEquals("Newest", viewModel.state.value.detail?.card?.title)
    }

    private fun model(repository: FakeDetailDataSource) =
        KanbanCardDetailViewModel(repository, "main", "CARD-1", parentAllowsWrites = true)

    private class FakeDetailDataSource : KanbanBrowseDataSource {
        var detailCalls = 0
        var workerLogCalls = 0
        var commentCalls = 0
        var detailLoader: suspend (String, String) -> KanbanCardDetailEnvelope = { _, _ -> detail() }
        var workerLogLoader: suspend (String, String, Int) -> KanbanWorkerLog = { card, _, _ ->
            KanbanWorkerLog(cardId = card, exists = true, content = "tail", truncated = false)
        }
        var commentLoader: suspend (String, String, String) -> KanbanAddCommentResponse = { _, _, _ ->
            KanbanAddCommentResponse(ok = true, commentId = "C-new", readOnly = false)
        }

        override suspend fun compatibilityHandshake() = error("unused")
        override suspend fun boardSnapshot(board: String, filters: KanbanBrowseFilters): KanbanBoardSnapshot = error("unused")
        override suspend fun stats(board: String): KanbanStats = error("unused")
        override suspend fun assignees(board: String): KanbanAssigneeHistory = error("unused")
        override suspend fun cardDetail(cardId: String, board: String): KanbanCardDetailEnvelope {
            detailCalls += 1
            return detailLoader(cardId, board)
        }
        override suspend fun workerLog(cardId: String, board: String, tailBytes: Int): KanbanWorkerLog {
            workerLogCalls += 1
            return workerLogLoader(cardId, board, tailBytes)
        }
        override suspend fun addComment(cardId: String, board: String, body: String): KanbanAddCommentResponse {
            commentCalls += 1
            return commentLoader(cardId, board, body)
        }
    }

    private companion object {
        fun detail(
            title: String = "Original",
            comments: List<KanbanComment> = emptyList(),
        ) = KanbanCardDetailEnvelope(
            card = KanbanCardSummary(cardId = "CARD-1", title = title, status = "todo"),
            comments = comments,
            readOnly = false,
        )
    }
}
