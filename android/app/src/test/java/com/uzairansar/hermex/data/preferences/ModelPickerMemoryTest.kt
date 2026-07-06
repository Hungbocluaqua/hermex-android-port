package com.uzairansar.hermex.data.preferences

import com.uzairansar.hermex.core.model.ModelSummary
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelPickerMemoryTest {
    @Test
    fun modelFavoriteKeyUsesSwiftCompatibleJsonNames() {
        val encoded = Json.encodeToString(ModelFavoriteKey(modelId = "gpt-4.1", providerId = "openai"))

        assertTrue(encoded.contains("\"modelID\""))
        assertTrue(encoded.contains("\"providerID\""))
    }

    @Test
    fun visibleFavoriteModelsKeepCustomFallbacks() {
        val catalog = listOf(ModelSummary(id = "gpt-4.1", label = "GPT 4.1", provider = "openai"))
        val favorites = listOf(
            ModelFavoriteKey(modelId = "gpt-4.1", providerId = "openai"),
            ModelFavoriteKey(modelId = "custom-large", providerId = "local"),
        )

        val visible = catalog.visibleFavoriteModels(favorites)

        assertEquals("GPT 4.1", visible[0].displayModelTitle)
        assertEquals("custom-large", visible[1].id)
        assertEquals("local", visible[1].provider)
    }

    @Test
    fun visibleRecentModelsExcludeFavoritesAndDeduplicate() {
        val catalog = listOf(
            ModelSummary(id = "a", provider = "openai"),
            ModelSummary(id = "b", provider = "anthropic"),
        )
        val favorites = listOf(ModelFavoriteKey(modelId = "a", providerId = "openai"))
        val recents = listOf(
            ModelFavoriteKey(modelId = "b", providerId = "anthropic"),
            ModelFavoriteKey(modelId = "a", providerId = "openai"),
            ModelFavoriteKey(modelId = "b", providerId = "anthropic"),
            ModelFavoriteKey(modelId = "custom", providerId = "local"),
        )

        val visible = catalog.visibleRecentModels(recents, favorites)

        assertEquals(listOf("b", "custom"), visible.map { it.id })
    }

    @Test
    fun recentKeysAreLimitedToFiveNewestDistinctModels() {
        val keys = listOf("a", "b", "c", "d", "e", "f", "a")
            .map { ModelFavoriteKey(modelId = it, providerId = "provider") }

        val limited = keys.limitedDeduplicatedModelKeys()

        assertEquals(listOf("a", "b", "c", "d", "e"), limited.map { it.modelId })
    }

    @Test
    fun selectionRequiresProviderOnlyWhenSelectedProviderIsPresent() {
        val catalogModel = ModelSummary(id = "gpt-4.1", provider = "openai")

        assertTrue(catalogModel.matchesSelection(ModelSummary(id = "gpt-4.1")))
        assertTrue(catalogModel.matchesSelection(ModelSummary(id = "gpt-4.1", provider = "openai")))
        assertFalse(catalogModel.matchesSelection(ModelSummary(id = "gpt-4.1", provider = "azure")))
    }
}
