package com.uzairansar.hermex.ui.sessions

import com.uzairansar.hermex.core.model.SessionSummary
import com.uzairansar.hermex.data.preferences.SessionRowDisplaySettings
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionListUiStateTest {
    @Test
    fun visibleSessionsHideCliSessionsWhenPreferenceIsOff() {
        val state = SessionListUiState(
            showCliSessions = false,
            sessions = listOf(
                SessionSummary(sessionId = "app", title = "App session", isCliSession = false),
                SessionSummary(sessionId = "cli", title = "CLI session", isCliSession = true),
            ),
        )

        assertEquals(listOf("app"), state.visibleSessions.map { it.sessionId })
    }

    @Test
    fun visibleSessionsKeepCliSessionsWhenPreferenceIsOn() {
        val state = SessionListUiState(
            showCliSessions = true,
            sessions = listOf(
                SessionSummary(sessionId = "app", title = "App session", isCliSession = false),
                SessionSummary(sessionId = "cli", title = "CLI session", isCliSession = true),
            ),
        )

        assertEquals(listOf("app", "cli"), state.visibleSessions.map { it.sessionId })
    }

    @Test
    fun visibleSessionsHideCronSessionsWhenPreferenceIsOff() {
        val state = SessionListUiState(
            sessionRowDisplaySettings = SessionRowDisplaySettings(showCronSessions = false),
            sessions = listOf(
                SessionSummary(sessionId = "app", title = "App session", sourceTag = "app"),
                SessionSummary(sessionId = "cron", title = "Cron session", sourceTag = "cron"),
                SessionSummary(sessionId = "scheduled", title = "Scheduled", sessionSource = "cron_job"),
            ),
        )

        assertEquals(listOf("app"), state.visibleSessions.map { it.sessionId })
    }

    @Test
    fun visibleSessionsKeepCronSessionsWhenPreferenceIsOn() {
        val state = SessionListUiState(
            sessionRowDisplaySettings = SessionRowDisplaySettings(showCronSessions = true),
            sessions = listOf(
                SessionSummary(sessionId = "app", title = "App session", sourceTag = "app"),
                SessionSummary(sessionId = "cron", title = "Cron session", sourceLabel = "Cron"),
            ),
        )

        assertEquals(listOf("app", "cron"), state.visibleSessions.map { it.sessionId })
    }
}
