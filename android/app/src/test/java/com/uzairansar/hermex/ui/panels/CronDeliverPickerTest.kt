package com.uzairansar.hermex.ui.panels

import com.uzairansar.hermex.core.model.CronDeliveryPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CronDeliverPickerTest {
    private val serverOptions = listOf(
        CronDeliveryPlatform(value = " local ", label = " Local output "),
        CronDeliveryPlatform(value = "origin", label = null),
        CronDeliveryPlatform(value = "local", label = "Duplicate"),
        CronDeliveryPlatform(value = " ", label = "Invalid"),
    )

    @Test
    fun buildsTrimmedDeduplicatedServerRows() {
        val options = cronDeliverPickerOptions(serverOptions, currentValue = "local")

        assertEquals(
            listOf(
                CronDeliverPickerOption("local", "Local output", isCustom = false),
                CronDeliverPickerOption("origin", "origin", isCustom = false),
            ),
            options,
        )
    }

    @Test
    fun preservesInitialAndCurrentUnknownTargetsAsCustomRows() {
        val options = cronDeliverPickerOptions(
            serverOptions = serverOptions,
            currentValue = "future-target",
            initialValue = "legacy-target",
        )

        assertEquals(
            listOf("local", "origin", "legacy-target", "future-target"),
            options?.map { it.value },
        )
        assertEquals(listOf(false, false, true, true), options?.map { it.isCustom })
    }

    @Test
    fun fallsBackToFreeTextWhenOptionsOrCurrentValueAreUnavailable() {
        assertNull(cronDeliverPickerOptions(emptyList(), currentValue = "local"))
        assertNull(cronDeliverPickerOptions(serverOptions, currentValue = "  "))
    }
}
