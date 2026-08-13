package com.uzairansar.hermex.ui.kanban

import com.uzairansar.hermex.core.model.KanbanBoardSnapshot
import com.uzairansar.hermex.core.model.KanbanCardSummary
import com.uzairansar.hermex.core.model.KanbanColumn
import org.junit.Assert.assertEquals
import org.junit.Test

class KanbanPresentationTest {
    @Test
    fun statusFocusKeepsNativeOrderAndRetainsUnknownServerColumns() {
        val snapshot = KanbanBoardSnapshot(
            columns = listOf(
                KanbanColumn("ready"),
                KanbanColumn("future"),
                KanbanColumn("todo"),
                KanbanColumn("triage", listOf(KanbanCardSummary(cardId = "odd", status = "later"))),
            ),
        )

        assertEquals(
            listOf("triage", "todo", "blocked", "ready", "running", "done", "archived", "future", "later"),
            availableKanbanStatuses(snapshot, includeArchived = true),
        )
    }

    @Test
    fun searchMatchesIdentityTitleBodyProfileAndTenant() {
        val snapshot = snapshot(
            KanbanCardSummary("CARD-1", "Ship mobile", "ready", "reviewer", "Markdown body", "app"),
            KanbanCardSummary("CARD-2", "Server work", "ready", "builder", "Other", "infra"),
        )

        assertEquals(listOf("CARD-1"), snapshot.searchMatchedCards("markdown").map { it.cardId })
        assertEquals(listOf("CARD-2"), snapshot.searchMatchedCards("infra").map { it.cardId })
        assertEquals(listOf("CARD-1"), visibleKanbanCards(snapshot, "ready", "reviewer").map { it.cardId })
    }

    @Test
    fun profileGroupsPutUnassignedFirstAndSortNames() {
        val groups = groupedKanbanCards(
            listOf(
                KanbanCardSummary(cardId = "3", assignee = "Zulu"),
                KanbanCardSummary(cardId = "1"),
                KanbanCardSummary(cardId = "2", assignee = "alpha"),
            ),
        )

        assertEquals(listOf(null, "alpha", "Zulu"), groups.map { it.profile })
    }

    @Test
    fun stalenessMatchesIosThresholds() {
        assertEquals(KanbanStaleness.Warning, kanbanStaleness(card("running", 600.0)))
        assertEquals(KanbanStaleness.Critical, kanbanStaleness(card("running", 3_600.0)))
        assertEquals(KanbanStaleness.Warning, kanbanStaleness(card("ready", 3_600.0)))
        assertEquals(KanbanStaleness.Warning, kanbanStaleness(card("blocked", 3_600.0)))
        assertEquals(KanbanStaleness.Critical, kanbanStaleness(card("blocked", 86_400.0)))
    }

    @Test
    fun markdownPreviewRemovesCommonInlineAndBlockMarkers() {
        assertEquals(
            "Heading\nitem with link and code",
            kanbanMarkdownPreview("## Heading\n- item with [link](https://example.com) and `code`"),
        )
    }

    private fun snapshot(vararg cards: KanbanCardSummary) = KanbanBoardSnapshot(
        columns = listOf(KanbanColumn("ready", cards.toList())),
    )

    private fun card(status: String, age: Double) = KanbanCardSummary(
        cardId = "CARD",
        status = status,
        ageSeconds = age,
    )
}
