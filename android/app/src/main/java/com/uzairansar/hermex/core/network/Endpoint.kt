package com.uzairansar.hermex.core.network

import okhttp3.HttpUrl

sealed class Endpoint(
    val path: String,
    private val queryItems: List<Pair<String, String?>> = emptyList(),
) {
    data object Health : Endpoint("/health")
    data object AuthStatus : Endpoint("/api/auth/status")
    data object Login : Endpoint("/api/auth/login")
    data object Logout : Endpoint("/api/auth/logout")
    data class Sessions(val includeArchived: Boolean = false, val archivedLimit: Int? = null) : Endpoint(
        "/api/sessions",
        listOf(
            "include_archived" to includeArchived.asIosQueryFlagOrNull(),
            "archived_limit" to archivedLimit?.takeIf { includeArchived }?.toString(),
        ),
    )
    data class SessionsSearch(val query: String, val content: Boolean, val depth: Int) : Endpoint(
        "/api/sessions/search",
        listOf("q" to query, "content" to content.asIosQueryFlag(), "depth" to depth.toString()),
    )
    data class Session(
        val id: String,
        val includeMessages: Boolean,
        val messageLimit: Int? = null,
        val messageBefore: Int? = null,
        val expandRenderable: Boolean = false,
    ) : Endpoint(
        "/api/session",
        listOf(
            "session_id" to id,
            "messages" to if (includeMessages) "1" else "0",
            "msg_limit" to messageLimit?.toString(),
            "msg_before" to messageBefore?.toString(),
            "expand_renderable" to expandRenderable.asIosQueryFlagOrNull(),
        ),
    )
    data class SessionStatus(val id: String) : Endpoint("/api/session/status", listOf("session_id" to id))
    data object NewSession : Endpoint("/api/session/new")
    data object RenameSession : Endpoint("/api/session/rename")
    data object DeleteSession : Endpoint("/api/session/delete")
    data object PinSession : Endpoint("/api/session/pin")
    data object ArchiveSession : Endpoint("/api/session/archive")
    data object BranchSession : Endpoint("/api/session/branch")
    data object CompressSession : Endpoint("/api/session/compress")
    data object UndoSession : Endpoint("/api/session/undo")
    data object RetrySession : Endpoint("/api/session/retry")
    data object TruncateSession : Endpoint("/api/session/truncate")
    data object UpdateSession : Endpoint("/api/session/update")
    data object MoveSession : Endpoint("/api/session/move")
    data class SessionYolo(val sessionId: String?) : Endpoint("/api/session/yolo", listOf("session_id" to sessionId))
    data class ExportSession(val sessionId: String, val format: String) : Endpoint(
        "/api/session/export",
        listOf("session_id" to sessionId, "format" to format),
    )
    data object Projects : Endpoint("/api/projects")
    data object CreateProject : Endpoint("/api/projects/create")
    data object RenameProject : Endpoint("/api/projects/rename")
    data object DeleteProject : Endpoint("/api/projects/delete")
    data object ChatStart : Endpoint("/api/chat/start")
    data class ChatStream(val streamId: String, val replayAfterSeq: Int? = null) : Endpoint(
        "/api/chat/stream",
        listOf(
            "stream_id" to streamId,
            "replay" to replayAfterSeq?.let { "1" },
            "after_seq" to replayAfterSeq?.coerceAtLeast(0)?.toString(),
        ),
    )
    data class ChatCancel(val streamId: String) : Endpoint("/api/chat/cancel", listOf("stream_id" to streamId))
    data class ChatStreamStatus(val streamId: String) : Endpoint("/api/chat/stream/status", listOf("stream_id" to streamId))
    data object ChatSteer : Endpoint("/api/chat/steer")
    data object SubmitGoal : Endpoint("/api/goal")
    data class ApprovalPending(val sessionId: String) : Endpoint("/api/approval/pending", listOf("session_id" to sessionId))
    data class ApprovalStream(val sessionId: String) : Endpoint("/api/approval/stream", listOf("session_id" to sessionId))
    data object ApprovalRespond : Endpoint("/api/approval/respond")
    data class ClarifyPending(val sessionId: String) : Endpoint("/api/clarify/pending", listOf("session_id" to sessionId))
    data class ClarifyStream(val sessionId: String) : Endpoint("/api/clarify/stream", listOf("session_id" to sessionId))
    data object ClarifyRespond : Endpoint("/api/clarify/respond")
    data object Btw : Endpoint("/api/btw")
    data object Background : Endpoint("/api/background")
    data class BackgroundStatus(val sessionId: String) : Endpoint("/api/background/status", listOf("session_id" to sessionId))
    data object Workspaces : Endpoint("/api/workspaces")
    data class WorkspaceSuggestions(val prefix: String) : Endpoint("/api/workspaces/suggest", listOf("prefix" to prefix))
    data class DirectoryList(val sessionId: String, val pathValue: String?) : Endpoint(
        "/api/list",
        listOf("session_id" to sessionId, "path" to pathValue),
    )
    data class File(val sessionId: String, val pathValue: String) : Endpoint("/api/file", listOf("session_id" to sessionId, "path" to pathValue))
    data class RawFile(val sessionId: String, val pathValue: String) : Endpoint("/api/file/raw", listOf("session_id" to sessionId, "path" to pathValue))
    data class Media(val pathValue: String) : Endpoint("/api/media", listOf("path" to pathValue))
    data class GitInfo(val sessionId: String) : Endpoint("/api/git-info", listOf("session_id" to sessionId))
    data class GitStatus(val sessionId: String) : Endpoint("/api/git/status", listOf("session_id" to sessionId))
    data class GitBranches(val sessionId: String) : Endpoint("/api/git/branches", listOf("session_id" to sessionId))
    data class GitDiff(val sessionId: String, val pathValue: String, val kind: String) : Endpoint(
        "/api/git/diff",
        listOf("session_id" to sessionId, "path" to pathValue, "kind" to kind),
    )
    data object GitFetch : Endpoint("/api/git/fetch")
    data object GitPull : Endpoint("/api/git/pull")
    data object GitPush : Endpoint("/api/git/push")
    data object GitCheckout : Endpoint("/api/git/checkout")
    data object GitStashCheckout : Endpoint("/api/git/stash-checkout")
    data object GitStage : Endpoint("/api/git/stage")
    data object GitUnstage : Endpoint("/api/git/unstage")
    data object GitDiscard : Endpoint("/api/git/discard")
    data object GitCommit : Endpoint("/api/git/commit")
    data object GitCommitSelected : Endpoint("/api/git/commit-selected")
    data object GitCommitMessage : Endpoint("/api/git/commit-message")
    data object GitCommitMessageSelected : Endpoint("/api/git/commit-message-selected")
    data object Models : Endpoint("/api/models")
    data object ModelsLive : Endpoint("/api/models/live")
    data object Commands : Endpoint("/api/commands")
    data object DefaultModel : Endpoint("/api/default-model")
    data class Reasoning(val model: String? = null, val provider: String? = null) : Endpoint(
        "/api/reasoning",
        listOf("model" to model, "provider" to provider),
    )
    data object Personalities : Endpoint("/api/personalities")
    data object SetPersonality : Endpoint("/api/personality/set")
    data object Profiles : Endpoint("/api/profiles")
    data object SwitchProfile : Endpoint("/api/profile/switch")
    data object CreateProfile : Endpoint("/api/profile/create")
    data object Providers : Endpoint("/api/providers")
    data object Settings : Endpoint("/api/settings")
    data object UpdatesCheck : Endpoint("/api/updates/check")
    data object UpdatesApply : Endpoint("/api/updates/apply")
    data class Insights(val days: Int) : Endpoint("/api/insights", listOf("days" to days.toString()))
    data object Crons : Endpoint("/api/crons")
    data object CronCreate : Endpoint("/api/crons/create")
    data object CronUpdate : Endpoint("/api/crons/update")
    data object CronDelete : Endpoint("/api/crons/delete")
    data object CronRun : Endpoint("/api/crons/run")
    data object CronPause : Endpoint("/api/crons/pause")
    data object CronResume : Endpoint("/api/crons/resume")
    data class CronStatus(val jobId: String? = null) : Endpoint("/api/crons/status", listOf("job_id" to jobId))
    data class CronOutput(val jobId: String, val limit: Int? = null) : Endpoint(
        "/api/crons/output",
        listOf("job_id" to jobId, "limit" to limit?.toString()),
    )
    data class CronHistory(val jobId: String, val offset: Int? = null, val limit: Int? = null) : Endpoint(
        "/api/crons/history",
        listOf("job_id" to jobId, "offset" to offset?.toString(), "limit" to limit?.toString()),
    )
    data object CronDeliveryOptions : Endpoint("/api/crons/delivery-options")
    data object Memory : Endpoint("/api/memory")
    data object MemoryWrite : Endpoint("/api/memory/write")
    data object Skills : Endpoint("/api/skills")
    data class SkillContent(val name: String, val file: String? = null) : Endpoint(
        "/api/skills/content",
        listOf("name" to name, "file" to file),
    )
    data object ToggleSkill : Endpoint("/api/skills/toggle")
    data object Upload : Endpoint("/api/upload")
    data object Transcribe : Endpoint("/api/transcribe")
    data object Tts : Endpoint("/api/tts")

    fun url(relativeTo: HttpUrl): HttpUrl {
        val builder = relativeTo.newBuilder()
            .encodedPath(path)
            .encodedQuery(null)
        queryItems.forEach { (name, value) ->
            if (!value.isNullOrBlank()) builder.addQueryParameter(name, value)
        }
        return builder.build()
    }
}

private fun Boolean.asIosQueryFlag(): String = if (this) "1" else "0"

private fun Boolean.asIosQueryFlagOrNull(): String? = if (this) "1" else null
