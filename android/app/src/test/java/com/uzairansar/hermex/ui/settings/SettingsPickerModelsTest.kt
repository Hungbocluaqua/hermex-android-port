package com.uzairansar.hermex.ui.settings

import com.uzairansar.hermex.core.model.ModelSummary
import com.uzairansar.hermex.core.model.ModelsLiveResponse
import com.uzairansar.hermex.core.model.ProfileSummary
import com.uzairansar.hermex.core.model.ProfilesResponse
import com.uzairansar.hermex.core.model.ProviderSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsPickerModelsTest {
    @Test
    fun profileNameRulesMatchIosCreateProfileForm() {
        assertTrue(ProfileNameRules.isValid("research_1"))
        assertTrue(ProfileNameRules.isValid("work-profile"))
        assertFalse(ProfileNameRules.isValid("Work"))
        assertFalse(ProfileNameRules.isValid("-work"))
        assertFalse(ProfileNameRules.isValid("work profile"))
        assertTrue(ProfileNameRules.isValidBaseUrl("http://localhost:11434"))
        assertTrue(ProfileNameRules.isValidBaseUrl("https://api.example.com"))
        assertFalse(ProfileNameRules.isValidBaseUrl("localhost:11434"))
    }

    @Test
    fun effectiveDefaultProfileMirrorsIosPriority() {
        val activeResponse = ProfilesResponse(
            profiles = listOf(ProfileSummary(name = "default", isDefault = true), ProfileSummary(name = "work", isActive = true)),
            active = null,
        )
        val defaultResponse = ProfilesResponse(
            profiles = listOf(ProfileSummary(name = "default", isDefault = true), ProfileSummary(name = "work")),
            active = "",
        )

        assertEquals("work", activeResponse.effectiveDefaultProfileName())
        assertEquals("default", defaultResponse.effectiveDefaultProfileName())
        assertEquals("Default", defaultResponse.displayNameForProfile("default"))
    }

    @Test
    fun overlayLiveModelsReplacesOnlyMatchingProvider() {
        val catalog = listOf(
            ModelSummary(id = "gpt-5", provider = "openai", label = "GPT-5"),
            ModelSummary(id = "claude", provider = "anthropic", label = "Claude"),
        )
        val merged = overlayLiveModels(
            catalog,
            ModelsLiveResponse(
                provider = "openai",
                models = listOf(ModelSummary(id = "gpt-5.5", label = "GPT-5.5")),
                count = 1,
            ),
        )

        assertEquals(listOf("claude", "gpt-5.5"), merged.map { it.id })
        assertEquals("openai", merged.last().provider)
    }

    @Test
    fun defaultModelGroupsUseProviderNamesAndSearch() {
        val groups = defaultModelPickerGroups(
            models = listOf(
                ModelSummary(id = "gpt-5", provider = "openai", label = "GPT-5"),
                ModelSummary(id = "claude-sonnet", provider = "anthropic", label = "Claude Sonnet"),
            ),
            providers = listOf(ProviderSummary(id = "anthropic", name = "Anthropic")),
            query = "claude",
        )

        assertEquals(1, groups.size)
        assertEquals("Anthropic", groups.single().title)
        assertEquals("claude-sonnet", groups.single().models.single().id)
    }
}
