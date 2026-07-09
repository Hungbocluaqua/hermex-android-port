package com.uzairansar.hermex.data.repository

import com.uzairansar.hermex.core.model.NewSessionRequest
import com.uzairansar.hermex.core.model.ProjectSummary
import com.uzairansar.hermex.core.model.SessionDetail
import com.uzairansar.hermex.core.model.SessionExportFile
import com.uzairansar.hermex.core.model.SessionExportFormat
import com.uzairansar.hermex.core.model.SessionSummary
import com.uzairansar.hermex.core.model.SessionUsageResponse
import com.uzairansar.hermex.core.network.HermesApiClient
import com.uzairansar.hermex.data.db.CacheDao
import com.uzairansar.hermex.data.db.CachedSessionEntity

data class SessionPage(
    val sessions: List<SessionSummary>,
    val archivedCount: Int? = null,
)

data class SessionDuplicateResult(
    val session: SessionSummary? = null,
    val errorMessage: String? = null,
)

class SessionRepository(
    private val client: HermesApiClient,
    private val cacheDao: CacheDao,
) {
    private val serverUrl = client.baseUrl.toString()

    suspend fun loadSessions(includeArchived: Boolean = false): ResultState<SessionPage> {
        val now = System.currentTimeMillis()
        return try {
            val response = client.sessions(includeArchived = includeArchived, archivedLimit = if (includeArchived) 200 else null)
            val sessions = response.sessions.orEmpty().filter { includeArchived || it.archived != true }
            val entities = sessions.filter { it.archived != true }.mapNotNull { CachedSessionEntity.from(serverUrl, it, now) }
            cacheDao.replaceSessions(serverUrl, entities, now)
            ResultState.Data(SessionPage(sessions, response.archivedCount))
        } catch (error: Throwable) {
            val cached = cacheDao.cachedSessions(serverUrl, now).map { it.toSummary() }
            if (cached.isNotEmpty()) ResultState.Data(SessionPage(cached), fromCache = true) else ResultState.Error(error.userMessage(), error)
        }
    }

    suspend fun searchSessions(query: String, content: Boolean = true, depth: Int = 5): SessionPage {
        val response = client.searchSessions(query, content, depth)
        return SessionPage(response.sessions.orEmpty(), response.count)
    }

    suspend fun loadProjects(): List<ProjectSummary> = client.projects().projects.orEmpty()
    suspend fun createProject(name: String): ProjectSummary? = client.createProject(name).project
    suspend fun renameProject(projectId: String, name: String): ProjectSummary? = client.renameProject(projectId, name).project
    suspend fun deleteProject(projectId: String) = client.deleteProject(projectId)

    suspend fun createSession(profile: String? = null): SessionSummary? =
        client.newSession(NewSessionRequest(profile = profile?.trim()?.takeIf { it.isNotBlank() })).session

    suspend fun usage(sessionId: String): SessionUsageResponse = client.sessionUsage(sessionId)

    suspend fun rename(sessionId: String, title: String): SessionSummary? = client.renameSession(sessionId, title).session
    suspend fun delete(sessionId: String) = client.deleteSession(sessionId)
    suspend fun pin(sessionId: String, pinned: Boolean): SessionSummary? = client.pinSession(sessionId, pinned).session
    suspend fun archive(sessionId: String, archived: Boolean): SessionSummary? = client.archiveSession(sessionId, archived).session
    suspend fun move(sessionId: String, projectId: String?): SessionSummary? = client.moveSession(sessionId, projectId).session
    suspend fun branch(sessionId: String, title: String? = null): String? = client.branchSession(sessionId, title = title).sessionId
    suspend fun duplicate(sessionId: String, title: String): SessionDuplicateResult {
        val response = client.branchSession(sessionId, title = title)
        val duplicateId = response.sessionId?.takeIf { it.isNotBlank() }
            ?: return SessionDuplicateResult(
                errorMessage = response.error ?: "The server did not return the duplicated session ID.",
            )
        val duplicate = client.session(duplicateId, includeMessages = false, limit = null).session
            ?: return SessionDuplicateResult(errorMessage = "The server did not return the duplicated session.")
        return SessionDuplicateResult(session = duplicate.toSummary())
    }

    suspend fun exportSession(sessionId: String, format: SessionExportFormat, fallbackTitle: String?): SessionExportFile =
        client.exportSession(sessionId, format, fallbackTitle)
}

private fun SessionDetail.toSummary(): SessionSummary = SessionSummary(
    sessionId = sessionId,
    title = title,
    workspace = workspace,
    model = model,
    modelProvider = modelProvider,
    messageCount = messageCount ?: messages?.size,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastMessageAt = lastMessageAt,
    pinned = pinned,
    archived = archived,
    projectId = projectId,
    profile = profile,
    inputTokens = inputTokens,
    outputTokens = outputTokens,
    estimatedCost = estimatedCost,
    activeStreamId = activeStreamId,
    isStreaming = isStreaming,
    isCliSession = isCliSession,
)
