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
import com.uzairansar.hermex.core.network.SseEvent
import com.uzairansar.hermex.core.network.SseReplayCursor
import com.uzairansar.hermex.core.network.SseStreamClient
import com.uzairansar.hermex.data.db.CacheDao
import com.uzairansar.hermex.data.db.CachedMessageEntity
import kotlinx.coroutines.flow.Flow
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
    private val sse: SseStreamClient,
) {
    private val serverUrl = client.baseUrl.toString()
    private val streamEventIds = ConcurrentHashMap<String, String>()

    suspend fun loadSessionSnapshot(sessionId: String): ResultState<ChatSessionSnapshot> {
        val now = System.currentTimeMillis()
        return try {
            val session = client.session(sessionId).session
            val messages = session?.messages.orEmpty()
            cacheDao.replaceMessages(
                serverUrl,
                sessionId,
                messages.mapIndexed { index, message -> CachedMessageEntity.from(serverUrl, sessionId, message, index, now) },
                now,
            )
            ResultState.Data(snapshotFromSession(session))
        } catch (error: Throwable) {
            val cached = cacheDao.cachedMessages(serverUrl, sessionId, now).mapNotNull { it.toMessage() }
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
    ): ChatSessionSnapshot {
        val messages = session.messages.orEmpty()
        if (messages.isNotEmpty()) {
            val now = System.currentTimeMillis()
            cacheDao.replaceMessages(
                serverUrl,
                session.sessionId?.takeIf { it.isNotBlank() } ?: sessionId,
                messages.mapIndexed { index, message ->
                    CachedMessageEntity.from(serverUrl, sessionId, message, index, now)
                },
                now,
            )
        }
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
    ): String? = client.chatStart(
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

    fun stream(streamId: String, replayAfterSeq: Int? = null): Flow<SseEvent> =
        sse.stream(client.streamUrl(streamId, replayAfterSeq)) { eventId ->
            streamEventIds[streamId] = eventId
        }

    suspend fun cancel(streamId: String) = client.chatCancel(streamId)
    suspend fun chatStreamStatus(streamId: String): SessionStatusResponse = client.chatStreamStatus(streamId)
    fun replayAfterSeq(streamId: String): Int? = SseReplayCursor.afterSeqFromEventId(streamEventIds[streamId])
    fun clearStreamCursor(streamId: String) {
        streamEventIds.remove(streamId)
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
    suspend fun compressSession(sessionId: String, focusTopic: String?) = client.compressSession(sessionId, focusTopic)
    suspend fun undoSession(sessionId: String) = client.undoSession(sessionId)
    suspend fun retrySession(sessionId: String) = client.retrySession(sessionId)
    suspend fun renameSession(sessionId: String, title: String): SessionMutationResponse = client.renameSession(sessionId, title)
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
        val session = client.truncateSession(sessionId, keepCount).session
        return snapshotFromSession(session)
    }

    suspend fun clearSessionSnapshot(sessionId: String): ChatSessionClearResult {
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
        cacheDao.replaceMessages(
            serverUrl,
            resolvedSessionId,
            clearedMessages.mapIndexed { index, message ->
                CachedMessageEntity.from(serverUrl, resolvedSessionId, message, index, now)
            },
            now,
        )
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
        cacheDao.replaceMessages(
            serverUrl,
            sessionId,
            messages.mapIndexed { index, message -> CachedMessageEntity.from(serverUrl, sessionId, message, index, now) },
            now,
        )
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
    suspend fun models(): List<ModelSummary> = client.models().models.orEmpty()
    suspend fun commands() = client.commands().commands.orEmpty()
    suspend fun profilesResponse(): ProfilesResponse = client.profiles()
    suspend fun profiles(): List<ProfileSummary> = client.profiles().profiles.orEmpty()
    suspend fun skills(): List<SkillSummary> = client.skills().skills.orEmpty()
    suspend fun personalities(): List<PersonalitySummary> = client.personalities().personalities.orEmpty()
    suspend fun setPersonality(sessionId: String, name: String) = client.setPersonality(sessionId, name)
    suspend fun switchProfile(profile: ProfileSummary) {
        val name = profile.name ?: return
        client.switchProfile(name)
    }
    suspend fun reasoning(model: ModelSummary?): ReasoningResponse = client.reasoning(model?.id ?: model?.name, model?.provider)
    suspend fun workspaces(): WorkspacesResponse = client.workspaces()
    suspend fun workspaceSuggestions(prefix: String): List<String> = client.workspaceSuggestions(prefix).suggestions.orEmpty()
    suspend fun setReasoning(effort: String, model: ModelSummary?) {
        client.setReasoning(effort, model?.id ?: model?.name, model?.provider)
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
