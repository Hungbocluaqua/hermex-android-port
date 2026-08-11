package com.uzairansar.hermex.ui.kanban

import com.uzairansar.hermex.MainDispatcherRule
import com.uzairansar.hermex.core.model.KanbanAssigneeHistory
import com.uzairansar.hermex.core.model.KanbanBoardSnapshot
import com.uzairansar.hermex.core.model.KanbanCardDetailEnvelope
import com.uzairansar.hermex.core.model.KanbanCardMutationEnvelope
import com.uzairansar.hermex.core.model.KanbanCardSummary
import com.uzairansar.hermex.core.model.KanbanColumn
import com.uzairansar.hermex.core.model.KanbanCreateCardRequestBody
import com.uzairansar.hermex.core.model.KanbanDependencyLinks
import com.uzairansar.hermex.core.model.KanbanEditCardRequestBody
import com.uzairansar.hermex.core.model.KanbanStats
import com.uzairansar.hermex.data.repository.KanbanBrowseDataSource
import com.uzairansar.hermex.data.repository.KanbanBrowseFilters
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KanbanCardEditorViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun createStartsWithSafeDefaultsAndValidatesRequiredFields() = runTest {
        val model = createModel(FakeEditorDataSource())

        assertEquals(KanbanCardEditorAvailability.Ready, model.state.value.availability)
        assertEquals("triage", model.state.value.draft.status)
        assertEquals("scratch", model.state.value.draft.workspaceKind)

        model.requestSave()

        assertEquals(
            KanbanCardEditorSubmission.ValidationFailed(KanbanCardEditorField.Title),
            model.state.value.submission,
        )
    }

    @Test
    fun readyUnassignedCreateRequiresExplicitConfirmation() = runTest {
        val repository = FakeEditorDataSource()
        val model = createModel(repository)
        model.updateDraft { it.copy(title = "Ready", status = "ready") }

        model.requestSave()

        assertTrue(model.state.value.readyUnassignedConfirmation)
        assertEquals(0, repository.createCalls)

        model.confirmReadyAndSave()

        assertEquals(1, repository.createCalls)
        assertTrue(model.state.value.submission is KanbanCardEditorSubmission.Succeeded)
    }

    @Test
    fun createNormalizesOptionalFieldsAndKeepsStableIdempotencyKeyAcrossRetry() = runTest {
        val repository = FakeEditorDataSource()
        repository.createLoader = { throw IOException("write disconnected") }
        repository.snapshotLoader = { snapshot(emptyList()) }
        val model = createModel(repository)
        model.updateDraft {
            it.copy(
                title = "  New Card  ",
                body = "  Markdown body  ",
                priorityText = "2",
                assignee = " builder ",
                tenant = " app ",
                workspaceKind = "worktree",
                workspacePath = " /workspace/card ",
                skillsText = " android, review ",
                maximumRuntimeText = "3600",
                prerequisiteId = " CARD-P ",
            )
        }

        model.requestSave()

        assertEquals(KanbanCardEditorSubmission.Failed, model.state.value.submission)
        val first = repository.createBodies.single()
        assertEquals("New Card", first.title)
        assertEquals("  Markdown body  ", first.body)
        assertEquals(listOf("android", "review"), first.skills)
        assertEquals(listOf("CARD-P"), first.parents)

        model.retry()

        assertEquals(2, repository.createBodies.size)
        assertEquals(first.idempotencyKey, repository.createBodies.last().idempotencyKey)
    }

    @Test
    fun ambiguousCreateCompletesOnlyWhenExactlyOneNewCanonicalCardMatches() = runTest {
        val repository = FakeEditorDataSource()
        repository.createLoader = { throw IOException("timeout") }
        val canonical = card("CARD-NEW", "Canonical")
        repository.snapshotLoader = { snapshot(listOf(canonical)) }
        val model = createModel(repository)
        model.updateDraft { it.copy(title = "Canonical") }

        model.requestSave()

        assertEquals(KanbanCardEditorSubmission.Succeeded("CARD-NEW"), model.state.value.submission)
    }

    @Test
    fun ambiguousCreateWithMultipleMatchesIsUncertainAndCannotRetryBlindly() = runTest {
        val repository = FakeEditorDataSource()
        repository.createLoader = { throw IOException("timeout") }
        repository.snapshotLoader = {
            snapshot(listOf(card("CARD-A", "Duplicate"), card("CARD-B", "Duplicate")))
        }
        val model = createModel(repository)
        model.updateDraft { it.copy(title = "Duplicate") }

        model.requestSave()

        assertEquals(KanbanCardEditorSubmission.OutcomeUncertain, model.state.value.submission)
        assertFalse(model.state.value.canSubmit)
        model.retry()
        assertEquals(1, repository.createCalls)
    }

    @Test
    fun readOnlyMutationResponseClosesTheEditorWriteGate() = runTest {
        val repository = FakeEditorDataSource()
        repository.createLoader = { body ->
            KanbanCardMutationEnvelope(card = cardFromCreate("CARD-NEW", body), readOnly = true)
        }
        val model = createModel(repository)
        model.updateDraft { it.copy(title = "Rejected") }

        model.requestSave()

        assertEquals(KanbanCardEditorSubmission.Failed, model.state.value.submission)
        assertTrue(model.state.value.capabilityUnavailable)
        assertFalse(model.state.value.canSubmit)
    }

    @Test
    fun editPreflightDetectsRemoteConflictBeforeAnyWrite() = runTest {
        val repository = FakeEditorDataSource()
        repository.detailLoader = { call ->
            if (call == 1) detail(card("CARD-1", "Original")) else detail(card("CARD-1", "Server changed"))
        }
        val model = editModel(repository)
        model.updateDraft { it.copy(title = "Local draft") }

        model.requestSave()

        assertEquals(KanbanCardEditorSubmission.Conflict, model.state.value.submission)
        assertEquals("Local draft", model.state.value.draft.title)
        assertEquals("Server changed", model.state.value.remoteCard?.title)
        assertEquals(0, repository.editCalls)
    }

    @Test
    fun conflictCanReloadServerOrExplicitlyOverwritePreservedDraft() = runTest {
        val repository = FakeEditorDataSource()
        repository.detailLoader = { call ->
            if (call == 1) detail(card("CARD-1", "Original")) else detail(card("CARD-1", "Server changed"))
        }
        val reloadModel = editModel(repository)
        reloadModel.updateDraft { it.copy(title = "Local draft") }
        reloadModel.requestSave()

        reloadModel.reloadServerVersion()

        assertEquals("Server changed", reloadModel.state.value.draft.title)
        assertEquals(KanbanCardEditorSubmission.Idle, reloadModel.state.value.submission)

        val overwriteRepository = FakeEditorDataSource()
        overwriteRepository.detailLoader = { call ->
            if (call == 1) detail(card("CARD-1", "Original")) else detail(card("CARD-1", "Server changed"))
        }
        val overwriteModel = editModel(overwriteRepository)
        overwriteModel.updateDraft { it.copy(title = "Local draft") }
        overwriteModel.requestSave()

        overwriteModel.reviewAndOverwrite()

        assertEquals(1, overwriteRepository.editCalls)
        assertEquals("Local draft", overwriteRepository.editBodies.single().title)
        assertEquals(KanbanCardEditorSubmission.Succeeded("CARD-1"), overwriteModel.state.value.submission)
    }

    @Test
    fun failedEditWriteReconcilesCanonicalResultBeforeSuccess() = runTest {
        val repository = FakeEditorDataSource()
        repository.detailLoader = { call ->
            when (call) {
                1, 2 -> detail(card("CARD-1", "Original"))
                else -> detail(card("CARD-1", "Edited"))
            }
        }
        repository.editLoader = { _, _ -> throw IOException("timeout") }
        val model = editModel(repository)
        model.updateDraft { it.copy(title = "Edited") }

        model.requestSave()

        assertEquals(KanbanCardEditorSubmission.Succeeded("CARD-1"), model.state.value.submission)
        assertEquals(3, repository.detailCalls)
    }

    @Test
    fun editPreflightReadFailureIsDefinitiveForTheAttemptAndNeverWrites() = runTest {
        val repository = FakeEditorDataSource()
        val model = editModel(repository)
        repository.detailLoader = { throw IOException("offline before write") }
        model.updateDraft { it.copy(title = "Edited") }

        model.requestSave()

        assertEquals(KanbanCardEditorSubmission.Failed, model.state.value.submission)
        assertEquals(0, repository.editCalls)
    }

    private fun createModel(repository: FakeEditorDataSource) = KanbanCardEditorViewModel(
        repository = repository,
        board = "main",
        mode = KanbanCardEditorMode.Create,
        profileOptions = listOf("builder"),
        tenantOptions = listOf("app"),
        prerequisiteOptions = listOf(card("CARD-P", "Parent")),
        baselineCards = listOf(card("CARD-OLD", "Old")),
        allowsMutation = true,
        idempotencyKey = "stable-idempotency-key",
    )

    private fun editModel(repository: FakeEditorDataSource) = KanbanCardEditorViewModel(
        repository = repository,
        board = "main",
        mode = KanbanCardEditorMode.Edit("CARD-1"),
        profileOptions = listOf("builder"),
        tenantOptions = listOf("app"),
        prerequisiteOptions = emptyList(),
        baselineCards = listOf(card("CARD-1", "Original")),
        allowsMutation = true,
    )

    private class FakeEditorDataSource : KanbanBrowseDataSource {
        var detailCalls = 0
        var createCalls = 0
        var editCalls = 0
        val createBodies = mutableListOf<KanbanCreateCardRequestBody>()
        val editBodies = mutableListOf<KanbanEditCardRequestBody>()
        var detailLoader: suspend (Int) -> KanbanCardDetailEnvelope = { detail(card("CARD-1", "Original")) }
        var snapshotLoader: suspend () -> KanbanBoardSnapshot = { snapshot(emptyList()) }
        var createLoader: suspend (KanbanCreateCardRequestBody) -> KanbanCardMutationEnvelope = { body ->
            KanbanCardMutationEnvelope(card = cardFromCreate("CARD-NEW", body), readOnly = false)
        }
        var editLoader: suspend (String, KanbanEditCardRequestBody) -> KanbanCardMutationEnvelope = { id, body ->
            KanbanCardMutationEnvelope(card = cardFromEdit(id, body), readOnly = false)
        }

        override suspend fun compatibilityHandshake() = error("unused")
        override suspend fun boardSnapshot(board: String, filters: KanbanBrowseFilters): KanbanBoardSnapshot =
            snapshotLoader()
        override suspend fun stats(board: String): KanbanStats = error("unused")
        override suspend fun assignees(board: String): KanbanAssigneeHistory = error("unused")
        override suspend fun cardDetail(cardId: String, board: String): KanbanCardDetailEnvelope {
            detailCalls += 1
            return detailLoader(detailCalls)
        }
        override suspend fun createCard(board: String, body: KanbanCreateCardRequestBody): KanbanCardMutationEnvelope {
            createCalls += 1
            createBodies += body
            return createLoader(body)
        }
        override suspend fun editCard(
            cardId: String,
            board: String,
            body: KanbanEditCardRequestBody,
        ): KanbanCardMutationEnvelope {
            editCalls += 1
            editBodies += body
            return editLoader(cardId, body)
        }
    }

    private companion object {
        fun card(id: String, title: String) = KanbanCardSummary(
            cardId = id,
            title = title,
            body = null,
            status = "triage",
            priority = 0,
            workspaceKind = "scratch",
        )
        fun detail(card: KanbanCardSummary) = KanbanCardDetailEnvelope(
            card = card,
            links = KanbanDependencyLinks(prerequisites = emptyList()),
            readOnly = false,
        )
        fun snapshot(cards: List<KanbanCardSummary>) = KanbanBoardSnapshot(
            columns = listOf(KanbanColumn(name = "triage", cards = cards)),
            changed = true,
            readOnly = false,
        )
        fun cardFromCreate(id: String, body: KanbanCreateCardRequestBody) = KanbanCardSummary(
            cardId = id,
            title = body.title,
            body = body.body,
            status = body.status,
            priority = body.priority,
            assignee = body.assignee,
            tenant = body.tenant,
            workspaceKind = body.workspaceKind,
            workspacePath = body.workspacePath,
            skills = body.skills,
            maxRuntimeSeconds = body.maxRuntimeSeconds,
        )
        fun cardFromEdit(id: String, body: KanbanEditCardRequestBody) = KanbanCardSummary(
            cardId = id,
            title = body.title,
            body = body.body,
            status = body.status ?: "triage",
            priority = body.priority,
            assignee = body.assignee,
            tenant = body.tenant,
            workspaceKind = "scratch",
        )
    }
}
