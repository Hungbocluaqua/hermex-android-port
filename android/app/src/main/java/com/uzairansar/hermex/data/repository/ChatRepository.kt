package com.uzairansar.hermex.data.repository

import com.uzairansar.hermex.core.model.ChatMessage
import com.uzairansar.hermex.core.model.ChatStartRequest
import com.uzairansar.hermex.core.model.ApprovalChoice
import com.uzairansar.hermex.core.model.ApprovalPendingResponse
import com.uzairansar.hermex.core.model.ApprovalRespondResponse
import com.uzairansar.hermex.core.model.ClarificationPendingResponse
import com.uzairansar.hermex.core.model.ClarificationRespondResponse
import com.uzairansar.hermex.core.model.ModelSummary
import com.uzairansar.hermex.core.model.ProfileSummary
import com.uzairansar.hermex.core.model.ReasoningResponse
import com.uzairansar.hermex.core.model.TranscribeResponse
import com.uzairansar.hermex.core.model.UploadResponse
import com.uzairansar.hermex.core.network.HermesApiClient
import com.uzairansar.hermex.core.network.SseEvent
import com.uzairansar.hermex.core.network.SseStreamClient
import com.uzairansar.hermex.data.db.CacheDao
import com.uzairansar.hermex.data.db.CachedMessageEntity
import kotlinx.coroutines.flow.Flow
import java.io.File

class ChatRepository(
    private val client: HermesApiClient,
    private val cacheDao: CacheDao,
    private val sse: SseStreamClient,
) {
    private val serverUrl = client.baseUrl.toString()

    suspend fun loadMessages(sessionId: String): ResultState<List<ChatMessage>> {
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
            ResultState.Data(messages)
        } catch (error: Throwable) {
            val cached = cacheDao.cachedMessages(serverUrl, sessionId, now).mapNotNull { it.toMessage() }
            if (cached.isNotEmpty()) ResultState.Data(cached, fromCache = true) else ResultState.Error(error.userMessage(), error)
        }
    }

    suspend fun send(
        sessionId: String?,
        message: String,
        model: ModelSummary? = null,
        attachments: List<UploadResponse> = emptyList(),
    ): String? = client.chatStart(
        ChatStartRequest(
            sessionId = sessionId,
            message = message,
            model = model?.id ?: model?.name,
            modelProvider = model?.provider,
            attachments = attachments.ifEmpty { null },
        ),
    ).streamId

    fun stream(streamId: String): Flow<SseEvent> = sse.stream(client.streamUrl(streamId))

    suspend fun cancel(streamId: String) = client.chatCancel(streamId)
    suspend fun steer(sessionId: String, text: String) = client.chatSteer(sessionId, text)
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
    suspend fun approvalPending(sessionId: String): ApprovalPendingResponse = client.approvalPending(sessionId)
    suspend fun respondApproval(sessionId: String, choice: ApprovalChoice, approvalId: String?): ApprovalRespondResponse =
        client.respondApproval(sessionId, choice, approvalId)
    suspend fun clarificationPending(sessionId: String): ClarificationPendingResponse = client.clarifyPending(sessionId)
    suspend fun respondClarification(sessionId: String, response: String, clarifyId: String?): ClarificationRespondResponse =
        client.respondClarification(sessionId, response, clarifyId)
    suspend fun upload(sessionId: String, file: File, mimeType: String?) = client.upload(sessionId, file, mimeType)
    suspend fun transcribe(file: File): TranscribeResponse = client.transcribe(file)
    suspend fun synthesizeSpeech(text: String, voice: String = "en-US-AriaNeural"): ByteArray =
        client.synthesizeSpeech(text, voice)
    suspend fun models(): List<ModelSummary> = client.models().models.orEmpty()
    suspend fun profiles(): List<ProfileSummary> = client.profiles().profiles.orEmpty()
    suspend fun switchProfile(profile: ProfileSummary) {
        val name = profile.name ?: return
        client.switchProfile(name)
    }
    suspend fun reasoning(model: ModelSummary?): ReasoningResponse = client.reasoning(model?.id ?: model?.name, model?.provider)
    suspend fun setReasoning(effort: String, model: ModelSummary?) {
        client.setReasoning(effort, model?.id ?: model?.name, model?.provider)
    }
}
