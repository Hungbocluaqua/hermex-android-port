package com.uzairansar.hermex.ui.panels

import com.uzairansar.hermex.core.model.SessionSummary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class InsightsDataSource {
    Server,
    LocalFallback,
    Local,
}

data class SessionUsageAnalytics(
    val sessions: List<SessionSummary>,
) {
    val totalInputTokens: Int = sessions.sumOf { it.inputTokens ?: 0 }
    val totalOutputTokens: Int = sessions.sumOf { it.outputTokens ?: 0 }
    val totalTokens: Int = totalInputTokens + totalOutputTokens
    val totalMessages: Int = sessions.sumOf { it.messageCount ?: 0 }
    val estimatedCost: Double = sessions.sumOf { it.estimatedCost ?: 0.0 }
    val sessionCount: Int = sessions.size
    val topSessions: List<SessionSummary> =
        sessions.sortedByDescending { (it.inputTokens ?: 0) + (it.outputTokens ?: 0) }
}

fun List<SessionSummary>.analyticsFor(
    timeframe: AnalyticsTimeframe,
    nowMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): SessionUsageAnalytics =
    SessionUsageAnalytics(filter { timeframe.contains(it, nowMillis, zoneId) })

fun AnalyticsTimeframe.contains(
    session: SessionSummary,
    nowMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): Boolean {
    if (this == AnalyticsTimeframe.AllTime) return true
    val timestamp = session.analyticsTimestamp ?: return false
    val now = Instant.ofEpochMilli(nowMillis)
    val sessionInstant = Instant.ofEpochMilli((timestamp * 1_000.0).toLong())
    return when (this) {
        AnalyticsTimeframe.Today -> {
            val today = LocalDate.ofInstant(now, zoneId)
            LocalDate.ofInstant(sessionInstant, zoneId) == today
        }
        AnalyticsTimeframe.Last7Days -> !sessionInstant.isBefore(now.minusSeconds(7L * 24L * 60L * 60L)) &&
            !sessionInstant.isAfter(now)
        AnalyticsTimeframe.Last30Days -> !sessionInstant.isBefore(now.minusSeconds(30L * 24L * 60L * 60L)) &&
            !sessionInstant.isAfter(now)
        AnalyticsTimeframe.AllTime -> true
    }
}

val SessionSummary.analyticsTimestamp: Double?
    get() = lastMessageAt ?: updatedAt ?: createdAt
