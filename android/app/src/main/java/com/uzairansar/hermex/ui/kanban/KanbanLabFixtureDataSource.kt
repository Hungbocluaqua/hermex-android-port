package com.uzairansar.hermex.ui.kanban

import com.uzairansar.hermex.core.model.KanbanAssigneeHistory
import com.uzairansar.hermex.core.model.KanbanAssigneeValue
import com.uzairansar.hermex.core.model.KanbanBoardSnapshot
import com.uzairansar.hermex.core.model.KanbanBoardSummary
import com.uzairansar.hermex.core.model.KanbanCardSummary
import com.uzairansar.hermex.core.model.KanbanCardDetailEnvelope
import com.uzairansar.hermex.core.model.KanbanComment
import com.uzairansar.hermex.core.model.KanbanDetailEvent
import com.uzairansar.hermex.core.model.KanbanDetailEventPayload
import com.uzairansar.hermex.core.model.KanbanDependencyLinks
import com.uzairansar.hermex.core.model.KanbanDispatchRun
import com.uzairansar.hermex.core.model.KanbanWorkerLog
import com.uzairansar.hermex.core.model.KanbanAddCommentResponse
import com.uzairansar.hermex.core.model.KanbanCardMutationEnvelope
import com.uzairansar.hermex.core.model.KanbanCreateCardRequestBody
import com.uzairansar.hermex.core.model.KanbanEditCardRequestBody
import com.uzairansar.hermex.core.model.KanbanColumn
import com.uzairansar.hermex.core.model.KanbanCompatibilityReport
import com.uzairansar.hermex.core.model.KanbanCompatibilityWarning
import com.uzairansar.hermex.core.model.KanbanConfiguration
import com.uzairansar.hermex.core.model.KanbanContractViolation
import com.uzairansar.hermex.core.model.KanbanEventsEnvelope
import com.uzairansar.hermex.core.model.KanbanLinkCounts
import com.uzairansar.hermex.core.model.KanbanStats
import com.uzairansar.hermex.core.network.ApiError
import com.uzairansar.hermex.core.network.KanbanStreamFrame
import com.uzairansar.hermex.data.repository.KanbanBrowseDataSource
import com.uzairansar.hermex.data.repository.KanbanBrowseFilters
import java.io.IOException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

internal class KanbanLabFixtureDataSource(
    private val scenario: String,
) : KanbanBrowseDataSource {
    private val mutatedCards = linkedMapOf<String, KanbanCardSummary>()
    private val boards = listOf(
        KanbanBoardSummary(slug = "default", name = "Default Board", total = 5, readOnly = scenario == "read-only"),
        KanbanBoardSummary(slug = "release", name = "Release Board", total = 1, readOnly = scenario == "read-only"),
    )

    override suspend fun compatibilityHandshake(): KanbanCompatibilityReport {
        if (scenario == "incompatible") throw KanbanContractViolation.MissingConfigurationColumns
        val configuration = KanbanConfiguration(
            columns = kanbanLiveStatuses,
            assignees = listOf(KanbanAssigneeValue("builder"), KanbanAssigneeValue("reviewer")),
            readOnly = scenario == "read-only",
        )
        val snapshot = snapshot("default", KanbanBrowseFilters())
        return KanbanCompatibilityReport(
            configuration = configuration,
            boards = boards,
            currentBoard = boards.first(),
            snapshot = snapshot,
            warnings = buildList {
                if (scenario == "read-only") add(KanbanCompatibilityWarning.ReadOnly)
                if (scenario != "empty") add(KanbanCompatibilityWarning.UnsupportedStatus("future"))
            },
        )
    }

    override suspend fun boardSnapshot(board: String, filters: KanbanBrowseFilters): KanbanBoardSnapshot =
        snapshot(board, filters)

    override suspend fun stats(board: String): KanbanStats {
        val cards = snapshot(board, KanbanBrowseFilters(includeArchived = true)).allCards()
        return KanbanStats(
            total = cards.size,
            byStatus = cards.groupingBy { it.status.orEmpty() }.eachCount(),
        )
    }

    override suspend fun assignees(board: String): KanbanAssigneeHistory =
        KanbanAssigneeHistory(listOf(KanbanAssigneeValue("builder"), KanbanAssigneeValue("reviewer")))

    override suspend fun events(board: String, since: Int, limit: Int): KanbanEventsEnvelope {
        if (scenario == "offline") throw ApiError.Network(IOException("Fixture is offline."))
        return KanbanEventsEnvelope(events = emptyList(), cursor = maxOf(since, 42), latestEventId = 42)
    }

    override fun eventStream(board: String, since: Int): Flow<KanbanStreamFrame> = flow {
        when (scenario) {
            "offline", "delayed" -> throw IOException("Fixture stream is unavailable.")
            else -> awaitCancellation()
        }
    }

    override suspend fun cardDetail(cardId: String, board: String): KanbanCardDetailEnvelope {
        val card = snapshot(board, KanbanBrowseFilters(includeArchived = true)).allCards()
            .firstOrNull { it.cardId == cardId }
            ?: throw ApiError.Http(404, null)
        return KanbanCardDetailEnvelope(
            card = card.copy(
                createdAt = "2026-08-09T08:00:00Z",
                updatedAt = "2026-08-09T09:00:00Z",
                workspaceKind = "worktree",
                workspacePath = "/fixture/worktree",
                skills = listOf("android", "review"),
                maxRuntimeSeconds = 3_600,
                currentRunId = "run-fixture",
                claimLock = "claim-fixture",
                claimExpires = "2026-08-09T10:00:00Z",
                workerId = "worker-fixture",
            ),
            comments = listOf(
                KanbanComment("1", cardId, "builder", "Initial fixture comment", "2026-08-09T08:10:00Z"),
            ),
            events = listOf(
                KanbanDetailEvent(
                    eventId = "41",
                    cardId = cardId,
                    runId = "run-fixture",
                    kind = "status_changed",
                    createdAt = "2026-08-09T08:20:00Z",
                    payload = KanbanDetailEventPayload(status = card.status, summary = "Fixture status updated"),
                ),
            ),
            links = KanbanDependencyLinks(prerequisites = listOf("CARD-0"), dependents = listOf("CARD-9")),
            runs = listOf(
                KanbanDispatchRun(
                    runId = "run-fixture",
                    status = "completed",
                    outcome = "success",
                    summary = "Fixture dispatch completed",
                    startedAt = "2026-08-09T08:30:00Z",
                    finishedAt = "2026-08-09T08:45:00Z",
                    workerId = "worker-fixture",
                ),
            ),
            readOnly = scenario == "read-only",
        )
    }

    override suspend fun workerLog(cardId: String, board: String, tailBytes: Int): KanbanWorkerLog =
        KanbanWorkerLog(
            cardId = cardId,
            exists = true,
            sizeBytes = 36,
            content = "Fixture worker log\nCompleted safely.",
            truncated = false,
        )

    override suspend fun addComment(cardId: String, board: String, body: String): KanbanAddCommentResponse =
        KanbanAddCommentResponse(ok = true, commentId = "fixture-new", readOnly = scenario == "read-only")

    override suspend fun createCard(board: String, body: KanbanCreateCardRequestBody): KanbanCardMutationEnvelope {
        val cardId = "CARD-NEW-${mutatedCards.size + 1}"
        val created = KanbanCardSummary(
            cardId = cardId,
            title = body.title,
            body = body.body,
            status = body.status,
            priority = body.priority ?: 0,
            assignee = body.assignee,
            tenant = body.tenant,
            workspaceKind = body.workspaceKind,
            workspacePath = body.workspacePath,
            skills = body.skills,
            maxRuntimeSeconds = body.maxRuntimeSeconds,
            ageSeconds = 0.0,
        )
        mutatedCards["$board:$cardId"] = created
        return KanbanCardMutationEnvelope(card = created, readOnly = scenario == "read-only")
    }

    override suspend fun editCard(
        cardId: String,
        board: String,
        body: KanbanEditCardRequestBody,
    ): KanbanCardMutationEnvelope {
        val existing = snapshot(board, KanbanBrowseFilters(includeArchived = true)).allCards()
            .firstOrNull { it.cardId == cardId }
            ?: throw ApiError.Http(404, null)
        val edited = existing.copy(
            title = body.title,
            body = body.body,
            tenant = body.tenant,
            priority = body.priority,
            assignee = body.assignee,
            status = body.status ?: existing.status,
        )
        mutatedCards["$board:$cardId"] = edited
        return KanbanCardMutationEnvelope(card = edited, readOnly = scenario == "read-only")
    }

    private fun snapshot(board: String, filters: KanbanBrowseFilters): KanbanBoardSnapshot {
        val baseCards = when {
            scenario == "empty" -> emptyList()
            board == "release" -> listOf(
                card("CARD-REL", "Prepare release evidence", "todo", "reviewer", 1, 1_800.0),
            )
            else -> listOf(
                card("CARD-1", "Triage Android parity", "triage", "builder", 2, 120.0),
                card("CARD-2", "Implement Status Focus", "ready", "reviewer", 0, 3_600.0),
                card("CARD-3", "Investigate blocked contract", "blocked", null, -1, 90_000.0),
                card("CARD-4", "Monitor active worker", "running", "builder", 1, 3_900.0),
                card("CARD-5", "Future server workflow", "future", "reviewer", 3, 400.0),
            )
        }
        val cards = (baseCards.associateBy { it.cardId } + mutatedCards
            .filterKeys { it.startsWith("$board:") }
            .values
            .associateBy { it.cardId })
            .values
            .filter { card ->
            (filters.profile == null || card.assignee == filters.profile) &&
                (filters.tenant == null || card.tenant == filters.tenant) &&
                (!filters.onlyMine || card.assignee == "reviewer") &&
                (filters.includeArchived || card.status != "archived")
        }
        val statuses = (kanbanLiveStatuses + cards.mapNotNull { it.status }).distinct()
        return KanbanBoardSnapshot(
            columns = statuses.map { status -> KanbanColumn(status, cards.filter { it.status == status }) },
            tenants = listOf("app", "infra"),
            assignees = listOf("builder", "reviewer"),
            changed = true,
            latestEventId = 42,
            readOnly = scenario == "read-only",
        )
    }

    private fun card(
        id: String,
        title: String,
        status: String,
        profile: String?,
        priority: Int,
        age: Double,
    ) = KanbanCardSummary(
        cardId = id,
        title = title,
        status = status,
        assignee = profile,
        body = "## Fixture\n- Native Android Board browsing\n- Read-only Status Focus",
        tenant = if (id == "CARD-3") "infra" else "app",
        priority = priority,
        commentCount = if (id == "CARD-2") 4 else 1,
        linkCounts = KanbanLinkCounts(parents = if (id == "CARD-2") 1 else 0, children = if (id == "CARD-2") 2 else 0),
        ageSeconds = age,
    )
}

internal val supportedKanbanLabScenarios = setOf(
    "dense",
    "empty",
    "incompatible",
    "read-only",
    "offline",
    "delayed",
)
