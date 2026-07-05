package com.uzairansar.hermex.core.network

import com.uzairansar.hermex.core.model.SessionsResponse
import com.uzairansar.hermex.core.model.GitBranchesResponse
import com.uzairansar.hermex.core.model.CronsResponse
import com.uzairansar.hermex.core.model.GoalSubmissionResponse
import org.junit.Assert.assertEquals
import org.junit.Test

class TolerantDecodingTest {
    @Test
    fun sessionsDecodeWithUnknownFieldsAndMissingOptionals() {
        val json = """
            {
              "sessions": [
                {
                  "session_id": "s1",
                  "title": "Port Android",
                  "brand_new_upstream_field": {"nested": true}
                }
              ],
              "another_future_field": 42
            }
        """.trimIndent()

        val decoded = HermesJson.decodeFromString<SessionsResponse>(json)

        assertEquals("s1", decoded.sessions?.single()?.sessionId)
        assertEquals("Port Android", decoded.sessions?.single()?.title)
    }

    @Test
    fun gitBranchesDecodeRichServerShape() {
        val json = """
            {
              "branches": {
                "is_git": true,
                "current": "main",
                "local": [
                  {"name": "main", "subject": "Keep Android branch picker aligned"}
                ],
                "remote": [
                  {"name": "origin/main", "ahead": 0, "behind": 1}
                ]
              }
            }
        """.trimIndent()

        val decoded = HermesJson.decodeFromString<GitBranchesResponse>(json)

        assertEquals("main", decoded.branches?.current)
        assertEquals("main", decoded.branches?.local?.single()?.name)
        assertEquals("origin/main", decoded.branches?.remote?.single()?.name)
        assertEquals(1, decoded.branches?.remote?.single()?.behind)
    }

    @Test
    fun cronScheduleDecodesWhenServerReturnsObject() {
        val json = """
            {
              "jobs": [
                {
                  "id": "job-1",
                  "name": "Nightly summary",
                  "schedule": {"kind": "cron", "expression": "0 7 * * *"},
                  "toast_notifications": true
                }
              ]
            }
        """.trimIndent()

        val decoded = HermesJson.decodeFromString<CronsResponse>(json)

        assertEquals("job-1", decoded.jobs?.single()?.id)
        assertEquals("Nightly summary", decoded.jobs?.single()?.name)
        assertEquals(true, decoded.jobs?.single()?.toastNotifications)
    }

    @Test
    fun goalResponseDecodesKickoffPrompt() {
        val json = """
            {
              "ok": true,
              "message": "Goal started.",
              "goal": {"goal": "Port Android", "turns_used": 1, "max_turns": 8},
              "kickoff_prompt": "Continue the port."
            }
        """.trimIndent()

        val decoded = HermesJson.decodeFromString<GoalSubmissionResponse>(json)

        assertEquals("Goal started.", decoded.message)
        assertEquals("Port Android", decoded.goal?.goal)
        assertEquals(1, decoded.goal?.turnsUsed)
        assertEquals("Continue the port.", decoded.kickoffPrompt)
    }
}
