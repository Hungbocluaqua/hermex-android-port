package com.uzairansar.hermex.ui.chat

import com.uzairansar.hermex.core.model.ReasoningResponse
import java.util.Locale

data class ReasoningEffortOption(
    val id: String,
    val title: String,
) {
    companion object {
        val allCases: List<ReasoningEffortOption> = listOf(
            ReasoningEffortOption(id = "none", title = "None"),
            ReasoningEffortOption(id = "minimal", title = "Minimal"),
            ReasoningEffortOption(id = "low", title = "Low"),
            ReasoningEffortOption(id = "medium", title = "Medium"),
            ReasoningEffortOption(id = "high", title = "High"),
            ReasoningEffortOption(id = "xhigh", title = "XHigh"),
        )

        fun titleFor(effort: String?): String =
            effort
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { value -> allCases.firstOrNull { it.id == value }?.title ?: value.capitalizedTitle() }
                ?: "Reasoning"

        fun optionsForSupportedEfforts(supportedEfforts: List<String>?): List<ReasoningEffortOption> {
            val normalized = supportedEfforts.normalizedReasoningEfforts()
            if (normalized == null || normalized.isEmpty()) return allCases
            return normalized.map { id ->
                allCases.firstOrNull { it.id == id } ?: ReasoningEffortOption(id = id, title = id.capitalizedTitle())
            }
        }

        fun showsEffortControl(
            supportsReasoningEffort: Boolean?,
            supportedEfforts: List<String>?,
        ): Boolean {
            supportsReasoningEffort?.let { return it }
            supportedEfforts?.let { return it.isNotEmpty() }
            return true
        }
    }
}

val ReasoningResponse.normalizedSupportedEfforts: List<String>?
    get() = supportedEfforts.normalizedReasoningEfforts()

fun List<String>?.normalizedReasoningEfforts(): List<String>? {
    this ?: return null
    val seen = LinkedHashSet<String>()
    return map { it.trim().lowercase(Locale.US) }
        .filter { it.isNotEmpty() && seen.add(it) }
}

private fun String.capitalizedTitle(): String =
    replaceFirstChar { first ->
        if (first.isLowerCase()) {
            first.titlecase(Locale.US)
        } else {
            first.toString()
        }
    }
