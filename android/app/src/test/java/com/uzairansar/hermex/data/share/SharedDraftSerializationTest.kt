package com.uzairansar.hermex.data.share

import com.uzairansar.hermex.core.network.HermesJson
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Test

class SharedDraftSerializationTest {
    @Test
    fun sharedDraftRoundTripsAttachmentMetadata() {
        val draft = SharedDraft(
            text = "Review this",
            attachments = listOf(
                SharedAttachment(
                    uri = "content://example/file",
                    displayName = "file.txt",
                    mimeType = "text/plain",
                    cachedPath = "/tmp/file.txt",
                ),
            ),
            uris = listOf("content://example/file"),
        )

        val decoded = HermesJson.decodeFromString<SharedDraft>(HermesJson.encodeToString(draft))

        assertEquals("Review this", decoded.text)
        assertEquals("file.txt", decoded.attachments.single().displayName)
        assertEquals("text/plain", decoded.attachments.single().mimeType)
        assertEquals("/tmp/file.txt", decoded.attachments.single().cachedPath)
        assertEquals("content://example/file", decoded.uris.single())
    }

    @Test
    fun legacyUriOnlyDraftStillDecodes() {
        val decoded = HermesJson.decodeFromString<SharedDraft>(
            """{"text":"Legacy","uris":["content://example/old"],"createdAtEpochMillis":1}""",
        )

        assertEquals("Legacy", decoded.text)
        assertEquals(emptyList<SharedAttachment>(), decoded.attachments)
        assertEquals("content://example/old", decoded.uris.single())
    }
}
