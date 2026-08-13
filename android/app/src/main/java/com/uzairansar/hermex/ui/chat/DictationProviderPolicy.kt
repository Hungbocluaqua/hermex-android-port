package com.uzairansar.hermex.ui.chat

import com.uzairansar.hermex.data.preferences.DictationProviderPreference

enum class DictationProvider {
    Server,
    OnDevice,
}

object DictationProviderPolicy {
    fun primaryProvider(
        preference: DictationProviderPreference,
        serverConfigured: Boolean,
        onDeviceSupported: Boolean,
    ): DictationProvider? = when (preference) {
        DictationProviderPreference.ServerFirst -> when {
            serverConfigured -> DictationProvider.Server
            onDeviceSupported -> DictationProvider.OnDevice
            else -> null
        }
        DictationProviderPreference.OnDeviceFirst -> when {
            onDeviceSupported -> DictationProvider.OnDevice
            serverConfigured -> DictationProvider.Server
            else -> null
        }
        DictationProviderPreference.OnDeviceOnly -> DictationProvider.OnDevice.takeIf { onDeviceSupported }
    }
}
