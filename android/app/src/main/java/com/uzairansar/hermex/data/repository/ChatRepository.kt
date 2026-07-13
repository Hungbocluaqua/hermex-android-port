package com.uzairansar.hermex.data.repository

import com.uzairansar.hermex.core.model.ChatMessage
import com.uzairansar.hermex.core.model.ChatMessagePageMerger
import com.uzairansar.hermex.core.model.ChatStartRequest
import com.uzairansar.hermex.core.model.CompressionAnchorResolver
import com.uzairansar.hermex.core.model.CompressionReferenceCard
import com.uzairansar.hermex.core.model.ContextWindowSnapshot
import com.uzairansar.hermex.core.model.ApprovalChoice
import com.uzairansar.hermex.core.model.ApprovalPendingResponse
import com.uzairansar.hermex.core.model.ApprovalRespondResponse
import com.uzairansar.hermex.core.model.ClarificationPendingResponse
import com.uzairansar.hermex.core.model.ClarificationRespondResponse
import com.uzairansar.hermex.core.model.FileResponse
import com.uzairansar.hermex.core.model.ModelSummary
import com.uzairansar.hermex.core.model.NewSessionRequest
import com.uzairansar.hermex.core.model.PersonalitySummary
import com.uzairansar.hermex.core.model.ProfilesResponse
import com.uzairansar.hermex.core.model.ProfileSummary
import com.uzairansar.hermex.core.model.ReasoningResponse
import com.uzairansar.hermex.core.model.SessionDetail
import com.uzairansar.hermex.core.model.SessionMutationResponse
import com.uzairansar.hermex.core.model.SessionStatusResponse
import com.uzairansar.hermex.core.model.SessionSummary
import com.uzairansar.hermex.core.model.SkillSummary
import com.uzairansar.hermex.core.model.ToolCallGroup
import com.uzairansar.hermex.core.model.ToolCallGroupResolver
import com.uzairansar.hermex.core.model.TranscribeResponse
import com.uzairansar.hermex.core.model.TranscriptMediaReference
import com.uzairansar.hermex.core.model.UploadResponse
import com.uzairansar.hermex.core.model.WorkspacesResponse
import com.uzairansar.hermex.core.model.compressionAnchorMetadata
import com.uzairansar.hermex.core.model.contextWindowSnapshot
import com.uzairansar.hermex.core.model.hasOlderMessages
import com.uzairansar.hermex.core.model.resolvedMessagesOffset
import com.uzairansar.hermex.core.network.HermesApiClient
import com.uzairansar.hermex.core.network.ApiError
import com.uzairansar.hermex.core.network.SseEvent
import com.uzairansar.hermex.core.network.SseReplayCursor
import com.uzairansar.hermex.core.network.SseStreamClient
import com.uzairansar.hermex.data.db.CacheDao
import com.uzairansar.hermex.data.db.CachedMessageEntity
import com.uzairansar.hermex.data.db.ServerCacheOwnership
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.CancellationException
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private const val MESSAGE_PAGE_LIMIT = 50

data class ChatSessionSnapshot(
    val messages: List<ChatMessage>,
    val messagesOffset: Int = 0,
    val hasOlderMessages: Boolean = false,
    val compressionReferenceCard: CompressionReferenceCard? = null,
    val completedToolCallGroups: List<ToolCallGroup> = emptyList(),
    val contextWindowSnapshot: ContextWindowSnapshot? = null,
    val title: String? = null,
    val workspace: String? = null,
    val model: String? = null,
    val modelProvider: String? = null,
    val profile: String? = null,
    val activeStreamId: String? = null,
    val isStreaming: Boolean = false,
)

data class ChatSessionConfiguration(
    val workspace: String? = null,
    val model: String? = null,
    val modelProvider: String? = null,
)

data class ChatSessionClearResult(
    val snapshot: ChatSessionSnapshot? = null,
    val error: String? = null,
)

class ChatRepository(
    private val client: HermesApiClient,
    private val cacheDao: CacheDao,
    private val cacheOwnership: ServerCacheOwnership,
    private val sse: SseStreamClient,
) {
    val serverUrl: String = client.baseUrl.toString()
    private val streamEventIds = ConcurrentHashMap<String, String>()
    private val streamCacheGenerations = ConcurrentHashMap<String, Long>()

    suspend fun loadSessionSnapshot(sessionId: String): ResultState<ChatSessionSnapshot> {
        val operationGeneration = cacheOwnership.generation(serverUrl)
        val now = System.currentTimeMillis()
        return try {
            val session = client.session(sessionId).session
            val messages = session?.messages.orEmpty()
            replaceCachedMessages(sessionId, messages, now, operationGeneration)
            ResultState.Data(snapshotFromSession(session))
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            if (error is ApiError.Unauthorized) return ResultState.Error(error.userMessage(), error)
            if (error is ApiError.Http && error.statusCode == 404) {
                cacheOwnership.writeIfCurrent(serverUrl, operationGeneration) {
                    cacheDao.purgeSession(serverUrl, sessionId)
                }
                return ResultState.Error(error.userMessage(), error)
            }
            if (!error.isChatCacheFallbackEligible()) return ResultState.Error(error.userMessage(), error)
            val cached = cacheOwnership.readIfCurrent(serverUrl, operationGeneration) {
                cacheDao.cachedMessages(serverUrl, sessionId, now).mapNotNull { it.toMessage() }
            } ?: return ResultState.Error("The active profile changed while this conversation was loading.", error)
            if (cached.isNotEmpty()) {
                ResultState.Data(ChatSessionSnapshot(cached), fromCache = true)
            } else {
                ResultState.Error(error.userMessage(), error)
            }
        }
    }

    suspend fun snapshotFromCompletedSession(
        sessionId: String,
        session: SessionDetail,
        streamId: String? = null,
    ): ChatSessionSnapshot {
        val operationGeneration = streamId?.let(streamCacheGenerations::remove)
            ?: cacheOwnership.generation(serverUrl)
        val messages = session.messages.orEmpty()
        val now = System.currentTimeMillis()
        val resolvedSessionId = session.sessionId?.takeIf { it.isNotBlank() } ?: sessionId
        replaceCachedMessages(resolvedSessionId, messages, now, operationGeneration)
        return snapshotFromSession(session)
    }

    suspend fun send(
        sessionId: String?,
        message: String,
        model: ModelSummary? = null,
        profile: ProfileSummary? = null,
        profileName: String? = null,
        explicitModelPick: Boolean = false,
        attachments: List<UploadResponse> = emptyList(),
        workspace: String? = null,
    ): String? {
        val operationGeneration = cacheOwnership.generation(serverUrl)
        val streamId = client.chatStart(
            ChatStartRequest(
                sessionId = sessionId,
                message = message,
                workspace = workspace,
                model = model?.id ?: model?.name,
                modelProvider = model?.provider,
                profile = profile?.name ?: profile?.displayName ?: profileName,
                explicitModelPick = explicitModelPick,
                attachments = attachments.ifEmpty { null },
            ),
        ).streamId
        streamId?.let { streamCacheGenerations[it] = operationGeneration }
        return streamId
    }

    fun stream(streamId: String, replayAfterSeq: Int? = null): Flow<SseEvent> =
        sse.stream(client.streamUrl(streamId, replayAfterSeq)) { eventId ->
            streamEventIds[streamId] = eventId
        }

    suspend fun cancel(streamId: String) = client.chatCancel(streamId).also {
        streamCacheGenerations.remove(streamId)
    }
    suspend fun chatStreamStatus(streamId: String): SessionStatusResponse = client.chatStreamStatus(streamId)
    fun replayAfterSeq(streamId: String): Int? = SseReplayCursor.afterSeqFromEventId(streamEventIds[streamId])
    fun clearStreamCursor(streamId: String) {
        streamEventIds.remove(streamId)
        streamCacheGenerations.remove(streamId)
    }
    suspend fun steer(sessionId: String, text: String) = client.chatSteer(sessionId, text)
    suspend fun startBtw(sessionId: String, question: String) = client.startBtw(sessionId, question)
    suspend fun startBackground(sessionId: String, prompt: String) = client.startBackground(sessionId, prompt)
    suspend fun backgroundStatus(sessionId: String) = client.backgroundStatus(sessionId)
    suspend fun submitGoal(sessionId: String, args: String, model: ModelSummary?, profile: ProfileSummary?) =
        client.submitGoal(
            sessionId = sessionId,
            args = args,
            model = model?.id ?: model?.name,
            modelProvider = model?.provider,
            profile = profile?.name,
        )
    suspend fun compressSession(sessionId: String, focusTopic: String?) = run {
        val operationGeneration = cacheOwnership.generation(serverUrl)
        client.compressSession(sessionId, focusTopic).also { response ->
            if (response.ok != false && response.error.isNullOrBlank()) {
                response.session?.let { session ->
                    val resolvedSessionId = session.sessionId?.takeIf { it.isNotBlank() } ?: sessionId
                    replaceCachedMessages(resolvedSessionId, session.messages.orEmpty(), generation = operationGeneration)
                }
            }
        }
    }
    suspend fun undoSession(sessionId: String) = client.undoSession(sessionId)
    suspend fun retrySession(sessionId: String) = client.retrySession(sessionId)
    suspend fun renameSession(sessionId: String, title: String): SessionMutationResponse {
        val operationGeneration = cacheOwnership.generation(serverUrl)
        val response = client.renameSession(sessionId, title)
        if (response.ok != false && response.error.isNullOrBlank()) {
            cacheOwnership.writeIfCurrent(serverUrl, operationGeneration) {
                cacheDao.updateSessionTitle(serverUrl, sessionId, title)
            }
        }
        return response
    }
    suspend fun branchSession(sessionId: String, title: String? = null, keepCount: Int? = null): SessionDuplicateResult {
        val response = client.branchSession(sessionId, keepCount = keepCount, title = title)
        val branchId = response.sessionId?.takeIf { it.isNotBlank() }
            ?: return SessionDuplicateResult(errorMessage = response.error ?: "The server did not return the forked session ID.")
        val branch = client.session(branchId, includeMessages = false, limit = null).session
            ?: return SessionDuplicateResult(errorMessage = "The server did not return the forked session.")
        return SessionDuplicateResult(session = branch.toSummary())
    }

    suspend fun sessionYolo(sessionId: String) = client.sessionYolo(sessionId)
    suspend fun setSessionYolo(sessionId: String, enabled: Boolean) = client.setSessionYolo(sessionId, enabled)

    suspend fun truncateSessionSnapshot(sessionId: String, keepCount: Int): ChatSessionSnapshot {
        val operationGeneration = cacheOwnership.generation(serverUrl)
        val session = client.truncateSession(sessionId, keepCount).session
        session?.let {
            val resolvedSessionId = it.sessionId?.takeIf { value -> value.isNotBlank() } ?: sessionId
            replaceCachedMessages(resolvedSessionId, it.messages.orEmpty(), generation = operationGeneration)
        }
        return snapshotFromSession(session)
    }

    suspend fun clearSessionSnapshot(sessionId: String): ChatSessionClearResult {
        val operationGeneration = cacheOwnership.generation(serverUrl)
        val response = client.clearSession(sessionId)
        val error = response.error?.trim()?.takeIf { it.isNotBlank() }
        if (response.ok == false || error != null) {
            return ChatSessionClearResult(error = error ?: "The server could not clear this conversation.")
        }

        val session = response.session
            ?: return ChatSessionClearResult(error = "The server did not return the cleared session.")
        val clearedMessages = session.messages.orEmpty()
        val resolvedSessionId = session.sessionId?.takeIf { it.isNotBlank() } ?: sessionId
        val now = System.currentTimeMillis()
        replaceCachedMessages(resolvedSessionId, clearedMessages, now, operationGeneration)
        return ChatSessionClearResult(snapshot = snapshotFromSession(session, messagesOverride = clearedMessages))
    }

    suspend fun updateSessionConfiguration(
        sessionId: String,
        workspace: String?,
        model: ModelSummary?,
    ): ChatSessionConfiguration {
        val response = client.updateSession(
            sessionId = sessionId,
            workspace = workspace?.trim()?.takeIf { it.isNotBlank() },
            model = model?.id ?: model?.name,
            modelProvider = model?.provider,
        )
        val session = response.session
        return ChatSessionConfiguration(
            workspace = session?.workspace ?: workspace,
            model = session?.model ?: (model?.id ?: model?.name),
            modelProvider = session?.modelProvider ?: model?.provider,
        )
    }

    suspend fun loadOlderSessionSnapshot(
        sessionId: String,
        before: Int,
        currentMessages: List<ChatMessage>,
    ): ChatSessionSnapshot {
        val operationGeneration = cacheOwnership.generation(serverUrl)
        val session = client.session(
            id = sessionId,
            includeMessages = true,
            limit = MESSAGE_PAGE_LIMIT,
            before = before,
        ).session
        val messages = ChatMessagePageMerger.prependOlderMessages(
            olderMessages = session?.messages.orEmpty(),
            currentMessages = currentMessages,
        )
        val now = System.currentTimeMillis()
        replaceCachedMessages(sessionId, messages, now, operationGeneration)
        return snapshotFromSession(session, messagesOverride = messages)
    }

    suspend fun createSession(
        workspace: String?,
        model: ModelSummary?,
        profile: ProfileSummary?,
    ): SessionSummary? = client.newSession(
        NewSessionRequest(
            workspace = workspace?.trim()?.takeIf { it.isNotBlank() },
            model = model?.id ?: model?.name,
            modelProvider = model?.provider,
            profile = profile?.name,
        ),
    ).session

    suspend fun approvalPending(sessionId: String): ApprovalPendingResponse = client.approvalPending(sessionId)
    suspend fun respondApproval(sessionId: String, choice: ApprovalChoice, approvalId: String?): ApprovalRespondResponse =
        client.respondApproval(sessionId, choice, approvalId)
    suspend fun clarificationPending(sessionId: String): ClarificationPendingResponse = client.clarifyPending(sessionId)
    suspend fun respondClarification(sessionId: String, response: String, clarifyId: String?): ClarificationRespondResponse =
        client.respondClarification(sessionId, response, clarifyId)
    suspend fun upload(sessionId: String, file: File, mimeType: String?) = client.upload(sessionId, file, mimeType)
    suspend fun transcribe(file: File): TranscribeResponse = client.transcribe(file)
    suspend fun transcriptMediaData(reference: TranscriptMediaReference): ByteArray = client.transcriptMediaData(reference)
    suspend fun attachmentFile(sessionId: String, path: String): FileResponse = client.file(sessionId, path)
    suspend fun synthesizeSpeech(text: String, voice: String = "en-US-AriaNeural"): ByteArray =
        client.synthesizeSpeech(text, voice)
    suspend fun models(): List<ModelSummary> = client.models().flattenedModels
    suspend fun commands() = client.commands().commands.orEmpty()
    suspend fun profilesResponse(): ProfilesResponse = client.profiles()
    suspend fun profiles(): List<ProfileSummary> = client.profiles().profiles.orEmpty()
    suspend fun skills(): List<SkillSummary> = client.skills().skills.orEmpty()
    suspend fun personalities(): List<PersonalitySummary> = client.personalities().personalities.orEmpty()
    suspend fun setPersonality(sessionId: String, name: String) = client.setPersonality(sessionId, name)
    suspend fun switchProfile(profile: ProfileSummary) {
        val name = profile.name ?: return
        val response = client.switchProfile(name)
        require(response.error.isNullOrBlank()) { response.error ?: "Could not switch profile." }
    }
    suspend fun reasoning(model: ModelSummary?): ReasoningResponse = client.reasoning(model?.id ?: model?.name, model?.provider)
    suspend fun workspaces(): WorkspacesResponse = client.workspaces()
    suspend fun workspaceSuggestions(prefix: String): List<String> = client.workspaceSuggestions(prefix).suggestions.orEmpty()
    suspend fun setReasoning(effort: String, model: ModelSummary?) {
        client.setReasoning(effort, model?.id ?: model?.name, model?.provider)
    }

    private suspend fun replaceCachedMessages(
        sessionId: String,
        messages: List<ChatMessage>,
        now: Long = System.currentTimeMillis(),
        generation: Long = cacheOwnership.generation(serverUrl),
    ) {
        cacheOwnership.writeIfCurrent(serverUrl, generation) {
            cacheDao.replaceMessages(
                serverUrl,
                sessionId,
                messages.mapIndexed { index, message ->
                    CachedMessageEntity.from(serverUrl, sessionId, message, index, now)
                },
                now,
            )
        }
    }

    private fun snapshotFromSession(
        session: SessionDetail?,
        messagesOverride: List<ChatMessage>? = null,
    ): ChatSessionSnapshot {
        val messages = messagesOverride ?: session?.messages.orEmpty()
        val messagesOffset = session?.resolvedMessagesOffset(messages.size) ?: 0
        val activeStreamId = session?.activeStreamId?.trim()?.takeIf { it.isNotBlank() }
        return ChatSessionSnapshot(
            messages = messages,
            messagesOffset = messagesOffset,
            hasOlderMessages = session?.hasOlderMessages(messages.size) ?: false,
            compressionReferenceCard = CompressionAnchorResolver.resolve(
                messages = messages,
                messagesOffset = messagesOffset,
                metadata = session?.compressionAnchorMetadata(),
            ),
            completedToolCallGroups = ToolCallGroupResolver.groups(
                messages = messages,
                messagesOffset = messagesOffset,
                persistedToolCalls = session?.toolCalls,
            ),
            contextWindowSnapshot = session?.contextWindowSnapshot(),
            title = session?.title,
            workspace = session?.workspace,
            model = session?.model,
            modelProvider = session?.modelProvider,
            profile = session?.profile,
            activeStreamId = activeStreamId,
            isStreaming = session?.isStreaming == true || activeStreamId != null,
        )
    }
}

private fun Throwable.isChatCacheFallbackEligible(): Boolean = when (this) {
    is ApiError.Network -> true
    is ApiError.Http -> statusCode in setOf(408, 502, 503, 504)
    else -> false
}

private fun SessionDetail.toSummary(): SessionSummary = SessionSummary(
    sessionId = sessionId,
    title = title,
    workspace = workspace,
    model = model,
    modelProvider = modelProvider,
    messageCount = messageCount ?: messages?.size,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastMessageAt = lastMessageAt,
    pinned = pinned,
    archived = archived,
    projectId = projectId,
    profile = profile,
    inputTokens = inputTokens,
    outputTokens = outputTokens,
    estimatedCost = estimatedCost,
    activeStreamId = activeStreamId,
    isStreaming = isStreaming,
    isCliSession = isCliSession,
)
