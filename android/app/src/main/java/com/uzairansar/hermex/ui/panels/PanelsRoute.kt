package com.uzairansar.hermex.ui.panels

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uzairansar.hermex.core.model.CronJob
import com.uzairansar.hermex.core.model.SkillSummary
import com.uzairansar.hermex.data.repository.PanelsRepository

@Composable
fun PanelsRoute(
    panelsRepository: PanelsRepository,
    onBack: () -> Unit,
) {
    val viewModel: PanelsViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return PanelsViewModel(panelsRepository) as T
        }
    })
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = onBack) { Text("Back") }
            Button(onClick = viewModel::refresh) { Text("Refresh") }
        }
        Spacer(Modifier.height(12.dp))
        Text("Server Panels", style = MaterialTheme.typography.headlineMedium)
        state.notice?.let { Text(it, color = MaterialTheme.colorScheme.tertiary) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(12.dp))
        if (state.isLoading) {
            CircularProgressIndicator()
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    PanelCard("Insights") {
                        val insights = state.insights
                        if (insights == null) {
                            Text("Insights unavailable.")
                        } else {
                            Text("${insights.periodDays ?: 30} days - ${insights.totalSessions ?: 0} sessions - ${insights.totalMessages ?: 0} messages")
                            Text("${insights.totalTokens ?: 0} tokens${insights.totalCost?.let { " - $${"%.4f".format(it)}" }.orEmpty()}")
                            insights.models.orEmpty().take(4).forEach { model ->
                                Text("${model.model ?: "model"} - ${model.totalTokens ?: 0} tokens", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                item {
                    PanelCard("Tasks") {
                        Button(onClick = viewModel::openCreateTask, enabled = !state.isMutating) {
                            Text("New Task")
                        }
                        Spacer(Modifier.height(8.dp))
                        if (state.crons.isEmpty()) {
                            Text("No scheduled tasks.")
                        } else {
                            state.crons.take(12).forEach { job ->
                                CronRow(
                                    job = job,
                                    isMutating = state.isMutating,
                                    onEdit = { viewModel.openEditTask(job) },
                                    onRun = { viewModel.runCron(job) },
                                    onPauseResume = { viewModel.pauseOrResumeCron(job) },
                                    onOutput = { viewModel.loadCronOutput(job) },
                                    onDelete = { viewModel.requestDeleteCron(job) },
                                )
                            }
                        }
                    }
                }
                state.selectedCronOutput?.let { output ->
                    item {
                        PanelCard("Task Output") {
                            if (output.outputs.isNullOrEmpty()) {
                                Text(output.error ?: "No recent output.")
                            } else {
                                output.outputs.take(3).forEach { item ->
                                    Text(item.filename ?: "output", fontWeight = FontWeight.SemiBold)
                                    Text(item.content ?: "", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                }
                item {
                    PanelCard("Skills") {
                        if (state.skills.isEmpty()) {
                            Text("No skills found.")
                        } else {
                            state.skills.take(20).forEach { skill ->
                                SkillRow(
                                    skill = skill,
                                    isMutating = state.isMutating,
                                    onOpen = { viewModel.loadSkill(skill) },
                                    onToggle = { viewModel.toggleSkill(skill) },
                                )
                            }
                        }
                    }
                }
                state.selectedSkill?.let { skill ->
                    item {
                        PanelCard(skill.name ?: "Skill") {
                            Text(skill.content ?: skill.error ?: "No content.", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item {
                    PanelCard("Memory") {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf("memory", "user", "soul").forEach { section ->
                                AssistChip(
                                    onClick = { viewModel.selectMemorySection(section) },
                                    label = { Text(if (state.memorySection == section) "$section selected" else section) },
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.memoryDraft,
                            onValueChange = viewModel::updateMemoryDraft,
                            label = { Text(state.memorySection) },
                            minLines = 4,
                            maxLines = 8,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isMutating,
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = viewModel::saveMemory, enabled = !state.isMutating) {
                            Text("Save")
                        }
                        state.memory?.projectContext?.let {
                            Spacer(Modifier.height(8.dp))
                            Text("Project: ${state.memory?.projectContextName ?: "context"}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }

    state.pendingDeleteCron?.let { job ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteCron,
            title = { Text("Delete task?") },
            text = { Text("Delete ${job.displayName}? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDeleteCron, enabled = !state.isMutating) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeleteCron) { Text("Cancel") }
            },
        )
    }

    state.taskDraft?.let { draft ->
        TaskEditorDialog(
            draft = draft,
            isMutating = state.isMutating,
            onDraftChange = viewModel::updateTaskDraft,
            onDismiss = viewModel::dismissTaskEditor,
            onSave = viewModel::saveTaskDraft,
        )
    }
}

@Composable
private fun CronRow(
    job: CronJob,
    isMutating: Boolean,
    onEdit: () -> Unit,
    onRun: () -> Unit,
    onPauseResume: () -> Unit,
    onOutput: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(job.displayName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(job.scheduleText, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssistChip(onClick = onEdit, enabled = !isMutating, label = { Text("Edit") })
            AssistChip(onClick = onRun, enabled = !isMutating, label = { Text("Run") })
            AssistChip(onClick = onPauseResume, enabled = !isMutating, label = { Text(if (job.isPaused) "Resume" else "Pause") })
            AssistChip(onClick = onOutput, enabled = !isMutating, label = { Text("Output") })
            AssistChip(onClick = onDelete, enabled = !isMutating, label = { Text("Delete") })
        }
    }
}

@Composable
private fun TaskEditorDialog(
    draft: CronTaskDraft,
    isMutating: Boolean,
    onDraftChange: (CronTaskDraft) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (draft.isEditing) "Edit Task" else "New Task") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { onDraftChange(draft.copy(name = it)) },
                    label = { Text("Name") },
                    singleLine = true,
                    enabled = !isMutating,
                )
                OutlinedTextField(
                    value = draft.prompt,
                    onValueChange = { onDraftChange(draft.copy(prompt = it)) },
                    label = { Text("Prompt") },
                    minLines = 3,
                    maxLines = 6,
                    enabled = !isMutating,
                )
                OutlinedTextField(
                    value = draft.schedule,
                    onValueChange = { onDraftChange(draft.copy(schedule = it)) },
                    label = { Text("Schedule") },
                    singleLine = true,
                    enabled = !isMutating,
                )
                OutlinedTextField(
                    value = draft.deliver,
                    onValueChange = { onDraftChange(draft.copy(deliver = it)) },
                    label = { Text("Deliver") },
                    singleLine = true,
                    enabled = !isMutating,
                )
                OutlinedTextField(
                    value = draft.skillsText,
                    onValueChange = { onDraftChange(draft.copy(skillsText = it)) },
                    label = { Text("Skills") },
                    minLines = 1,
                    maxLines = 3,
                    enabled = !isMutating,
                )
                OutlinedTextField(
                    value = draft.model,
                    onValueChange = { onDraftChange(draft.copy(model = it)) },
                    label = { Text("Model") },
                    singleLine = true,
                    enabled = !isMutating,
                )
                OutlinedTextField(
                    value = draft.profile,
                    onValueChange = { onDraftChange(draft.copy(profile = it)) },
                    label = { Text("Profile") },
                    singleLine = true,
                    enabled = !isMutating,
                )
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(
                        checked = draft.toastNotifications,
                        onCheckedChange = { onDraftChange(draft.copy(toastNotifications = it)) },
                        enabled = !isMutating,
                    )
                    Text("Toast notifications")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSave,
                enabled = !isMutating && draft.prompt.isNotBlank() && draft.schedule.isNotBlank(),
            ) {
                Text(if (draft.isEditing) "Save" else "Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun SkillRow(
    skill: SkillSummary,
    isMutating: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(skill.name ?: "Skill", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            text = listOfNotNull(skill.category, skill.description).joinToString(" - ").ifBlank { "No description." },
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = onOpen, enabled = !isMutating, label = { Text("Open") })
            AssistChip(
                onClick = onToggle,
                enabled = !isMutating,
                label = { Text(if (skill.disabled == true || skill.enabled == false) "Enable" else "Disable") },
            )
        }
    }
}

@Composable
private fun PanelCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}
