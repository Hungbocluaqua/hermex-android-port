package com.uzairansar.hermex.ui.kanban

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uzairansar.hermex.core.model.KanbanCardDetailEnvelope
import com.uzairansar.hermex.core.model.KanbanComment
import com.uzairansar.hermex.core.model.KanbanContractViolation
import com.uzairansar.hermex.core.model.KanbanWorkerLog
import com.uzairansar.hermex.core.network.ApiError
import com.uzairansar.hermex.data.repository.KanbanBrowseDataSource
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal enum class KanbanCardDetailAvailability {
    Loading,
    Content,
    Missing,
    NetworkUnavailable,
    ServerUnavailable,
    IncompatibleContract,
}

internal sealed interface KanbanWorkerLogState {
    data object Idle : KanbanWorkerLogState
    data object Loading : KanbanWorkerLogState
    data object Absent : KanbanWorkerLogState
    data class Loaded(val log: KanbanWorkerLog) : KanbanWorkerLogState
    data object Failed : KanbanWorkerLogState
}

internal enum class KanbanCommentSubmissionState {
    Idle,
    ValidationFailed,
    Submitting,
    CheckingResult,
    Succeeded,
    RetryAllowed,
    OutcomeUncertain,
    Failed,
}

internal data class KanbanCardDetailUiState(
    val availability: KanbanCardDetailAvailability = KanbanCardDetailAvailability.Loading,
    val detail: KanbanCardDetailEnvelope? = null,
    val isRefreshing: Boolean = false,
    val isStale: Boolean = false,
    val parentAllowsWrites: Boolean = false,
    val workerLog: KanbanWorkerLogState = KanbanWorkerLogState.Idle,
    val commentDraft: String = "",
    val commentSubmission: KanbanCommentSubmissionState = KanbanCommentSubmissionState.Idle,
    val commentCapabilityUnavailable: Boolean = false,
) {
    val canComment: Boolean
        get() = availability == KanbanCardDetailAvailability.Content &&
            parentAllowsWrites &&
            detail?.readOnly == false &&
            !commentCapabilityUnavailable &&
            !isStale &&
            commentSubmission !in setOf(
                KanbanCommentSubmissionState.Submitting,
                KanbanCommentSubmissionState.CheckingResult,
                KanbanCommentSubmissionState.OutcomeUncertain,
            )
}

internal class KanbanCardDetailViewModel(
    private val repository: KanbanBrowseDataSource,
    private val board: String,
    private val cardId: String,
    parentAllowsWrites: Boolean,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        KanbanCardDetailUiState(parentAllowsWrites = parentAllowsWrites),
    )
    val state: StateFlow<KanbanCardDetailUiState> = mutableState

    private var loadGeneration = 0
    private var parentRefreshRevision: Int? = null
    private var commentJob: Job? = null
    private var workerLogJob: Job? = null

    init {
        load()
    }

    fun load() {
        val generation = ++loadGeneration
        val previous = mutableState.value
        mutableState.value = if (previous.detail != null) {
            previous.copy(isRefreshing = true)
        } else {
            previous.copy(availability = KanbanCardDetailAvailability.Loading)
        }
        viewModelScope.launch {
            try {
                val detail = repository.cardDetail(cardId, board)
                if (generation != loadGeneration) return@launch
                mutableState.value = mutableState.value.copy(
                    availability = KanbanCardDetailAvailability.Content,
                    detail = detail,
                    isRefreshing = false,
                    isStale = false,
                    commentCapabilityUnavailable = false,
                    commentSubmission = if (
                        mutableState.value.commentCapabilityUnavailable &&
                        mutableState.value.commentSubmission == KanbanCommentSubmissionState.Failed
                    ) {
                        KanbanCommentSubmissionState.Idle
                    } else {
                        mutableState.value.commentSubmission
                    },
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (generation != loadGeneration) return@launch
                val current = mutableState.value
                mutableState.value = if (current.detail != null) {
                    current.copy(
                        isRefreshing = false,
                        isStale = current.isStale || isDetailNetworkError(error),
                    )
                } else {
                    current.copy(
                        availability = detailAvailabilityFor(error),
                        isRefreshing = false,
                    )
                }
            }
        }
    }

    fun updateParentState(isOffline: Boolean, allowsWrites: Boolean, refreshRevision: Int) {
        val current = mutableState.value
        mutableState.value = current.copy(
            isStale = current.isStale || isOffline,
            parentAllowsWrites = allowsWrites,
        )
        val previousRevision = parentRefreshRevision
        parentRefreshRevision = maxOf(previousRevision ?: refreshRevision, refreshRevision)
        if (!isOffline && current.detail != null && previousRevision != null && refreshRevision > previousRevision) {
            load()
        }
    }

    fun updateCommentDraft(value: String) {
        mutableState.value = mutableState.value.copy(
            commentDraft = value,
            commentSubmission = if (mutableState.value.commentSubmission == KanbanCommentSubmissionState.ValidationFailed) {
                KanbanCommentSubmissionState.Idle
            } else {
                mutableState.value.commentSubmission
            },
        )
    }

    fun submitComment() {
        val current = mutableState.value
        val body = current.commentDraft.trim()
        if (body.isEmpty()) {
            mutableState.value = current.copy(commentSubmission = KanbanCommentSubmissionState.ValidationFailed)
            return
        }
        if (!current.canComment || commentJob?.isActive == true) return
        val previousComments = current.detail?.comments.orEmpty()
        commentJob = viewModelScope.launch {
            mutableState.value = mutableState.value.copy(commentSubmission = KanbanCommentSubmissionState.Submitting)
            try {
                val response = repository.addComment(cardId, board, body)
                if (response.ok == true && !response.commentId.isNullOrBlank() && response.readOnly != true) {
                    val optimistic = KanbanComment(
                        commentId = response.commentId,
                        cardId = cardId,
                        body = body,
                    )
                    val state = mutableState.value
                    mutableState.value = state.copy(
                        detail = state.detail?.copy(comments = state.detail.comments.orEmpty() + optimistic),
                        commentDraft = "",
                        commentSubmission = KanbanCommentSubmissionState.Succeeded,
                    )
                } else if (response.readOnly == true) {
                    mutableState.value = mutableState.value.copy(
                        parentAllowsWrites = false,
                        commentSubmission = KanbanCommentSubmissionState.Failed,
                    )
                } else {
                    reconcileComment(previousComments, body)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (isMissingKanbanCapability(error)) {
                    mutableState.value = mutableState.value.copy(
                        commentCapabilityUnavailable = true,
                        commentSubmission = KanbanCommentSubmissionState.Failed,
                    )
                } else {
                    reconcileComment(previousComments, body)
                }
            }
        }
    }

    fun retryComment() {
        if (mutableState.value.commentSubmission == KanbanCommentSubmissionState.RetryAllowed) submitComment()
    }

    fun dismissCommentNotice() {
        if (mutableState.value.commentSubmission in setOf(
                KanbanCommentSubmissionState.Succeeded,
                KanbanCommentSubmissionState.Failed,
                KanbanCommentSubmissionState.ValidationFailed,
            )
        ) {
            mutableState.value = mutableState.value.copy(commentSubmission = KanbanCommentSubmissionState.Idle)
        }
    }

    fun loadWorkerLog() {
        if (workerLogJob?.isActive == true || mutableState.value.isStale) return
        workerLogJob = viewModelScope.launch {
            mutableState.value = mutableState.value.copy(workerLog = KanbanWorkerLogState.Loading)
            try {
                val log = repository.workerLog(cardId, board)
                mutableState.value = mutableState.value.copy(
                    workerLog = if (log.exists == true) {
                        KanbanWorkerLogState.Loaded(log)
                    } else {
                        KanbanWorkerLogState.Absent
                    },
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                mutableState.value = mutableState.value.copy(workerLog = KanbanWorkerLogState.Failed)
            }
        }
    }

    private suspend fun reconcileComment(previousComments: List<KanbanComment>, body: String) {
        mutableState.value = mutableState.value.copy(commentSubmission = KanbanCommentSubmissionState.CheckingResult)
        try {
            val detail = repository.cardDetail(cardId, board)
            val previousIds = previousComments.mapNotNull { it.commentId }.toSet()
            val canonicalComments = detail.comments.orEmpty()
            val found = canonicalComments.any { comment ->
                comment.commentId?.let { it !in previousIds } == true && comment.body?.trim() == body
            } || (
                canonicalComments.size > previousComments.size &&
                    canonicalComments.drop(previousComments.size).any { it.body?.trim() == body }
                )
            mutableState.value = mutableState.value.copy(
                availability = KanbanCardDetailAvailability.Content,
                detail = detail,
                isStale = false,
                commentDraft = if (found) "" else mutableState.value.commentDraft,
                commentSubmission = if (found) {
                    KanbanCommentSubmissionState.Succeeded
                } else {
                    KanbanCommentSubmissionState.RetryAllowed
                },
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            mutableState.value = mutableState.value.copy(
                commentSubmission = KanbanCommentSubmissionState.OutcomeUncertain,
                isStale = true,
            )
        }
    }

    override fun onCleared() {
        commentJob?.cancel()
        workerLogJob?.cancel()
        super.onCleared()
    }
}

internal fun detailAvailabilityFor(error: Throwable): KanbanCardDetailAvailability = when (error) {
    is ApiError.Http -> when {
        error.statusCode == 404 -> KanbanCardDetailAvailability.Missing
        error.statusCode >= 500 -> KanbanCardDetailAvailability.ServerUnavailable
        else -> KanbanCardDetailAvailability.IncompatibleContract
    }
    is ApiError.Network,
    is IOException,
    -> KanbanCardDetailAvailability.NetworkUnavailable
    is KanbanContractViolation,
    is ApiError.Decoding,
    is ApiError.InvalidResponse,
    -> KanbanCardDetailAvailability.IncompatibleContract
    else -> KanbanCardDetailAvailability.ServerUnavailable
}

private fun isDetailNetworkError(error: Throwable): Boolean = error is ApiError.Network || error is IOException
