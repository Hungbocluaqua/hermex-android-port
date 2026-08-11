package com.uzairansar.hermex.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.uzairansar.hermex.core.model.ChatMessage
import com.uzairansar.hermex.core.model.SessionSummary
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CacheDaoMaintenanceInstrumentedTest {
    private lateinit var database: HermexDatabase
    private lateinit var dao: CacheDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, HermexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.cacheDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun maintenanceEnforcesSevenDayExpiryAndPerServerMessageCap() = runBlocking {
        val now = 1_000_000_000L
        val sevenDaysMillis = 7L * 24L * 60L * 60L * 1_000L
        val serverA = "https://a.example/"
        val serverB = "https://b.example/"

        val freshSession = requireNotNull(
            CachedSessionEntity.from(serverA, SessionSummary(sessionId = "fresh"), now),
        )
        val expiredSession = requireNotNull(
            CachedSessionEntity.from(serverA, SessionSummary(sessionId = "expired"), now),
        ).copy(expiresAtEpochMillis = now)
        assertEquals(now + sevenDaysMillis, freshSession.expiresAtEpochMillis)
        dao.upsertSessions(listOf(freshSession, expiredSession))

        val freshMessage = CachedMessageEntity.from(
            serverUrl = serverB,
            sessionId = "fresh",
            message = ChatMessage(role = "assistant", content = "fresh"),
            index = 0,
            now = now,
        )
        val expiredMessage = freshMessage.copy(
            cacheKey = CachedMessageEntity.cacheKey(serverB, "expired", 0),
            sessionId = "expired",
            expiresAtEpochMillis = now,
        )
        assertEquals(now + sevenDaysMillis, freshMessage.expiresAtEpochMillis)

        val cappedServerMessages = (0 until 5_002).map { index ->
            CachedMessageEntity(
                cacheKey = CachedMessageEntity.cacheKey(serverA, "cap", index),
                serverUrl = serverA,
                sessionId = "cap",
                sortIndex = index,
                messageJson = "{}",
                cachedAtEpochMillis = now + index,
                expiresAtEpochMillis = now + sevenDaysMillis,
            )
        }
        dao.upsertMessages(cappedServerMessages + freshMessage + expiredMessage)

        dao.maintenance(now)

        assertEquals(listOf("fresh"), dao.cachedSessions(serverA, now).map { it.sessionId })
        assertTrue(dao.messageKeys(serverB, "expired").isEmpty())
        assertEquals(listOf(freshMessage.cacheKey), dao.messageKeys(serverB, "fresh"))

        val retainedServerAKeys = dao.messageKeys(serverA, "cap")
        assertEquals(5_000, retainedServerAKeys.size)
        assertFalse(retainedServerAKeys.contains(CachedMessageEntity.cacheKey(serverA, "cap", 0)))
        assertFalse(retainedServerAKeys.contains(CachedMessageEntity.cacheKey(serverA, "cap", 1)))
        assertTrue(retainedServerAKeys.contains(CachedMessageEntity.cacheKey(serverA, "cap", 5_001)))
    }
}
