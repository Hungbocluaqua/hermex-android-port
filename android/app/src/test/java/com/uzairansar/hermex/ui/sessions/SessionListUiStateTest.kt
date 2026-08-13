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

    @Test
    fun scheduledGroupsRecognizeCronIdsAndSeparateThemFromOrdinarySessions() {
        val state = SessionListUiState(
            sessions = listOf(
                SessionSummary(sessionId = "ordinary", title = "Normal", lastMessageAt = 30.0),
                SessionSummary(sessionId = "cron_nightly", title = "Nightly", lastMessageAt = 20.0),
                SessionSummary(sessionId = "marked", title = "Marked", rawSource = "cron", lastMessageAt = 10.0),
                SessionSummary(sessionId = "cron_archived", title = "Archived", archived = true),
            ),
        )

        val groups = state.scheduledSessionGroups
        assertEquals(listOf("ordinary"), groups.ordinary.map { it.sessionId })
        assertEquals(listOf("cron_nightly", "marked"), groups.scheduled.map { it.sessionId })
        assertEquals(2, groups.totalScheduledCount)
        assertEquals(true, groups.showsDisclosure(isSearchActive = false))
    }

    @Test
    fun scheduledDisclosureUsesFiveRowPreviewAndSearchAwareVisibility() {
        val scheduled = (1..6).map { index ->
            SessionSummary(
                sessionId = "cron_$index",
                title = if (index == 6) "Needle" else "Scheduled $index",
                lastMessageAt = index.toDouble(),
            )
        }
        val state = SessionListUiState(sessions = scheduled)

        assertEquals(5, state.scheduledSessionGroups.scheduledPreview.size)
        assertEquals(true, state.scheduledSessionGroups.hasAdditionalScheduledSessions)
        assertEquals(listOf("cron_6"), state.scheduledSessions("needle").map { it.sessionId })
        assertEquals(
            false,
            state.copy(searchQuery = "missing").scheduledSessionGroups.showsDisclosure(isSearchActive = true),
        )
    }

    @Test
    fun automatedSessionKindsHaveIndependentVisibilityControls() {
        val sessions = listOf(
            SessionSummary(sessionId = "app", title = "App"),
            SessionSummary(sessionId = "cli", title = "CLI", isCliSession = true),
            SessionSummary(sessionId = "claude", title = "Claude", sourceTag = "claude_code"),
            SessionSummary(sessionId = "subagent", title = "Subagent", rawSource = " SubAgent "),
        )

        val defaults = SessionListUiState(sessions = sessions)
        assertEquals(listOf("app", "cli", "claude"), defaults.visibleSessions.map { it.sessionId })

        val onlyApp = defaults.copy(
            showCliSessions = false,
            showClaudeCodeSessions = false,
        )
        assertEquals(listOf("app"), onlyApp.visibleSessions.map { it.sessionId })

        val showSubagents = onlyApp.copy(
            sessionRowDisplaySettings = SessionRowDisplaySettings(showSubagentSessions = true),
        )
        assertEquals(listOf("app", "subagent"), showSubagents.visibleSessions.map { it.sessionId })
    }

    @Test
    fun visibleSessionsSortPinnedFirstThenByMostRecentActivity() {
        val state = SessionListUiState(
            sessions = listOf(
                SessionSummary(sessionId = "old", lastMessageAt = 10.0),
                SessionSummary(sessionId = "recent", updatedAt = 30.0),
                SessionSummary(sessionId = "pinned-old", pinned = true, createdAt = 5.0),
                SessionSummary(sessionId = "pinned-recent", pinned = true, lastMessageAt = 20.0),
            ),
        )

        assertEquals(
            listOf("pinned-recent", "pinned-old", "recent", "old"),
            state.visibleSessions.map { it.sessionId },
        )
    }

    @Test
    fun visibleSessionsAppendLoadedRemoteContentMatchesAfterLocalMatches() {
        val state = SessionListUiState(
            searchQuery = " needle ",
            remoteSearchQuery = "needle",
            remoteContentSearchSessionIds = listOf("content-project", "content-other-project", "local-title"),
            sessions = listOf(
                SessionSummary(
                    sessionId = "local-title",
                    title = "Needle planning",
                    projectId = "project-1",
                    lastMessageAt = 30.0,
                ),
                SessionSummary(
                    sessionId = "content-project",
                    title = "Budget",
                    projectId = "project-1",
                    lastMessageAt = 20.0,
                ),
                SessionSummary(
                    sessionId = "content-other-project",
                    title = "Roadmap",
                    projectId = "project-2",
                    lastMessageAt = 40.0,
                ),
            ),
        )

        assertEquals(
            listOf("local-title", "content-other-project", "content-project"),
            state.visibleSessions.map { it.sessionId },
        )
        assertEquals(
            listOf("local-title", "content-project"),
            state.copy(selectedProjectId = "project-1").visibleSessions.map { it.sessionId },
        )
    }

    @Test
    fun visibleSessionsIgnoreRemoteMatchesFromAnOlderQuery() {
        val state = SessionListUiState(
            searchQuery = "new",
            remoteSearchQuery = "old",
            remoteContentSearchSessionIds = listOf("remote"),
            sessions = listOf(
                SessionSummary(sessionId = "remote", title = "Unrelated"),
                SessionSummary(sessionId = "local", workspace = "/work/new-project"),
            ),
        )

        assertEquals(listOf("local"), state.visibleSessions.map { it.sessionId })
    }

    @Test
    fun contentMatchSessionIdsKeepKnownVisibleContentMatchesOnlyOnce() {
        val loaded = listOf(
            SessionSummary(sessionId = "content"),
            SessionSummary(sessionId = "archived", archived = true),
        )
        val results = listOf(
            SessionSummary(sessionId = "content", matchType = "CONTENT"),
            SessionSummary(sessionId = "content", matchType = "content"),
            SessionSummary(sessionId = "title-only", matchType = "title"),
            SessionSummary(sessionId = "unknown", matchType = "content"),
            SessionSummary(sessionId = "archived", matchType = "content"),
        )

        assertEquals(listOf("content"), contentMatchSessionIds(loaded, results))
    }
}
