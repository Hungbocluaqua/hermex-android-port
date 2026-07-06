package com.uzairansar.hermex.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class EmptyBody

@Serializable
data class LoginRequest(
    val password: String,
)

@Serializable
data class NewSessionRequest(
    val workspace: String? = null,
    val model: String? = null,
    @SerialName("model_provider") val modelProvider: String? = null,
    val profile: String? = null,
)

@Serializable
data class SessionIdRequest(
    @SerialName("session_id") val sessionId: String,
)

@Serializable
data class SessionYoloRequest(
    @SerialName("session_id") val sessionId: String,
    val enabled: Boolean,
)

@Serializable
data class RenameSessionRequest(
    @SerialName("session_id") val sessionId: String,
    val title: String,
)

@Serializable
data class PinSessionRequest(
    @SerialName("session_id") val sessionId: String,
    val pinned: Boolean,
)

@Serializable
data class ArchiveSessionRequest(
    @SerialName("session_id") val sessionId: String,
    val archived: Boolean,
)

@Serializable
data class MoveSessionRequest(
    @SerialName("session_id") val sessionId: String,
    @SerialName("project_id") val projectId: String? = null,
)

@Serializable
data class BranchSessionRequest(
    @SerialName("session_id") val sessionId: String,
    @SerialName("keep_count") val keepCount: Int? = null,
    val title: String? = null,
)

@Serializable
data class TruncateSessionRequest(
    @SerialName("session_id") val sessionId: String,
    @SerialName("keep_count") val keepCount: Int,
)

@Serializable
data class UpdateSessionRequest(
    @SerialName("session_id") val sessionId: String,
    val workspace: String? = null,
    val model: String? = null,
    @SerialName("model_provider") val modelProvider: String? = null,
)

@Serializable
data class CreateProjectRequest(
    val name: String,
)

@Serializable
data class RenameProjectRequest(
    @SerialName("project_id") val projectId: String,
    val name: String,
)

@Serializable
data class DeleteProjectRequest(
    @SerialName("project_id") val projectId: String,
)

@Serializable
data class ChatStartRequest(
    @SerialName("session_id") val sessionId: String? = null,
    val message: String,
    val workspace: String? = null,
    val model: String? = null,
    @SerialName("model_provider") val modelProvider: String? = null,
    val profile: String? = null,
    @SerialName("explicit_model_pick") val explicitModelPick: Boolean = false,
    val attachments: List<UploadResponse>? = null,
)

@Serializable
data class ChatSteerRequest(
    @SerialName("session_id") val sessionId: String,
    val text: String,
)

@Serializable
data class BtwRequest(
    @SerialName("session_id") val sessionId: String,
    val question: String,
)

@Serializable
data class BackgroundRequest(
    @SerialName("session_id") val sessionId: String,
    val prompt: String,
)

@Serializable
data class GoalRequest(
    @SerialName("session_id") val sessionId: String,
    val args: String,
    val workspace: String? = null,
    val model: String? = null,
    @SerialName("model_provider") val modelProvider: String? = null,
    val profile: String? = null,
)

@Serializable
data class CompressSessionRequest(
    @SerialName("session_id") val sessionId: String,
    @SerialName("focus_topic") val focusTopic: String? = null,
)

@Serializable
data class ApprovalRespondRequest(
    @SerialName("session_id") val sessionId: String,
    val choice: ApprovalChoice,
    @SerialName("approval_id") val approvalId: String? = null,
)

@Serializable
data class ClarifyRespondRequest(
    @SerialName("session_id") val sessionId: String,
    @SerialName("clarify_id") val clarifyId: String? = null,
    val response: String,
)

@Serializable
data class DefaultModelRequest(
    val model: String,
)

@Serializable
data class ProfileCreateRequest(
    val name: String,
    @SerialName("clone_config") val cloneConfig: Boolean,
    @SerialName("default_model") val defaultModel: String? = null,
    @SerialName("model_provider") val modelProvider: String? = null,
    @SerialName("base_url") val baseUrl: String? = null,
    @SerialName("api_key") val apiKey: String? = null,
)

@Serializable
data class UpdateSettingsRequest(
    @SerialName("show_cli_sessions") val showCliSessions: Boolean? = null,
)

@Serializable
data class UpdatesCheckForceRequest(
    val force: Boolean,
)

@Serializable
data class UpdatesApplyRequest(
    val target: String,
)

@Serializable
data class CronCreateRequest(
    val prompt: String,
    val schedule: String,
    val name: String? = null,
    val deliver: String? = null,
    val skills: List<String> = emptyList(),
    val model: String? = null,
    val profile: String? = null,
    @SerialName("toast_notifications") val toastNotifications: Boolean,
)

@Serializable
data class CronUpdateRequest(
    @SerialName("job_id") val jobId: String,
    val prompt: String? = null,
    val schedule: String? = null,
    val name: String? = null,
    val deliver: String? = null,
    val skills: List<String>? = null,
    val model: String? = null,
    val profile: String? = null,
    @SerialName("toast_notifications") val toastNotifications: Boolean? = null,
)

@Serializable
data class CronJobIdRequest(
    @SerialName("job_id") val jobId: String,
    val reason: String? = null,
)

@Serializable
data class ToggleSkillRequest(
    val name: String,
    val enabled: Boolean,
)

@Serializable
data class MemoryWriteRequest(
    val section: String,
    val content: String,
)

@Serializable
data class ReasoningRequest(
    val effort: String,
    val model: String? = null,
    val provider: String? = null,
)

@Serializable
data class SwitchProfileRequest(
    val name: String,
)

@Serializable
data class SetPersonalityRequest(
    @SerialName("session_id") val sessionId: String,
    val name: String,
)

@Serializable
data class GitSessionRequest(
    @SerialName("session_id") val sessionId: String,
)

@Serializable
data class GitPathsRequest(
    @SerialName("session_id") val sessionId: String,
    val paths: List<String>,
)

@Serializable
data class GitDiscardRequest(
    @SerialName("session_id") val sessionId: String,
    val paths: List<String>,
    @SerialName("delete_untracked") val deleteUntracked: Boolean = false,
)

@Serializable
data class GitCommitRequest(
    @SerialName("session_id") val sessionId: String,
    val message: String,
)

@Serializable
data class GitCommitSelectedRequest(
    @SerialName("session_id") val sessionId: String,
    val message: String,
    val paths: List<String>,
)

@Serializable
data class GitCheckoutRequest(
    @SerialName("session_id") val sessionId: String,
    val ref: String,
    val mode: String,
    @SerialName("new_branch") val newBranch: String? = null,
    val track: Boolean? = null,
    @SerialName("dirty_mode") val dirtyMode: String? = null,
)

@Serializable
data class TtsSynthesisRequest(
    val text: String,
    val voice: String,
)
