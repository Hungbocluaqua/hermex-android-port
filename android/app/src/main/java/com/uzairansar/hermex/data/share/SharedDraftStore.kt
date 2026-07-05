package com.uzairansar.hermex.data.share

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import com.uzairansar.hermex.core.network.HermesJson

@Serializable
data class SharedDraft(
    val text: String,
    val attachments: List<SharedAttachment> = emptyList(),
    val uris: List<String> = emptyList(),
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
)

@Serializable
data class SharedAttachment(
    val uri: String,
    val displayName: String? = null,
    val mimeType: String? = null,
    val cachedPath: String? = null,
)

class SharedDraftStore(context: Context) {
    private val preferences = context.getSharedPreferences("hermex_share", Context.MODE_PRIVATE)

    fun savePendingDraft(text: String, attachments: List<SharedAttachment>) {
        if (text.isBlank() && attachments.isEmpty()) return
        preferences.edit()
            .putString(
                KEY,
                HermesJson.encodeToString(
                    SharedDraft(
                        text = text.trim(),
                        attachments = attachments,
                        uris = attachments.map { it.uri },
                    ),
                ),
            )
            .apply()
    }

    fun loadPendingDraft(removeAfterLoad: Boolean = true): SharedDraft? {
        val value = preferences.getString(KEY, null) ?: return null
        if (removeAfterLoad) preferences.edit().remove(KEY).apply()
        return runCatching { HermesJson.decodeFromString<SharedDraft>(value) }.getOrNull()
    }

    fun hasPendingDraft(): Boolean = preferences.contains(KEY)

    companion object {
        private const val KEY = "pending_share_draft"
    }
}
