package com.uzairansar.hermex.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class HealthResponse(
    val status: String? = null,
)

@Serializable
data class AuthStatusResponse(
    @SerialName("auth_enabled") val authEnabled: Boolean? = null,
    @SerialName("password_auth_enabled") val passwordAuthEnabled: Boolean? = null,
)

@Serializable
data class LoginResponse(
    val ok: Boolean? = null,
    val error: String? = null,
)

@Serializable
data class SessionsResponse(
    val sessions: List<SessionSummary>? = null,
    @SerialName("cli_count") val cliCount: Int? = null,
    @SerialName("archived_count") val archivedCount: Int? = null,
    @SerialName("server_time") val serverTime: Double? = null,
    @SerialName("server_tz") val serverTz: String? = null,
)

@Serializable
data class SessionSearchResponse(
    val sessions: List<SessionSummary>? = null,
    val query: String? = null,
    val count: Int? = null,
)

@Serializable
data class SessionResponse(
    val session: SessionDetail? = null,
)

@Serializable
data class SessionMutationResponse(
    val ok: Boolean? = null,
    val session: SessionSummary? = null,
    val error: String? = null,
)

@Serializable
data class SessionBranchResponse(
    @SerialName("session_id") val sessionId: String? = null,
    val title: String? = null,
    @SerialName("parent_session_id") val parentSessionId: String? = null,
    val error: String? = null,
)

@Serializable
data class SessionCompressResponse(
    val ok: Boolean? = null,
    val session: SessionDetail? = null,
    val summary: SessionCompressionSummary? = null,
    @SerialName("focus_topic") val focusTopic: String? = null,
    val error: String? = null,
)

@Serializable
data class SessionCompressionSummary(
    val headline: String? = null,
    @SerialName("token_line") val tokenLine: String? = null,
    val note: String? = null,
    @SerialName("reference_message") val referenceMessage: String? = null,
)

@Serializable
data class SessionUndoResponse(
    val ok: Boolean? = null,
    @SerialName("removed_count") val removedCount: Int? = null,
    @SerialName("removed_preview") val removedPreview: String? = null,
    val error: String? = null,
)

@Serializable
data class SessionRetryResponse(
    val ok: Boolean? = null,
    @SerialName("last_user_text") val lastUserText: String? = null,
    @SerialName("removed_count") val removedCount: Int? = null,
    val error: String? = null,
)

@Serializable
data class SessionStatusResponse(
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("active_stream_id") val activeStreamId: String? = null,
    @SerialName("is_streaming") val isStreaming: Boolean? = null,
    @SerialName("pending_user_message") val pendingUserMessage: String? = null,
    val error: String? = null,
)

@Serializable
data class SessionSummary(
    @SerialName("session_id") val sessionId: String? = null,
    val title: String? = null,
    val workspace: String? = null,
    val model: String? = null,
    @SerialName("model_provider") val modelProvider: String? = null,
    @SerialName("message_count") val messageCount: Int? = null,
    @SerialName("created_at") val createdAt: Double? = null,
    @SerialName("updated_at") val updatedAt: Double? = null,
    @SerialName("last_message_at") val lastMessageAt: Double? = null,
    val pinned: Boolean? = null,
    val archived: Boolean? = null,
    @SerialName("project_id") val projectId: String? = null,
    val profile: String? = null,
    @SerialName("input_tokens") val inputTokens: Int? = null,
    @SerialName("output_tokens") val outputTokens: Int? = null,
    @SerialName("estimated_cost") val estimatedCost: Double? = null,
    @SerialName("active_stream_id") val activeStreamId: String? = null,
    @SerialName("is_streaming") val isStreaming: Boolean? = null,
    @SerialName("is_cli_session") val isCliSession: Boolean? = null,
    @SerialName("source_tag") val sourceTag: String? = null,
    @SerialName("session_source") val sessionSource: String? = null,
    @SerialName("source_label") val sourceLabel: String? = null,
    @SerialName("match_type") val matchType: String? = null,
) {
    val stableId: String
        get() = sessionId?.takeIf { it.isNotBlank() }
            ?: "session-${title.orEmpty()}-${createdAt ?: updatedAt ?: lastMessageAt ?: 0.0}"
}

@Serializable
data class SessionDetail(
    @SerialName("session_id") val sessionId: String? = null,
    val title: String? = null,
    val workspace: String? = null,
    val model: String? = null,
    @SerialName("model_provider") val modelProvider: String? = null,
    val profile: String? = null,
    val messages: List<ChatMessage>? = null,
    @SerialName("_messages_offset") val messagesOffset: Int? = null,
    @SerialName("context_length") val contextLength: Int? = null,
    @SerialName("threshold_tokens") val thresholdTokens: Int? = null,
    @SerialName("last_prompt_tokens") val lastPromptTokens: Int? = null,
    @SerialName("input_tokens") val inputTokens: Int? = null,
    @SerialName("output_tokens") val outputTokens: Int? = null,
    @SerialName("estimated_cost") val estimatedCost: Double? = null,
)

@Serializable
data class ChatMessage(
    val id: String? = null,
    val role: String? = null,
    val content: String? = null,
    val text: String? = null,
    val timestamp: Double? = null,
    val parts: List<JsonElement>? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCall>? = null,
) {
    val displayText: String
        get() = content ?: text ?: parts?.joinToString("\n") { it.toString() }.orEmpty()
}

@Serializable
data class ToolCall(
    val id: String? = null,
    val name: String? = null,
    val preview: String? = null,
    val args: Map<String, JsonElement>? = null,
    val result: JsonElement? = null,
    @SerialName("is_error") val isError: Boolean? = null,
)

@Serializable
data class ProjectsResponse(
    val projects: List<ProjectSummary>? = null,
)

@Serializable
data class ProjectMutationResponse(
    val ok: Boolean? = null,
    val project: ProjectSummary? = null,
    val error: String? = null,
)

@Serializable
data class ProjectSummary(
    @SerialName("project_id") val projectId: String? = null,
    val name: String? = null,
    val color: String? = null,
    @SerialName("created_at") val createdAt: Double? = null,
) {
    val stableId: String get() = projectId ?: name ?: "project"
}

@Serializable
data class ChatStartResponse(
    @SerialName("stream_id") val streamId: String? = null,
    @SerialName("session_id") val sessionId: String? = null,
    val error: String? = null,
)

@Serializable
data class ChatSteerResponse(
    val accepted: Boolean? = null,
    val fallback: String? = null,
    @SerialName("stream_id") val streamId: String? = null,
    val error: String? = null,
)

@Serializable
data class GoalSubmissionResponse(
    val ok: Boolean? = null,
    val action: String? = null,
    val message: String? = null,
    val goal: SubmittedGoal? = null,
    @SerialName("kickoff_prompt") val kickoffPrompt: String? = null,
    val decision: GoalDecision? = null,
    val error: String? = null,
)

@Serializable
data class SubmittedGoal(
    val goal: String? = null,
    val status: String? = null,
    @SerialName("turns_used") val turnsUsed: Int? = null,
    @SerialName("max_turns") val maxTurns: Int? = null,
    @SerialName("last_verdict") val lastVerdict: String? = null,
    @SerialName("last_reason") val lastReason: String? = null,
    @SerialName("paused_reason") val pausedReason: String? = null,
)

@Serializable
data class GoalDecision(
    val status: String? = null,
    @SerialName("should_continue") val shouldContinue: Boolean? = null,
    @SerialName("continuation_prompt") val continuationPrompt: String? = null,
    val verdict: String? = null,
    val reason: String? = null,
    val message: String? = null,
    @SerialName("message_key") val messageKey: String? = null,
    @SerialName("message_args") val messageArgs: List<JsonElement>? = null,
)

@Serializable
enum class ApprovalChoice {
    @SerialName("once")
    Once,

    @SerialName("session")
    Session,

    @SerialName("always")
    Always,

    @SerialName("deny")
    Deny,
}

@Serializable
data class ApprovalPendingResponse(
    val pending: PendingApproval? = null,
    val pendingCount: Int? = null,
    @SerialName("pending_count") val pendingCountSnake: Int? = null,
) {
    val displayPendingCount: Int get() = maxOf(pendingCount ?: pendingCountSnake ?: 1, 1)
}

@Serializable
data class PendingApproval(
    val approvalId: String? = null,
    @SerialName("approval_id") val approvalIdSnake: String? = null,
    val command: String? = null,
    val description: String? = null,
    val patternKey: String? = null,
    @SerialName("pattern_key") val patternKeySnake: String? = null,
    val patternKeys: List<String>? = null,
    @SerialName("pattern_keys") val patternKeysSnake: List<String>? = null,
) {
    val stableId: String
        get() = normalizedApprovalId ?: "${command.orEmpty()}-${description.orEmpty()}-${displayPatternKeys.joinToString(",")}"
    val normalizedApprovalId: String? get() = approvalId ?: approvalIdSnake
    val displayPatternKeys: List<String>
        get() = (patternKeys ?: patternKeysSnake)
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.takeIf { it.isNotEmpty() }
            ?: listOfNotNull(patternKey ?: patternKeySnake).map { it.trim() }.filter { it.isNotEmpty() }
    val isEmpty: Boolean
        get() = normalizedApprovalId == null &&
            command == null &&
            description == null &&
            (patternKey ?: patternKeySnake) == null &&
            (patternKeys ?: patternKeysSnake).isNullOrEmpty()
}

@Serializable
data class ApprovalRespondResponse(
    val ok: Boolean? = null,
    val choice: ApprovalChoice? = null,
    val staleCleared: Boolean? = null,
    @SerialName("stale_cleared") val staleClearedSnake: Boolean? = null,
    val relayed: Boolean? = null,
    val stale: Boolean? = null,
)

@Serializable
data class ClarificationPendingResponse(
    val pending: PendingClarification? = null,
    val pendingCount: Int? = null,
    @SerialName("pending_count") val pendingCountSnake: Int? = null,
) {
    val displayPendingCount: Int get() = maxOf(pendingCount ?: pendingCountSnake ?: 1, 1)
}

@Serializable
data class PendingClarification(
    val clarifyId: String? = null,
    @SerialName("clarify_id") val clarifyIdSnake: String? = null,
    val question: String? = null,
    val choicesOffered: List<String>? = null,
    @SerialName("choices_offered") val choicesOfferedSnake: List<String>? = null,
    val sessionId: String? = null,
    @SerialName("session_id") val sessionIdSnake: String? = null,
    val kind: String? = null,
    val requestedAt: Double? = null,
    @SerialName("requested_at") val requestedAtSnake: Double? = null,
    val timeoutSeconds: Int? = null,
    @SerialName("timeout_seconds") val timeoutSecondsSnake: Int? = null,
    val expiresAt: Double? = null,
    @SerialName("expires_at") val expiresAtSnake: Double? = null,
) {
    val stableId: String
        get() = normalizedClarifyId ?: "${sessionId ?: sessionIdSnake}-${question.orEmpty()}-${requestedAt ?: requestedAtSnake ?: 0.0}"
    val normalizedClarifyId: String? get() = clarifyId ?: clarifyIdSnake
    val displayQuestion: String
        get() = question?.trim()?.takeIf { it.isNotEmpty() } ?: "The agent needs more information before continuing."
    val displayChoices: List<String>
        get() = (choicesOffered ?: choicesOfferedSnake).orEmpty().map { it.trim() }.filter { it.isNotEmpty() }
    val isEmpty: Boolean
        get() = normalizedClarifyId == null &&
            question == null &&
            displayChoices.isEmpty() &&
            (sessionId ?: sessionIdSnake) == null &&
            kind == null &&
            (requestedAt ?: requestedAtSnake) == null &&
            (timeoutSeconds ?: timeoutSecondsSnake) == null &&
            (expiresAt ?: expiresAtSnake) == null
}

@Serializable
data class ClarificationRespondResponse(
    val ok: Boolean? = null,
    val response: String? = null,
    val stale: Boolean? = null,
    val staleCleared: Boolean? = null,
    @SerialName("stale_cleared") val staleClearedSnake: Boolean? = null,
    val relayed: Boolean? = null,
)

@Serializable
data class UploadResponse(
    val filename: String? = null,
    val path: String? = null,
    val mime: String? = null,
    val size: Long? = null,
    @SerialName("is_image") val isImage: Boolean? = null,
    val error: String? = null,
)

@Serializable
data class TranscribeResponse(
    val ok: Boolean? = null,
    val transcript: String? = null,
    val error: String? = null,
)

@Serializable
data class WorkspaceRoot(
    val path: String? = null,
    val name: String? = null,
    val exists: Boolean? = null,
)

@Serializable
data class WorkspacesResponse(
    val workspaces: List<WorkspaceRoot>? = null,
    val roots: List<WorkspaceRoot>? = null,
)

@Serializable
data class WorkspaceSuggestionsResponse(
    val suggestions: List<String>? = null,
)

@Serializable
data class DirectoryListResponse(
    val path: String? = null,
    val entries: List<WorkspaceEntry>? = null,
    val error: String? = null,
)

@Serializable
data class WorkspaceEntry(
    val name: String? = null,
    val path: String? = null,
    val type: String? = null,
    val size: Long? = null,
    @SerialName("modified_at") val modifiedAt: Double? = null,
)

@Serializable
data class FileResponse(
    val path: String? = null,
    val content: String? = null,
    val encoding: String? = null,
    val language: String? = null,
    val size: Long? = null,
    val error: String? = null,
)

@Serializable
data class ModelCatalogResponse(
    val models: List<ModelSummary>? = null,
    val providers: List<ProviderSummary>? = null,
    @SerialName("default_model") val defaultModel: String? = null,
)

@Serializable
data class ModelSummary(
    val id: String? = null,
    val name: String? = null,
    val provider: String? = null,
    val label: String? = null,
)

@Serializable
data class ProviderSummary(
    val id: String? = null,
    val name: String? = null,
    val configured: Boolean? = null,
)

@Serializable
data class ProfilesResponse(
    val profiles: List<ProfileSummary>? = null,
    val active: String? = null,
    @SerialName("single_profile_mode") val singleProfileMode: Boolean? = null,
)

@Serializable
data class ProfileSummary(
    val name: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    val model: String? = null,
    val provider: String? = null,
)

@Serializable
data class ReasoningResponse(
    val effort: String? = null,
    @SerialName("supported_efforts") val supportedEfforts: List<String>? = null,
    @SerialName("supports_reasoning_effort") val supportsReasoningEffort: Boolean? = null,
)

@Serializable
data class SettingsResponse(
    @SerialName("webui_version") val webuiVersion: String? = null,
    @SerialName("bot_name") val botName: String? = null,
    val theme: String? = null,
)

@Serializable
data class DefaultModelResponse(
    val ok: Boolean? = null,
    val model: String? = null,
    val error: String? = null,
)

@Serializable
data class CronsResponse(
    val crons: List<CronJob>? = null,
    val jobs: List<CronJob>? = null,
)

@Serializable
data class CronJob(
    @SerialName("job_id") val jobId: String? = null,
    val id: String? = null,
    val name: String? = null,
    val prompt: String? = null,
    val command: String? = null,
    val schedule: JsonElement? = null,
    @SerialName("schedule_display") val scheduleDisplay: String? = null,
    val enabled: Boolean? = null,
    val state: String? = null,
    val paused: Boolean? = null,
    val running: Boolean? = null,
    @SerialName("next_run_at") val nextRunAt: JsonElement? = null,
    @SerialName("last_run_at") val lastRunAt: JsonElement? = null,
    @SerialName("last_status") val lastStatus: String? = null,
    @SerialName("last_error") val lastError: String? = null,
    @SerialName("last_delivery_error") val lastDeliveryError: String? = null,
    val repeat: JsonElement? = null,
    val deliver: String? = null,
    val skills: List<String>? = null,
    val model: String? = null,
    val profile: String? = null,
    @SerialName("toast_notifications") val toastNotifications: Boolean? = null,
)

@Serializable
data class CronMutationResponse(
    val ok: Boolean? = null,
    val job: CronJob? = null,
    val error: String? = null,
)

@Serializable
data class CronStatusResponse(
    @SerialName("job_id") val jobId: String? = null,
    val running: JsonElement? = null,
    val elapsed: Double? = null,
    @SerialName("running_jobs") val runningJobs: Map<String, Double>? = null,
    val error: String? = null,
)

@Serializable
data class CronOutputResponse(
    @SerialName("job_id") val jobId: String? = null,
    val outputs: List<CronOutputItem>? = null,
    val error: String? = null,
)

@Serializable
data class CronOutputItem(
    val filename: String? = null,
    val content: String? = null,
)

@Serializable
data class SkillsResponse(
    val skills: List<SkillSummary>? = null,
)

@Serializable
data class SkillSummary(
    val name: String? = null,
    val description: String? = null,
    val category: String? = null,
    val path: String? = null,
    val enabled: Boolean? = null,
    val disabled: Boolean? = null,
    val tags: List<String>? = null,
    @SerialName("related_skills") val relatedSkills: List<String>? = null,
)

@Serializable
data class SkillContentResponse(
    val name: String? = null,
    val content: String? = null,
    val files: List<String>? = null,
    @SerialName("linked_files") val linkedFiles: JsonElement? = null,
    val error: String? = null,
)

@Serializable
data class ToggleSkillResponse(
    val ok: Boolean? = null,
    val name: String? = null,
    val enabled: Boolean? = null,
    val error: String? = null,
)

@Serializable
data class MemoryResponse(
    val memory: JsonElement? = null,
    val user: String? = null,
    val soul: String? = null,
    val notes: List<String>? = null,
    @SerialName("user_profile") val userProfile: JsonElement? = null,
    @SerialName("memory_path") val memoryPath: String? = null,
    @SerialName("user_path") val userPath: String? = null,
    @SerialName("soul_path") val soulPath: String? = null,
    @SerialName("project_context") val projectContext: String? = null,
    @SerialName("project_context_name") val projectContextName: String? = null,
    @SerialName("project_context_path") val projectContextPath: String? = null,
    @SerialName("project_context_workspace") val projectContextWorkspace: String? = null,
    @SerialName("external_notes_enabled") val externalNotesEnabled: Boolean? = null,
)

@Serializable
data class MemoryWriteResponse(
    val ok: Boolean? = null,
    val section: String? = null,
    val path: String? = null,
    val error: String? = null,
)

@Serializable
data class InsightsResponse(
    @SerialName("period_days") val periodDays: Int? = null,
    @SerialName("total_sessions") val totalSessions: Int? = null,
    @SerialName("total_messages") val totalMessages: Int? = null,
    @SerialName("total_input_tokens") val totalInputTokens: Int? = null,
    @SerialName("total_output_tokens") val totalOutputTokens: Int? = null,
    @SerialName("total_tokens") val totalTokens: Int? = null,
    @SerialName("total_cost") val totalCost: Double? = null,
    @SerialName("total_cache_read_tokens") val totalCacheReadTokens: Int? = null,
    @SerialName("total_cache_hit_percent") val totalCacheHitPercent: Double? = null,
    val models: List<InsightsModelBreakdown>? = null,
    @SerialName("daily_tokens") val dailyTokens: List<InsightsDailyToken>? = null,
    @SerialName("activity_by_day") val activityByDay: List<InsightsActivityByDay>? = null,
    @SerialName("activity_by_hour") val activityByHour: List<InsightsActivityByHour>? = null,
)

@Serializable
data class InsightsModelBreakdown(
    val model: String? = null,
    val sessions: Int? = null,
    @SerialName("input_tokens") val inputTokens: Int? = null,
    @SerialName("output_tokens") val outputTokens: Int? = null,
    @SerialName("total_tokens") val totalTokens: Int? = null,
    val cost: Double? = null,
    @SerialName("cache_hit_percent") val cacheHitPercent: Double? = null,
    @SerialName("session_share") val sessionShare: Int? = null,
    @SerialName("token_share") val tokenShare: Int? = null,
    @SerialName("cost_share") val costShare: Int? = null,
)

@Serializable
data class InsightsDailyToken(
    val date: String? = null,
    @SerialName("input_tokens") val inputTokens: Int? = null,
    @SerialName("output_tokens") val outputTokens: Int? = null,
    val sessions: Int? = null,
    val cost: Double? = null,
)

@Serializable
data class InsightsActivityByDay(
    val day: String? = null,
    val sessions: Int? = null,
)

@Serializable
data class InsightsActivityByHour(
    val hour: Int? = null,
    val sessions: Int? = null,
)

@Serializable
data class GitInfoResponse(
    val ok: Boolean? = null,
    val repo: String? = null,
    val branch: String? = null,
    val error: String? = null,
)

@Serializable
data class GitStatusResponse(
    val ok: Boolean? = null,
    val files: List<GitFileChange>? = null,
    val branch: String? = null,
    val error: String? = null,
)

@Serializable
data class GitFileChange(
    val path: String? = null,
    val status: String? = null,
    val staged: Boolean? = null,
)

@Serializable
data class GitBranchesResponse(
    val ok: Boolean? = null,
    val branches: GitBranches? = null,
    val current: String? = null,
    val error: String? = null,
)

@Serializable
data class GitBranches(
    @SerialName("is_git") val isGit: Boolean? = null,
    val current: String? = null,
    val detached: Boolean? = null,
    val head: String? = null,
    val local: List<GitBranchRef>? = null,
    val remote: List<GitBranchRef>? = null,
    val upstream: String? = null,
    val ahead: Int? = null,
    val behind: Int? = null,
)

@Serializable
data class GitBranchRef(
    val name: String? = null,
    val sha: String? = null,
    val updated: Int? = null,
    @SerialName("updated_relative") val updatedRelative: String? = null,
    val author: String? = null,
    val subject: String? = null,
    val upstream: String? = null,
    val ahead: Int? = null,
    val behind: Int? = null,
)

@Serializable
data class GitDiffResponse(
    val ok: Boolean? = null,
    val path: String? = null,
    val diff: String? = null,
    val error: String? = null,
)

@Serializable
data class GitMutationResponse(
    val ok: Boolean? = null,
    val message: String? = null,
    val error: String? = null,
)

typealias GitRemoteActionResponse = GitMutationResponse

@Serializable
data class GitCheckoutResponse(
    val ok: Boolean? = null,
    val message: String? = null,
    val status: GitStatusResponse? = null,
    val git: GitStatusResponse? = null,
    val branches: GitBranches? = null,
    @SerialName("current_branch") val currentBranch: String? = null,
    @SerialName("stash_name") val stashName: String? = null,
    val stashed: Boolean? = null,
    @SerialName("restored_stash") val restoredStash: GitRestoredStash? = null,
    @SerialName("restore_failed") val restoreFailed: Boolean? = null,
    @SerialName("restore_error") val restoreError: String? = null,
    @SerialName("restore_stash") val restoreStash: GitRestoredStash? = null,
    val error: String? = null,
)

@Serializable
data class GitRestoredStash(
    val ref: String? = null,
    val branch: String? = null,
    val message: String? = null,
)

@Serializable
data class GitCommitResponse(
    val ok: Boolean? = null,
    val sha: String? = null,
    val message: String? = null,
    val error: String? = null,
)

@Serializable
data class GitCommitMessageResponse(
    val ok: Boolean? = null,
    val message: String? = null,
    val error: String? = null,
)
