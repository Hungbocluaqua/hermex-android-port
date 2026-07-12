package com.uzairansar.hermex.data.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.RandomAccessFile

class SharedDraftPolicyTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun draftTextPreservesTrimmedSubjectAndBody() {
        assertEquals(
            "Shared subject\n\nShared body",
            SharedDraftPolicy.draftText("  Shared subject  ", "\nShared body\n"),
        )
        assertEquals(
            "Same text",
            SharedDraftPolicy.draftText("Same text", "Same text"),
        )
    }

    @Test
    fun attachmentSelectionRejectsEmptyOversizeAndCountOverflow() {
        val empty = temporaryFolder.newFile("empty")
        val oversized = temporaryFolder.newFile("oversized")
        RandomAccessFile(oversized, "rw").use { file ->
            file.setLength(SharedDraftPolicy.MAXIMUM_SHARED_ATTACHMENT_BYTES + 1)
        }
        val valid = (1..12).map { index ->
            val file = temporaryFolder.newFile("valid-$index").apply { writeBytes(byteArrayOf(1)) }
            SharedAttachment(uri = "content://valid/$index", cachedPath = file.absolutePath)
        }

        val selection = selectSharedAttachments(
            listOf(
                SharedAttachment(uri = "content://empty", cachedPath = empty.absolutePath),
                SharedAttachment(uri = "content://oversized", cachedPath = oversized.absolutePath),
            ) + valid,
        )

        assertEquals(valid.take(10), selection.accepted)
        assertEquals(4, selection.rejected.size)
        assertTrue(selection.rejected.containsAll(valid.takeLast(2)))
    }

    @Test
    fun cacheCopyAcceptsExactLimit() {
        val destination = File(temporaryFolder.root, "accepted")

        val byteCount = copyAcceptedSharedAttachment(
            input = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)),
            destination = destination,
            maximumBytes = 4,
        )

        assertEquals(4L, byteCount)
        assertTrue(destination.isFile)
        assertEquals(listOf<Byte>(1, 2, 3, 4), destination.readBytes().toList())
    }

    @Test
    fun cacheCopyRejectsEmptyAndOversizeWithoutOrphans() {
        val emptyDestination = File(temporaryFolder.root, "empty-copy")
        val oversizedDestination = File(temporaryFolder.root, "oversized-copy")

        assertNull(
            copyAcceptedSharedAttachment(
                input = ByteArrayInputStream(byteArrayOf()),
                destination = emptyDestination,
                maximumBytes = 4,
            ),
        )
        assertNull(
            copyAcceptedSharedAttachment(
                input = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5)),
                destination = oversizedDestination,
                maximumBytes = 4,
            ),
        )

        assertFalse(emptyDestination.exists())
        assertFalse(oversizedDestination.exists())
    }

    @Test
    fun rejectedCacheCleanupStaysInsideAppCacheDirectory() {
        val cacheDirectory = temporaryFolder.newFolder("cache")
        val cachedFile = File(cacheDirectory, "rejected").apply { writeBytes(byteArrayOf(1)) }
        val outsideFile = temporaryFolder.newFile("outside").apply { writeBytes(byteArrayOf(1)) }

        deleteSharedAttachmentCaches(
            attachments = listOf(
                SharedAttachment(uri = "content://cached", cachedPath = cachedFile.absolutePath),
                SharedAttachment(uri = "content://outside", cachedPath = outsideFile.absolutePath),
            ),
            cacheDirectory = cacheDirectory,
        )

        assertFalse(cachedFile.exists())
        assertTrue(outsideFile.exists())
    }
}
