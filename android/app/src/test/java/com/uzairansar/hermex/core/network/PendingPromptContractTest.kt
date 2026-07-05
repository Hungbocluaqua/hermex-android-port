package com.uzairansar.hermex.core.network

import com.uzairansar.hermex.core.model.ApprovalChoice
import com.uzairansar.hermex.core.model.ApprovalRespondRequest
import com.uzairansar.hermex.core.model.BranchSessionRequest
import com.uzairansar.hermex.core.model.ClarifyRespondRequest
import com.uzairansar.hermex.core.model.CompressSessionRequest
import com.uzairansar.hermex.core.model.CronCreateRequest
import com.uzairansar.hermex.core.model.CronJobIdRequest
import com.uzairansar.hermex.core.model.CronUpdateRequest
import com.uzairansar.hermex.core.model.GitCheckoutRequest
import com.uzairansar.hermex.core.model.GoalRequest
import com.uzairansar.hermex.core.model.MemoryWriteRequest
import com.uzairansar.hermex.core.model.MoveSessionRequest
import com.uzairansar.hermex.core.model.RenameSessionRequest
import com.uzairansar.hermex.core.model.ToggleSkillRequest
import com.uzairansar.hermex.core.model.TtsSynthesisRequest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingPromptContractTest {
    @Test
    fun approvalResponseUsesChoiceAndApprovalIdKeys() {
        val body = HermesJson.encodeToString(
            ApprovalRespondRequest(
                sessionId = "session-1",
                choice = ApprovalChoice.Session,
                approvalId = "approval-1",
            ),
        )

        assertTrue(body.contains(""""session_id":"session-1""""))
        assertTrue(body.contains(""""choice":"session""""))
        assertTrue(body.contains(""""approval_id":"approval-1""""))
    }

    @Test
    fun clarificationResponseUsesResponseAndClarifyIdKeys() {
        val body = HermesJson.encodeToString(
            ClarifyRespondRequest(
                sessionId = "session-1",
                clarifyId = "clarify-1",
                response = "Use the release branch.",
            ),
        )

        assertTrue(body.contains(""""session_id":"session-1""""))
        assertTrue(body.contains(""""clarify_id":"clarify-1""""))
        assertTrue(body.contains(""""response":"Use the release branch.""""))
    }

    @Test
    fun gitCheckoutIncludesDirtyModeWhenGuarded() {
        val body = HermesJson.encodeToString(
            GitCheckoutRequest(
                sessionId = "session-1",
                ref = "main",
                mode = "local",
                dirtyMode = "block",
            ),
        )

        assertTrue(body.contains(""""session_id":"session-1""""))
        assertTrue(body.contains(""""ref":"main""""))
        assertTrue(body.contains(""""mode":"local""""))
        assertTrue(body.contains(""""dirty_mode":"block""""))
    }

    @Test
    fun gitStashCheckoutBodyOmitsDirtyMode() {
        val body = HermesJson.encodeToString(
            GitCheckoutRequest(
                sessionId = "session-1",
                ref = "origin/main",
                mode = "remote",
                track = true,
            ),
        )

        assertTrue(body.contains(""""track":true"""))
        assertFalse(body.contains("dirty_mode"))
    }

    @Test
    fun ttsRequestSendsTextAndVoiceOnly() {
        val body = HermesJson.encodeToString(
            TtsSynthesisRequest(
                text = "Hello from Hermex.",
                voice = "en-US-AriaNeural",
            ),
        )

        assertTrue(body.contains(""""text":"Hello from Hermex.""""))
        assertTrue(body.contains(""""voice":"en-US-AriaNeural""""))
        assertFalse(body.contains("engine"))
    }

    @Test
    fun cronJobIdRequestUsesJobIdKey() {
        val body = HermesJson.encodeToString(CronJobIdRequest(jobId = "job-1", reason = "paused from Android"))

        assertTrue(body.contains(""""job_id":"job-1""""))
        assertTrue(body.contains(""""reason":"paused from Android""""))
    }

    @Test
    fun cronCreateRequestUsesServerKeys() {
        val body = HermesJson.encodeToString(
            CronCreateRequest(
                prompt = "Summarize yesterday.",
                schedule = "0 8 * * *",
                name = "Morning summary",
                deliver = "local",
                skills = listOf("summarizer", "calendar"),
                model = "gpt-5",
                profile = "default",
                toastNotifications = true,
            ),
        )

        assertTrue(body.contains(""""prompt":"Summarize yesterday.""""))
        assertTrue(body.contains(""""schedule":"0 8 * * *""""))
        assertTrue(body.contains(""""name":"Morning summary""""))
        assertTrue(body.contains(""""deliver":"local""""))
        assertTrue(body.contains(""""skills":["summarizer","calendar"]"""))
        assertTrue(body.contains(""""toast_notifications":true"""))
    }

    @Test
    fun cronUpdateRequestUsesJobIdAndOptionalFields() {
        val body = HermesJson.encodeToString(
            CronUpdateRequest(
                jobId = "job-1",
                prompt = "Updated prompt.",
                schedule = "@daily",
                name = null,
                skills = emptyList(),
                toastNotifications = false,
            ),
        )

        assertTrue(body.contains(""""job_id":"job-1""""))
        assertTrue(body.contains(""""prompt":"Updated prompt.""""))
        assertTrue(body.contains(""""schedule":"@daily""""))
        assertTrue(body.contains(""""skills":[]"""))
        assertTrue(body.contains(""""toast_notifications":false"""))
        assertFalse(body.contains(""""name""""))
    }

    @Test
    fun skillToggleUsesNameAndEnabledKeys() {
        val body = HermesJson.encodeToString(ToggleSkillRequest(name = "android-port", enabled = false))

        assertTrue(body.contains(""""name":"android-port""""))
        assertTrue(body.contains(""""enabled":false"""))
    }

    @Test
    fun memoryWriteUsesSectionAndContentKeys() {
        val body = HermesJson.encodeToString(MemoryWriteRequest(section = "user", content = "Prefers concise summaries."))

        assertTrue(body.contains(""""section":"user""""))
        assertTrue(body.contains(""""content":"Prefers concise summaries.""""))
    }

    @Test
    fun sessionMutationRequestsUseServerKeys() {
        val rename = HermesJson.encodeToString(RenameSessionRequest(sessionId = "s1", title = "Android parity"))
        val move = HermesJson.encodeToString(MoveSessionRequest(sessionId = "s1", projectId = "p1"))
        val branch = HermesJson.encodeToString(BranchSessionRequest(sessionId = "s1", keepCount = null, title = "Forked"))

        assertTrue(rename.contains(""""session_id":"s1""""))
        assertTrue(rename.contains(""""title":"Android parity""""))
        assertTrue(move.contains(""""project_id":"p1""""))
        assertTrue(branch.contains(""""session_id":"s1""""))
        assertTrue(branch.contains(""""title":"Forked""""))
        assertFalse(branch.contains("keep_count"))
    }

    @Test
    fun goalRequestUsesArgsKeyNotText() {
        val body = HermesJson.encodeToString(
            GoalRequest(
                sessionId = "s1",
                args = "finish the Android port",
                model = "gpt-5",
                modelProvider = "openai",
                profile = "default",
            ),
        )

        assertTrue(body.contains(""""session_id":"s1""""))
        assertTrue(body.contains(""""args":"finish the Android port""""))
        assertTrue(body.contains(""""model":"gpt-5""""))
        assertTrue(body.contains(""""model_provider":"openai""""))
        assertFalse(body.contains(""""text""""))
    }

    @Test
    fun compressSessionRequestUsesFocusTopicKey() {
        val body = HermesJson.encodeToString(CompressSessionRequest(sessionId = "s1", focusTopic = "Android parity"))

        assertTrue(body.contains(""""session_id":"s1""""))
        assertTrue(body.contains(""""focus_topic":"Android parity""""))
    }
}
