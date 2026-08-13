package com.uzairansar.hermex.ui.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptAutoScrollPolicyTest {
    @Test
    fun upwardDragDisablesFollowingEvenWhileStillNearBottom() {
        assertFalse(
            transcriptFollowState(
                currentlyFollowing = true,
                observation = TranscriptScrollObservation(
                    isUserDragging = true,
                    lastScrolledBackward = true,
                    isNearBottom = true,
                ),
            ),
        )
    }

    @Test
    fun downwardDragNearBottomReenablesFollowing() {
        assertTrue(
            transcriptFollowState(
                currentlyFollowing = false,
                observation = TranscriptScrollObservation(
                    isUserDragging = true,
                    lastScrolledBackward = false,
                    isNearBottom = true,
                ),
            ),
        )
    }

    @Test
    fun downwardDragAwayFromBottomKeepsFollowingDisabled() {
        assertFalse(
            transcriptFollowState(
                currentlyFollowing = false,
                observation = TranscriptScrollObservation(
                    isUserDragging = true,
                    lastScrolledBackward = false,
                    isNearBottom = false,
                ),
            ),
        )
    }

    @Test
    fun idleLayoutChangesPreserveUserChoice() {
        assertFalse(
            transcriptFollowState(
                currentlyFollowing = false,
                observation = TranscriptScrollObservation(
                    isUserDragging = false,
                    lastScrolledBackward = false,
                    isNearBottom = true,
                ),
            ),
        )
    }

    @Test
    fun activeUserDragBlocksPendingAutoScroll() {
        assertFalse(
            shouldAutoScrollTranscript(
                followsBottom = true,
                isScrollInProgress = true,
            ),
        )
    }

    @Test
    fun idleFollowerMayAutoScrollForNewContent() {
        assertTrue(
            shouldAutoScrollTranscript(
                followsBottom = true,
                isScrollInProgress = false,
            ),
        )
    }

    @Test
    fun cooldownBlocksAutoScrollAfterUserLetsGo() {
        assertFalse(
            shouldAutoScrollTranscript(
                followsBottom = true,
                isScrollInProgress = false,
                isUserScrollCooldownActive = true,
            ),
        )
    }

    @Test
    fun tallLastMessageUsesPixelDistanceInsteadOfItemCount() {
        assertFalse(
            isTranscriptNearBottom(
                totalItemsCount = 4,
                lastVisibleIndex = 3,
                lastVisibleOffset = -200,
                lastVisibleSize = 1_200,
                viewportEndOffset = 700,
                tolerancePixels = 160,
            ),
        )
        assertTrue(
            isTranscriptNearBottom(
                totalItemsCount = 4,
                lastVisibleIndex = 3,
                lastVisibleOffset = -350,
                lastVisibleSize = 1_200,
                viewportEndOffset = 700,
                tolerancePixels = 160,
            ),
        )
    }

    @Test
    fun readingOlderChromeRequiresHysteresisAndExpandsNearBottom() {
        assertFalse(
            transcriptReadingOlderState(
                currentlyReadingOlder = false,
                isNearBottom = false,
                distanceFromBottomPixels = 140,
                nearBottomTolerancePixels = 80,
                hysteresisPixels = 64,
            ),
        )
        assertTrue(
            transcriptReadingOlderState(
                currentlyReadingOlder = false,
                isNearBottom = false,
                distanceFromBottomPixels = 145,
                nearBottomTolerancePixels = 80,
                hysteresisPixels = 64,
            ),
        )
        assertFalse(
            transcriptReadingOlderState(
                currentlyReadingOlder = true,
                isNearBottom = true,
                distanceFromBottomPixels = 80,
                nearBottomTolerancePixels = 80,
                hysteresisPixels = 64,
            ),
        )
    }

    @Test
    fun oversizedLastMessageIsNotAtBottomUntilItsEndIsVisible() {
        assertFalse(
            isTranscriptBottomVisible(
                totalItemsCount = 4,
                lastVisibleIndex = 3,
                lastVisibleOffset = 120,
                lastVisibleSize = 1_200,
                viewportEndOffset = 900,
            ),
        )
        assertTrue(
            isTranscriptBottomVisible(
                totalItemsCount = 4,
                lastVisibleIndex = 3,
                lastVisibleOffset = -300,
                lastVisibleSize = 1_200,
                viewportEndOffset = 900,
            ),
        )
    }
}
