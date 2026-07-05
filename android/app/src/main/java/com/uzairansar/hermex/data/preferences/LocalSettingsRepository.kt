package com.uzairansar.hermex.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.localSettingsDataStore by preferencesDataStore(name = "hermex_local_settings")

class LocalSettingsRepository(context: Context) {
    private val dataStore = context.applicationContext.localSettingsDataStore

    val themeMode: Flow<AppThemeMode> = dataStore.data.map { preferences ->
        AppThemeMode.fromStorageValue(preferences[THEME_MODE])
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        dataStore.edit { preferences ->
            preferences[THEME_MODE] = mode.storageValue
        }
    }

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }
}
