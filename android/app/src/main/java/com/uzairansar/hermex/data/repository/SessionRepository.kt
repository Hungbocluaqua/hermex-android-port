package com.uzairansar.hermex.data.repository

import com.uzairansar.hermex.core.model.NewSessionRequest
import com.uzairansar.hermex.core.model.ProjectSummary
import com.uzairansar.hermex.core.model.SessionSummary
import com.uzairansar.hermex.core.network.HermesApiClient
import com.uzairansar.hermex.data.db.CacheDao
import com.uzairansar.hermex.data.db.CachedSessionEntity

data class SessionPage(
    val sessions: List<SessionSummary>,
    val archivedCount: Int? = null,
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

    suspend fun createSession(): SessionSummary? = client.newSession(NewSessionRequest()).session
    suspend fun rename(sessionId: String, title: String): SessionSummary? = client.renameSession(sessionId, title).session
    suspend fun delete(sessionId: String) = client.deleteSession(sessionId)
    suspend fun pin(sessionId: String, pinned: Boolean): SessionSummary? = client.pinSession(sessionId, pinned).session
    suspend fun archive(sessionId: String, archived: Boolean): SessionSummary? = client.archiveSession(sessionId, archived).session
    suspend fun move(sessionId: String, projectId: String?): SessionSummary? = client.moveSession(sessionId, projectId).session
    suspend fun branch(sessionId: String, title: String? = null): String? = client.branchSession(sessionId, title = title).sessionId
}
