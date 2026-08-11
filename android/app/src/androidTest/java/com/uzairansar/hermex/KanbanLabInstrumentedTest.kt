package com.uzairansar.hermex

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.uzairansar.hermex.ui.kanban.KanbanLabRoute
import com.uzairansar.hermex.ui.kanban.KanbanAvailability
import com.uzairansar.hermex.ui.kanban.KanbanBoardContent
import com.uzairansar.hermex.ui.kanban.KanbanLabUiState
import com.uzairansar.hermex.ui.kanban.KanbanLabFixtureDataSource
import com.uzairansar.hermex.ui.kanban.KanbanCardDetailContent
import com.uzairansar.hermex.ui.kanban.KanbanCardDetailAvailability
import com.uzairansar.hermex.ui.kanban.KanbanCardDetailUiState
import com.uzairansar.hermex.ui.kanban.KanbanWorkerLogState
import com.uzairansar.hermex.core.model.KanbanBoardSnapshot
import com.uzairansar.hermex.core.model.KanbanBoardSummary
import com.uzairansar.hermex.core.model.KanbanCardDetailEnvelope
import com.uzairansar.hermex.core.model.KanbanCardSummary
import com.uzairansar.hermex.core.model.KanbanColumn
import com.uzairansar.hermex.core.model.KanbanComment
import com.uzairansar.hermex.core.model.KanbanDependencyLinks
import com.uzairansar.hermex.core.model.KanbanDetailEvent
import com.uzairansar.hermex.core.model.KanbanDetailEventPayload
import com.uzairansar.hermex.core.model.KanbanDispatchRun
import com.uzairansar.hermex.core.model.KanbanWorkerLog
import com.uzairansar.hermex.ui.theme.HermexTheme
import java.util.concurrent.CopyOnWriteArrayList
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KanbanLabInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun readOnlyStatusFocusBrowsesFiltersAndSwitchesBoardsLocally() {
        val requests = CopyOnWriteArrayList<RecordedRequest>()
        val server = MockWebServer().also { mockServer ->
            mockServer.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    requests += request
                    return when (request.url.encodedPath) {
                        "/api/kanban/config" -> json(
                            """{"columns":["triage","todo","blocked","ready","running","done"],"assignees":["builder","reviewer"],"read_only":false}""",
                        )
                        "/api/kanban/boards" -> json(
                            """{"current":"main","read_only":false,"boards":[{"slug":"main","name":"Main Board","read_only":false},{"slug":"release","name":"Release Board","read_only":false}]}""",
                        )
                        "/api/kanban/board" -> if (request.url.queryParameter("board") == "release") {
                            json(
                                """{"changed":true,"read_only":false,"tenants":["app"],"assignees":["reviewer"],"columns":[{"name":"todo","tasks":[{"id":"CARD-release","title":"Prepare release","status":"todo","assignee":"reviewer","tenant":"app","priority":1,"age_seconds":300}]}]}""",
                            )
                        } else {
                            json(
                                """
                                {
                                  "changed": true,
                                  "read_only": false,
                                  "tenants": ["app", "infra"],
                                  "assignees": ["builder", "reviewer"],
                                  "columns": [
                                    {"name":"triage","tasks":[{"id":"CARD-1","title":"Triage mobile","body":"## Summary\nNeeds review","status":"triage","assignee":"builder","tenant":"app","priority":2,"comment_count":1,"age_seconds":120}]},
                                    {"name":"ready","tasks":[{"id":"CARD-2","title":"Ship Android","body":"- Verify bundle\n- Publish prerelease","status":"ready","assignee":"reviewer","tenant":"app","priority":0,"comment_count":4,"link_counts":{"parents":1,"children":2},"age_seconds":3600}]},
                                    {"name":"future","tasks":[{"id":"CARD-3","title":"Future workflow","status":"future","assignee":null,"tenant":"infra"}]}
                                  ]
                                }
                                """.trimIndent(),
                            )
                        }
                        "/api/kanban/stats" -> json("""{"total":3,"by_status":{"triage":1,"ready":1,"future":1}}""")
                        "/api/kanban/assignees" -> json("""{"assignees":["builder","reviewer"]}""")
                        else -> MockResponse.Builder().code(404).body("""{"error":"unexpected"}""").build()
                    }
                }
            }
            mockServer.start()
        }
        val application = ApplicationProvider.getApplicationContext<android.app.Application>()
        val container = AppContainer(application)

        try {
            composeRule.setContent {
                HermexTheme {
                    KanbanLabRoute(
                        repository = container.kanbanRepository(server.url("/")),
                        onBack = {},
                    )
                }
            }

            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithText("CARD-1").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Main Board").assertIsDisplayed()
            composeRule.onNodeWithText("Unknown Status: future").assertIsDisplayed()
            composeRule.onNodeWithTag("kanban_status_ready").performClick()
            composeRule.onNodeWithText("CARD-2").assertIsDisplayed()
            composeRule.onNodeWithTag("kanban_search").performTextInput("reviewer")
            composeRule.onNodeWithText("Ship Android").assertIsDisplayed()

            composeRule.onNodeWithTag("kanban_filters").performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithText("Apply").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("kanban_filter_profile").performClick()
            composeRule.onAllNodesWithText("reviewer")[1].performClick()
            composeRule.onNodeWithText("Group by Profile").performClick()
            composeRule.onNodeWithText("Apply").performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                requests.any { request ->
                    request.url.encodedPath == "/api/kanban/board" && request.url.queryParameter("assignee") == "reviewer"
                }
            }
            composeRule.onNodeWithTag("kanban_profile_group_reviewer").assertIsDisplayed()

            composeRule.onNodeWithTag("kanban_board_picker").performClick()
            composeRule.onNodeWithText("Release Board").performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                requests.any { request ->
                    request.url.encodedPath == "/api/kanban/board" &&
                        request.url.queryParameter("board") == "release" &&
                        request.url.queryParameter("assignee") == "reviewer"
                }
            }
            composeRule.onNodeWithTag("kanban_status_todo").performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodesWithTag("kanban_card_list").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("kanban_card_list").performScrollToNode(hasText("CARD-release"))
            composeRule.onNodeWithText("CARD-release").assertIsDisplayed()

            assertTrue(requests.any { it.url.encodedPath == "/api/kanban/config" })
            assertTrue(requests.any { it.url.encodedPath == "/api/kanban/boards" })
            assertFalse(requests.any { it.url.encodedPath.endsWith("/switch") })
        } finally {
            server.close()
        }
    }

    @Test
    fun incompatibleHandshakeIsDistinctAndRetryable() {
        val server = MockWebServer().also {
            it.enqueue(json("""{"columns":[],"read_only":false}"""))
            it.start()
        }
        val application = ApplicationProvider.getApplicationContext<android.app.Application>()
        val container = AppContainer(application)

        try {
            composeRule.setContent {
                HermexTheme {
                    KanbanLabRoute(container.kanbanRepository(server.url("/")), onBack = {})
                }
            }

            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithText("Retry").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("kanban_unavailable_IncompatibleContract").assertIsDisplayed()
            composeRule.onNodeWithText("Retry").assertIsDisplayed()
        } finally {
            server.close()
        }
    }

    @Test
    fun streamFailureFallsBackToPollingWithPersistentDelayedNotice() {
        composeRule.setContent {
            HermexTheme {
                KanbanBoardContent(
                    state = liveUiState(liveUpdatesDelayed = true),
                    onRefresh = {},
                    onSearch = {},
                    onSelectStatus = {},
                    onClearFilters = {},
                )
            }
        }

        composeRule.onNodeWithTag("kanban_live_delayed_notice").assertIsDisplayed()
        composeRule.onNodeWithText("CARD-1").assertIsDisplayed()
    }

    @Test
    fun connectivityLossPreservesBoardAndShowsOfflineNotice() {
        composeRule.setContent {
            HermexTheme {
                KanbanBoardContent(
                    state = liveUiState(isOffline = true),
                    onRefresh = {},
                    onSearch = {},
                    onSelectStatus = {},
                    onClearFilters = {},
                )
            }
        }

        composeRule.onNodeWithTag("kanban_offline_notice").assertIsDisplayed()
        composeRule.onNodeWithText("CARD-1").assertIsDisplayed()
    }

    @Test
    fun cardDetailShowsMarkdownCommentsHistoryAndLoadsWorkerLogOnlyOnRequest() {
        var workerLogCalls = 0
        val commentBodies = mutableListOf<String>()
        val card = KanbanCardSummary(
            cardId = "CARD-1",
            title = "Open detail",
            status = "todo",
            body = "## Detailed heading\n- rendered item",
            currentRunId = "RUN-1",
            workerId = "worker-7",
        )
        val detail = KanbanCardDetailEnvelope(
            card = card,
            comments = listOf(KanbanComment(commentId = "COMMENT-1", cardId = "CARD-1", author = "builder", body = "Existing **comment**")),
            events = listOf(KanbanDetailEvent(eventId = "EVENT-1", cardId = "CARD-1", kind = "updated", payload = KanbanDetailEventPayload(summary = "Status changed"))),
            links = KanbanDependencyLinks(prerequisites = listOf("CARD-P"), dependents = listOf("CARD-C")),
            runs = listOf(KanbanDispatchRun(runId = "RUN-1", status = "done", summary = "Worker completed")),
            readOnly = false,
        )
        composeRule.setContent {
            var selected by remember { mutableStateOf(false) }
            var draft by remember { mutableStateOf("") }
            var submitted by remember { mutableStateOf(false) }
            var workerLog by remember { mutableStateOf<KanbanWorkerLogState>(KanbanWorkerLogState.Idle) }
            HermexTheme {
                if (!selected) {
                    KanbanBoardContent(
                        state = liveUiState().copy(
                            selectedStatus = "todo",
                            snapshot = KanbanBoardSnapshot(
                                columns = listOf(KanbanColumn(name = "todo", cards = listOf(card))),
                                changed = true,
                                readOnly = false,
                            ),
                        ),
                        onRefresh = {},
                        onSearch = {},
                        onSelectStatus = {},
                        onClearFilters = {},
                        onOpenCard = { selected = true },
                    )
                } else {
                    KanbanCardDetailContent(
                        state = KanbanCardDetailUiState(
                            availability = KanbanCardDetailAvailability.Content,
                            detail = detail,
                            parentAllowsWrites = true,
                            workerLog = workerLog,
                            commentDraft = draft,
                            commentSubmission = if (submitted) {
                                com.uzairansar.hermex.ui.kanban.KanbanCommentSubmissionState.Succeeded
                            } else {
                                com.uzairansar.hermex.ui.kanban.KanbanCommentSubmissionState.Idle
                            },
                        ),
                        onCommentDraft = { draft = it },
                        onSubmitComment = {
                            commentBodies += draft
                            draft = ""
                            submitted = true
                        },
                        onRetryComment = {},
                        onLoadWorkerLog = {
                            workerLogCalls += 1
                            workerLog = KanbanWorkerLogState.Loaded(
                                KanbanWorkerLog(cardId = "CARD-1", exists = true, content = "explicit worker output", truncated = false),
                            )
                        },
                        onOpenRelatedCard = {},
                    )
                }
            }
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Open detail").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("kanban_card_CARD-1").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Detailed heading", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("kanban_card_detail").performScrollToNode(hasText("builder"))
        composeRule.onNodeWithText("builder").assertIsDisplayed()
        composeRule.onNodeWithTag("kanban_card_detail").performScrollToNode(hasText("CARD-P"))
        composeRule.onNodeWithText("CARD-P").assertIsDisplayed()
        assertTrue(workerLogCalls == 0)

        composeRule.onNodeWithTag("kanban_card_detail").performScrollToNode(hasTestTag("kanban_operational_history"))
        composeRule.onNodeWithTag("kanban_operational_history").performClick()
        composeRule.onNodeWithTag("kanban_card_detail").performScrollToNode(hasTestTag("kanban_detail_events"))
        composeRule.onNodeWithTag("kanban_detail_events").assertIsDisplayed()
        composeRule.onNodeWithTag("kanban_card_detail").performScrollToNode(hasTestTag("kanban_detail_runs"))
        composeRule.onNodeWithTag("kanban_detail_runs").assertIsDisplayed()
        composeRule.onNodeWithTag("kanban_card_detail").performScrollToNode(hasTestTag("kanban_load_worker_log"))
        composeRule.onNodeWithTag("kanban_load_worker_log").performClick()
        composeRule.waitUntil(5_000) { workerLogCalls == 1 }
        composeRule.onNodeWithText("explicit worker output").assertIsDisplayed()

        composeRule.onNodeWithTag("kanban_card_detail").performScrollToNode(hasTestTag("kanban_comment_draft"))
        composeRule.onNodeWithTag("kanban_comment_draft").performTextInput("New comment")
        composeRule.onNodeWithTag("kanban_comment_send").performClick()
        composeRule.waitUntil(5_000) { commentBodies == listOf("New comment") }
        composeRule.onNodeWithText("Added").assertIsDisplayed()
    }

    @Test
    fun readOnlyStaleDetailHidesCommentsAndDisablesWorkerLog() {
        val detail = KanbanCardDetailEnvelope(
            card = KanbanCardSummary(cardId = "CARD-1", title = "Cached", status = "todo"),
            readOnly = true,
        )
        composeRule.setContent {
            HermexTheme {
                KanbanCardDetailContent(
                    state = KanbanCardDetailUiState(
                        availability = KanbanCardDetailAvailability.Content,
                        detail = detail,
                        isStale = true,
                        parentAllowsWrites = false,
                        workerLog = KanbanWorkerLogState.Idle,
                    ),
                    onCommentDraft = {},
                    onSubmitComment = {},
                    onRetryComment = {},
                    onLoadWorkerLog = {},
                    onOpenRelatedCard = {},
                )
            }
        }

        composeRule.onNodeWithTag("kanban_comment_draft").assertDoesNotExist()
        composeRule.onNodeWithTag("kanban_card_detail").performScrollToNode(hasTestTag("kanban_operational_history"))
        composeRule.onNodeWithTag("kanban_operational_history").performClick()
        composeRule.onNodeWithTag("kanban_card_detail").performScrollToNode(hasTestTag("kanban_load_worker_log"))
        composeRule.onNodeWithTag("kanban_load_worker_log").assertIsNotEnabled()
    }

    @Test
    fun createCardEditorSavesAndReconcilesTheBoard() {
        composeRule.setContent {
            HermexTheme {
                KanbanLabRoute(
                    repository = KanbanLabFixtureDataSource("dense"),
                    onBack = {},
                    viewModelKey = "kanban-create-device",
                )
            }
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("CARD-1").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("kanban_new_card").performClick()
        composeRule.onNodeWithTag("kanban_editor_title").performTextInput("Created on Android")
        composeRule.onNodeWithTag("kanban_editor_save").performClick()

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Created on Android").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Created on Android").assertIsDisplayed()
    }

    @Test
    fun editCardEditorPreflightsSavesAndRefreshesDetail() {
        composeRule.setContent {
            HermexTheme {
                KanbanLabRoute(
                    repository = KanbanLabFixtureDataSource("dense"),
                    onBack = {},
                    viewModelKey = "kanban-edit-device",
                )
            }
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("CARD-1").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("kanban_card_CARD-1").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("kanban_edit_card").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("kanban_edit_card").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("kanban_editor_title").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("kanban_editor_title").performTextClearance()
        composeRule.onNodeWithTag("kanban_editor_title").performTextInput("Edited on Android")
        composeRule.onNodeWithTag("kanban_editor_save").performClick()

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Edited on Android").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Edited on Android").assertIsDisplayed()
    }

    @Test
    fun readOnlyBoardDisablesCreateAndEditActions() {
        composeRule.setContent {
            HermexTheme {
                KanbanLabRoute(
                    repository = KanbanLabFixtureDataSource("read-only"),
                    onBack = {},
                    viewModelKey = "kanban-read-only-editor-device",
                )
            }
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("CARD-1").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("kanban_new_card").assertIsNotEnabled()
        composeRule.onNodeWithTag("kanban_select_cards").assertIsNotEnabled()
        composeRule.onNodeWithTag("kanban_card_CARD-1").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("kanban_edit_card").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("kanban_edit_card").assertIsNotEnabled()
        composeRule.onNodeWithTag("kanban_card_actions_CARD-1").assertIsNotEnabled()
    }

    @Test
    fun cardActionsCompleteAndMoveFocusToTheCanonicalStatus() {
        composeRule.setContent {
            HermexTheme {
                KanbanLabRoute(
                    repository = KanbanLabFixtureDataSource("dense"),
                    onBack = {},
                    viewModelKey = "kanban-workflow-complete-device",
                )
            }
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("CARD-1").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("kanban_card_actions_CARD-1").performClick()
        composeRule.onNodeWithTag("kanban_complete_CARD-1").performClick()

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("kanban_status_done").fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText("Triage Android parity").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("kanban_mutation_CARD-1", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun runningArchiveRequiresConfirmationAndOffersUndo() {
        composeRule.setContent {
            HermexTheme {
                KanbanLabRoute(
                    repository = KanbanLabFixtureDataSource("dense"),
                    onBack = {},
                    viewModelKey = "kanban-workflow-running-device",
                )
            }
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("kanban_status_running").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("kanban_status_running").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("kanban_card_CARD-4", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("kanban_card_CARD-4", useUnmergedTree = true).performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("kanban_card_detail").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("kanban_card_actions_CARD-4").performClick()
        composeRule.onNodeWithTag("kanban_archive_CARD-4").performClick()

        composeRule.onNodeWithText("Leave Running?").assertIsDisplayed()
        composeRule.onNodeWithTag("kanban_confirm_running_exit").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("kanban_archive_undo").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("kanban_archive_undo_action").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("CARD-4").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun detailAddsAndRemovesPrerequisiteWithCanonicalRefresh() {
        composeRule.setContent {
            HermexTheme {
                KanbanLabRoute(
                    repository = KanbanLabFixtureDataSource("dense"),
                    onBack = {},
                    viewModelKey = "kanban-workflow-dependency-device",
                )
            }
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("CARD-1").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("kanban_card_CARD-1").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("kanban_card_detail").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("kanban_card_detail")
            .performScrollToNode(hasTestTag("kanban_add_prerequisite"))
        composeRule.onNodeWithTag("kanban_add_prerequisite").performClick()
        composeRule.onNodeWithTag("kanban_prerequisite_option_CARD-2").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("kanban_remove_prerequisite_CARD-2").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("kanban_remove_prerequisite_CARD-2").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("kanban_remove_prerequisite_CARD-2").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun selectionChangesStatusWithoutOpeningCardDetail() {
        composeRule.setContent {
            HermexTheme {
                KanbanLabRoute(
                    repository = KanbanLabFixtureDataSource("dense"),
                    onBack = {},
                    viewModelKey = "kanban-bulk-status-device",
                )
            }
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("kanban_card_CARD-1").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("kanban_select_cards").performClick()
        composeRule.onNodeWithTag("kanban_card_CARD-1").performClick().assertIsSelected()
        composeRule.onNodeWithTag("kanban_selection_controls").assertIsDisplayed()
        composeRule.onNodeWithText("1 Card · Selected").assertIsDisplayed()
        composeRule.onNodeWithTag("kanban_bulk_actions").performClick()
        composeRule.onNodeWithText("1 Card").assertIsDisplayed()
        composeRule.onNodeWithTag("kanban_bulk_change_status").performClick()

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("kanban_bulk_summary").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("kanban_status_todo").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("kanban_card_CARD-1").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Triage Android parity").assertIsDisplayed()
        composeRule.onNodeWithTag("kanban_card_detail").assertDoesNotExist()
    }

    @Test
    fun bulkMoveOutOfRunningRequiresConfirmation() {
        composeRule.setContent {
            HermexTheme {
                KanbanLabRoute(
                    repository = KanbanLabFixtureDataSource("dense"),
                    onBack = {},
                    viewModelKey = "kanban-bulk-running-device",
                )
            }
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("kanban_status_running").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("kanban_status_running").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("kanban_card_CARD-4", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("kanban_select_cards").performClick()
        composeRule.onNodeWithTag("kanban_card_CARD-4", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("kanban_bulk_actions").performClick()
        composeRule.onNodeWithTag("kanban_bulk_change_status").performClick()

        composeRule.onNodeWithText("Leave Running?").assertIsDisplayed()
        composeRule.onNodeWithTag("kanban_bulk_confirm_running_exit").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("kanban_bulk_summary").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun partialBulkFailureRetainsOnlyFailedCardAndRetryFailedTargetsIt() {
        composeRule.setContent {
            HermexTheme {
                KanbanLabRoute(
                    repository = KanbanLabFixtureDataSource("partial"),
                    onBack = {},
                    viewModelKey = "kanban-bulk-partial-device",
                )
            }
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("kanban_card_CARD-1").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("kanban_select_cards").performClick()
        composeRule.onNodeWithTag("kanban_card_CARD-1").performClick()
        composeRule.onNodeWithTag("kanban_status_blocked").performClick()
        composeRule.onNodeWithTag("kanban_card_CARD-3").performClick()
        composeRule.onNodeWithTag("kanban_bulk_actions").performClick()
        composeRule.onNodeWithTag("kanban_bulk_change_status").performClick()

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("kanban_bulk_retry_failed").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("kanban_bulk_member_CARD-3").assertIsDisplayed()
        composeRule.onNodeWithTag("kanban_bulk_retry_failed").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("kanban_bulk_retry_failed").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("kanban_status_todo").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("kanban_card_CARD-3").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun bulkArchiveRequiresConfirmationAndShowsCanonicalSummary() {
        composeRule.setContent {
            HermexTheme {
                KanbanLabRoute(
                    repository = KanbanLabFixtureDataSource("dense"),
                    onBack = {},
                    viewModelKey = "kanban-bulk-archive-device",
                )
            }
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("kanban_card_CARD-1").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("kanban_select_cards").performClick()
        composeRule.onNodeWithTag("kanban_card_CARD-1").performClick()
        composeRule.onNodeWithTag("kanban_bulk_actions").performClick()
        composeRule.onNodeWithTag("kanban_bulk_archive").performScrollTo().performClick()

        composeRule.onNodeWithTag("kanban_bulk_confirm_archive").assertIsDisplayed().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("kanban_bulk_summary").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("kanban_bulk_summary").assertIsDisplayed()
    }

    private fun liveUiState(
        isOffline: Boolean = false,
        liveUpdatesDelayed: Boolean = false,
    ) = KanbanLabUiState(
        availability = KanbanAvailability.Content,
        boards = listOf(KanbanBoardSummary(slug = "main", name = "Main")),
        selectedBoardSlug = "main",
        snapshot = KanbanBoardSnapshot(
            columns = listOf(
                KanbanColumn(
                    name = "triage",
                    cards = listOf(KanbanCardSummary(cardId = "CARD-1", title = "Cached Card", status = "triage")),
                ),
            ),
            changed = true,
            latestEventId = 42,
            readOnly = true,
        ),
        isOffline = isOffline,
        liveUpdatesDelayed = liveUpdatesDelayed,
    )

    private fun json(body: String): MockResponse = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()
}
