package com.uzairansar.hermex.data.repository

import com.uzairansar.hermex.core.model.SessionDetail
import com.uzairansar.hermex.core.model.ProfileSummary
import com.uzairansar.hermex.core.model.SessionSummary
import com.uzairansar.hermex.core.network.HermesApiClient
import com.uzairansar.hermex.core.network.SseStreamClient
import com.uzairansar.hermex.data.db.CacheDao
import com.uzairansar.hermex.data.db.CachedMessageEntity
import com.uzairansar.hermex.data.db.CachedSessionEntity
import com.uzairansar.hermex.data.db.ServerCacheOwnership
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class RepositoryCacheSafetyTest {
    @Test
    fun completedEmptySessionReplacesCachedMessagesAndNewOperationUsesCurrentGeneration() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            val dao = RecordingCacheDao()
            val ownership = ServerCacheOwnership()
            val client = HermesApiClient(server.url("/"), OkHttpClient())
            val repository = ChatRepository(
                client = client,
                cacheDao = dao,
                cacheOwnership = ownership,
                sse = SseStreamClient(server.url("/"), OkHttpClient()) { emptyList() },
            )

            repository.snapshotFromCompletedSession("session-1", SessionDetail(sessionId = "session-1", messages = emptyList()))
            ownership.invalidateAndClear(server.url("/").toString()) {}
            repository.snapshotFromCompletedSession("session-1", SessionDetail(sessionId = "session-1", messages = emptyList()))

            assertEquals(2, dao.replacedMessageBatches.size)
            assertTrue(dao.replacedMessageBatches.all { it.isEmpty() })
        } finally {
            server.close()
        }
    }

    @Test
    fun profileInvalidationBlocksDelayedSessionListResponseFromRepopulatingCache() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            val dao = RecordingCacheDao()
            val ownership = ServerCacheOwnership()
            val repository = SessionRepository(
                client = HermesApiClient(server.url("/"), OkHttpClient()),
                cacheDao = dao,
                cacheOwnership = ownership,
            )
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .addHeader("Content-Type", "application/json")
                    .body("""{"sessions":[{"session_id":"old-profile"}]}""")
                    .bodyDelay(1, TimeUnit.SECONDS)
                    .build(),
            )

            val load = async(Dispatchers.IO) { repository.loadSessions() }
            assertNotNull(withTimeout(5_000) { server.takeRequest(5, TimeUnit.SECONDS) })
            ownership.invalidateAndClear(server.url("/").toString()) {}
            load.await()

            assertEquals(0, dao.replacedSessionBatches.size)
        } finally {
            server.close()
        }
    }

    @Test
    fun profileSwitchCannotAuthorizeAnOlderChatResponseWithTheNewGeneration() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            val dao = RecordingCacheDao()
            val ownership = ServerCacheOwnership()
            val client = HermesApiClient(
                server.url("/"),
                OkHttpClient(),
                onProfileChanged = { url, _ -> ownership.invalidateAndClear(url.toString()) {} },
            )
            val repository = ChatRepository(
                client = client,
                cacheDao = dao,
                cacheOwnership = ownership,
                sse = SseStreamClient(server.url("/"), OkHttpClient()) { emptyList() },
            )
            server.enqueue(
                json("""{"session":{"session_id":"old","messages":[{"role":"assistant","content":"old"}]}}""")
                    .newBuilder()
                    .bodyDelay(1, TimeUnit.SECONDS)
                    .build(),
            )
            server.enqueue(json("""{"active":"new"}"""))

            val load = async(Dispatchers.IO) { repository.loadSessionSnapshot("old") }
            assertNotNull(withTimeout(5_000) { server.takeRequest(5, TimeUnit.SECONDS) })
            repository.switchProfile(ProfileSummary(name = "new"))
            load.await()

            assertEquals(0, dao.replacedMessageBatches.size)
            repository.snapshotFromCompletedSession("new", SessionDetail(sessionId = "new", messages = emptyList()))
            assertEquals(1, dao.replacedMessageBatches.size)
        } finally {
            server.close()
        }
    }

    @Test
    fun completedStreamUsesGenerationCapturedWhenTheStreamStarted() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            val dao = RecordingCacheDao()
            val ownership = ServerCacheOwnership()
            val repository = ChatRepository(
                client = HermesApiClient(server.url("/"), OkHttpClient()),
                cacheDao = dao,
                cacheOwnership = ownership,
                sse = SseStreamClient(server.url("/"), OkHttpClient()) { emptyList() },
            )
            server.enqueue(json("""{"stream_id":"old-stream"}"""))

            val streamId = repository.send(sessionId = "session-1", message = "hello")
            ownership.invalidateAndClear(server.url("/").toString()) {}
            repository.snapshotFromCompletedSession(
                sessionId = "session-1",
                session = SessionDetail(sessionId = "session-1", messages = emptyList()),
                streamId = streamId,
            )

            assertEquals(0, dao.replacedMessageBatches.size)
        } finally {
            server.close()
        }
    }

    @Test
    fun failedOldSessionLoadCannotReadNewProfileCache() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            val dao = RecordingCacheDao()
            val ownership = ServerCacheOwnership()
            val repository = SessionRepository(
                client = HermesApiClient(server.url("/"), OkHttpClient()),
                cacheDao = dao,
                cacheOwnership = ownership,
            )
            server.enqueue(
                MockResponse.Builder()
                    .code(503)
                    .body("unavailable")
                    .bodyDelay(1, TimeUnit.SECONDS)
                    .build(),
            )

            val load = async(Dispatchers.IO) { repository.loadSessions() }
            assertNotNull(withTimeout(5_000) { server.takeRequest(5, TimeUnit.SECONDS) })
            ownership.invalidateAndClear(server.url("/").toString()) {}
            dao.cachedSessionResult = listOf(
                requireNotNull(CachedSessionEntity.from(server.url("/").toString(), SessionSummary(sessionId = "new-profile"))),
            )

            assertTrue(load.await() is ResultState.Error)
        } finally {
            server.close()
        }
    }

    @Test
    fun completeArchivedSessionSyncIsMarkedAuthoritative() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            val dao = RecordingCacheDao()
            val repository = SessionRepository(
                client = HermesApiClient(server.url("/"), OkHttpClient()),
                cacheDao = dao,
                cacheOwnership = ServerCacheOwnership(),
            )
            server.enqueue(
                json(
                    """{"sessions":[{"session_id":"active"},{"session_id":"archived","archived":true}],"archived_count":1}""",
                ),
            )

            val result = repository.loadSessions(includeArchived = false)

            assertEquals(listOf(true), dao.authoritativeSessionSyncs)
            assertEquals(listOf("active", "archived"), dao.replacedSessionBatches.single().map { it.sessionId })
            assertEquals(listOf("active"), (result as ResultState.Data).value.sessions.map { it.sessionId })
        } finally {
            server.close()
        }
    }

    private fun json(body: String): MockResponse = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()
}

private class RecordingCacheDao : CacheDao {
    val replacedSessionBatches = mutableListOf<List<CachedSessionEntity>>()
    val replacedMessageBatches = mutableListOf<List<CachedMessageEntity>>()
    val authoritativeSessionSyncs = mutableListOf<Boolean>()
    var cachedSessionResult: List<CachedSessionEntity> = emptyList()

    override suspend fun cachedSessions(serverUrl: String, now: Long): List<CachedSessionEntity> = cachedSessionResult
    override suspend fun cachedMessages(serverUrl: String, sessionId: String, now: Long): List<CachedMessageEntity> = emptyList()
    override suspend fun upsertSessions(sessions: List<CachedSessionEntity>) { replacedSessionBatches += sessions }
    override suspend fun upsertMessages(messages: List<CachedMessageEntity>) { replacedMessageBatches += messages }
    override suspend fun sessionKeys(serverUrl: String): List<String> = emptyList()
    override suspend fun messageSessionIds(serverUrl: String): List<String> = emptyList()
    override suspend fun messageKeys(serverUrl: String, sessionId: String): List<String> = emptyList()
    override suspend fun deleteSessionsByKeys(keys: List<String>) = Unit
    override suspend fun deleteMessagesByKeys(keys: List<String>) = Unit
    override suspend fun deleteMessagesBySessionIds(serverUrl: String, sessionIds: List<String>) = Unit
    override suspend fun clearSessions(serverUrl: String) = Unit
    override suspend fun clearMessages(serverUrl: String) = Unit
    override suspend fun deleteExpiredSessions(now: Long) = Unit
    override suspend fun deleteExpiredMessages(now: Long) = Unit
    override suspend fun oldMessageKeysBeyond(keep: Int): List<String> = emptyList()
    override suspend fun updateSessionTitle(serverUrl: String, sessionId: String, title: String) = Unit
    override suspend fun updateSessionPinned(serverUrl: String, sessionId: String, pinned: Boolean) = Unit
    override suspend fun updateSessionArchived(serverUrl: String, sessionId: String, archived: Boolean) = Unit
    override suspend fun updateSessionProject(serverUrl: String, sessionId: String, projectId: String?) = Unit
    override suspend fun deleteCachedSession(serverUrl: String, sessionId: String) = Unit
    override suspend fun deleteCachedSessionMessages(serverUrl: String, sessionId: String) = Unit

    override suspend fun replaceSessions(serverUrl: String, sessions: List<CachedSessionEntity>, now: Long, authoritative: Boolean) {
        replacedSessionBatches += sessions
        authoritativeSessionSyncs += authoritative
    }

    override suspend fun replaceMessages(serverUrl: String, sessionId: String, messages: List<CachedMessageEntity>, now: Long) {
        replacedMessageBatches += messages
    }
}
