package com.uzairansar.hermex

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.uzairansar.hermex.data.share.SharedDraftStore
import com.uzairansar.hermex.data.share.SharedAttachment
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShareIntentTest {
    @Test
    fun sharedAttachmentUrisCollectsAndDeduplicatesEveryAndroidUriSource() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val stream = Uri.parse("content://shares/stream")
        val data = Uri.parse("content://shares/data")
        val clipped = Uri.parse("content://shares/clipped")
        val clipData = ClipData.newUri(context.contentResolver, "shares", data).apply {
            addItem(ClipData.Item(clipped))
            addItem(ClipData.Item(stream))
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, stream)
            this.data = data
            this.clipData = clipData
        }

        assertEquals(listOf(stream, data, clipped), intent.sharedAttachmentUris(limit = 10).uris)
    }

    @Test
    fun sharedAttachmentUrisSupportsSendMultipleExtrasAndClipData() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val first = Uri.parse("content://shares/first")
        val second = Uri.parse("content://shares/second")
        val third = Uri.parse("content://shares/third")
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(first, second))
            clipData = ClipData.newUri(context.contentResolver, "shares", third)
        }

        assertEquals(listOf(first, second, third), intent.sharedAttachmentUris(limit = 10).uris)
    }

    @Test
    fun sharedAttachmentUrisCountsEverythingBeyondTheConfiguredLimit() {
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            putParcelableArrayListExtra(
                Intent.EXTRA_STREAM,
                ArrayList((1..1_000).map { Uri.parse("content://shares/$it") }),
            )
        }

        val selection = intent.sharedAttachmentUris(limit = 10)

        assertEquals(10, selection.uris.size)
        assertEquals(990, selection.overflowCount)
    }

    @Test
    fun malformedStreamExtrasAreRejectedWithoutCrashing() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, "not-a-uri")
        }

        val selection = intent.sharedAttachmentUris(limit = 10)

        assertEquals(emptyList<Uri>(), selection.uris)
        assertEquals(1, selection.overflowCount)
    }

    @Test
    fun corruptPendingDraftIsRemovedInsteadOfLoopingForever() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("hermex_share", android.content.Context.MODE_PRIVATE)
            .edit()
            .putString("pending_share_draft", "not-json")
            .commit()

        val store = SharedDraftStore(context)

        assertEquals(false, store.hasPendingDraft())
        assertEquals(null, store.loadPendingDraft())
    }

    @Test
    fun importedDraftIsAcknowledgedOnlyWhenItsGenerationStillMatches() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("hermex_share", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        val store = SharedDraftStore(context)
        val attachmentFile = File(context.cacheDir, "share-ack-test.txt").apply { writeText("hello") }
        store.savePendingDraft(
            text = "first",
            attachments = listOf(SharedAttachment("content://share/first", cachedPath = attachmentFile.absolutePath)),
        )
        val first = requireNotNull(store.loadPendingDraft(removeAfterLoad = false))
        store.savePendingDraft(text = "newer", attachments = emptyList())

        assertEquals(false, store.commitImportedDraft(first.createdAtEpochMillis, emptyList()))
        assertEquals("newer", store.loadPendingDraft(removeAfterLoad = false)?.text)

        val newer = requireNotNull(store.loadPendingDraft(removeAfterLoad = false))
        assertEquals(true, store.commitImportedDraft(newer.createdAtEpochMillis, emptyList()))
        assertEquals(null, store.loadPendingDraft(removeAfterLoad = false))
    }
}
