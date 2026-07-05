package com.uzairansar.hermex

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import com.uzairansar.hermex.data.share.SharedAttachment
import com.uzairansar.hermex.data.share.SharedDraftStore
import java.io.File

class ShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = SharedDraftStore(this)
        val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()
        val uris = mutableListOf<Uri>()
        intent.streamUri()?.let(uris::add)
        intent.streamUris()?.let(uris::addAll)
        val attachments = uris.mapNotNull(::cacheSharedAttachment)
        store.savePendingDraft(
            text = listOf(subject, text).filter { it.isNotBlank() }.joinToString("\n\n"),
            attachments = attachments,
        )
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.parse("hermes-agent://share")
        })
        finish()
    }

    private fun cacheSharedAttachment(uri: Uri): SharedAttachment? {
        val displayName = displayName(uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "shared-file"
        val safeName = displayName.replace(Regex("""[^\w.\- ]"""), "_")
        val file = File(cacheDir, "shared-${System.currentTimeMillis()}-$safeName")
        return runCatching {
            contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Could not open shared file." }
                file.outputStream().use { output -> input.copyTo(output) }
            }
            SharedAttachment(
                uri = uri.toString(),
                displayName = displayName,
                mimeType = contentResolver.getType(uri),
                cachedPath = file.absolutePath,
            )
        }.getOrNull()
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
