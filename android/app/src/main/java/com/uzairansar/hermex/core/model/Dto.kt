package com.uzairansar.hermex.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.Instant

@Serializable
data class HealthResponse(
    val status: String? = null,
)

@Serializable
data class AuthStatusResponse(
    @SerialName("auth_enabled") val authEnabled: Boolean? = null,
    @SerialName("password_auth_enabled") val passwordAuthEnabled: Boolean? = null,
)

@Serializable
data class LoginResponse(
    val ok: Boolean? = null,
    val error: String? = null,
)

@Serializable
data class SessionsResponse(
    val sessions: List<SessionSummary>? = null,
    @SerialName("cli_count") val cliCount: Int? = null,
    @SerialName("archived_count") val archivedCount: Int? = null,
    @SerialName("server_time") val serverTime: Double? = null,
    @SerialName("server_tz") val serverTz: String? = null,
)

@Serializable
data class SessionSearchResponse(
    val sessions: List<SessionSummary>? = null,
    val query: String? = null,
    val count: Int? = null,
)

@Serializable
data class SessionResponse(
    val session: SessionDetail? = null,
)

@Serializable
data class SessionMutationResponse(
    val ok: Boolean? = null,
    val session: SessionSummary? = null,
    val error: String? = null,
)

@Serializable
data class SessionClearResponse(
    val ok: Boolean? = null,
    val session: SessionDetail? = null,
    val error: String? = null,
)

@Serializable
data class SessionBranchResponse(
    @SerialName("session_id") val sessionId: String? = null,
    val title: String? = null,
    @SerialName("parent_session_id") val parentSessionId: String? = null,
    val error: String? = null,
)

@Serializable
data class SessionCompressResponse(
    val ok: Boolean? = null,
    val session: SessionDetail? = null,
    val summary: SessionCompressionSummary? = null,
    @SerialName("focus_topic") val focusTopic: String? = null,
    val error: String? = null,
)

@Serializable
data class SessionCompressionSummary(
    val headline: String? = null,
    @SerialName("token_line") val tokenLine: String? = null,
    val note: String? = null,
    @SerialName("reference_message") val referenceMessage: String? = null,
)

@Serializable
data class SessionUndoResponse(
    val ok: Boolean? = null,
    @SerialName("removed_count") val removedCount: Int? = null,
    @SerialName("removed_preview") val removedPreview: String? = null,
    val error: String? = null,
)

@Serializable
data class SessionRetryResponse(
    val ok: Boolean? = null,
    @SerialName("last_user_text") val lastUserText: String? = null,
    @SerialName("removed_count") val removedCount: Int? = null,
    val error: String? = null,
)

@Serializable
data class SessionStatusResponse(
    val active: Boolean? = null,
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("stream_id") val streamId: String? = null,
    @SerialName("active_stream_id") val activeStreamId: String? = null,
    @SerialName("is_streaming") val isStreaming: Boolean? = null,
    @SerialName("replay_available") val replayAvailable: Boolean? = null,
    @SerialName("pending_user_message") val pendingUserMessage: String? = null,
    val error: String? = null,
)

@Serializable
data class SessionUsageResponse(
    @SerialName("input_tokens") val inputTokens: Int? = null,
    @SerialName("output_tokens") val outputTokens: Int? = null,
    @SerialName("total_tokens") val totalTokens: Int? = null,
    @SerialName("estimated_cost") val estimatedCost: Double? = null,
    val model: String? = null,
    val error: String? = null,
)

@Serializable
data class SessionSummary(
    @SerialName("session_id") val sessionId: String? = null,
    val title: String? = null,
    val workspace: String? = null,
    val model: String? = null,
    @SerialName("model_provider") val modelProvider: String? = null,
    @SerialName("message_count") val messageCount: Int? = null,
    @SerialName("created_at") val createdAt: Double? = null,
    @SerialName("updated_at") val updatedAt: Double? = null,
    @SerialName("last_message_at") val lastMessageAt: Double? = null,
    val pinned: Boolean? = null,
    val archived: Boolean? = null,
    @SerialName("project_id") val projectId: String? = null,
    val profile: String? = null,
    @SerialName("input_tokens") val inputTokens: Int? = null,
    @SerialName("output_tokens") val outputTokens: Int? = null,
    @SerialName("estimated_cost") val estimatedCost: Double? = null,
    @SerialName("active_stream_id") val activeStreamId: String? = null,
    @SerialName("is_streaming") val isStreaming: Boolean? = null,
    @SerialName("is_cli_session") val isCliSession: Boolean? = null,
    @SerialName("source_tag") val sourceTag: String? = null,
    @SerialName("session_source") val sessionSource: String? = null,
    @SerialName("source_label") val sourceLabel: String? = null,
    @SerialName("match_type") val matchType: String? = null,
) {
    val stableId: String
        get() = sessionId?.takeIf { it.isNotBlank() }
            ?: "session-${title.orEmpty()}-${createdAt ?: updatedAt ?: lastMessageAt ?: 0.0}"
}

@Serializable
data class SessionDetail(
    @SerialName("session_id") val sessionId: String? = null,
    val title: String? = null,
    val workspace: String? = null,
    val model: String? = null,
    @SerialName("model_provider") val modelProvider: String? = null,
    @SerialName("message_count") val messageCount: Int? = null,
    @SerialName("created_at") val createdAt: Double? = null,
    @SerialName("updated_at") val updatedAt: Double? = null,
    @SerialName("last_message_at") val lastMessageAt: Double? = null,
    val pinned: Boolean? = null,
    val archived: Boolean? = null,
    @SerialName("project_id") val projectId: String? = null,
    val profile: String? = null,
    val messages: List<ChatMessage>? = null,
    @SerialName("_messages_offset") val messagesOffset: Int? = null,
    @SerialName("messagesOffset") val camelMessagesOffset: Int? = null,
    @SerialName("_messagesOffset") val transformedMessagesOffset: Int? = null,
    @SerialName("_messages_truncated") val messagesTruncated: Boolean? = null,
    @SerialName("messages_truncated") val snakeMessagesTruncated: Boolean? = null,
    @SerialName("messagesTruncated") val camelMessagesTruncated: Boolean? = null,
    @SerialName("_messagesTruncated") val transformedMessagesTruncated: Boolean? = null,
    @SerialName("context_length") val contextLength: Int? = null,
    @SerialName("threshold_tokens") val thresholdTokens: Int? = null,
    @SerialName("last_prompt_tokens") val lastPromptTokens: Int? = null,
    @SerialName("input_tokens") val inputTokens: Int? = null,
    @SerialName("output_tokens") val outputTokens: Int? = null,
    @SerialName("estimated_cost") val estimatedCost: Double? = null,
    @SerialName("active_stream_id") val activeStreamId: String? = null,
    @SerialName("is_streaming") val isStreaming: Boolean? = null,
    @SerialName("is_cli_session") val isCliSession: Boolean? = null,
    @SerialName("tool_calls") val toolCalls: List<PersistedToolCall>? = null,
    @SerialName("compression_anchor_visible_idx") val compressionAnchorVisibleIdx: Int? = null,
    @SerialName("compression_anchor_message_key") val compressionAnchorMessageKey: CompressionAnchorMessageKey? = null,
    @SerialName("compression_anchor_summary") val compressionAnchorSummary: String? = null,
)

fun SessionDetail.resolvedMessagesOffset(loadedMessageCount: Int): Int {
    messagesOffset?.let { return it.coerceAtLeast(0) }
    camelMessagesOffset?.let { return it.coerceAtLeast(0) }
    transformedMessagesOffset?.let { return it.coerceAtLeast(0) }
    if (resolvedMessagesTruncated() && messageCount != null) {
        return (messageCount - loadedMessageCount).coerceAtLeast(0)
    }
    return 0
}

fun SessionDetail.hasOlderMessages(loadedMessageCount: Int): Boolean =
    resolvedMessagesOffset(loadedMessageCount) > 0 || resolvedMessagesTruncated()

fun SessionDetail.resolvedMessagesTruncated(): Boolean =
    messagesTruncated == true ||
        snakeMessagesTruncated == true ||
        camelMessagesTruncated == true ||
        transformedMessagesTruncated == true

object ChatMessagePageMerger {
    fun prependOlderMessages(
        olderMessages: List<ChatMessage>,
        currentMessages: List<ChatMessage>,
    ): List<ChatMessage> {
        if (olderMessages.isEmpty()) return currentMessages
        val seenKeys = currentMessages.map { it.stablePageKey() }.toMutableSet()
        val uniqueOlderMessages = olderMessages.filter { message -> seenKeys.add(message.stablePageKey()) }
        return uniqueOlderMessages + currentMessages
    }

    private fun ChatMessage.stablePageKey(): String =
        messageId?.takeIf { it.isNotBlank() }
            ?: id?.takeIf { it.isNotBlank() }
            ?: listOf(
                role.orEmpty(),
                timestamp?.toString().orEmpty(),
                displayText,
            ).joinToString(separator = "\u001f")
}

@Serializable
data class CompressionAnchorMessageKey(
    val role: String? = null,
    val ts: Double? = null,
    val text: String? = null,
    val attachments: Int? = null,
)

data class CompressionAnchorMetadata(
    val visibleIdx: Int? = null,
    val messageKey: CompressionAnchorMessageKey? = null,
    val summary: String? = null,
)

fun SessionDetail.compressionAnchorMetadata(): CompressionAnchorMetadata? {
    if (
        compressionAnchorVisibleIdx == null &&
        compressionAnchorMessageKey == null &&
        compressionAnchorSummary == null
    ) {
        return null
    }
    return CompressionAnchorMetadata(
        visibleIdx = compressionAnchorVisibleIdx,
        messageKey = compressionAnchorMessageKey,
        summary = compressionAnchorSummary,
    )
}

data class CompressionReferenceCard(
    val referenceText: String,
    val afterMessageIndex: Int? = null,
)

object CompressionAnchorResolver {
    fun resolve(
        messages: List<ChatMessage>,
        messagesOffset: Int,
        metadata: CompressionAnchorMetadata?,
    ): CompressionReferenceCard? {
        metadata ?: return null
        val summary = metadata.summary.orEmpty().trim()
        if (metadata.visibleIdx == null && metadata.messageKey == null && summary.isBlank()) return null

        val referenceText = referenceText(messages, summary)
        if (!shouldShowReference(referenceText)) return null

        val candidateIndices = anchorCandidateIndices(messages)
        val anchorKey = metadata.messageKey
        if (anchorKey != null) {
            val matchedIndex = latestMatch(anchorKey, messages, candidateIndices)
            if (matchedIndex != null) return CompressionReferenceCard(referenceText, afterMessageIndex = matchedIndex)
        }

        val visibleIdx = metadata.visibleIdx
        if (visibleIdx != null && candidateIndices.isNotEmpty()) {
            val localIdx = visibleIdx - messagesOffset.coerceAtLeast(0)
            if (localIdx < 0) return CompressionReferenceCard(referenceText, afterMessageIndex = null)
            val clampedIdx = localIdx.coerceAtMost(candidateIndices.lastIndex)
            return CompressionReferenceCard(referenceText, afterMessageIndex = candidateIndices[clampedIdx])
        }

        return CompressionReferenceCard(referenceText, afterMessageIndex = null)
    }

    private fun referenceText(messages: List<ChatMessage>, summary: String): String {
        val normalizedSummary = normalizedWhitespace(summary)
        messages.asReversed().forEach { message ->
            if (classifyMarker(message) != MarkerKind.ContextCompaction) return@forEach
            val content = message.content.orEmpty()
            if (normalizedSummary.isBlank() || normalizedWhitespace(content).contains(normalizedSummary)) {
                return content
            }
        }
        return summary
    }

    private fun shouldShowReference(referenceText: String): Boolean {
        val trimmed = referenceText.trim()
        return trimmed.isNotEmpty() && !isContextCompactionText(trimmed)
    }

    private fun anchorCandidateIndices(messages: List<ChatMessage>): List<Int> =
        messages.indices.filter { index ->
            val message = messages[index]
            val role = message.role.orEmpty()
            if (role.isBlank() || role == "tool") return@filter false
            if (classifyMarker(message) != null) return@filter false

            val hasText = message.content?.isNotBlank() == true
            val hasAttachments = message.attachments?.isNotEmpty() == true
            if (hasText || hasAttachments) return@filter true

            if (role != "assistant") return@filter false
            val hasTools = message.toolCalls?.isNotEmpty() == true
            val hasReasoning = message.reasoning.orEmpty().any { it.text?.isNotBlank() == true }
            hasTools || hasReasoning
        }

    private data class CandidateKey(
        val role: String,
        val ts: Double?,
        val text: String,
        val attachments: Int,
    )

    private fun candidateKey(message: ChatMessage): CandidateKey? {
        val role = message.role?.takeIf { it.isNotBlank() && it != "tool" } ?: return null
        val normalizedText = normalizedAnchorText(message.content.orEmpty())
        val attachments = message.attachments?.size ?: 0
        val ts = message.timestamp
        if (normalizedText.isEmpty() && attachments == 0 && ts == null) return null
        return CandidateKey(role = role, ts = ts, text = normalizedText, attachments = attachments)
    }

    private fun latestMatch(
        anchorKey: CompressionAnchorMessageKey,
        messages: List<ChatMessage>,
        candidateIndices: List<Int>,
    ): Int? {
        for (index in candidateIndices.asReversed()) {
            val candidate = candidateKey(messages[index]) ?: continue
            if (candidate.role != anchorKey.role.orEmpty()) continue
            val anchorTs = anchorKey.ts
            if (anchorTs != null && candidate.ts != null && anchorTs != candidate.ts) continue
            if (candidate.text != anchorKey.text.orEmpty()) continue
            if (candidate.attachments != (anchorKey.attachments ?: 0)) continue
            return index
        }
        return null
    }

    fun normalizedAnchorText(text: String): String =
        normalizedWhitespace(text).take(160)

    private fun normalizedWhitespace(text: String): String =
        text.splitToSequence(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ")

    private enum class MarkerKind {
        ContextCompaction,
        PreservedTaskList,
    }

    private fun classifyMarker(message: ChatMessage): MarkerKind? {
        val role = message.role ?: return null
        if (role == "tool") return null
        val text = message.content.orEmpty().trim()
        if (role == "user" && text.startsWith(PRESERVED_TASK_LIST_PREFIX, ignoreCase = true)) {
            return MarkerKind.PreservedTaskList
        }
        if (isContextCompactionText(text)) return MarkerKind.ContextCompaction
        return null
    }

    private fun isContextCompactionText(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed.startsWith("[context compaction", ignoreCase = true) ||
            trimmed.startsWith("context compaction", ignoreCase = true)
    }

    private const val PRESERVED_TASK_LIST_PREFIX = "[your active task list was preserved across context compression]"
}

@Serializable
data class ContextWindowSnapshot(
    @SerialName("context_length") val contextLength: Int? = null,
    @SerialName("threshold_tokens") val thresholdTokens: Int? = null,
    @SerialName("last_prompt_tokens") val lastPromptTokens: Int? = null,
    @SerialName("input_tokens") val inputTokens: Int? = null,
    @SerialName("output_tokens") val outputTokens: Int? = null,
    @SerialName("estimated_cost") val estimatedCost: Double? = null,
) {
    val tokensUsed: Int?
        get() = lastPromptTokens ?: inputTokens

    val percentage: Double?
        get() {
            val used = tokensUsed ?: return null
            val total = contextLength?.takeIf { it > 0 } ?: return null
            return used.toDouble() / total.toDouble()
        }
}

fun SessionDetail.contextWindowSnapshot(): ContextWindowSnapshot? {
    val hasUsage = listOf(
        contextLength,
        thresholdTokens,
        lastPromptTokens,
        inputTokens,
        outputTokens,
        estimatedCost,
    ).any { it != null }
    if (!hasUsage) return null
    return ContextWindowSnapshot(
        contextLength = contextLength,
        thresholdTokens = thresholdTokens,
        lastPromptTokens = lastPromptTokens,
        inputTokens = inputTokens,
        outputTokens = outputTokens,
        estimatedCost = estimatedCost,
    )
}

@Serializable(with = ChatMessageSerializer::class)
data class ChatMessage(
    val id: String? = null,
    val role: String? = null,
    val content: String? = null,
    val text: String? = null,
    val timestamp: Double? = null,
    val messageId: String? = null,
    val name: String? = null,
    val toolCallId: String? = null,
    val toolUseId: String? = null,
    val parts: List<JsonElement>? = null,
    val attachments: List<MessageAttachment>? = null,
    val reasoning: List<ReasoningSegment>? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCall>? = null,
) {
    val displayText: String
        get() = content ?: text ?: parts?.joinToString("\n") { it.toString() }.orEmpty()
}

fun ChatMessage.shouldRenderTranscriptItem(showThinkingAndToolCards: Boolean = true): Boolean {
    if (role == "tool") return false
    val hasText = displayText.trim().isNotEmpty()
    val hasAttachments = attachments?.isNotEmpty() == true
    if (role == "user") return hasText || hasAttachments

    val hasAccessoryCards = showThinkingAndToolCards &&
        (reasoning.orEmpty().any { it.text?.isNotBlank() == true } || toolCalls?.isNotEmpty() == true)
    return hasText || hasAttachments || hasAccessoryCards
}

object ChatMessageSerializer : KSerializer<ChatMessage> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ChatMessage") {
        element<String?>("id")
        element<String?>("role")
        element<String?>("content")
        element<String?>("text")
        element<Double?>("timestamp")
        element<String?>("messageId")
        element<String?>("name")
        element<String?>("toolCallId")
        element<String?>("toolUseId")
        element<List<JsonElement>?>("parts")
        element<List<MessageAttachment>?>("attachments")
        element<List<ReasoningSegment>?>("reasoning")
        element<List<ToolCall>?>("toolCalls")
    }

    override fun deserialize(decoder: Decoder): ChatMessage {
        val jsonDecoder = decoder as? JsonDecoder ?: return ChatMessage()
        val element = jsonDecoder.decodeJsonElement() as? JsonObject ?: return ChatMessage()
        val contentValue = element["content"]
        val contentParts = contentValue as? JsonArray
        val contentText = when (contentValue) {
            is JsonPrimitive -> contentValue.contentOrNull
            is JsonArray -> textContentFromParts(contentValue)
            is JsonObject -> contentValue.toString()
            else -> null
        }

        val decodedAttachments = decodeListOrNull<MessageAttachment>(jsonDecoder, element["attachments"])
        return ChatMessage(
            id = element["id"].stringOrNull() ?: element["message_id"].stringOrNull() ?: element["messageId"].stringOrNull(),
            role = element["role"].stringOrNull(),
            content = contentText,
            text = element["text"].stringOrNull(),
            timestamp = element["_ts"].doubleValueOrNull()
                ?: element["timestamp"].doubleValueOrNull()
                ?: element["ts"].doubleValueOrNull(),
            messageId = element["message_id"].stringOrNull() ?: element["messageId"].stringOrNull(),
            name = element["name"].stringOrNull(),
            toolCallId = element["tool_call_id"].stringOrNull() ?: element["toolCallId"].stringOrNull(),
            toolUseId = element["tool_use_id"].stringOrNull() ?: element["toolUseId"].stringOrNull(),
            parts = contentParts?.toList() ?: (element["parts"] as? JsonArray)?.toList(),
            attachments = mergeAttachmentsWithAttachedFilesMarker(decodedAttachments, contentText),
            reasoning = decodeReasoning(jsonDecoder, element["reasoning"]),
            toolCalls = decodeListOrNull(jsonDecoder, element["tool_calls"] ?: element["toolCalls"]),
        )
    }

    override fun serialize(encoder: Encoder, value: ChatMessage) {
        val jsonEncoder = encoder as? JsonEncoder ?: return
        jsonEncoder.encodeJsonElement(
            buildJsonObject {
                value.id?.let { put("id", it) }
                value.messageId?.let { put("message_id", it) }
                value.role?.let { put("role", it) }
                value.content?.let { put("content", it) }
                value.text?.let { put("text", it) }
                value.timestamp?.let { put("timestamp", it) }
                value.name?.let { put("name", it) }
                value.toolCallId?.let { put("tool_call_id", it) }
                value.toolUseId?.let { put("tool_use_id", it) }
                value.parts?.let { put("parts", jsonEncoder.json.encodeToJsonElement(it)) }
                value.attachments?.let { put("attachments", jsonEncoder.json.encodeToJsonElement(it)) }
                value.reasoning?.let { put("reasoning", jsonEncoder.json.encodeToJsonElement(it)) }
                value.toolCalls?.let { put("tool_calls", jsonEncoder.json.encodeToJsonElement(it)) }
            },
        )
    }

    private inline fun <reified T> decodeListOrNull(jsonDecoder: JsonDecoder, element: JsonElement?): List<T>? {
        if (element !is JsonArray) return null
        return runCatching { jsonDecoder.json.decodeFromJsonElement<List<T>>(element) }.getOrNull()
    }

    private fun decodeReasoning(jsonDecoder: JsonDecoder, element: JsonElement?): List<ReasoningSegment>? =
        when (element) {
            is JsonPrimitive -> element.contentOrNull?.let { listOf(ReasoningSegment(text = it)) }
            is JsonArray -> runCatching { jsonDecoder.json.decodeFromJsonElement<List<ReasoningSegment>>(element) }.getOrNull()
            is JsonObject -> listOf(
                ReasoningSegment(
                    text = element["text"].stringOrNull()
                        ?: element["content"].stringOrNull()
                        ?: element["summary"].stringOrNull(),
                ),
            )
            else -> null
        }

    private fun textContentFromParts(parts: JsonArray): String? {
        val text = parts.joinToString(separator = "") { part ->
            when (part) {
                is JsonPrimitive -> part.contentOrNull.orEmpty()
                is JsonObject -> {
                    val type = part["type"].stringOrNull()
                    if (type == "text") (part["text"] as? JsonPrimitive)?.contentOrNull.orEmpty() else ""
                }
                else -> ""
            }
        }.trim()
        return text.takeIf { it.isNotEmpty() }
    }

    private fun mergeAttachmentsWithAttachedFilesMarker(
        decodedAttachments: List<MessageAttachment>?,
        content: String?,
    ): List<MessageAttachment>? {
        val inferredAttachments = inferredAttachmentsFromAttachedFilesMarker(content)
        if (decodedAttachments.isNullOrEmpty()) return inferredAttachments
        if (inferredAttachments.isNullOrEmpty()) return decodedAttachments

        val availableInferred = inferredAttachments.mapIndexed { index, attachment -> index to attachment }.toMutableList()
        return decodedAttachments.mapIndexed { index, attachment ->
            if (!attachment.path.isNullOrBlank()) return@mapIndexed attachment
            val matchIndex = attachment.identityKey?.let { key ->
                availableInferred.indexOfFirst { (_, inferred) -> inferred.identityKey == key }
            }?.takeIf { it >= 0 } ?: availableInferred.indexOfFirst { (offset, _) -> offset == index }
            if (matchIndex < 0) return@mapIndexed attachment

            val inferred = availableInferred.removeAt(matchIndex).second
            MessageAttachment(
                name = attachment.name.nonBlank() ?: inferred.name,
                path = inferred.path.nonBlank(),
                mime = attachment.mime ?: inferred.mime,
                size = attachment.size ?: inferred.size,
                isImage = attachment.isImage ?: inferred.isImage,
            )
        }
    }

    private fun inferredAttachmentsFromAttachedFilesMarker(content: String?): List<MessageAttachment>? {
        val marker = attachedFilesMarker(content) ?: return null
        val fallbackDirectory = marker.references
            .firstOrNull { it.contains('/') || it.contains('\\') }
            ?.parentPath()
            ?.takeIf { it.isNotBlank() }

        val attachments = marker.references.map { reference ->
            val name = reference.lastPathComponent()
            val path = when {
                reference.contains('/') || reference.contains('\\') -> reference
                reference.isImageReference() && !fallbackDirectory.isNullOrBlank() -> "$fallbackDirectory/$name"
                else -> null
            }
            MessageAttachment(
                name = name,
                path = path,
                isImage = reference.isImageReference(),
            )
        }

        return attachments.takeIf { it.isNotEmpty() }
    }

    private data class AttachedFilesMarker(
        val references: List<String>,
    )

    private fun attachedFilesMarker(content: String?): AttachedFilesMarker? {
        val value = content ?: return null
        val markerStart = value.lastIndexOf("[Attached files:")
        if (markerStart < 0) return null
        val close = value.indexOf(']', startIndex = markerStart)
        if (close < 0) return null
        if (value.substring(close + 1).trim().isNotEmpty()) return null
        val references = value
            .substring(markerStart + "[Attached files:".length, close)
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return references.takeIf { it.isNotEmpty() }?.let(::AttachedFilesMarker)
    }
}

private val MessageAttachment.identityKey: String?
    get() {
        val raw = listOf(name, path)
            .mapNotNull { it.nonBlank() }
            .firstOrNull()
            ?: return null
        return raw.lastPathComponent().lowercase().takeIf { it.isNotBlank() }
    }

private fun String?.nonBlank(): String? =
    this?.trim()?.takeIf { it.isNotBlank() }

private fun String.lastPathComponent(): String {
    val normalized = replace('\\', '/').trimEnd('/')
    return normalized.substringAfterLast('/', normalized).takeIf { it.isNotBlank() } ?: this
}

private fun String.parentPath(): String? {
    val normalized = replace('\\', '/').trimEnd('/')
    val index = normalized.lastIndexOf('/')
    return if (index > 0) normalized.substring(0, index) else null
}

private fun String.isImageReference(): Boolean =
    lastPathComponent()
        .substringAfterLast('.', missingDelimiterValue = "")
        .lowercase() in imageReferenceExtensions

private val imageReferenceExtensions = setOf(
    "jpg",
    "jpeg",
    "png",
    "gif",
    "webp",
    "heic",
    "heif",
    "bmp",
    "tiff",
    "tif",
)

@Serializable(with = ReasoningSegmentSerializer::class)
data class ReasoningSegment(
    val text: String? = null,
)

object ReasoningSegmentSerializer : KSerializer<ReasoningSegment> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ReasoningSegment") {
        element<String?>("text")
    }

    override fun deserialize(decoder: Decoder): ReasoningSegment {
        val jsonDecoder = decoder as? JsonDecoder ?: return ReasoningSegment()
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> ReasoningSegment(text = element.contentOrNull)
            is JsonObject -> ReasoningSegment(
                text = element["text"]?.jsonPrimitive?.contentOrNull
                    ?: element["content"]?.jsonPrimitive?.contentOrNull
                    ?: element["summary"]?.jsonPrimitive?.contentOrNull,
            )
            else -> ReasoningSegment()
        }
    }

    override fun serialize(encoder: Encoder, value: ReasoningSegment) {
        val jsonEncoder = encoder as? JsonEncoder ?: return
        jsonEncoder.encodeJsonElement(buildJsonObject {
            value.text?.let { put("text", it) }
        })
    }
}

@Serializable(with = MessageAttachmentSerializer::class)
data class MessageAttachment(
    val name: String? = null,
    val path: String? = null,
    val mime: String? = null,
    val size: Int? = null,
    val isImage: Boolean? = null,
)

object MessageAttachmentSerializer : KSerializer<MessageAttachment> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("MessageAttachment") {
        element<String?>("name")
        element<String?>("path")
        element<String?>("mime")
        element<Int?>("size")
        element<Boolean?>("isImage")
    }

    override fun deserialize(decoder: Decoder): MessageAttachment {
        val jsonDecoder = decoder as? JsonDecoder ?: return MessageAttachment()
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> MessageAttachment(name = element.contentOrNull)
            is JsonObject -> MessageAttachment(
                name = element["name"]?.jsonPrimitive?.contentOrNull
                    ?: element["filename"]?.jsonPrimitive?.contentOrNull,
                path = element["path"]?.jsonPrimitive?.contentOrNull,
                mime = element["mime"]?.jsonPrimitive?.contentOrNull,
                size = element["size"]?.jsonPrimitive?.intOrNull,
                isImage = element["isImage"]?.jsonPrimitive?.booleanOrNull
                    ?: element["is_image"]?.jsonPrimitive?.booleanOrNull,
            )
            else -> MessageAttachment()
        }
    }

    override fun serialize(encoder: Encoder, value: MessageAttachment) {
        val jsonEncoder = encoder as? JsonEncoder ?: return
        jsonEncoder.encodeJsonElement(
            buildJsonObject {
                value.name?.let { put("name", it) }
                value.path?.let { put("path", it) }
                value.mime?.let { put("mime", it) }
                value.size?.let { put("size", it) }
                value.isImage?.let { put("isImage", it) }
            },
        )
    }
}

@Serializable
data class ToolCall(
    val id: String? = null,
    val name: String? = null,
    val preview: String? = null,
    val args: Map<String, JsonElement>? = null,
    val result: JsonElement? = null,
    @SerialName("is_error") val isError: Boolean? = null,
)

@Serializable
data class PersistedToolCall(
    val name: String? = null,
    val snippet: String? = null,
    val tid: String? = null,
    @SerialName("assistant_msg_idx") val assistantMsgIdx: Int? = null,
    val args: Map<String, JsonElement>? = null,
) {
    fun toToolCall(fallbackIndex: Int): ToolCall =
        ToolCall(
            id = tid?.trim()?.takeIf { it.isNotEmpty() } ?: "persisted-tool-$fallbackIndex",
            name = name,
            preview = snippet,
            args = args,
            result = null,
            isError = null,
        )
}

data class ToolCallGroup(
    val afterMessageIndex: Int,
    val tools: List<ToolCall>,
)

object ToolCallGroupResolver {
    fun groups(
        messages: List<ChatMessage>,
        messagesOffset: Int,
        persistedToolCalls: List<PersistedToolCall>?,
    ): List<ToolCallGroup> {
        if (messages.isEmpty() || persistedToolCalls.isNullOrEmpty()) return emptyList()

        val grouped = linkedMapOf<Int, MutableList<ToolCall>>()
        persistedToolCalls.forEachIndexed { index, persistedToolCall ->
            val assistantMsgIdx = persistedToolCall.assistantMsgIdx ?: return@forEachIndexed
            val loadedIndex = assistantMsgIdx - messagesOffset.coerceAtLeast(0)
            if (loadedIndex !in messages.indices) return@forEachIndexed
            val anchorIndex = assistantAnchorIndexForRawIndex(loadedIndex, messages) ?: return@forEachIndexed
            grouped.getOrPut(anchorIndex) { mutableListOf() }.add(persistedToolCall.toToolCall(index))
        }

        return grouped
            .toSortedMap()
            .map { (index, tools) -> ToolCallGroup(afterMessageIndex = index, tools = uniqueToolCalls(tools)) }
    }

    private fun assistantAnchorIndexForRawIndex(rawIndex: Int, messages: List<ChatMessage>): Int? {
        if (messages[rawIndex].role == "assistant") return rawIndex

        val lowerBound = previousUserBoundaryIndex(rawIndex, messages)?.plus(1) ?: messages.indices.first
        if (rawIndex > lowerBound) {
            for (index in rawIndex - 1 downTo lowerBound) {
                if (messages[index].role == "assistant") return index
            }
        }

        val upperBound = nextUserBoundaryIndex(rawIndex, messages) ?: messages.size
        if (rawIndex + 1 < upperBound) {
            for (index in rawIndex + 1 until upperBound) {
                if (messages[index].role == "assistant") return index
            }
        }

        return null
    }

    private fun previousUserBoundaryIndex(before: Int, messages: List<ChatMessage>): Int? =
        (before - 1 downTo 0).firstOrNull { messages[it].isUserTurnBoundary }

    private fun nextUserBoundaryIndex(after: Int, messages: List<ChatMessage>): Int? =
        (after + 1 until messages.size).firstOrNull { messages[it].isUserTurnBoundary }

    private val ChatMessage.isUserTurnBoundary: Boolean
        get() = role == "user" && (displayText.isNotBlank() || attachments?.isNotEmpty() == true)

    private fun uniqueToolCalls(tools: List<ToolCall>): List<ToolCall> {
        val seen = linkedMapOf<String, ToolCall>()
        tools.forEachIndexed { index, tool ->
            val key = tool.id?.takeUnless { it.startsWith("persisted-tool-") }
                ?: "${tool.name.orEmpty().trim()}:${tool.args?.toString().orEmpty()}:$index"
            seen[key] = tool
        }
        return seen.values.toList()
    }
}

@Serializable
data class ProjectsResponse(
    val projects: List<ProjectSummary>? = null,
)

@Serializable
data class ProjectMutationResponse(
    val ok: Boolean? = null,
    val project: ProjectSummary? = null,
    val error: String? = null,
)

@Serializable
data class ProjectSummary(
    @SerialName("project_id") val projectId: String? = null,
    val name: String? = null,
    val color: String? = null,
    @SerialName("created_at") val createdAt: Double? = null,
) {
    val stableId: String get() = projectId ?: name ?: "project"
}

@Serializable
data class ChatStartResponse(
    @SerialName("stream_id") val streamId: String? = null,
    @SerialName("session_id") val sessionId: String? = null,
    val error: String? = null,
)

@Serializable
data class ChatSteerResponse(
    val accepted: Boolean? = null,
    val fallback: String? = null,
    @SerialName("stream_id") val streamId: String? = null,
    val error: String? = null,
)

@Serializable
data class BtwStartResponse(
    @SerialName("stream_id") val streamId: String? = null,
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("parent_session_id") val parentSessionId: String? = null,
    val error: String? = null,
)

@Serializable
data class BackgroundStartResponse(
    @SerialName("task_id") val taskId: String? = null,
    @SerialName("stream_id") val streamId: String? = null,
    @SerialName("session_id") val sessionId: String? = null,
    val error: String? = null,
)

@Serializable
data class BackgroundStatusResponse(
    val results: List<BackgroundResult>? = null,
)

@Serializable
data class BackgroundResult(
    @SerialName("task_id") val taskId: String? = null,
    val prompt: String? = null,
    val answer: String? = null,
    @SerialName("completed_at") val completedAt: Double? = null,
)

@Serializable
data class GoalSubmissionResponse(
    val ok: Boolean? = null,
    val action: String? = null,
    val message: String? = null,
    val goal: SubmittedGoal? = null,
    @SerialName("kickoff_prompt") val kickoffPrompt: String? = null,
    val decision: GoalDecision? = null,
    val error: String? = null,
)

@Serializable
data class SubmittedGoal(
    val goal: String? = null,
    val status: String? = null,
    @SerialName("turns_used") val turnsUsed: Int? = null,
    @SerialName("max_turns") val maxTurns: Int? = null,
    @SerialName("last_verdict") val lastVerdict: String? = null,
    @SerialName("last_reason") val lastReason: String? = null,
    @SerialName("paused_reason") val pausedReason: String? = null,
)

@Serializable
data class GoalDecision(
    val status: String? = null,
    @SerialName("should_continue") val shouldContinue: Boolean? = null,
    @SerialName("continuation_prompt") val continuationPrompt: String? = null,
    val verdict: String? = null,
    val reason: String? = null,
    val message: String? = null,
    @SerialName("message_key") val messageKey: String? = null,
    @SerialName("message_args") val messageArgs: List<JsonElement>? = null,
)

@Serializable
enum class ApprovalChoice {
    @SerialName("once")
    Once,

    @SerialName("session")
    Session,

    @SerialName("always")
    Always,

    @SerialName("deny")
    Deny,
}

@Serializable
data class ApprovalPendingResponse(
    val pending: PendingApproval? = null,
    val pendingCount: Int? = null,
    @SerialName("pending_count") val pendingCountSnake: Int? = null,
) {
    val displayPendingCount: Int get() = maxOf(pendingCount ?: pendingCountSnake ?: 1, 1)
}

@Serializable
data class PendingApproval(
    val approvalId: String? = null,
    @SerialName("approval_id") val approvalIdSnake: String? = null,
    val command: String? = null,
    val description: String? = null,
    val patternKey: String? = null,
    @SerialName("pattern_key") val patternKeySnake: String? = null,
    val patternKeys: List<String>? = null,
    @SerialName("pattern_keys") val patternKeysSnake: List<String>? = null,
) {
    val stableId: String
        get() = normalizedApprovalId ?: "${command.orEmpty()}-${description.orEmpty()}-${displayPatternKeys.joinToString(",")}"
    val normalizedApprovalId: String? get() = approvalId ?: approvalIdSnake
    val displayPatternKeys: List<String>
        get() = (patternKeys ?: patternKeysSnake)
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.takeIf { it.isNotEmpty() }
            ?: listOfNotNull(patternKey ?: patternKeySnake).map { it.trim() }.filter { it.isNotEmpty() }
    val isEmpty: Boolean
        get() = normalizedApprovalId == null &&
            command == null &&
            description == null &&
            (patternKey ?: patternKeySnake) == null &&
            (patternKeys ?: patternKeysSnake).isNullOrEmpty()
}

@Serializable
data class ApprovalRespondResponse(
    val ok: Boolean? = null,
    val choice: ApprovalChoice? = null,
    val staleCleared: Boolean? = null,
    @SerialName("stale_cleared") val staleClearedSnake: Boolean? = null,
    val relayed: Boolean? = null,
    val stale: Boolean? = null,
)

@Serializable
data class SessionYoloResponse(
    val ok: Boolean? = null,
    val yoloEnabled: Boolean? = null,
    @SerialName("yolo_enabled") val yoloEnabledSnake: Boolean? = null,
    val error: String? = null,
) {
    val isEnabled: Boolean get() = yoloEnabled ?: yoloEnabledSnake ?: false
}

@Serializable
data class ClarificationPendingResponse(
    val pending: PendingClarification? = null,
    val pendingCount: Int? = null,
    @SerialName("pending_count") val pendingCountSnake: Int? = null,
) {
    val displayPendingCount: Int get() = maxOf(pendingCount ?: pendingCountSnake ?: 1, 1)
}

@Serializable
data class PendingClarification(
    val clarifyId: String? = null,
    @SerialName("clarify_id") val clarifyIdSnake: String? = null,
    val question: String? = null,
    val choicesOffered: List<String>? = null,
    @SerialName("choices_offered") val choicesOfferedSnake: List<String>? = null,
    val sessionId: String? = null,
    @SerialName("session_id") val sessionIdSnake: String? = null,
    val kind: String? = null,
    val requestedAt: Double? = null,
    @SerialName("requested_at") val requestedAtSnake: Double? = null,
    val timeoutSeconds: Int? = null,
    @SerialName("timeout_seconds") val timeoutSecondsSnake: Int? = null,
    val expiresAt: Double? = null,
    @SerialName("expires_at") val expiresAtSnake: Double? = null,
) {
    val stableId: String
        get() = normalizedClarifyId ?: "${sessionId ?: sessionIdSnake}-${question.orEmpty()}-${requestedAt ?: requestedAtSnake ?: 0.0}"
    val normalizedClarifyId: String? get() = clarifyId ?: clarifyIdSnake
    val displayQuestion: String
        get() = question?.trim()?.takeIf { it.isNotEmpty() } ?: "The agent needs more information before continuing."
    val displayChoices: List<String>
        get() = (choicesOffered ?: choicesOfferedSnake).orEmpty().map { it.trim() }.filter { it.isNotEmpty() }
    val isEmpty: Boolean
        get() = normalizedClarifyId == null &&
            question == null &&
            displayChoices.isEmpty() &&
            (sessionId ?: sessionIdSnake) == null &&
            kind == null &&
            (requestedAt ?: requestedAtSnake) == null &&
            (timeoutSeconds ?: timeoutSecondsSnake) == null &&
            (expiresAt ?: expiresAtSnake) == null
}

@Serializable
data class ClarificationRespondResponse(
    val ok: Boolean? = null,
    val response: String? = null,
    val stale: Boolean? = null,
    val staleCleared: Boolean? = null,
    @SerialName("stale_cleared") val staleClearedSnake: Boolean? = null,
    val relayed: Boolean? = null,
)

@Serializable
data class UploadResponse(
    val filename: String? = null,
    val path: String? = null,
    val mime: String? = null,
    val size: Long? = null,
    @SerialName("is_image") val isImage: Boolean? = null,
    val error: String? = null,
)

@Serializable
data class TranscribeResponse(
    val ok: Boolean? = null,
    val transcript: String? = null,
    val error: String? = null,
)

@Serializable(with = WorkspaceRootSerializer::class)
data class WorkspaceRoot(
    val path: String? = null,
    val name: String? = null,
    val exists: Boolean? = null,
)

@Serializable
data class WorkspacesResponse(
    val workspaces: List<WorkspaceRoot>? = null,
    val roots: List<WorkspaceRoot>? = null,
    val last: String? = null,
)

@Serializable
data class WorkspaceSuggestionsResponse(
    val suggestions: List<String>? = null,
    val prefix: String? = null,
)

object WorkspaceRootSerializer : KSerializer<WorkspaceRoot> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("WorkspaceRoot") {
        element<String?>("path")
        element<String?>("name")
        element<Boolean?>("exists")
    }

    override fun deserialize(decoder: Decoder): WorkspaceRoot {
        val jsonDecoder = decoder as? JsonDecoder ?: return WorkspaceRoot()
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> WorkspaceRoot(path = element.contentOrNull)
            is JsonObject -> WorkspaceRoot(
                path = element["path"]?.jsonPrimitive?.contentOrNull,
                name = element["name"]?.jsonPrimitive?.contentOrNull,
                exists = element["exists"]?.jsonPrimitive?.booleanOrNull,
            )
            else -> WorkspaceRoot()
        }
    }

    override fun serialize(encoder: Encoder, value: WorkspaceRoot) {
        val jsonEncoder = encoder as? JsonEncoder ?: return
        jsonEncoder.encodeJsonElement(
            buildJsonObject {
                value.path?.let { put("path", it) }
                value.name?.let { put("name", it) }
                value.exists?.let { put("exists", it) }
            },
        )
    }
}

@Serializable
data class DirectoryListResponse(
    val path: String? = null,
    val entries: List<WorkspaceEntry>? = null,
    val error: String? = null,
)

@Serializable
data class WorkspaceEntry(
    val name: String? = null,
    val path: String? = null,
    val type: String? = null,
    val size: Long? = null,
    @SerialName("modified_at") val modifiedAt: Double? = null,
)

@Serializable
data class FileResponse(
    val path: String? = null,
    val content: String? = null,
    val encoding: String? = null,
    val language: String? = null,
    val size: Long? = null,
    val error: String? = null,
)

@Serializable
data class ModelCatalogResponse(
    val models: List<ModelSummary>? = null,
    val providers: List<ProviderSummary>? = null,
    @SerialName("default_model") val defaultModel: String? = null,
)

@Serializable
data class CommandsResponse(
    val commands: List<AgentCommand>? = null,
)

@Serializable
data class AgentCommand(
    val name: String? = null,
    val description: String? = null,
    val category: String? = null,
    val aliases: List<String>? = null,
    val argsHint: String? = null,
    @SerialName("args_hint") val argsHintSnake: String? = null,
    val subcommands: List<String>? = null,
    val cliOnly: Boolean? = null,
    @SerialName("cli_only") val cliOnlySnake: Boolean? = null,
    val gatewayOnly: Boolean? = null,
    @SerialName("gateway_only") val gatewayOnlySnake: Boolean? = null,
) {
    val displayName: String? get() = name?.trim()?.takeIf { it.isNotEmpty() }
    val displayDescription: String? get() = description?.trim()?.takeIf { it.isNotEmpty() }
    val displayArgsHint: String? get() = (argsHint ?: argsHintSnake)?.trim()?.takeIf { it.isNotEmpty() }
    val isMobileVisible: Boolean get() = cliOnly != true && cliOnlySnake != true && gatewayOnly != true && gatewayOnlySnake != true && displayName != null
}

@Serializable
data class ModelsLiveResponse(
    val provider: String? = null,
    val models: List<ModelSummary>? = null,
    val count: Int? = null,
)

@Serializable
data class ModelSummary(
    val id: String? = null,
    val name: String? = null,
    val provider: String? = null,
    val label: String? = null,
)

@Serializable
data class ProviderSummary(
    val id: String? = null,
    val name: String? = null,
    val configured: Boolean? = null,
)

@Serializable
data class ProfilesResponse(
    val profiles: List<ProfileSummary>? = null,
    val active: String? = null,
    @SerialName("single_profile_mode") val singleProfileMode: Boolean? = null,
)

@Serializable
data class ProfileSummary(
    val name: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    val path: String? = null,
    @SerialName("is_default") val isDefault: Boolean? = null,
    @SerialName("is_active") val isActive: Boolean? = null,
    @SerialName("gateway_running") val gatewayRunning: Boolean? = null,
    val model: String? = null,
    val provider: String? = null,
    @SerialName("has_env") val hasEnv: Boolean? = null,
    @SerialName("skill_count") val skillCount: Int? = null,
)

@Serializable
data class ProfileSwitchResponse(
    val profiles: List<ProfileSummary>? = null,
    val active: String? = null,
    @SerialName("default_model") val defaultModel: String? = null,
    @SerialName("default_workspace") val defaultWorkspace: String? = null,
    val error: String? = null,
)

@Serializable
data class ProfileCreateResponse(
    val ok: Boolean? = null,
    val profile: ProfileSummary? = null,
    val error: String? = null,
)

@Serializable
data class ReasoningResponse(
    val effort: String? = null,
    @SerialName("supported_efforts") val supportedEfforts: List<String>? = null,
    @SerialName("supports_reasoning_effort") val supportsReasoningEffort: Boolean? = null,
)

@Serializable
data class SettingsResponse(
    @SerialName("webui_version") val webuiVersion: String? = null,
    @SerialName("bot_name") val botName: String? = null,
    val theme: String? = null,
    @SerialName("show_cli_sessions") val showCliSessions: Boolean? = null,
)

@Serializable
data class UpdatesCheckResponse(
    val webui: UpdateTargetInfo? = null,
    val agent: UpdateTargetInfo? = null,
    @SerialName("checked_at") val checkedAt: Double? = null,
    val disabled: Boolean? = null,
)

@Serializable
data class UpdateTargetInfo(
    val name: String? = null,
    val behind: Int? = null,
    @SerialName("current_sha") val currentSha: String? = null,
    @SerialName("latest_sha") val latestSha: String? = null,
    val branch: String? = null,
    @SerialName("repo_url") val repoUrl: String? = null,
    @SerialName("compare_url") val compareUrl: String? = null,
    val error: String? = null,
    @SerialName("stale_check") val staleCheck: Boolean? = null,
)

@Serializable
data class UpdatesApplyResponse(
    val ok: Boolean? = null,
    val message: String? = null,
    val target: String? = null,
    val conflict: Boolean? = null,
    val diverged: Boolean? = null,
    @SerialName("restart_blocked") val restartBlocked: Boolean? = null,
    @SerialName("restart_scheduled") val restartScheduled: Boolean? = null,
    @SerialName("stash_conflict") val stashConflict: Boolean? = null,
    @SerialName("active_streams") val activeStreams: Int? = null,
    @SerialName("active_runs") val activeRuns: Int? = null,
)

@Serializable
data class DefaultModelResponse(
    val ok: Boolean? = null,
    val model: String? = null,
    val error: String? = null,
)

@Serializable
data class CronsResponse(
    val crons: List<CronJob>? = null,
    val jobs: List<CronJob>? = null,
)

@Serializable
data class CronJob(
    @SerialName("job_id") val jobId: String? = null,
    val id: String? = null,
    val name: String? = null,
    val prompt: String? = null,
    val command: String? = null,
    val schedule: CronSchedule? = null,
    @SerialName("schedule_display") val scheduleDisplay: String? = null,
    val enabled: Boolean? = null,
    val state: String? = null,
    val paused: Boolean? = null,
    val running: Boolean? = null,
    @SerialName("next_run_at") val nextRunAt: CronDateValue? = null,
    @SerialName("last_run_at") val lastRunAt: CronDateValue? = null,
    @SerialName("last_status") val lastStatus: String? = null,
    @SerialName("last_error") val lastError: String? = null,
    @SerialName("last_delivery_error") val lastDeliveryError: String? = null,
    val repeat: CronRepeat? = null,
    val deliver: String? = null,
    val skills: List<String>? = null,
    val model: String? = null,
    val profile: String? = null,
    @SerialName("toast_notifications") val toastNotifications: Boolean? = null,
)

@Serializable(with = CronScheduleSerializer::class)
data class CronSchedule(
    val kind: String? = null,
    val expression: String? = null,
    val expr: String? = null,
    val runAt: String? = null,
    val every: String? = null,
) {
    val displayText: String?
        get() = expression ?: expr ?: runAt ?: every ?: kind
}

object CronScheduleSerializer : KSerializer<CronSchedule> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("CronSchedule") {
        element<String?>("kind")
        element<String?>("expression")
        element<String?>("expr")
        element<String?>("runAt")
        element<String?>("every")
    }

    override fun deserialize(decoder: Decoder): CronSchedule {
        val jsonDecoder = decoder as? JsonDecoder ?: return CronSchedule()
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> CronSchedule(expression = element.contentOrNull)
            is JsonObject -> CronSchedule(
                kind = element["kind"].stringOrNull(),
                expression = element["expression"].stringOrNull(),
                expr = element["expr"].stringOrNull(),
                runAt = element["run_at"].stringOrNull() ?: element["runAt"].stringOrNull(),
                every = element["every"].stringOrNull(),
            )
            else -> CronSchedule()
        }
    }

    override fun serialize(encoder: Encoder, value: CronSchedule) {
        val jsonEncoder = encoder as? JsonEncoder ?: return
        jsonEncoder.encodeJsonElement(
            buildJsonObject {
                value.kind?.let { put("kind", it) }
                value.expression?.let { put("expression", it) }
                value.expr?.let { put("expr", it) }
                value.runAt?.let { put("run_at", it) }
                value.every?.let { put("every", it) }
            },
        )
    }
}

@Serializable(with = CronDateValueSerializer::class)
data class CronDateValue(
    val epochSeconds: Double? = null,
    val raw: String? = null,
)

object CronDateValueSerializer : KSerializer<CronDateValue> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("CronDateValue") {
        element<Double?>("epochSeconds")
        element<String?>("raw")
    }

    override fun deserialize(decoder: Decoder): CronDateValue {
        val jsonDecoder = decoder as? JsonDecoder ?: return CronDateValue()
        return cronDateValue(jsonDecoder.decodeJsonElement()) ?: CronDateValue()
    }

    override fun serialize(encoder: Encoder, value: CronDateValue) {
        val jsonEncoder = encoder as? JsonEncoder ?: return
        val element = value.epochSeconds?.let(::JsonPrimitive)
            ?: value.raw?.let(::JsonPrimitive)
            ?: JsonNull
        jsonEncoder.encodeJsonElement(element)
    }
}

@Serializable(with = CronRepeatSerializer::class)
data class CronRepeat(
    val times: Int? = null,
    val completed: Int? = null,
)

object CronRepeatSerializer : KSerializer<CronRepeat> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("CronRepeat") {
        element<Int?>("times")
        element<Int?>("completed")
    }

    override fun deserialize(decoder: Decoder): CronRepeat {
        val jsonDecoder = decoder as? JsonDecoder ?: return CronRepeat()
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonObject -> CronRepeat(
                times = element["times"].intValueOrNull(),
                completed = element["completed"].intValueOrNull(),
            )
            else -> CronRepeat()
        }
    }

    override fun serialize(encoder: Encoder, value: CronRepeat) {
        val jsonEncoder = encoder as? JsonEncoder ?: return
        jsonEncoder.encodeJsonElement(
            buildJsonObject {
                value.times?.let { put("times", it) }
                value.completed?.let { put("completed", it) }
            },
        )
    }
}

private fun cronDateValue(element: JsonElement): CronDateValue? =
    when (element) {
        is JsonPrimitive -> {
            val number = element.doubleOrNull
            if (number != null) {
                CronDateValue(epochSeconds = number)
            } else {
                val raw = element.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
                val parsed = raw?.toDoubleOrNull() ?: raw?.let { runCatching { Instant.parse(it) }.getOrNull()?.toEpochSecondWithFraction() }
                CronDateValue(epochSeconds = parsed, raw = raw)
            }
        }
        is JsonObject -> listOf("date", "timestamp", "value", "time")
            .firstNotNullOfOrNull { key -> element[key]?.let(::cronDateValue) }
        else -> null
    }

private fun Instant.toEpochSecondWithFraction(): Double =
    epochSecond.toDouble() + nano.toDouble() / 1_000_000_000.0

private fun JsonElement?.stringOrNull(): String? =
    (this as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

private fun JsonElement?.doubleValueOrNull(): Double? {
    val primitive = this as? JsonPrimitive ?: return null
    return primitive.doubleOrNull ?: primitive.contentOrNull?.trim()?.toDoubleOrNull()
}

private fun JsonElement?.intValueOrNull(): Int? {
    val primitive = this as? JsonPrimitive ?: return null
    return primitive.intOrNull ?: primitive.contentOrNull?.trim()?.toIntOrNull()
}

@Serializable
data class CronMutationResponse(
    val ok: Boolean? = null,
    val job: CronJob? = null,
    val error: String? = null,
)

@Serializable
data class CronStatusResponse(
    @SerialName("job_id") val jobId: String? = null,
    val running: JsonElement? = null,
    val elapsed: Double? = null,
    @SerialName("running_jobs") val runningJobs: Map<String, Double>? = null,
    val error: String? = null,
) {
    val runningJobDurations: Map<String, Double>
        get() = runningJobs ?: running.cronRunningDurations()
}

private fun JsonElement?.cronRunningDurations(): Map<String, Double> {
    val obj = this as? JsonObject ?: return emptyMap()
    return obj.mapNotNull { (key, value) ->
        val primitive = value as? JsonPrimitive
        val elapsed = primitive?.doubleOrNull ?: primitive?.contentOrNull?.trim()?.toDoubleOrNull()
        elapsed?.let { key to it }
    }.toMap()
}

@Serializable
data class CronOutputResponse(
    @SerialName("job_id") val jobId: String? = null,
    val outputs: List<CronOutputItem>? = null,
    val error: String? = null,
)

@Serializable
data class CronOutputItem(
    val filename: String? = null,
    val content: String? = null,
)

@Serializable
data class CronHistoryResponse(
    @SerialName("job_id") val jobId: String? = null,
    val runs: List<CronRunSummary>? = null,
    val total: Int? = null,
    val offset: Int? = null,
    val error: String? = null,
)

@Serializable
data class CronRunSummary(
    val filename: String? = null,
    val size: Int? = null,
    val modified: Double? = null,
    val usage: CronRunUsage? = null,
)

@Serializable
data class CronRunUsage(
    val model: String? = null,
    val provider: String? = null,
    @SerialName("estimated_cost_usd") val estimatedCostUsd: Double? = null,
    @SerialName("duration_seconds") val durationSeconds: Double? = null,
    @SerialName("input_tokens") val inputTokens: Int? = null,
    @SerialName("output_tokens") val outputTokens: Int? = null,
    @SerialName("total_tokens") val totalTokens: Int? = null,
)

@Serializable
data class CronDeliveryOptionsResponse(
    val platforms: List<CronDeliveryPlatform>? = null,
    val error: String? = null,
)

@Serializable
data class CronDeliveryPlatform(
    val value: String? = null,
    val label: String? = null,
)

@Serializable
data class SkillsResponse(
    val skills: List<SkillSummary>? = null,
)

@Serializable
data class SkillSummary(
    val name: String? = null,
    val description: String? = null,
    val category: String? = null,
    val path: String? = null,
    val enabled: Boolean? = null,
    val disabled: Boolean? = null,
    val tags: List<String>? = null,
    @SerialName("related_skills") val relatedSkills: List<String>? = null,
)

@Serializable
data class SkillContentResponse(
    val name: String? = null,
    val content: String? = null,
    val files: List<String>? = null,
    @SerialName("linked_files") val linkedFiles: JsonElement? = null,
    val error: String? = null,
)

@Serializable
data class ToggleSkillResponse(
    val ok: Boolean? = null,
    val name: String? = null,
    val enabled: Boolean? = null,
    val error: String? = null,
)

@Serializable
data class PersonalitiesResponse(
    val personalities: List<PersonalitySummary>? = null,
)

@Serializable
data class PersonalitySummary(
    val name: String? = null,
    val description: String? = null,
)

@Serializable
data class PersonalitySetResponse(
    val ok: Boolean? = null,
    val personality: String? = null,
    val prompt: String? = null,
    val error: String? = null,
)

@Serializable
data class MemoryResponse(
    val memory: JsonElement? = null,
    val user: String? = null,
    val soul: String? = null,
    val notes: List<String>? = null,
    @SerialName("user_profile") val userProfile: JsonElement? = null,
    @SerialName("memory_path") val memoryPath: String? = null,
    @SerialName("memory_mtime") val memoryMtime: Double? = null,
    @SerialName("user_path") val userPath: String? = null,
    @SerialName("user_mtime") val userMtime: Double? = null,
    @SerialName("soul_path") val soulPath: String? = null,
    @SerialName("soul_mtime") val soulMtime: Double? = null,
    @SerialName("project_context") val projectContext: String? = null,
    @SerialName("project_context_name") val projectContextName: String? = null,
    @SerialName("project_context_path") val projectContextPath: String? = null,
    @SerialName("project_context_mtime") val projectContextMtime: Double? = null,
    @SerialName("project_context_workspace") val projectContextWorkspace: String? = null,
    @Serializable(with = FlexibleBooleanOrNonEmptyArraySerializer::class)
    @SerialName("project_context_shadowed") val projectContextShadowed: Boolean? = null,
    @SerialName("external_notes_enabled") val externalNotesEnabled: Boolean? = null,
)

object FlexibleBooleanOrNonEmptyArraySerializer : KSerializer<Boolean?> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("FlexibleBooleanOrNonEmptyArray") {
        element<Boolean?>("value")
    }

    override fun deserialize(decoder: Decoder): Boolean? {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeBoolean()
        return when (val element = jsonDecoder.decodeJsonElement()) {
            JsonNull -> null
            is JsonPrimitive -> element.booleanOrNull
            is JsonArray -> element.isNotEmpty()
            is JsonObject -> element.isNotEmpty()
        }
    }

    override fun serialize(encoder: Encoder, value: Boolean?) {
        val jsonEncoder = encoder as? JsonEncoder
        if (jsonEncoder != null) {
            jsonEncoder.encodeJsonElement(value?.let(::JsonPrimitive) ?: JsonNull)
        } else {
            encoder.encodeBoolean(value == true)
        }
    }
}

@Serializable
data class MemoryWriteResponse(
    val ok: Boolean? = null,
    val section: String? = null,
    val path: String? = null,
    val error: String? = null,
)

@Serializable
data class InsightsResponse(
    @SerialName("period_days") val periodDays: Int? = null,
    @SerialName("total_sessions") val totalSessions: Int? = null,
    @SerialName("total_messages") val totalMessages: Int? = null,
    @SerialName("total_input_tokens") val totalInputTokens: Int? = null,
    @SerialName("total_output_tokens") val totalOutputTokens: Int? = null,
    @SerialName("total_tokens") val totalTokens: Int? = null,
    @SerialName("total_cost") val totalCost: Double? = null,
    @SerialName("total_cache_read_tokens") val totalCacheReadTokens: Int? = null,
    @SerialName("total_cache_hit_percent") val totalCacheHitPercent: Double? = null,
    val models: List<InsightsModelBreakdown>? = null,
    @SerialName("daily_tokens") val dailyTokens: List<InsightsDailyToken>? = null,
    @SerialName("activity_by_day") val activityByDay: List<InsightsActivityByDay>? = null,
    @SerialName("activity_by_hour") val activityByHour: List<InsightsActivityByHour>? = null,
)

@Serializable
data class InsightsModelBreakdown(
    val model: String? = null,
    val sessions: Int? = null,
    @SerialName("input_tokens") val inputTokens: Int? = null,
    @SerialName("output_tokens") val outputTokens: Int? = null,
    @SerialName("total_tokens") val totalTokens: Int? = null,
    val cost: Double? = null,
    @SerialName("cache_hit_percent") val cacheHitPercent: Double? = null,
    @SerialName("session_share") val sessionShare: Int? = null,
    @SerialName("token_share") val tokenShare: Int? = null,
    @SerialName("cost_share") val costShare: Int? = null,
)

@Serializable
data class InsightsDailyToken(
    val date: String? = null,
    @SerialName("input_tokens") val inputTokens: Int? = null,
    @SerialName("output_tokens") val outputTokens: Int? = null,
    val sessions: Int? = null,
    val cost: Double? = null,
)

@Serializable
data class InsightsActivityByDay(
    val day: String? = null,
    val sessions: Int? = null,
)

@Serializable
data class InsightsActivityByHour(
    val hour: Int? = null,
    val sessions: Int? = null,
)

@Serializable
data class GitInfoResponse(
    val ok: Boolean? = null,
    val repo: String? = null,
    val branch: String? = null,
    val error: String? = null,
)

@Serializable
data class GitStatusResponse(
    val ok: Boolean? = null,
    val files: List<GitFileChange>? = null,
    val branch: String? = null,
    @SerialName("is_git") val isGit: Boolean? = null,
    val truncated: Boolean? = null,
    @SerialName("changed_count") val changedCount: Int? = null,
    @SerialName("total_additions") val totalAdditions: Int? = null,
    @SerialName("total_deletions") val totalDeletions: Int? = null,
    val error: String? = null,
)

@Serializable
data class GitFileChange(
    val path: String? = null,
    @SerialName("workspace_path") val workspacePath: String? = null,
    val status: String? = null,
    val staged: Boolean? = null,
    val additions: Int? = null,
    val deletions: Int? = null,
    val untracked: Boolean? = null,
)

@Serializable
data class GitBranchesResponse(
    val ok: Boolean? = null,
    val branches: GitBranches? = null,
    val current: String? = null,
    val error: String? = null,
)

@Serializable
data class GitBranches(
    @SerialName("is_git") val isGit: Boolean? = null,
    val current: String? = null,
    val detached: Boolean? = null,
    val head: String? = null,
    val local: List<GitBranchRef>? = null,
    val remote: List<GitBranchRef>? = null,
    val upstream: String? = null,
    val ahead: Int? = null,
    val behind: Int? = null,
)

@Serializable
data class GitBranchRef(
    val name: String? = null,
    val sha: String? = null,
    val updated: Int? = null,
    @SerialName("updated_relative") val updatedRelative: String? = null,
    val author: String? = null,
    val subject: String? = null,
    val upstream: String? = null,
    val ahead: Int? = null,
    val behind: Int? = null,
)

@Serializable
data class GitDiffResponse(
    val ok: Boolean? = null,
    val path: String? = null,
    val diff: String? = null,
    val binary: Boolean? = null,
    @SerialName("too_large") val tooLarge: Boolean? = null,
    @SerialName("tooLarge") val camelTooLarge: Boolean? = null,
    val additions: Int? = null,
    val deletions: Int? = null,
    val error: String? = null,
) {
    val isTooLarge: Boolean
        get() = tooLarge == true || camelTooLarge == true
}

@Serializable
data class GitMutationResponse(
    val ok: Boolean? = null,
    val message: String? = null,
    val status: GitStatusResponse? = null,
    val git: GitStatusResponse? = null,
    val error: String? = null,
)

typealias GitRemoteActionResponse = GitMutationResponse

@Serializable
data class GitCheckoutResponse(
    val ok: Boolean? = null,
    val message: String? = null,
    val status: GitStatusResponse? = null,
    val git: GitStatusResponse? = null,
    val branches: GitBranches? = null,
    @SerialName("current_branch") val currentBranch: String? = null,
    @SerialName("stash_name") val stashName: String? = null,
    val stashed: Boolean? = null,
    @SerialName("restored_stash") val restoredStash: GitRestoredStash? = null,
    @SerialName("restore_failed") val restoreFailed: Boolean? = null,
    @SerialName("restore_error") val restoreError: String? = null,
    @SerialName("restore_stash") val restoreStash: GitRestoredStash? = null,
    val error: String? = null,
)

@Serializable
data class GitRestoredStash(
    val ref: String? = null,
    val branch: String? = null,
    val message: String? = null,
)

@Serializable
data class GitCommitResponse(
    val ok: Boolean? = null,
    val sha: String? = null,
    @SerialName("short_sha") val shortSha: String? = null,
    val message: String? = null,
    val status: GitStatusResponse? = null,
    val git: GitStatusResponse? = null,
    val error: String? = null,
)

@Serializable
data class GitCommitMessageResponse(
    val ok: Boolean? = null,
    val message: String? = null,
    val truncated: Boolean? = null,
    val error: String? = null,
)
