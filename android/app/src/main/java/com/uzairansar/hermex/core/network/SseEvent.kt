package com.uzairansar.hermex.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

sealed interface SseEvent {
    data class Token(val text: String) : SseEvent
    data class InterimAssistant(val text: String, val alreadyStreamed: Boolean?) : SseEvent
    data class Reasoning(val text: String) : SseEvent
    data class ToolStarted(val event: ToolStreamEvent) : SseEvent
    data class ToolCompleted(val event: ToolStreamEvent) : SseEvent
    data class Title(val sessionId: String?, val title: String?) : SseEvent
    data class Done(val sessionId: String?, val usage: JsonElement?) : SseEvent
    data class PendingSteerLeftover(val text: String) : SseEvent
    data object StreamEnd : SseEvent
    data object Cancelled : SseEvent
    data class Error(val message: String) : SseEvent
    data class TransportError(val message: String) : SseEvent
    data object Ignored : SseEvent
}

@Serializable
data class ToolStreamEvent(
    @SerialName("event_type") val eventType: String? = null,
    val name: String? = null,
    val preview: String? = null,
    val args: Map<String, JsonElement>? = null,
    val duration: Double? = null,
    @SerialName("is_error") val isError: Boolean? = null,
    val tid: String? = null,
    val id: String? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
    @SerialName("tool_use_id") val toolUseId: String? = null,
    @SerialName("call_id") val callId: String? = null,
)

@Serializable
private data class TextPayload(
    val text: String? = null,
)

@Serializable
private data class InterimAssistantPayload(
    val text: String? = null,
    @SerialName("already_streamed") val alreadyStreamed: Boolean? = null,
)

@Serializable
private data class TitlePayload(
    @SerialName("session_id") val sessionId: String? = null,
    val title: String? = null,
)

@Serializable
private data class DonePayload(
    @SerialName("session_id") val sessionId: String? = null,
    val usage: JsonElement? = null,
    val event: DonePayload? = null,
)

@Serializable
private data class ErrorPayload(
    val error: String? = null,
    val message: String? = null,
)

object SseEventDecoder {
    fun decode(eventType: String?, data: String): SseEvent {
        val type = eventType ?: "message"
        return try {
            when (type) {
                "token" -> SseEvent.Token(HermesJson.decodeFromString<TextPayload>(data).text.orEmpty())
                "interim_assistant" -> HermesJson.decodeFromString<InterimAssistantPayload>(data).let {
                    SseEvent.InterimAssistant(it.text.orEmpty(), it.alreadyStreamed)
                }
                "reasoning" -> SseEvent.Reasoning(HermesJson.decodeFromString<TextPayload>(data).text.orEmpty())
                "tool" -> SseEvent.ToolStarted(HermesJson.decodeFromString(data))
                "tool_complete" -> SseEvent.ToolCompleted(HermesJson.decodeFromString(data))
                "title" -> HermesJson.decodeFromString<TitlePayload>(data).let { SseEvent.Title(it.sessionId, it.title) }
                "done" -> HermesJson.decodeFromString<DonePayload>(data).let {
                    val event = it.event ?: it
                    SseEvent.Done(event.sessionId, event.usage)
                }
                "pending_steer_leftover" -> SseEvent.PendingSteerLeftover(HermesJson.decodeFromString<TextPayload>(data).text.orEmpty())
                "stream_end" -> SseEvent.StreamEnd
                "cancel" -> SseEvent.Cancelled
                "error", "apperror" -> HermesJson.decodeFromString<ErrorPayload>(data).let {
                    SseEvent.Error(it.error ?: it.message ?: "The stream returned an error.")
                }
                "initial", "approval", "clarify" -> SseEvent.Ignored
                else -> SseEvent.Ignored
            }
        } catch (error: Throwable) {
            SseEvent.TransportError("Malformed SSE $type event.")
        }
    }
}
