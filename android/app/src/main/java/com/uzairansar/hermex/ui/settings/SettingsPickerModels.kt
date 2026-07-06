package com.uzairansar.hermex.ui.settings

import com.uzairansar.hermex.core.model.ModelSummary
import com.uzairansar.hermex.core.model.ModelsLiveResponse
import com.uzairansar.hermex.core.model.ProfileSummary
import com.uzairansar.hermex.core.model.ProfilesResponse
import com.uzairansar.hermex.core.model.ProviderSummary
import com.uzairansar.hermex.data.preferences.displayModelTitle
import com.uzairansar.hermex.data.preferences.modelIdentifier
import com.uzairansar.hermex.data.preferences.normalizedProvider
import java.util.Locale

data class SettingsModelPickerGroup(
    val id: String,
    val title: String,
    val providerId: String?,
    val models: List<ModelSummary>,
)

object ProfileNameRules {
    fun isValid(name: String): Boolean {
        if (name.isEmpty() || name.length > 64) return false
        val first = name.first()
        if (!first.isLowercaseAsciiLetterOrDigit()) return false
        return name.all { it.isLowercaseAsciiLetterOrDigit() || it == '-' || it == '_' }
    }

    fun isValidBaseUrl(value: String): Boolean =
        value.startsWith("http://") || value.startsWith("https://")
}

fun overlayLiveModels(
    catalogModels: List<ModelSummary>,
    live: ModelsLiveResponse,
): List<ModelSummary> {
    val provider = live.provider?.trim()?.takeIf { it.isNotBlank() } ?: return catalogModels
    val liveModels = live.models.orEmpty()
        .map { model ->
            if (model.normalizedProvider == null) {
                model.copy(provider = provider)
            } else {
                model
            }
        }
        .filter { it.modelIdentifier != null }
    if (liveModels.isEmpty()) return catalogModels

    val providerKey = provider.lowercase(Locale.US)
    return catalogModels.filter { it.normalizedProvider?.lowercase(Locale.US) != providerKey } + liveModels
}

fun defaultModelPickerGroups(
    models: List<ModelSummary>,
    providers: List<ProviderSummary>,
    query: String,
): List<SettingsModelPickerGroup> {
    val providerNames = providers.associate { provider ->
        provider.id.orEmpty().lowercase(Locale.US) to (provider.name?.trim()?.takeIf { it.isNotBlank() } ?: provider.id.orEmpty())
    }
    val grouped = linkedMapOf<String, MutableList<ModelSummary>>()
    val providerIds = linkedMapOf<String, String?>()

    models.forEach { model ->
        if (!model.matchesDefaultModelQuery(query)) return@forEach
        val providerId = model.normalizedProvider
        val key = providerId?.lowercase(Locale.US) ?: "default"
        grouped.getOrPut(key) { mutableListOf() } += model
        providerIds.putIfAbsent(key, providerId)
    }

    return grouped.map { (key, groupModels) ->
        val providerId = providerIds[key]
        SettingsModelPickerGroup(
            id = "model-group-$key",
            title = providerId?.let { providerNames[it.lowercase(Locale.US)] ?: it } ?: "Models",
            providerId = providerId,
            models = groupModels,
        )
    }
}

fun ModelSummary.matchesDefaultModelQuery(query: String): Boolean {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isBlank()) return true
    return listOfNotNull(displayModelTitle, modelIdentifier, normalizedProvider)
        .any { it.contains(normalizedQuery, ignoreCase = true) }
}

fun ProfileSummary.normalizedProfileName(): String? =
    name?.trim()?.takeIf { it.isNotBlank() }

fun ProfileSummary.settingsDisplayName(): String {
    displayName?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
    val profileName = normalizedProfileName() ?: return "Profile"
    return if (profileName == "default") "Default" else profileName
}

fun ProfileSummary.settingsDetails(): String? {
    val details = buildList {
        model?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
        provider?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
        skillCount?.let { add("$it skills") }
    }
    return details.takeIf { it.isNotEmpty() }?.joinToString(" - ")
}

fun ProfilesResponse.effectiveDefaultProfileName(): String? {
    active?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
    profiles.orEmpty().firstOrNull { it.isActive == true }?.normalizedProfileName()?.let { return it }
    profiles.orEmpty().firstOrNull { it.isDefault == true }?.normalizedProfileName()?.let { return it }
    return profiles.orEmpty().firstNotNullOfOrNull { it.normalizedProfileName() }
}

fun ProfilesResponse.displayNameForProfile(profileName: String?): String? {
    val normalized = profileName?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return profiles.orEmpty().firstOrNull { it.normalizedProfileName() == normalized }?.settingsDisplayName()
        ?: if (normalized == "default") "Default" else normalized
}

private fun Char.isLowercaseAsciiLetterOrDigit(): Boolean =
    this in 'a'..'z' || this in '0'..'9'
