package com.uzairansar.hermex.data.db

import com.uzairansar.hermex.core.model.SessionSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CachedSessionMetadataTest {
    @Test
    fun cachedSessionPreservesReadOnlyOwnershipMetadata() {
        val cached = requireNotNull(
            CachedSessionEntity.from(
                serverUrl = "https://hermex.test/",
                session = SessionSummary(
                    sessionId = "subagent-1",
                    isCliSession = false,
                    sourceTag = "subagent",
                    rawSource = "subagent",
                    parentSessionId = "parent-1",
                    relationshipType = "child_session",
                    readOnly = true,
                ),
            ),
        )

        val restored = cached.toSummary()

        assertEquals("subagent", restored.rawSource)
        assertEquals("parent-1", restored.parentSessionId)
        assertEquals("child_session", restored.relationshipType)
        assertTrue(restored.isSessionReadOnly)
    }
}
