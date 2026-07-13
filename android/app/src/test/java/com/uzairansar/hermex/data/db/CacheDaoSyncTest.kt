package com.uzairansar.hermex.data.db

import com.uzairansar.hermex.core.model.SessionSummary
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class CacheDaoSyncTest {
    @Test
    fun authoritativeSyncPurgesDeletedTranscriptsButPreservesArchivedOnes() = runBlocking {
        val serverUrl = "https://hermex.test/"
        val dao = SyncRecordingCacheDao(
            existingMessageSessionIds = mutableListOf("active", "archived", "deleted"),
        )
        val sessions = listOf(
            requireNotNull(CachedSessionEntity.from(serverUrl, SessionSummary(sessionId = "active"))),
            requireNotNull(CachedSessionEntity.from(serverUrl, SessionSummary(sessionId = "archived", archived = true))),
        )

        dao.replaceSessions(serverUrl, sessions, System.currentTimeMillis(), authoritative = true)

        assertEquals(listOf("deleted"), dao.deletedMessageSessionIds)
    }
}

private class SyncRecordingCacheDao(
    private val existingMessageSessionIds: MutableList<String>,
) : CacheDao {
    val deletedMessageSessionIds = mutableListOf<String>()

    override suspend fun cachedSessions(serverUrl: String, now: Long) = emptyList<CachedSessionEntity>()
    override suspend fun cachedMessages(serverUrl: String, sessionId: String, now: Long) = emptyList<CachedMessageEntity>()
    override suspend fun upsertSessions(sessions: List<CachedSessionEntity>) = Unit
    override suspend fun upsertMessages(messages: List<CachedMessageEntity>) = Unit
    override suspend fun sessionKeys(serverUrl: String) = emptyList<String>()
    override suspend fun messageSessionIds(serverUrl: String) = existingMessageSessionIds.toList()
    override suspend fun messageKeys(serverUrl: String, sessionId: String) = emptyList<String>()
    override suspend fun deleteSessionsByKeys(keys: List<String>) = Unit
    override suspend fun deleteMessagesByKeys(keys: List<String>) = Unit
    override suspend fun deleteMessagesBySessionIds(serverUrl: String, sessionIds: List<String>) {
        deletedMessageSessionIds += sessionIds
        existingMessageSessionIds.removeAll(sessionIds.toSet())
    }
    override suspend fun clearSessions(serverUrl: String) = Unit
    override suspend fun clearMessages(serverUrl: String) = Unit
    override suspend fun deleteExpiredSessions(now: Long) = Unit
    override suspend fun deleteExpiredMessages(now: Long) = Unit
    override suspend fun oldMessageKeysBeyond(keep: Int) = emptyList<String>()
    override suspend fun updateSessionTitle(serverUrl: String, sessionId: String, title: String) = Unit
    override suspend fun updateSessionPinned(serverUrl: String, sessionId: String, pinned: Boolean) = Unit
    override suspend fun updateSessionArchived(serverUrl: String, sessionId: String, archived: Boolean) = Unit
    override suspend fun updateSessionProject(serverUrl: String, sessionId: String, projectId: String?) = Unit
    override suspend fun deleteCachedSession(serverUrl: String, sessionId: String) = Unit
    override suspend fun deleteCachedSessionMessages(serverUrl: String, sessionId: String) = Unit
}
