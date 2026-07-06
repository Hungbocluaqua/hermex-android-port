package com.uzairansar.hermex.data.preferences

import com.uzairansar.hermex.core.model.ModelSummary
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val RECENT_MODEL_LIMIT = 5

@Serializable
data class ModelFavoriteKey(
    @SerialName("modelID") val modelId: String,
    @SerialName("providerID") val providerId: String? = null,
)

fun ModelSummary.favoriteKeyOrNull(): ModelFavoriteKey? {
    val modelId = modelIdentifier ?: return null
    return ModelFavoriteKey(modelId = modelId, providerId = normalizedProvider)
}

fun ModelSummary.matchesSelection(selected: ModelSummary?): Boolean {
    val modelId = modelIdentifier ?: return false
    val selectedModelId = selected?.modelIdentifier ?: return false
    if (modelId != selectedModelId) return false
    val selectedProvider = selected.normalizedProvider
    return selectedProvider == null || normalizedProvider == selectedProvider
}

val ModelSummary.modelIdentifier: String?
    get() = id.nonBlank() ?: name.nonBlank()

val ModelSummary.displayModelTitle: String
    get() = label.nonBlank() ?: name.nonBlank() ?: id.nonBlank() ?: "Model"

val ModelSummary.normalizedProvider: String?
    get() = provider.nonBlank()

fun ModelFavoriteKey.fallbackModel(): ModelSummary =
    ModelSummary(id = modelId, name = modelId, label = modelId, provider = providerId)

fun List<ModelSummary>.visibleFavoriteModels(favoriteKeys: List<ModelFavoriteKey>): List<ModelSummary> {
    val modelsByKey = catalogModelsByFavoriteKey()
    return favoriteKeys.deduplicatedModelKeys().map { key -> modelsByKey[key] ?: key.fallbackModel() }
}

fun List<ModelSummary>.visibleRecentModels(
    recentKeys: List<ModelFavoriteKey>,
    favoriteKeys: List<ModelFavoriteKey>,
): List<ModelSummary> {
    val favoriteKeySet = favoriteKeys.toSet()
    val modelsByKey = catalogModelsByFavoriteKey()
    return recentKeys
        .limitedDeduplicatedModelKeys(limit = recentKeys.size)
        .filter { it !in favoriteKeySet }
        .map { key -> modelsByKey[key] ?: key.fallbackModel() }
}

fun List<ModelFavoriteKey>.deduplicatedModelKeys(): List<ModelFavoriteKey> {
    val seen = LinkedHashSet<ModelFavoriteKey>()
    return filter { seen.add(it) }
}

fun List<ModelFavoriteKey>.limitedDeduplicatedModelKeys(limit: Int = RECENT_MODEL_LIMIT): List<ModelFavoriteKey> {
    if (limit <= 0) return emptyList()
    val seen = LinkedHashSet<ModelFavoriteKey>()
    val result = mutableListOf<ModelFavoriteKey>()
    for (key in this) {
        if (seen.add(key)) {
            result += key
            if (result.size == limit) break
        }
    }
    return result
}

private fun List<ModelSummary>.catalogModelsByFavoriteKey(): Map<ModelFavoriteKey, ModelSummary> {
    val modelsByKey = linkedMapOf<ModelFavoriteKey, ModelSummary>()
    for (model in this) {
        val key = model.favoriteKeyOrNull() ?: continue
        modelsByKey.putIfAbsent(key, model)
    }
    return modelsByKey
}

private fun String?.nonBlank(): String? = this?.trim()?.takeIf { it.isNotBlank() }
