package com.uzairansar.hermex.ui.kanban

import com.uzairansar.hermex.core.model.KanbanBoardMutationEnvelope
import com.uzairansar.hermex.core.model.KanbanBoardsResponse
import com.uzairansar.hermex.core.model.KanbanCreateBoardRequest
import com.uzairansar.hermex.core.model.KanbanEditBoardRequest
import com.uzairansar.hermex.core.model.KanbanContractViolation
import com.uzairansar.hermex.core.network.ApiError
import com.uzairansar.hermex.data.repository.KanbanBrowseDataSource
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal sealed interface KanbanBoardMutationKind {
    val slug: String

    data class Create(val request: KanbanCreateBoardRequest) : KanbanBoardMutationKind {
        override val slug: String get() = request.slug
    }

    data class Edit(val request: KanbanEditBoardRequest) : KanbanBoardMutationKind {
        override val slug: String get() = request.slug
    }

    data class Archive(override val slug: String) : KanbanBoardMutationKind
    data class MakeActive(override val slug: String) : KanbanBoardMutationKind
}

internal data class KanbanBoardMutationState(
    val kind: KanbanBoardMutationKind,
    val phase: KanbanCardMutationPhase,
)

internal data class KanbanBoardManagementUiState(
    val mutation: KanbanBoardMutationState? = null,
    val capabilityUnavailable: Boolean = false,
) {
    val blocksWrites: Boolean
        get() = mutation?.phase in setOf(
            KanbanCardMutationPhase.Updating,
            KanbanCardMutationPhase.CheckingResult,
            KanbanCardMutationPhase.OutcomeUncertain,
        )
}

internal class KanbanBoardManagementController(
    private val repository: KanbanBrowseDataSource,
    private val scope: CoroutineScope,
    private val baseAllowsMutation: () -> Boolean,
    private val hasOtherBoardActivity: () -> Boolean,
    private val boardExists: (String) -> Boolean,
    private val applyBoardsResponse: (KanbanBoardsResponse) -> Unit,
) {
    private val mutableState = MutableStateFlow(KanbanBoardManagementUiState())
    val state: StateFlow<KanbanBoardManagementUiState> = mutableState
    private var generation = 0
    private var activeMutationId: UUID? = null
    private var intendedResult: ((KanbanBoardsResponse) -> Boolean)? = null

    fun canManageBoards(): Boolean =
        baseAllowsMutation() &&
            !hasOtherBoardActivity() &&
            !mutableState.value.capabilityUnavailable &&
            !mutableState.value.blocksWrites

    fun create(slug: String, name: String, description: String, icon: String, color: String) {
        val normalizedSlug = slug.normalized() ?: return
        val normalizedName = name.normalized() ?: return
        val request = KanbanCreateBoardRequest(
            slug = normalizedSlug,
            name = normalizedName,
            description = description.trim(),
            icon = icon.trim(),
            color = color.trim(),
        )
        perform(
            kind = KanbanBoardMutationKind.Create(request),
            write = { repository.createBoard(request) },
            intended = { response -> response.boards.orEmpty().any { it.slug.normalized() == normalizedSlug } },
        )
    }

    fun edit(slug: String, name: String, description: String, icon: String, color: String) {
        val normalizedSlug = slug.normalized() ?: return
        val normalizedName = name.normalized() ?: return
        if (!boardExists(normalizedSlug)) return
        val request = KanbanEditBoardRequest(
            slug = normalizedSlug,
            name = normalizedName,
            description = description.trim(),
            icon = icon.trim(),
            color = color.trim(),
        )
        perform(
            kind = KanbanBoardMutationKind.Edit(request),
            write = { repository.editBoard(request) },
            intended = { response ->
                response.boards.orEmpty().firstOrNull { it.slug.normalized() == normalizedSlug }?.let { board ->
                    board.name.normalized() == request.name &&
                        board.description.normalized() == request.description.normalized() &&
                        board.icon.normalized() == request.icon.normalized() &&
                        board.color.normalized() == request.color.normalized()
                } == true
            },
        )
    }

    fun archive(slug: String) {
        val normalizedSlug = slug.normalized() ?: return
        if (normalizedSlug == "default" || !boardExists(normalizedSlug)) return
        perform(
            kind = KanbanBoardMutationKind.Archive(normalizedSlug),
            write = { repository.archiveBoard(normalizedSlug) },
            intended = { response -> response.boards.orEmpty().none { it.slug.normalized() == normalizedSlug } },
        )
    }

    fun makeActive(slug: String) {
        val normalizedSlug = slug.normalized() ?: return
        if (!boardExists(normalizedSlug)) return
        perform(
            kind = KanbanBoardMutationKind.MakeActive(normalizedSlug),
            write = { repository.makeBoardActive(normalizedSlug) },
            intended = { response -> response.current.normalized() == normalizedSlug },
        )
    }

    fun checkResult() {
        val current = mutableState.value.mutation ?: return
        val intended = intendedResult ?: return
        if (current.phase != KanbanCardMutationPhase.OutcomeUncertain) return
        val mutationId = UUID.randomUUID()
        val currentGeneration = ++generation
        activeMutationId = mutationId
        setMutation(current.kind, KanbanCardMutationPhase.CheckingResult)
        scope.launch {
            val response = fetchCanonical(current.kind, currentGeneration, mutationId)
            if (!isCurrent(current.kind, currentGeneration, mutationId)) return@launch
            setMutation(
                current.kind,
                when {
                    response == null -> KanbanCardMutationPhase.OutcomeUncertain
                    intended(response) -> KanbanCardMutationPhase.Succeeded
                    else -> KanbanCardMutationPhase.Failed
                },
            )
            activeMutationId = null
        }
    }

    fun dismissResult() {
        val phase = mutableState.value.mutation?.phase ?: return
        if (phase in setOf(KanbanCardMutationPhase.Updating, KanbanCardMutationPhase.CheckingResult, KanbanCardMutationPhase.OutcomeUncertain)) return
        mutableState.value = mutableState.value.copy(mutation = null)
        intendedResult = null
    }

    fun acknowledgeFullReload() {
        generation += 1
        activeMutationId = null
        intendedResult = null
        mutableState.value = KanbanBoardManagementUiState()
    }

    private fun perform(
        kind: KanbanBoardMutationKind,
        write: suspend () -> KanbanBoardMutationEnvelope,
        intended: (KanbanBoardsResponse) -> Boolean,
    ) {
        if (!canManageBoards()) return
        val mutationId = UUID.randomUUID()
        val currentGeneration = ++generation
        activeMutationId = mutationId
        intendedResult = intended
        setMutation(kind, KanbanCardMutationPhase.Updating)
        scope.launch {
            var definitiveFailure = false
            try {
                val envelope = write()
                if (!isCurrent(kind, currentGeneration, mutationId)) return@launch
                if (envelope.readOnly == true) {
                    mutableState.value = mutableState.value.copy(capabilityUnavailable = true)
                    definitiveFailure = true
                }
            } catch (error: CancellationException) {
                clearIfCurrent(currentGeneration, mutationId)
                throw error
            } catch (error: Throwable) {
                if (!isCurrent(kind, currentGeneration, mutationId)) return@launch
                if (isMissingKanbanCapability(error)) {
                    mutableState.value = mutableState.value.copy(capabilityUnavailable = true)
                }
                definitiveFailure = isDefinitiveBoardWriteFailure(error)
            }

            if (!isCurrent(kind, currentGeneration, mutationId)) return@launch
            if (!definitiveFailure) setMutation(kind, KanbanCardMutationPhase.CheckingResult)
            val response = fetchCanonical(kind, currentGeneration, mutationId)
            if (!isCurrent(kind, currentGeneration, mutationId)) return@launch
            setMutation(
                kind,
                when {
                    definitiveFailure -> KanbanCardMutationPhase.Failed
                    response == null -> KanbanCardMutationPhase.OutcomeUncertain
                    intended(response) -> KanbanCardMutationPhase.Succeeded
                    else -> KanbanCardMutationPhase.Failed
                },
            )
            activeMutationId = null
        }
    }

    private suspend fun fetchCanonical(
        kind: KanbanBoardMutationKind,
        currentGeneration: Int,
        mutationId: UUID,
    ): KanbanBoardsResponse? = try {
        repository.boards().also { response ->
            val boards = response.boards ?: throw KanbanContractViolation.MissingBoardIdentity
            if (boards.any { it.slug.isNullOrBlank() }) throw KanbanContractViolation.MissingBoardIdentity
            val current = response.current.normalized() ?: throw KanbanContractViolation.MissingCurrentBoard
            if (boards.none { it.slug.normalized() == current }) throw KanbanContractViolation.MissingCurrentBoard
            if (isCurrent(kind, currentGeneration, mutationId)) applyBoardsResponse(response)
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        null
    }

    private fun isCurrent(kind: KanbanBoardMutationKind, currentGeneration: Int, mutationId: UUID): Boolean =
        generation == currentGeneration && activeMutationId == mutationId && mutableState.value.mutation?.kind == kind

    private fun clearIfCurrent(currentGeneration: Int, mutationId: UUID) {
        if (generation != currentGeneration || activeMutationId != mutationId) return
        activeMutationId = null
        intendedResult = null
        mutableState.value = mutableState.value.copy(mutation = null)
    }

    private fun setMutation(kind: KanbanBoardMutationKind, phase: KanbanCardMutationPhase) {
        mutableState.value = mutableState.value.copy(mutation = KanbanBoardMutationState(kind, phase))
    }
}

private fun isDefinitiveBoardWriteFailure(error: Throwable): Boolean = when (error) {
    ApiError.Unauthorized,
    is ApiError.InsecureTransport,
    -> true
    is ApiError.Http -> error.statusCode in 400..499 && error.statusCode != 408
    else -> false
}

private fun String?.normalized(): String? = this?.trim()?.takeIf(String::isNotEmpty)
