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

class SharedDraftStore(context: Context) {
    private val preferences = context.getSharedPreferences("hermex_share", Context.MODE_PRIVATE)
    private val cacheDirectory = context.cacheDir

    fun savePendingDraft(text: String, attachments: List<SharedAttachment>): Boolean {
        val attachmentSelection = selectSharedAttachments(attachments)
        deleteSharedAttachmentCaches(attachmentSelection.rejected, cacheDirectory)

        val trimmedText = text.trim()
        val previousDraft = loadPendingDraft(removeAfterLoad = false)
        if (trimmedText.isBlank() && attachmentSelection.accepted.isEmpty()) {
            preferences.edit().remove(KEY).apply()
            deleteSharedAttachmentCaches(previousDraft?.attachments.orEmpty(), cacheDirectory)
            return false
        }

        val encodedDraft = HermesJson.encodeToString(
            SharedDraft(
                text = trimmedText,
                attachments = attachmentSelection.accepted,
                uris = attachmentSelection.accepted.map { it.uri },
            ),
        )
        preferences.edit()
            .putString(KEY, encodedDraft)
            .apply()

        val retainedPaths = attachmentSelection.accepted.mapNotNull { it.cachedPath }.toSet()
        deleteSharedAttachmentCaches(
            previousDraft?.attachments.orEmpty().filterNot { it.cachedPath in retainedPaths },
            cacheDirectory,
        )
        return true
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
