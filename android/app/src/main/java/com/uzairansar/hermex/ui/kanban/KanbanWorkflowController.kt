package com.uzairansar.hermex.ui.kanban

import com.uzairansar.hermex.core.model.KanbanBoardSnapshot
import com.uzairansar.hermex.core.model.KanbanCardSummary
import com.uzairansar.hermex.core.model.KanbanDependencyRequestBody
import com.uzairansar.hermex.core.network.ApiError
import com.uzairansar.hermex.data.repository.KanbanBrowseDataSource
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal sealed interface KanbanCardWorkflowAction {
    data class Move(val status: String) : KanbanCardWorkflowAction
    data object Block : KanbanCardWorkflowAction
    data object Unblock : KanbanCardWorkflowAction
    data object Complete : KanbanCardWorkflowAction
    data object Archive : KanbanCardWorkflowAction
}

internal sealed interface KanbanCardMutationKind {
    data class Status(val status: String) : KanbanCardMutationKind
    data class Block(val reason: String?) : KanbanCardMutationKind
    data object Unblock : KanbanCardMutationKind
    data class AddPrerequisite(val prerequisiteId: String) : KanbanCardMutationKind
    data class RemovePrerequisite(val prerequisiteId: String) : KanbanCardMutationKind
    data class Archive(val previousStatus: String) : KanbanCardMutationKind
    data class UndoArchive(val status: String) : KanbanCardMutationKind
}

internal enum class KanbanCardMutationPhase {
    Updating,
    CheckingResult,
    Succeeded,
    Failed,
    OutcomeUncertain,
}

internal data class KanbanCardMutationState(
    val kind: KanbanCardMutationKind,
    val phase: KanbanCardMutationPhase,
)

internal data class KanbanPendingDependencyChange(
    val prerequisiteId: String,
    val isAdding: Boolean,
)

internal data class KanbanArchiveUndo(
    val cardId: String,
    val cardTitle: String,
    val previousStatus: String,
    val expiresAtMillis: Long?,
    val card: KanbanCardSummary,
)

internal data class KanbanWorkflowUiState(
    val mutations: Map<String, KanbanCardMutationState> = emptyMap(),
    val activeCardIds: Set<String> = emptySet(),
    val pendingDependencies: Map<String, KanbanPendingDependencyChange> = emptyMap(),
    val settledDetailStatuses: Map<String, String> = emptyMap(),
    val archiveUndo: KanbanArchiveUndo? = null,
)

internal class KanbanWorkflowController(
    private val repository: KanbanBrowseDataSource,
    private val scope: CoroutineScope,
    private val selectedBoard: () -> String?,
    private val configuredColumns: () -> List<String>,
    private val includesArchived: () -> Boolean,
    private val canMutate: (KanbanCardSummary) -> Boolean,
    private val cardInSnapshot: (String) -> KanbanCardSummary?,
    private val replaceCard: (KanbanCardSummary) -> Unit,
    private val removeCard: (String) -> Unit,
    private val onStatusSucceeded: (String, String) -> Unit,
    private val onDetailRefresh: () -> Unit,
    private val onCapabilityUnavailable: () -> Unit,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val archiveUndoLifetimeMillis: Long = 8_000,
) {
    private val mutableState = MutableStateFlow(KanbanWorkflowUiState())
    val state: StateFlow<KanbanWorkflowUiState> = mutableState

    private val activeMutationIds = mutableMapOf<String, String>()
    private val pendingOptimisticStatuses = mutableMapOf<String, String>()
    private val uncertainProtectedCards = mutableMapOf<String, KanbanCardSummary>()
    private val pendingUndoRecovery = mutableMapOf<String, KanbanArchiveUndo>()
    private var undoExpiryJob: Job? = null

    fun canMutateCard(card: KanbanCardSummary): Boolean =
        card.cardId.normalized() != null && card.hasSupportedStatus && canMutate(card)

    fun isMutatingCard(cardId: String?): Boolean = cardId.normalized() in activeMutationIds

    fun moveDestinations(card: KanbanCardSummary, configuredColumns: List<String>): List<String> {
        if (!canMutateCard(card)) return emptyList()
        val ordinary = setOf("triage", "todo", "ready")
        return configuredColumns.map(String::trim).filter { it in ordinary && it != card.status }
    }

    fun mutationState(cardId: String?): KanbanCardMutationState? =
        cardId.normalized()?.let(mutableState.value.mutations::get)

    fun displayedCard(card: KanbanCardSummary): KanbanCardSummary {
        val cardId = card.cardId.normalized() ?: return card
        val status = pendingOptimisticStatuses[cardId]
            ?: mutableState.value.settledDetailStatuses[cardId]
            ?: return card
        return card.copy(status = status)
    }

    fun displayedPrerequisites(cardId: String, canonical: List<String>): List<String> {
        val change = mutableState.value.pendingDependencies[cardId] ?: return canonical
        val values = canonical.filterNot { it == change.prerequisiteId }.toMutableList()
        if (change.isAdding) values += change.prerequisiteId
        return values.distinct().sorted()
    }

    fun protectSnapshot(snapshot: KanbanBoardSnapshot): KanbanBoardSnapshot {
        var protected = snapshot
        (activeMutationIds.keys + uncertainProtectedCards.keys).distinct().forEach { cardId ->
            val card = uncertainProtectedCards[cardId] ?: cardInSnapshot(cardId) ?: return@forEach
            val optimisticStatus = pendingOptimisticStatuses[cardId]
            protected = protected.replacingKanbanCard(
                optimisticStatus?.let { card.copy(status = it) } ?: card,
                includeArchived = includesArchived(),
            )
        }
        return protected
    }

    fun acknowledgeCanonicalBoardLoad(boardChanged: Boolean) {
        if (boardChanged) {
            reset()
            return
        }
        val succeeded = mutableState.value.mutations.filterValues { it.phase == KanbanCardMutationPhase.Succeeded }.keys
        if (succeeded.isEmpty()) return
        updateState { current ->
            current.copy(
                mutations = current.mutations - succeeded,
                settledDetailStatuses = current.settledDetailStatuses - succeeded,
                pendingDependencies = current.pendingDependencies - succeeded,
            )
        }
    }

    fun acknowledgeLoadedCardDetail(cardId: String) {
        if (activeMutationIds.containsKey(cardId)) return
        val mutation = mutableState.value.mutations[cardId] ?: return
        if (mutation.phase != KanbanCardMutationPhase.Succeeded) return
        updateState { current ->
            current.copy(
                settledDetailStatuses = current.settledDetailStatuses - cardId,
                pendingDependencies = current.pendingDependencies - cardId,
            )
        }
    }

    fun moveCard(card: KanbanCardSummary, status: String, confirmingRunningExit: Boolean = false) {
        if (status == "running" || status !in moveDestinations(card, currentColumns())) return
        startStatusMutation(card, status, KanbanCardMutationKind.Status(status), confirmingRunningExit)
    }

    fun completeCard(card: KanbanCardSummary, confirmingRunningExit: Boolean = false) {
        if (card.status in setOf("done", "archived")) return
        startStatusMutation(card, "done", KanbanCardMutationKind.Status("done"), confirmingRunningExit)
    }

    fun archiveCard(card: KanbanCardSummary, confirmingRunningExit: Boolean = false) {
        val previous = card.status.normalized()?.takeUnless { it == "archived" } ?: return
        startStatusMutation(card, "archived", KanbanCardMutationKind.Archive(previous), confirmingRunningExit)
    }

    fun blockCard(card: KanbanCardSummary, reason: String? = null, confirmingRunningExit: Boolean = false) {
        if (card.status in setOf("blocked", "archived")) return
        val normalizedReason = reason.normalized()
        startStatusMutation(
            card,
            "blocked",
            KanbanCardMutationKind.Block(normalizedReason),
            confirmingRunningExit,
        ) { cardId, board -> repository.blockCard(cardId, board, normalizedReason) }
    }

    fun unblockCard(card: KanbanCardSummary) {
        if (card.status != "blocked") return
        startStatusMutation(card, "ready", KanbanCardMutationKind.Unblock) { cardId, board ->
            repository.unblockCard(cardId, board)
        }
    }

    fun addPrerequisite(prerequisiteId: String, card: KanbanCardSummary) {
        startDependencyMutation(prerequisiteId, card, isAdding = true)
    }

    fun removePrerequisite(prerequisiteId: String, card: KanbanCardSummary) {
        startDependencyMutation(prerequisiteId, card, isAdding = false)
    }

    fun retryMutation(card: KanbanCardSummary, confirmingRunningExit: Boolean = false) {
        val mutation = mutationState(card.cardId) ?: return
        if (mutation.phase != KanbanCardMutationPhase.Failed) return
        when (val kind = mutation.kind) {
            is KanbanCardMutationKind.Status -> startStatusMutation(card, kind.status, kind, confirmingRunningExit)
            is KanbanCardMutationKind.Block -> blockCard(card, kind.reason, confirmingRunningExit)
            KanbanCardMutationKind.Unblock -> unblockCard(card)
            is KanbanCardMutationKind.AddPrerequisite -> addPrerequisite(kind.prerequisiteId, card)
            is KanbanCardMutationKind.RemovePrerequisite -> removePrerequisite(kind.prerequisiteId, card)
            is KanbanCardMutationKind.Archive -> archiveCard(card, confirmingRunningExit)
            is KanbanCardMutationKind.UndoArchive -> startStatusMutation(card, kind.status, kind)
        }
    }

    fun checkUncertainMutation(card: KanbanCardSummary) {
        val cardId = card.cardId.normalized() ?: return
        val mutation = mutationState(cardId) ?: return
        val board = selectedBoard() ?: return
        if (mutation.phase != KanbanCardMutationPhase.OutcomeUncertain || activeMutationIds.containsKey(cardId)) return
        val checkId = UUID.randomUUID().toString()
        activeMutationIds[cardId] = checkId
        setMutation(cardId, mutation.kind, KanbanCardMutationPhase.CheckingResult)
        scope.launch {
            try {
                val detail = repository.cardDetail(cardId, board)
                if (activeMutationIds[cardId] != checkId) return@launch
                val authoritative = requireNotNull(detail.card)
                uncertainProtectedCards.remove(cardId)
                replaceCard(authoritative)
                val succeeded = when (val kind = mutation.kind) {
                    is KanbanCardMutationKind.Status -> authoritative.status == kind.status
                    is KanbanCardMutationKind.Block -> authoritative.status == "blocked"
                    KanbanCardMutationKind.Unblock -> authoritative.status == "ready"
                    is KanbanCardMutationKind.Archive -> authoritative.status == "archived"
                    is KanbanCardMutationKind.UndoArchive -> authoritative.status == kind.status
                    is KanbanCardMutationKind.AddPrerequisite -> kind.prerequisiteId in detail.links?.prerequisites.orEmpty()
                    is KanbanCardMutationKind.RemovePrerequisite -> kind.prerequisiteId !in detail.links?.prerequisites.orEmpty()
                }
                finishMutation(cardId, mutation.kind, if (succeeded) KanbanCardMutationPhase.Succeeded else KanbanCardMutationPhase.Failed)
                if (succeeded && mutation.kind is KanbanCardMutationKind.UndoArchive) clearArchiveUndo()
            } catch (error: CancellationException) {
                finishMutation(cardId, mutation.kind, KanbanCardMutationPhase.Failed)
                throw error
            } catch (error: Throwable) {
                if (isNotFound(error)) {
                    uncertainProtectedCards.remove(cardId)
                    removeCard(cardId)
                    if (mutation.kind is KanbanCardMutationKind.UndoArchive) clearArchiveUndo()
                    finishMutation(cardId, mutation.kind, KanbanCardMutationPhase.Failed)
                } else {
                    finishMutation(cardId, mutation.kind, KanbanCardMutationPhase.OutcomeUncertain)
                }
            }
        }
    }

    fun undoArchive() {
        val undo = mutableState.value.archiveUndo ?: return
        if (undo.expiresAtMillis != null && undo.expiresAtMillis <= nowMillis()) {
            clearArchiveUndo()
            return
        }
        val board = selectedBoard() ?: run {
            clearArchiveUndo()
            return
        }
        if (activeMutationIds.containsKey(undo.cardId)) return
        undoExpiryJob?.cancel()
        val preflightId = UUID.randomUUID().toString()
        activeMutationIds[undo.cardId] = preflightId
        setMutation(undo.cardId, KanbanCardMutationKind.UndoArchive(undo.previousStatus), KanbanCardMutationPhase.CheckingResult)
        scope.launch {
            try {
                val detail = repository.cardDetail(undo.cardId, board)
                if (activeMutationIds[undo.cardId] != preflightId) return@launch
                val card = requireNotNull(detail.card)
                activeMutationIds.remove(undo.cardId)
                publishActiveIds()
                if (card.status != "archived") {
                    clearArchiveUndo()
                    replaceCard(card)
                    setMutation(undo.cardId, KanbanCardMutationKind.UndoArchive(undo.previousStatus), KanbanCardMutationPhase.Failed)
                    onDetailRefresh()
                    return@launch
                }
                clearArchiveUndo()
                pendingUndoRecovery[undo.cardId] = undo.copy(card = card, expiresAtMillis = null)
                startStatusMutation(card, undo.previousStatus, KanbanCardMutationKind.UndoArchive(undo.previousStatus))
            } catch (error: CancellationException) {
                activeMutationIds.remove(undo.cardId)
                publishActiveIds()
                clearArchiveUndo()
                throw error
            } catch (error: Throwable) {
                activeMutationIds.remove(undo.cardId)
                publishActiveIds()
                if (isNotFound(error)) {
                    clearArchiveUndo()
                    uncertainProtectedCards.remove(undo.cardId)
                    removeCard(undo.cardId)
                    setMutation(undo.cardId, KanbanCardMutationKind.UndoArchive(undo.previousStatus), KanbanCardMutationPhase.Failed)
                } else {
                    offerArchiveUndo(undo.copy(expiresAtMillis = null))
                    setMutation(undo.cardId, KanbanCardMutationKind.UndoArchive(undo.previousStatus), KanbanCardMutationPhase.OutcomeUncertain)
                }
                onDetailRefresh()
            }
        }
    }

    fun reset() {
        undoExpiryJob?.cancel()
        activeMutationIds.clear()
        pendingOptimisticStatuses.clear()
        uncertainProtectedCards.clear()
        pendingUndoRecovery.clear()
        mutableState.value = KanbanWorkflowUiState()
    }

    private fun startStatusMutation(
        card: KanbanCardSummary,
        status: String,
        kind: KanbanCardMutationKind,
        confirmingRunningExit: Boolean = false,
        write: (suspend (String, String) -> com.uzairansar.hermex.core.model.KanbanCardMutationEnvelope)? = null,
    ) {
        val cardId = card.cardId.normalized() ?: return
        val board = selectedBoard() ?: return
        if (
            status == "running" ||
            (card.status == "running" && !confirmingRunningExit) ||
            !canMutateCard(card) ||
            activeMutationIds.containsKey(cardId)
        ) return

        val baseline = cardInSnapshot(cardId) ?: card
        val mutationId = UUID.randomUUID().toString()
        uncertainProtectedCards.remove(cardId)
        activeMutationIds[cardId] = mutationId
        pendingOptimisticStatuses[cardId] = status
        setMutation(cardId, kind, KanbanCardMutationPhase.Updating)
        replaceCard(baseline.copy(status = status))

        scope.launch {
            try {
                val response = write?.invoke(cardId, board) ?: repository.setCardStatus(cardId, board, status)
                if (activeMutationIds[cardId] != mutationId) return@launch
                if (response.readOnly == true) {
                    onCapabilityUnavailable()
                    restoreStatusMutation(cardId, baseline, kind, KanbanCardMutationPhase.Failed)
                    return@launch
                }
                val authoritative = requireNotNull(response.card)
                if (authoritative.status != status) error("Kanban mutation status mismatch")
                settleSuccessfulStatus(authoritative, baseline, kind, mutationId)
            } catch (error: CancellationException) {
                if (activeMutationIds[cardId] == mutationId) {
                    restoreStatusMutation(cardId, baseline, kind, KanbanCardMutationPhase.Failed)
                }
                throw error
            } catch (error: Throwable) {
                if (activeMutationIds[cardId] != mutationId) return@launch
                if (isMissingKanbanCapability(error)) onCapabilityUnavailable()
                if (isDefinitiveWriteFailure(error)) {
                    restoreStatusMutation(cardId, baseline, kind, KanbanCardMutationPhase.Failed)
                } else {
                    setMutation(cardId, kind, KanbanCardMutationPhase.CheckingResult)
                    reconcileStatusMutation(cardId, board, status, baseline, kind, mutationId)
                }
            }
        }
    }

    private suspend fun reconcileStatusMutation(
        cardId: String,
        board: String,
        expectedStatus: String,
        baseline: KanbanCardSummary,
        kind: KanbanCardMutationKind,
        mutationId: String,
    ) {
        try {
            val detail = repository.cardDetail(cardId, board)
            if (activeMutationIds[cardId] != mutationId) return
            val authoritative = requireNotNull(detail.card)
            if (authoritative.status == expectedStatus) {
                settleSuccessfulStatus(authoritative, baseline, kind, mutationId)
            } else {
                restoreStatusMutation(cardId, authoritative, kind, KanbanCardMutationPhase.Failed)
            }
        } catch (error: CancellationException) {
            if (activeMutationIds[cardId] == mutationId) {
                restoreStatusMutation(cardId, baseline, kind, KanbanCardMutationPhase.Failed)
            }
            throw error
        } catch (error: Throwable) {
            if (activeMutationIds[cardId] != mutationId) return
            if (isNotFound(error)) {
                pendingOptimisticStatuses.remove(cardId)
                uncertainProtectedCards.remove(cardId)
                removeCard(cardId)
                finishMutation(cardId, kind, KanbanCardMutationPhase.Failed)
            } else {
                uncertainProtectedCards[cardId] = baseline
                restoreStatusMutation(cardId, baseline, kind, KanbanCardMutationPhase.OutcomeUncertain)
            }
        }
    }

    private fun settleSuccessfulStatus(
        authoritative: KanbanCardSummary,
        baseline: KanbanCardSummary,
        kind: KanbanCardMutationKind,
        mutationId: String,
    ) {
        val cardId = authoritative.cardId.normalized() ?: return
        if (activeMutationIds[cardId] != mutationId) return
        val status = authoritative.status.normalized() ?: return
        pendingOptimisticStatuses.remove(cardId)
        uncertainProtectedCards.remove(cardId)
        replaceCard(authoritative)
        updateState { current -> current.copy(settledDetailStatuses = current.settledDetailStatuses + (cardId to status)) }
        finishMutation(cardId, kind, KanbanCardMutationPhase.Succeeded)
        when (kind) {
            is KanbanCardMutationKind.Archive -> offerArchiveUndo(
                KanbanArchiveUndo(
                    cardId = cardId,
                    cardTitle = baseline.title.normalized() ?: cardId,
                    previousStatus = kind.previousStatus,
                    expiresAtMillis = nowMillis() + archiveUndoLifetimeMillis,
                    card = authoritative,
                ),
            )
            is KanbanCardMutationKind.UndoArchive -> {
                pendingUndoRecovery.remove(cardId)
                clearArchiveUndo()
            }
            else -> onStatusSucceeded(cardId, status)
        }
    }

    private fun restoreStatusMutation(
        cardId: String,
        baseline: KanbanCardSummary,
        kind: KanbanCardMutationKind,
        phase: KanbanCardMutationPhase,
    ) {
        pendingOptimisticStatuses.remove(cardId)
        replaceCard(baseline)
        finishMutation(cardId, kind, phase)
        pendingUndoRecovery.remove(cardId)?.let { undo ->
            if (kind is KanbanCardMutationKind.UndoArchive && phase != KanbanCardMutationPhase.Succeeded) {
                offerArchiveUndo(undo.copy(expiresAtMillis = null))
            }
        }
    }

    private fun startDependencyMutation(prerequisiteId: String, card: KanbanCardSummary, isAdding: Boolean) {
        val cardId = card.cardId.normalized() ?: return
        val parentId = prerequisiteId.normalized() ?: return
        val board = selectedBoard() ?: return
        if (parentId == cardId || !canMutateCard(card) || activeMutationIds.containsKey(cardId)) return
        val mutationId = UUID.randomUUID().toString()
        val kind: KanbanCardMutationKind = if (isAdding) {
            KanbanCardMutationKind.AddPrerequisite(parentId)
        } else {
            KanbanCardMutationKind.RemovePrerequisite(parentId)
        }
        val body = KanbanDependencyRequestBody(parentId, cardId)
        activeMutationIds[cardId] = mutationId
        updateState { current ->
            current.copy(
                pendingDependencies = current.pendingDependencies +
                    (cardId to KanbanPendingDependencyChange(parentId, isAdding)),
            )
        }
        setMutation(cardId, kind, KanbanCardMutationPhase.Updating)
        scope.launch {
            try {
                val response = if (isAdding) repository.addDependency(board, body) else repository.removeDependency(board, body)
                if (activeMutationIds[cardId] != mutationId) return@launch
                if (response.readOnly == true) {
                    onCapabilityUnavailable()
                    clearPendingDependency(cardId)
                    finishMutation(cardId, kind, KanbanCardMutationPhase.Failed)
                    return@launch
                }
                setMutation(cardId, kind, KanbanCardMutationPhase.CheckingResult)
                reconcileDependencyMutation(body, isAdding, kind, mutationId)
            } catch (error: CancellationException) {
                clearPendingDependency(cardId)
                finishMutation(cardId, kind, KanbanCardMutationPhase.Failed)
                throw error
            } catch (error: Throwable) {
                if (activeMutationIds[cardId] != mutationId) return@launch
                if (isMissingKanbanCapability(error)) onCapabilityUnavailable()
                if (isDefinitiveWriteFailure(error)) {
                    clearPendingDependency(cardId)
                    finishMutation(cardId, kind, KanbanCardMutationPhase.Failed)
                } else {
                    setMutation(cardId, kind, KanbanCardMutationPhase.CheckingResult)
                    reconcileDependencyMutation(body, isAdding, kind, mutationId)
                }
            }
        }
    }

    private suspend fun reconcileDependencyMutation(
        body: KanbanDependencyRequestBody,
        shouldExist: Boolean,
        kind: KanbanCardMutationKind,
        mutationId: String,
    ) {
        val cardId = body.dependentId
        try {
            val board = selectedBoard() ?: return
            val detail = repository.cardDetail(cardId, board)
            if (activeMutationIds[cardId] != mutationId) return
            val exists = body.prerequisiteId in detail.links?.prerequisites.orEmpty()
            val succeeded = exists == shouldExist
            if (!succeeded) clearPendingDependency(cardId)
            finishMutation(cardId, kind, if (succeeded) KanbanCardMutationPhase.Succeeded else KanbanCardMutationPhase.Failed)
        } catch (error: CancellationException) {
            clearPendingDependency(cardId)
            finishMutation(cardId, kind, KanbanCardMutationPhase.Failed)
            throw error
        } catch (error: Throwable) {
            if (activeMutationIds[cardId] != mutationId) return
            clearPendingDependency(cardId)
            finishMutation(
                cardId,
                kind,
                if (isNotFound(error)) KanbanCardMutationPhase.Failed else KanbanCardMutationPhase.OutcomeUncertain,
            )
        }
    }

    private fun finishMutation(cardId: String, kind: KanbanCardMutationKind, phase: KanbanCardMutationPhase) {
        activeMutationIds.remove(cardId)
        setMutation(cardId, kind, phase)
        onDetailRefresh()
    }

    private fun setMutation(cardId: String, kind: KanbanCardMutationKind, phase: KanbanCardMutationPhase) {
        updateState { current ->
            current.copy(
                mutations = current.mutations + (cardId to KanbanCardMutationState(kind, phase)),
                activeCardIds = activeMutationIds.keys.toSet(),
            )
        }
    }

    private fun publishActiveIds() {
        updateState { it.copy(activeCardIds = activeMutationIds.keys.toSet()) }
    }

    private fun clearPendingDependency(cardId: String) {
        updateState { current -> current.copy(pendingDependencies = current.pendingDependencies - cardId) }
    }

    private fun offerArchiveUndo(undo: KanbanArchiveUndo) {
        undoExpiryJob?.cancel()
        updateState { it.copy(archiveUndo = undo) }
        val expiresAt = undo.expiresAtMillis ?: return
        undoExpiryJob = scope.launch {
            delay((expiresAt - nowMillis()).coerceAtLeast(0))
            if (mutableState.value.archiveUndo == undo) updateState { it.copy(archiveUndo = null) }
        }
    }

    private fun clearArchiveUndo() {
        undoExpiryJob?.cancel()
        undoExpiryJob = null
        updateState { it.copy(archiveUndo = null) }
    }

    private fun currentColumns(): List<String> = configuredColumns()

    private fun updateState(transform: (KanbanWorkflowUiState) -> KanbanWorkflowUiState) {
        mutableState.value = transform(mutableState.value)
    }
}

private fun isDefinitiveWriteFailure(error: Throwable): Boolean = when (error) {
    ApiError.Unauthorized,
    is ApiError.InsecureTransport,
    -> true
    is ApiError.Http -> error.statusCode in 400..499 && error.statusCode != 408
    else -> false
}

private fun isNotFound(error: Throwable): Boolean = error is ApiError.Http && error.statusCode == 404

private fun String?.normalized(): String? = this?.trim()?.takeIf(String::isNotEmpty)

internal fun KanbanBoardSnapshot.replacingKanbanCard(
    card: KanbanCardSummary,
    includeArchived: Boolean,
): KanbanBoardSnapshot {
    val cardId = card.cardId?.trim()?.takeIf(String::isNotEmpty) ?: return this
    val destination = card.status?.trim()?.takeIf(String::isNotEmpty) ?: return this
    val withoutCard = columns.orEmpty().map { column ->
        column.copy(cards = column.cards.orEmpty().filterNot { it.cardId?.trim() == cardId })
    }.toMutableList()
    if (destination == "archived" && !includeArchived) return copy(columns = withoutCard)
    val destinationIndex = withoutCard.indexOfFirst { it.name?.trim() == destination }
    if (destinationIndex >= 0) {
        val column = withoutCard[destinationIndex]
        withoutCard[destinationIndex] = column.copy(cards = column.cards.orEmpty() + card)
    } else {
        withoutCard += com.uzairansar.hermex.core.model.KanbanColumn(name = destination, cards = listOf(card))
    }
    return copy(columns = withoutCard)
}
