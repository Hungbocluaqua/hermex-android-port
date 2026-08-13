package com.uzairansar.hermex.core.network

import com.uzairansar.hermex.core.model.KanbanEvent
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.BufferedSource

sealed interface KanbanStreamFrame {
    data class Hello(val cursor: Int, val board: String) : KanbanStreamFrame
    data class Events(
        val events: List<KanbanEvent>,
        val cursor: Int,
        val frameId: Int?,
    ) : KanbanStreamFrame

    data object Ignored : KanbanStreamFrame
    data object Malformed : KanbanStreamFrame
}

internal object KanbanStreamFrameDecoder {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    fun decode(eventType: String?, data: String, frameId: String?): KanbanStreamFrame = when (eventType) {
        "hello" -> runCatching { json.decodeFromString<HelloPayload>(data) }
            .getOrNull()
            ?.let { payload ->
                val board = payload.board?.trim()?.takeIf(String::isNotEmpty)
                if (payload.cursor != null && payload.cursor >= 0 && board != null) {
                    KanbanStreamFrame.Hello(payload.cursor, board)
                } else {
                    KanbanStreamFrame.Malformed
                }
            }
            ?: KanbanStreamFrame.Malformed

        "events" -> runCatching { json.decodeFromString<EventsPayload>(data) }
            .getOrNull()
            ?.let { payload ->
                val parsedFrameId = frameId?.toIntOrNull()
                if (
                    payload.events != null &&
                    payload.cursor != null &&
                    payload.cursor >= 0 &&
                    (frameId == null || parsedFrameId != null)
                ) {
                    KanbanStreamFrame.Events(payload.events, payload.cursor, parsedFrameId)
                } else {
                    KanbanStreamFrame.Malformed
                }
            }
            ?: KanbanStreamFrame.Malformed

        else -> KanbanStreamFrame.Ignored
    }

    @Serializable
    private data class HelloPayload(
        val cursor: Int? = null,
        val board: String? = null,
    )

    @Serializable
    private data class EventsPayload(
        val events: List<KanbanEvent>? = null,
        val cursor: Int? = null,
    )
}

interface KanbanEventStreamingClient {
    fun stream(url: HttpUrl): Flow<KanbanStreamFrame>
}

class HttpKanbanEventStreamingClient(
    private val baseUrl: HttpUrl,
    client: OkHttpClient,
    private val onUnauthorized: (HttpUrl) -> Unit = {},
    private val customHeaders: () -> List<CustomHeader>,
) : KanbanEventStreamingClient {
    private val client: OkHttpClient = client.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .addNetworkInterceptor(ServerTransportPolicyInterceptor())
        .addNetworkInterceptor(SameOriginCustomHeaderInterceptor(baseUrl, customHeaders))
        .build()

    override fun stream(url: HttpUrl): Flow<KanbanStreamFrame> = callbackFlow {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache, no-transform")
            .header("Accept-Encoding", "identity")
            .build()
        val call = client.newCall(request)
        call.enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (!call.isCanceled()) close(ApiError.Network(e)) else close()
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!response.isSuccessful) {
                            if (response.code == 401 && response.request.url.isSameOriginAs(baseUrl)) {
                                onUnauthorized(baseUrl)
                            }
                            close(IOException("Kanban live updates are unavailable (HTTP ${response.code})."))
                            return
                        }
                        try {
                            response.body.source().readKanbanSseEvents { id, type, data ->
                                trySendBlocking(KanbanStreamFrameDecoder.decode(type, data, id))
                            }
                            close()
                        } catch (error: KanbanSseEventTooLargeException) {
                            trySendBlocking(KanbanStreamFrame.Malformed)
                            close(IOException("Kanban live update exceeded the safety limit."))
                        } catch (error: IOException) {
                            if (!call.isCanceled()) close(ApiError.Network(error)) else close()
                        }
                    }
                }
            },
        )
        awaitClose { call.cancel() }
    }

    private companion object {
        const val MAX_EVENT_CHARACTERS = 1024 * 1024
    }

    private class KanbanSseEventTooLargeException : IOException()

    private fun BufferedSource.readKanbanSseEvents(
        onEvent: (id: String?, type: String?, data: String) -> Unit,
    ) {
        var eventId: String? = null
        var eventType: String? = null
        val data = StringBuilder()

        fun dispatch() {
            if (data.isEmpty()) {
                eventType = null
                return
            }
            data.setLength(data.length - 1)
            onEvent(eventId, eventType, data.toString())
            data.setLength(0)
            eventType = null
        }

        while (true) {
            val line = readBoundedUtf8Line(MAX_EVENT_CHARACTERS.toLong())
            if (line == null) {
                dispatch()
                return
            }
            if (line.isEmpty()) {
                dispatch()
                continue
            }
            if (line.startsWith(':')) continue

            val delimiter = line.indexOf(':')
            val field = if (delimiter >= 0) line.substring(0, delimiter) else line
            val rawValue = if (delimiter >= 0) line.substring(delimiter + 1) else ""
            val value = rawValue.removePrefix(" ")
            when (field) {
                "event" -> eventType = value
                "data" -> {
                    if (data.length + value.length + 1 > MAX_EVENT_CHARACTERS) {
                        throw KanbanSseEventTooLargeException()
                    }
                    data.append(value).append('\n')
                }
                "id" -> if ('\u0000' !in value) eventId = value
            }
        }
    }

    private fun BufferedSource.readBoundedUtf8Line(maxBytes: Long): String? {
        if (exhausted()) return null
        val newlineIndex = indexOf('\n'.code.toByte(), 0L, maxBytes + 1L)
        if (newlineIndex >= 0L) return readUtf8LineStrict(maxBytes)
        if (buffer.size > maxBytes || request(maxBytes + 1L)) throw KanbanSseEventTooLargeException()
        return readUtf8()
    }
}

private fun HttpUrl.isSameOriginAs(other: HttpUrl): Boolean =
    scheme == other.scheme && host == other.host && port == other.port
