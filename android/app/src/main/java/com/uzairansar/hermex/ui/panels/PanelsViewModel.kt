package com.uzairansar.hermex.ui.panels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uzairansar.hermex.core.model.CronCreateRequest
import com.uzairansar.hermex.core.model.CronJob
import com.uzairansar.hermex.core.model.CronOutputResponse
import com.uzairansar.hermex.core.model.CronUpdateRequest
import com.uzairansar.hermex.core.model.InsightsResponse
import com.uzairansar.hermex.core.model.MemoryResponse
import com.uzairansar.hermex.core.model.SessionSummary
import com.uzairansar.hermex.core.model.SkillContentResponse
import com.uzairansar.hermex.core.model.SkillSummary
import com.uzairansar.hermex.data.repository.PanelsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class CronTaskDraft(
    val editingJobId: String? = null,
    val name: String = "",
    val prompt: String = "",
    val schedule: String = "",
    val deliver: String = "local",
    val skillsText: String = "",
    val model: String = "",
    val profile: String = "",
    val toastNotifications: Boolean = true,
) {
    val isEditing: Boolean get() = editingJobId != null
    val skills: List<String>
        get() = skillsText
            .split(',', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
}

enum class AnalyticsTimeframe(
    val title: String,
    val serverDays: Int,
) {
    Today("Today", 1),
    Last7Days("Last 7 Days", 7),
    Last30Days("Last 30 Days", 30),
    AllTime("All Time", 365),
}

data class PanelsUiState(
    val isLoading: Boolean = true,
    val isMutating: Boolean = false,
    val crons: List<CronJob> = emptyList(),
    val runningCrons: Map<String, Double> = emptyMap(),
    val taskDraft: CronTaskDraft? = null,
    val selectedCronDetail: CronJob? = null,
    val selectedCronOutput: CronOutputResponse? = null,
    val isLoadingCronOutput: Boolean = false,
    val pendingDeleteCron: CronJob? = null,
    val skills: List<SkillSummary> = emptyList(),
    val skillSearchText: String = "",
    val togglingSkillNames: Set<String> = emptySet(),
    val selectedSkillName: String? = null,
    val selectedSkill: SkillContentResponse? = null,
    val selectedSkillFileName: String? = null,
    val selectedSkillFileContent: String? = null,
    val isLoadingSkillFile: Boolean = false,
    val memory: MemoryResponse? = null,
    val memorySection: String = "memory",
    val memoryDraft: String = "",
    val editingMemorySection: String? = null,
    val insights: InsightsResponse? = null,
    val selectedInsightsTimeframe: AnalyticsTimeframe = AnalyticsTimeframe.Last30Days,
    val insightSessions: List<SessionSummary> = emptyList(),
    val insightsDataSource: InsightsDataSource = InsightsDataSource.Local,
    val insightsFallbackReason: String? = null,
    val refreshErrors: Map<String, String> = emptyMap(),
    val notice: String? = null,
    val error: String? = null,
)

class PanelsViewModel(
    private val repository: PanelsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PanelsUiState())
    val state: StateFlow<PanelsUiState> = _state
    private var refreshJob: Job? = null
    private var refreshGeneration: Long = 0

    init {
        refresh()
    }

    fun refresh(clearNotice: Boolean = true) {
        refreshJob?.cancel()
        val generation = ++refreshGeneration
        refreshJob = viewModelScope.launch {
            val snapshot = _state.value
            val selectedInsightsTimeframe = snapshot.selectedInsightsTimeframe
            _state.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    refreshErrors = emptyMap(),
                    notice = if (clearNotice) null else it.notice,
                )
            }
            try {
                coroutineScope {
                    launch {
                        try {
                            val memory = repository.memory()
                            if (refreshGeneration != generation) return@launch
                            _state.update { state ->
                                val currentSection = state.memorySection
                                state.copy(
                                    memory = memory,
                                    memoryDraft = if (state.editingMemorySection != null) {
                                        state.memoryDraft
                                    } else {
                                        memory.sectionText(currentSection)
                                    },
                                    refreshErrors = state.refreshErrors - "Memory",
                                )
                            }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Throwable) {
                            recordRefreshError("Memory", error, "Could not load memory.", generation)
                        }
                    }
                    launch {
                        try {
                            val crons = repository.crons()
                            if (refreshGeneration != generation) return@launch
                            _state.update { state ->
                                val sorted = crons.sortedForTasks(state.runningCrons)
                                val currentDetailId = state.selectedCronDetail?.stableId
                                state.copy(
                                    crons = sorted,
                                    selectedCronDetail = currentDetailId?.let { id ->
                                        sorted.firstOrNull { it.stableId == id }
                                    },
                                    refreshErrors = state.refreshErrors - "Tasks",
                                )
                            }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Throwable) {
                            recordRefreshError("Tasks", error, "Could not load tasks.", generation)
                        }
                    }
                    launch {
                        try {
                            val runningCrons = repository.cronStatus().runningJobDurations
                            if (refreshGeneration != generation) return@launch
                            _state.update { state ->
                                val sorted = state.crons.sortedForTasks(runningCrons)
                                val currentDetailId = state.selectedCronDetail?.stableId
                                state.copy(
                                    crons = sorted,
                                    runningCrons = runningCrons,
                                    selectedCronDetail = currentDetailId?.let { id ->
                                        sorted.firstOrNull { it.stableId == id }
                                    },
                                    refreshErrors = state.refreshErrors - "Task status",
                                )
                            }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Throwable) {
                            recordRefreshError("Task status", error, "Could not load running task status.", generation)
                        }
                    }
                    launch {
                        try {
                            val skills = repository.skills()
                            if (refreshGeneration != generation) return@launch
                            _state.update { it.copy(skills = skills, refreshErrors = it.refreshErrors - "Skills") }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Throwable) {
                            recordRefreshError("Skills", error, "Could not load skills.", generation)
                        }
                    }
                    launch {
                        try {
                            val insights = repository.insights(selectedInsightsTimeframe.serverDays)
                            if (refreshGeneration != generation) return@launch
                            _state.update {
                                it.copy(
                                    insights = insights,
                                    selectedInsightsTimeframe = selectedInsightsTimeframe,
                                    insightSessions = emptyList(),
                                    insightsDataSource = InsightsDataSource.Server,
                                    insightsFallbackReason = null,
                                    refreshErrors = it.refreshErrors - "Insights",
                                )
                            }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (insightsError: Throwable) {
                            try {
                                val sessions = repository.sessions()
                                if (refreshGeneration != generation) return@launch
                                _state.update {
                                    it.copy(
                                        insights = null,
                                        selectedInsightsTimeframe = selectedInsightsTimeframe,
                                        insightSessions = sessions,
                                        insightsDataSource = InsightsDataSource.LocalFallback,
                                        insightsFallbackReason = insightsError.message,
                                        refreshErrors = it.refreshErrors - "Insights",
                                    )
                                }
                            } catch (error: CancellationException) {
                                throw error
                            } catch (fallbackError: Throwable) {
                                recordRefreshError("Insights", fallbackError, "Could not load insights.", generation)
                                if (refreshGeneration == generation) {
                                    _state.update { it.copy(insightsFallbackReason = insightsError.message) }
                                }
                            }
                        }
                    }
                }
            } finally {
                if (refreshGeneration == generation) {
                    _state.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    private fun recordRefreshError(
        section: String,
        error: Throwable,
        fallback: String,
        generation: Long,
    ) {
        if (refreshGeneration != generation) return
        _state.update {
            it.copy(refreshErrors = it.refreshErrors + (section to (error.message ?: fallback)))
        }
    }

    fun selectInsightsTimeframe(timeframe: AnalyticsTimeframe) {
        if (_state.value.selectedInsightsTimeframe == timeframe) return
        _state.update {
            it.copy(
                selectedInsightsTimeframe = timeframe,
                insights = null,
                insightSessions = emptyList(),
                insightsDataSource = InsightsDataSource.Local,
                insightsFallbackReason = null,
                error = null,
                notice = null,
            )
        }
        refresh()
    }

    fun runCron(job: CronJob) {
        mutate("Task started.") {
            val id = job.stableId ?: return@mutate "Task id unavailable."
            repository.runCron(id).error
        }
    }

    fun pauseOrResumeCron(job: CronJob) {
        mutate(if (job.isPaused) "Task resumed." else "Task paused.") {
            val id = job.stableId ?: return@mutate "Task id unavailable."
            if (job.isPaused) repository.resumeCron(id).error else repository.pauseCron(id).error
        }
    }

    fun loadCronOutput(job: CronJob) {
        openCronDetail(job)
    }

    fun openCronDetail(job: CronJob) {
        val id = job.stableId ?: return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    selectedCronDetail = job,
                    selectedCronOutput = null,
                    isLoadingCronOutput = true,
                    error = null,
                    notice = null,
                )
            }
            runCatching { repository.cronOutput(id) }
                .onSuccess { output -> _state.update { it.copy(selectedCronOutput = output, isLoadingCronOutput = false) } }
                .onFailure { error -> _state.update { it.copy(isLoadingCronOutput = false, error = error.message ?: "Could not load task output.") } }
        }
    }

    fun refreshCronDetailOutput() {
        val job = _state.value.selectedCronDetail ?: return
        openCronDetail(job)
    }

    fun dismissCronDetail() {
        _state.update { it.copy(selectedCronDetail = null, selectedCronOutput = null, isLoadingCronOutput = false) }
    }

    fun openCreateTask() {
        _state.update { it.copy(taskDraft = CronTaskDraft(), error = null, notice = null) }
    }

    fun openEditTask(job: CronJob) {
        _state.update {
            it.copy(
                taskDraft = CronTaskDraft(
                    editingJobId = job.stableId,
                    name = job.name.orEmpty(),
                    prompt = job.prompt ?: job.command.orEmpty(),
                    schedule = job.editableScheduleText,
                    deliver = job.deliver ?: "local",
                    skillsText = job.skills.orEmpty().joinToString(", "),
                    model = job.model.orEmpty(),
                    profile = job.profile.orEmpty(),
                    toastNotifications = job.toastNotifications ?: true,
                ),
                error = null,
                notice = null,
            )
        }
    }

    fun updateTaskDraft(draft: CronTaskDraft) {
        _state.update { it.copy(taskDraft = draft, error = null) }
    }

    fun dismissTaskEditor() {
        _state.update { it.copy(taskDraft = null) }
    }

    fun saveTaskDraft() {
        val draft = _state.value.taskDraft ?: return
        val prompt = draft.prompt.trim()
        val schedule = draft.schedule.trim()
        if (prompt.isBlank()) {
            _state.update { it.copy(error = "Prompt is required.") }
            return
        }
        if (schedule.isBlank()) {
            _state.update { it.copy(error = "Schedule is required.") }
            return
        }
        mutate(if (draft.isEditing) "Task updated." else "Task created.") {
            val error = if (draft.isEditing) {
                repository.updateCron(
                    CronUpdateRequest(
                        jobId = draft.editingJobId ?: return@mutate "Task id unavailable.",
                        prompt = prompt,
                        schedule = schedule,
                        name = draft.name.trim().ifBlank { null },
                        deliver = draft.deliver.trim().ifBlank { null },
                        skills = draft.skills,
                        model = draft.model.trim().ifBlank { null },
                        profile = draft.profile.trim().ifBlank { null },
                        toastNotifications = draft.toastNotifications,
                    ),
                ).error
            } else {
                repository.createCron(
                    CronCreateRequest(
                        prompt = prompt,
                        schedule = schedule,
                        name = draft.name.trim().ifBlank { null },
                        deliver = draft.deliver.trim().ifBlank { null },
                        skills = draft.skills,
                        model = draft.model.trim().ifBlank { null },
                        profile = draft.profile.trim().ifBlank { null },
                        toastNotifications = draft.toastNotifications,
                    ),
                ).error
            }
            if (error == null) _state.update { it.copy(taskDraft = null) }
            error
        }
    }

    fun requestDeleteCron(job: CronJob) {
        _state.update { it.copy(pendingDeleteCron = job, error = null) }
    }

    fun dismissDeleteCron() {
        _state.update { it.copy(pendingDeleteCron = null) }
    }

    fun confirmDeleteCron() {
        val job = _state.value.pendingDeleteCron ?: return
        mutate("Task deleted.") {
            val id = job.stableId ?: return@mutate "Task id unavailable."
            repository.deleteCron(id).error
        }
        _state.update { it.copy(pendingDeleteCron = null) }
    }

    fun loadSkill(skill: SkillSummary) {
        val name = skill.name ?: return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isMutating = true,
                    error = null,
                    notice = null,
                    selectedSkillName = name,
                    selectedSkillFileName = null,
                    selectedSkillFileContent = null,
                    isLoadingSkillFile = false,
                )
            }
            runCatching { repository.skillContent(name) }
                .onSuccess { detail -> _state.update { it.copy(selectedSkill = detail, isMutating = false) } }
                .onFailure { error -> _state.update { it.copy(isMutating = false, error = error.message ?: "Could not load skill.") } }
        }
    }

    fun loadSkillLinkedFile(fileName: String) {
        val name = _state.value.selectedSkillName ?: _state.value.selectedSkill?.name ?: return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    selectedSkillFileName = fileName,
                    selectedSkillFileContent = null,
                    isLoadingSkillFile = true,
                    error = null,
                    notice = null,
                )
            }
            runCatching { repository.skillContent(name, fileName) }
                .onSuccess { detail ->
                    _state.update {
                        it.copy(
                            selectedSkillFileContent = detail.content ?: detail.error ?: "",
                            isLoadingSkillFile = false,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            selectedSkillFileContent = "Could not load file: ${error.message ?: "Unknown error"}",
                            isLoadingSkillFile = false,
                        )
                    }
                }
        }
    }

    fun dismissSkillLinkedFile() {
        _state.update {
            it.copy(
                selectedSkillFileName = null,
                selectedSkillFileContent = null,
                isLoadingSkillFile = false,
            )
        }
    }

    fun updateSkillSearchText(value: String) {
        _state.update { it.copy(skillSearchText = value, error = null) }
    }

    fun toggleSkill(skill: SkillSummary) {
        val name = skill.toggleSkillName ?: return
        if (!skill.canToggleSkill || name in _state.value.togglingSkillNames) return
        val enable = skill.disabled == true
        val optimisticDisabled = !enable
        viewModelScope.launch {
            _state.update {
                it.copy(
                    skills = it.skills.withSkillDisabled(name, optimisticDisabled),
                    togglingSkillNames = it.togglingSkillNames + name,
                    error = null,
                    notice = null,
                )
            }
            runCatching { repository.toggleSkill(name, enable) }
                .onSuccess { response ->
                    if (response.error == null) {
                        _state.update {
                            it.copy(
                                togglingSkillNames = it.togglingSkillNames - name,
                                notice = if (enable) "Skill enabled." else "Skill disabled.",
                            )
                        }
                        refresh(clearNotice = false)
                    } else {
                        _state.update {
                            it.copy(
                                skills = it.skills.withSkillDisabled(name, skill.disabled),
                                togglingSkillNames = it.togglingSkillNames - name,
                                error = response.error,
                            )
                        }
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            skills = it.skills.withSkillDisabled(name, skill.disabled),
                            togglingSkillNames = it.togglingSkillNames - name,
                            error = error.message ?: "Could not update skill.",
                        )
                    }
                }
        }
    }

    fun selectMemorySection(section: String) {
        val memory = _state.value.memory
        _state.update { it.copy(memorySection = section, memoryDraft = memory.sectionText(section), error = null, notice = null) }
    }

    fun openMemoryEditor(section: String) {
        val memory = _state.value.memory
        _state.update {
            it.copy(
                memorySection = section,
                memoryDraft = memory.sectionText(section),
                editingMemorySection = section,
                error = null,
                notice = null,
            )
        }
    }

    fun dismissMemoryEditor() {
        _state.update { it.copy(editingMemorySection = null) }
    }

    fun updateMemoryDraft(value: String) {
        _state.update { it.copy(memoryDraft = value, error = null) }
    }

    fun saveMemory() {
        val section = _state.value.memorySection
        val content = _state.value.memoryDraft
        mutate(
            success = "Memory saved.",
            afterSuccess = { _state.update { it.copy(editingMemorySection = null) } },
        ) {
            repository.writeMemory(section, content).error
        }
    }

    private fun mutate(
        success: String,
        afterSuccess: () -> Unit = {},
        action: suspend () -> String?,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(isMutating = true, error = null, notice = null) }
            runCatching { action() }
                .onSuccess { error ->
                    if (error == null) {
                        _state.update { it.copy(isMutating = false, notice = success) }
                        afterSuccess()
                        refresh(clearNotice = false)
                    } else {
                        _state.update { it.copy(isMutating = false, error = error) }
                    }
                }
                .onFailure { error -> _state.update { it.copy(isMutating = false, error = error.message ?: "Panel action failed.") } }
        }
    }
}

val CronJob.stableId: String?
    get() = jobId ?: id ?: name

val CronJob.displayName: String
    get() = name?.takeIf { it.isNotBlank() }
        ?: prompt?.lineSequence()?.firstOrNull()?.take(60)
        ?: stableId
        ?: "Task"

val CronJob.scheduleText: String
    get() = scheduleDisplay
        ?: schedule?.displayText
        ?: command
        ?: "manual"

val CronJob.isPaused: Boolean
    get() = paused == true || state.equals("paused", ignoreCase = true)

val CronJob.editableScheduleText: String
    get() = schedule?.displayText ?: scheduleDisplay.orEmpty()

fun Map<String, Double>.runningElapsedFor(job: CronJob): Double? =
    job.jobId?.let { this[it] } ?: job.id?.let { this[it] }

val SkillSummary.toggleSkillName: String?
    get() = name?.trim()?.takeIf { it.isNotEmpty() }

val SkillSummary.canToggleSkill: Boolean
    get() = disabled != null && toggleSkillName != null

private fun MemoryResponse?.sectionText(section: String): String {
    if (this == null) return ""
    return when (section) {
        "user" -> user.orEmpty()
        "soul" -> soul.orEmpty()
        else -> memory.plainText().orEmpty()
    }
}

private fun JsonElement?.plainText(): String? {
    if (this == null) return null
    return (this as? JsonPrimitive)?.contentOrNull ?: toString()
}

private fun List<SkillSummary>.withSkillDisabled(name: String, disabled: Boolean?): List<SkillSummary> =
    map { skill ->
        if (skill.toggleSkillName == name) {
            skill.copy(disabled = disabled, enabled = disabled?.not())
        } else {
            skill
        }
    }

private fun List<CronJob>.sortedForTasks(runningCrons: Map<String, Double>): List<CronJob> =
    sortedWith { left, right ->
        val leftRunning = runningCrons.runningElapsedFor(left) != null
        val rightRunning = runningCrons.runningElapsedFor(right) != null
        val leftNext = left.nextRunAt?.epochSeconds
        val rightNext = right.nextRunAt?.epochSeconds
        when {
            leftRunning && !rightRunning -> -1
            !leftRunning && rightRunning -> 1
            leftNext != null && rightNext != null -> leftNext.compareTo(rightNext)
            leftNext != null -> -1
            rightNext != null -> 1
            else -> String.CASE_INSENSITIVE_ORDER.compare(left.displayName, right.displayName)
        }
    }
