package com.uzairansar.hermex.core.network

import com.uzairansar.hermex.core.model.ContextWindowSnapshot
import com.uzairansar.hermex.core.model.ApprovalPendingResponse
import com.uzairansar.hermex.core.model.ClarificationPendingResponse
import com.uzairansar.hermex.core.model.SessionDetail
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

sealed interface SseEvent {
    data class Token(val text: String) : SseEvent
    data class InterimAssistant(val text: String, val alreadyStreamed: Boolean?) : SseEvent
    data class Reasoning(val text: String) : SseEvent
    data class ToolStarted(val event: ToolStreamEvent) : SseEvent
    data class ToolCompleted(val event: ToolStreamEvent) : SseEvent
    data class Title(val sessionId: String?, val title: String?) : SseEvent
    data class Done(
        val sessionId: String?,
        val usage: ContextWindowSnapshot?,
        val session: SessionDetail?,
    ) : SseEvent
    data class PendingSteerLeftover(val text: String) : SseEvent
    data class ApprovalPending(val response: ApprovalPendingResponse) : SseEvent
    data class ClarificationPending(val response: ClarificationPendingResponse) : SseEvent
    data object StreamEnd : SseEvent
    data object Cancelled : SseEvent
    data class Error(val message: String) : SseEvent
    data class TransportError(val message: String) : SseEvent
    data object Ignored : SseEvent
}

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
    val usage: ContextWindowSnapshot? = null,
    val session: SessionDetail? = null,
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
                "tool" -> SseEvent.ToolStarted(decodeToolEvent(data))
                "tool_complete" -> SseEvent.ToolCompleted(decodeToolEvent(data))
                "title" -> HermesJson.decodeFromString<TitlePayload>(data).let { SseEvent.Title(it.sessionId, it.title) }
                "done" -> HermesJson.decodeFromString<DonePayload>(data).let {
                    val event = it.event ?: it
                    SseEvent.Done(
                        sessionId = event.sessionId ?: event.session?.sessionId,
                        usage = event.usage,
                        session = event.session,
                    )
                }
                "pending_steer_leftover" -> SseEvent.PendingSteerLeftover(HermesJson.decodeFromString<TextPayload>(data).text.orEmpty())
                "stream_end" -> SseEvent.StreamEnd
                "cancel" -> SseEvent.Cancelled
                "error", "apperror" -> HermesJson.decodeFromString<ErrorPayload>(data).let {
                    SseEvent.Error(it.error ?: it.message ?: "The stream returned an error.")
                }
                "approval" -> SseEvent.ApprovalPending(HermesJson.decodeFromString(data))
                "clarify" -> SseEvent.ClarificationPending(HermesJson.decodeFromString(data))
                "initial" -> if (data.contains("clarify_id") || data.contains("choices_offered") || data.contains("\"question\"")) {
                    SseEvent.ClarificationPending(HermesJson.decodeFromString(data))
                } else {
                    SseEvent.ApprovalPending(HermesJson.decodeFromString(data))
                }
                else -> SseEvent.Ignored
            }
        } catch (error: Throwable) {
            SseEvent.TransportError("Malformed SSE $type event.")
        }
    }

    private fun decodeToolEvent(data: String): ToolStreamEvent {
        val payload = HermesJson.parseToJsonElement(data) as? JsonObject
            ?: throw IllegalArgumentException("Tool event payload must be an object.")
        return ToolStreamEvent(
            eventType = payload.lossyString("event_type"),
            name = payload.lossyString("name"),
            preview = payload.lossyString("preview"),
            args = payload["args"] as? JsonObject,
            duration = payload.lossyDouble("duration"),
            isError = payload.lossyBoolean("is_error"),
            tid = payload.lossyString("tid"),
            id = payload.lossyString("id"),
            toolCallId = payload.lossyString("tool_call_id"),
            toolUseId = payload.lossyString("tool_use_id"),
            callId = payload.lossyString("call_id"),
        )
    }

    private fun JsonObject.lossyString(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.lossyDouble(key: String): Double? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        return primitive.doubleOrNull ?: primitive.contentOrNull?.toDoubleOrNull()
    }

    private fun JsonObject.lossyBoolean(key: String): Boolean? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        primitive.booleanOrNull?.let { return it }
        return when (primitive.contentOrNull?.trim()?.lowercase()) {
            "true", "1", "yes" -> true
            "false", "0", "no" -> false
            else -> null
        }
    }
}
