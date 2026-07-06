package com.uzairansar.hermex.ui.settings

import com.uzairansar.hermex.core.model.UpdatesApplyResponse
import com.uzairansar.hermex.core.model.UpdatesCheckResponse

sealed interface WebUiUpdateState {
    data object UpToDate : WebUiUpdateState
    data class UpdateAvailable(val behind: Int) : WebUiUpdateState
    data object Unavailable : WebUiUpdateState
}

sealed interface ForcedUpdateCheckOutcome {
    data object UpToDate : ForcedUpdateCheckOutcome
    data class UpdateAvailable(val behind: Int) : ForcedUpdateCheckOutcome
    data object Disabled : ForcedUpdateCheckOutcome
    data object Error : ForcedUpdateCheckOutcome
}

enum class ServerUpdateApplyPhase {
    Idle,
    Applying,
    Recovering,
    Blocked,
    Failed,
}

enum class ServerUpdateApplyOutcome {
    Applying,
    RestartBlocked,
    Failed,
}

fun UpdatesCheckResponse.forcedCheckOutcome(): ForcedUpdateCheckOutcome {
    if (disabled == true) return ForcedUpdateCheckOutcome.Disabled
    val info = webui ?: return ForcedUpdateCheckOutcome.Error
    if (info.error != null || info.staleCheck == true) return ForcedUpdateCheckOutcome.Error
    val behind = info.behind
    return if (behind != null && behind > 0) {
        ForcedUpdateCheckOutcome.UpdateAvailable(behind)
    } else {
        ForcedUpdateCheckOutcome.UpToDate
    }
}

fun UpdatesCheckResponse.webUiUpdateState(): WebUiUpdateState =
    when (val outcome = forcedCheckOutcome()) {
        is ForcedUpdateCheckOutcome.UpdateAvailable -> WebUiUpdateState.UpdateAvailable(outcome.behind)
        ForcedUpdateCheckOutcome.UpToDate -> WebUiUpdateState.UpToDate
        ForcedUpdateCheckOutcome.Disabled,
        ForcedUpdateCheckOutcome.Error,
        -> WebUiUpdateState.Unavailable
    }

fun UpdatesApplyResponse.applyOutcome(): ServerUpdateApplyOutcome =
    when {
        restartBlocked == true -> ServerUpdateApplyOutcome.RestartBlocked
        ok == true -> ServerUpdateApplyOutcome.Applying
        else -> ServerUpdateApplyOutcome.Failed
    }

fun UpdatesApplyResponse.displayMessage(fallback: String): String =
    message?.trim()?.takeIf { it.isNotBlank() } ?: fallback
