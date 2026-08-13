package com.uzairansar.hermex.core.model

fun SessionMutationResponse.isConfirmedMutation(): Boolean =
    error.isNullOrBlank() && ok != false && (ok == true || session != null)

fun SessionClearResponse.isConfirmedMutation(): Boolean =
    error.isNullOrBlank() && ok != false && (ok == true || session != null)

fun SessionCompressResponse.isConfirmedMutation(): Boolean =
    error.isNullOrBlank() && ok != false && (ok == true || session != null)

fun SessionUndoResponse.isConfirmedMutation(): Boolean =
    error.isNullOrBlank() && ok == true

fun SessionRetryResponse.isConfirmedMutation(): Boolean =
    error.isNullOrBlank() && ok == true

fun ProjectMutationResponse.isConfirmedMutation(): Boolean =
    error.isNullOrBlank() && ok != false && (ok == true || project != null)

fun CronMutationResponse.isConfirmedMutation(): Boolean =
    error.isNullOrBlank() && ok != false && (ok == true || job != null || !jobId.isNullOrBlank())

fun ChatCancelResponse.isConfirmedMutation(): Boolean =
    error.isNullOrBlank() && ok == true

fun GoalSubmissionResponse.isConfirmedMutation(): Boolean =
    error.isNullOrBlank() && ok == true && !action.equals("error", ignoreCase = true)

fun PersonalitySetResponse.isConfirmedPersonalityMutation(requestedName: String): Boolean =
    error.isNullOrBlank() &&
        ok == true &&
        if (requestedName.isBlank()) personality.isNullOrBlank() else personality == requestedName

fun ApprovalRespondResponse.isConfirmedMutation(): Boolean =
    ok == true || staleCleared == true || staleClearedSnake == true

fun SessionYoloResponse.isConfirmedYoloMutation(enabled: Boolean): Boolean =
    error.isNullOrBlank() && ok == true && isEnabled == enabled

fun ClarificationRespondResponse.isConfirmedClarification(responseText: String): Boolean =
    ok == true && (response == null || response == responseText)

fun DefaultModelResponse.isConfirmedDefaultModel(providerId: String?): Boolean =
    error.isNullOrBlank() &&
        ok == true &&
        !model.isNullOrBlank() &&
        (providerId.isNullOrBlank() || provider.equals(providerId, ignoreCase = true))

fun ProfileSwitchResponse.isConfirmedProfileSwitch(profileName: String): Boolean =
    error.isNullOrBlank() && active.equals(profileName, ignoreCase = true)

fun ProfileCreateResponse.isConfirmedMutation(): Boolean =
    error.isNullOrBlank() && ok == true

fun MemoryWriteResponse.isConfirmedMutation(): Boolean =
    error.isNullOrBlank() && ok == true

fun SettingsResponse.isConfirmedShowCliSessions(enabled: Boolean): Boolean =
    showCliSessions == enabled

fun SettingsResponse.isConfirmedShowClaudeCodeSessions(enabled: Boolean): Boolean =
    showClaudeCodeSessions == enabled
