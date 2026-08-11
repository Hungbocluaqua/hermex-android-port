package com.uzairansar.hermex.ui.kanban

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uzairansar.hermex.core.model.KanbanCardDetailEnvelope
import com.uzairansar.hermex.core.model.KanbanCardSummary
import com.uzairansar.hermex.core.model.KanbanCreateCardRequestBody
import com.uzairansar.hermex.core.model.KanbanEditCardRequestBody
import com.uzairansar.hermex.core.network.ApiError
import com.uzairansar.hermex.data.repository.KanbanBrowseDataSource
import com.uzairansar.hermex.data.repository.KanbanBrowseFilters
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal sealed interface KanbanCardEditorMode {
    data object Create : KanbanCardEditorMode
    data class Edit(val cardId: String) : KanbanCardEditorMode
}

internal enum class KanbanCardEditorAvailability { Loading, Ready, Failed }

internal enum class KanbanCardEditorField {
    Title,
    Priority,
    Status,
    WorkspacePath,
    MaximumRuntime,
    Prerequisite,
}

internal sealed interface KanbanCardEditorSubmission {
    data object Idle : KanbanCardEditorSubmission
    data class ValidationFailed(val field: KanbanCardEditorField) : KanbanCardEditorSubmission
    data object Saving : KanbanCardEditorSubmission
    data object CheckingResult : KanbanCardEditorSubmission
    data object Conflict : KanbanCardEditorSubmission
    data class Succeeded(val cardId: String) : KanbanCardEditorSubmission
    data object Failed : KanbanCardEditorSubmission
    data object OutcomeUncertain : KanbanCardEditorSubmission
}

internal data class KanbanCardEditorDraft(
    val title: String = "",
    val body: String = "",
    val status: String = "triage",
    val priorityText: String = "0",
    val assignee: String? = null,
    val tenant: String = "",
    val workspaceKind: String = "scratch",
    val workspacePath: String = "",
    val skillsText: String = "",
    val maximumRuntimeText: String = "",
    val prerequisiteId: String = "",
)

internal data class KanbanCardEditorUiState(
    val availability: KanbanCardEditorAvailability = KanbanCardEditorAvailability.Loading,
    val draft: KanbanCardEditorDraft = KanbanCardEditorDraft(),
    val submission: KanbanCardEditorSubmission = KanbanCardEditorSubmission.Idle,
    val originalStatus: String? = null,
    val remoteCard: KanbanCardSummary? = null,
    val readyUnassignedConfirmation: Boolean = false,
    val allowsMutation: Boolean = false,
    val capabilityUnavailable: Boolean = false,
) {
    val isInFlight: Boolean
        get() = submission == KanbanCardEditorSubmission.Saving ||
            submission == KanbanCardEditorSubmission.CheckingResult
    val canSubmit: Boolean
        get() = availability == KanbanCardEditorAvailability.Ready &&
            allowsMutation &&
            !capabilityUnavailable &&
            !isInFlight &&
            submission != KanbanCardEditorSubmission.Conflict &&
            submission != KanbanCardEditorSubmission.OutcomeUncertain
}

internal class KanbanCardEditorViewModel(
    private val repository: KanbanBrowseDataSource,
    private val board: String,
    val mode: KanbanCardEditorMode,
    val profileOptions: List<String>,
    val tenantOptions: List<String>,
    val prerequisiteOptions: List<KanbanCardSummary>,
    baselineCards: List<KanbanCardSummary>,
    allowsMutation: Boolean,
    private val idempotencyKey: String = UUID.randomUUID().toString(),
) : ViewModel() {
    private val baselineMatchingCardIds = baselineCards.mapNotNull { it.cardId?.normalized() }.toSet()
    private val mutableState = MutableStateFlow(
        KanbanCardEditorUiState(
            availability = if (mode == KanbanCardEditorMode.Create) {
                KanbanCardEditorAvailability.Ready
            } else {
                KanbanCardEditorAvailability.Loading
            },
            allowsMutation = allowsMutation,
        ),
    )
    val state: StateFlow<KanbanCardEditorUiState> = mutableState

    private var baselineCard: KanbanCardSummary? = null
    private var remotePrerequisiteId: String? = null
    private var saveJob: Job? = null
    private var loadGeneration = 0

    init {
        if (mode is KanbanCardEditorMode.Edit) loadBaseline()
    }

    fun updateAllowsMutation(allowsMutation: Boolean) {
        mutableState.value = mutableState.value.copy(allowsMutation = allowsMutation)
    }

    fun updateDraft(transform: (KanbanCardEditorDraft) -> KanbanCardEditorDraft) {
        val current = mutableState.value
        if (current.isInFlight) return
        mutableState.value = current.copy(
            draft = transform(current.draft),
            submission = if (current.submission is KanbanCardEditorSubmission.ValidationFailed) {
                KanbanCardEditorSubmission.Idle
            } else {
                current.submission
            },
        )
    }

    fun requestSave() {
        val current = mutableState.value
        if (mode == KanbanCardEditorMode.Create &&
            current.draft.status == "ready" &&
            current.draft.assignee.normalized() == null
        ) {
            mutableState.value = current.copy(readyUnassignedConfirmation = true)
            return
        }
        save(overwriteConflict = false)
    }

    fun confirmReadyAndSave() {
        mutableState.value = mutableState.value.copy(readyUnassignedConfirmation = false)
        save(overwriteConflict = false, readyUnassignedConfirmed = true)
    }

    fun dismissReadyConfirmation() {
        mutableState.value = mutableState.value.copy(readyUnassignedConfirmation = false)
    }

    fun retry() {
        if (mutableState.value.submission == KanbanCardEditorSubmission.Failed) requestSave()
    }

    fun reviewAndOverwrite() {
        if (mutableState.value.submission == KanbanCardEditorSubmission.Conflict) {
            save(overwriteConflict = true, readyUnassignedConfirmed = true)
        }
    }

    fun reloadServerVersion() {
        val remote = mutableState.value.remoteCard ?: return
        baselineCard = remote
        mutableState.value = mutableState.value.copy(
            availability = KanbanCardEditorAvailability.Ready,
            draft = draftFrom(remote, remotePrerequisiteId),
            originalStatus = remote.status,
            remoteCard = null,
            submission = KanbanCardEditorSubmission.Idle,
        )
    }

    fun loadBaseline() {
        val edit = mode as? KanbanCardEditorMode.Edit ?: return
        val generation = ++loadGeneration
        mutableState.value = mutableState.value.copy(availability = KanbanCardEditorAvailability.Loading)
        viewModelScope.launch {
            try {
                val detail = repository.cardDetail(edit.cardId, board)
                if (generation != loadGeneration) return@launch
                val card = detail.card ?: error("validated detail omitted Card")
                baselineCard = card
                remotePrerequisiteId = detail.links?.prerequisites?.firstOrNull()
                mutableState.value = mutableState.value.copy(
                    availability = KanbanCardEditorAvailability.Ready,
                    draft = draftFrom(card, remotePrerequisiteId),
                    originalStatus = card.status,
                    submission = KanbanCardEditorSubmission.Idle,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                if (generation == loadGeneration) {
                    mutableState.value = mutableState.value.copy(
                        availability = KanbanCardEditorAvailability.Failed,
                    )
                }
            }
        }
    }

    private fun save(overwriteConflict: Boolean, readyUnassignedConfirmed: Boolean = false) {
        val current = mutableState.value
        if (!current.canSubmit && !(overwriteConflict && current.submission == KanbanCardEditorSubmission.Conflict)) return
        val validation = validate(current.draft)
        if (validation != null) {
            mutableState.value = current.copy(submission = KanbanCardEditorSubmission.ValidationFailed(validation))
            return
        }
        if (mode == KanbanCardEditorMode.Create &&
            current.draft.status == "ready" &&
            current.draft.assignee.normalized() == null &&
            !readyUnassignedConfirmed
        ) return
        if (saveJob?.isActive == true) return

        saveJob = viewModelScope.launch {
            mutableState.value = mutableState.value.copy(submission = KanbanCardEditorSubmission.Saving)
            if (mode is KanbanCardEditorMode.Edit && !overwriteConflict) {
                try {
                    val detail = repository.cardDetail(mode.cardId, board)
                    val latest = detail.card ?: error("validated detail omitted Card")
                    if (fingerprint(latest) != baselineCard?.let(::fingerprint)) {
                        remotePrerequisiteId = detail.links?.prerequisites?.firstOrNull()
                        mutableState.value = mutableState.value.copy(
                            submission = KanbanCardEditorSubmission.Conflict,
                            remoteCard = latest,
                        )
                        return@launch
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    mutableState.value = mutableState.value.copy(submission = KanbanCardEditorSubmission.Failed)
                    return@launch
                }
            }

            val draft = mutableState.value.draft
            validate(draft)?.let { field ->
                mutableState.value = mutableState.value.copy(
                    submission = KanbanCardEditorSubmission.ValidationFailed(field),
                )
                return@launch
            }
            val intent = mutationIntent(draft)
            try {
                val envelope = when (intent) {
                    is EditorMutationIntent.Create -> repository.createCard(board, intent.body)
                    is EditorMutationIntent.Edit -> repository.editCard(intent.cardId, board, intent.body)
                }
                if (envelope.readOnly == true) {
                    mutableState.value = mutableState.value.copy(
                        submission = KanbanCardEditorSubmission.Failed,
                        capabilityUnavailable = true,
                    )
                    return@launch
                }
                val card = requireNotNull(envelope.card)
                if (!intendedValuesAppear(card, intent)) error("mutation response did not match intent")
                complete(card)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (isMissingKanbanCapability(error)) {
                    mutableState.value = mutableState.value.copy(capabilityUnavailable = true)
                }
                if (isDefinitiveWriteFailure(error)) {
                    mutableState.value = mutableState.value.copy(submission = KanbanCardEditorSubmission.Failed)
                } else {
                    reconcileAmbiguousOutcome(intent)
                }
            }
        }
    }

    private suspend fun reconcileAmbiguousOutcome(intent: EditorMutationIntent) {
        mutableState.value = mutableState.value.copy(submission = KanbanCardEditorSubmission.CheckingResult)
        try {
            when (intent) {
                is EditorMutationIntent.Create -> {
                    val snapshot = repository.boardSnapshot(board, KanbanBrowseFilters())
                    val matches = snapshot.allCards().filter { card ->
                        val id = card.cardId.normalized()
                        id != null && id !in baselineMatchingCardIds && intendedValuesAppear(card, intent)
                    }
                    when (matches.size) {
                        1 -> complete(matches.single())
                        0 -> mutableState.value = mutableState.value.copy(submission = KanbanCardEditorSubmission.Failed)
                        else -> mutableState.value = mutableState.value.copy(submission = KanbanCardEditorSubmission.OutcomeUncertain)
                    }
                }
                is EditorMutationIntent.Edit -> {
                    val detail = repository.cardDetail(intent.cardId, board)
                    val card = requireNotNull(detail.card)
                    if (intendedValuesAppear(card, intent)) {
                        complete(card)
                    } else {
                        remotePrerequisiteId = detail.links?.prerequisites?.firstOrNull()
                        mutableState.value = mutableState.value.copy(
                            submission = KanbanCardEditorSubmission.Failed,
                            remoteCard = card,
                        )
                    }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            mutableState.value = mutableState.value.copy(submission = KanbanCardEditorSubmission.OutcomeUncertain)
        }
    }

    private fun validate(draft: KanbanCardEditorDraft): KanbanCardEditorField? {
        if (draft.title.normalized() == null) return KanbanCardEditorField.Title
        val priority = draft.priorityText.toIntOrNull()
        if (priority == null || priority !in -100..100) return KanbanCardEditorField.Priority
        val original = mutableState.value.originalStatus
        if (draft.status !in CREATE_STATUSES && !(mode is KanbanCardEditorMode.Edit && draft.status == original)) {
            return KanbanCardEditorField.Status
        }
        if (mode == KanbanCardEditorMode.Create) {
            if (draft.workspaceKind !in WORKSPACE_KINDS) return KanbanCardEditorField.WorkspacePath
            if (draft.workspaceKind != "scratch" && draft.workspacePath.normalized() == null) {
                return KanbanCardEditorField.WorkspacePath
            }
            if (draft.maximumRuntimeText.isNotEmpty() &&
                draft.maximumRuntimeText.toIntOrNull()?.let { it > 0 } != true
            ) return KanbanCardEditorField.MaximumRuntime
            if (draft.prerequisiteId.length > 255) return KanbanCardEditorField.Prerequisite
        }
        return null
    }

    private fun mutationIntent(draft: KanbanCardEditorDraft): EditorMutationIntent = when (mode) {
        KanbanCardEditorMode.Create -> EditorMutationIntent.Create(
            KanbanCreateCardRequestBody(
                title = requireNotNull(draft.title.normalized()),
                body = draft.body.takeIf { it.normalized() != null },
                status = draft.status,
                priority = draft.priorityText.toInt().takeUnless { it == 0 },
                assignee = draft.assignee.normalized(),
                tenant = draft.tenant.normalized(),
                workspaceKind = draft.workspaceKind,
                workspacePath = draft.workspacePath.normalized(),
                skills = draft.skillsText.split(',').mapNotNull(String::normalized).takeIf { it.isNotEmpty() },
                maxRuntimeSeconds = draft.maximumRuntimeText.toIntOrNull(),
                parents = draft.prerequisiteId.normalized()?.let(::listOf),
                idempotencyKey = idempotencyKey,
            ),
        )
        is KanbanCardEditorMode.Edit -> EditorMutationIntent.Edit(
            cardId = mode.cardId,
            body = KanbanEditCardRequestBody(
                title = requireNotNull(draft.title.normalized()),
                body = draft.body,
                tenant = draft.tenant.normalized(),
                priority = draft.priorityText.toInt(),
                assignee = draft.assignee.normalized(),
                status = draft.status.takeUnless { it == mutableState.value.originalStatus },
            ),
        )
    }

    private fun intendedValuesAppear(card: KanbanCardSummary, intent: EditorMutationIntent): Boolean = when (intent) {
        is EditorMutationIntent.Create -> {
            val body = intent.body
            card.title.normalized() == body.title.normalized() &&
                card.body.normalized() == body.body.normalized() &&
                card.assignee.normalized() == body.assignee.normalized() &&
                card.tenant.normalized() == body.tenant.normalized() &&
                (card.priority ?: 0) == (body.priority ?: 0) &&
                card.status == body.status &&
                card.workspaceKind.normalized() == body.workspaceKind.normalized() &&
                card.workspacePath.normalized() == body.workspacePath.normalized() &&
                card.skills.orEmpty() == body.skills.orEmpty() &&
                card.maxRuntimeSeconds == body.maxRuntimeSeconds
        }
        is EditorMutationIntent.Edit -> {
            val body = intent.body
            card.title.normalized() == body.title.normalized() &&
                card.body.normalized() == body.body.normalized() &&
                card.assignee.normalized() == body.assignee.normalized() &&
                card.tenant.normalized() == body.tenant.normalized() &&
                (card.priority ?: 0) == body.priority &&
                (body.status == null || card.status == body.status)
        }
    }

    private fun complete(card: KanbanCardSummary) {
        val cardId = card.cardId.normalized() ?: return
        baselineCard = card
        remotePrerequisiteId = null
        mutableState.value = mutableState.value.copy(
            submission = KanbanCardEditorSubmission.Succeeded(cardId),
            remoteCard = null,
        )
    }

    private fun draftFrom(card: KanbanCardSummary, prerequisiteId: String?) = KanbanCardEditorDraft(
        title = card.title.orEmpty(),
        body = card.body.orEmpty(),
        status = card.status ?: "triage",
        priorityText = (card.priority ?: 0).toString(),
        assignee = card.assignee.normalized(),
        tenant = card.tenant.orEmpty(),
        workspaceKind = card.workspaceKind?.takeIf(WORKSPACE_KINDS::contains) ?: "scratch",
        workspacePath = card.workspacePath.orEmpty(),
        skillsText = card.skills.orEmpty().joinToString(", "),
        maximumRuntimeText = card.maxRuntimeSeconds?.toString().orEmpty(),
        prerequisiteId = prerequisiteId.orEmpty(),
    )

    private fun fingerprint(card: KanbanCardSummary): List<String> = listOf(
        card.title.normalized().orEmpty(),
        card.body.normalized().orEmpty(),
        card.assignee.normalized().orEmpty(),
        card.tenant.normalized().orEmpty(),
        (card.priority ?: 0).toString(),
        card.status.normalized().orEmpty(),
        card.workspaceKind.normalized().orEmpty(),
        card.workspacePath.normalized().orEmpty(),
        card.skills.orEmpty().joinToString("\u001f"),
        card.maxRuntimeSeconds?.toString().orEmpty(),
    )

    private fun isDefinitiveWriteFailure(error: Throwable): Boolean = when (error) {
        ApiError.Unauthorized,
        is ApiError.InsecureTransport,
        -> true
        is ApiError.Http -> error.statusCode in 400..499 && error.statusCode != 408
        else -> false
    }

    private sealed interface EditorMutationIntent {
        data class Create(val body: KanbanCreateCardRequestBody) : EditorMutationIntent
        data class Edit(val cardId: String, val body: KanbanEditCardRequestBody) : EditorMutationIntent
    }

    internal companion object {
        val CREATE_STATUSES = listOf("triage", "todo", "ready")
        val WORKSPACE_KINDS = listOf("scratch", "worktree", "dir")
    }
}

private fun String?.normalized(): String? = this?.trim()?.takeIf(String::isNotEmpty)
