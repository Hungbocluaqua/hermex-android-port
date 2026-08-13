package com.uzairansar.hermex.ui.chat

import com.uzairansar.hermex.data.preferences.DictationProviderPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DictationProviderPolicyTest {
    @Test
    fun serverFirstPrefersHermesAndFallsBackToOnDevice() {
        assertEquals(
            DictationProvider.Server,
            DictationProviderPolicy.primaryProvider(DictationProviderPreference.ServerFirst, true, true),
        )
        assertEquals(
            DictationProvider.OnDevice,
            DictationProviderPolicy.primaryProvider(DictationProviderPreference.ServerFirst, false, true),
        )
    }

    @Test
    fun onDeviceFirstUsesServerWhenDeviceRecognizerIsUnavailable() {
        assertEquals(
            DictationProvider.OnDevice,
            DictationProviderPolicy.primaryProvider(DictationProviderPreference.OnDeviceFirst, true, true),
        )
        assertEquals(
            DictationProvider.Server,
            DictationProviderPolicy.primaryProvider(DictationProviderPreference.OnDeviceFirst, true, false),
        )
    }

    @Test
    fun onDeviceOnlyNeverSendsAudioToServer() {
        assertEquals(
            DictationProvider.OnDevice,
            DictationProviderPolicy.primaryProvider(DictationProviderPreference.OnDeviceOnly, true, true),
        )
        assertNull(DictationProviderPolicy.primaryProvider(DictationProviderPreference.OnDeviceOnly, true, false))
    }
}
