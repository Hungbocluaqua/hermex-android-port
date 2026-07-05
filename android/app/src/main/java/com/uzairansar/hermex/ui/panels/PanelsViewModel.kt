package com.uzairansar.hermex.ui.panels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uzairansar.hermex.core.model.CronCreateRequest
import com.uzairansar.hermex.core.model.CronJob
import com.uzairansar.hermex.core.model.CronOutputResponse
import com.uzairansar.hermex.core.model.CronUpdateRequest
import com.uzairansar.hermex.core.model.InsightsResponse
import com.uzairansar.hermex.core.model.MemoryResponse
import com.uzairansar.hermex.core.model.SkillContentResponse
import com.uzairansar.hermex.core.model.SkillSummary
import com.uzairansar.hermex.data.repository.PanelsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
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

data class PanelsUiState(
    val isLoading: Boolean = true,
    val isMutating: Boolean = false,
    val crons: List<CronJob> = emptyList(),
    val taskDraft: CronTaskDraft? = null,
    val selectedCronOutput: CronOutputResponse? = null,
    val pendingDeleteCron: CronJob? = null,
    val skills: List<SkillSummary> = emptyList(),
    val selectedSkill: SkillContentResponse? = null,
    val memory: MemoryResponse? = null,
    val memorySection: String = "memory",
    val memoryDraft: String = "",
    val insights: InsightsResponse? = null,
    val notice: String? = null,
    val error: String? = null,
)

class PanelsViewModel(
    private val repository: PanelsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PanelsUiState())
    val state: StateFlow<PanelsUiState> = _state

    init {
        refresh()
    }

    fun refresh(clearNotice: Boolean = true) {
        viewModelScope.launch {
            val section = _state.value.memorySection
            _state.update { it.copy(isLoading = true, error = null, notice = if (clearNotice) null else it.notice) }
            runCatching {
                val memory = repository.memory()
                PanelsUiState(
                    isLoading = false,
                    crons = repository.crons(),
                    skills = repository.skills(),
                    memory = memory,
                    memorySection = section,
                    memoryDraft = memory.sectionText(section),
                    insights = runCatching { repository.insights() }.getOrNull(),
                    notice = if (clearNotice) null else _state.value.notice,
                )
            }.onSuccess { loaded ->
                _state.value = loaded
            }.onFailure { error ->
                _state.update { it.copy(isLoading = false, error = error.message ?: "Could not load panels.") }
            }
        }
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
        val id = job.stableId ?: return
        viewModelScope.launch {
            _state.update { it.copy(isMutating = true, error = null, notice = null) }
            runCatching { repository.cronOutput(id) }
                .onSuccess { output -> _state.update { it.copy(selectedCronOutput = output, isMutating = false) } }
                .onFailure { error -> _state.update { it.copy(isMutating = false, error = error.message ?: "Could not load task output.") } }
        }
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
            _state.update { it.copy(isMutating = true, error = null, notice = null) }
            runCatching { repository.skillContent(name) }
                .onSuccess { detail -> _state.update { it.copy(selectedSkill = detail, isMutating = false) } }
                .onFailure { error -> _state.update { it.copy(isMutating = false, error = error.message ?: "Could not load skill.") } }
        }
    }

    fun toggleSkill(skill: SkillSummary) {
        val name = skill.name ?: return
        val enable = skill.disabled == true || skill.enabled == false
        mutate(if (enable) "Skill enabled." else "Skill disabled.") {
            repository.toggleSkill(name, enable).error
        }
    }

    fun selectMemorySection(section: String) {
        val memory = _state.value.memory
        _state.update { it.copy(memorySection = section, memoryDraft = memory.sectionText(section), error = null, notice = null) }
    }

    fun updateMemoryDraft(value: String) {
        _state.update { it.copy(memoryDraft = value, error = null) }
    }

    fun saveMemory() {
        val section = _state.value.memorySection
        val content = _state.value.memoryDraft
        mutate("Memory saved.") { repository.writeMemory(section, content).error }
    }

    private fun mutate(success: String, action: suspend () -> String?) {
        viewModelScope.launch {
            _state.update { it.copy(isMutating = true, error = null, notice = null) }
            runCatching { action() }
                .onSuccess { error ->
                    if (error == null) {
                        _state.update { it.copy(isMutating = false, notice = success) }
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
        ?: schedule.scheduleExpression()
        ?: command
        ?: "manual"

val CronJob.isPaused: Boolean
    get() = paused == true || state == "paused" || enabled == false

val CronJob.editableScheduleText: String
    get() = schedule.scheduleExpression() ?: scheduleDisplay.orEmpty()

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

private fun JsonElement?.scheduleExpression(): String? {
    if (this == null) return null
    (this as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
    val obj = this as? JsonObject ?: return toString()
    return listOf("expression", "expr", "run_at", "runAt", "every", "kind")
        .firstNotNullOfOrNull { key ->
            (obj[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        }
}
