package com.uzairansar.hermex.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenPlaybackPolicyTest {
    @Test
    fun serverTtsHonorsTheServerCharacterLimit() {
        assertTrue(ServerTtsPolicy.shouldUseServer("Hello"))
        assertTrue(ServerTtsPolicy.shouldUseServer("x".repeat(ServerTtsPolicy.MaximumCharacters)))
        assertFalse(ServerTtsPolicy.shouldUseServer("x".repeat(ServerTtsPolicy.MaximumCharacters + 1)))
        assertFalse(ServerTtsPolicy.shouldUseServer("  "))
    }

    @Test
    fun playbackSpeedRestoresOnlySupportedValues() {
        assertEquals(ListenPlaybackSpeed.Half, ListenPlaybackSpeed.fromStoredRate(0.5f))
        assertEquals(ListenPlaybackSpeed.OneAndHalf, ListenPlaybackSpeed.fromStoredRate(1.5f))
        assertEquals(ListenPlaybackSpeed.Normal, ListenPlaybackSpeed.fromStoredRate(1.25f))
    }

    @Test
    fun playbackDurationUsesMonospacedMinuteAndSecondFormatting() {
        assertEquals("0:00", formatPlaybackDuration(-1))
        assertEquals("0:05", formatPlaybackDuration(5_999))
        assertEquals("1:05", formatPlaybackDuration(65_000))
    }

    @Test
    fun playbackBarOnlyPersistsForLoadingOrSeekableServerAudio() {
        assertTrue(ListenPlaybackUiState(phase = ListenPlaybackPhase.Loading).showsPlaybackBar)
        assertTrue(
            ListenPlaybackUiState(
                phase = ListenPlaybackPhase.Playing,
                hasSeekableAudio = true,
            ).showsPlaybackBar,
        )
        assertFalse(
            ListenPlaybackUiState(
                phase = ListenPlaybackPhase.Playing,
                hasSeekableAudio = false,
            ).showsPlaybackBar,
        )
    }
}
