package com.uzairansar.hermex.core.network

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

class SseStreamClient(
    private val client: OkHttpClient,
    private val customHeaders: () -> List<CustomHeader>,
) {
    fun stream(url: okhttp3.HttpUrl): Flow<SseEvent> = callbackFlow {
        val requestBuilder = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache, no-transform")
            .header("Accept-Encoding", "identity")
        customHeaders().applyTo(requestBuilder)

        val eventSource = EventSources.createFactory(client).newEventSource(
            requestBuilder.build(),
            object : EventSourceListener() {
                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    trySend(SseEventDecoder.decode(type, data))
                }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: okhttp3.Response?) {
                    val message = t?.message ?: response?.message ?: "SSE connection failed."
                    trySend(SseEvent.TransportError(message))
                    close(t)
                }

                override fun onClosed(eventSource: EventSource) {
                    close()
                }
            },
        )

        awaitClose { eventSource.cancel() }
    }
}
