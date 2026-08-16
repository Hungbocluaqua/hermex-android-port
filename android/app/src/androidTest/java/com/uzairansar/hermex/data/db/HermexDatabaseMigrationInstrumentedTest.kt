package com.uzairansar.hermex.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HermexDatabaseMigrationInstrumentedTest {
    private lateinit var context: Context
    private lateinit var databaseFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DATABASE_NAME)
        databaseFile = context.getDatabasePath(DATABASE_NAME)
        databaseFile.parentFile?.mkdirs()
    }

    @After
    fun tearDown() {
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun migrationFromOneToTwoPreservesRowsAndAddsSessionMetadataColumns() {
        SQLiteDatabase.openOrCreateDatabase(databaseFile, null).use { db ->
            db.execSQL(CREATE_V1_SESSIONS)
            db.execSQL(CREATE_V1_MESSAGES)
            db.execSQL("CREATE INDEX index_cached_sessions_serverUrl ON cached_sessions(serverUrl)")
            db.execSQL("CREATE UNIQUE INDEX index_cached_sessions_serverUrl_sessionId ON cached_sessions(serverUrl, sessionId)")
            db.execSQL("CREATE INDEX index_cached_messages_serverUrl ON cached_messages(serverUrl)")
            db.execSQL("CREATE INDEX index_cached_messages_sessionId ON cached_messages(sessionId)")
            db.execSQL("CREATE UNIQUE INDEX index_cached_messages_serverUrl_sessionId_sortIndex ON cached_messages(serverUrl, sessionId, sortIndex)")
            db.execSQL(
                "INSERT INTO cached_sessions " +
                    "(cacheKey, serverUrl, sessionId, title, cachedAtEpochMillis, expiresAtEpochMillis) " +
                    "VALUES (?, ?, ?, ?, ?, ?)",
                arrayOf<Any>("server::session", "https://server/", "session", "Preserved", 1L, Long.MAX_VALUE),
            )
            db.version = 1
        }

        val database = HermexDatabase.create(context)
        try {
            database.openHelper.writableDatabase.query("SELECT title FROM cached_sessions WHERE sessionId = 'session'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Preserved", cursor.getString(0))
            }
            database.openHelper.writableDatabase.query("PRAGMA table_info(cached_sessions)").use { cursor ->
                val columns = buildSet {
                    val nameIndex = cursor.getColumnIndexOrThrow("name")
                    while (cursor.moveToNext()) add(cursor.getString(nameIndex))
                }
                assertTrue(
                    columns.containsAll(
                        setOf(
                            "isCliSession",
                            "sourceTag",
                            "rawSource",
                            "sessionSource",
                            "sourceLabel",
                            "parentSessionId",
                            "relationshipType",
                            "readOnly",
                            "isReadOnly",
                        ),
                    ),
                )
            }
        } finally {
            database.close()
        }
    }

    private companion object {
        const val DATABASE_NAME = "hermex.db"
        const val CREATE_V1_SESSIONS = """
            CREATE TABLE IF NOT EXISTS cached_sessions (
                cacheKey TEXT NOT NULL PRIMARY KEY,
                serverUrl TEXT NOT NULL,
                sessionId TEXT NOT NULL,
                title TEXT,
                workspace TEXT,
                model TEXT,
                modelProvider TEXT,
                messageCount INTEGER,
                createdAt REAL,
                updatedAt REAL,
                lastMessageAt REAL,
                pinned INTEGER,
                archived INTEGER,
                projectId TEXT,
                profile TEXT,
                inputTokens INTEGER,
                outputTokens INTEGER,
                estimatedCost REAL,
                activeStreamId TEXT,
                isStreaming INTEGER,
                cachedAtEpochMillis INTEGER NOT NULL,
                expiresAtEpochMillis INTEGER NOT NULL
            )
        """
        const val CREATE_V1_MESSAGES = """
            CREATE TABLE IF NOT EXISTS cached_messages (
                cacheKey TEXT NOT NULL PRIMARY KEY,
                serverUrl TEXT NOT NULL,
                sessionId TEXT NOT NULL,
                sortIndex INTEGER NOT NULL,
                messageJson TEXT NOT NULL,
                cachedAtEpochMillis INTEGER NOT NULL,
                expiresAtEpochMillis INTEGER NOT NULL
            )
        """
    }
}
