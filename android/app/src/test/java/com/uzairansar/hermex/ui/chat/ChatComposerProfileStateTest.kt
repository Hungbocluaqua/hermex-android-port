package com.uzairansar.hermex.ui.chat

import com.uzairansar.hermex.core.model.ProfileSummary
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatComposerProfileStateTest {
    @Test
    fun profileControlIsHiddenForSingleProfileMode() {
        val state = ChatUiState(
            profileOptions = listOf(ProfileSummary(name = "default")),
            isSingleProfileMode = true,
        )

        assertFalse(state.showsProfileControl)
    }

    @Test
    fun profileControlRequiresMoreThanImplicitServerProfile() {
        assertFalse(ChatUiState(profileOptions = emptyList()).showsProfileControl)

        val state = ChatUiState(
            profileOptions = listOf(ProfileSummary(name = "default"), ProfileSummary(name = "work")),
            isSingleProfileMode = false,
        )

        assertTrue(state.showsProfileControl)
    }
}
