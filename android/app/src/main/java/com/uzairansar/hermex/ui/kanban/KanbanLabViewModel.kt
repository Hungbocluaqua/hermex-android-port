package com.uzairansar.hermex.ui.kanban

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uzairansar.hermex.core.model.KanbanAssigneeHistory
import com.uzairansar.hermex.core.model.KanbanBoardSnapshot
import com.uzairansar.hermex.core.model.KanbanBoardSummary
import com.uzairansar.hermex.core.model.KanbanBoardsResponse
import com.uzairansar.hermex.core.model.KanbanCompatibilityReport
import com.uzairansar.hermex.core.model.KanbanCompatibilityWarning
import com.uzairansar.hermex.core.model.KanbanConfiguration
import com.uzairansar.hermex.core.model.KanbanContractViolation
import com.uzairansar.hermex.core.model.KanbanCardSummary
import com.uzairansar.hermex.core.model.KanbanEventsEnvelope
import com.uzairansar.hermex.core.model.KanbanStats
import com.uzairansar.hermex.core.network.ApiError
import com.uzairansar.hermex.core.network.KanbanStreamFrame
import com.uzairansar.hermex.data.repository.KanbanBrowseDataSource
import com.uzairansar.hermex.data.repository.KanbanBrowseFilters
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal enum class KanbanAvailability {
    Loading,
    Content,
    AuthenticationRequired,
    NetworkUnavailable,
    ServerUnavailable,
    IncompatibleContract,
}

internal data class KanbanLiveTiming(
    val reconnectDelaysMillis: List<Long> = listOf(1_000, 2_000),
    val failuresBeforePolling: Int = 3,
    val coalescingDelayMillis: Long = 250,
    val pollingIntervalMillis: Long = 30_000,
    val initialPollingDelayMillis: Long? = null,
)

internal data class KanbanFilterState(
    val profile: String? = null,
    val tenant: String? = null,
    val includeArchived: Boolean = false,
    val onlyMine: Boolean = false,
    val groupByProfile: Boolean = false,
) {
    val hasServerFilters: Boolean
        get() = profile != null || tenant != null || includeArchived || onlyMine

    fun request(): KanbanBrowseFilters = KanbanBrowseFilters(
        profile = profile.takeUnless { onlyMine },
        tenant = tenant,
        includeArchived = includeArchived,
        onlyMine = onlyMine,
    )
}

internal data class KanbanLabUiState(
    val availability: KanbanAvailability = KanbanAvailability.Loading,
    val configuration: KanbanConfiguration? = null,
    val boards: List<KanbanBoardSummary> = emptyList(),
    val sharedActiveBoardSlug: String? = null,
    val boardsReadOnly: Boolean? = false,
    val selectedBoardSlug: String? = null,
    val boardSelectionNotice: String? = null,
    val snapshot: KanbanBoardSnapshot? = null,
    val stats: KanbanStats? = null,
    val assigneeHistory: KanbanAssigneeHistory? = null,
    val warnings: List<KanbanCompatibilityWarning> = emptyList(),
    val filters: KanbanFilterState = KanbanFilterState(),
    val selectedStatus: String = "triage",
    val searchQuery: String = "",
    val isRefreshing: Boolean = false,
    val refreshFailed: Boolean = false,
    val isOffline: Boolean = false,
    val liveUpdatesDelayed: Boolean = false,
    val detailRefreshRevision: Int = 0,
    val workflowCapabilityUnavailable: Boolean = false,
) {
    val selectedBoard: KanbanBoardSummary?
        get() = boards.firstOrNull { it.slug?.trim() == selectedBoardSlug }

    val availableStatuses: List<String>
        get() = availableKanbanStatuses(snapshot, filters.includeArchived)

    val visibleCards
        get() = visibleKanbanCards(snapshot, selectedStatus, searchQuery)

    val profileOptions: List<String>
        get() = kanbanProfileOptions(configuration, snapshot, assigneeHistory?.names.orEmpty())

    val tenantOptions: List<String>
        get() = kanbanTenantOptions(snapshot)

    val hasActiveFilters: Boolean
        get() = searchQuery.isNotBlank() || filters.hasServerFilters

    val canMutateCards: Boolean
        get() = canUseWrites &&
            KanbanCardEditorViewModel.CREATE_STATUSES.all { it in configuration?.columns.orEmpty() }

    val canUseWrites: Boolean
        get() = availability == KanbanAvailability.Content &&
            !isOffline &&
            !isRefreshing &&
            !refreshFailed &&
            configuration?.readOnly == false &&
            boardsReadOnly == false &&
            snapshot?.readOnly == false &&
            selectedBoard?.readOnly != true &&
            warnings.none {
                it == KanbanCompatibilityWarning.ReadOnly ||
                    it == KanbanCompatibilityWarning.WriteCapabilityUnavailable
            }

    val canUseCardWorkflow: Boolean
        get() = canMutateCards && !workflowCapabilityUnavailable
}

internal class KanbanLabViewModel(
    private val repository: KanbanBrowseDataSource,
    private val liveTiming: KanbanLiveTiming = KanbanLiveTiming(),
) : ViewModel() {
    private val mutableState = MutableStateFlow(KanbanLabUiState())
    val state: StateFlow<KanbanLabUiState> = mutableState
    private val bulkController = KanbanBulkActionController(
        repository = repository,
        scope = viewModelScope,
        selectedBoard = { mutableState.value.selectedBoardSlug },
        configuredColumns = { mutableState.value.configuration?.columns.orEmpty() },
        profileOptions = { mutableState.value.profileOptions },
        isOffline = { mutableState.value.isOffline },
        isRefreshing = { mutableState.value.isRefreshing },
        baseAllowsMutation = { mutableState.value.canMutateCards },
        contractCompatible = {
            mutableState.value.availability == KanbanAvailability.Content &&
                mutableState.value.snapshot != null &&
                KanbanCardEditorViewModel.CREATE_STATUSES.all {
                    status -> status in mutableState.value.configuration?.columns.orEmpty()
                }
        },
        isReadOnly = {
            val current = mutableState.value
            current.configuration?.readOnly != false ||
                current.snapshot?.readOnly != false ||
                current.selectedBoard?.readOnly == true ||
                current.warnings.any {
                    it == KanbanCompatibilityWarning.ReadOnly ||
                        it == KanbanCompatibilityWarning.WriteCapabilityUnavailable
                }
        },
        hasOtherBoardActivity = { hasActiveWorkflowMutation() || boardMutationBlocksWrites() || dispatchBlocksWrites() },
        cardInSnapshot = ::cardInCurrentSnapshot,
        replaceCard = ::replaceCardInCurrentSnapshot,
        refreshBoard = ::refreshAfterBulkAction,
    )
    val bulkState: StateFlow<KanbanBulkUiState> = bulkController.state
    private val workflowController = KanbanWorkflowController(
        repository = repository,
        scope = viewModelScope,
        selectedBoard = { mutableState.value.selectedBoardSlug },
        configuredColumns = { mutableState.value.configuration?.columns.orEmpty() },
        includesArchived = { mutableState.value.filters.includeArchived },
        canMutate = { card ->
            mutableState.value.canUseCardWorkflow &&
                bulkController.state.value.phase == null &&
                !boardMutationBlocksWrites() &&
                !dispatchBlocksWrites() &&
                card.hasSupportedStatus
        },
        cardInSnapshot = ::cardInCurrentSnapshot,
        replaceCard = ::replaceCardInCurrentSnapshot,
        removeCard = ::removeCardFromCurrentSnapshot,
        onStatusSucceeded = { _, status ->
            val current = mutableState.value
            if (status in current.availableStatuses) mutableState.value = current.copy(selectedStatus = status)
        },
        onDetailRefresh = {
            val current = mutableState.value
            mutableState.value = current.copy(detailRefreshRevision = current.detailRefreshRevision + 1)
        },
        onCapabilityUnavailable = {
            mutableState.value = mutableState.value.copy(workflowCapabilityUnavailable = true)
        },
    )
    val workflowState: StateFlow<KanbanWorkflowUiState> = workflowController.state
    private val boardController = KanbanBoardManagementController(
        repository = repository,
        scope = viewModelScope,
        baseAllowsMutation = { mutableState.value.canUseWrites },
        hasOtherBoardActivity = {
            hasActiveWorkflowMutation() || bulkController.state.value.phase != null || dispatchBlocksWrites()
        },
        boardExists = { slug -> mutableState.value.boards.any { it.slug?.trim() == slug } },
        applyBoardsResponse = ::applyBoardsResponse,
    )
    val boardState: StateFlow<KanbanBoardManagementUiState>
        get() = boardController.state
    private val dispatcherController = KanbanDispatcherController(
        repository = repository,
        scope = viewModelScope,
        selectedBoard = { mutableState.value.selectedBoardSlug },
        availability = ::dispatcherAvailability,
        boardActivityGeneration = { boardActivityGeneration },
        markBoardActivity = ::markBoardActivity,
        applyBoards = ::reconcileBoardCollectionForDispatch,
        refreshBoard = ::refreshBoardForDispatch,
        onOffline = { mutableState.value = mutableState.value.copy(isOffline = true, refreshFailed = false) },
    )
    val dispatchState: StateFlow<KanbanDispatchState?> = dispatcherController.state

    private var loadGeneration = 0
    private var liveGeneration = 0
    private var liveCursor = 0
    private var streamFailureCount = 0
    private var boardActivityGeneration = 0
    private var isVisible = false
    private var lifecycleActive = false
    private var hasBeenLifecycleActive = false
    private var streamJob: Job? = null
    private var reconnectJob: Job? = null
    private var coalescingJob: Job? = null
    private var pollingJob: Job? = null

    init {
        load()
    }

    fun load() {
        suspendLiveUpdates()
        val generation = ++loadGeneration
        val previous = mutableState.value
        mutableState.value = if (previous.availability == KanbanAvailability.Content) {
            previous.copy(isRefreshing = true, refreshFailed = false)
        } else {
            KanbanLabUiState(availability = KanbanAvailability.Loading)
        }
        viewModelScope.launch {
            try {
                val report = repository.compatibilityHandshake()
                val previousFilters = previous.filters
                val filters = if (previous.availability == KanbanAvailability.Content) {
                    previousFilters
                } else {
                    previousFilters.copy(includeArchived = report.configuration.includeArchivedByDefault == true)
                }
                val previousSlug = previous.selectedBoardSlug
                val previousBoardWasRemoved = previous.availability == KanbanAvailability.Content &&
                    previousSlug != null && report.boards.none { it.slug?.trim() == previousSlug }
                val selectedSlug = if (previousBoardWasRemoved) {
                    null
                } else {
                    previousSlug
                        ?.takeIf { slug -> report.boards.any { it.slug?.trim() == slug } }
                        ?: report.currentBoard.slug?.trim().orEmpty()
                }
                val snapshot = selectedSlug?.let { slug ->
                    val needsFilteredSnapshot = slug != report.currentBoard.slug?.trim() || filters.hasServerFilters
                    if (needsFilteredSnapshot) repository.boardSnapshot(slug, filters.request()) else report.snapshot
                }
                val supplementary = selectedSlug?.let { loadSupplementary(it) } ?: (null to null)
                if (generation != loadGeneration) return@launch
                val boardChanged = previous.selectedBoardSlug != null && previous.selectedBoardSlug != selectedSlug
                workflowController.acknowledgeCanonicalBoardLoad(boardChanged)
                val protectedSnapshot = snapshot?.let {
                    if (boardChanged) it else workflowController.protectSnapshot(it)
                }
                val statuses = availableKanbanStatuses(protectedSnapshot, filters.includeArchived)
                val selectedStatus = previous.selectedStatus.takeIf(statuses::contains)
                    ?: "triage".takeIf(statuses::contains)
                    ?: statuses.firstOrNull().orEmpty()
                liveCursor = snapshot?.latestEventId?.coerceAtLeast(0) ?: 0
                streamFailureCount = 0
                mutableState.value = KanbanLabUiState(
                    availability = KanbanAvailability.Content,
                    configuration = report.configuration,
                    boards = report.boards,
                    sharedActiveBoardSlug = report.currentBoard.slug?.trim(),
                    boardsReadOnly = report.boardsReadOnly,
                    selectedBoardSlug = selectedSlug,
                    boardSelectionNotice = if (previousBoardWasRemoved) {
                        previous.selectedBoard?.name?.trim()?.takeIf(String::isNotEmpty) ?: previousSlug
                    } else {
                        null
                    },
                    snapshot = protectedSnapshot,
                    stats = supplementary.first,
                    assigneeHistory = supplementary.second,
                    warnings = if (selectedSlug != null && protectedSnapshot != null) {
                        warningsFor(report, selectedSlug, protectedSnapshot)
                    } else {
                        emptyList()
                    },
                    filters = filters,
                    selectedStatus = selectedStatus,
                    searchQuery = previous.searchQuery,
                    isOffline = false,
                    liveUpdatesDelayed = false,
                    detailRefreshRevision = previous.detailRefreshRevision + 1,
                )
                bulkController.acknowledgeFullReload(protectedSnapshot?.allCards().orEmpty(), boardChanged)
                boardController.acknowledgeFullReload()
                dispatcherController.acknowledgeFullReload()
                markBoardActivity()
                startLiveUpdatesIfReady()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (generation != loadGeneration) return@launch
                mutableState.value = if (previous.availability == KanbanAvailability.Content) {
                    val offline = isOfflineError(error)
                    previous.copy(
                        isRefreshing = false,
                        refreshFailed = !offline,
                        isOffline = previous.isOffline || offline,
                    )
                } else {
                    KanbanLabUiState(availability = kanbanAvailabilityFor(error))
                }
                if (mutableState.value.isOffline) startPollingIfNeeded()
            }
        }
    }

    fun setVisible(visible: Boolean) {
        if (isVisible == visible) return
        isVisible = visible
        if (visible) startLiveUpdatesIfReady() else suspendLiveUpdates()
    }

    fun setLifecycleActive(active: Boolean) {
        if (lifecycleActive == active) return
        lifecycleActive = active
        if (!active) {
            suspendLiveUpdates()
            return
        }
        val returningToForeground = hasBeenLifecycleActive
        hasBeenLifecycleActive = true
        if (returningToForeground && isVisible && mutableState.value.availability == KanbanAvailability.Content) {
            load()
        } else {
            startLiveUpdatesIfReady()
        }
    }

    fun selectBoard(slug: String) {
        val normalized = slug.trim().takeIf(String::isNotEmpty) ?: return
        if (normalized == mutableState.value.selectedBoardSlug) return
        mutableState.value = mutableState.value.copy(boardSelectionNotice = null)
        bulkController.resetForBoardChange()
        loadBoard(normalized, mutableState.value.filters)
    }

    fun canManageBoards(): Boolean = boardController.canManageBoards()

    fun createBoard(slug: String, name: String, description: String, icon: String, color: String) =
        boardController.create(slug, name, description, icon, color)

    fun editBoard(slug: String, name: String, description: String, icon: String, color: String) =
        boardController.edit(slug, name, description, icon, color)

    fun archiveBoard(slug: String) = boardController.archive(slug)

    fun makeBoardActive(slug: String) = boardController.makeActive(slug)

    fun checkBoardMutationResult() = boardController.checkResult()

    fun dismissBoardMutationResult() = boardController.dismissResult()

    fun previewDispatch() = dispatcherController.preview()
    fun runDispatcher() = dispatcherController.run()
    fun dismissDispatchResult() = dispatcherController.dismiss()
    fun refreshUncertainDispatchOutcome() = dispatcherController.refreshUncertain()
    fun isPreviewStale(): Boolean = dispatcherController.isPreviewStale()

    fun dispatcherAvailability(): KanbanDispatcherAvailability {
        val dispatch = dispatcherController.state.value
        if (dispatch?.isInFlight == true || boardMutationBlocksWrites() || bulkController.state.value.phase != null || hasActiveWorkflowMutation()) {
            return KanbanDispatcherAvailability.Busy
        }
        val state = mutableState.value
        if (state.isOffline) return KanbanDispatcherAvailability.Offline
        if (state.isRefreshing) return KanbanDispatcherAvailability.Refreshing
        if (state.refreshFailed) return KanbanDispatcherAvailability.RefreshFailed
        if (dispatch?.mode == KanbanDispatchMode.Run && dispatch.phase == KanbanDispatchPhase.OutcomeUncertain) {
            return KanbanDispatcherAvailability.OutcomeUncertain
        }
        if (dispatcherController.capabilityIncompatible || state.availability != KanbanAvailability.Content || state.snapshot == null || state.selectedBoardSlug == null) {
            return KanbanDispatcherAvailability.Incompatible
        }
        if (state.configuration?.readOnly == true || state.boardsReadOnly == true || state.snapshot.readOnly == true || state.selectedBoard?.readOnly == true) {
            return KanbanDispatcherAvailability.ReadOnly
        }
        if (state.configuration?.readOnly != false || state.boardsReadOnly != false || state.snapshot.readOnly != false) {
            return KanbanDispatcherAvailability.Incompatible
        }
        return KanbanDispatcherAvailability.Available
    }

    fun applyFilters(filters: KanbanFilterState) {
        val state = mutableState.value
        val normalized = filters.copy(
            profile = filters.profile.normalizedFilterValue().takeUnless { filters.onlyMine },
            tenant = filters.tenant.normalizedFilterValue(),
        )
        if (normalized.request() == state.filters.request()) {
            mutableState.value = state.copy(filters = normalized)
            return
        }
        val slug = state.selectedBoardSlug ?: return
        loadBoard(slug, normalized)
    }

    fun clearFilters() {
        mutableState.value = mutableState.value.copy(searchQuery = "")
        val current = mutableState.value.filters
        applyFilters(KanbanFilterState(groupByProfile = current.groupByProfile))
    }

    fun setSearchQuery(query: String) {
        mutableState.value = mutableState.value.copy(searchQuery = query)
    }

    fun selectStatus(status: String) {
        if (status in mutableState.value.availableStatuses) {
            mutableState.value = mutableState.value.copy(selectedStatus = status)
        }
    }

    private fun loadBoard(slug: String, filters: KanbanFilterState) {
        suspendLiveUpdates()
        val previous = mutableState.value
        val generation = ++loadGeneration
        mutableState.value = previous.copy(isRefreshing = true, refreshFailed = false)
        viewModelScope.launch {
            try {
                val snapshot = repository.boardSnapshot(slug, filters.request())
                val supplementary = loadSupplementary(slug)
                if (generation != loadGeneration) return@launch
                val current = mutableState.value
                val boardChanged = previous.selectedBoardSlug != slug
                workflowController.acknowledgeCanonicalBoardLoad(boardChanged)
                val protectedSnapshot = if (boardChanged) snapshot else workflowController.protectSnapshot(snapshot)
                val statuses = availableKanbanStatuses(protectedSnapshot, filters.includeArchived)
                val selectedStatus = current.selectedStatus
                    .takeIf { it in statuses && (it != "archived" || filters.includeArchived) }
                    ?: "triage".takeIf(statuses::contains)
                    ?: statuses.firstOrNull().orEmpty()
                val report = KanbanCompatibilityReport(
                    configuration = requireNotNull(previous.configuration),
                    boards = previous.boards,
                    currentBoard = previous.selectedBoard ?: previous.boards.first(),
                    snapshot = protectedSnapshot,
                    warnings = previous.warnings,
                )
                liveCursor = snapshot.latestEventId?.coerceAtLeast(0) ?: 0
                streamFailureCount = 0
                mutableState.value = current.copy(
                    availability = KanbanAvailability.Content,
                    selectedBoardSlug = slug,
                    snapshot = protectedSnapshot,
                    stats = supplementary.first,
                    assigneeHistory = supplementary.second,
                    warnings = warningsFor(report, slug, protectedSnapshot),
                    filters = filters,
                    selectedStatus = selectedStatus,
                    isRefreshing = false,
                    refreshFailed = false,
                    isOffline = false,
                    liveUpdatesDelayed = false,
                    detailRefreshRevision = current.detailRefreshRevision + 1,
                    workflowCapabilityUnavailable = if (boardChanged) false else current.workflowCapabilityUnavailable,
                )
                markBoardActivity()
                if (boardChanged) {
                    bulkController.resetForBoardChange()
                } else {
                    bulkController.acknowledgeSnapshot(protectedSnapshot.allCards())
                }
                startLiveUpdatesIfReady()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (generation == loadGeneration) {
                    val offline = isOfflineError(error)
                    mutableState.value = previous.copy(
                        isRefreshing = false,
                        refreshFailed = !offline,
                        isOffline = previous.isOffline || offline,
                    )
                    if (mutableState.value.isOffline) startPollingIfNeeded() else startLiveUpdatesIfReady()
                }
            }
        }
    }

    private fun startLiveUpdatesIfReady() {
        val state = mutableState.value
        if (!isVisible || !lifecycleActive || state.availability != KanbanAvailability.Content) return
        if (state.isOffline) {
            startPollingIfNeeded()
            return
        }
        if (streamJob?.isActive == true || reconnectJob?.isActive == true || pollingJob?.isActive == true) return
        startStream()
    }

    private fun startStream() {
        val board = mutableState.value.selectedBoardSlug ?: return
        if (!isVisible || !lifecycleActive) return
        val generation = liveGeneration
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            var failed = false
            try {
                repository.eventStream(board, liveCursor).collect { frame ->
                    if (!isCurrentLiveWork(board, generation)) throw CancellationException()
                    when (frame) {
                        is KanbanStreamFrame.Hello -> {
                            if (frame.board != board) throw MalformedKanbanLiveUpdate
                            liveCursor = maxOf(liveCursor, frame.cursor)
                            streamFailureCount = 0
                            mutableState.value = mutableState.value.copy(liveUpdatesDelayed = false)
                            if (mutableState.value.isOffline) {
                                scheduleCoalescedReconciliation(board, generation, immediate = true)
                            }
                        }
                        is KanbanStreamFrame.Events -> {
                            validateEventFrame(frame.events.map { it.eventId }, frame.cursor, frame.frameId)
                            if (frame.cursor > liveCursor) {
                                liveCursor = frame.cursor
                                scheduleCoalescedReconciliation(board, generation)
                            }
                        }
                        KanbanStreamFrame.Malformed -> throw MalformedKanbanLiveUpdate
                        KanbanStreamFrame.Ignored -> Unit
                    }
                }
                failed = true
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                failed = true
            } finally {
                if (failed && isCurrentLiveWork(board, generation)) {
                    streamJob = null
                    handleStreamFailure(board, generation)
                }
            }
        }
    }

    private fun handleStreamFailure(board: String, generation: Int) {
        if (!isCurrentLiveWork(board, generation)) return
        streamFailureCount += 1
        if (streamFailureCount >= liveTiming.failuresBeforePolling.coerceAtLeast(1)) {
            mutableState.value = mutableState.value.copy(liveUpdatesDelayed = true)
            startPollingIfNeeded()
            return
        }
        val delays = liveTiming.reconnectDelaysMillis.ifEmpty { listOf(1_000L) }
        val delayMillis = delays[minOf(streamFailureCount - 1, delays.lastIndex)].coerceAtLeast(0L)
        reconnectJob?.cancel()
        reconnectJob = viewModelScope.launch {
            delay(delayMillis)
            reconnectJob = null
            if (isCurrentLiveWork(board, generation)) startStream()
        }
    }

    private fun scheduleCoalescedReconciliation(
        board: String,
        generation: Int,
        immediate: Boolean = false,
    ) {
        coalescingJob?.cancel()
        coalescingJob = viewModelScope.launch {
            if (!immediate) delay(liveTiming.coalescingDelayMillis.coerceAtLeast(0))
            if (!isCurrentLiveWork(board, generation)) return@launch
            val succeeded = refreshLiveBoard(board, generation)
            coalescingJob = null
            if (!succeeded && mutableState.value.isOffline) startPollingIfNeeded()
        }
    }

    private fun startPollingIfNeeded() {
        val board = mutableState.value.selectedBoardSlug ?: return
        if (!isVisible || !lifecycleActive || pollingJob?.isActive == true) return
        streamJob?.cancel()
        streamJob = null
        reconnectJob?.cancel()
        reconnectJob = null
        val generation = liveGeneration
        pollingJob = viewModelScope.launch {
            var nextDelayMillis = liveTiming.initialPollingDelayMillis
                ?: liveTiming.pollingIntervalMillis
            while (isCurrentLiveWork(board, generation)) {
                delay(nextDelayMillis.coerceAtLeast(1))
                if (!isCurrentLiveWork(board, generation)) return@launch
                pollEvents(board, generation)
                nextDelayMillis = liveTiming.pollingIntervalMillis
            }
        }
    }

    private suspend fun pollEvents(board: String, generation: Int) {
        try {
            val envelope = repository.events(board, liveCursor)
            if (!isCurrentLiveWork(board, generation)) return
            val cursor = validateEventsEnvelope(envelope)
            if (mutableState.value.isOffline) {
                liveCursor = maxOf(liveCursor, cursor)
                if (refreshLiveBoard(board, generation)) retryLiveStream()
            } else if (cursor > liveCursor) {
                liveCursor = cursor
                scheduleCoalescedReconciliation(board, generation)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (isCurrentLiveWork(board, generation) && isOfflineError(error)) markOffline()
        }
    }

    private suspend fun refreshLiveBoard(board: String, generation: Int): Boolean {
        val requestState = mutableState.value
        return try {
            val snapshot = repository.boardSnapshot(board, requestState.filters.request())
            val supplementary = loadSupplementary(board)
            if (!isCurrentLiveWork(board, generation)) return false
            val current = mutableState.value
            val selectedBoard = current.boards.firstOrNull { it.slug?.trim() == board }
                ?: return false
            val report = KanbanCompatibilityReport(
                configuration = current.configuration ?: return false,
                boards = current.boards,
                currentBoard = selectedBoard,
                snapshot = snapshot,
                warnings = current.warnings,
            )
            liveCursor = maxOf(liveCursor, snapshot.latestEventId?.coerceAtLeast(0) ?: 0)
            mutableState.value = current.copy(
                snapshot = workflowController.protectSnapshot(snapshot),
                stats = supplementary.first,
                assigneeHistory = supplementary.second,
                warnings = warningsFor(report, board, snapshot),
                refreshFailed = false,
                isOffline = false,
                detailRefreshRevision = current.detailRefreshRevision + 1,
            )
            bulkController.acknowledgeSnapshot(mutableState.value.snapshot?.allCards().orEmpty())
            true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (isCurrentLiveWork(board, generation) && isOfflineError(error)) markOffline()
            false
        }
    }

    private fun retryLiveStream() {
        if (!isVisible || !lifecycleActive) return
        pollingJob?.cancel()
        pollingJob = null
        reconnectJob?.cancel()
        reconnectJob = null
        streamFailureCount = 0
        mutableState.value = mutableState.value.copy(liveUpdatesDelayed = false, isOffline = false)
        startStream()
    }

    private fun suspendLiveUpdates() {
        liveGeneration += 1
        streamJob?.cancel()
        streamJob = null
        reconnectJob?.cancel()
        reconnectJob = null
        coalescingJob?.cancel()
        coalescingJob = null
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun markOffline() {
        if (mutableState.value.snapshot == null) return
        mutableState.value = mutableState.value.copy(isOffline = true, refreshFailed = false)
    }

    private fun isCurrentLiveWork(board: String, generation: Int): Boolean =
        generation == liveGeneration &&
            mutableState.value.selectedBoardSlug == board &&
            isVisible &&
            lifecycleActive

    private fun validateEventsEnvelope(envelope: KanbanEventsEnvelope): Int {
        val cursor = envelope.cursor ?: throw MalformedKanbanLiveUpdate
        validateEventFrame(envelope.events?.map { it.eventId }, cursor, frameId = null)
        if (cursor < liveCursor) throw MalformedKanbanLiveUpdate
        return cursor
    }

    private fun validateEventFrame(eventIds: List<Int?>?, cursor: Int, frameId: Int?) {
        if (
            cursor < 0 ||
            (frameId != null && frameId != cursor) ||
            eventIds == null ||
            eventIds.any { it == null || it < 0 || it > cursor }
        ) {
            throw MalformedKanbanLiveUpdate
        }
    }

    fun canMutateCard(card: KanbanCardSummary): Boolean = workflowController.canMutateCard(card)

    fun isMutatingCard(cardId: String?): Boolean = workflowController.isMutatingCard(cardId)

    fun moveDestinations(card: KanbanCardSummary): List<String> =
        workflowController.moveDestinations(card, mutableState.value.configuration?.columns.orEmpty())

    fun moveCard(card: KanbanCardSummary, status: String, confirmingRunningExit: Boolean = false) =
        workflowController.moveCard(card, status, confirmingRunningExit)

    fun completeCard(card: KanbanCardSummary, confirmingRunningExit: Boolean = false) =
        workflowController.completeCard(card, confirmingRunningExit)

    fun archiveCard(card: KanbanCardSummary, confirmingRunningExit: Boolean = false) =
        workflowController.archiveCard(card, confirmingRunningExit)

    fun blockCard(card: KanbanCardSummary, confirmingRunningExit: Boolean = false) =
        workflowController.blockCard(card, confirmingRunningExit = confirmingRunningExit)

    fun unblockCard(card: KanbanCardSummary) = workflowController.unblockCard(card)

    fun addPrerequisite(prerequisiteId: String, card: KanbanCardSummary) =
        workflowController.addPrerequisite(prerequisiteId, card)

    fun removePrerequisite(prerequisiteId: String, card: KanbanCardSummary) =
        workflowController.removePrerequisite(prerequisiteId, card)

    fun retryMutation(card: KanbanCardSummary, confirmingRunningExit: Boolean = false) =
        workflowController.retryMutation(card, confirmingRunningExit)

    fun checkUncertainMutation(card: KanbanCardSummary) = workflowController.checkUncertainMutation(card)

    fun undoArchive() = workflowController.undoArchive()

    fun displayedCard(card: KanbanCardSummary): KanbanCardSummary = workflowController.displayedCard(card)

    fun displayedPrerequisites(cardId: String, canonical: List<String>): List<String> =
        workflowController.displayedPrerequisites(cardId, canonical)

    fun acknowledgeLoadedCardDetail(cardId: String) = workflowController.acknowledgeLoadedCardDetail(cardId)

    fun canUseBulkActions(): Boolean = bulkController.canEnterSelection()

    fun bulkActionsAvailability(): KanbanBulkActionsAvailability = bulkController.availability()

    fun canSubmitBulkAction(action: KanbanBulkAction): Boolean = bulkController.canSubmit(action)

    fun canRetryFailedBulkAction(): Boolean = bulkController.canRetryFailed()

    fun canCheckUncertainBulkAction(): Boolean = bulkController.canCheckUncertain()

    fun bulkSelectionContainsRunning(): Boolean = bulkController.selectionContainsRunning()

    fun beginSelectingCards() = bulkController.beginSelection()

    fun toggleCardSelection(card: KanbanCardSummary) = bulkController.toggleSelection(card)

    fun clearCardSelection() = bulkController.clearSelection()

    fun dismissBulkActionSummary() = bulkController.dismissSummary()

    fun performBulkAction(
        action: KanbanBulkAction,
        confirmedRunningExit: Boolean = false,
        confirmedArchive: Boolean = false,
    ) = bulkController.perform(action, confirmedRunningExit, confirmedArchive)

    fun retryFailedBulkAction() = bulkController.retryFailed()

    fun checkUncertainBulkAction() = bulkController.checkUncertain()

    private fun cardInCurrentSnapshot(cardId: String): KanbanCardSummary? =
        mutableState.value.snapshot?.allCards()?.firstOrNull { it.cardId?.trim() == cardId }

    private fun replaceCardInCurrentSnapshot(card: KanbanCardSummary) {
        val current = mutableState.value
        val snapshot = current.snapshot ?: return
        mutableState.value = current.copy(
            snapshot = snapshot.replacingKanbanCard(card, current.filters.includeArchived),
        )
        markBoardActivity()
    }

    private fun removeCardFromCurrentSnapshot(cardId: String) {
        val current = mutableState.value
        val snapshot = current.snapshot ?: return
        mutableState.value = current.copy(
            snapshot = snapshot.copy(
                columns = snapshot.columns.orEmpty().map { column ->
                    column.copy(cards = column.cards.orEmpty().filterNot { it.cardId?.trim() == cardId })
                },
            ),
        )
        markBoardActivity()
    }

    private fun hasActiveWorkflowMutation(): Boolean =
        workflowController.state.value.activeCardIds.isNotEmpty()

    private fun boardMutationBlocksWrites(): Boolean =
        boardController.state.value.blocksWrites

    private fun dispatchBlocksWrites(): Boolean =
        dispatcherController.blocksBoardActions()

    private fun markBoardActivity() { boardActivityGeneration += 1 }

    private suspend fun reconcileBoardCollectionForDispatch(): Boolean = try {
        applyBoardsResponse(repository.boards())
        true
    } catch (error: Throwable) {
        if (isOfflineError(error)) mutableState.value = mutableState.value.copy(isOffline = true, refreshFailed = false)
        false
    }

    private suspend fun refreshBoardForDispatch(board: String): Boolean = try {
        if (mutableState.value.selectedBoardSlug != board) return false
        val snapshot = repository.boardSnapshot(board, mutableState.value.filters.request())
        val supplementary = loadSupplementary(board)
        if (mutableState.value.selectedBoardSlug != board) return false
        val current = mutableState.value
        mutableState.value = current.copy(
            snapshot = snapshot,
            stats = supplementary.first,
            assigneeHistory = supplementary.second,
            isRefreshing = false,
            refreshFailed = false,
            isOffline = false,
            detailRefreshRevision = current.detailRefreshRevision + 1,
        )
        bulkController.acknowledgeSnapshot(snapshot.allCards())
        markBoardActivity()
        true
    } catch (error: Throwable) {
        if (isOfflineError(error)) mutableState.value = mutableState.value.copy(isOffline = true, refreshFailed = false)
        false
    }

    private fun applyBoardsResponse(response: KanbanBoardsResponse) {
        markBoardActivity()
        val availableBoards = response.boards.orEmpty()
        val current = mutableState.value
        val selectedSlug = current.selectedBoardSlug
        val selectedWasRemoved = selectedSlug != null && availableBoards.none { it.slug?.trim() == selectedSlug }
        if (selectedWasRemoved) {
            loadGeneration += 1
            suspendLiveUpdates()
            workflowController.acknowledgeCanonicalBoardLoad(boardChanged = true)
            bulkController.resetForBoardChange()
            val removedName = current.selectedBoard?.name?.trim()?.takeIf(String::isNotEmpty) ?: selectedSlug
            mutableState.value = current.copy(
                boards = availableBoards,
                sharedActiveBoardSlug = response.current?.trim(),
                boardsReadOnly = response.readOnly,
                selectedBoardSlug = null,
                boardSelectionNotice = removedName,
                snapshot = null,
                stats = null,
                assigneeHistory = null,
                warnings = emptyList(),
                isRefreshing = false,
                refreshFailed = false,
                isOffline = false,
                liveUpdatesDelayed = false,
            )
            return
        }
        mutableState.value = current.copy(
            boards = availableBoards,
            sharedActiveBoardSlug = response.current?.trim(),
            boardsReadOnly = response.readOnly,
            isOffline = false,
        )
    }

    private suspend fun refreshAfterBulkAction() {
        val requestState = mutableState.value
        val board = requestState.selectedBoardSlug ?: return
        mutableState.value = requestState.copy(isRefreshing = true, refreshFailed = false)
        try {
            val snapshot = repository.boardSnapshot(board, requestState.filters.request())
            val supplementary = loadSupplementary(board)
            val current = mutableState.value
            if (current.selectedBoardSlug != board) return
            val selectedBoard = current.selectedBoard ?: return
            val protectedSnapshot = workflowController.protectSnapshot(snapshot)
            val report = KanbanCompatibilityReport(
                configuration = current.configuration ?: return,
                boards = current.boards,
                currentBoard = selectedBoard,
                snapshot = protectedSnapshot,
                warnings = current.warnings,
            )
            bulkController.acknowledgeSnapshot(protectedSnapshot.allCards())
            mutableState.value = current.copy(
                snapshot = protectedSnapshot,
                stats = supplementary.first,
                assigneeHistory = supplementary.second,
                warnings = warningsFor(report, board, protectedSnapshot),
                isRefreshing = false,
                refreshFailed = false,
                isOffline = false,
                detailRefreshRevision = current.detailRefreshRevision + 1,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val current = mutableState.value
            if (current.selectedBoardSlug != board) return
            val offline = isOfflineError(error)
            mutableState.value = current.copy(
                isRefreshing = false,
                refreshFailed = !offline,
                isOffline = current.isOffline || offline,
            )
        }
    }

    private suspend fun loadSupplementary(slug: String): Pair<KanbanStats?, KanbanAssigneeHistory?> = coroutineScope {
        val stats = async { runCatching { repository.stats(slug) }.getOrNull() }
        val assignees = async { runCatching { repository.assignees(slug) }.getOrNull() }
        stats.await() to assignees.await()
    }

    private fun warningsFor(
        report: KanbanCompatibilityReport,
        selectedSlug: String,
        snapshot: KanbanBoardSnapshot,
    ): List<KanbanCompatibilityWarning> {
        val configured = report.configuration.columns.orEmpty()
            .mapNotNull { it.trim().lowercase(Locale.ROOT).takeIf(String::isNotEmpty) }
            .toSet()
        val selectedBoard = report.boards.firstOrNull { it.slug?.trim() == selectedSlug }
        return buildList {
            addAll(report.warnings.filterNot { it is KanbanCompatibilityWarning.UnsupportedStatus })
            if (selectedBoard?.readOnly == true || snapshot.readOnly == true) add(KanbanCompatibilityWarning.ReadOnly)
            if (selectedBoard?.readOnly == null || snapshot.readOnly == null) {
                add(KanbanCompatibilityWarning.WriteCapabilityUnavailable)
            }
            snapshot.columns.orEmpty().flatMap { column ->
                listOfNotNull(column.name?.trim()?.lowercase(Locale.ROOT)) +
                    column.cards.orEmpty().mapNotNull { it.status?.trim()?.lowercase(Locale.ROOT) }
            }.filterNot(configured::contains).distinct().forEach { status ->
                add(KanbanCompatibilityWarning.UnsupportedStatus(status))
            }
        }.distinct()
    }

    override fun onCleared() {
        suspendLiveUpdates()
        workflowController.reset()
        super.onCleared()
    }
}

internal fun kanbanAvailabilityFor(error: Throwable): KanbanAvailability = when (error) {
    ApiError.Unauthorized -> KanbanAvailability.AuthenticationRequired
    is ApiError.Network,
    is IOException,
    -> KanbanAvailability.NetworkUnavailable
    is ApiError.Http -> if (error.statusCode >= 500) {
        KanbanAvailability.ServerUnavailable
    } else {
        KanbanAvailability.IncompatibleContract
    }
    is KanbanContractViolation,
    is kotlinx.serialization.SerializationException,
    -> KanbanAvailability.IncompatibleContract
    else -> KanbanAvailability.ServerUnavailable
}

private fun isOfflineError(error: Throwable): Boolean = error is ApiError.Network || error is IOException

private data object MalformedKanbanLiveUpdate : IOException("The Kanban live update response was malformed.")

private fun String?.normalizedFilterValue(): String? = this?.trim()?.takeIf(String::isNotEmpty)
