package com.uzairansar.hermex.ui.kanban

import com.uzairansar.hermex.core.model.KanbanBulkActionRequestBody
import com.uzairansar.hermex.core.model.KanbanCardSummary
import com.uzairansar.hermex.core.network.ApiError
import com.uzairansar.hermex.data.repository.KanbanBrowseDataSource
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal sealed interface KanbanBulkAction {
    data class ChangeStatus(val status: String) : KanbanBulkAction
    data class AssignProfile(val profile: String?) : KanbanBulkAction
    data class SetPriority(val priority: Int) : KanbanBulkAction
    data object ArchiveCards : KanbanBulkAction
}

internal enum class KanbanBulkActionPhase {
    Submitting,
    Reconciling,
}

internal enum class KanbanBulkMemberOutcome {
    Succeeded,
    Failed,
    OutcomeUncertain,
}

internal data class KanbanBulkMemberResult(
    val cardId: String,
    val cardTitle: String,
    val outcome: KanbanBulkMemberOutcome,
)

internal data class KanbanBulkActionSummary(
    val action: KanbanBulkAction,
    val members: List<KanbanBulkMemberResult>,
) {
    val succeededCount: Int get() = members.count { it.outcome == KanbanBulkMemberOutcome.Succeeded }
    val failedCount: Int get() = members.count { it.outcome == KanbanBulkMemberOutcome.Failed }
    val uncertainCount: Int get() = members.count { it.outcome == KanbanBulkMemberOutcome.OutcomeUncertain }
    val failedCardIds: Set<String>
        get() = members.filter { it.outcome == KanbanBulkMemberOutcome.Failed }.mapTo(mutableSetOf()) { it.cardId }
    val needsAttention: List<KanbanBulkMemberResult>
        get() = members.filter { it.outcome != KanbanBulkMemberOutcome.Succeeded }
}

internal enum class KanbanBulkActionsAvailability {
    Available,
    NoSelection,
    Offline,
    Incompatible,
    ReadOnly,
    Refreshing,
    BoardBusy,
    InvalidSelection,
    UnknownStatus,
}

internal data class KanbanBulkUiState(
    val isSelectingCards: Boolean = false,
    val selectedCardIds: Set<String> = emptySet(),
    val phase: KanbanBulkActionPhase? = null,
    val summary: KanbanBulkActionSummary? = null,
    val capabilityUnavailable: Boolean = false,
) {
    val selectedCardCount: Int get() = selectedCardIds.size
}

internal class KanbanBulkActionController(
    private val repository: KanbanBrowseDataSource,
    private val scope: CoroutineScope,
    private val selectedBoard: () -> String?,
    private val configuredColumns: () -> List<String>,
    private val profileOptions: () -> List<String>,
    private val isOffline: () -> Boolean,
    private val isRefreshing: () -> Boolean,
    private val baseAllowsMutation: () -> Boolean,
    private val contractCompatible: () -> Boolean,
    private val isReadOnly: () -> Boolean,
    private val hasOtherBoardActivity: () -> Boolean,
    private val cardInSnapshot: (String) -> KanbanCardSummary?,
    private val replaceCard: (KanbanCardSummary) -> Unit,
    private val refreshBoard: suspend () -> Unit,
) {
    private val mutableState = MutableStateFlow(KanbanBulkUiState())
    val state: StateFlow<KanbanBulkUiState> = mutableState

    private val selectedCardsById = mutableMapOf<String, KanbanCardSummary>()
    private var boardGeneration = 0
    private var activeSubmissionId: UUID? = null

    fun canEnterSelection(): Boolean =
        baseAllowsMutation() &&
            mutableState.value.phase == null &&
            !mutableState.value.capabilityUnavailable &&
            !hasOtherBoardActivity()

    fun availability(): KanbanBulkActionsAvailability = availabilityFor(mutableState.value.selectedCardIds)

    fun canSubmit(action: KanbanBulkAction): Boolean =
        availability() == KanbanBulkActionsAvailability.Available && validate(action)

    fun canRetryFailed(): Boolean {
        val summary = mutableState.value.summary ?: return false
        return summary.failedCardIds.isNotEmpty() &&
            availabilityFor(summary.failedCardIds) == KanbanBulkActionsAvailability.Available &&
            validate(summary.action)
    }

    fun canCheckUncertain(): Boolean =
        mutableState.value.summary?.uncertainCount?.let { it > 0 } == true &&
            mutableState.value.phase == null &&
            !hasOtherBoardActivity() &&
            !isOffline() &&
            !isRefreshing() &&
            selectedBoard() != null

    fun selectionContainsRunning(): Boolean = mutableState.value.selectedCardIds.any { cardId ->
        (selectedCardsById[cardId] ?: cardInSnapshot(cardId))?.status.normalized()?.lowercase() == "running"
    }

    fun beginSelection() {
        if (!canEnterSelection()) return
        updateState { it.copy(isSelectingCards = true, summary = null) }
    }

    fun toggleSelection(card: KanbanCardSummary) {
        val current = mutableState.value
        if (!current.isSelectingCards || current.phase != null) return
        val cardId = card.cardId.normalized() ?: return
        if (cardId in current.selectedCardIds) {
            selectedCardsById.remove(cardId)
            updateState { it.copy(selectedCardIds = it.selectedCardIds - cardId) }
        } else {
            selectedCardsById[cardId] = card
            updateState { it.copy(selectedCardIds = it.selectedCardIds + cardId) }
        }
    }

    fun clearSelection() {
        if (mutableState.value.phase != null) return
        resetSelection()
    }

    fun dismissSummary() = updateState { it.copy(summary = null) }

    fun acknowledgeSnapshot(cards: List<KanbanCardSummary>) {
        val canonical = cards.mapNotNull { card -> card.cardId.normalized()?.let { it to card } }.toMap()
        mutableState.value.selectedCardIds.forEach { cardId ->
            canonical[cardId]?.let { selectedCardsById[cardId] = it }
        }
    }

    fun resetForBoardChange() {
        boardGeneration += 1
        activeSubmissionId = null
        selectedCardsById.clear()
        mutableState.value = KanbanBulkUiState(capabilityUnavailable = mutableState.value.capabilityUnavailable)
    }

    fun acknowledgeFullReload(cards: List<KanbanCardSummary>, boardChanged: Boolean) {
        if (boardChanged) {
            resetForBoardChange()
        } else {
            acknowledgeSnapshot(cards)
        }
        updateState { it.copy(capabilityUnavailable = false) }
    }

    fun perform(
        action: KanbanBulkAction,
        confirmedRunningExit: Boolean = false,
        confirmedArchive: Boolean = false,
    ) {
        perform(action, mutableState.value.selectedCardIds, confirmedRunningExit, confirmedArchive)
    }

    fun retryFailed() {
        val summary = mutableState.value.summary ?: return
        if (!canRetryFailed()) return
        val failedIds = summary.failedCardIds
        updateState { it.copy(selectedCardIds = failedIds) }
        selectedCardsById.keys.retainAll(failedIds)
        perform(
            action = summary.action,
            cardIds = failedIds,
            confirmedRunningExit = true,
            confirmedArchive = true,
        )
    }

    fun checkUncertain() {
        val previousSummary = mutableState.value.summary ?: return
        if (!canCheckUncertain()) return
        val uncertainIds = previousSummary.members
            .filter { it.outcome == KanbanBulkMemberOutcome.OutcomeUncertain }
            .map { it.cardId }
        if (uncertainIds.isEmpty()) return
        val board = selectedBoard() ?: return
        val generation = boardGeneration
        val submissionId = UUID.randomUUID()
        activeSubmissionId = submissionId
        updateState { it.copy(phase = KanbanBulkActionPhase.Reconciling) }
        scope.launch {
            val detailResults = fetchDetails(uncertainIds, board)
            if (!isCurrent(board, generation, submissionId)) return@launch
            val refreshedMembers = previousSummary.members.map { member ->
                if (member.cardId !in uncertainIds) return@map member
                detailResults[member.cardId]?.fold(
                    onSuccess = { authoritative ->
                        replaceCard(authoritative)
                        selectedCardsById[member.cardId] = authoritative
                        bulkMember(
                            member.cardId,
                            authoritative,
                            if (previousSummary.action.matches(authoritative)) {
                                KanbanBulkMemberOutcome.Succeeded
                            } else {
                                KanbanBulkMemberOutcome.Failed
                            },
                        )
                    },
                    onFailure = { member },
                ) ?: member
            }
            if (!isCurrent(board, generation, submissionId)) return@launch
            val summary = KanbanBulkActionSummary(previousSummary.action, refreshedMembers)
            val retainedIds = summary.needsAttention.mapTo(mutableSetOf()) { it.cardId }
            selectedCardsById.keys.retainAll(retainedIds)
            activeSubmissionId = null
            updateState { it.copy(selectedCardIds = retainedIds, phase = null, summary = summary) }
            refreshBoard()
        }
    }

    private fun perform(
        action: KanbanBulkAction,
        cardIds: Set<String>,
        confirmedRunningExit: Boolean,
        confirmedArchive: Boolean,
    ) {
        if (
            availabilityFor(cardIds) != KanbanBulkActionsAvailability.Available ||
            !validate(action) ||
            cardIds.isEmpty() ||
            cardIds != mutableState.value.selectedCardIds ||
            (action is KanbanBulkAction.ArchiveCards && !confirmedArchive) ||
            (leavesRunning(action, cardIds) && !confirmedRunningExit)
        ) return
        val board = selectedBoard() ?: return
        val orderedIds = cardIds.sorted()
        val originalCards = orderedIds.mapNotNull { cardId ->
            (selectedCardsById[cardId] ?: cardInSnapshot(cardId))?.let { cardId to it }
        }.toMap()
        if (originalCards.size != orderedIds.size) return

        val generation = boardGeneration
        val submissionId = UUID.randomUUID()
        activeSubmissionId = submissionId
        updateState { it.copy(phase = KanbanBulkActionPhase.Submitting, summary = null) }
        scope.launch {
            try {
                val response = repository.performBulkAction(board, action.requestBody(orderedIds))
                if (!isCurrent(board, generation, submissionId)) return@launch
                if (response.readOnly == true) updateState { it.copy(capabilityUnavailable = true) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (!isCurrent(board, generation, submissionId)) return@launch
                if (isMissingKanbanCapability(error)) updateState { it.copy(capabilityUnavailable = true) }
            }

            if (!isCurrent(board, generation, submissionId)) return@launch
            updateState { it.copy(phase = KanbanBulkActionPhase.Reconciling) }
            val detailResults = fetchDetails(orderedIds, board)
            if (!isCurrent(board, generation, submissionId)) return@launch

            val members = orderedIds.map { cardId ->
                val original = originalCards[cardId]
                detailResults[cardId]?.fold(
                    onSuccess = { authoritative ->
                        replaceCard(authoritative)
                        selectedCardsById[cardId] = authoritative
                        bulkMember(
                            cardId,
                            authoritative,
                            if (action.matches(authoritative)) {
                                KanbanBulkMemberOutcome.Succeeded
                            } else {
                                KanbanBulkMemberOutcome.Failed
                            },
                        )
                    },
                    onFailure = {
                        bulkMember(cardId, original, KanbanBulkMemberOutcome.OutcomeUncertain)
                    },
                ) ?: bulkMember(cardId, original, KanbanBulkMemberOutcome.OutcomeUncertain)
            }
            if (!isCurrent(board, generation, submissionId)) return@launch

            val summary = KanbanBulkActionSummary(action, members)
            val retainedIds = summary.needsAttention.mapTo(mutableSetOf()) { it.cardId }
            selectedCardsById.keys.retainAll(retainedIds)
            activeSubmissionId = null
            updateState {
                it.copy(
                    selectedCardIds = retainedIds,
                    phase = null,
                    summary = summary,
                )
            }
            refreshBoard()
        }
    }

    private suspend fun fetchDetails(
        cardIds: List<String>,
        board: String,
    ): Map<String, Result<KanbanCardSummary>> = coroutineScope {
        cardIds.chunked(BULK_RECONCILIATION_CONCURRENCY).flatMap { chunk ->
            chunk.map { cardId ->
                async {
                    val result = try {
                        Result.success(
                            repository.cardDetail(cardId, board).card
                                ?: error("The canonical Card was missing."),
                        )
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Throwable) {
                        Result.failure(error)
                    }
                    cardId to result
                }
            }.map { it.await() }
        }.toMap()
    }

    private fun availabilityFor(cardIds: Set<String>): KanbanBulkActionsAvailability {
        if (cardIds.isEmpty()) return KanbanBulkActionsAvailability.NoSelection
        if (mutableState.value.phase != null || hasOtherBoardActivity()) return KanbanBulkActionsAvailability.BoardBusy
        if (isOffline()) return KanbanBulkActionsAvailability.Offline
        if (isRefreshing()) return KanbanBulkActionsAvailability.Refreshing
        if (!contractCompatible() || mutableState.value.capabilityUnavailable) {
            return KanbanBulkActionsAvailability.Incompatible
        }
        if (isReadOnly()) return KanbanBulkActionsAvailability.ReadOnly
        if (!baseAllowsMutation()) return KanbanBulkActionsAvailability.Incompatible
        val selectedCards = cardIds.mapNotNull { selectedCardsById[it] ?: cardInSnapshot(it) }
        if (selectedCards.size != cardIds.size) return KanbanBulkActionsAvailability.InvalidSelection
        if (selectedCards.any { !it.hasSupportedStatus }) return KanbanBulkActionsAvailability.UnknownStatus
        return KanbanBulkActionsAvailability.Available
    }

    private fun validate(action: KanbanBulkAction): Boolean = when (action) {
        is KanbanBulkAction.ChangeStatus -> {
            val status = action.status.normalized()?.lowercase()
            status != null && status != "running" && configuredColumns().any { it.trim().lowercase() == status }
        }
        is KanbanBulkAction.AssignProfile -> {
            val profile = action.profile.normalized()
            profile == null && action.profile == null || profile != null && profile in profileOptions()
        }
        is KanbanBulkAction.SetPriority -> action.priority in -100..100
        KanbanBulkAction.ArchiveCards -> true
    }

    private fun leavesRunning(action: KanbanBulkAction, cardIds: Set<String>): Boolean =
        (action is KanbanBulkAction.ChangeStatus || action is KanbanBulkAction.ArchiveCards) &&
            cardIds.any { cardId ->
                (selectedCardsById[cardId] ?: cardInSnapshot(cardId))?.status.normalized()?.lowercase() == "running"
            }

    private fun isCurrent(board: String, generation: Int, submissionId: UUID): Boolean =
        selectedBoard() == board && boardGeneration == generation && activeSubmissionId == submissionId

    private fun resetSelection() {
        selectedCardsById.clear()
        updateState {
            it.copy(
                isSelectingCards = false,
                selectedCardIds = emptySet(),
                summary = null,
            )
        }
    }

    private fun updateState(transform: (KanbanBulkUiState) -> KanbanBulkUiState) {
        mutableState.value = transform(mutableState.value)
    }

    private fun bulkMember(
        cardId: String,
        card: KanbanCardSummary?,
        outcome: KanbanBulkMemberOutcome,
    ): KanbanBulkMemberResult = KanbanBulkMemberResult(
        cardId = cardId,
        cardTitle = card?.title.normalized() ?: cardId,
        outcome = outcome,
    )

    private companion object {
        const val BULK_RECONCILIATION_CONCURRENCY = 4
    }
}

private fun KanbanBulkAction.requestBody(cardIds: List<String>): KanbanBulkActionRequestBody = when (this) {
    is KanbanBulkAction.ChangeStatus -> KanbanBulkActionRequestBody(ids = cardIds, status = status.trim().lowercase())
    is KanbanBulkAction.AssignProfile -> KanbanBulkActionRequestBody(ids = cardIds, assignee = profile?.trim() ?: "")
    is KanbanBulkAction.SetPriority -> KanbanBulkActionRequestBody(ids = cardIds, priority = priority)
    KanbanBulkAction.ArchiveCards -> KanbanBulkActionRequestBody(ids = cardIds, archive = true)
}

private fun KanbanBulkAction.matches(card: KanbanCardSummary): Boolean = when (this) {
    is KanbanBulkAction.ChangeStatus -> card.status.normalized()?.lowercase() == status.normalized()?.lowercase()
    is KanbanBulkAction.AssignProfile -> card.assignee.normalized() == profile.normalized()
    is KanbanBulkAction.SetPriority -> (card.priority ?: 0) == priority
    KanbanBulkAction.ArchiveCards -> card.status.normalized()?.lowercase() == "archived"
}

private fun String?.normalized(): String? = this?.trim()?.takeIf(String::isNotEmpty)
