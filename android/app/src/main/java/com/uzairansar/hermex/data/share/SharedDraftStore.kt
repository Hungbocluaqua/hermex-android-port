package com.uzairansar.hermex.data.share

import android.content.Context
import com.uzairansar.hermex.core.network.HermesJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.File
import java.io.InputStream

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

internal data class SharedAttachmentSelection(
    val accepted: List<SharedAttachment>,
    val rejected: List<SharedAttachment>,
)

internal object SharedDraftPolicy {
    const val MAXIMUM_SHARED_ATTACHMENT_BYTES = 20L * 1_024 * 1_024
    const val MAXIMUM_SHARED_ATTACHMENT_COUNT = 10

    fun draftText(subject: String?, text: String?): String =
        listOf(subject, text)
            .mapNotNull { value -> value?.trim()?.takeIf(String::isNotEmpty) }
            .distinct()
            .joinToString("\n\n")

    fun acceptsByteCount(byteCount: Long): Boolean =
        byteCount in 1..MAXIMUM_SHARED_ATTACHMENT_BYTES
}

internal fun selectSharedAttachments(
    attachments: List<SharedAttachment>,
): SharedAttachmentSelection {
    val accepted = mutableListOf<SharedAttachment>()
    val rejected = mutableListOf<SharedAttachment>()

    attachments.forEach { attachment ->
        val cachedPath = attachment.cachedPath?.takeIf { it.isNotBlank() }
        val hasValidSource = if (cachedPath == null) {
            attachment.uri.isNotBlank()
        } else {
            val file = File(cachedPath)
            file.isFile && SharedDraftPolicy.acceptsByteCount(file.length())
        }

        if (hasValidSource && accepted.size < SharedDraftPolicy.MAXIMUM_SHARED_ATTACHMENT_COUNT) {
            accepted += attachment
        } else {
            rejected += attachment
        }
    }

    return SharedAttachmentSelection(accepted = accepted, rejected = rejected)
}

internal fun copyAcceptedSharedAttachment(
    input: InputStream,
    destination: File,
    maximumBytes: Long = SharedDraftPolicy.MAXIMUM_SHARED_ATTACHMENT_BYTES,
): Long? {
    var accepted = false
    return try {
        var totalBytes = 0L
        destination.parentFile?.mkdirs()
        destination.outputStream().buffered().use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue

                totalBytes += count
                if (totalBytes > maximumBytes) return null
                output.write(buffer, 0, count)
            }
        }

        if (totalBytes <= 0L) return null
        accepted = true
        totalBytes
    } catch (_: Exception) {
        null
    } finally {
        if (!accepted) runCatching { destination.delete() }
    }
}

internal fun deleteSharedAttachmentCaches(
    attachments: Iterable<SharedAttachment>,
    cacheDirectory: File,
) {
    val canonicalCacheDirectory = runCatching { cacheDirectory.canonicalFile }.getOrNull() ?: return
    val cachePathPrefix = canonicalCacheDirectory.path + File.separator

    attachments.forEach { attachment ->
        val file = attachment.cachedPath
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?: return@forEach
        val canonicalFile = runCatching { file.canonicalFile }.getOrNull() ?: return@forEach
        if (canonicalFile.isFile && canonicalFile.path.startsWith(cachePathPrefix)) {
            runCatching { canonicalFile.delete() }
        }
    }
}

class SharedDraftStore(
    context: Context,
    preferencesName: String = "hermex_share",
) {
    private val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    private val cacheDirectory = context.cacheDir

    fun savePendingDraft(text: String, attachments: List<SharedAttachment>): Boolean = synchronized(STORE_LOCK) {
        val attachmentSelection = selectSharedAttachments(attachments)
        deleteSharedAttachmentCaches(attachmentSelection.rejected, cacheDirectory)

        val trimmedText = text.trim()
        val previousDraft = loadPendingDraft(removeAfterLoad = false)
        val createdAtEpochMillis = maxOf(
            System.currentTimeMillis(),
            (previousDraft?.createdAtEpochMillis ?: Long.MIN_VALUE).let { previous ->
                if (previous == Long.MAX_VALUE) Long.MAX_VALUE else previous + 1
            },
        )
        if (trimmedText.isBlank() && attachmentSelection.accepted.isEmpty()) {
            if (preferences.edit().remove(KEY).commit()) {
                deleteSharedAttachmentCaches(previousDraft?.attachments.orEmpty(), cacheDirectory)
            }
            return@synchronized false
        }

        val encodedDraft = HermesJson.encodeToString(
            SharedDraft(
                text = trimmedText,
                attachments = attachmentSelection.accepted,
                uris = attachmentSelection.accepted.map { it.uri },
                createdAtEpochMillis = createdAtEpochMillis,
            ),
        )
        val saved = preferences.edit()
            .putString(KEY, encodedDraft)
            .commit()
        if (!saved) {
            deleteSharedAttachmentCaches(attachmentSelection.accepted, cacheDirectory)
            return@synchronized false
        }

        val retainedPaths = attachmentSelection.accepted.mapNotNull { it.cachedPath }.toSet()
        deleteSharedAttachmentCaches(
            previousDraft?.attachments.orEmpty().filterNot { it.cachedPath in retainedPaths },
            cacheDirectory,
        )
        true
    }

    fun loadPendingDraft(removeAfterLoad: Boolean = true): SharedDraft? = synchronized(STORE_LOCK) {
        val value = preferences.getString(KEY, null) ?: return@synchronized null
        val draft = runCatching { HermesJson.decodeFromString<SharedDraft>(value) }.getOrNull()
        if (draft == null) {
            preferences.edit().remove(KEY).apply()
            return@synchronized null
        }
        if (removeAfterLoad && !preferences.edit().remove(KEY).commit()) return@synchronized null
        draft
    }

    fun commitImportedDraft(
        expectedCreatedAtEpochMillis: Long,
        remainingAttachments: List<SharedAttachment>,
    ): Boolean = synchronized(STORE_LOCK) {
        val current = loadPendingDraft(removeAfterLoad = false) ?: return@synchronized true
        if (current.createdAtEpochMillis != expectedCreatedAtEpochMillis) return@synchronized false

        val selection = selectSharedAttachments(remainingAttachments)
        val replacement = selection.accepted.takeIf { it.isNotEmpty() }?.let { attachments ->
            HermesJson.encodeToString(
                SharedDraft(
                    text = "",
                    attachments = attachments,
                    uris = attachments.map { it.uri },
                    createdAtEpochMillis = maxOf(
                        System.currentTimeMillis(),
                        if (current.createdAtEpochMillis == Long.MAX_VALUE) Long.MAX_VALUE else current.createdAtEpochMillis + 1,
                    ),
                ),
            )
        }
        val editor = preferences.edit()
        if (replacement == null) editor.remove(KEY) else editor.putString(KEY, replacement)
        if (!editor.commit()) return@synchronized false

        val retainedPaths = selection.accepted.mapNotNull { it.cachedPath }.toSet()
        deleteSharedAttachmentCaches(
            current.attachments.filterNot { it.cachedPath in retainedPaths } + selection.rejected,
            cacheDirectory,
        )
        true
    }

    fun hasPendingDraft(): Boolean = loadPendingDraft(removeAfterLoad = false) != null

    companion object {
        private const val KEY = "pending_share_draft"
        private val STORE_LOCK = Any()
    }
}
