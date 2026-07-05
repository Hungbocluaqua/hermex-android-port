package com.uzairansar.hermex.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface CacheDao {
    @Query("SELECT * FROM cached_sessions WHERE serverUrl = :serverUrl AND archived != 1 AND expiresAtEpochMillis > :now ORDER BY COALESCE(lastMessageAt, updatedAt, createdAt, 0) DESC")
    suspend fun cachedSessions(serverUrl: String, now: Long): List<CachedSessionEntity>

    @Query("SELECT * FROM cached_messages WHERE serverUrl = :serverUrl AND sessionId = :sessionId AND expiresAtEpochMillis > :now ORDER BY sortIndex ASC")
    suspend fun cachedMessages(serverUrl: String, sessionId: String, now: Long): List<CachedMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSessions(sessions: List<CachedSessionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessages(messages: List<CachedMessageEntity>)

    @Query("DELETE FROM cached_sessions WHERE serverUrl = :serverUrl AND cacheKey NOT IN (:freshKeys)")
    suspend fun deleteStaleSessions(serverUrl: String, freshKeys: List<String>)

    @Query("DELETE FROM cached_messages WHERE serverUrl = :serverUrl AND sessionId = :sessionId AND cacheKey NOT IN (:freshKeys)")
    suspend fun deleteStaleMessages(serverUrl: String, sessionId: String, freshKeys: List<String>)

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

    @Query("DELETE FROM cached_messages WHERE cacheKey IN (:keys)")
    suspend fun deleteMessagesByKeys(keys: List<String>)

    @Transaction
    suspend fun replaceSessions(serverUrl: String, sessions: List<CachedSessionEntity>, now: Long) {
        if (sessions.isNotEmpty()) upsertSessions(sessions)
        deleteStaleSessions(serverUrl, sessions.map { it.cacheKey }.ifEmpty { listOf("__none__") })
        maintenance(now)
    }

    @Transaction
    suspend fun replaceMessages(serverUrl: String, sessionId: String, messages: List<CachedMessageEntity>, now: Long) {
        if (messages.isNotEmpty()) upsertMessages(messages)
        deleteStaleMessages(serverUrl, sessionId, messages.map { it.cacheKey }.ifEmpty { listOf("__none__") })
        maintenance(now)
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
        if (oldKeys.isNotEmpty()) deleteMessagesByKeys(oldKeys)
    }
}
