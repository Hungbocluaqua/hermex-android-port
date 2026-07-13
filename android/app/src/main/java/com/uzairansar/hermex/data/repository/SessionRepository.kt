package com.uzairansar.hermex.data.repository

import com.uzairansar.hermex.core.model.NewSessionRequest
import com.uzairansar.hermex.core.model.ProjectMutationResponse
import com.uzairansar.hermex.core.model.ProjectSummary
import com.uzairansar.hermex.core.model.SessionBranchResponse
import com.uzairansar.hermex.core.model.SessionClearResponse
import com.uzairansar.hermex.core.model.SessionDetail
import com.uzairansar.hermex.core.model.SessionExportFile
import com.uzairansar.hermex.core.model.SessionExportFormat
import com.uzairansar.hermex.core.model.SessionMutationResponse
import com.uzairansar.hermex.core.model.SessionSummary
import com.uzairansar.hermex.core.model.SessionUsageResponse
import com.uzairansar.hermex.core.network.HermesApiClient
import com.uzairansar.hermex.core.network.ApiError
import com.uzairansar.hermex.data.db.CacheDao
import com.uzairansar.hermex.data.db.CachedSessionEntity
import com.uzairansar.hermex.data.db.ServerCacheOwnership
import kotlinx.coroutines.CancellationException

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
    private val cacheOwnership: ServerCacheOwnership,
) {
    private val serverUrl = client.baseUrl.toString()

    suspend fun loadSessions(includeArchived: Boolean = false): ResultState<SessionPage> {
        val cacheGeneration = cacheOwnership.generation(serverUrl)
        val now = System.currentTimeMillis()
        return try {
            // Fetch archived identities too so a complete response can distinguish deletion from archiving.
            val response = client.sessions(includeArchived = true, archivedLimit = ARCHIVED_SYNC_LIMIT)
            val allSessions = response.sessions.orEmpty()
            val sessions = allSessions.filter { includeArchived || it.archived != true }
            val entities = allSessions.mapNotNull { CachedSessionEntity.from(serverUrl, it, now) }
            val archivedReturned = allSessions.count { it.archived == true }
            val authoritative = response.archivedCount?.let { archivedReturned >= it } == true
            cacheOwnership.writeIfCurrent(serverUrl, cacheGeneration) {
                cacheDao.replaceSessions(serverUrl, entities, now, authoritative)
            }
            ResultState.Data(SessionPage(sessions, response.archivedCount))
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            if (error is ApiError.Unauthorized) return ResultState.Error(error.userMessage(), error)
            if (!error.isCacheFallbackEligible()) return ResultState.Error(error.userMessage(), error)
            val cached = cacheOwnership.readIfCurrent(serverUrl, cacheGeneration) {
                cacheDao.cachedSessions(serverUrl, now).map { it.toSummary() }
            } ?: return ResultState.Error("The active profile changed while sessions were loading.", error)
            if (cached.isNotEmpty()) ResultState.Data(SessionPage(cached), fromCache = true) else ResultState.Error(error.userMessage(), error)
        }
    }

    suspend fun searchSessions(query: String, content: Boolean = true, depth: Int = 5): SessionPage {
        val response = client.searchSessions(query, content, depth)
        return SessionPage(response.sessions.orEmpty(), response.count)
    }

    suspend fun loadProjects(): List<ProjectSummary> = client.projects().projects.orEmpty()
    suspend fun createProject(name: String, color: String?): ProjectMutationResponse = client.createProject(name, color)
    suspend fun renameProject(projectId: String, name: String, color: String?): ProjectMutationResponse =
        client.renameProject(projectId, name, color)
    suspend fun deleteProject(projectId: String) = client.deleteProject(projectId)

    suspend fun createSession(profile: String? = null): SessionMutationResponse =
        client.newSession(NewSessionRequest(profile = profile?.trim()?.takeIf { it.isNotBlank() }))

    suspend fun usage(sessionId: String): SessionUsageResponse = client.sessionUsage(sessionId)

    suspend fun rename(sessionId: String, title: String): SessionMutationResponse {
        val cacheGeneration = cacheOwnership.generation(serverUrl)
        val response = client.renameSession(sessionId, title)
        if (response.isSuccessfulMutation()) {
            cacheOwnership.writeIfCurrent(serverUrl, cacheGeneration) {
                cacheDao.updateSessionTitle(serverUrl, sessionId, title)
            }
        }
        return response
    }

    suspend fun clear(sessionId: String): SessionClearResponse {
        val cacheGeneration = cacheOwnership.generation(serverUrl)
        val response = client.clearSession(sessionId)
        if (response.ok != false && response.error.isNullOrBlank()) {
            cacheOwnership.writeIfCurrent(serverUrl, cacheGeneration) {
                cacheDao.deleteCachedSessionMessages(serverUrl, sessionId)
            }
        }
        return response
    }

    suspend fun delete(sessionId: String): SessionMutationResponse {
        val cacheGeneration = cacheOwnership.generation(serverUrl)
        val response = client.deleteSession(sessionId)
        if (response.isSuccessfulMutation()) {
            cacheOwnership.writeIfCurrent(serverUrl, cacheGeneration) {
                cacheDao.purgeSession(serverUrl, sessionId)
            }
        }
        return response
    }

    suspend fun pin(sessionId: String, pinned: Boolean): SessionMutationResponse {
        val cacheGeneration = cacheOwnership.generation(serverUrl)
        val response = client.pinSession(sessionId, pinned)
        if (response.isSuccessfulMutation()) {
            cacheOwnership.writeIfCurrent(serverUrl, cacheGeneration) {
                cacheDao.updateSessionPinned(serverUrl, sessionId, pinned)
            }
        }
        return response
    }

    suspend fun archive(sessionId: String, archived: Boolean): SessionMutationResponse {
        val cacheGeneration = cacheOwnership.generation(serverUrl)
        val response = client.archiveSession(sessionId, archived)
        if (response.isSuccessfulMutation()) {
            cacheOwnership.writeIfCurrent(serverUrl, cacheGeneration) {
                cacheDao.updateSessionArchived(serverUrl, sessionId, archived)
            }
        }
        return response
    }

    suspend fun move(sessionId: String, projectId: String?): SessionMutationResponse {
        val cacheGeneration = cacheOwnership.generation(serverUrl)
        val response = client.moveSession(sessionId, projectId)
        if (response.isSuccessfulMutation()) {
            cacheOwnership.writeIfCurrent(serverUrl, cacheGeneration) {
                cacheDao.updateSessionProject(serverUrl, sessionId, projectId)
            }
        }
        return response
    }
    suspend fun branch(sessionId: String, title: String? = null): SessionBranchResponse = client.branchSession(sessionId, title = title)
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

private const val ARCHIVED_SYNC_LIMIT = 2_000

private fun com.uzairansar.hermex.core.model.SessionMutationResponse.isSuccessfulMutation(): Boolean =
    ok != false && error.isNullOrBlank()

private fun Throwable.isCacheFallbackEligible(): Boolean = when (this) {
    is ApiError.Network -> true
    is ApiError.Http -> statusCode in setOf(408, 502, 503, 504)
    else -> false
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
