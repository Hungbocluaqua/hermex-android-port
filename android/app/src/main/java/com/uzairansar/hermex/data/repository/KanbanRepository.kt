package com.uzairansar.hermex.data.repository

import com.uzairansar.hermex.core.model.KanbanCompatibilityReport
import com.uzairansar.hermex.core.model.KanbanCompatibilityWarning
import com.uzairansar.hermex.core.model.KanbanContractViolation
import com.uzairansar.hermex.core.model.KanbanEventsEnvelope
import com.uzairansar.hermex.core.model.KanbanBoardSnapshot
import com.uzairansar.hermex.core.model.KanbanCardDetailEnvelope
import com.uzairansar.hermex.core.model.KanbanWorkerLog
import com.uzairansar.hermex.core.model.KanbanAddCommentResponse
import com.uzairansar.hermex.core.model.KanbanCardMutationEnvelope
import com.uzairansar.hermex.core.model.KanbanCreateCardRequestBody
import com.uzairansar.hermex.core.model.KanbanEditCardRequestBody
import com.uzairansar.hermex.core.model.KanbanStats
import com.uzairansar.hermex.core.model.KanbanAssigneeHistory
import com.uzairansar.hermex.core.network.HermesApiClient
import com.uzairansar.hermex.core.network.KanbanEventStreamingClient
import com.uzairansar.hermex.core.network.KanbanStreamFrame
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.HttpUrl

data class KanbanBrowseFilters(
    val profile: String? = null,
    val tenant: String? = null,
    val includeArchived: Boolean = false,
    val onlyMine: Boolean = false,
)

interface KanbanBrowseDataSource {
    suspend fun compatibilityHandshake(): KanbanCompatibilityReport
    suspend fun boardSnapshot(board: String, filters: KanbanBrowseFilters): KanbanBoardSnapshot
    suspend fun stats(board: String): KanbanStats
    suspend fun assignees(board: String): KanbanAssigneeHistory
    suspend fun events(board: String, since: Int, limit: Int = 200): KanbanEventsEnvelope =
        KanbanEventsEnvelope(events = emptyList(), cursor = since)

    fun eventStream(board: String, since: Int): Flow<KanbanStreamFrame> = flow {
        throw IOException("Kanban live updates are unavailable.")
    }

    suspend fun cardDetail(cardId: String, board: String): KanbanCardDetailEnvelope =
        throw UnsupportedOperationException("Kanban Card detail is unavailable.")

    suspend fun workerLog(cardId: String, board: String, tailBytes: Int = 65_536): KanbanWorkerLog =
        throw UnsupportedOperationException("Kanban worker log is unavailable.")

    suspend fun addComment(cardId: String, board: String, body: String): KanbanAddCommentResponse =
        throw UnsupportedOperationException("Kanban comments are unavailable.")

    suspend fun createCard(board: String, body: KanbanCreateCardRequestBody): KanbanCardMutationEnvelope =
        throw UnsupportedOperationException("Kanban Card creation is unavailable.")

    suspend fun editCard(cardId: String, board: String, body: KanbanEditCardRequestBody): KanbanCardMutationEnvelope =
        throw UnsupportedOperationException("Kanban Card editing is unavailable.")
}

class KanbanRepository(
    private val client: HermesApiClient,
    private val streamingClient: KanbanEventStreamingClient? = null,
) : KanbanBrowseDataSource {
    override suspend fun compatibilityHandshake(): KanbanCompatibilityReport {
        val configuration = client.kanbanConfiguration()
        val configuredColumns = configuration.columns.orEmpty().map(String::trim).filter(String::isNotEmpty)
        if (configuredColumns.isEmpty()) throw KanbanContractViolation.MissingConfigurationColumns

        val boardsResponse = client.kanbanBoards()
        val boards = boardsResponse.boards.orEmpty()
        if (boards.any { it.slug.isNullOrBlank() }) throw KanbanContractViolation.MissingBoardIdentity
        val currentSlug = boardsResponse.current?.trim()?.takeIf(String::isNotEmpty)
            ?: boards.firstOrNull { it.isCurrent == true }?.slug?.trim()?.takeIf(String::isNotEmpty)
            ?: throw KanbanContractViolation.MissingCurrentBoard
        val currentBoard = boards.firstOrNull { it.slug?.trim() == currentSlug }
            ?: throw KanbanContractViolation.MissingCurrentBoard

        val snapshot = client.kanbanBoard(currentSlug)
        validateSnapshot(snapshot)

        val warnings = compatibilityWarnings(
            configurationReadOnly = configuration.readOnly,
            boardsReadOnly = boardsResponse.readOnly,
            boardReadOnly = currentBoard.readOnly,
            snapshot = snapshot,
            configuredColumns = configuredColumns,
        )
        return KanbanCompatibilityReport(configuration, boards, currentBoard, snapshot, warnings)
    }

    override suspend fun boardSnapshot(board: String, filters: KanbanBrowseFilters): KanbanBoardSnapshot =
        client.kanbanBoard(
            board = board,
            tenant = filters.tenant,
            assignee = filters.profile,
            includeArchived = filters.includeArchived,
            onlyMine = filters.onlyMine,
        ).also(::validateSnapshot)

    override suspend fun stats(board: String): KanbanStats = client.kanbanStats(board)
    override suspend fun assignees(board: String): KanbanAssigneeHistory = client.kanbanAssignees(board)
    override suspend fun events(board: String, since: Int, limit: Int): KanbanEventsEnvelope =
        client.kanbanEvents(board, since, limit)
    override fun eventStream(board: String, since: Int): Flow<KanbanStreamFrame> =
        streamingClient?.stream(eventsStreamUrl(board, since)) ?: super.eventStream(board, since)

    fun eventsStreamUrl(board: String, since: Int): HttpUrl = client.kanbanEventsStreamUrl(board, since)

    override suspend fun cardDetail(cardId: String, board: String): KanbanCardDetailEnvelope =
        client.kanbanCardDetail(cardId, board).also { detail ->
            val card = detail.card ?: throw KanbanContractViolation.MissingCardIdentity
            if (card.cardId?.trim() != cardId.trim()) throw KanbanContractViolation.MissingCardIdentity
            if (card.status.isNullOrBlank()) throw KanbanContractViolation.MissingCardStatus
        }

    override suspend fun workerLog(cardId: String, board: String, tailBytes: Int): KanbanWorkerLog =
        client.kanbanWorkerLog(cardId, board, tailBytes).also { log ->
            if (log.cardId?.trim() != cardId.trim() || log.exists == null) {
                throw KanbanContractViolation.MissingCardIdentity
            }
        }

    override suspend fun addComment(cardId: String, board: String, body: String): KanbanAddCommentResponse =
        client.addKanbanComment(cardId, board, body)

    override suspend fun createCard(board: String, body: KanbanCreateCardRequestBody): KanbanCardMutationEnvelope =
        client.createKanbanCard(board, body).also { validateMutationCard(it, expectedCardId = null) }

    override suspend fun editCard(
        cardId: String,
        board: String,
        body: KanbanEditCardRequestBody,
    ): KanbanCardMutationEnvelope = client.editKanbanCard(cardId, board, body).also {
        validateMutationCard(it, expectedCardId = cardId)
    }

    private fun validateSnapshot(snapshot: KanbanBoardSnapshot) {
        val columns = snapshot.columns
        if (snapshot.changed != true || columns.isNullOrEmpty()) throw KanbanContractViolation.MissingBoardSnapshot
        if (columns.any { it.name.isNullOrBlank() }) throw KanbanContractViolation.MissingColumnStatus
        val cards = columns.flatMap { it.cards.orEmpty() }
        if (cards.any { it.cardId.isNullOrBlank() }) throw KanbanContractViolation.MissingCardIdentity
        if (cards.any { it.status.isNullOrBlank() }) throw KanbanContractViolation.MissingCardStatus
    }

    private fun validateMutationCard(envelope: KanbanCardMutationEnvelope, expectedCardId: String?) {
        val card = envelope.card ?: throw KanbanContractViolation.MissingCardIdentity
        val cardId = card.cardId?.trim()?.takeIf(String::isNotEmpty)
            ?: throw KanbanContractViolation.MissingCardIdentity
        if (expectedCardId != null && cardId != expectedCardId.trim()) {
            throw KanbanContractViolation.MissingCardIdentity
        }
        if (card.status.isNullOrBlank()) throw KanbanContractViolation.MissingCardStatus
    }

    private fun compatibilityWarnings(
        configurationReadOnly: Boolean?,
        boardsReadOnly: Boolean?,
        boardReadOnly: Boolean?,
        snapshot: KanbanBoardSnapshot,
        configuredColumns: List<String>,
    ): List<KanbanCompatibilityWarning> {
        val configuredStatuses = configuredColumns.map { it.lowercase() }.toSet()
        return buildList {
            if (configurationReadOnly == true || boardsReadOnly == true || boardReadOnly == true || snapshot.readOnly == true) {
                add(KanbanCompatibilityWarning.ReadOnly)
            }
            if (configurationReadOnly == null || boardsReadOnly == null || boardReadOnly == null || snapshot.readOnly == null) {
                add(KanbanCompatibilityWarning.WriteCapabilityUnavailable)
            }
            snapshot.columns.orEmpty().flatMap { column ->
                buildList {
                    column.name?.trim()?.lowercase()?.let(::add)
                    addAll(column.cards.orEmpty().mapNotNull { it.status?.trim()?.lowercase() })
                }
            }
                .filterNot(configuredStatuses::contains)
                .distinct()
                .forEach { add(KanbanCompatibilityWarning.UnsupportedStatus(it)) }
        }
    }
}
