package com.uzairansar.hermex.ui.kanban

import com.uzairansar.hermex.core.model.KanbanDispatchResult
import com.uzairansar.hermex.core.network.ApiError
import com.uzairansar.hermex.data.repository.KanbanBrowseDataSource
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal enum class KanbanDispatchMode { Preview, Run }
internal enum class KanbanDispatchPhase { Submitting, Reconciling, Succeeded, Refused, Failed, OutcomeUncertain, BoardUnavailable }
internal enum class KanbanDispatcherAvailability { Available, Busy, OutcomeUncertain, Offline, Incompatible, ReadOnly, Refreshing, RefreshFailed }

internal data class KanbanDispatchState(
    val mode: KanbanDispatchMode,
    val boardSlug: String,
    val phase: KanbanDispatchPhase,
    val result: KanbanDispatchResult? = null,
    val completedAtMillis: Long? = null,
    val boardActivityGeneration: Int,
    val canAcknowledgeUncertainOutcome: Boolean = false,
) {
    val isInFlight get() = phase == KanbanDispatchPhase.Submitting || phase == KanbanDispatchPhase.Reconciling
}

internal class KanbanDispatcherController(
    private val repository: KanbanBrowseDataSource,
    private val scope: CoroutineScope,
    private val selectedBoard: () -> String?,
    private val availability: () -> KanbanDispatcherAvailability,
    private val boardActivityGeneration: () -> Int,
    private val markBoardActivity: () -> Unit,
    private val applyBoards: suspend () -> Boolean,
    private val refreshBoard: suspend (String) -> Boolean,
    private val onOffline: () -> Unit = {},
) {
    private val mutableState = MutableStateFlow<KanbanDispatchState?>(null)
    val state: StateFlow<KanbanDispatchState?> = mutableState
    private var generation = 0
    private var activeId: UUID? = null
    var capabilityIncompatible = false
        private set

    fun preview() = perform(KanbanDispatchMode.Preview)
    fun run() = perform(KanbanDispatchMode.Run)

    fun isPreviewStale(): Boolean {
        val state = mutableState.value ?: return false
        return state.mode == KanbanDispatchMode.Preview && state.phase == KanbanDispatchPhase.Succeeded &&
            (state.boardSlug != selectedBoard() || state.boardActivityGeneration != boardActivityGeneration())
    }

    fun blocksBoardActions(): Boolean = mutableState.value?.isInFlight == true

    fun dismiss() {
        val state = mutableState.value ?: return
        if (state.isInFlight || (state.phase == KanbanDispatchPhase.OutcomeUncertain && !state.canAcknowledgeUncertainOutcome)) return
        mutableState.value = null
    }

    fun refreshUncertain() {
        val previous = mutableState.value ?: return
        if (previous.mode != KanbanDispatchMode.Run || previous.phase != KanbanDispatchPhase.OutcomeUncertain ||
            previous.boardSlug != selectedBoard() || availability() != KanbanDispatcherAvailability.OutcomeUncertain) return
        val id = UUID.randomUUID()
        val currentGeneration = ++generation
        activeId = id
        mutableState.value = previous.copy(
            phase = KanbanDispatchPhase.Reconciling,
            boardActivityGeneration = boardActivityGeneration(),
            canAcknowledgeUncertainOutcome = false,
        )
        scope.launch {
            reconcileRun(previous.boardSlug, previous.result, previous.completedAtMillis ?: System.currentTimeMillis(),
                requestOutcomeUncertain = previous.result == null, allowAcknowledgement = true, currentGeneration, id)
        }
    }

    fun acknowledgeFullReload() {
        generation += 1
        activeId = null
        mutableState.value = null
        capabilityIncompatible = false
    }

    private fun perform(mode: KanbanDispatchMode) {
        if (availability() != KanbanDispatcherAvailability.Available) return
        val board = selectedBoard() ?: return
        if (mode == KanbanDispatchMode.Run) markBoardActivity()
        val startingActivity = boardActivityGeneration()
        val id = UUID.randomUUID()
        val currentGeneration = ++generation
        activeId = id
        mutableState.value = KanbanDispatchState(mode, board, KanbanDispatchPhase.Submitting, boardActivityGeneration = startingActivity)
        scope.launch {
            try {
                val result = repository.dispatch(board, dryRun = mode == KanbanDispatchMode.Preview)
                if (!isCurrent(mode, board, currentGeneration, id)) return@launch
                val completed = System.currentTimeMillis()
                if (mode == KanbanDispatchMode.Preview) {
                    mutableState.value = KanbanDispatchState(mode, board, KanbanDispatchPhase.Succeeded, result, completed, startingActivity)
                    activeId = null
                } else {
                    mutableState.value = KanbanDispatchState(mode, board, KanbanDispatchPhase.Reconciling, result, completed, boardActivityGeneration())
                    reconcileRun(board, result, completed, false, false, currentGeneration, id)
                }
            } catch (error: CancellationException) {
                clearIfCurrent(currentGeneration, id)
                throw error
            } catch (error: Throwable) {
                if (!isCurrent(mode, board, currentGeneration, id)) return@launch
                if (error is ApiError.Network) onOffline()
                if (error is ApiError.Http && error.statusCode in setOf(404, 405)) capabilityIncompatible = true
                val completed = System.currentTimeMillis()
                if (isDefinitiveDispatchFailure(error)) {
                    mutableState.value = KanbanDispatchState(mode, board, KanbanDispatchPhase.Refused, completedAtMillis = completed, boardActivityGeneration = boardActivityGeneration())
                    activeId = null
                } else if (mode == KanbanDispatchMode.Preview) {
                    mutableState.value = KanbanDispatchState(mode, board, KanbanDispatchPhase.Failed, completedAtMillis = completed, boardActivityGeneration = startingActivity)
                    activeId = null
                } else {
                    mutableState.value = KanbanDispatchState(mode, board, KanbanDispatchPhase.Reconciling, completedAtMillis = completed, boardActivityGeneration = boardActivityGeneration())
                    reconcileRun(board, null, completed, true, false, currentGeneration, id)
                }
            }
        }
    }

    private suspend fun reconcileRun(
        board: String, result: KanbanDispatchResult?, completed: Long, requestOutcomeUncertain: Boolean,
        allowAcknowledgement: Boolean, currentGeneration: Int, id: UUID,
    ) {
        val collectionSucceeded = runCatching { applyBoards() }.getOrDefault(false)
        if (!isCurrent(KanbanDispatchMode.Run, board, currentGeneration, id)) return
        if (selectedBoard() != board) {
            mutableState.value = KanbanDispatchState(KanbanDispatchMode.Run, board, KanbanDispatchPhase.BoardUnavailable, result, completed, boardActivityGeneration())
            activeId = null
            return
        }
        val boardSucceeded = runCatching { refreshBoard(board) }.getOrDefault(false)
        if (!isCurrent(KanbanDispatchMode.Run, board, currentGeneration, id)) return
        val uncertain = requestOutcomeUncertain || !collectionSucceeded || !boardSucceeded
        mutableState.value = KanbanDispatchState(
            KanbanDispatchMode.Run, board, if (uncertain) KanbanDispatchPhase.OutcomeUncertain else KanbanDispatchPhase.Succeeded,
            result, completed, boardActivityGeneration(), requestOutcomeUncertain && collectionSucceeded && boardSucceeded && allowAcknowledgement,
        )
        activeId = null
    }

    private fun isCurrent(mode: KanbanDispatchMode, board: String, currentGeneration: Int, id: UUID) =
        generation == currentGeneration && activeId == id && mutableState.value?.mode == mode && mutableState.value?.boardSlug == board

    private fun clearIfCurrent(currentGeneration: Int, id: UUID) {
        if (generation == currentGeneration && activeId == id) { activeId = null; mutableState.value = null }
    }
}

private fun isDefinitiveDispatchFailure(error: Throwable): Boolean = when (error) {
    ApiError.Unauthorized, is ApiError.InsecureTransport -> true
    is ApiError.Http -> error.statusCode in 400..499 && error.statusCode != 408
    else -> false
}
