package com.uzairansar.hermex.data.db

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerCacheOwnershipTest {
    @Test
    fun invalidationRejectsWritesFromPreviousGeneration() = runBlocking {
        val ownership = ServerCacheOwnership()
        val server = "https://hermes.example/"
        val staleGeneration = ownership.generation(server)

        ownership.invalidateAndClear(server) {}

        assertFalse(ownership.writeIfCurrent(server, staleGeneration) {})
        assertTrue(ownership.writeIfCurrent(server, ownership.generation(server)) {})
    }

    @Test
    fun invalidationClearsAnyWriteThatAlreadyOwnsTheServerLock() = runBlocking {
        val ownership = ServerCacheOwnership()
        val server = "https://hermes.example/"
        val generation = ownership.generation(server)
        val writeStarted = CompletableDeferred<Unit>()
        val releaseWrite = CompletableDeferred<Unit>()
        val values = mutableListOf<String>()

        val write = async {
            ownership.writeIfCurrent(server, generation) {
                writeStarted.complete(Unit)
                releaseWrite.await()
                values += "old profile"
            }
        }
        writeStarted.await()
        val invalidate = async {
            ownership.invalidateAndClear(server) { values.clear() }
        }
        releaseWrite.complete(Unit)
        write.await()
        invalidate.await()

        assertEquals(emptyList<String>(), values)
    }
}
