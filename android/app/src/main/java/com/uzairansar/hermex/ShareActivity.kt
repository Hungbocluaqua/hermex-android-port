package com.uzairansar.hermex

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.uzairansar.hermex.data.share.SharedAttachment
import com.uzairansar.hermex.data.share.SharedDraftStore
import com.uzairansar.hermex.data.share.SharedDraftPolicy
import com.uzairansar.hermex.data.share.copyAcceptedSharedAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class ShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            val store = SharedDraftStore(this@ShareActivity)
            val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
            val subject = intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString().orEmpty()
            val uris = mutableListOf<Uri>()
            intent.streamUri()?.let(uris::add)
            intent.streamUris()?.let(uris::addAll)
            val (saved, rejectedAttachmentCount) = withContext(Dispatchers.IO) {
                var rejected = (uris.size - SharedDraftPolicy.MAXIMUM_SHARED_ATTACHMENT_COUNT).coerceAtLeast(0)
                val attachments = buildList {
                    for (uri in uris.take(SharedDraftPolicy.MAXIMUM_SHARED_ATTACHMENT_COUNT)) {
                        val attachment = cacheSharedAttachment(uri)
                        if (attachment == null) rejected += 1 else add(attachment)
                    }
                }
                Pair(
                    store.savePendingDraft(
                        text = SharedDraftPolicy.draftText(subject = subject, text = text),
                        attachments = attachments,
                    ),
                    rejected,
                )
            }
            if (saved) {
                if (rejectedAttachmentCount > 0) {
                    Toast.makeText(
                        this@ShareActivity,
                        "Imported the share, but skipped $rejectedAttachmentCount unreadable, empty, oversized, or extra file(s).",
                        Toast.LENGTH_LONG,
                    ).show()
                }
                startActivity(Intent(this@ShareActivity, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    data = Uri.parse("hermes-agent://share")
                })
            } else {
                Toast.makeText(
                    this@ShareActivity,
                    "Hermex could not import this share. Files must be readable, non-empty, and no larger than 20 MB.",
                    Toast.LENGTH_LONG,
                ).show()
            }
            finish()
        }
    }

    private fun cacheSharedAttachment(uri: Uri): SharedAttachment? {
        val displayName = (
            runCatching { displayName(uri) }.getOrNull()
                ?: uri.lastPathSegment?.substringAfterLast('/')
        )
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: "shared-file"
        val safeName = displayName
            .replace(Regex("""[^\w.\- ]"""), "_")
            .ifBlank { "shared-file" }
        val file = File(cacheDir, "shared-${UUID.randomUUID()}-$safeName")
        val copiedBytes = runCatching {
            contentResolver.openInputStream(uri)?.use { input ->
                copyAcceptedSharedAttachment(input = input, destination = file)
            }
        }.getOrNull() ?: return null
        if (!SharedDraftPolicy.acceptsByteCount(copiedBytes)) {
            runCatching { file.delete() }
            return null
        }

        return SharedAttachment(
            uri = uri.toString(),
            displayName = displayName,
            mimeType = runCatching { contentResolver.getType(uri) }.getOrNull(),
            cachedPath = file.absolutePath,
        )
    }

    private fun displayName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) return cursor.getString(index)
        }
        return null
    }

    private fun Intent.streamUri(): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(Intent.EXTRA_STREAM)
        }

    private fun Intent.streamUris(): ArrayList<Uri>? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableArrayListExtra(Intent.EXTRA_STREAM)
        }
}
