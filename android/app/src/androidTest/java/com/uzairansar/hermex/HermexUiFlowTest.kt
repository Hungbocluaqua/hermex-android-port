package com.uzairansar.hermex

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.uzairansar.hermex.core.network.HermesApiClient
import com.uzairansar.hermex.core.network.PersistentCookieJar
import com.uzairansar.hermex.data.repository.AuthRepository
import com.uzairansar.hermex.data.repository.AuthState
import com.uzairansar.hermex.data.secure.SecretStore
import com.uzairansar.hermex.data.secure.ServerAccount
import com.uzairansar.hermex.data.secure.ServerRegistry
import com.uzairansar.hermex.ui.chat.ChatRoute
import com.uzairansar.hermex.ui.onboarding.OnboardingRoute
import com.uzairansar.hermex.ui.sessions.SessionListRoute
import com.uzairansar.hermex.ui.theme.HermexTheme
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class HermexUiFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var server: MockWebServer? = null

    @After
    fun tearDown() {
        server?.close()
        server = null
    }

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

        composeRule.onNodeWithText("Server URL").performTextInput(mockServer.url("/").toString())
        composeRule.onNodeWithText("Test connection").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            hasText("Connection OK. No password required.")
        }

        composeRule.onNodeWithText("Connection OK. No password required.").assertIsDisplayed()
        assertEquals("/health", mockServer.takeRequest().url.encodedPath)
        assertEquals("/api/auth/status", mockServer.takeRequest().url.encodedPath)
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

        composeRule.onNodeWithText("Server URL").performTextInput(mockServer.url("/").toString())
        composeRule.onNodeWithText("Connect").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { hasText("Connected") }

        composeRule.onNodeWithText("Connected").assertIsDisplayed()
        assertEquals("/health", mockServer.takeRequest().url.encodedPath)
        assertEquals("/api/auth/status", mockServer.takeRequest().url.encodedPath)
    }

    @Test
    fun sessionListRendersServerSessionsAndProjects() {
        val mockServer = startServer(
            json("""{"projects":[{"project_id":"proj-1","name":"Mobile"}]}"""),
            json(
                """
                {
                  "sessions": [
                    {
                      "session_id": "s1",
                      "title": "Android Port",
                      "workspace": "/workspace/hermex",
                      "model": "gpt-5",
                      "message_count": 3,
                      "project_id": "proj-1"
                    }
                  ],
                  "archived_count": 2
                }
                """.trimIndent(),
            ),
        )
        val application = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(application)
        val account = ServerAccount(
            id = mockServer.url("/").toString(),
            urlString = mockServer.url("/").toString(),
            displayName = "Mock Hermex",
            initials = "MH",
        )

        composeRule.setContent {
            HermexTheme {
                SessionListRoute(
                    authState = AuthState.LoggedIn(mockServer.url("/"), account),
                    container = container,
                    onOpenChat = {},
                    onOpenSharedDraft = {},
                    onOpenPanels = {},
                    onOpenSettings = {},
                    onNeedsOnboarding = {},
                )
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { hasText("Android Port") }

        composeRule.onNodeWithText("Sessions").assertIsDisplayed()
        composeRule.onNodeWithText("Mock Hermex").assertIsDisplayed()
        composeRule.onNodeWithText("Android Port").assertIsDisplayed()
        composeRule.onNodeWithText("Mobile").assertIsDisplayed()
        composeRule.onNodeWithText("3 msgs").assertIsDisplayed()
        assertEquals("/api/projects", mockServer.takeRequest().url.encodedPath)
        assertEquals("/api/sessions", mockServer.takeRequest().url.encodedPath)
    }

    @Test
    fun chatRouteSendsMessageAndRendersAssistantTurn() {
        val chatStarted = AtomicBoolean(false)
        var chatStartBody = ""
        val mockServer = MockWebServer().also { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    return when (request.url.encodedPath) {
                        "/api/session" -> json(
                            if (chatStarted.get()) {
                                """
                                {
                                  "session": {
                                    "session_id": "s1",
                                    "messages": [
                                      {"role": "user", "content": "Hello Android"},
                                      {"role": "assistant", "content": "Mock response"}
                                    ]
                                  }
                                }
                                """.trimIndent()
                            } else {
                                """{"session":{"session_id":"s1","messages":[]}}"""
                            },
                        )
                        "/api/models" -> json("""{"models":[{"id":"gpt-5","label":"GPT-5"}]}""")
                        "/api/profiles" -> json("""{"profiles":[{"name":"default","display_name":"Default"}]}""")
                        "/api/reasoning" -> json("""{"effort":"medium","supported_efforts":["low","medium","high"]}""")
                        "/api/profile/switch" -> json("""{"ok":true}""")
                        "/api/chat/start" -> {
                            chatStarted.set(true)
                            chatStartBody = request.body?.utf8().orEmpty()
                            json("""{"stream_id":"stream-1","session_id":"s1"}""")
                        }
                        "/api/chat/stream" -> eventStream(
                            """
                            event: token
                            data: {"text":"Mock response"}

                            event: done
                            data: {"session_id":"s1"}

                            """.trimIndent(),
                        )
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
        composeRule.waitUntil(timeoutMillis = 5_000) { hasText("Message") }
        composeRule.onNodeWithText("Message").performTextInput("Hello Android")
        composeRule.onNodeWithText("Send").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { hasText("assistant") }

        composeRule.onNodeWithText("user").assertIsDisplayed()
        composeRule.onNodeWithText("assistant").assertIsDisplayed()
        assertTrue(chatStartBody.contains(""""message":"Hello Android""""))
        assertTrue(chatStartBody.contains(""""session_id":"s1""""))
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
            .body(body)
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
            cookieJar = cookieJar,
        )
    }

    private fun hasText(text: String): Boolean =
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
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
