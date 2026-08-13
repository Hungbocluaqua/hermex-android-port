package com.uzairansar.hermex.ui.chat

internal data class TranscriptScrollObservation(
    val isUserDragging: Boolean,
    val lastScrolledBackward: Boolean,
    val isNearBottom: Boolean,
)

internal data class TranscriptScrollMetrics(
    val observation: TranscriptScrollObservation,
    val distanceFromBottomPixels: Int,
)

internal const val TRANSCRIPT_USER_SCROLL_COOLDOWN_MILLIS = 250L

internal fun transcriptFollowState(
    currentlyFollowing: Boolean,
    observation: TranscriptScrollObservation,
): Boolean = when {
    !observation.isUserDragging -> currentlyFollowing
    observation.lastScrolledBackward -> false
    observation.isNearBottom -> true
    else -> currentlyFollowing
}

internal fun shouldAutoScrollTranscript(
    followsBottom: Boolean,
    isScrollInProgress: Boolean,
    isUserScrollCooldownActive: Boolean = false,
): Boolean = followsBottom && !isScrollInProgress && !isUserScrollCooldownActive

internal fun isTranscriptNearBottom(
    totalItemsCount: Int,
    lastVisibleIndex: Int,
    lastVisibleOffset: Int,
    lastVisibleSize: Int,
    viewportEndOffset: Int,
    tolerancePixels: Int,
): Boolean = totalItemsCount == 0 || (
    lastVisibleIndex == totalItemsCount - 1 &&
        lastVisibleOffset + lastVisibleSize - viewportEndOffset <= tolerancePixels
    )

internal fun transcriptReadingOlderState(
    currentlyReadingOlder: Boolean,
    isNearBottom: Boolean,
    distanceFromBottomPixels: Int,
    nearBottomTolerancePixels: Int,
    hysteresisPixels: Int,
): Boolean = when {
    isNearBottom -> false
    currentlyReadingOlder -> true
    else -> distanceFromBottomPixels > nearBottomTolerancePixels + hysteresisPixels
}

internal fun isTranscriptBottomVisible(
    totalItemsCount: Int,
    lastVisibleIndex: Int,
    lastVisibleOffset: Int,
    lastVisibleSize: Int,
    viewportEndOffset: Int,
    tolerancePixels: Int = 2,
): Boolean = totalItemsCount == 0 || (
    lastVisibleIndex == totalItemsCount - 1 &&
        lastVisibleOffset + lastVisibleSize <= viewportEndOffset + tolerancePixels
    )
