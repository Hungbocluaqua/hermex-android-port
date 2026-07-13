package com.uzairansar.hermex.core.network

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.BufferedSource
import java.io.IOException

class SseStreamClient(
    baseUrl: HttpUrl,
    client: OkHttpClient,
    private val customHeaders: () -> List<CustomHeader>,
) {
    private val client: OkHttpClient = client.newBuilder()
        .addNetworkInterceptor(ServerTransportPolicyInterceptor())
        .addNetworkInterceptor(SameOriginCustomHeaderInterceptor(baseUrl, customHeaders))
        .build()

    fun stream(
        url: okhttp3.HttpUrl,
        onEventId: (String) -> Unit = {},
    ): Flow<SseEvent> = callbackFlow {
        val requestBuilder = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache, no-transform")
            .header("Accept-Encoding", "identity")

        val call = client.newCall(requestBuilder.build())
        call.enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (!call.isCanceled()) {
                        trySendBlocking(SseEvent.TransportError(e.message ?: "SSE connection failed."))
                    }
                    close()
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!response.isSuccessful) {
                            val error = IOException("SSE request failed with HTTP ${response.code}.")
                            trySendBlocking(SseEvent.TransportError(requireNotNull(error.message)))
                            close()
                            return
                        }
                        try {
                            response.body.source().readSseEvents { id, type, data ->
                                val event = SseEventDecoder.decode(type, data)
                                if (trySendBlocking(event).isSuccess) {
                                    id?.trim()?.takeIf { it.isNotEmpty() }?.let(onEventId)
                                }
                            }
                            close()
                        } catch (error: SseEventTooLargeException) {
                            trySendBlocking(SseEvent.TransportError("SSE event exceeded the stream safety limit."))
                            close()
                        } catch (error: IOException) {
                            if (!call.isCanceled()) {
                                trySendBlocking(SseEvent.TransportError(error.message ?: "SSE connection failed."))
                            }
                            close()
                        }
                    }
                }
            },
        )

        awaitClose { call.cancel() }
    }

    companion object {
        internal const val MAX_SSE_EVENT_CHARACTERS = 1024 * 1024
    }
}

private class SseEventTooLargeException : IOException()

private fun BufferedSource.readSseEvents(
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
        val line = readBoundedUtf8Line(SseStreamClient.MAX_SSE_EVENT_CHARACTERS.toLong())
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
                if (data.length + value.length + 1 > SseStreamClient.MAX_SSE_EVENT_CHARACTERS) {
                    throw SseEventTooLargeException()
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
    if (buffer.size > maxBytes || request(maxBytes + 1L)) throw SseEventTooLargeException()
    return readUtf8()
}
