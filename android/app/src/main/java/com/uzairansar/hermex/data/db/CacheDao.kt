package com.uzairansar.hermex.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface CacheDao {
    @Query("SELECT * FROM cached_sessions WHERE serverUrl = :serverUrl AND archived IS NOT 1 AND expiresAtEpochMillis > :now ORDER BY COALESCE(lastMessageAt, updatedAt, createdAt, 0) DESC")
    suspend fun cachedSessions(serverUrl: String, now: Long): List<CachedSessionEntity>

    @Query("SELECT * FROM cached_messages WHERE serverUrl = :serverUrl AND sessionId = :sessionId AND expiresAtEpochMillis > :now ORDER BY sortIndex ASC")
    suspend fun cachedMessages(serverUrl: String, sessionId: String, now: Long): List<CachedMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSessions(sessions: List<CachedSessionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessages(messages: List<CachedMessageEntity>)

    @Query("SELECT cacheKey FROM cached_sessions WHERE serverUrl = :serverUrl")
    suspend fun sessionKeys(serverUrl: String): List<String>

    @Query("SELECT DISTINCT sessionId FROM cached_messages WHERE serverUrl = :serverUrl")
    suspend fun messageSessionIds(serverUrl: String): List<String>

    @Query("SELECT cacheKey FROM cached_messages WHERE serverUrl = :serverUrl AND sessionId = :sessionId")
    suspend fun messageKeys(serverUrl: String, sessionId: String): List<String>

    @Query("DELETE FROM cached_sessions WHERE cacheKey IN (:keys)")
    suspend fun deleteSessionsByKeys(keys: List<String>)

    @Query("DELETE FROM cached_messages WHERE cacheKey IN (:keys)")
    suspend fun deleteMessagesByKeys(keys: List<String>)

    @Query("DELETE FROM cached_messages WHERE serverUrl = :serverUrl AND sessionId IN (:sessionIds)")
    suspend fun deleteMessagesBySessionIds(serverUrl: String, sessionIds: List<String>)

    @Query("DELETE FROM cached_sessions WHERE serverUrl = :serverUrl")
    suspend fun clearSessions(serverUrl: String)

    @Query("DELETE FROM cached_messages WHERE serverUrl = :serverUrl")
    suspend fun clearMessages(serverUrl: String)

    @Query("DELETE FROM cached_sessions WHERE expiresAtEpochMillis <= :now")
    suspend fun deleteExpiredSessions(now: Long)

    @Query("DELETE FROM cached_messages WHERE expiresAtEpochMillis <= :now")
    suspend fun deleteExpiredMessages(now: Long)

    @Query("SELECT cacheKey FROM cached_messages ORDER BY cachedAtEpochMillis DESC LIMIT -1 OFFSET :keep")
    suspend fun oldMessageKeysBeyond(keep: Int): List<String>

    @Query("UPDATE cached_sessions SET title = :title WHERE serverUrl = :serverUrl AND sessionId = :sessionId")
    suspend fun updateSessionTitle(serverUrl: String, sessionId: String, title: String)

    @Query("UPDATE cached_sessions SET pinned = :pinned WHERE serverUrl = :serverUrl AND sessionId = :sessionId")
    suspend fun updateSessionPinned(serverUrl: String, sessionId: String, pinned: Boolean)

    @Query("UPDATE cached_sessions SET archived = :archived WHERE serverUrl = :serverUrl AND sessionId = :sessionId")
    suspend fun updateSessionArchived(serverUrl: String, sessionId: String, archived: Boolean)

    @Query("UPDATE cached_sessions SET projectId = :projectId WHERE serverUrl = :serverUrl AND sessionId = :sessionId")
    suspend fun updateSessionProject(serverUrl: String, sessionId: String, projectId: String?)

    @Query("DELETE FROM cached_sessions WHERE serverUrl = :serverUrl AND sessionId = :sessionId")
    suspend fun deleteCachedSession(serverUrl: String, sessionId: String)

    @Query("DELETE FROM cached_messages WHERE serverUrl = :serverUrl AND sessionId = :sessionId")
    suspend fun deleteCachedSessionMessages(serverUrl: String, sessionId: String)

    @Transaction
    suspend fun replaceSessions(
        serverUrl: String,
        sessions: List<CachedSessionEntity>,
        now: Long,
        authoritative: Boolean = false,
    ) {
        if (sessions.isNotEmpty()) upsertSessions(sessions)
        val freshKeys = sessions.asSequence().map { it.cacheKey }.toHashSet()
        val freshSessionIds = sessions.asSequence().map { it.sessionId }.toHashSet()
        if (authoritative) {
            messageSessionIds(serverUrl)
                .filterNot(freshSessionIds::contains)
                .chunked(BIND_CHUNK_SIZE)
                .forEach { deleteMessagesBySessionIds(serverUrl, it) }
        }
        sessionKeys(serverUrl)
            .filterNot(freshKeys::contains)
            .chunked(BIND_CHUNK_SIZE)
            .forEach { deleteSessionsByKeys(it) }
        maintenance(now)
    }

    @Transaction
    suspend fun replaceMessages(serverUrl: String, sessionId: String, messages: List<CachedMessageEntity>, now: Long) {
        if (messages.isNotEmpty()) upsertMessages(messages)
        val freshKeys = messages.asSequence().map { it.cacheKey }.toHashSet()
        messageKeys(serverUrl, sessionId)
            .filterNot(freshKeys::contains)
            .chunked(BIND_CHUNK_SIZE)
            .forEach { deleteMessagesByKeys(it) }
        maintenance(now)
    }

    @Transaction
    suspend fun purgeSession(serverUrl: String, sessionId: String) {
        deleteCachedSession(serverUrl, sessionId)
        deleteCachedSessionMessages(serverUrl, sessionId)
    }

    @Transaction
    suspend fun clearServer(serverUrl: String) {
        clearSessions(serverUrl)
        clearMessages(serverUrl)
    }

    @Transaction
    suspend fun maintenance(now: Long, maxMessages: Int = 5_000) {
        deleteExpiredSessions(now)
        deleteExpiredMessages(now)
        val oldKeys = oldMessageKeysBeyond(maxMessages)
        oldKeys.chunked(BIND_CHUNK_SIZE).forEach { deleteMessagesByKeys(it) }
    }

    companion object {
        private const val BIND_CHUNK_SIZE = 900
    }
}
