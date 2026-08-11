package com.uzairansar.hermex.ui.kanban

import com.uzairansar.hermex.core.network.ApiError
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KanbanCapabilityTest {
    @Test
    fun endpointMissingResponsesCloseTheScopedCapability() {
        assertTrue(isMissingKanbanCapability(ApiError.Http(405, null)))
        assertTrue(
            isMissingKanbanCapability(
                ApiError.Http(404, "{\"error\":\"Unknown Kanban endpoint; refresh the client\"}"),
            ),
        )
        assertTrue(isMissingKanbanCapability(ApiError.Http(404, "Kanban endpoint not found")))
        assertTrue(isMissingKanbanCapability(ApiError.Http(404, "Unsupported Kanban endpoint")))
    }

    @Test
    fun resourceNotFoundResponsesDoNotCloseTheCapability() {
        assertFalse(isMissingKanbanCapability(ApiError.Http(404, "{\"error\":\"task not found\"}")))
        assertFalse(isMissingKanbanCapability(ApiError.Http(404, null)))
        assertFalse(isMissingKanbanCapability(ApiError.Http(500, "Unknown Kanban endpoint")))
        assertFalse(isMissingKanbanCapability(ApiError.Http(501, null)))
    }
}
