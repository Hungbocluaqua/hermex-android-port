package com.uzairansar.hermex.ui.chat

import com.uzairansar.hermex.core.model.UploadResponse
import com.uzairansar.hermex.core.network.HermesJson
import java.io.ByteArrayInputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ChatStateSafetyPolicyTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun lateCancellationCannotClaimAnotherStream() {
        assertTrue(ChatStreamOwnershipPolicy.stillOwnsStream("stream-a", "stream-a"))
        assertFalse(ChatStreamOwnershipPolicy.stillOwnsStream("stream-a", "stream-b"))
        assertFalse(ChatStreamOwnershipPolicy.stillOwnsStream("stream-a", null))
    }

    @Test
    fun failedQueuedSendStopsAutomaticDrain() {
        assertFalse(QueuedDraftDrainPolicy.shouldContinue(sent = false, isStreaming = false))
        assertFalse(QueuedDraftDrainPolicy.shouldContinue(sent = true, isStreaming = true))
        assertTrue(QueuedDraftDrainPolicy.shouldContinue(sent = true, isStreaming = false))
    }

    @Test
    fun asyncComposerActionDoesNotEraseNewerText() {
        assertEquals("", draftAfterConsuming(current = "sent text", consumed = "sent text"))
        assertEquals("newer text", draftAfterConsuming(current = "newer text", consumed = "sent text"))
        assertEquals("sent text", draftAfterFailedConsumption(current = "", consumed = "sent text"))
        assertEquals("newer text", draftAfterFailedConsumption(current = "newer text", consumed = "sent text"))
    }

    @Test
    fun streamRecoveryUsesBoundedExponentialBackoff() {
        assertTrue(StreamRecoveryBackoffPolicy.shouldRetry(attempt = 6, maximumAttempts = 6))
        assertFalse(StreamRecoveryBackoffPolicy.shouldRetry(attempt = 7, maximumAttempts = 6))
        assertEquals(750L, StreamRecoveryBackoffPolicy.delayMillis(1, 750L, 12_000L))
        assertEquals(3_000L, StreamRecoveryBackoffPolicy.delayMillis(3, 750L, 12_000L))
        assertEquals(12_000L, StreamRecoveryBackoffPolicy.delayMillis(10, 750L, 12_000L))
    }

    @Test
    fun attachmentLimitIncludesUploadsAlreadyInFlight() {
        assertEquals(3, AttachmentLimitPolicy.remaining(maximum = 10, attached = 5, uploadsInFlight = 2))
        assertEquals(0, AttachmentLimitPolicy.remaining(maximum = 10, attached = 9, uploadsInFlight = 3))
    }

    @Test
    fun pendingChatStateRoundTripsAcrossProcessRecreation() {
        val state = PersistedChatPendingState(
            draft = "unsent draft",
            pendingAttachments = listOf(UploadResponse(filename = "notes.txt", path = "/uploads/notes.txt")),
            queuedDrafts = listOf(QueuedDraft("queued", emptyList())),
            backgroundTasks = mapOf("task-1" to BackgroundTaskState("background", 123L)),
            btwTask = BtwTaskState("stream-1", "message-1", "question", "partial"),
        )

        val decoded = HermesJson.decodeFromString<PersistedChatPendingState>(
            HermesJson.encodeToString(state),
        )

        assertEquals(state, decoded)
    }

    @Test
    fun queuedDraftRegistrySurvivesViewModelReplacement() {
        val sessionId = "queue-test"
        val queued = listOf(
            QueuedDraft(
                text = "send this later",
                attachments = listOf(UploadResponse(filename = "notes.txt", path = "/tmp/notes.txt")),
            ),
        )

        try {
            QueuedDraftRegistry.save(sessionId, queued)
            assertEquals(queued, QueuedDraftRegistry.load(sessionId))
        } finally {
            QueuedDraftRegistry.clear(sessionId)
        }
    }

    @Test
    fun attachmentCopyAcceptsExactLimit() {
        val destination = File(temporaryFolder.root, "exact")

        val count = copyAttachmentWithLimit(
            input = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)),
            destination = destination,
            maximumBytes = 4,
        )

        assertEquals(4L, count)
        assertEquals(listOf<Byte>(1, 2, 3, 4), destination.readBytes().toList())
    }

    @Test
    fun attachmentCopyRejectsOversizeAndDeletesPartialFile() {
        val destination = File(temporaryFolder.root, "oversize")

        val result = runCatching {
            copyAttachmentWithLimit(
                input = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5)),
                destination = destination,
                maximumBytes = 4,
            )
        }

        assertTrue(result.isFailure)
        assertFalse(destination.exists())
    }
}
