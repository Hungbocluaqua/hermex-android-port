package com.uzairansar.hermex.ui.panels

import com.uzairansar.hermex.core.model.SessionSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class InsightsAnalyticsTest {
    private val zone = ZoneId.of("UTC")
    private val now = Instant.parse("2026-07-06T12:00:00Z").toEpochMilli()

    @Test
    fun filtersAndAggregatesSessionMetadataLikeIosFallback() {
        val sessions = listOf(
            SessionSummary(
                title = "Recent Heavy",
                lastMessageAt = Instant.parse("2026-07-05T12:00:00Z").epochSecondsDouble(),
                messageCount = 4,
                inputTokens = 100,
                outputTokens = 200,
                estimatedCost = 0.25,
            ),
            SessionSummary(
                title = "Older",
                updatedAt = Instant.parse("2026-06-01T12:00:00Z").epochSecondsDouble(),
                messageCount = 10,
                inputTokens = 900,
                outputTokens = 900,
                estimatedCost = 2.0,
            ),
        )

        val analytics = sessions.analyticsFor(AnalyticsTimeframe.Last7Days, nowMillis = now, zoneId = zone)

        assertEquals(1, analytics.sessionCount)
        assertEquals(4, analytics.totalMessages)
        assertEquals(100, analytics.totalInputTokens)
        assertEquals(200, analytics.totalOutputTokens)
        assertEquals(300, analytics.totalTokens)
        assertEquals(0.25, analytics.estimatedCost, 0.0)
        assertEquals("Recent Heavy", analytics.topSessions.single().title)
    }

    @Test
    fun timeframeContainsUsesSessionTimestampFallbackOrder() {
        val today = SessionSummary(createdAt = Instant.parse("2026-07-06T01:00:00Z").epochSecondsDouble())
        val lastWeek = SessionSummary(lastMessageAt = Instant.parse("2026-07-03T01:00:00Z").epochSecondsDouble())
        val older = SessionSummary(updatedAt = Instant.parse("2026-05-01T01:00:00Z").epochSecondsDouble())

        assertTrue(AnalyticsTimeframe.Today.contains(today, nowMillis = now, zoneId = zone))
        assertFalse(AnalyticsTimeframe.Today.contains(lastWeek, nowMillis = now, zoneId = zone))
        assertTrue(AnalyticsTimeframe.Last7Days.contains(lastWeek, nowMillis = now, zoneId = zone))
        assertFalse(AnalyticsTimeframe.Last30Days.contains(older, nowMillis = now, zoneId = zone))
        assertTrue(AnalyticsTimeframe.AllTime.contains(older, nowMillis = now, zoneId = zone))
    }
}

private fun Instant.epochSecondsDouble(): Double = epochSecond.toDouble() + nano.toDouble() / 1_000_000_000.0
