package com.uzairansar.hermex.data.db

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class ServerCacheOwnership {
    private data class ServerState(
        val generation: AtomicLong = AtomicLong(0),
        val mutex: Mutex = Mutex(),
    )

    private val states = ConcurrentHashMap<String, ServerState>()

    fun generation(serverUrl: String): Long = state(serverUrl).generation.get()

    suspend fun writeIfCurrent(
        serverUrl: String,
        generation: Long,
        write: suspend () -> Unit,
    ): Boolean {
        val state = state(serverUrl)
        return state.mutex.withLock {
            if (state.generation.get() != generation) return@withLock false
            write()
            true
        }
    }

    suspend fun <T> readIfCurrent(
        serverUrl: String,
        generation: Long,
        read: suspend () -> T,
    ): T? {
        val state = state(serverUrl)
        return state.mutex.withLock {
            if (state.generation.get() != generation) return@withLock null
            read()
        }
    }

    suspend fun invalidateAndClear(
        serverUrl: String,
        clear: suspend () -> Unit,
    ) {
        val state = state(serverUrl)
        state.mutex.withLock {
            state.generation.incrementAndGet()
            clear()
        }
    }

    private fun state(serverUrl: String): ServerState = states.getOrPut(serverUrl, ::ServerState)
}
