package com.uzairansar.hermex.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppIconManagerTest {
    @Test
    fun manifestDefaultsResolveToDefaultIcon() {
        val states = defaultStates()

        assertEquals(setOf(AppIconChoice.Default), AppIconSelectionPolicy.enabledChoices(states))
        assertEquals(AppIconChoice.Default, AppIconSelectionPolicy.preferredChoice(states))
    }

    @Test
    fun switchingEnablesTargetBeforeDisablingPreviousIcon() {
        val states = defaultStates()

        assertEquals(
            listOf(
                AppIconComponentChange(AppIconChoice.GradientDark, AppIconComponentState.Enabled),
                AppIconComponentChange(AppIconChoice.Default, AppIconComponentState.Disabled),
            ),
            AppIconSelectionPolicy.changesFor(states, AppIconChoice.GradientDark),
        )
    }

    @Test
    fun selectingCurrentIconIsIdempotent() {
        val controller = FakeAppIconComponentController(defaultStates())
        val manager = AppIconManager(controller)

        assertTrue(manager.setChoice(AppIconChoice.Default).isSuccess)
        assertEquals(emptyList<AppIconComponentChange>(), controller.changes)
    }

    @Test
    fun switchingLeavesExactlyOneLauncherAliasEnabled() {
        val controller = FakeAppIconComponentController(defaultStates())
        val manager = AppIconManager(controller)

        assertTrue(manager.setChoice(AppIconChoice.MonochromeDark).isSuccess)

        assertEquals(
            setOf(AppIconChoice.MonochromeDark),
            AppIconSelectionPolicy.enabledChoices(controller.states),
        )
        assertEquals(AppIconChoice.MonochromeDark, manager.currentChoice())
    }

    @Test
    fun failedSwitchRollsBackToPreviousLauncherAlias() {
        val controller = FakeAppIconComponentController(defaultStates(), failOnChange = 2)
        val manager = AppIconManager(controller)

        val result = manager.setChoice(AppIconChoice.Disco)

        assertTrue(result.isFailure)
        assertEquals(setOf(AppIconChoice.Default), AppIconSelectionPolicy.enabledChoices(controller.states))
    }

    @Test
    fun reconciliationRepairsMultipleEnabledAliases() {
        val states = defaultStates().toMutableMap().apply {
            this[AppIconChoice.Dark] = AppIconComponentState.Enabled
        }
        val controller = FakeAppIconComponentController(states)
        val manager = AppIconManager(controller)

        assertTrue(manager.ensureValidSelection().isSuccess)

        assertEquals(setOf(AppIconChoice.Default), AppIconSelectionPolicy.enabledChoices(controller.states))
    }

    private fun defaultStates(): Map<AppIconChoice, AppIconComponentState> =
        AppIconChoice.entries.associateWith { AppIconComponentState.Default }
}

private class FakeAppIconComponentController(
    initialStates: Map<AppIconChoice, AppIconComponentState>,
    private var failOnChange: Int? = null,
) : AppIconComponentController {
    val states = initialStates.toMutableMap()
    val changes = mutableListOf<AppIconComponentChange>()
    private var changeCount = 0

    override fun state(choice: AppIconChoice): AppIconComponentState =
        states.getValue(choice)

    override fun setState(choice: AppIconChoice, state: AppIconComponentState) {
        changeCount += 1
        if (failOnChange == changeCount) {
            failOnChange = null
            error("Simulated PackageManager failure")
        }
        states[choice] = state
        changes += AppIconComponentChange(choice, state)
    }
}
