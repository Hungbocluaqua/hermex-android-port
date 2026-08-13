package com.uzairansar.hermex.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegularWidthSessionLayoutTest {
    @Test
    fun usesTheMaterialExpandedWidthBreakpoint() {
        assertFalse(usesRegularWidthSessionLayout(839))
        assertTrue(usesRegularWidthSessionLayout(840))
        assertTrue(usesRegularWidthSessionLayout(1_200))
    }

    @Test
    fun routesChatDeepLinksIntoTheRegularWidthDetailPane() {
        assertEquals(
            "sessions?openSessionId=session%2Fone&openSessionConsumeShare=false&openSessionAutoVoice=false",
            regularWidthDestinationRoute("chat/session%2Fone", usesRegularWidthLayout = true),
        )
        assertEquals(
            "sessions?openSessionId=s1&openSessionConsumeShare=true&openSessionAutoVoice=false",
            regularWidthDestinationRoute("chat/s1?consumeShare=true", usesRegularWidthLayout = true),
        )
        assertEquals(
            "chat/s1",
            regularWidthDestinationRoute("chat/s1", usesRegularWidthLayout = false),
        )
    }
}
