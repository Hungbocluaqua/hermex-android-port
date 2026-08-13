package com.uzairansar.hermex.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import com.uzairansar.hermex.core.model.ChatMessage
import com.uzairansar.hermex.core.model.SessionSummary
import com.uzairansar.hermex.core.network.HermesJson

@Entity(
    tableName = "cached_sessions",
    indices = [Index("serverUrl"), Index(value = ["serverUrl", "sessionId"], unique = true)],
)
data class CachedSessionEntity(
    @PrimaryKey val cacheKey: String,
    val serverUrl: String,
    val sessionId: String,
    val title: String?,
    val workspace: String?,
    val model: String?,
    val modelProvider: String?,
    val messageCount: Int?,
    val createdAt: Double?,
    val updatedAt: Double?,
    val lastMessageAt: Double?,
    val pinned: Boolean?,
    val archived: Boolean?,
    val projectId: String?,
    val profile: String?,
    val inputTokens: Int?,
    val outputTokens: Int?,
    val estimatedCost: Double?,
    val activeStreamId: String?,
    val isStreaming: Boolean?,
    val isCliSession: Boolean? = null,
    val sourceTag: String? = null,
    val rawSource: String? = null,
    val sessionSource: String? = null,
    val sourceLabel: String? = null,
    val parentSessionId: String? = null,
    val relationshipType: String? = null,
    @ColumnInfo(name = "readOnly") val explicitReadOnlyFlag: Boolean? = null,
    @ColumnInfo(name = "isReadOnly") val alternateReadOnlyFlag: Boolean? = null,
    val cachedAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
) {
    fun toSummary(): SessionSummary = SessionSummary(
        sessionId = sessionId,
        title = title,
        workspace = workspace,
        model = model,
        modelProvider = modelProvider,
        messageCount = messageCount,
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
        sourceTag = sourceTag,
        rawSource = rawSource,
        sessionSource = sessionSource,
        sourceLabel = sourceLabel,
        parentSessionId = parentSessionId,
        relationshipType = relationshipType,
        readOnly = explicitReadOnlyFlag,
        isReadOnly = alternateReadOnlyFlag,
    )

    companion object {
        private const val ttlMillis = 7L * 24L * 60L * 60L * 1000L

        fun from(serverUrl: String, session: SessionSummary, now: Long = System.currentTimeMillis()): CachedSessionEntity? {
            val sessionId = session.sessionId?.takeIf { it.isNotBlank() } ?: return null
            return CachedSessionEntity(
                cacheKey = cacheKey(serverUrl, sessionId),
                serverUrl = serverUrl,
                sessionId = sessionId,
                title = session.title,
                workspace = session.workspace,
                model = session.model,
                modelProvider = session.modelProvider,
                messageCount = session.messageCount,
                createdAt = session.createdAt,
                updatedAt = session.updatedAt,
                lastMessageAt = session.lastMessageAt,
                pinned = session.pinned,
                archived = session.archived,
                projectId = session.projectId,
                profile = session.profile,
                inputTokens = session.inputTokens,
                outputTokens = session.outputTokens,
                estimatedCost = session.estimatedCost,
                activeStreamId = session.activeStreamId,
                isStreaming = session.isStreaming,
                isCliSession = session.isCliSession,
                sourceTag = session.sourceTag,
                rawSource = session.rawSource,
                sessionSource = session.sessionSource,
                sourceLabel = session.sourceLabel,
                parentSessionId = session.parentSessionId,
                relationshipType = session.relationshipType,
                explicitReadOnlyFlag = session.readOnly,
                alternateReadOnlyFlag = session.isReadOnly,
                cachedAtEpochMillis = now,
                expiresAtEpochMillis = now + ttlMillis,
            )
        }

        fun cacheKey(serverUrl: String, sessionId: String): String = "$serverUrl::$sessionId"
    }
}

@Entity(
    tableName = "cached_messages",
    indices = [Index("serverUrl"), Index("sessionId"), Index(value = ["serverUrl", "sessionId", "sortIndex"], unique = true)],
)
data class CachedMessageEntity(
    @PrimaryKey val cacheKey: String,
    val serverUrl: String,
    val sessionId: String,
    val sortIndex: Int,
    val messageJson: String,
    val cachedAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
) {
    fun toMessage(): ChatMessage? = runCatching { HermesJson.decodeFromString<ChatMessage>(messageJson) }.getOrNull()

    companion object {
        private const val ttlMillis = 7L * 24L * 60L * 60L * 1000L

        fun from(serverUrl: String, sessionId: String, message: ChatMessage, index: Int, now: Long = System.currentTimeMillis()): CachedMessageEntity =
            CachedMessageEntity(
                cacheKey = cacheKey(serverUrl, sessionId, index),
                serverUrl = serverUrl,
                sessionId = sessionId,
                sortIndex = index,
                messageJson = HermesJson.encodeToString(message),
                cachedAtEpochMillis = now,
                expiresAtEpochMillis = now + ttlMillis,
            )

        fun cacheKey(serverUrl: String, sessionId: String, index: Int): String = "$serverUrl::$sessionId::$index"
    }
}
