package com.uzairansar.hermex.ui.panels

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uzairansar.hermex.core.model.CronJob
import com.uzairansar.hermex.core.model.CronOutputResponse
import com.uzairansar.hermex.core.model.CronDeliveryPlatform
import com.uzairansar.hermex.core.model.InsightsActivityByDay
import com.uzairansar.hermex.core.model.InsightsActivityByHour
import com.uzairansar.hermex.core.model.InsightsDailyToken
import com.uzairansar.hermex.core.model.InsightsModelBreakdown
import com.uzairansar.hermex.core.model.InsightsResponse
import com.uzairansar.hermex.core.model.MemoryResponse
import com.uzairansar.hermex.core.model.SessionSummary
import com.uzairansar.hermex.core.model.SkillContentResponse
import com.uzairansar.hermex.core.model.SkillSummary
import com.uzairansar.hermex.data.repository.PanelsRepository
import com.uzairansar.hermex.ui.chat.MarkdownText
import com.uzairansar.hermex.ui.chat.markdownPlainTextChunksForLargeContent
import com.uzairansar.hermex.ui.theme.HermexCardShape
import com.uzairansar.hermex.ui.theme.HermexGlassShape
import com.uzairansar.hermex.ui.theme.HermexIconButton
import com.uzairansar.hermex.ui.theme.HermexPillShape
import com.uzairansar.hermex.ui.theme.HermexPillButton
import com.uzairansar.hermex.ui.theme.HermexSurfaceLevel
import com.uzairansar.hermex.ui.theme.hermexGlass
import com.uzairansar.hermex.R
import com.uzairansar.hermex.core.model.CronDateValue
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import com.uzairansar.hermex.ui.localization.localizedString
import com.uzairansar.hermex.ui.localization.localizedStringFormat

private val PanelPrimaryText: Color
    @Composable get() = MaterialTheme.colorScheme.onSurface

private val PanelSecondaryText: Color
    @Composable get() = MaterialTheme.colorScheme.secondary

@Composable
fun PanelsRoute(
    panelsRepository: PanelsRepository,
    initialSection: String? = null,
    onBack: () -> Unit,
) {
    val viewModel: PanelsViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return PanelsViewModel(panelsRepository) as T
        }

        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            @Suppress("UNCHECKED_CAST")
            return PanelsViewModel(panelsRepository, extras.createSavedStateHandle()) as T
        }
    })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusedSection = initialSection.normalizedPanelSection()
    val headerTitle = focusedSection?.panelTitle() ?: "Server Panels"
    val headerSubtitle = focusedSection?.let { "Server Panels" } ?: "Insights, tasks, skills, and memory"

    LaunchedEffect(viewModel) {
        viewModel.refresh()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            PanelsHeader(
                title = headerTitle,
                subtitle = headerSubtitle,
                focusedSection = focusedSection,
                onBack = onBack,
                onRefresh = viewModel::refresh,
                onCreateTask = viewModel::openCreateTask,
                createTaskEnabled = !state.isMutating,
            )
            state.notice?.let { Text(localizedString(it), color = PanelSecondaryText, style = MaterialTheme.typography.bodySmall) }
            state.error?.let { Text(localizedString(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            if (state.isLoading) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 6.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Text(localizedString("Refreshing panels"), color = PanelSecondaryText, style = MaterialTheme.typography.bodySmall)
                }
            }
            state.refreshErrors.toSortedMap().forEach { (section, message) ->
                Text(
                    "$section: $message",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                    if (focusedSection.shouldShowPanel("insights")) {
                        item {
                            if (focusedSection == "insights") {
                                FocusedInsightsPanel(
                                    insights = state.insights,
                                    selectedTimeframe = state.selectedInsightsTimeframe,
                                    sessions = state.insightSessions,
                                    dataSource = state.insightsDataSource,
                                    fallbackReason = state.insightsFallbackReason,
                                    onTimeframeSelected = viewModel::selectInsightsTimeframe,
                                )
                            } else PanelCard("Insights") {
                                InsightsPanel(
                                    insights = state.insights,
                                    selectedTimeframe = state.selectedInsightsTimeframe,
                                    sessions = state.insightSessions,
                                    dataSource = state.insightsDataSource,
                                    fallbackReason = state.insightsFallbackReason,
                                    onTimeframeSelected = viewModel::selectInsightsTimeframe,
                                )
                            }
                        }
                    }
                    if (focusedSection.shouldShowPanel("tasks")) {
                        if (focusedSection == "tasks") {
                            item {
                                RunningTasksCard(
                                    count = state.crons.count { job ->
                                        state.runningCrons.runningElapsedFor(job) != null || job.running == true
                                    },
                                )
                            }
                            item { PanelSectionLabel(localizedString("Scheduled Jobs")) }
                            if (state.crons.isEmpty()) {
                                item { PanelEmptyCard(localizedString("No scheduled tasks.")) }
                            } else {
                                state.crons.forEach { job ->
                                    item {
                                        FocusedTaskCard(
                                            job = job,
                                            runningElapsed = state.runningCrons.runningElapsedFor(job),
                                            isMutating = state.isMutating,
                                            onDetails = { viewModel.openCronDetail(job) },
                                            onEdit = { viewModel.openEditTask(job) },
                                            onRun = { viewModel.runCron(job) },
                                            onPauseResume = { viewModel.pauseOrResumeCron(job) },
                                            onOutput = { viewModel.openCronDetail(job) },
                                            onDelete = { viewModel.requestDeleteCron(job) },
                                        )
                                    }
                                }
                            }
                        } else {
                            item {
                                PanelCard("Tasks") {
                                    HermexPillButton(localizedString("New Task"), viewModel::openCreateTask, enabled = !state.isMutating, filled = true)
                                    Spacer(Modifier.height(8.dp))
                                    if (state.crons.isEmpty()) {
                                        Text(localizedString("No scheduled tasks."))
                                    } else {
                                        state.crons.forEach { job ->
                                            CronRow(
                                                job = job,
                                                runningElapsed = state.runningCrons.runningElapsedFor(job),
                                                isMutating = state.isMutating,
                                                onDetails = { viewModel.openCronDetail(job) },
                                                onEdit = { viewModel.openEditTask(job) },
                                                onRun = { viewModel.runCron(job) },
                                                onPauseResume = { viewModel.pauseOrResumeCron(job) },
                                                onOutput = { viewModel.openCronDetail(job) },
                                                onDelete = { viewModel.requestDeleteCron(job) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (focusedSection.shouldShowPanel("tasks")) {
                        state.selectedCronOutput?.takeIf { state.selectedCronDetail == null }?.let { output ->
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
                    }
                    if (focusedSection.shouldShowPanel("skills")) {
                        if (focusedSection == "skills") {
                            item {
                                SkillSearchField(
                                    value = state.skillSearchText,
                                    onValueChange = viewModel::updateSkillSearchText,
                                )
                            }
                            when {
                                state.skills.isEmpty() -> item {
                                    PanelEmptyCard(localizedString("Skills from the Hermes server will appear here."), title = "No Skills")
                                }
                                state.skillSearchText.isNotBlank() && state.filteredSkillGroups.isEmpty() -> item {
                                    PanelEmptyCard(
                                        message = localizedStringFormat("No skills match \"%@\".", state.skillSearchText.trim()),
                                        title = "No Results",
                                    )
                                }
                                else -> state.filteredSkillGroups.forEach { group ->
                                    item {
                                        SkillCategorySection(
                                            group = group,
                                            isMutating = state.isMutating,
                                            togglingSkillNames = state.togglingSkillNames,
                                            compact = true,
                                            onOpen = viewModel::loadSkill,
                                            onToggle = viewModel::toggleSkill,
                                        )
                                    }
                                }
                            }
                        } else {
                            item {
                                PanelCard("Skills") {
                                    SkillSearchField(
                                        value = state.skillSearchText,
                                        onValueChange = viewModel::updateSkillSearchText,
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    if (state.skills.isEmpty()) {
                                        Text(localizedString("No Skills"), fontWeight = FontWeight.SemiBold)
                                        Text(localizedString("Skills from the Hermes server will appear here."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                                    } else if (state.skillSearchText.isNotBlank() && state.filteredSkillGroups.isEmpty()) {
                                        Text(localizedString("No Results"), fontWeight = FontWeight.SemiBold)
                                        Text(
                                            localizedStringFormat("No skills match \"%@\".", state.skillSearchText.trim()),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.secondary,
                                        )
                                    } else {
                                        state.filteredSkillGroups.forEach { group ->
                                            SkillCategorySection(
                                                group = group,
                                                isMutating = state.isMutating,
                                                togglingSkillNames = state.togglingSkillNames,
                                                onOpen = viewModel::loadSkill,
                                                onToggle = viewModel::toggleSkill,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (focusedSection != "skills" && focusedSection.shouldShowPanel("skills")) {
                        state.selectedSkill?.let { skill ->
                            item {
                                SkillDetailPanel(
                                    skill = skill,
                                    isLoadingFile = state.isLoadingSkillFile,
                                    onOpenLinkedFile = viewModel::loadSkillLinkedFile,
                                )
                            }
                        }
                    }
                    if (focusedSection.shouldShowPanel("memory")) {
                        item {
                            if (focusedSection == "memory") {
                                FocusedMemoryPanel(
                                    memory = state.memory,
                                    isSaving = state.isMutating,
                                    onEditSection = viewModel::openMemoryEditor,
                                )
                            } else PanelCard("Memory") {
                                MemoryPanel(
                                    memory = state.memory,
                                    isSaving = state.isMutating,
                                    onEditSection = viewModel::openMemoryEditor,
                                )
                            }
                        }
                    }
            }
        }
    }

    state.pendingDeleteCron?.let { job ->
        AlertDialog(
            modifier = Modifier.panelDialogChrome(),
            shape = HermexGlassShape,
            containerColor = Color.Transparent,
            onDismissRequest = viewModel::dismissDeleteCron,
            title = { Text(localizedString("Delete task?")) },
            text = { Text(job.displayName) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDeleteCron, enabled = !state.isMutating) { Text(localizedString("Delete")) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeleteCron) { Text(localizedString("Cancel")) }
            },
        )
    }

    state.taskDraft?.let { draft ->
        TaskEditorDialog(
            draft = draft,
            deliveryOptions = state.cronDeliveryOptions,
            isMutating = state.isMutating,
            onDraftChange = viewModel::updateTaskDraft,
            onDismiss = viewModel::dismissTaskEditor,
            onSave = viewModel::saveTaskDraft,
        )
    }

    state.selectedCronDetail?.let { job ->
        TaskDetailSheet(
            job = job,
            runningElapsed = state.runningCrons.runningElapsedFor(job),
            output = state.selectedCronOutput,
            isLoadingOutput = state.isLoadingCronOutput,
            isMutating = state.isMutating,
            onDismiss = viewModel::dismissCronDetail,
            onRefreshOutput = viewModel::refreshCronDetailOutput,
            onRun = { viewModel.runCron(job) },
            onPauseResume = { viewModel.pauseOrResumeCron(job) },
            onEdit = {
                viewModel.dismissCronDetail()
                viewModel.openEditTask(job)
            },
            onDelete = { viewModel.requestDeleteCron(job) },
        )
    }

    state.selectedSkillFileName?.let { fileName ->
        SkillLinkedFileDialog(
            fileName = fileName,
            content = state.selectedSkillFileContent,
            isLoading = state.isLoadingSkillFile,
            onDismiss = viewModel::dismissSkillLinkedFile,
        )
    }

    state.editingMemorySection?.let { section ->
        MemoryEditSheet(
            section = memoryPanelSections.firstOrNull { it.key == section } ?: memoryPanelSections.first(),
            draft = state.memoryDraft,
            isSaving = state.isMutating,
            onDraftChange = viewModel::updateMemoryDraft,
            onDismiss = viewModel::dismissMemoryEditor,
            onSave = viewModel::saveMemory,
        )
    }

    if (focusedSection == "skills") {
        state.selectedSkill?.let { skill ->
            SkillDetailSheet(
                skill = skill,
                isLoadingFile = state.isLoadingSkillFile,
                onOpenLinkedFile = viewModel::loadSkillLinkedFile,
                onDismiss = viewModel::dismissSkillDetail,
            )
        }
    }
}

@Composable
private fun FocusedInsightsPanel(
    insights: InsightsResponse?,
    selectedTimeframe: AnalyticsTimeframe,
    sessions: List<SessionSummary>,
    dataSource: InsightsDataSource,
    fallbackReason: String?,
    onTimeframeSelected: (AnalyticsTimeframe) -> Unit,
) {
    val local = sessions.analyticsFor(selectedTimeframe)
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .hermexGlass(shape = HermexPillShape, castsShadow = false)
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            AnalyticsTimeframe.entries.forEach { timeframe ->
                val selected = timeframe == selectedTimeframe
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(HermexPillShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
                            else Color.Transparent,
                        )
                        .semantics {
                            role = Role.Tab
                            this.selected = selected
                        }
                        .clickable { onTimeframeSelected(timeframe) }
                        .padding(horizontal = 4.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        timeframe.title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) PanelPrimaryText else PanelSecondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        PanelSectionLabel((insights?.periodTitle(selectedTimeframe) ?: selectedTimeframe.title).uppercase())
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .hermexGlass(shape = HermexCardShape, castsShadow = false)
                .padding(horizontal = 18.dp),
        ) {
            listOf(
                AnalyticsMetric("Sessions", formattedTokens(insights?.totalSessions ?: local.sessionCount), "S", Color(0xFF0A84FF)),
                AnalyticsMetric("Messages", formattedTokens(insights?.totalMessages ?: local.totalMessages), "M", Color(0xFF32D7F4)),
                AnalyticsMetric("Input Tokens", formattedTokens(insights?.totalInputTokens ?: local.totalInputTokens), "↓", Color(0xFF30D158)),
                AnalyticsMetric("Output Tokens", formattedTokens(insights?.totalOutputTokens ?: local.totalOutputTokens), "↑", Color(0xFFFF9F0A)),
                AnalyticsMetric("Total Tokens", formattedTokens(insights?.totalTokens ?: local.totalTokens), "Σ", Color(0xFFBF5AF2)),
                AnalyticsMetric("Estimated Cost", formattedCost(insights?.totalCost ?: local.estimatedCost), "$", Color(0xFF5E5CE6)),
            ).forEachIndexed { index, metric ->
                FocusedAnalyticsMetricRow(metric)
                if (index < 5) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f))
            }
        }
        if (!insights?.models.isNullOrEmpty()) {
            PanelSectionLabel(localizedString("Models"))
            Column(
                modifier = Modifier.fillMaxWidth().hermexGlass(shape = HermexCardShape, castsShadow = false).padding(horizontal = 18.dp),
            ) {
                insights.models.orEmpty().take(10).forEachIndexed { index, model ->
                    ModelBreakdownPanelRow(model)
                    if (index < insights.models.orEmpty().take(10).lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f))
                }
            }
        }
        if (!insights?.dailyTokens.isNullOrEmpty()) {
            PanelSectionLabel(localizedString("Recent Daily Tokens"))
            Column(
                modifier = Modifier.fillMaxWidth().hermexGlass(shape = HermexCardShape, castsShadow = false).padding(horizontal = 18.dp),
            ) {
                insights.dailyTokens.orEmpty().takeLast(14).forEachIndexed { index, day ->
                    DailyTokenPanelRow(day)
                    if (index < insights.dailyTokens.orEmpty().takeLast(14).lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f))
                }
            }
        }
        val peakDay = insights?.activityByDay.orEmpty().maxByOrNull { it.sessions ?: 0 }
        val peakHour = insights?.activityByHour.orEmpty().maxByOrNull { it.sessions ?: 0 }
        if (peakDay != null || peakHour != null) {
            PanelSectionLabel(localizedString("Activity"))
            Column(
                modifier = Modifier.fillMaxWidth().hermexGlass(shape = HermexCardShape, castsShadow = false).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                peakDay?.let { ActivityPanelRow(localizedString("Peak Day"), it.day ?: "Unknown", "${it.sessions ?: 0} sessions") }
                peakHour?.let { ActivityPanelRow(localizedString("Peak Hour"), formatHour(it.hour), "${it.sessions ?: 0} sessions") }
            }
        }
        if (dataSource != InsightsDataSource.Server && local.topSessions.isNotEmpty()) {
            PanelSectionLabel(localizedString("Top Sessions"))
            Column(
                modifier = Modifier.fillMaxWidth().hermexGlass(shape = HermexCardShape, castsShadow = false).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                local.topSessions.take(10).forEach { TopSessionPanelRow(it) }
            }
        }
        Text(
            insightsSourceDescription(dataSource, insights?.periodDays ?: selectedTimeframe.serverDays, fallbackReason),
            style = MaterialTheme.typography.labelSmall,
            color = PanelSecondaryText,
        )
    }

}

private data class AnalyticsMetric(
    val title: String,
    val value: String,
    val symbol: String,
    val tint: Color,
)

@Composable
private fun FocusedAnalyticsMetricRow(metric: AnalyticsMetric) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 17.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(metric.tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(metric.symbol, color = metric.tint, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(metric.title, style = MaterialTheme.typography.titleMedium, color = PanelSecondaryText, fontWeight = FontWeight.SemiBold)
            Text(metric.value, style = MaterialTheme.typography.headlineSmall, color = PanelPrimaryText, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PanelsHeader(
    title: String,
    subtitle: String,
    focusedSection: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onCreateTask: () -> Unit,
    createTaskEnabled: Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(bottom = 18.dp),
    ) {
        HermexIconButton(
            label = localizedString("Back"),
            symbol = "<",
            onClick = onBack,
            modifier = Modifier.align(androidx.compose.ui.Alignment.CenterStart),
        )
        Column(
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = if (focusedSection == "tasks") 104.dp else 64.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = PanelPrimaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (focusedSection == null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = PanelSecondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (focusedSection == "tasks") {
            Row(
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.CenterEnd)
                    .hermexGlass(
                        shape = HermexPillShape,
                        castsShadow = false,
                        surfaceLevel = HermexSurfaceLevel.Raised,
                    )
                    .padding(2.dp),
            ) {
                PanelHeaderIconAction(
                    label = localizedString("New Task"),
                    iconRes = R.drawable.ic_hermex_plus,
                    onClick = onCreateTask,
                    enabled = createTaskEnabled,
                )
                PanelHeaderIconAction(
                    label = localizedString("Refresh"),
                    iconRes = R.drawable.ic_hermex_refresh,
                    onClick = onRefresh,
                )
            }
        } else {
            HermexIconButton(
                label = localizedString("Refresh"),
                symbol = "Refresh",
                onClick = onRefresh,
                modifier = Modifier.align(androidx.compose.ui.Alignment.CenterEnd),
            )
        }
    }
}

@Composable
private fun PanelHeaderIconAction(
    label: String,
    iconRes: Int,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(44.dp)
            .defaultMinSize(minWidth = 0.dp, minHeight = 0.dp),
        shape = androidx.compose.foundation.shape.CircleShape,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.textButtonColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = label,
            modifier = Modifier.size(20.dp),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
        )
    }
}

@Composable
private fun RunningTasksCard(count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .hermexGlass(
                shape = HermexGlassShape,
                castsShadow = false,
                surfaceLevel = HermexSurfaceLevel.Raised,
            )
            .padding(horizontal = 18.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_hermex_lightning_bolt),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                colorFilter = ColorFilter.tint(Color(0xFF0A84FF)),
            )
            Text(
                localizedString("Running now"),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = PanelPrimaryText,
            )
        }
        Text(
            count.toString(),
            style = MaterialTheme.typography.titleLarge,
            color = PanelSecondaryText,
        )
    }
}

@Composable
private fun PanelSectionLabel(title: String) {
    Text(
        localizedString(title),
        modifier = Modifier.padding(start = 8.dp, top = 8.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.secondary,
    )
}

@Composable
private fun PanelEmptyCard(
    message: String,
    title: String? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .hermexGlass(
                shape = HermexGlassShape,
                castsShadow = false,
                surfaceLevel = HermexSurfaceLevel.Base,
            )
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        title?.let {
            Text(localizedString(it), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        Text(localizedString(message), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun FocusedTaskCard(
    job: CronJob,
    runningElapsed: Double?,
    isMutating: Boolean,
    onDetails: () -> Unit,
    onEdit: () -> Unit,
    onRun: () -> Unit,
    onPauseResume: () -> Unit,
    onOutput: () -> Unit,
    onDelete: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .hermexGlass(
                shape = HermexGlassShape,
                castsShadow = false,
                surfaceLevel = HermexSurfaceLevel.Raised,
            )
            .clickable(enabled = !isMutating, onClick = onDetails)
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Column(Modifier.padding(end = 22.dp)) {
            CronRow(
                job = job,
                runningElapsed = runningElapsed,
                isMutating = isMutating,
                showInlineActions = false,
                onDetails = onDetails,
                onEdit = onEdit,
                onRun = onRun,
                onPauseResume = onPauseResume,
                onOutput = onOutput,
                onDelete = onDelete,
            )
        }
        Image(
            painter = painterResource(R.drawable.ic_hermex_chevron_right),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(17.dp),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.secondary.copy(alpha = 0.65f)),
        )
    }
}

@Composable
private fun SkillSearchField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(localizedString("Search skills...")) },
        leadingIcon = {
            Image(
                painter = painterResource(R.drawable.ic_hermex_search),
                contentDescription = null,
                modifier = Modifier.size(21.dp),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
            )
        },
        singleLine = true,
        shape = HermexPillShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            disabledBorderColor = Color.Transparent,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .hermexGlass(
                shape = HermexPillShape,
                castsShadow = false,
                surfaceLevel = HermexSurfaceLevel.Raised,
            ),
    )
}

@Composable
private fun Modifier.panelDialogChrome(): Modifier = hermexGlass(
    shape = HermexGlassShape,
    surfaceLevel = HermexSurfaceLevel.Floating,
    tintEnabled = false,
)

private data class SkillGroup(
    val category: String,
    val skills: List<SkillSummary>,
)

private val PanelsUiState.filteredSkillGroups: List<SkillGroup>
    get() {
        val query = skillSearchText.trim()
        val visibleSkills = if (query.isBlank()) {
            skills
        } else {
            skills.filter { it.matchesSkillQuery(query) }
        }
        return visibleSkills
            .groupBy { it.skillCategoryName }
            .toSortedMap(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
            .map { (category, categorySkills) ->
                SkillGroup(
                    category = category,
                    skills = categorySkills.sortedBy { it.skillDisplayName.lowercase() },
                )
            }
    }

private val SkillSummary.skillDisplayName: String
    get() = name?.trim()?.takeIf { it.isNotEmpty() } ?: "Unnamed Skill"

private val SkillSummary.skillCategoryName: String
    get() = category?.trim()?.takeIf { it.isNotEmpty() } ?: "Uncategorized"

private fun SkillSummary.matchesSkillQuery(query: String): Boolean =
    listOfNotNull(name, description, category).any { it.contains(query, ignoreCase = true) } ||
        tags.orEmpty().any { it.contains(query, ignoreCase = true) }

private fun String?.normalizedPanelSection(): String? =
    this?.lowercase()?.takeIf { it in setOf("tasks", "skills", "memory", "insights") }

private fun CronJob.statusLabel(runningElapsed: Double?): String =
    when {
        runningElapsed != null || running == true -> "Running"
        needsAttention -> "Needs Attention"
        isPaused -> "Paused"
        enabled == false -> "Off"
        lastStatus.equals("error", ignoreCase = true) ||
            state.equals("error", ignoreCase = true) ||
            state.equals("failed", ignoreCase = true) ||
            lastError?.isNotBlank() == true ||
            lastDeliveryError?.isNotBlank() == true -> "Error"
        else -> "Active"
    }

@Composable
private fun CronJob.statusColor(runningElapsed: Double?): Color =
    when (statusLabel(runningElapsed)) {
        "Running" -> MaterialTheme.colorScheme.primary
        "Active" -> Color(0xFF34C759)
        "Paused", "Off" -> Color(0xFFFF9500)
        "Error" -> MaterialTheme.colorScheme.error
        else -> Color(0xFFFFCC00)
    }

private val CronJob.needsAttention: Boolean
    get() {
        val recurring = schedule?.kind.equals("cron", ignoreCase = true) ||
            schedule?.kind.equals("interval", ignoreCase = true)
        val hasNextRun = nextRunAt?.epochSeconds != null
        return recurring &&
            ((repeat?.times == null && enabled == false && state.equals("completed", ignoreCase = true) && !hasNextRun) ||
                (!hasNextRun && (state.equals("error", ignoreCase = true) || lastStatus.equals("error", ignoreCase = true))))
    }

private fun String?.shouldShowPanel(panel: String): Boolean =
    this == null || this == panel

private fun String.panelTitle(): String =
    when (this) {
        "tasks" -> "Tasks"
        "skills" -> "Skills"
        "memory" -> "Memory"
        "insights" -> "Usage Analytics"
        else -> "Server Panels"
    }

@Composable
private fun CronRow(
    job: CronJob,
    runningElapsed: Double?,
    isMutating: Boolean,
    showInlineActions: Boolean = true,
    onDetails: () -> Unit,
    onEdit: () -> Unit,
    onRun: () -> Unit,
    onPauseResume: () -> Unit,
    onOutput: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.Top,
        ) {
            Text(
                job.displayName,
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.SemiBold,
                style = if (showInlineActions) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleLarge,
                color = PanelPrimaryText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            StatusBadge(
                text = localizedString(job.statusLabel(runningElapsed)),
                color = job.statusColor(runningElapsed),
            )
        }
        job.prompt?.takeIf { it.isNotBlank() }?.let { prompt ->
            Text(
                prompt,
                style = if (showInlineActions) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CronJobMetadataRow("Schedule", job.scheduleText)
            CronJobMetadataRow("Next", job.nextRunAt.panelDateText() ?: "Not available")
            CronJobMetadataRow("Last", job.lastRunAt.panelDateText() ?: "Never")
            runningElapsed?.let { CronJobMetadataRow("Elapsed", elapsedText(it)) }
            CronJobMetadataRow("Deliver", job.deliver?.takeIf { it.isNotBlank() } ?: "local")
            job.model?.takeIf { it.isNotBlank() }?.let { CronJobMetadataRow("Model", it) }
            job.profile?.takeIf { it.isNotBlank() }?.let { CronJobMetadataRow("Profile", it) }
            job.skills?.takeIf { it.isNotEmpty() }?.let { CronJobMetadataRow("Skills", it.joinToString(", ")) }
            (job.lastError ?: job.lastDeliveryError)?.takeIf { it.isNotBlank() }?.let { error ->
                CronJobMetadataRow("Error", error, isError = true)
            }
        }
        if (showInlineActions) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HermexPillButton(localizedString("Details"), onDetails, enabled = !isMutating, filled = true)
                HermexPillButton(localizedString("Edit"), onEdit, enabled = !isMutating)
                HermexPillButton(localizedString("Run"), onRun, enabled = !isMutating)
                HermexPillButton(localizedString(if (job.isPaused) "Resume" else "Pause"), onPauseResume, enabled = !isMutating)
                HermexPillButton(localizedString("Output"), onOutput, enabled = !isMutating)
                HermexPillButton(localizedString("Delete"), onDelete, enabled = !isMutating)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskDetailSheet(
    job: CronJob,
    runningElapsed: Double?,
    output: CronOutputResponse?,
    isLoadingOutput: Boolean,
    isMutating: Boolean,
    onDismiss: () -> Unit,
    onRefreshOutput: () -> Unit,
    onRun: () -> Unit,
    onPauseResume: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = Color.Transparent,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.96f)
                .hermexGlass(
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    castsShadow = false,
                    surfaceLevel = HermexSurfaceLevel.Floating,
                )
                .testTag("task_detail_sheet")
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = androidx.compose.ui.Alignment.Top,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        job.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    job.prompt?.takeIf { it.isNotBlank() }?.let { prompt ->
                        Text(
                            prompt,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                StatusBadge(localizedString(job.statusLabel(runningElapsed)), job.statusColor(runningElapsed))
                HermexIconButton(
                    label = localizedString("Close task details"),
                    symbol = "×",
                    onClick = onDismiss,
                    tonalContainerColor = Color.Transparent,
                    modifier = Modifier.size(40.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HermexPillButton(localizedString("Refresh"), onRefreshOutput, enabled = !isLoadingOutput)
                HermexPillButton(localizedString("Run Now"), onRun, enabled = !isMutating)
                HermexPillButton(localizedString(if (job.isPaused) "Resume" else "Pause"), onPauseResume, enabled = !isMutating)
                HermexPillButton(localizedString("Edit"), onEdit, enabled = !isMutating)
                HermexPillButton(localizedString("Delete"), onDelete, enabled = !isMutating)
                HermexPillButton(localizedString("Done"), onDismiss, enabled = true, filled = true)
            }

            if (isMutating) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        localizedString("Updating task..."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .testTag("task_detail_scroll"),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                PanelSubsection(localizedString("Details")) {
                    CronJobMetadataRow("Schedule", job.scheduleText)
                    CronJobMetadataRow("Next", job.nextRunAt.panelDateText() ?: "Not available")
                    CronJobMetadataRow("Last", job.lastRunAt.panelDateText() ?: "Never")
                    runningElapsed?.let { CronJobMetadataRow("Elapsed", elapsedText(it)) }
                    CronJobMetadataRow("Deliver", job.deliver?.takeIf { it.isNotBlank() } ?: "local")
                    job.model?.takeIf { it.isNotBlank() }?.let { CronJobMetadataRow("Model", it) }
                    job.provider?.takeIf { it.isNotBlank() }?.let { CronJobMetadataRow("Provider", it) }
                    job.profile?.takeIf { it.isNotBlank() }?.let { CronJobMetadataRow("Profile", it) }
                    job.toastNotifications?.let { CronJobMetadataRow("Toasts", if (it) "On" else "Off") }
                    job.skills?.takeIf { it.isNotEmpty() }?.let { CronJobMetadataRow("Skills", it.joinToString(", ")) }
                    (job.lastError ?: job.lastDeliveryError)?.takeIf { it.isNotBlank() }?.let { error ->
                        CronJobMetadataRow("Error", error, isError = true)
                    }
                }

                PanelSubsection(localizedString("Recent Output")) {
                    when {
                        isLoadingOutput -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(localizedString("Loading output..."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        }
                        output?.error?.isNotBlank() == true && output.outputs.isNullOrEmpty() -> Text(
                            output.error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        output?.outputs.isNullOrEmpty() -> Text(
                            localizedString("This task has not produced any output yet."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        else -> output.outputs.orEmpty().forEach { item ->
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(item.filename ?: "Untitled", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    item.content?.takeIf { it.isNotBlank() } ?: "Empty output",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f), HermexCardShape)
                                        .padding(10.dp),
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(
    text: String,
    color: Color,
) {
    Text(
        text,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), shape = androidx.compose.foundation.shape.CircleShape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
    )
}

@Composable
private fun CronJobMetadataRow(
    title: String,
    value: String,
    isError: Boolean = false,
) {
    val usesStackedLayout = LocalConfiguration.current.fontScale >= 1.3f
    val titleContent: @Composable () -> Unit = {
        Text(
            title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
    val valueContent: @Composable (Modifier) -> Unit = { modifier ->
        Text(
            value,
            modifier = modifier,
            style = MaterialTheme.typography.labelSmall,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            maxLines = if (usesStackedLayout) 4 else 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (usesStackedLayout) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            titleContent()
            valueContent(Modifier.fillMaxWidth())
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.Top,
        ) {
            Box(Modifier.width(64.dp)) { titleContent() }
            valueContent(Modifier.weight(1f))
        }
    }
}

private fun CronDateValue?.panelDateText(): String? {
    if (this == null) return null
    epochSeconds?.let {
        return cronDateFormatter.format(Instant.ofEpochMilli((it * 1000.0).toLong()))
    }
    return raw?.takeIf { it.isNotBlank() }
}

private fun elapsedText(elapsed: Double): String =
    if (elapsed < 60.0) {
        "${elapsed.toInt()}s"
    } else {
        val minutes = (elapsed / 60.0).toInt()
        val seconds = (elapsed % 60.0).toInt()
        "${minutes}m ${seconds}s"
    }

private val cronDateFormatter: DateTimeFormatter =
    DateTimeFormatter
        .ofPattern("d MMM yyyy 'at' HH:mm", Locale.ENGLISH)
        .withZone(ZoneId.systemDefault())

@Composable
private fun InsightsPanel(
    insights: InsightsResponse?,
    selectedTimeframe: AnalyticsTimeframe,
    sessions: List<SessionSummary>,
    dataSource: InsightsDataSource,
    fallbackReason: String?,
    onTimeframeSelected: (AnalyticsTimeframe) -> Unit,
) {
    val localAnalytics = sessions.analyticsFor(selectedTimeframe)
    val hasLoadedAnalytics = insights != null || dataSource == InsightsDataSource.LocalFallback
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnalyticsTimeframe.entries.forEach { timeframe ->
                HermexPillButton(
                    label = timeframe.title,
                    onClick = { onTimeframeSelected(timeframe) },
                    filled = timeframe == selectedTimeframe,
                )
            }
        }

        if (!hasLoadedAnalytics) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(localizedString("No Data"), fontWeight = FontWeight.SemiBold)
                Text(
                    localizedString("Session usage data will appear here once you have conversations."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        } else {
            Text(
                insights?.periodTitle(selectedTimeframe) ?: selectedTimeframe.title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.secondary,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AnalyticsMetricRow(localizedString("Sessions"), formattedTokens(insights?.totalSessions ?: localAnalytics.sessionCount))
                AnalyticsMetricRow(localizedString("Messages"), formattedTokens(insights?.totalMessages ?: localAnalytics.totalMessages))
                AnalyticsMetricRow(localizedString("Input Tokens"), formattedTokens(insights?.totalInputTokens ?: localAnalytics.totalInputTokens))
                AnalyticsMetricRow(localizedString("Output Tokens"), formattedTokens(insights?.totalOutputTokens ?: localAnalytics.totalOutputTokens))
                AnalyticsMetricRow(localizedString("Total Tokens"), formattedTokens(insights?.totalTokens ?: localAnalytics.totalTokens))
                AnalyticsMetricRow(localizedString("Estimated Cost"), formattedCost(insights?.totalCost ?: localAnalytics.estimatedCost))
                insights?.totalCacheHitPercent?.let { AnalyticsMetricRow(localizedString("Cache Hit Rate"), formattedPercent(it)) }
                insights?.totalCacheReadTokens?.let { AnalyticsMetricRow(localizedString("Cache Read Tokens"), formattedTokens(it)) }
            }

            if (!insights?.models.isNullOrEmpty()) {
                PanelSubsection(localizedString("Models")) {
                    insights.models.orEmpty().take(10).forEach { model ->
                        ModelBreakdownPanelRow(model)
                    }
                }
            }

            if (!insights?.dailyTokens.isNullOrEmpty()) {
                PanelSubsection(localizedString("Recent Daily Tokens")) {
                    insights.dailyTokens.orEmpty().takeLast(14).forEach { day ->
                        DailyTokenPanelRow(day)
                    }
                }
            }

            val peakDay = insights?.activityByDay.orEmpty().maxByOrNull { it.sessions ?: 0 }
            val peakHour = insights?.activityByHour.orEmpty().maxByOrNull { it.sessions ?: 0 }
            if (peakDay != null || peakHour != null) {
                PanelSubsection(localizedString("Activity")) {
                    peakDay?.let {
                        ActivityPanelRow(localizedString("Peak Day"), it.day ?: "Unknown", "${it.sessions ?: 0} sessions")
                    }
                    peakHour?.let {
                        ActivityPanelRow(localizedString("Peak Hour"), formatHour(it.hour), "${it.sessions ?: 0} sessions")
                    }
                }
            }

            if (dataSource != InsightsDataSource.Server && localAnalytics.topSessions.isNotEmpty()) {
                PanelSubsection(localizedString("Top Sessions")) {
                    localAnalytics.topSessions.take(10).forEach { session ->
                        TopSessionPanelRow(session)
                    }
                }
            }

            Text(
                insightsSourceDescription(
                    source = dataSource,
                    periodDays = insights?.periodDays ?: selectedTimeframe.serverDays,
                    fallbackReason = fallbackReason,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

private fun InsightsResponse.periodTitle(timeframe: AnalyticsTimeframe): String =
    if (timeframe == AnalyticsTimeframe.AllTime) {
        "Last ${periodDays ?: timeframe.serverDays} Days"
    } else {
        timeframe.title
    }

private fun insightsSourceDescription(
    source: InsightsDataSource,
    periodDays: Int,
    fallbackReason: String?,
): String =
    when (source) {
        InsightsDataSource.Server -> "Source: server insights from the last $periodDays days."
        InsightsDataSource.LocalFallback -> {
            val reason = fallbackReason?.takeIf { it.isNotBlank() }
            if (reason == null) {
                "Source: local session metadata fallback."
            } else {
                "Source: local session metadata fallback. Server insights failed: $reason"
            }
        }
        InsightsDataSource.Local -> "Source: local session metadata."
    }

@Composable
private fun AnalyticsMetricRow(title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(localizedString(title), style = MaterialTheme.typography.bodySmall, color = PanelSecondaryText)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = PanelPrimaryText)
    }
}

@Composable
private fun PanelSubsection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            localizedString(title).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.secondary,
        )
        content()
    }
}

@Composable
private fun ModelBreakdownPanelRow(model: InsightsModelBreakdown) {
    val totalTokens = model.totalTokens ?: ((model.inputTokens ?: 0) + (model.outputTokens ?: 0))
    val details = buildList {
        add("${formattedTokens(totalTokens)} tokens")
        add("${model.sessions ?: 0} sessions")
        model.cost?.takeIf { it > 0.0 }?.let { add(formattedCost(it)) }
        model.displayShare?.let { add("$it% share") }
        model.cacheHitPercent?.let { add("${formattedPercent(it)} cache") }
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            model.model ?: "Unknown Model",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = PanelPrimaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(details.joinToString("  "), style = MaterialTheme.typography.bodySmall, color = PanelSecondaryText)
    }
}

@Composable
private fun DailyTokenPanelRow(day: InsightsDailyToken) {
    val totalTokens = (day.inputTokens ?: 0) + (day.outputTokens ?: 0)
    val details = buildList {
        add("${formattedTokens(totalTokens)} tokens")
        add("${day.sessions ?: 0} sessions")
        day.cost?.takeIf { it > 0.0 }?.let { add(formattedCost(it)) }
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(day.date ?: "Unknown Date", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = PanelPrimaryText)
        Text(details.joinToString("  "), style = MaterialTheme.typography.bodySmall, color = PanelSecondaryText)
    }
}

@Composable
private fun TopSessionPanelRow(session: SessionSummary) {
    val input = session.inputTokens ?: 0
    val output = session.outputTokens ?: 0
    val total = input + output
    val details = buildList {
        add("${formattedTokens(total)} tokens")
        session.estimatedCost?.takeIf { it > 0.0 }?.let { add(formattedCost(it)) }
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            session.title?.takeIf { it.isNotBlank() } ?: "Untitled Session",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = PanelPrimaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(details.joinToString("  "), style = MaterialTheme.typography.labelSmall, color = PanelSecondaryText)
    }
}

@Composable
private fun ActivityPanelRow(title: String, value: String, detail: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(localizedString(title), style = MaterialTheme.typography.labelSmall, color = PanelSecondaryText)
            Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = PanelPrimaryText)
        }
        Text(detail, style = MaterialTheme.typography.labelSmall, color = PanelSecondaryText)
    }
}

@Composable
private fun TaskEditorDialog(
    draft: CronTaskDraft,
    deliveryOptions: List<CronDeliveryPlatform>?,
    isMutating: Boolean,
    onDraftChange: (CronTaskDraft) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    var deliveryMenuExpanded by rememberSaveable(draft.editingJobId) { mutableStateOf(false) }
    val deliverLabel = localizedString("Deliver")
    val pickerOptions = cronDeliverPickerOptions(
        serverOptions = deliveryOptions,
        currentValue = draft.deliver,
        initialValue = draft.initialDeliver ?: draft.deliver,
    )
    AlertDialog(
        modifier = Modifier.panelDialogChrome(),
        shape = HermexGlassShape,
        containerColor = Color.Transparent,
        onDismissRequest = onDismiss,
        title = { Text(localizedString(if (draft.isEditing) "Edit Task" else "New Task")) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { onDraftChange(draft.copy(name = it)) },
                    label = { Text(localizedString("Name")) },
                    singleLine = true,
                    enabled = !isMutating,
                )
                OutlinedTextField(
                    value = draft.prompt,
                    onValueChange = { onDraftChange(draft.copy(prompt = it)) },
                    label = { Text(localizedString("Prompt")) },
                    minLines = 3,
                    maxLines = 6,
                    enabled = !isMutating,
                )
                OutlinedTextField(
                    value = draft.schedule,
                    onValueChange = { onDraftChange(draft.copy(schedule = it)) },
                    label = { Text(localizedString("Schedule")) },
                    singleLine = true,
                    enabled = !isMutating,
                )
                if (pickerOptions == null) {
                    OutlinedTextField(
                        value = draft.deliver,
                        onValueChange = { onDraftChange(draft.copy(deliver = it)) },
                        label = { Text(deliverLabel) },
                        singleLine = true,
                        enabled = !isMutating,
                    )
                } else {
                    val selected = pickerOptions.firstOrNull { it.value == draft.deliver.trim() }
                    val selectedLabel = selected?.let { option ->
                        if (option.isCustom) localizedStringFormat("%@ (custom)", option.label) else option.label
                    } ?: draft.deliver
                    Box(Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedLabel,
                            onValueChange = {},
                            label = { Text(deliverLabel) },
                            trailingIcon = { Text(if (deliveryMenuExpanded) "^" else "v") },
                            singleLine = true,
                            readOnly = true,
                            enabled = !isMutating,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clickable(enabled = !isMutating, role = Role.Button) {
                                    deliveryMenuExpanded = true
                                }
                                .semantics { contentDescription = deliverLabel },
                        )
                        DropdownMenu(
                            expanded = deliveryMenuExpanded,
                            onDismissRequest = { deliveryMenuExpanded = false },
                        ) {
                            pickerOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (option.isCustom) localizedStringFormat("%@ (custom)", option.label)
                                            else option.label,
                                        )
                                    },
                                    onClick = {
                                        deliveryMenuExpanded = false
                                        onDraftChange(draft.copy(deliver = option.value))
                                    },
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = draft.skillsText,
                    onValueChange = { onDraftChange(draft.copy(skillsText = it)) },
                    label = { Text(localizedString("Skills")) },
                    minLines = 1,
                    maxLines = 3,
                    enabled = !isMutating,
                )
                OutlinedTextField(
                    value = draft.model,
                    onValueChange = { onDraftChange(draft.copy(model = it)) },
                    label = { Text(localizedString("Model")) },
                    singleLine = true,
                    enabled = !isMutating,
                )
                OutlinedTextField(
                    value = draft.provider,
                    onValueChange = { onDraftChange(draft.copy(provider = it)) },
                    label = { Text(localizedString("Provider")) },
                    singleLine = true,
                    enabled = !isMutating,
                )
                OutlinedTextField(
                    value = draft.profile,
                    onValueChange = { onDraftChange(draft.copy(profile = it)) },
                    label = { Text(localizedString("Profile")) },
                    singleLine = true,
                    enabled = !isMutating,
                )
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(
                        checked = draft.toastNotifications,
                        onCheckedChange = { onDraftChange(draft.copy(toastNotifications = it)) },
                        enabled = !isMutating,
                    )
                    Text(localizedString("Toast notifications"))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSave,
                enabled = !isMutating && draft.prompt.isNotBlank() && draft.schedule.isNotBlank(),
            ) {
                Text(localizedString(if (draft.isEditing) "Save" else "Create"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(localizedString("Cancel")) }
        },
    )
}

@Composable
private fun SkillCategorySection(
    group: SkillGroup,
    isMutating: Boolean,
    togglingSkillNames: Set<String>,
    compact: Boolean = false,
    onOpen: (SkillSummary) -> Unit,
    onToggle: (SkillSummary) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            group.category.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        group.skills.forEachIndexed { index, skill ->
            SkillRow(
                skill = skill,
                isMutating = isMutating,
                isToggling = skill.toggleSkillName?.let { it in togglingSkillNames } == true,
                compact = compact,
                onOpen = { onOpen(skill) },
                onToggle = if (skill.canToggleSkill) {
                    { onToggle(skill) }
                } else {
                    null
                },
            )
            if (compact && index != group.skills.lastIndex) {
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 58.dp)
                        .height(0.5.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f)),
                )
            }
        }
    }
}

@Composable
private fun SkillRow(
    skill: SkillSummary,
    isMutating: Boolean,
    isToggling: Boolean,
    compact: Boolean,
    onOpen: () -> Unit,
    onToggle: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isMutating, onClick = onOpen)
            .alpha(if (skill.disabled == true) 0.55f else 1f)
            .padding(vertical = if (compact) 11.dp else 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = androidx.compose.ui.Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f), androidx.compose.foundation.shape.CircleShape),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_lucide_hammer),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                skill.skillDisplayName,
                fontWeight = FontWeight.SemiBold,
                color = PanelPrimaryText,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            skill.description?.trim()?.takeIf { it.isNotEmpty() }?.let { description ->
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val tags = skill.tags.orEmpty().map { it.trim() }.filter { it.isNotEmpty() }
            if (skill.disabled == true || tags.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (skill.disabled == true) {
                        TextChip("Disabled")
                    }
                    tags.forEach { tag -> TextChip(tag) }
                }
            }
            if (!compact) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HermexPillButton(localizedString("Open"), onOpen, enabled = !isMutating)
                    onToggle?.let {
                        HermexPillButton(
                            label = if (skill.disabled == true) "Enable" else "Disable",
                            onClick = it,
                            enabled = !isMutating && !isToggling,
                        )
                    }
                }
            }
        }
        if (compact) {
            onToggle?.let { toggle ->
                Switch(
                    checked = skill.disabled != true,
                    onCheckedChange = { toggle() },
                    enabled = !isMutating && !isToggling,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .semantics { contentDescription = skill.skillDisplayName }
                        .graphicsLayer(scaleX = 0.8f, scaleY = 0.8f),
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = Color(0xFF34C759),
                    ),
                )
            }
            Image(
                painter = painterResource(R.drawable.ic_hermex_chevron_right),
                contentDescription = null,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .size(14.dp),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.secondary.copy(alpha = 0.65f)),
            )
        }
    }
}

@Composable
private fun TextChip(text: String) {
    Text(
        text,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f), androidx.compose.foundation.shape.CircleShape)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.secondary,
        maxLines = 1,
    )
}

@Composable
private fun SkillDetailPanel(
    skill: SkillContentResponse,
    isLoadingFile: Boolean,
    onOpenLinkedFile: (String) -> Unit,
) {
    PanelCard(skill.name ?: "Skill") {
        val content = skill.content?.trim().orEmpty()
        when {
            content.isNotEmpty() -> MarkdownText(content)
            !skill.error.isNullOrBlank() -> Text(skill.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            else -> {
                Text(localizedString("No Content"), fontWeight = FontWeight.SemiBold)
                Text(
                    localizedString("This skill has no content."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }

        val linkedFiles = skill.linkedFileNames
        if (linkedFiles.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            PanelSubsection(localizedString("Linked Files")) {
                linkedFiles.forEach { fileName ->
                    LinkedFileRow(
                        fileName = fileName,
                        enabled = !isLoadingFile,
                        onClick = { onOpenLinkedFile(fileName) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SkillDetailSheet(
    skill: SkillContentResponse,
    isLoadingFile: Boolean,
    onOpenLinkedFile: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val density = LocalDensity.current
    val windowHeight = LocalWindowInfo.current.containerSize.height
    val sheetHeight = with(density) { windowHeight.toDp() } * 0.9f
    val content = skill.content?.trim().orEmpty()
    val plainTextChunks = remember(content) { markdownPlainTextChunksForLargeContent(content) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        dragHandle = null,
        containerColor = Color.Transparent,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(sheetHeight)
                .hermexGlass(
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    castsShadow = false,
                    surfaceLevel = HermexSurfaceLevel.Floating,
                )
                .testTag("skill_detail_sheet")
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    skill.name ?: "Skill",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                HermexPillButton(localizedString("Done"), onDismiss, filled = true)
            }
            Spacer(Modifier.height(14.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("skill_detail_scroll"),
                contentPadding = PaddingValues(bottom = 18.dp),
            ) {
                when {
                    content.isNotEmpty() && plainTextChunks == null -> item("skill-markdown") {
                        MarkdownText(content)
                    }
                    content.isNotEmpty() -> items(
                        count = plainTextChunks.orEmpty().size,
                        key = { index -> "skill-plain-$index" },
                    ) { index ->
                        SelectionContainer {
                            Text(
                                text = plainTextChunks.orEmpty()[index],
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    !skill.error.isNullOrBlank() -> item("skill-error") {
                        Text(skill.error, color = MaterialTheme.colorScheme.error)
                    }
                    else -> item("skill-empty") {
                        Text(localizedString("This skill has no content."), color = PanelSecondaryText)
                    }
                }
                if (skill.linkedFileNames.isNotEmpty()) {
                    item("skill-linked-header") {
                        Spacer(Modifier.height(18.dp))
                        PanelSectionLabel(localizedString("Linked Files"))
                    }
                    items(
                        count = skill.linkedFileNames.size,
                        key = { index -> "skill-linked-${skill.linkedFileNames[index]}-$index" },
                    ) { index ->
                        val fileName = skill.linkedFileNames[index]
                        LinkedFileRow(fileName, !isLoadingFile) { onOpenLinkedFile(fileName) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkedFileRow(
    fileName: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f), androidx.compose.foundation.shape.CircleShape),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            Text("doc", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        }
        Text(fileName, modifier = Modifier.weight(1f), color = PanelPrimaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(">", color = PanelSecondaryText, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SkillLinkedFileDialog(
    fileName: String,
    content: String?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.panelDialogChrome(),
        shape = HermexGlassShape,
        containerColor = Color.Transparent,
        onDismissRequest = onDismiss,
        title = { Text(fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                when {
                    isLoading -> CircularProgressIndicator(strokeWidth = 2.dp)
                    !content.isNullOrBlank() -> MarkdownText(content)
                    else -> Text(localizedString("This file appears to be empty."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(localizedString("Close")) }
        },
    )
}

private data class MemoryPanelSection(
    val key: String,
    val title: String,
    val emptyMessage: String,
    val icon: Int,
)

private val memoryPanelSections = listOf(
    MemoryPanelSection("memory", "My Notes", "No notes yet.", R.drawable.ic_lucide_brain),
    MemoryPanelSection("user", "User Profile", "No profile yet.", R.drawable.ic_lucide_user_round_cog),
    MemoryPanelSection("soul", "Agent Soul", "No soul defined yet.", R.drawable.ic_hermex_chat_bubbles),
)

@Composable
private fun MemoryPanel(
    memory: MemoryResponse?,
    isSaving: Boolean,
    onEditSection: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        memoryPanelSections.forEach { section ->
            MemorySectionSummary(
                section = section,
                content = memory.sectionTextForRoute(section.key),
                modifiedAt = memory.modifiedAtForSection(section.key),
                isSaving = isSaving,
                onEdit = { onEditSection(section.key) },
            )
        }

        memory?.projectContext?.trim()?.takeIf { it.isNotEmpty() }?.let { context ->
            ProjectContextPanel(memory, context)
        }
    }
}

@Composable
private fun FocusedMemoryPanel(
    memory: MemoryResponse?,
    isSaving: Boolean,
    onEditSection: (String) -> Unit,
) {
    val populatedSections = memoryPanelSections.filter { memory.sectionTextForRoute(it.key).isNotBlank() }
    val sections = populatedSections.ifEmpty { listOf(memoryPanelSections.first()) }
    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        sections.forEach { section ->
            FocusedMemorySection(
                section = section,
                content = memory.sectionTextForRoute(section.key),
                modifiedAt = memory.modifiedAtForSection(section.key),
                isSaving = isSaving,
                onEdit = { onEditSection(section.key) },
            )
        }
        memory?.projectContext?.trim()?.takeIf { it.isNotEmpty() }?.let { ProjectContextPanel(memory, it) }
    }
}

@Composable
private fun FocusedMemorySection(
    section: MemoryPanelSection,
    content: String,
    modifiedAt: Double?,
    isSaving: Boolean,
    onEdit: () -> Unit,
) {
    val context = LocalContext.current
    val localizedSectionTitle = localizedString(section.title)
    val editDescription = localizedStringFormat("Edit %@", localizedSectionTitle)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(section.icon),
                contentDescription = null,
                modifier = Modifier.size(25.dp),
                colorFilter = ColorFilter.tint(PanelSecondaryText),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(localizedSectionTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                modifiedAt?.let {
                    Text(
                        "${localizedString("Modified")}: ${it.relativeTimeAgoText(context)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = PanelSecondaryText,
                    )
                }
            }
            TextButton(
                onClick = onEdit,
                enabled = !isSaving,
                modifier = Modifier.semantics {
                    contentDescription = editDescription
                },
            ) {
                Text("✎", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.76f), HermexCardShape)
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            if (content.isBlank()) {
                Text(section.emptyMessage, style = MaterialTheme.typography.bodyLarge, color = PanelSecondaryText)
            } else {
                MarkdownText(content, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun MemorySectionSummary(
    section: MemoryPanelSection,
    content: String,
    modifiedAt: Double?,
    isSaving: Boolean,
    onEdit: () -> Unit,
) {
    val context = LocalContext.current
    val localizedSectionTitle = localizedString(section.title)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.42f),
                HermexCardShape,
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(section.icon),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
            )
            Text(localizedSectionTitle, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = PanelPrimaryText)
            HermexPillButton(localizedStringFormat("Edit %@", localizedSectionTitle), onEdit, enabled = !isSaving)
        }
        modifiedAt?.let {
            Text(
                "${localizedString("Modified")}: ${it.relativeTimeAgoText(context)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        if (content.isBlank()) {
            Text(section.emptyMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        } else {
            MarkdownText(content, modifier = Modifier.fillMaxWidth())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemoryEditSheet(
    section: MemoryPanelSection,
    draft: String,
    isSaving: Boolean,
    onDraftChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = Color.Transparent,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .hermexGlass(
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    castsShadow = false,
                    surfaceLevel = HermexSurfaceLevel.Floating,
                )
                .testTag("memory_edit_sheet")
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(
                    "Edit ${section.title}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                HermexPillButton(localizedString("Cancel"), onDismiss, enabled = !isSaving)
                HermexPillButton(localizedString("Save"), onSave, enabled = !isSaving, filled = true)
            }
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                minLines = 12,
                maxLines = 18,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("memory_edit_body"),
                enabled = !isSaving,
            )
            if (isSaving) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    Text(localizedString("Saving memory..."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}

@Composable
private fun ProjectContextPanel(memory: MemoryResponse, content: String) {
    val context = LocalContext.current
    PanelSubsection(localizedString("Project Context")) {
        TextChip("Read-only")
        if (memory.projectContextShadowed == true) {
            Text(
                localizedString("A workspace-local file is overriding the global project context."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        MarkdownText(content)
        val detail = listOfNotNull(
            memory.projectContextName?.takeIf { it.isNotBlank() }?.let { "${localizedString("Name")}: $it" },
            memory.projectContextWorkspace?.takeIf { it.isNotBlank() }?.let { "${localizedString("Workspace")}: $it" },
            memory.projectContextPath?.takeIf { it.isNotBlank() }?.let { "${localizedString("Path")}: $it" },
            memory.projectContextMtime?.let { "${localizedString("Modified")}: ${it.relativeTimeAgoText(context)}" },
        )
        detail.forEach {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun PanelCard(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .hermexGlass(
                shape = HermexCardShape,
                castsShadow = false,
                surfaceLevel = HermexSurfaceLevel.Base,
            )
            .padding(12.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides PanelPrimaryText) {
            Text(
                title,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
                color = PanelPrimaryText,
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

private val InsightsModelBreakdown.displayShare: Int?
    get() = tokenShare ?: sessionShare ?: costShare

private fun formattedTokens(value: Number): String =
    String.format(Locale.US, "%,d", value.toLong())

private fun formattedCost(value: Double): String =
    if (value == 0.0) "$0" else "$${String.format(Locale.US, "%.4f", value)}"

private fun formattedPercent(value: Double): String =
    if (value % 1.0 == 0.0) {
        "${value.toInt()}%"
    } else {
        "${String.format(Locale.US, "%.1f", value)}%"
    }

private fun formatHour(hour: Int?): String {
    if (hour == null) return "Unknown"
    val normalized = ((hour % 24) + 24) % 24
    val display = when {
        normalized == 0 -> 12
        normalized > 12 -> normalized - 12
        else -> normalized
    }
    val suffix = if (normalized < 12) "AM" else "PM"
    return "$display $suffix"
}

private val SkillContentResponse.linkedFileNames: List<String>
    get() = (files.orEmpty() + linkedFiles.linkedFileNames())
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

private fun JsonElement?.linkedFileNames(): List<String> =
    when (this) {
        null -> emptyList()
        is JsonPrimitive -> listOfNotNull(contentOrNull)
        is JsonArray -> flatMap { it.linkedFileNames() }
        is JsonObject -> flatMap { (key, value) ->
            value.linkedFileNames().ifEmpty { listOf(key) }
        }
    }

private fun MemoryResponse?.sectionTextForRoute(section: String): String {
    if (this == null) return ""
    return when (section) {
        "user" -> user?.takeIf { it.isNotBlank() } ?: userProfile.panelBodyText().orEmpty()
        "soul" -> soul.orEmpty()
        else -> memory.panelBodyText().orEmpty()
    }
}

private fun MemoryResponse?.modifiedAtForSection(section: String): Double? {
    if (this == null) return null
    return when (section) {
        "user" -> userMtime
        "soul" -> soulMtime
        else -> memoryMtime
    }
}

private fun Double.relativeTimeAgoText(context: android.content.Context, nowMillis: Long = System.currentTimeMillis()): String {
    val timestampMillis = if (this > 10_000_000_000.0) this.toLong() else (this * 1000.0).toLong()
    return android.text.format.DateUtils.getRelativeTimeSpanString(
        timestampMillis,
        nowMillis,
        android.text.format.DateUtils.MINUTE_IN_MILLIS,
        android.text.format.DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()
}

private fun JsonElement?.panelBodyText(): String? {
    if (this == null) return null
    return (this as? JsonPrimitive)?.contentOrNull ?: toString()
}
