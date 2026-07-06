package com.uzairansar.hermex.ui.panels

import org.junit.Assert.assertEquals
import org.junit.Test

class AnalyticsTimeframeTest {
    @Test
    fun mapsIosTimeframesToServerDays() {
        assertEquals(1, AnalyticsTimeframe.Today.serverDays)
        assertEquals(7, AnalyticsTimeframe.Last7Days.serverDays)
        assertEquals(30, AnalyticsTimeframe.Last30Days.serverDays)
        assertEquals(365, AnalyticsTimeframe.AllTime.serverDays)
    }

    @Test
    fun exposesIosTimeframeTitles() {
        assertEquals("Today", AnalyticsTimeframe.Today.title)
        assertEquals("Last 7 Days", AnalyticsTimeframe.Last7Days.title)
        assertEquals("Last 30 Days", AnalyticsTimeframe.Last30Days.title)
        assertEquals("All Time", AnalyticsTimeframe.AllTime.title)
    }
}
