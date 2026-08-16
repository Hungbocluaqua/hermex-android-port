package com.uzairansar.hermex.ui.chat

import com.uzairansar.hermex.MainDispatcherRule
import com.uzairansar.hermex.core.model.ChatMessage
import com.uzairansar.hermex.core.model.SessionSummary
import com.uzairansar.hermex.core.network.HermesApiClient
import com.uzairansar.hermex.core.network.SseStreamClient
import com.uzairansar.hermex.data.db.CachedMessageEntity
import com.uzairansar.hermex.data.db.CachedSessionEntity
import com.uzairansar.hermex.data.db.ServerCacheOwnership
import com.uzairansar.hermex.data.repository.ChatRepository
import com.uzairansar.hermex.data.repository.RecordingCacheDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelCacheFirstTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun cachedTranscriptRendersWhileTheBoundedNetworkPageLoads() = runTest {
        val sessionRequest = AtomicReference<RecordedRequest?>()
        val server = MockWebServer()
        try {
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when (request.url.encodedPath) {
                    "/api/session" -> {
                        sessionRequest.set(request)
                        json(
                            """{"session":{"session_id":"session-1","title":"Fresh","messages":[{"role":"assistant","content":"fresh transcript"}]}}""",
                        ).newBuilder().bodyDelay(1, TimeUnit.SECONDS).build()
                    }
                    "/api/models" -> json("""{"models":[]}""")
                    "/api/profiles" -> json("""{"profiles":[]}""")
                    "/api/workspaces" -> json("""{"workspaces":[]}""")
                    "/api/reasoning" -> json("""{"supported_efforts":[]}""")
                    "/api/commands" -> json("""{"commands":[]}""")
                    "/api/skills" -> json("""{"skills":[]}""")
                    "/api/session/yolo" -> json("""{"yolo_enabled":false}""")
                    else -> MockResponse.Builder().code(404).body("""{"error":"unexpected"}""").build()
                }
            }
            server.start()
            val serverUrl = server.url("/").toString()
            val dao = RecordingCacheDao().apply {
                cachedSessionResult = listOf(
                    requireNotNull(
                        CachedSessionEntity.from(
                            serverUrl,
                            SessionSummary(sessionId = "session-1", title = "Cached"),
                        ),
                    ),
                )
                cachedMessageResult = listOf(
                    CachedMessageEntity.from(
                        serverUrl,
                        "session-1",
                        ChatMessage(role = "assistant", content = "cached transcript"),
                        index = 0,
                    ),
                )
            }
            val client = HermesApiClient(server.url("/"), OkHttpClient())
            val repository = ChatRepository(
                client = client,
                cacheDao = dao,
                cacheOwnership = ServerCacheOwnership(),
                sse = SseStreamClient(server.url("/"), OkHttpClient()) { emptyList() },
            )
            val viewModel = ChatViewModel("session-1", repository)

            val cachedState = withTimeout(1_000) {
                viewModel.state.first { state -> state.messages.singleOrNull()?.displayText == "cached transcript" }
            }
            assertEquals("Cached", cachedState.sessionTitle)
            assertFalse(cachedState.isViewingCachedData)

            val freshState = withContext(Dispatchers.Default) {
                withTimeout(5_000) {
                    viewModel.state.first { state -> state.messages.singleOrNull()?.displayText == "fresh transcript" }
                }
            }
            assertEquals("Fresh", freshState.sessionTitle)
            assertFalse(freshState.isLoading)
            assertEquals("50", sessionRequest.get()?.url?.queryParameter("msg_limit"))
            assertNull(sessionRequest.get()?.url?.queryParameter("expand_renderable"))
        } finally {
            server.close()
        }
    }

    private fun json(body: String): MockResponse =
        MockResponse.Builder()
            .code(200)
            .setHeader("Content-Type", "application/json")
            .body(body)
            .build()
}
