package com.uzairansar.hermex

import android.app.Application
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText as hasSemanticsText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.uzairansar.hermex.core.network.HermesApiClient
import com.uzairansar.hermex.core.network.PersistentCookieJar
import com.uzairansar.hermex.data.preferences.StreamingSendBehavior
import com.uzairansar.hermex.data.repository.AuthRepository
import com.uzairansar.hermex.data.repository.AuthState
import com.uzairansar.hermex.data.secure.SecretStore
import com.uzairansar.hermex.data.secure.ServerAccount
import com.uzairansar.hermex.data.secure.ServerRegistry
import com.uzairansar.hermex.ui.chat.ChatRoute
import com.uzairansar.hermex.ui.git.GitRoute
import com.uzairansar.hermex.ui.onboarding.OnboardingRoute
import com.uzairansar.hermex.ui.panels.PanelsRoute
import com.uzairansar.hermex.ui.sessions.SessionListRoute
import com.uzairansar.hermex.ui.settings.SettingsRoute
import com.uzairansar.hermex.ui.theme.HermexTheme
import com.uzairansar.hermex.ui.workspace.WorkspaceRoute
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class HermexUiFlowTest {
    private var server: MockWebServer? = null
    val composeRule = createComposeRule()

    @get:Rule
    val ruleChain: TestRule = RuleChain
        .outerRule(object : ExternalResource() {
            override fun after() {
                runCatching { server?.close() }
                server = null
            }
        })
        .around(composeRule)

    @Test
    fun onboardingTestsConnectionAgainstServer() {
        val mockServer = startServer(
            json("""{"status":"ok"}"""),
            json("""{"auth_enabled":false,"password_auth_enabled":true}"""),
        )
        val authRepository = authRepository()

        composeRule.setContent {
            HermexTheme {
                OnboardingRoute(authRepository = authRepository, onConnected = {})
            }
        }

        composeRule.onNodeWithText("Already have a server?").performClick()
        composeRule.onNodeWithContentDescription("Server URL").performTextInput(mockServer.url("/").toString())
        composeRule.onNodeWithText("Advanced").performClick()
        composeRule.onNodeWithContentDescription("Custom Headers").performTextInput("CF-Access-Client-Id: id")
        composeRule.onNodeWithContentDescription("Test Connection").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            hasText("Connection ok. Password not required.")
        }

        composeRule.onNodeWithText("Connection ok. Password not required.").assertIsDisplayed()
        val healthRequest = mockServer.takeRequest()
        val authRequest = mockServer.takeRequest()
        assertEquals("/health", healthRequest.url.encodedPath)
        assertEquals("/api/auth/status", authRequest.url.encodedPath)
        assertEquals("id", healthRequest.headers["CF-Access-Client-Id"])
        assertEquals("id", authRequest.headers["CF-Access-Client-Id"])
    }

    @Test
    fun gitRouteUsesGroupedRepositorySections() {
        val mockServer = MockWebServer().also { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when (request.url.encodedPath) {
                    "/api/git/status" -> json(
                        """{"ok":true,"is_git":true,"branch":"main","files":[],"changed_count":0,"total_additions":0,"total_deletions":0}""",
                    )
                    "/api/git/branches" -> json(
                        """{"ok":true,"branches":{"is_git":true,"current":"main","local":[{"name":"main"}]}}""",
                    )
                    else -> MockResponse.Builder().code(404).body("""{"error":"unexpected"}""").build()
                }
            }
            server.start()
            this.server = server
        }
        val application = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(application)

        composeRule.setContent {
            HermexTheme {
                GitRoute(
                    sessionId = "s1",
                    repository = container.gitRepository(mockServer.url("/")),
                    onBack = {},
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) { hasText("Working tree clean") }
        composeRule.onNodeWithText("WORKING TREE").assertIsDisplayed()
        composeRule.onNodeWithText("0 changes").assertIsDisplayed()
        composeRule.onNodeWithText("BRANCHES").assertIsDisplayed()
        composeRule.onNodeWithText("COMMIT").assertIsDisplayed()
        composeRule.onNodeWithText("Fetch").assertHasClickAction()
        composeRule.onNodeWithText("Pull").assertHasClickAction()
        composeRule.onNodeWithText("Push").assertHasClickAction()
    }

    @Test
    fun onboardingConnectsAndInvokesConnectedCallback() {
        val mockServer = startServer(
            json("""{"status":"ok"}"""),
            json("""{"auth_enabled":false,"password_auth_enabled":true}"""),
        )
        val authRepository = authRepository()

        composeRule.setContent {
            HermexTheme {
                var connected by remember { mutableStateOf(false) }
                OnboardingRoute(
                    authRepository = authRepository,
                    onConnected = { connected = true },
                )
                if (connected) Text("Connected")
            }
        }

        composeRule.onNodeWithText("Already have a server?").performClick()
        composeRule.onNodeWithContentDescription("Server URL").performTextInput(mockServer.url("/").toString())
        composeRule.onNodeWithContentDescription("Connect").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) { hasText("Connected") }

        composeRule.onNodeWithText("Connected").assertIsDisplayed()
        assertEquals("/health", mockServer.takeRequest().url.encodedPath)
        assertEquals("/api/auth/status", mockServer.takeRequest().url.encodedPath)
    }

    @Test
    fun onboardingMirrorsIosSetupPagerAndCopyReminder() {
        composeRule.setContent {
            HermexTheme {
                OnboardingRoute(authRepository = authRepository(), onConnected = {})
            }
        }

        composeRule.onNodeWithText("Control your Hermes agent from Android.").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Get Started").performClick()
        composeRule.waitUntil(timeoutMillis = 2_000) { hasText("What you get") }
        composeRule.onNodeWithContentDescription("Set Up").performClick()
        composeRule.waitUntil(timeoutMillis = 2_000) { hasText("Set up Hermes Web UI") }
        composeRule.onNodeWithContentDescription("Continue").performClick()
        composeRule.onNodeWithText("Copy the setup prompt first").assertIsDisplayed()
        composeRule.onNodeWithText("Stay Here").performClick()
        composeRule.onNodeWithContentDescription("Copy prompt").performClick()
        composeRule.onNodeWithContentDescription("Continue").performClick()
        composeRule.waitUntil(timeoutMillis = 2_000) { hasText("Install Tailscale on Android") }
    }

    @Test
    fun sessionListRendersServerSessionsAndProjects() {
        val sessionRequests = mutableListOf<String?>()
        var switchProfileBody = ""
        var branchBody = ""
        val mockServer = MockWebServer().also { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    return when (request.url.encodedPath) {
                        "/api/projects" -> json(
                            """{"projects":[{"project_id":"proj-1","name":"Mobile","color":"#7DD3FCFF"},{"project_id":"proj-3","name":"Purple","color":"not-a-color"}]}""",
                        )
                        "/api/profiles" -> json(
                            """
                            {
                              "active": "default",
                              "single_profile_mode": false,
                              "profiles": [
                                {"name":"default","model":"gpt-5","provider":"openai"},
                                {"name":"review","display_name":"Review","model":"o4-mini","provider":"openai"}
                              ]
                            }
                            """.trimIndent(),
                        )
                        "/api/profile/switch" -> {
                            switchProfileBody = request.body?.utf8().orEmpty()
                            json("""{"ok":true}""")
                        }
                        "/api/session/branch" -> {
                            branchBody = request.body?.utf8().orEmpty()
                            json("""{"session_id":"s-copy","title":"Android Port (copy)","parent_session_id":"s1"}""")
                        }
                        "/api/session" -> json(
                            """
                            {
                              "session": {
                                "session_id": "s-copy",
                                "title": "Android Port (copy)",
                                "workspace": "/workspace/hermex",
                                "model": "gpt-5",
                                "message_count": 3,
                                "created_at": 1720000000,
                                "updated_at": 1720000100,
                                "project_id": "proj-1"
                              }
                            }
                            """.trimIndent(),
                        )
                        "/api/sessions" -> {
                            sessionRequests += request.url.queryParameter("include_archived")
                            val includeArchived = request.url.queryParameter("include_archived") == "1"
                            json(
                                if (includeArchived) {
                                    """
                                    {
                                      "sessions": [
                                        {
                                          "session_id": "s1",
                                          "title": "Android Port",
                                          "workspace": "/workspace/hermex",
                                          "model": "gpt-5",
                                          "message_count": 3,
                                          "pinned": true,
                                          "active_stream_id": "run-1",
                                          "is_streaming": true,
                                          "project_id": "proj-1"
                                        },
                                        {
                                          "session_id": "s2",
                                          "title": "Old Debug Run",
                                          "workspace": "/workspace/hermex",
                                          "message_count": 1,
                                          "archived": true
                                        }
                                      ],
                                      "archived_count": 2
                                    }
                                    """.trimIndent()
                                } else {
                                    """
                                    {
                                      "sessions": [
                                        {
                                          "session_id": "s1",
                                          "title": "Android Port",
                                          "workspace": "/workspace/hermex",
                                          "model": "gpt-5",
                                          "message_count": 3,
                                          "pinned": true,
                                          "active_stream_id": "run-1",
                                          "is_streaming": true,
                                          "project_id": "proj-1"
                                        }
                                      ],
                                      "archived_count": 2
                                    }
                                    """.trimIndent()
                                },
                            )
                        }
                        else -> MockResponse.Builder().code(404).body("""{"error":"unexpected"}""").build()
                    }
                }
            }
            server.start()
            this.server = server
        }
        val application = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(application)
        val account = ServerAccount(
            id = mockServer.url("/").toString(),
            urlString = mockServer.url("/").toString(),
            displayName = "Mock Hermex",
            initials = "MH",
        )
        var openedPanel: String? = null
        var openedChat: String? = null

        composeRule.setContent {
            HermexTheme {
                SessionListRoute(
                    authState = AuthState.LoggedIn(mockServer.url("/"), account),
                    container = container,
                    onOpenChat = { openedChat = it },
                    onOpenVoiceChat = {},
                    onOpenSharedDraft = {},
                    onOpenPanels = {},
                    onOpenPanel = { openedPanel = it },
                    onOpenSettings = {},
                    onNeedsOnboarding = {},
                )
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { hasText("Android Port") }

        composeRule.onNodeWithContentDescription("HERMEX").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Search sessions").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Search sessions").performClick()
        composeRule.onNodeWithTag("session_search_field").assertIsFocused()
        composeRule.onNodeWithContentDescription("Close search").performClick()
        composeRule.onNodeWithContentDescription("Search sessions").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Settings").assertIsDisplayed()
        composeRule.onNodeWithText("Chat").assertIsDisplayed()
        composeRule.onNodeWithText("Sessions").assertIsDisplayed()
        composeRule.onNodeWithText("Android Port").assertIsDisplayed()
        assertTrue(hasText("3 messages", substring = true))
        composeRule.onNodeWithText("Live").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("gpt-5").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("pin").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithContentDescription("Session actions").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithTag("session_row_s1").performTouchInput { longClick() }
        composeRule.onNodeWithText("Duplicate").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Export HTML").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Export JSON").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Duplicate").performScrollTo()
        composeRule.onNodeWithText("Duplicate").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { openedChat == "s-copy" }
        assertTrue(branchBody.contains(""""session_id":"s1""""))
        assertTrue(branchBody.contains(""""title":"Android Port (copy)""""))
        composeRule.onNodeWithText("Tasks").assertIsDisplayed()
        composeRule.onNodeWithText("Skills").assertIsDisplayed()
        composeRule.onNodeWithText("Memory").assertIsDisplayed()
        composeRule.onNodeWithText("Insights").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("Cal").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Tool").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Mem").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Chart").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithText("Projects").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("Mobile").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("New project").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithText("Projects").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { hasText("Mobile") }
        composeRule.onNodeWithText("Purple").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Add project").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag("session_swipe_action_delete").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithTag("session_swipe_action_archive").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithTag("session_row_s1").performTouchInput { swipeLeft() }
        composeRule.onNodeWithTag("session_row_s1").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag("session_swipe_action_delete").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithTag("session_swipe_action_archive").fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithText("Tasks").performClick()
        assertEquals("tasks", openedPanel)
        composeRule.waitUntil(timeoutMillis = 5_000) { hasText("Active Profile") }
        composeRule.onNodeWithText("Active Profile").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { hasText("Review") }
        composeRule.onNodeWithText("gpt-5").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Selected profile Default").assertIsDisplayed()
        composeRule.onNodeWithText("Review").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { switchProfileBody.contains("review") }
        assertTrue(switchProfileBody.contains(""""name":"review""""))
        assertTrue(composeRule.onAllNodesWithText("Archived Sessions").fetchSemanticsNodes().isEmpty())
        assertTrue(sessionRequests.contains("1"))
    }

    @Test
    fun chatRouteSendsMessageAndRendersAssistantTurn() {
        val chatStarted = AtomicBoolean(false)
        var chatStartBody = ""
        var chatProfileSwitchBody = ""
        var chatReasoningBody = ""
        var chatRenameBody = ""
        var chatBranchBody = ""
        var chatNewSessionBody = ""
        var chatBtwBody = ""
        var chatBackgroundBody = ""
        var chatSkillsRequested = false
        val chatPersonalityBodies = mutableListOf<String>()
        val mockServer = MockWebServer().also { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    return when (request.url.encodedPath) {
                        "/api/session" -> json(
                            if (request.url.queryParameter("session_id") == "s-fork") {
                                """
                                {
                                  "session": {
                                    "session_id": "s-fork",
                                    "title": "Forked Android",
                                    "workspace": "/workspace/mobile",
                                    "model": "gpt-4o",
                                    "model_provider": "openai",
                                    "profile": "review",
                                    "message_count": 4
                                  }
                                }
                                """.trimIndent()
                            } else if (chatStarted.get()) {
                                """
                                {
                                  "session": {
                                    "session_id": "s1",
                                    "title": "Android Port",
                                    "workspace": "/workspace/hermex",
                                    "profile": "default",
                                    "context_length": 8000,
                                    "last_prompt_tokens": 2000,
                                    "input_tokens": 1800,
                                    "output_tokens": 200,
                                    "threshold_tokens": 7000,
                                    "estimated_cost": 0.0123,
                                    "messages": [
                                      {"role": "user", "content": "Hello Android"},
                                      {"role": "user", "content": "[your active task list was preserved across context compression]\n- Keep Android UI parity tight"},
                                      {"role": "local_notice", "content": "Cached local note"},
                                      {
                                        "role": "assistant",
                                        "content": "Mock response",
                                        "reasoning": [
                                          {"text": "Thinking through Android parity"}
                                        ],
                                        "attachments": [
                                          {"name": "design.pdf", "path": "/workspace/hermex/design.pdf", "mime": "application/pdf", "size": 12345}
                                        ],
                                        "tool_calls": [
                                          {"name": "shell", "preview": "pwd", "args": {"cmd": "pwd"}, "result": {"exit_code": 0}, "is_error": false}
                                        ]
                                      }
                                    ]
                                  }
                                }
                                """.trimIndent()
                            } else {
                                """
                                {
                                  "session": {
                                    "session_id": "s1",
                                    "title": "Android Port",
                                    "workspace": "/workspace/hermex",
                                    "profile": "default",
                                    "context_length": 8000,
                                    "last_prompt_tokens": 2000,
                                    "input_tokens": 1800,
                                    "output_tokens": 200,
                                    "threshold_tokens": 7000,
                                    "estimated_cost": 0.0123,
                                    "messages": []
                                  }
                                }
                                """.trimIndent()
                            },
                        )
                        "/api/models" -> json(
                            """{"models":[{"id":"gpt-5","label":"GPT-5","provider":"openai"},{"id":"gpt-4o","label":"GPT-4o","provider":"openai"}]}""",
                        )
                        "/api/profiles" -> json("""{"profiles":[{"name":"default","display_name":"Default"},{"name":"review","display_name":"Review"}]}""")
                        "/api/workspaces" -> json(
                            """
                            {
                              "last": "/workspace/hermex",
                              "workspaces": [
                                {"path": "/workspace/hermex", "name": "Hermex"},
                                {"path": "/workspace/mobile", "name": "Mobile"}
                              ]
                            }
                            """.trimIndent(),
                        )
                        "/api/reasoning" -> {
                            if (request.method == "POST") {
                                chatReasoningBody = request.body?.utf8().orEmpty()
                            }
                            json("""{"effort":"medium","supported_efforts":["low","medium","high"]}""")
                        }
                        "/api/session/update" -> json(
                            request.body?.utf8().orEmpty().let { body ->
                                val workspace = if (body.contains("/workspace/mobile")) "/workspace/mobile" else "/workspace/hermex"
                                val model = if (body.contains("gpt-4o")) "gpt-4o" else "gpt-5"
                                """
                                {
                                  "session": {
                                    "session_id": "s1",
                                    "workspace": "$workspace",
                                    "model": "$model",
                                    "model_provider": "openai"
                                  }
                                }
                                """.trimIndent()
                            },
                        )
                        "/api/profile/switch" -> {
                            chatProfileSwitchBody = request.body?.utf8().orEmpty()
                            json("""{"ok":true}""")
                        }
                        "/api/personalities" -> json(
                            """
                            {
                              "personalities": [
                                {"name":"mentor","description":"Patient technical coach"},
                                {"name":"reviewer","description":"Strict code reviewer"}
                              ]
                            }
                            """.trimIndent(),
                        )
                        "/api/personality/set" -> {
                            val body = request.body?.utf8().orEmpty()
                            chatPersonalityBodies += body
                            if (body.contains("mentor")) {
                                json("""{"ok":true,"personality":"mentor","prompt":"Be patient."}""")
                            } else {
                                json("""{"ok":true}""")
                            }
                        }
                        "/api/session/rename" -> {
                            chatRenameBody = request.body?.utf8().orEmpty()
                            json("""{"ok":true,"session":{"session_id":"s1","title":"Mobile Title"}}""")
                        }
                        "/api/session/branch" -> {
                            chatBranchBody = request.body?.utf8().orEmpty()
                            json("""{"session_id":"s-fork","title":"Forked Android","parent_session_id":"s1"}""")
                        }
                        "/api/session/new" -> {
                            chatNewSessionBody = request.body?.utf8().orEmpty()
                            json(
                                """
                                {
                                  "ok": true,
                                  "session": {
                                    "session_id": "s-new",
                                    "title": "New Android Session",
                                    "workspace": "/workspace/mobile",
                                    "model": "gpt-4o",
                                    "model_provider": "openai",
                                    "profile": "review"
                                  }
                                }
                                """.trimIndent(),
                            )
                        }
                        "/api/btw" -> {
                            chatBtwBody = request.body?.utf8().orEmpty()
                            json("""{"stream_id":"btw-1","session_id":"s-btw","parent_session_id":"s1"}""")
                        }
                        "/api/background" -> {
                            chatBackgroundBody = request.body?.utf8().orEmpty()
                            json("""{"task_id":"bg-1","session_id":"s-bg"}""")
                        }
                        "/api/background/status" -> json(
                            """
                            {
                              "results": [
                                {
                                  "task_id": "bg-1",
                                  "prompt": "parallel work",
                                  "answer": "Background answer",
                                  "completed_at": 1720000200
                                }
                              ]
                            }
                            """.trimIndent(),
                        )
                        "/api/skills" -> {
                            chatSkillsRequested = true
                            json(
                                """
                                {
                                  "skills": [
                                    {"name":"review-code","description":"Review code changes","category":"Engineering","enabled":true,"tags":["review","code"]},
                                    {"name":"write-docs","description":"Draft documentation","category":"Writing","enabled":true}
                                  ]
                                }
                                """.trimIndent(),
                            )
                        }
                        "/api/chat/start" -> {
                            chatStarted.set(true)
                            chatStartBody = request.body?.utf8().orEmpty()
                            json("""{"stream_id":"stream-1","session_id":"s1"}""")
                        }
                        "/api/chat/stream" -> {
                            if (request.url.queryParameter("stream_id") == "btw-1") {
                                eventStream(
                                    """
                                    event: token
                                    data: {"text":"Side answer"}

                                    event: done
                                    data: {"session_id":"s-btw"}

                                    """.trimIndent(),
                                )
                            } else {
                                eventStream(
                                    """
                                    event: token
                                    data: {"text":"Mock response"}

                                    event: done
                                    data: {"session_id":"s1"}

                                    """.trimIndent(),
                                )
                            }
                        }
                        "/api/approval/pending", "/api/clarify/pending" -> json("{}")
                        else -> MockResponse.Builder().code(404).body("""{"error":"unexpected"}""").build()
                    }
                }
            }
            server.start()
            this.server = server
        }
        val application = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(application)
        var openedChat: String? = null

        composeRule.setContent {
            HermexTheme {
                ChatRoute(
                    sessionId = "s1",
                    repository = container.chatRepository(mockServer.url("/")),
                    onOpenChat = { openedChat = it },
                    onBack = {},
                    onOpenWorkspace = {},
                    onOpenGit = {},
                )
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("Message").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { hasText("Hermex") }
        composeRule.onNodeWithContentDescription("Files").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithContentDescription("Git").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithContentDescription("Refresh").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithContentDescription("Clear conversation").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Undo").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Retry").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Compress").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithText("Android Port").assertIsDisplayed()
        composeRule.onNodeWithText("Send a message to start the conversation.").assertIsDisplayed()
        composeRule.onNodeWithText("25").assertIsDisplayed()
        composeRule.onNodeWithText("25").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { hasText("Context Window") }
        composeRule.onNodeWithText("2.0K / 8.0K").assertIsDisplayed()
        composeRule.onNodeWithText("Input").assertIsDisplayed()
        composeRule.onNodeWithText("1.8K").assertIsDisplayed()
        composeRule.onNodeWithText("Output").assertIsDisplayed()
        composeRule.onNodeWithText("200").assertIsDisplayed()
        composeRule.onNodeWithText("Threshold").assertIsDisplayed()
        composeRule.onNodeWithText("7.0K").assertIsDisplayed()
        composeRule.onNodeWithText("Cost").assertIsDisplayed()
        composeRule.onNodeWithText("$0.0123").assertIsDisplayed()
        composeRule.onNodeWithText("Done").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { !hasText("Context Window") }
        composeRule.onNodeWithContentDescription("Attach").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { hasText("Attach File") }
        composeRule.onNodeWithText("Attach File").assertIsDisplayed()
        composeRule.onNodeWithText("Photos").assertIsDisplayed()
        composeRule.onNodeWithText("Done").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { !hasText("Attach File") }
        composeRule.onNodeWithText("Hermex").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { hasText("Choose Workspace") }
        composeRule.onNodeWithText("Mobile").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { hasText("Mobile") }
        composeRule.onNodeWithText("GPT-5").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { hasText("Choose Model") }
        composeRule.onNodeWithText("Search models").performTextInput("gpt-4o")
        composeRule.waitUntil(timeoutMillis = 5_000) { hasText("GPT-4o") }
        composeRule.onNodeWithText("GPT-4o").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { hasText("GPT-4o") }
        composeRule.onNodeWithText("Default").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { hasText("Review") }
        composeRule.onNodeWithText("Review").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { chatProfileSwitchBody.contains("review") }
        composeRule.onNodeWithContentDescription("Message").performTextInput("/personality")
        composeRule.onNodeWithContentDescription("Send").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { hasText("mentor", substring = true) }
        composeRule.onNodeWithContentDescription("Message").performTextInput("/personality mentor")
        composeRule.onNodeWithContentDescription("Send").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { chatPersonalityBodies.any { it.contains("mentor") } }
        composeRule.onNodeWithContentDescription("Message").performTextInput("/personality none")
        composeRule.onNodeWithContentDescription("Send").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { chatPersonalityBodies.any { it.contains(""""name":""""") } }
        composeRule.onNodeWithText("Medium").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { hasText("High") }
        composeRule.onNodeWithText("High").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { chatReasoningBody.contains("high") }
        composeRule.onNodeWithContentDescription("Message").performTextInput("/title Mobile Title")
        composeRule.onNodeWithContentDescription("Send").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) { chatRenameBody.contains("Mobile Title") }
        composeRule.waitUntil(timeoutMillis = 5_000) { hasText("Mobile Title") }
        composeRule.onNodeWithText("Mobile Title").assertExists()
        composeRule.onNodeWithContentDescription("Message").performTextInput("/branch Forked Android")
        composeRule.onNodeWithContentDescription("Send").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { openedChat == "s-fork" }
        composeRule.onNodeWithContentDescription("Message").performTextInput("/new")
        composeRule.onNodeWithContentDescription("Send").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { openedChat == "s-new" }
        composeRule.onNodeWithContentDescription("Message").performTextInput("/btw side question")
        composeRule.onNodeWithContentDescription("Send").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { hasText("Side answer", substring = true) }
        composeRule.onNodeWithContentDescription("Message").performTextInput("/background parallel work")
        composeRule.onNodeWithContentDescription("Send").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { hasText("Background answer", substring = true) }
        composeRule.onNodeWithContentDescription("Message").performTextInput("/skills review")
        composeRule.onNodeWithContentDescription("Send").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { hasText("review-code", substring = true) }
        composeRule.onNodeWithContentDescription("Message").performTextInput("Hello Android")
        composeRule.onNodeWithContentDescription("Send").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { hasText("Mock response") }

        composeRule.onNodeWithText("Mock response").assertExists()
        val shortUserBubbleWidth = composeRule
            .onAllNodesWithTag("user_message_bubble")
            .fetchSemanticsNodes()
            .last()
            .boundsInRoot
            .width
        val screenWidth = composeRule.onRoot().fetchSemanticsNode().boundsInRoot.width
        assertTrue(shortUserBubbleWidth < screenWidth * 0.65f)
        assertTrue(chatStartBody.contains(""""message":"Hello Android""""))
        assertTrue(chatStartBody.contains(""""session_id":"s1""""))
        assertTrue(chatStartBody.contains(""""model":"gpt-4o""""))
        assertTrue(chatStartBody.contains(""""model_provider":"openai""""))
        assertTrue(chatStartBody.contains(""""workspace":"/workspace/mobile""""))
        assertTrue(chatProfileSwitchBody.contains(""""name":"review""""))
        assertTrue(chatPersonalityBodies.any { it.contains(""""session_id":"s1"""") && it.contains(""""name":"mentor"""") })
        assertTrue(chatPersonalityBodies.any { it.contains(""""session_id":"s1"""") && it.contains(""""name":""""") })
        assertTrue(chatReasoningBody.contains(""""effort":"high""""))
        assertTrue(chatRenameBody.contains(""""session_id":"s1""""))
        assertTrue(chatRenameBody.contains(""""title":"Mobile Title""""))
        assertTrue(chatBranchBody.contains(""""session_id":"s1""""))
        assertTrue(chatBranchBody.contains(""""title":"Forked Android""""))
        assertTrue(chatNewSessionBody.contains(""""workspace":"/workspace/mobile""""))
        assertTrue(chatNewSessionBody.contains(""""model":"gpt-4o""""))
        assertTrue(chatNewSessionBody.contains(""""model_provider":"openai""""))
        assertTrue(chatNewSessionBody.contains(""""profile":"review""""))
        assertTrue(chatBtwBody.contains(""""session_id":"s1""""))
        assertTrue(chatBtwBody.contains(""""question":"side question""""))
        assertTrue(chatBackgroundBody.contains(""""session_id":"s1""""))
        assertTrue(chatBackgroundBody.contains(""""prompt":"parallel work""""))
        assertTrue(chatSkillsRequested)
    }

    @Test
    fun chatRouteHonorsExpandedTranscriptDisplaySettings() {
        val mockServer = MockWebServer().also { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    return when (request.url.encodedPath) {
                        "/api/session" -> json(
                            """
                            {
                              "session": {
                                "session_id": "s1",
                                "title": "Display Settings",
                                "workspace": "/workspace/hermex",
                                "messages": [
                                  {
                                    "role": "user",
                                    "content": "Please inspect\n[Attached files: /workspace/hermex/design.pdf]"
                                  },
                                  {
                                    "role": "assistant",
                                    "content": "Visible answer\n```kotlin\nval parity = true\n```",
                                    "timestamp": 1720000200,
                                    "reasoning": [{"text":"Deep Android parity thought"}],
                                    "tool_calls": [
                                      {"name":"shell","preview":"pwd","args":{"cmd":"pwd"},"result":{"exit_code":0},"is_error":false}
                                    ]
                                  }
                                ]
                              }
                            }
                            """.trimIndent(),
                        )
                        "/api/models" -> json("""{"models":[{"id":"gpt-5","label":"GPT-5","provider":"openai"}]}""")
                        "/api/profiles" -> json("""{"profiles":[]}""")
                        "/api/workspaces" -> json("""{"workspaces":[{"path":"/workspace/hermex","name":"Hermex"}]}""")
                        "/api/reasoning" -> json("""{"effort":"medium","supported_efforts":["medium"]}""")
                        "/api/approval/pending", "/api/clarify/pending" -> json("{}")
                        else -> MockResponse.Builder().code(404).body("""{"error":"unexpected"}""").build()
                    }
                }
            }
            server.start()
            this.server = server
        }
        val application = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(application)
        runBlocking {
            container.localSettingsRepository.setShowThinkingAndToolCards(true)
            container.localSettingsRepository.setThinkingCardsStartExpanded(true)
            container.localSettingsRepository.setToolCardsStartExpanded(true)
            container.localSettingsRepository.setHidesAttachmentPaths(false)
            container.localSettingsRepository.setShowsAssistantTurnTimestamps(true)
            container.localSettingsRepository.setWrapsCodeBlockLines(false)
        }

        composeRule.setContent {
            HermexTheme {
                ChatRoute(
                    sessionId = "s1",
                    repository = container.chatRepository(mockServer.url("/")),
                    localSettingsRepository = container.localSettingsRepository,
                    onBack = {},
                    onOpenWorkspace = {},
                    onOpenGit = {},
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) { hasText("Visible answer") }
        composeRule.onNodeWithText("[Attached files: /workspace/hermex/design.pdf]", substring = true).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Assistant response timestamp").assertIsDisplayed()
        composeRule.onNodeWithText("Thinking").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("Deep Android parity thought").fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithText("kotlin").assertIsDisplayed()
        composeRule.onNodeWithTag("chat_transcript").performScrollToNode(hasSemanticsText("val parity = true"))
        composeRule.onNodeWithText("val parity = true").assertExists()
        composeRule.onNodeWithText("Wrap").assertExists()
        composeRule.onNodeWithText("Copy").assertExists()
        assertTrue(composeRule.onAllNodesWithText("shell").fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithText("Preview").assertIsDisplayed()
        composeRule.onNodeWithText("Arguments").assertIsDisplayed()
    }

    @Test
    fun chatRouteHidesThinkingAndToolCardsWhenDisabled() {
        val mockServer = MockWebServer().also { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    return when (request.url.encodedPath) {
                        "/api/session" -> json(
                            """
                            {
                              "session": {
                                "session_id": "s1",
                                "title": "Hidden Cards",
                                "workspace": "/workspace/hermex",
                                "messages": [
                                  {
                                    "role": "assistant",
                                    "content": "Visible answer",
                                    "reasoning": [{"text":"Hidden Android parity thought"}],
                                    "tool_calls": [
                                      {"name":"shell","preview":"pwd","args":{"cmd":"pwd"},"result":{"exit_code":0},"is_error":false}
                                    ]
                                  }
                                ]
                              }
                            }
                            """.trimIndent(),
                        )
                        "/api/models" -> json("""{"models":[{"id":"gpt-5","label":"GPT-5","provider":"openai"}]}""")
                        "/api/profiles" -> json("""{"profiles":[]}""")
                        "/api/workspaces" -> json("""{"workspaces":[{"path":"/workspace/hermex","name":"Hermex"}]}""")
                        "/api/reasoning" -> json("""{"effort":"medium","supported_efforts":["medium"]}""")
                        "/api/approval/pending", "/api/clarify/pending" -> json("{}")
                        else -> MockResponse.Builder().code(404).body("""{"error":"unexpected"}""").build()
                    }
                }
            }
            server.start()
            this.server = server
        }
        val application = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(application)
        runBlocking {
            container.localSettingsRepository.setShowThinkingAndToolCards(false)
            container.localSettingsRepository.setThinkingCardsStartExpanded(true)
            container.localSettingsRepository.setToolCardsStartExpanded(true)
        }

        composeRule.setContent {
            HermexTheme {
                ChatRoute(
                    sessionId = "s1",
                    repository = container.chatRepository(mockServer.url("/")),
                    localSettingsRepository = container.localSettingsRepository,
                    onBack = {},
                    onOpenWorkspace = {},
                    onOpenGit = {},
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) { hasText("Visible answer") }
        composeRule.onNodeWithText("Visible answer").assertIsDisplayed()
        assertTrue(!hasText("Thinking"))
        assertTrue(!hasText("Hidden Android parity thought"))
        assertTrue(!hasText("shell"))
    }

    @Test
    fun chatRouteQueuesSlashMessageUntilCurrentStreamFinishes() {
        val releaseFirstStream = CountDownLatch(1)
        val chatStartBodies = java.util.Collections.synchronizedList(mutableListOf<String>())
        val mockServer = MockWebServer().also { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    return when (request.url.encodedPath) {
                        "/api/session" -> json(
                            """
                            {
                              "session": {
                                "session_id": "s1",
                                "title": "Queue Test",
                                "workspace": "/workspace/hermex",
                                "messages": []
                              }
                            }
                            """.trimIndent(),
                        )
                        "/api/models" -> json("""{"models":[{"id":"gpt-5","label":"GPT-5","provider":"openai"}]}""")
                        "/api/profiles" -> json("""{"profiles":[]}""")
                        "/api/workspaces" -> json("""{"workspaces":[{"path":"/workspace/hermex","name":"Hermex"}]}""")
                        "/api/reasoning" -> json("""{"effort":"medium","supported_efforts":["medium"]}""")
                        "/api/chat/start" -> {
                            val body = request.body?.utf8().orEmpty()
                            chatStartBodies += body
                            val streamId = if (chatStartBodies.size == 1) "stream-1" else "stream-2"
                            json("""{"stream_id":"$streamId","session_id":"s1"}""")
                        }
                        "/api/chat/stream" -> {
                            if (request.url.queryParameter("stream_id") == "stream-1") {
                                releaseFirstStream.await(30, TimeUnit.SECONDS)
                                eventStream(
                                    """
                                    event: done
                                    data: {"session_id":"s1"}

                                    """.trimIndent(),
                                )
                            } else {
                                eventStream(
                                    """
                                    event: token
                                    data: {"text":"Queued response"}

                                    event: done
                                    data: {"session_id":"s1"}

                                    """.trimIndent(),
                                )
                            }
                        }
                        "/api/approval/pending", "/api/clarify/pending" -> json("{}")
                        else -> MockResponse.Builder().code(404).body("""{"error":"unexpected"}""").build()
                    }
                }
            }
            server.start()
            this.server = server
        }
        val application = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(application)
        runBlocking {
            container.localSettingsRepository.setStreamingSendBehavior(StreamingSendBehavior.Queue)
        }

        composeRule.setContent {
            HermexTheme {
                ChatRoute(
                    sessionId = "s1",
                    repository = container.chatRepository(mockServer.url("/")),
                    localSettingsRepository = container.localSettingsRepository,
                    onBack = {},
                    onOpenWorkspace = {},
                    onOpenGit = {},
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("Message").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("Message").performTextInput("First turn")
        composeRule.onNodeWithContentDescription("Send").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { chatStartBodies.size == 1 }
        composeRule.onNodeWithContentDescription("Message").performTextInput("second turn")
        composeRule.waitUntil(timeoutMillis = 5_000) { hasText("Queue") }
        composeRule.onNodeWithText("Queue")
            .assertHasClickAction()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            hasText("Queued for next turn (#1).", substring = true)
        }

        releaseFirstStream.countDown()
        composeRule.waitUntil(timeoutMillis = 5_000) { chatStartBodies.size == 2 }
        composeRule.waitUntil(timeoutMillis = 5_000) { hasText("Queued response") }

        assertTrue(chatStartBodies[0].contains(""""message":"First turn""""))
        assertTrue(chatStartBodies[1].contains(""""message":"second turn""""))
    }

    @Test
    fun chatRouteSendsRuntimeSlashCommandsAndHandlesUnsupportedMobileCommandsLocally() {
        val chatStartBodies = java.util.Collections.synchronizedList(mutableListOf<String>())
        val streamRequested = AtomicBoolean(false)
        val mockServer = MockWebServer().also { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    return when (request.url.encodedPath) {
                        "/api/session" -> json(
                            """
                            {
                              "session": {
                                "session_id": "s1",
                                "title": "Slash Test",
                                "workspace": "/workspace/hermex",
                                "messages": []
                              }
                            }
                            """.trimIndent(),
                        )
                        "/api/models" -> json("""{"models":[{"id":"gpt-5","label":"GPT-5","provider":"openai"}]}""")
                        "/api/profiles" -> json("""{"profiles":[]}""")
                        "/api/workspaces" -> json("""{"workspaces":[{"path":"/workspace/hermex","name":"Hermex"}]}""")
                        "/api/reasoning" -> json("""{"effort":"medium","supported_efforts":["medium"]}""")
                        "/api/chat/start" -> {
                            chatStartBodies += request.body?.utf8().orEmpty()
                            json("""{"stream_id":"stream-1","session_id":"s1"}""")
                        }
                        "/api/chat/stream" -> {
                            streamRequested.set(true)
                            eventStream(
                                """
                                event: token
                                data: {"text":"Runtime command handled"}

                                event: done
                                data: {"session_id":"s1"}

                                """.trimIndent(),
                            )
                        }
                        "/api/approval/pending", "/api/clarify/pending" -> json("{}")
                        else -> MockResponse.Builder().code(404).body("""{"error":"unexpected"}""").build()
                    }
                }
            }
            server.start()
            this.server = server
        }
        val application = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(application)

        composeRule.setContent {
            HermexTheme {
                ChatRoute(
                    sessionId = "s1",
                    repository = container.chatRepository(mockServer.url("/")),
                    onBack = {},
                    onOpenWorkspace = {},
                    onOpenGit = {},
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("Message").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("Message").performTextInput("/commands list")
        composeRule.onNodeWithContentDescription("Send").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { chatStartBodies.size == 1 }
        composeRule.waitUntil(timeoutMillis = 5_000) { streamRequested.get() }
        composeRule.waitUntil(timeoutMillis = 10_000) { hasText("Runtime command handled", substring = true) }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("Send").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithContentDescription("Message").performTextInput("/theme dark")
        composeRule.onNodeWithContentDescription("Send").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            hasText("Theme switching is not available from mobile slash commands.")
        }

        assertTrue(chatStartBodies.single().contains(""""message":"/commands list""""))
    }

    @Test
    fun chatRouteShowsTranscriptErrorWhenInitialLoadFails() {
        val mockServer = MockWebServer().also { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    return when (request.url.encodedPath) {
                        "/api/session" -> MockResponse.Builder()
                            .code(500)
                            .setHeader("Content-Type", "application/json")
                            .body("""{"error":"load failed"}""")
                            .build()
                        "/api/models" -> json("""{"models":[]}""")
                        "/api/profiles" -> json("""{"profiles":[]}""")
                        "/api/workspaces" -> json("""{"workspaces":[]}""")
                        "/api/reasoning" -> json("""{"effort":"medium","supported_efforts":["medium"]}""")
                        "/api/approval/pending", "/api/clarify/pending" -> json("{}")
                        else -> MockResponse.Builder().code(404).body("""{"error":"unexpected"}""").build()
                    }
                }
            }
            server.start()
            this.server = server
        }
        val application = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(application)

        composeRule.setContent {
            HermexTheme {
                ChatRoute(
                    sessionId = "missing",
                    repository = container.chatRepository(mockServer.url("/")),
                    onBack = {},
                    onOpenWorkspace = {},
                    onOpenGit = {},
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) { hasText("Could Not Load Messages") }
        composeRule.onNodeWithText("Could Not Load Messages").assertIsDisplayed()
        composeRule.onNodeWithText("Try Again").assertIsDisplayed()
    }

    @Test
    fun panelsRouteGroupsAndSearchesSkillsLikeIos() {
        val mockServer = MockWebServer().also { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    return when (request.url.encodedPath) {
                        "/api/memory" -> json("{}")
                        "/api/crons" -> json("""{"crons":[]}""")
                        "/api/crons/status" -> json("""{"running_jobs":{}}""")
                        "/api/skills" -> json(
                            """
                            {
                              "skills": [
                                {"name":"Review Code","description":"Review a branch","category":"Core","tags":["review"],"disabled":false},
                                {"name":"Draft Plan","description":"Write implementation plans","category":"Writing","tags":["draft"]},
                                {"name":"Loose Note","description":"No category skill"}
                              ]
                            }
                            """.trimIndent(),
                        )
                        "/api/skills/content" -> {
                            if (request.url.queryParameter("file") == null) {
                                json(
                                    """
                                    {
                                      "name": "Review Code",
                                      "content": "Use this skill to review a branch.",
                                      "linked_files": ["README.md"]
                                    }
                                    """.trimIndent(),
                                )
                            } else {
                                json("""{"name":"Review Code","content":"Linked file content"}""")
                            }
                        }
                        "/api/insights" -> json("""{"period_days":30,"total_sessions":0,"total_messages":0,"total_tokens":0}""")
                        else -> MockResponse.Builder().code(404).body("""{"error":"unexpected"}""").build()
                    }
                }
            }
            server.start()
            this.server = server
        }
        val application = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(application)

        composeRule.setContent {
            HermexTheme {
                PanelsRoute(
                    panelsRepository = container.panelsRepository(mockServer.url("/")),
                    initialSection = "skills",
                    onBack = {},
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) { hasText("CORE") }
        composeRule.onNodeWithText("CORE").assertIsDisplayed()
        composeRule.onNodeWithText("WRITING").assertIsDisplayed()
        composeRule.onNodeWithText("UNCATEGORIZED").assertIsDisplayed()
        composeRule.onNodeWithText("Review Code").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Review Code enabled").assertIsOn()
        composeRule.onNodeWithText("Draft Plan").assertIsDisplayed()
        composeRule.onNodeWithText("review").assertIsDisplayed()

        composeRule.onNodeWithText("Review Code").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { hasText("Linked Files") }
        composeRule.onNodeWithText("Linked Files").assertIsDisplayed()
        composeRule.onNodeWithText("README.md").assertIsDisplayed()
        composeRule.onNodeWithText("Done").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Done").fetchSemanticsNodes().isEmpty()
        }

        composeRule.onNodeWithText("Search skills...").performTextInput("zzzz")
        composeRule.waitUntil(timeoutMillis = 5_000) { hasText("No Results") }
        composeRule.onNodeWithText("No skills match \"zzzz\".").assertIsDisplayed()
    }

    @Test
    fun panelsRouteShowsTaskMetadataLikeIos() {
        val mockServer = MockWebServer().also { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    return when (request.url.encodedPath) {
                        "/api/memory" -> json("{}")
                        "/api/crons" -> json(
                            """
                            {
                              "crons": [
                                {
                                  "job_id": "job-1",
                                  "name": "Morning Review",
                                  "prompt": "Review open pull requests.",
                                  "schedule_display": "Every day at 9 AM",
                                  "running": true,
                                  "next_run_at": "2026-07-06T09:00:00Z",
                                  "last_run_at": "2026-07-05T09:00:00Z",
                                  "deliver": "local",
                                  "model": "gpt-5",
                                  "profile": "review",
                                  "skills": ["code-review", "summary"]
                                }
                              ]
                            }
                            """.trimIndent(),
                        )
                        "/api/crons/status" -> json("""{"running_jobs":{}}""")
                        "/api/skills" -> json("""{"skills":[]}""")
                        "/api/insights" -> json("""{"period_days":30,"total_sessions":0,"total_messages":0,"total_tokens":0}""")
                        else -> MockResponse.Builder().code(404).body("""{"error":"unexpected"}""").build()
                    }
                }
            }
            server.start()
            this.server = server
        }
        val application = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(application)

        composeRule.setContent {
            HermexTheme {
                PanelsRoute(
                    panelsRepository = container.panelsRepository(mockServer.url("/")),
                    initialSection = "tasks",
                    onBack = {},
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) { hasText("Morning Review") }
        composeRule.onNodeWithText("Morning Review").assertIsDisplayed()
        composeRule.onNodeWithText("Running").assertIsDisplayed()
        composeRule.onNodeWithText("Review open pull requests.").assertIsDisplayed()
        composeRule.onNodeWithText("Schedule").assertIsDisplayed()
        composeRule.onNodeWithText("Every day at 9 AM").assertIsDisplayed()
        composeRule.onNodeWithText("Next").assertIsDisplayed()
        assertTrue(hasText("6 Jul 2026", substring = true))
        composeRule.onNodeWithText("Last").assertIsDisplayed()
        assertTrue(hasText("5 Jul 2026", substring = true))
        composeRule.onNodeWithText("Deliver").assertIsDisplayed()
        composeRule.onNodeWithText("Model").assertIsDisplayed()
        composeRule.onNodeWithText("gpt-5").assertIsDisplayed()
        composeRule.onNodeWithText("Profile").assertIsDisplayed()
        composeRule.onNodeWithText("review").assertIsDisplayed()
        composeRule.onNodeWithText("Skills").assertIsDisplayed()
        composeRule.onNodeWithText("code-review, summary").assertIsDisplayed()
    }

    @Test
    fun panelsRouteShowsInsightsAndMemoryPanelsLikeIos() {
        val requestedInsightDays = mutableListOf<Int>()
        val mockServer = MockWebServer().also { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    return when (request.url.encodedPath) {
                        "/api/memory" -> json(
                            """
                            {
                              "memory": "Remember the Android parity sweep.",
                              "user": "Uzair prefers identical UI.",
                              "soul": "Be precise and warm.",
                              "project_context": "Project context body.",
                              "project_context_name": "Hermex",
                              "project_context_workspace": "/workspace/hermex",
                              "project_context_path": "/workspace/hermex/AGENTS.md"
                            }
                            """.trimIndent(),
                        )
                        "/api/crons" -> json("""{"crons":[]}""")
                        "/api/skills" -> json("""{"skills":[]}""")
                        "/api/insights" -> {
                            val days = request.url.queryParameter("days")?.toIntOrNull() ?: 30
                            requestedInsightDays += days
                            json(
                                """
                                {
                                  "period_days": $days,
                                  "total_sessions": 12,
                                  "total_messages": 48,
                                  "total_input_tokens": 1000,
                                  "total_output_tokens": 2500,
                                  "total_tokens": 3500,
                                  "total_cost": 1.25,
                                  "total_cache_hit_percent": 87.5,
                                  "total_cache_read_tokens": 222,
                                  "models": [{"model":"gpt-5","sessions":4,"total_tokens":3000,"cost":1.2,"token_share":86,"cache_hit_percent":90}],
                                  "daily_tokens": [{"date":"2026-07-05","input_tokens":100,"output_tokens":200,"sessions":3,"cost":0.2}],
                                  "activity_by_day": [{"day":"Sunday","sessions":6}],
                                  "activity_by_hour": [{"hour":9,"sessions":5}]
                                }
                                """.trimIndent(),
                            )
                        }
                        else -> MockResponse.Builder().code(404).body("""{"error":"unexpected"}""").build()
                    }
                }
            }
            server.start()
            this.server = server
        }
        val application = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(application)
        var panelSection by mutableStateOf("insights")

        composeRule.setContent {
            HermexTheme {
                PanelsRoute(
                    panelsRepository = container.panelsRepository(mockServer.url("/")),
                    initialSection = panelSection,
                    onBack = {},
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            requestedInsightDays.contains(30) && hasText("Sessions")
        }
        assertTrue(hasText("Today"))
        assertTrue(hasText("Last 7 Days"))
        assertTrue(hasText("Last 30 Days"))
        assertTrue(hasText("All Time"))
        composeRule.onNodeWithText("Sessions").assertIsDisplayed()
        composeRule.onNodeWithText("Estimated Cost").assertIsDisplayed()
        composeRule.onNodeWithText("Models").assertIsDisplayed()
        composeRule.onNodeWithText("gpt-5").assertIsDisplayed()
        composeRule.onNodeWithText("Recent Daily Tokens").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Activity").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Source: server insights from the last 30 days.").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithText("Last 7 Days").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            requestedInsightDays.contains(7) && hasText("Source: server insights from the last 7 days.")
        }
        assertTrue(hasText("Source: server insights from the last 7 days."))

        composeRule.runOnUiThread {
            panelSection = "memory"
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { hasText("My Notes") }
        composeRule.onNodeWithText("My Notes").assertIsDisplayed()
        composeRule.onNodeWithText("User Profile").assertIsDisplayed()
        composeRule.onNodeWithText("Agent Soul").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Edit My Notes").assertIsDisplayed()
        composeRule.onNodeWithText("PROJECT CONTEXT").assertIsDisplayed()
        composeRule.onNodeWithText("Read-only").assertIsDisplayed()
    }

    @Test
    fun panelsRouteFallsBackToLocalInsightSessionsLikeIos() {
        val nowSeconds = System.currentTimeMillis() / 1000.0
        val mockServer = MockWebServer().also { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    return when (request.url.encodedPath) {
                        "/api/memory" -> json("""{"memory":""}""")
                        "/api/crons" -> json("""{"crons":[]}""")
                        "/api/skills" -> json("""{"skills":[]}""")
                        "/api/insights" -> MockResponse.Builder()
                            .code(500)
                            .body("""{"error":"insights unavailable"}""")
                            .build()
                        "/api/sessions" -> json(
                            """
                            {
                              "sessions": [
                                {
                                  "session_id": "s-heavy",
                                  "title": "Heavy Session",
                                  "message_count": 8,
                                  "input_tokens": 1000,
                                  "output_tokens": 2000,
                                  "estimated_cost": 1.5,
                                  "last_message_at": $nowSeconds
                                },
                                {
                                  "session_id": "s-small",
                                  "title": "Small Session",
                                  "message_count": 2,
                                  "input_tokens": 10,
                                  "output_tokens": 20,
                                  "estimated_cost": 0.05,
                                  "last_message_at": $nowSeconds
                                }
                              ]
                            }
                            """.trimIndent(),
                        )
                        else -> MockResponse.Builder().code(404).body("""{"error":"unexpected"}""").build()
                    }
                }
            }
            server.start()
            this.server = server
        }
        val application = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(application)

        composeRule.setContent {
            HermexTheme {
                PanelsRoute(
                    panelsRepository = container.panelsRepository(mockServer.url("/")),
                    initialSection = "insights",
                    onBack = {},
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) { hasText("Top Sessions") }
        composeRule.onNodeWithText("Sessions").assertIsDisplayed()
        composeRule.onNodeWithText("Messages").assertIsDisplayed()
        composeRule.onNodeWithText("Top Sessions").assertIsDisplayed()
        composeRule.onNodeWithText("Heavy Session").assertIsDisplayed()
        composeRule.onNodeWithText("Small Session").performScrollTo().assertIsDisplayed()
        composeRule.waitUntil(timeoutMillis = 5_000) { hasText("Source: local session metadata fallback", substring = true) }
    }

    @Test
    fun settingsRouteShowsGroupedParitySettings() {
        val settingsUpdateBody = AtomicReference("")
        val settingsUpdateLatch = CountDownLatch(1)
        val addedServerRequests = CopyOnWriteArrayList<String>()
        val addedServer = MockWebServer().also { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    addedServerRequests += "${request.method} ${request.url.encodedPath}"
                    return when (request.url.encodedPath) {
                        "/health" -> json("""{"status":"ok"}""")
                        "/api/auth/status" -> json("""{"auth_enabled":false,"password_auth_enabled":true}""")
                        "/api/settings" -> {
                            if (request.method == "POST") {
                                settingsUpdateBody.set(request.body?.utf8().orEmpty())
                                settingsUpdateLatch.countDown()
                                json("""{"webui_version":"1.2.3","bot_name":"Hermes","show_cli_sessions":false}""")
                            } else {
                                json("""{"webui_version":"1.2.3","bot_name":"Hermes","show_cli_sessions":true}""")
                            }
                        }
                        "/api/models" -> json(
                            """
                            {
                              "default_model": "gpt-5",
                              "models": [
                                {"id":"gpt-5","label":"GPT-5","provider":"openai"},
                                {"id":"o4-mini","label":"o4 mini","provider":"openai"}
                              ]
                            }
                            """.trimIndent(),
                        )
                        "/api/profiles" -> json(
                            """
                            {
                              "active": "default",
                              "profiles": [
                                {"name":"default","display_name":"Default","model":"gpt-5"},
                                {"name":"review","display_name":"Review","model":"gpt-5"}
                              ]
                            }
                            """.trimIndent(),
                        )
                        else -> MockResponse.Builder().code(404).body("""{"error":"unexpected"}""").build()
                    }
                }
            }
            server.start()
        }
        val mockServer = MockWebServer().also { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    return when (request.url.encodedPath) {
                        "/health" -> json("""{"status":"ok"}""")
                        "/api/auth/status" -> json("""{"auth_enabled":false,"password_auth_enabled":true}""")
                        "/api/settings" -> {
                            if (request.method == "POST") {
                                settingsUpdateBody.set(request.body?.utf8().orEmpty())
                                json("""{"webui_version":"1.2.3","bot_name":"Hermes","show_cli_sessions":false}""")
                            } else {
                                json("""{"webui_version":"1.2.3","bot_name":"Hermes","show_cli_sessions":true}""")
                            }
                        }
                        "/api/models" -> json(
                            """
                            {
                              "default_model": "gpt-5",
                              "models": [
                                {"id":"gpt-5","label":"GPT-5","provider":"openai"},
                                {"id":"o4-mini","label":"o4 mini","provider":"openai"}
                              ]
                            }
                            """.trimIndent(),
                        )
                        "/api/profiles" -> json(
                            """
                            {
                              "active": "default",
                              "profiles": [
                                {"name":"default","display_name":"Default","model":"gpt-5"},
                                {"name":"review","display_name":"Review","model":"gpt-5"}
                              ]
                            }
                            """.trimIndent(),
                        )
                        else -> MockResponse.Builder().code(404).body("""{"error":"unexpected"}""").build()
                    }
                }
            }
            server.start()
            this.server = server
        }
        val application = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(application)
        val authRepository = authRepository()
        runBlocking {
            authRepository.configure(mockServer.url("/").toString(), "")
            container.localSettingsRepository.setStreamingSendBehavior(StreamingSendBehavior.Steer)
            container.localSettingsRepository.setShowThinkingAndToolCards(true)
            container.localSettingsRepository.setThinkingCardsStartExpanded(false)
            container.localSettingsRepository.setToolCardsStartExpanded(false)
            container.localSettingsRepository.setShowsAssistantTurnTimestamps(false)
            container.localSettingsRepository.setHidesAttachmentPaths(true)
            container.localSettingsRepository.setRtlChatLayoutEnabled(false)
            container.localSettingsRepository.setWrapsCodeBlockLines(false)
            container.localSettingsRepository.setStreamedTextAnimationEnabled(true)
        }

        composeRule.setContent {
            val authState by authRepository.state.collectAsState()
            val activeServer = (authState as? AuthState.LoggedIn)?.server
            HermexTheme {
                SettingsRoute(
                    authRepository = authRepository,
                    localSettingsRepository = container.localSettingsRepository,
                    cacheMaintenanceRepository = container.cacheMaintenanceRepository,
                    panelsRepository = activeServer?.let(container::panelsRepository),
                    authState = authState,
                    onBack = {},
                    onSignedOut = {},
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) { hasText("IDENTITY") }
        composeRule.onNodeWithContentDescription("Back").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithText("IDENTITY").assertIsDisplayed()
        composeRule.onNodeWithText("Stored on this device only.").assertIsDisplayed()
        composeRule.onNodeWithText("ARCHIVED SESSIONS").assertIsDisplayed()
        composeRule.onNodeWithTag("identity_display_name").performTextClearance()
        composeRule.onNodeWithTag("identity_display_name").performTextInput("Mobile ")
        composeRule.onNodeWithTag("identity_display_name").performTextInput("User")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            authRepository.servers.value.servers.firstOrNull()?.displayName == "Mobile User"
        }
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasSemanticsText("SERVERS"))
        composeRule.onNodeWithText("SERVERS").assertIsDisplayed()
        composeRule.onNodeWithText("Headers (0)").assertIsDisplayed()
        composeRule.onNodeWithText("Clear Cache").assertIsDisplayed()
        composeRule.onNodeWithText("Forget").assertIsDisplayed()
        composeRule.onNodeWithText("Headers (0)").performClick()
        composeRule.onNodeWithText("Name: Value").performTextInput("CF-Access-Client-Id: id")
        composeRule.onNodeWithText("Save").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { hasText("Headers (1)") }
        composeRule.onNodeWithText("Headers (1)").assertIsDisplayed()
        composeRule.onNodeWithText("Edit Identity").performClick()
        composeRule.onNodeWithTag("server_identity_display_name").performTextClearance()
        composeRule.onNodeWithTag("server_identity_display_name").performTextInput("Mobile Lab")
        composeRule.onNodeWithTag("server_identity_initials").performTextClearance()
        composeRule.onNodeWithTag("server_identity_initials").performTextInput("ML")
        composeRule.onNodeWithText("Save").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            authRepository.servers.value.servers.firstOrNull()?.displayName == "Mobile Lab"
        }
        assertEquals("ML", authRepository.servers.value.servers.first().initials)
        composeRule.onNodeWithText("Add Server").performClick()
        composeRule.onNodeWithText("Server URL").performTextInput(addedServer.url("/").toString())
        composeRule.onNodeWithText("Custom Headers").performTextInput("CF-Access-Client-Id: second")
        composeRule.onNodeWithTag("server_identity_display_name").performTextInput("Second Lab")
        composeRule.onNodeWithTag("server_identity_initials").performTextInput("SL")
        composeRule.onNodeWithText("Add").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { authRepository.servers.value.servers.size == 2 }
        assertEquals(addedServer.url("/").toString(), authRepository.servers.value.activeServerId)
        assertEquals("Second Lab", authRepository.servers.value.servers.first { it.id == addedServer.url("/").toString() }.displayName)
        assertEquals("SL", authRepository.servers.value.servers.first { it.id == addedServer.url("/").toString() }.initials)
        assertEquals("second", addedServer.takeRequest().headers["CF-Access-Client-Id"])
        assertEquals("second", addedServer.takeRequest().headers["CF-Access-Client-Id"])
        composeRule.onNodeWithText("Clean Expired Cache").assertExists()
        composeRule.onNodeWithText("Clear Active Cache").assertExists()
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasSemanticsText("APPEARANCE"))
        composeRule.onNodeWithText("APPEARANCE").assertIsDisplayed()
        composeRule.onNodeWithText("Header Logo Color").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Yellow header logo color").assertHasClickAction()
        composeRule.onNodeWithText("Custom").performClick()
        composeRule.onNodeWithText("Custom Header Color").assertIsDisplayed()
        composeRule.onNodeWithTag("header_custom_color_input").performTextClearance()
        composeRule.onNodeWithTag("header_custom_color_input").performTextInput("#123456")
        composeRule.onNodeWithText("Apply").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            val snapshot = authRepository.servers.value
            snapshot.servers.firstOrNull { it.id == snapshot.activeServerId }?.headerLogoColorHex == "#123456"
        }
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasSemanticsText("INTERACTION"))
        composeRule.onNodeWithText("INTERACTION").assertIsDisplayed()
        composeRule.onNodeWithText("Haptic Feedback").assertIsDisplayed()
        composeRule.onNodeWithText("Send While Responding").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasSemanticsText("CHAT"))
        composeRule.onNodeWithText("CHAT").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithText("Thinking and Tool Cards").assertIsDisplayed()
        composeRule.onNodeWithText("Expand Thinking by Default").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Expand Tools by Default").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Streamed Text Animation").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Response Timestamps").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Wrap Code Block Lines").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Hide Attachment Paths").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Right-to-Left Chat Layout").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasSemanticsText("ACTIVE SERVER"))
        composeRule.onNodeWithText("ACTIVE SERVER").assertIsDisplayed()
        composeRule.onNodeWithText("Default Model").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("GPT-5").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Default Profile").performScrollTo().assertIsDisplayed()
        assertTrue(hasText("Default"))

        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasSemanticsText("SESSIONS"))
        composeRule.onNodeWithText("SESSIONS").assertIsDisplayed()
        composeRule.onNodeWithText("CLI session visibility is synced with this server, so the WebUI follows it too.")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("cli_sessions_switch")
            .performScrollTo()
            .assertIsOn()
            .performClick()
            .assertIsOff()
        assertTrue(
            "Expected settings POST. Requests: $addedServerRequests",
            settingsUpdateLatch.await(5, TimeUnit.SECONDS),
        )
        assertEquals("""{"show_cli_sessions":false}""", settingsUpdateBody.get())

        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasSemanticsText("INTERACTION"))
        composeRule.onNodeWithText("INTERACTION").assertIsDisplayed()
        composeRule.onNodeWithText("Haptic Feedback").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Send While Responding").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Steer active response").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Send While Responding").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { hasText("Stop and send") }
        composeRule.onNodeWithText("Send While Responding").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { hasText("Send after response") }

        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasSemanticsText("OFFLINE DATA"))
        composeRule.onNodeWithText("OFFLINE DATA").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasSemanticsText("ACCOUNT"))
        composeRule.onNodeWithText("ACCOUNT").assertIsDisplayed()
        composeRule.onNodeWithText("Sign Out of This Server").performScrollTo().assertIsDisplayed()
        addedServer.close()
    }

    @Test
    fun workspaceRouteShowsLocationSearchAndRootNavigationLikeIos() {
        val requestedPaths = mutableListOf<String?>()
        val mockServer = MockWebServer().also { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    return when (request.url.encodedPath) {
                        "/api/workspaces" -> json(
                            """
                            {
                              "workspaces": [
                                {"path":"/workspace/hermex","name":"Hermex"},
                                {"path":"/workspace/mobile","name":"Mobile"}
                              ]
                            }
                            """.trimIndent(),
                        )
                        "/api/list" -> {
                            requestedPaths += request.url.queryParameter("path")
                            val path = request.url.queryParameter("path")
                            json(
                                """
                                {
                                  "path": ${if (path == null) "\".\"" else "\"$path\""},
                                  "entries": [
                                    {"name":"src","path":"${path ?: "."}/src","type":"directory"},
                                    {"name":"README.md","path":"${path ?: "."}/README.md","type":"file","size":1200},
                                    {"name":"report.pdf","path":"${path ?: "."}/report.pdf","type":"file","size":2048}
                                  ]
                                }
                                """.trimIndent(),
                            )
                        }
                        "/api/file" -> json(
                            """
                            {
                              "path": "${request.url.queryParameter("path") ?: "README.md"}",
                              "content": "# Hermex\nAndroid workspace preview"
                            }
                            """.trimIndent(),
                        )
                        "/api/file/raw" -> MockResponse.Builder()
                            .code(200)
                            .setHeader("Content-Type", "application/pdf")
                            .body("%PDF-1.7 mocked binary")
                            .build()
                        else -> MockResponse.Builder().code(404).body("""{"error":"unexpected"}""").build()
                    }
                }
            }
            server.start()
            this.server = server
        }
        val application = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(application)

        composeRule.setContent {
            HermexTheme {
                WorkspaceRoute(
                    sessionId = "s1",
                    repository = container.workspaceRepository(mockServer.url("/")),
                    onBack = {},
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) { hasText("README.md") }
        composeRule.onNodeWithText("Files").assertIsDisplayed()
        composeRule.onNodeWithText("Location").assertIsDisplayed()
        composeRule.onNodeWithText("README.md").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { hasText("Share") }
        composeRule.onNodeWithText("Share").assertIsDisplayed()
        composeRule.onNodeWithText("Close").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { hasText("report.pdf") }
        composeRule.onNodeWithText("report.pdf").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { hasText("No Preview") }
        composeRule.onNodeWithText("Preview is not available for this file type.").assertIsDisplayed()
        assertTrue(hasText("application/pdf", substring = true))
        composeRule.onNodeWithText("Share").assertIsDisplayed()
        composeRule.onNodeWithText("Close").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { hasText("Search files") }
        composeRule.onNodeWithText("Search files").performTextInput("read")
        composeRule.onNodeWithText("README.md").assertIsDisplayed()
        composeRule.onNodeWithText("Search files").performTextInput("zzz")
        composeRule.waitUntil(timeoutMillis = 5_000) { hasText("No Matches") }
        composeRule.onNodeWithText("No Matches").assertIsDisplayed()

        composeRule.onNodeWithText("Hermex").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { requestedPaths.contains("/workspace/hermex") }
        assertTrue(requestedPaths.contains("/workspace/hermex"))
    }

    private fun startServer(vararg responses: MockResponse): MockWebServer =
        MockWebServer().also { mockServer ->
            responses.forEach(mockServer::enqueue)
            mockServer.start()
            server = mockServer
        }

    private fun json(body: String): MockResponse =
        MockResponse.Builder()
            .code(200)
            .setHeader("Content-Type", "application/json")
            .body(body)
            .build()

    private fun eventStream(body: String): MockResponse =
        MockResponse.Builder()
            .code(200)
            .setHeader("Content-Type", "text/event-stream")
            .body(body.trimEnd() + "\n\n")
            .build()

    private fun authRepository(): AuthRepository {
        val secretStore = InMemorySecretStore()
        val cookieJar = PersistentCookieJar(secretStore)
        val okHttpClient = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .build()
        return AuthRepository(
            registry = ServerRegistry(secretStore),
            clientFactory = { url -> HermesApiClient(url, okHttpClient) },
            probeClientFactory = { url, headers -> HermesApiClient(url, okHttpClient, customHeaders = { headers }) },
            cookieJar = cookieJar,
        )
    }

    private fun hasText(text: String, substring: Boolean = false): Boolean =
        composeRule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
}

private class InMemorySecretStore : SecretStore {
    private val values = mutableMapOf<String, String>()

    override fun getString(key: String): String? = values[key]

    override fun putString(key: String, value: String) {
        values[key] = value
    }

    override fun remove(key: String) {
        values.remove(key)
    }

    override fun clearPrefix(prefix: String) {
        values.keys.filter { it.startsWith(prefix) }.forEach(values::remove)
    }
}
