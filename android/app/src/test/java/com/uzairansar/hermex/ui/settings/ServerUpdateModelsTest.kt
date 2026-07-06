package com.uzairansar.hermex.ui.settings

import com.uzairansar.hermex.core.model.UpdateTargetInfo
import com.uzairansar.hermex.core.model.UpdatesApplyResponse
import com.uzairansar.hermex.core.model.UpdatesCheckResponse
import org.junit.Assert.assertEquals
import org.junit.Test

class ServerUpdateModelsTest {
    @Test
    fun checkResponseDistinguishesForcedOutcomes() {
        assertEquals(
            ForcedUpdateCheckOutcome.UpdateAvailable(3),
            UpdatesCheckResponse(webui = UpdateTargetInfo(behind = 3)).forcedCheckOutcome(),
        )
        assertEquals(
            ForcedUpdateCheckOutcome.UpToDate,
            UpdatesCheckResponse(webui = UpdateTargetInfo(behind = 0)).forcedCheckOutcome(),
        )
        assertEquals(
            ForcedUpdateCheckOutcome.Disabled,
            UpdatesCheckResponse(disabled = true).forcedCheckOutcome(),
        )
        assertEquals(
            ForcedUpdateCheckOutcome.Error,
            UpdatesCheckResponse(webui = UpdateTargetInfo(error = "fetch failed")).forcedCheckOutcome(),
        )
        assertEquals(
            ForcedUpdateCheckOutcome.Error,
            UpdatesCheckResponse(webui = UpdateTargetInfo(behind = 1, staleCheck = true)).forcedCheckOutcome(),
        )
    }

    @Test
    fun passiveUpdateStateCollapsesDisabledAndErrors() {
        assertEquals(
            WebUiUpdateState.UpdateAvailable(2),
            UpdatesCheckResponse(webui = UpdateTargetInfo(behind = 2)).webUiUpdateState(),
        )
        assertEquals(
            WebUiUpdateState.UpToDate,
            UpdatesCheckResponse(webui = UpdateTargetInfo(behind = 0)).webUiUpdateState(),
        )
        assertEquals(
            WebUiUpdateState.Unavailable,
            UpdatesCheckResponse(disabled = true).webUiUpdateState(),
        )
    }

    @Test
    fun applyOutcomePrioritizesRestartBlockedBeforeOkFalse() {
        assertEquals(
            ServerUpdateApplyOutcome.RestartBlocked,
            UpdatesApplyResponse(ok = false, restartBlocked = true).applyOutcome(),
        )
        assertEquals(
            ServerUpdateApplyOutcome.Applying,
            UpdatesApplyResponse(ok = true, restartScheduled = true).applyOutcome(),
        )
        assertEquals(
            ServerUpdateApplyOutcome.Failed,
            UpdatesApplyResponse(ok = false, conflict = true).applyOutcome(),
        )
        assertEquals(
            "Server said no",
            UpdatesApplyResponse(message = " Server said no ").displayMessage("Fallback"),
        )
    }
}
