package com.uzairansar.hermex.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.uzairansar.hermex.core.model.ModelSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Locale

private val Context.localSettingsDataStore by preferencesDataStore(name = "hermex_local_settings")

fun defaultRtlChatLayoutEnabled(locale: Locale = Locale.getDefault()): Boolean =
    locale.language.lowercase(Locale.US) in setOf("ar", "fa", "he", "iw", "ps", "sd", "ug", "ur", "yi")

data class ChatDisplaySettings(
    val showThinkingAndToolCards: Boolean = true,
    val thinkingCardsStartExpanded: Boolean = false,
    val toolCardsStartExpanded: Boolean = false,
    val hidesAttachmentPaths: Boolean = true,
    val showsAssistantTurnTimestamps: Boolean = false,
    val rtlChatLayoutEnabled: Boolean = defaultRtlChatLayoutEnabled(),
    val wrapsCodeBlockLines: Boolean = false,
    val streamedTextAnimationEnabled: Boolean = true,
    val showsStatusNotificationResponseExcerpts: Boolean = false,
)

data class SessionRowDisplaySettings(
    val showMessageCount: Boolean = true,
    val showWorkspace: Boolean = true,
    val showCronSessions: Boolean = true,
)

class LocalSettingsRepository(context: Context) {
    private val dataStore = context.applicationContext.localSettingsDataStore

    val themeMode: Flow<AppThemeMode> = dataStore.data.map { preferences ->
        AppThemeMode.fromStorageValue(preferences[THEME_MODE])
    }

    val streamingSendBehavior: Flow<StreamingSendBehavior> = dataStore.data.map { preferences ->
        StreamingSendBehavior.fromStorageValue(preferences[STREAMING_SEND_BEHAVIOR])
    }

    val tintPrimaryActionsWithThemeColor: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[TINTS_PRIMARY_ACTIONS_WITH_THEME_COLOR] ?: false
    }

    val hapticsEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[HAPTICS_ENABLED] ?: true
    }

    val chatDisplaySettings: Flow<ChatDisplaySettings> = dataStore.data.map { preferences ->
        ChatDisplaySettings(
            showThinkingAndToolCards = preferences[SHOW_THINKING_AND_TOOL_CARDS] ?: true,
            thinkingCardsStartExpanded = preferences[THINKING_CARDS_START_EXPANDED] ?: false,
            toolCardsStartExpanded = preferences[TOOL_CARDS_START_EXPANDED] ?: false,
            hidesAttachmentPaths = preferences[HIDES_ATTACHMENT_PATHS] ?: true,
            showsAssistantTurnTimestamps = preferences[SHOWS_ASSISTANT_TURN_TIMESTAMPS] ?: false,
            rtlChatLayoutEnabled = preferences[RTL_CHAT_LAYOUT_ENABLED] ?: defaultRtlChatLayoutEnabled(),
            wrapsCodeBlockLines = preferences[WRAPS_CODE_BLOCK_LINES] ?: false,
            streamedTextAnimationEnabled = preferences[STREAMED_TEXT_ANIMATION_ENABLED] ?: true,
            showsStatusNotificationResponseExcerpts = preferences[SHOWS_STATUS_NOTIFICATION_RESPONSE_EXCERPTS] ?: false,
        )
    }

    val sessionRowDisplaySettings: Flow<SessionRowDisplaySettings> = dataStore.data.map { preferences ->
        SessionRowDisplaySettings(
            showMessageCount = preferences[SESSION_ROW_SHOW_MESSAGE_COUNT] ?: true,
            showWorkspace = preferences[SESSION_ROW_SHOW_WORKSPACE] ?: true,
            showCronSessions = preferences[SESSION_ROW_SHOW_CRON_SESSIONS] ?: true,
        )
    }

    val responseCompletionNotificationsEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[RESPONSE_COMPLETION_NOTIFICATIONS_ENABLED] ?: false
    }

    val hasRequestedResponseCompletionNotificationPermission: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[RESPONSE_COMPLETION_NOTIFICATION_PERMISSION_REQUESTED] ?: false
    }

    val favoriteModelKeys: Flow<List<ModelFavoriteKey>> = dataStore.data.map { preferences ->
        decodeModelKeys(preferences[FAVORITE_MODEL_KEYS]).deduplicatedModelKeys()
    }

    val recentModelKeys: Flow<List<ModelFavoriteKey>> = dataStore.data.map { preferences ->
        decodeModelKeys(preferences[RECENT_MODEL_KEYS]).limitedDeduplicatedModelKeys()
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        dataStore.edit { preferences ->
            preferences[THEME_MODE] = mode.storageValue
        }
    }

    suspend fun setStreamingSendBehavior(behavior: StreamingSendBehavior) {
        dataStore.edit { preferences ->
            preferences[STREAMING_SEND_BEHAVIOR] = behavior.storageValue
        }
    }

    suspend fun setTintPrimaryActionsWithThemeColor(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[TINTS_PRIMARY_ACTIONS_WITH_THEME_COLOR] = enabled
        }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[HAPTICS_ENABLED] = enabled
        }
    }

    fun showCliSessions(serverId: String): Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[showCliSessionsKey(serverId)] ?: true
        }

    suspend fun currentShowCliSessions(serverId: String): Boolean =
        showCliSessions(serverId).first()

    suspend fun setShowCliSessions(serverId: String, enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[showCliSessionsKey(serverId)] = enabled
        }
    }

    suspend fun setShowThinkingAndToolCards(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SHOW_THINKING_AND_TOOL_CARDS] = enabled
        }
    }

    suspend fun setThinkingCardsStartExpanded(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[THINKING_CARDS_START_EXPANDED] = enabled
        }
    }

    suspend fun setToolCardsStartExpanded(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[TOOL_CARDS_START_EXPANDED] = enabled
        }
    }

    suspend fun setHidesAttachmentPaths(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[HIDES_ATTACHMENT_PATHS] = enabled
        }
    }

    suspend fun setShowsAssistantTurnTimestamps(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SHOWS_ASSISTANT_TURN_TIMESTAMPS] = enabled
        }
    }

    suspend fun setRtlChatLayoutEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[RTL_CHAT_LAYOUT_ENABLED] = enabled
        }
    }

    suspend fun setWrapsCodeBlockLines(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[WRAPS_CODE_BLOCK_LINES] = enabled
        }
    }

    suspend fun setStreamedTextAnimationEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[STREAMED_TEXT_ANIMATION_ENABLED] = enabled
        }
    }

    suspend fun setShowsStatusNotificationResponseExcerpts(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SHOWS_STATUS_NOTIFICATION_RESPONSE_EXCERPTS] = enabled
        }
    }

    suspend fun setSessionRowShowMessageCount(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SESSION_ROW_SHOW_MESSAGE_COUNT] = enabled
        }
    }

    suspend fun setSessionRowShowWorkspace(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SESSION_ROW_SHOW_WORKSPACE] = enabled
        }
    }

    suspend fun setShowCronSessions(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SESSION_ROW_SHOW_CRON_SESSIONS] = enabled
        }
    }

    suspend fun setResponseCompletionNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[RESPONSE_COMPLETION_NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun setHasRequestedResponseCompletionNotificationPermission(requested: Boolean) {
        dataStore.edit { preferences ->
            preferences[RESPONSE_COMPLETION_NOTIFICATION_PERMISSION_REQUESTED] = requested
        }
    }

    suspend fun toggleFavoriteModel(model: ModelSummary) {
        val key = model.favoriteKeyOrNull() ?: return
        dataStore.edit { preferences ->
            val keys = decodeModelKeys(preferences[FAVORITE_MODEL_KEYS])
            val next = if (key in keys) {
                keys.filter { it != key }
            } else {
                keys + key
            }
            preferences[FAVORITE_MODEL_KEYS] = encodeModelKeys(next.deduplicatedModelKeys())
        }
    }

    suspend fun removeFavoriteModel(model: ModelSummary) {
        val key = model.favoriteKeyOrNull() ?: return
        dataStore.edit { preferences ->
            preferences[FAVORITE_MODEL_KEYS] = encodeModelKeys(
                decodeModelKeys(preferences[FAVORITE_MODEL_KEYS])
                    .filter { it != key }
                    .deduplicatedModelKeys(),
            )
        }
    }

    suspend fun recordRecentModel(model: ModelSummary) {
        val key = model.favoriteKeyOrNull() ?: return
        dataStore.edit { preferences ->
            preferences[RECENT_MODEL_KEYS] = encodeModelKeys(
                (listOf(key) + decodeModelKeys(preferences[RECENT_MODEL_KEYS]))
                    .limitedDeduplicatedModelKeys(),
            )
        }
    }

    suspend fun removeRecentModel(model: ModelSummary) {
        val key = model.favoriteKeyOrNull() ?: return
        dataStore.edit { preferences ->
            preferences[RECENT_MODEL_KEYS] = encodeModelKeys(
                decodeModelKeys(preferences[RECENT_MODEL_KEYS])
                    .filter { it != key }
                    .limitedDeduplicatedModelKeys(),
            )
        }
    }

    private companion object {
        val MODEL_KEY_JSON = Json { ignoreUnknownKeys = true }
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val STREAMING_SEND_BEHAVIOR = stringPreferencesKey("streamingSendBehavior")
        val TINTS_PRIMARY_ACTIONS_WITH_THEME_COLOR = booleanPreferencesKey("appearance.tintsPrimaryActionsWithThemeColor")
        val HAPTICS_ENABLED = booleanPreferencesKey("appHaptics.isEnabled")
        val SHOW_THINKING_AND_TOOL_CARDS = booleanPreferencesKey("chatTranscript.showsThinkingAndToolCards")
        val THINKING_CARDS_START_EXPANDED = booleanPreferencesKey("chatTranscript.thinkingCardsStartExpanded")
        val TOOL_CARDS_START_EXPANDED = booleanPreferencesKey("chatTranscript.toolCardsStartExpanded")
        val HIDES_ATTACHMENT_PATHS = booleanPreferencesKey("chatTranscript.hidesAttachmentPaths")
        val SHOWS_ASSISTANT_TURN_TIMESTAMPS = booleanPreferencesKey("chatTranscript.showsAssistantTurnTimestamps")
        val RTL_CHAT_LAYOUT_ENABLED = booleanPreferencesKey("chatTranscript.rtlChatLayoutEnabled")
        val WRAPS_CODE_BLOCK_LINES = booleanPreferencesKey("chatTranscript.wrapsCodeBlockLines")
        val STREAMED_TEXT_ANIMATION_ENABLED = booleanPreferencesKey("chatTranscript.streamedTextAnimationEnabled")
        val SHOWS_STATUS_NOTIFICATION_RESPONSE_EXCERPTS = booleanPreferencesKey("chatTranscript.showsStatusNotificationResponseExcerpts")
        val SESSION_ROW_SHOW_MESSAGE_COUNT = booleanPreferencesKey("sessionRow.showMessageCount")
        val SESSION_ROW_SHOW_WORKSPACE = booleanPreferencesKey("sessionRow.showWorkspace")
        val SESSION_ROW_SHOW_CRON_SESSIONS = booleanPreferencesKey("sessionRow.showCronSessions")
        val RESPONSE_COMPLETION_NOTIFICATIONS_ENABLED = booleanPreferencesKey("responseCompletionNotifications.isEnabled")
        val RESPONSE_COMPLETION_NOTIFICATION_PERMISSION_REQUESTED = booleanPreferencesKey("responseCompletionNotifications.hasRequestedPermission")
        val FAVORITE_MODEL_KEYS = stringPreferencesKey("chatComposer.favoriteModels")
        val RECENT_MODEL_KEYS = stringPreferencesKey("chatComposer.recentModels")

        fun showCliSessionsKey(serverId: String) = booleanPreferencesKey("show_cli_sessions::$serverId")

        fun decodeModelKeys(value: String?): List<ModelFavoriteKey> =
            value
                ?.takeIf { it.isNotBlank() }
                ?.let { raw -> runCatching { MODEL_KEY_JSON.decodeFromString<List<ModelFavoriteKey>>(raw) }.getOrNull() }
                .orEmpty()

        fun encodeModelKeys(keys: List<ModelFavoriteKey>): String =
            MODEL_KEY_JSON.encodeToString(keys)
    }
}
