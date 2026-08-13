package com.uzairansar.hermex.ui.kanban

import com.uzairansar.hermex.core.model.KanbanBoardSnapshot
import com.uzairansar.hermex.core.model.KanbanCardSummary
import com.uzairansar.hermex.core.model.KanbanConfiguration
import java.util.Locale
import kotlin.math.floor

internal val kanbanLiveStatuses = listOf("triage", "todo", "blocked", "ready", "running", "done")

internal enum class KanbanStaleness {
    None,
    Warning,
    Critical,
}

internal data class KanbanCardGroup(
    val profile: String?,
    val cards: List<KanbanCardSummary>,
)

internal fun availableKanbanStatuses(
    snapshot: KanbanBoardSnapshot?,
    includeArchived: Boolean,
): List<String> = buildList {
    addAll(kanbanLiveStatuses)
    if (includeArchived) add("archived")
    snapshot?.columns.orEmpty().forEach { column ->
        val status = column.name.normalizedKanbanValue() ?: return@forEach
        if (status !in this) add(status)
    }
    snapshot?.allCards().orEmpty().forEach { card ->
        val status = card.status.normalizedKanbanValue() ?: return@forEach
        if (status !in this) add(status)
    }
}

internal fun KanbanBoardSnapshot.allCards(): List<KanbanCardSummary> =
    columns.orEmpty().flatMap { it.cards.orEmpty() }

internal fun KanbanBoardSnapshot.searchMatchedCards(query: String): List<KanbanCardSummary> {
    val normalizedQuery = query.trim().lowercase(Locale.ROOT)
    if (normalizedQuery.isEmpty()) return allCards()
    return allCards().filter { card ->
        listOf(card.cardId, card.title, card.body, card.assignee, card.tenant)
            .mapNotNull { it?.lowercase(Locale.ROOT) }
            .any { normalizedQuery in it }
    }
}

internal fun visibleKanbanCards(
    snapshot: KanbanBoardSnapshot?,
    status: String,
    searchQuery: String,
): List<KanbanCardSummary> = snapshot
    ?.searchMatchedCards(searchQuery)
    .orEmpty()
    .filter { it.status.normalizedKanbanValue() == status.normalizedKanbanValue() }

internal fun groupedKanbanCards(cards: List<KanbanCardSummary>): List<KanbanCardGroup> =
    cards.groupBy { it.assignee.normalizedKanbanDisplayValue() }
        .map { (profile, values) -> KanbanCardGroup(profile, values) }
        .sortedWith(compareBy<KanbanCardGroup> { it.profile != null }.thenBy { it.profile?.lowercase(Locale.ROOT) })

internal fun kanbanStatusCount(
    snapshot: KanbanBoardSnapshot?,
    status: String,
    searchQuery: String,
): Int = visibleKanbanCards(snapshot, status, searchQuery).size

internal fun kanbanProfileOptions(
    configuration: KanbanConfiguration?,
    snapshot: KanbanBoardSnapshot?,
    history: List<String>,
): List<String> = (
    configuration?.assigneeNames.orEmpty() +
        history +
        snapshot?.assignees.orEmpty() +
        snapshot?.allCards().orEmpty().mapNotNull { it.assignee }
    ).normalizedSortedKanbanValues()

internal fun kanbanTenantOptions(snapshot: KanbanBoardSnapshot?): List<String> = (
    snapshot?.tenants.orEmpty() + snapshot?.allCards().orEmpty().mapNotNull { it.tenant }
    ).normalizedSortedKanbanValues()

internal fun kanbanStatusTitleKey(status: String): String = when (status.normalizedKanbanValue()) {
    "triage" -> "Triage"
    "todo" -> "To Do"
    "blocked" -> "Blocked"
    "ready" -> "Ready"
    "running" -> "Running"
    "done" -> "Done"
    "archived" -> "Archived"
    else -> status.trim().ifEmpty { "Unknown Status" }
}

internal fun kanbanStaleness(card: KanbanCardSummary): KanbanStaleness {
    val age = card.ageSeconds ?: return KanbanStaleness.None
    return when (card.status.normalizedKanbanValue()) {
        "running" -> when {
            age >= 3_600 -> KanbanStaleness.Critical
            age >= 600 -> KanbanStaleness.Warning
            else -> KanbanStaleness.None
        }
        "ready" -> if (age >= 3_600) KanbanStaleness.Warning else KanbanStaleness.None
        "blocked" -> when {
            age >= 86_400 -> KanbanStaleness.Critical
            age >= 3_600 -> KanbanStaleness.Warning
            else -> KanbanStaleness.None
        }
        else -> KanbanStaleness.None
    }
}

internal fun kanbanAgeAbbreviation(seconds: Double): String {
    val bounded = seconds.coerceAtLeast(0.0)
    return when {
        bounded >= 86_400 -> "${floor(bounded / 86_400).toInt()}d"
        bounded >= 3_600 -> "${floor(bounded / 3_600).toInt()}h"
        else -> "${floor(bounded / 60).toInt()}m"
    }
}

internal fun kanbanMarkdownPreview(source: String): String = source
    .replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1")
    .lineSequence()
    .map { line -> line.replace(Regex("^\\s{0,3}(#{1,6}|[-*+]|\\d+[.)])\\s+"), "") }
    .joinToString("\n")
    .replace(Regex("[`*_~]"), "")
    .trim()

private fun String?.normalizedKanbanValue(): String? =
    this?.trim()?.lowercase(Locale.ROOT)?.takeIf(String::isNotEmpty)

private fun String?.normalizedKanbanDisplayValue(): String? =
    this?.trim()?.takeIf(String::isNotEmpty)

private fun List<String>.normalizedSortedKanbanValues(): List<String> =
    mapNotNull { it.normalizedKanbanDisplayValue() }
        .distinctBy { it.lowercase(Locale.ROOT) }
        .sortedWith(String.CASE_INSENSITIVE_ORDER)
