package com.uzairansar.hermex.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SlashAutocompletePolicyTest {
    @Test
    fun autocompleteOnlyAppearsForLeadingSlashAfterOptionalWhitespace() {
        assertFalse(SlashAutocompletePolicy.evaluate("hello").isVisible)
        assertTrue(SlashAutocompletePolicy.evaluate("  /mo").isVisible)
    }

    @Test
    fun builtInsMatchNamesAndDescriptions() {
        val byName = SlashAutocompletePolicy.evaluate("/mod").suggestions
        val byDescription = SlashAutocompletePolicy.evaluate("/conversation").suggestions

        assertEquals(listOf("/model"), byName.map { it.label })
        assertEquals(listOf("/clear", "/branch"), byDescription.map { it.label })
    }

    @Test
    fun commandSelectionUsesSlashAndSingleArgumentSeparatorWithoutExecuting() {
        val model = SlashAutocompletePolicy.evaluate("/mod").suggestions.single()
        val help = SlashAutocompletePolicy.evaluate("/hel").suggestions.single()

        assertEquals("/model ", model.replacement)
        assertEquals("/help ", help.replacement)
        assertFalse(SlashAutocompletePolicy.evaluate(help.replacement).isVisible)
    }

    @Test
    fun contextArgumentsFilterDeduplicateAndMarkCurrentValue() {
        val result = SlashAutocompletePolicy.evaluate(
            draft = "/model g",
            context = SlashAutocompleteContext(
                modelIds = listOf("gpt-5", " GPT-5 ", "gemini-2.5", "claude"),
                selectedModelId = "gpt-5",
            ),
        )

        assertEquals(listOf("gpt-5", "gemini-2.5"), result.suggestions.map { it.label })
        assertTrue(result.suggestions.first().isSelected)
        assertEquals("/model gpt-5", result.suggestions.first().replacement)
    }

    @Test
    fun profileReasoningAndWorkspaceArgumentsUseLiveContext() {
        val context = SlashAutocompleteContext(
            profileNames = listOf("default", "work"),
            reasoningEfforts = listOf("low", "medium", "high"),
            workspacePaths = listOf("/src/hermex", "/src/mobile"),
        )

        assertEquals(listOf("work"), SlashAutocompletePolicy.evaluate("/profile wo", context).suggestions.map { it.label })
        assertEquals(listOf("medium"), SlashAutocompletePolicy.evaluate("/reasoning me", context).suggestions.map { it.label })
        assertEquals(listOf("/src/mobile"), SlashAutocompletePolicy.evaluate("/workspace /src/m", context).suggestions.map { it.label })
        assertEquals("/src/m", SlashAutocompletePolicy.workspaceArgumentQuery("/workspace /src/m"))
        assertEquals(null, SlashAutocompletePolicy.workspaceArgumentQuery("/workspace"))
    }

    @Test
    fun serverCommandsAreMobileFilteredAndDeduplicatedAgainstBuiltIns() {
        val result = SlashAutocompletePolicy.evaluate(
            draft = "/",
            context = SlashAutocompleteContext(
                serverCommands = listOf(
                    SlashServerCommand("deploy", "Deploy the workspace", "environment"),
                    SlashServerCommand("terminal", isMobileVisible = false),
                    SlashServerCommand("MODEL", "Server duplicate"),
                    SlashServerCommand("Deploy", "Duplicate with different case"),
                ),
            ),
        )

        assertEquals(1, result.suggestions.count { it.kind == SlashAutocompleteSuggestionKind.ServerCommand })
        val deploy = result.suggestions.single { it.kind == SlashAutocompleteSuggestionKind.ServerCommand }
        assertEquals("/deploy", deploy.label)
        assertEquals("environment", deploy.argumentHint)
        assertEquals("/deploy ", deploy.replacement)
    }

    @Test
    fun serverCommandAutocompleteClosesAfterItsArgumentSeparator() {
        val context = SlashAutocompleteContext(
            serverCommands = listOf(SlashServerCommand("deploy")),
        )

        assertFalse(SlashAutocompletePolicy.evaluate("/deploy ", context).isVisible)
    }

    @Test
    fun goalActionsAutocompleteUntilDraftBecomesFreeformGoalText() {
        val actionResult = SlashAutocompletePolicy.evaluate("/goal st")
        val freeformResult = SlashAutocompletePolicy.evaluate("/goal ship the app")

        assertEquals(listOf("/goal status"), actionResult.suggestions.map { it.label })
        assertFalse(freeformResult.isVisible)
    }

    @Test
    fun skillSlugsAreStableAsciiLowercaseAndHyphenated() {
        assertEquals("release-notes-v2", SlashSkillFormatter.slug("  Release__Notes---V2!!  "))
        assertEquals("caf-tools", SlashSkillFormatter.slug("Café Tools"))
        assertEquals("", SlashSkillFormatter.slug("日本語"))
    }

    @Test
    fun skillSuggestionsOnlyIncludeEnabledValidSkillsAndDeduplicateBySlug() {
        val suggestions = SlashSkillFormatter.suggestions(
            listOf(
                SlashSkillDefinition("Zebra Skill", category = " Utilities ", description = " Last "),
                SlashSkillDefinition("alpha_skill", description = " First "),
                SlashSkillDefinition("Alpha Skill", description = "Duplicate"),
                SlashSkillDefinition("Disabled One", disabled = true),
                SlashSkillDefinition("Disabled Two", enabled = false),
                SlashSkillDefinition("!!!"),
                SlashSkillDefinition(null),
            ),
        )

        assertEquals(listOf("alpha-skill", "zebra-skill"), suggestions.map { it.slug })
        assertEquals("First", suggestions.first().description)
        assertEquals("Utilities", suggestions.last().category)
    }

    @Test
    fun skillAutocompleteMatchesMetadataAndInsertsServerRoutedCommandText() {
        val context = SlashAutocompleteContext(
            skillSuggestions = SlashSkillFormatter.suggestions(
                listOf(
                    SlashSkillDefinition(
                        name = "Release Notes",
                        category = "Writing",
                        description = "Draft a changelog",
                    ),
                ),
            ),
        )

        val byName = SlashAutocompletePolicy.evaluate("/rel", context).suggestions.single()
        val byDescription = SlashAutocompletePolicy.evaluate("/changelog", context).suggestions.single()

        assertEquals(SlashAutocompleteSuggestionKind.Skill, byName.kind)
        assertEquals("/release-notes", byName.label)
        assertEquals("Writing", byName.argumentHint)
        assertEquals("Draft a changelog", byName.detail)
        assertEquals("/release-notes ", byName.replacement)
        assertEquals(byName, byDescription)
        assertFalse(SlashAutocompletePolicy.evaluate("/release-notes write it", context).isVisible)
    }

    @Test
    fun skillCommandsDoNotDuplicateBuiltInsOrServerCommands() {
        val context = SlashAutocompleteContext(
            skillSuggestions = SlashSkillFormatter.suggestions(
                listOf(
                    SlashSkillDefinition("Model"),
                    SlashSkillDefinition("Deploy"),
                ),
            ),
            serverCommands = listOf(
                SlashServerCommand("deploy", "Server deploy"),
                SlashServerCommand("review", "Server review"),
            ),
        )

        val suggestions = SlashAutocompletePolicy.evaluate("/", context).suggestions

        assertEquals(1, suggestions.count { it.label.equals("/model", ignoreCase = true) })
        assertEquals(SlashAutocompleteSuggestionKind.BuiltInCommand, suggestions.single { it.label == "/model" }.kind)
        assertEquals(1, suggestions.count { it.label.equals("/deploy", ignoreCase = true) })
        assertEquals(SlashAutocompleteSuggestionKind.Skill, suggestions.single { it.label == "/deploy" }.kind)
        assertEquals(SlashAutocompleteSuggestionKind.ServerCommand, suggestions.single { it.label == "/review" }.kind)
    }

    @Test
    fun skillsCommandKeepsItsExistingSearchArgumentBehavior() {
        val context = SlashAutocompleteContext(
            skillSuggestions = SlashSkillFormatter.suggestions(
                listOf(SlashSkillDefinition("Release Notes")),
            ),
        )

        assertFalse(SlashAutocompletePolicy.evaluate("/skills release", context).isVisible)
    }
}
