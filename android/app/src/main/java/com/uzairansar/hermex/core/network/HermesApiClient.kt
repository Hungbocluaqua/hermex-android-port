package com.uzairansar.hermex.core.network

import com.uzairansar.hermex.core.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import kotlin.time.Duration.Companion.seconds
import okhttp3.Call

class HermesApiClient(
    val baseUrl: HttpUrl,
    private val client: OkHttpClient,
    private val json: Json = HermesJson,
    private val customHeaders: () -> List<CustomHeader> = { emptyList() },
) {
    private val jsonMediaType = "application/json".toMediaType()

    suspend fun health(): HealthResponse = get(Endpoint.Health)
    suspend fun authStatus(): AuthStatusResponse = get(Endpoint.AuthStatus)
    suspend fun login(password: String): LoginResponse = post(Endpoint.Login, LoginRequest(password))
    suspend fun logout(): LoginResponse = post(Endpoint.Logout, EmptyBody())
    suspend fun sessions(includeArchived: Boolean = false, archivedLimit: Int? = null): SessionsResponse =
        get(Endpoint.Sessions(includeArchived, archivedLimit))
    suspend fun searchSessions(query: String, content: Boolean, depth: Int): SessionSearchResponse =
        get(Endpoint.SessionsSearch(query, content, depth))
    suspend fun session(id: String, includeMessages: Boolean = true, limit: Int? = 50, before: Int? = null): SessionResponse =
        get(Endpoint.Session(id, includeMessages, limit, before, expandRenderable = true))
    suspend fun sessionStatus(id: String): SessionStatusResponse = get(Endpoint.SessionStatus(id))
    suspend fun newSession(request: NewSessionRequest = NewSessionRequest()): SessionMutationResponse = post(Endpoint.NewSession, request)
    suspend fun renameSession(sessionId: String, title: String): SessionMutationResponse = post(Endpoint.RenameSession, RenameSessionRequest(sessionId, title))
    suspend fun deleteSession(sessionId: String): SessionMutationResponse = post(Endpoint.DeleteSession, SessionIdRequest(sessionId))
    suspend fun pinSession(sessionId: String, pinned: Boolean): SessionMutationResponse = post(Endpoint.PinSession, PinSessionRequest(sessionId, pinned))
    suspend fun archiveSession(sessionId: String, archived: Boolean): SessionMutationResponse = post(Endpoint.ArchiveSession, ArchiveSessionRequest(sessionId, archived))
    suspend fun moveSession(sessionId: String, projectId: String?): SessionMutationResponse = post(Endpoint.MoveSession, MoveSessionRequest(sessionId, projectId))
    suspend fun branchSession(sessionId: String, keepCount: Int? = null, title: String? = null): SessionBranchResponse =
        post(Endpoint.BranchSession, BranchSessionRequest(sessionId, keepCount, title))
    suspend fun compressSession(sessionId: String, focusTopic: String? = null): SessionCompressResponse =
        post(Endpoint.CompressSession, CompressSessionRequest(sessionId, focusTopic), timeoutSeconds = 120)
    suspend fun undoSession(sessionId: String): SessionUndoResponse = post(Endpoint.UndoSession, SessionIdRequest(sessionId))
    suspend fun retrySession(sessionId: String): SessionRetryResponse = post(Endpoint.RetrySession, SessionIdRequest(sessionId))
    suspend fun truncateSession(sessionId: String, keepCount: Int): SessionMutationResponse =
        post(Endpoint.TruncateSession, TruncateSessionRequest(sessionId, keepCount))
    suspend fun projects(): ProjectsResponse = get(Endpoint.Projects)
    suspend fun createProject(name: String): ProjectMutationResponse = post(Endpoint.CreateProject, CreateProjectRequest(name))
    suspend fun renameProject(projectId: String, name: String): ProjectMutationResponse = post(Endpoint.RenameProject, RenameProjectRequest(projectId, name))
    suspend fun deleteProject(projectId: String): ProjectMutationResponse = post(Endpoint.DeleteProject, DeleteProjectRequest(projectId))
    suspend fun chatStart(request: ChatStartRequest): ChatStartResponse = post(Endpoint.ChatStart, request)
    suspend fun chatCancel(streamId: String): SessionMutationResponse = get(Endpoint.ChatCancel(streamId))
    suspend fun chatStreamStatus(streamId: String): SessionStatusResponse = get(Endpoint.ChatStreamStatus(streamId))
    suspend fun chatSteer(sessionId: String, text: String): ChatSteerResponse = post(Endpoint.ChatSteer, ChatSteerRequest(sessionId, text))
    suspend fun submitGoal(
        sessionId: String,
        args: String,
        model: String? = null,
        modelProvider: String? = null,
        profile: String? = null,
    ): GoalSubmissionResponse = post(
        Endpoint.SubmitGoal,
        GoalRequest(
            sessionId = sessionId,
            args = args,
            model = model,
            modelProvider = modelProvider,
            profile = profile,
        ),
        timeoutSeconds = 120,
    )
    suspend fun approvalPending(sessionId: String): ApprovalPendingResponse = get(Endpoint.ApprovalPending(sessionId))
    suspend fun respondApproval(sessionId: String, choice: ApprovalChoice, approvalId: String?): ApprovalRespondResponse =
        post(Endpoint.ApprovalRespond, ApprovalRespondRequest(sessionId, choice, approvalId))
    suspend fun clarifyPending(sessionId: String): ClarificationPendingResponse = get(Endpoint.ClarifyPending(sessionId))
    suspend fun respondClarification(sessionId: String, response: String, clarifyId: String?): ClarificationRespondResponse =
        post(Endpoint.ClarifyRespond, ClarifyRespondRequest(sessionId, clarifyId, response))
    suspend fun workspaces(): WorkspacesResponse = get(Endpoint.Workspaces)
    suspend fun workspaceSuggestions(prefix: String): WorkspaceSuggestionsResponse = get(Endpoint.WorkspaceSuggestions(prefix))
    suspend fun directoryList(sessionId: String, path: String?): DirectoryListResponse = get(Endpoint.DirectoryList(sessionId, path))
    suspend fun file(sessionId: String, path: String): FileResponse = get(Endpoint.File(sessionId, path))
    suspend fun rawFile(sessionId: String, path: String): ByteArray = data(Endpoint.RawFile(sessionId, path), "GET")
    suspend fun models(): ModelCatalogResponse = get(Endpoint.Models)
    suspend fun profiles(): ProfilesResponse = get(Endpoint.Profiles)
    suspend fun switchProfile(profile: String): SessionMutationResponse = post(Endpoint.SwitchProfile, SwitchProfileRequest(profile))
    suspend fun providers(): ModelCatalogResponse = get(Endpoint.Providers)
    suspend fun settings(): SettingsResponse = get(Endpoint.Settings)
    suspend fun reasoning(model: String? = null, provider: String? = null): ReasoningResponse = get(Endpoint.Reasoning(model, provider))
    suspend fun setReasoning(effort: String, model: String? = null, provider: String? = null): ReasoningResponse =
        post(Endpoint.Reasoning(model, provider), ReasoningRequest(effort, model, provider))
    suspend fun defaultModel(model: String): DefaultModelResponse = post(Endpoint.DefaultModel, DefaultModelRequest(model))
    suspend fun insights(days: Int): InsightsResponse = get(Endpoint.Insights(days))
    suspend fun crons(): CronsResponse = get(Endpoint.Crons)
    suspend fun createCron(request: CronCreateRequest): CronMutationResponse = post(Endpoint.CronCreate, request)
    suspend fun updateCron(request: CronUpdateRequest): CronMutationResponse = post(Endpoint.CronUpdate, request)
    suspend fun deleteCron(jobId: String): CronMutationResponse = post(Endpoint.CronDelete, CronJobIdRequest(jobId))
    suspend fun runCron(jobId: String): CronMutationResponse = post(Endpoint.CronRun, CronJobIdRequest(jobId))
    suspend fun pauseCron(jobId: String, reason: String? = null): CronMutationResponse = post(Endpoint.CronPause, CronJobIdRequest(jobId, reason))
    suspend fun resumeCron(jobId: String): CronMutationResponse = post(Endpoint.CronResume, CronJobIdRequest(jobId))
    suspend fun cronStatus(jobId: String? = null): CronStatusResponse = get(Endpoint.CronStatus(jobId))
    suspend fun cronOutput(jobId: String, limit: Int? = 5): CronOutputResponse = get(Endpoint.CronOutput(jobId, limit))
    suspend fun skills(): SkillsResponse = get(Endpoint.Skills)
    suspend fun skillContent(name: String, file: String? = null): SkillContentResponse = get(Endpoint.SkillContent(name, file))
    suspend fun toggleSkill(name: String, enabled: Boolean): ToggleSkillResponse = post(Endpoint.ToggleSkill, ToggleSkillRequest(name, enabled))
    suspend fun memory(): MemoryResponse = get(Endpoint.Memory)
    suspend fun writeMemory(section: String, content: String): MemoryWriteResponse = post(Endpoint.MemoryWrite, MemoryWriteRequest(section, content))
    suspend fun gitInfo(sessionId: String): GitInfoResponse = get(Endpoint.GitInfo(sessionId))
    suspend fun gitStatus(sessionId: String): GitStatusResponse = get(Endpoint.GitStatus(sessionId))
    suspend fun gitBranches(sessionId: String): GitBranchesResponse = get(Endpoint.GitBranches(sessionId))
    suspend fun gitDiff(sessionId: String, path: String, kind: String = "unstaged"): GitDiffResponse = get(Endpoint.GitDiff(sessionId, path, kind))
    suspend fun gitFetch(sessionId: String): GitRemoteActionResponse = post(Endpoint.GitFetch, GitSessionRequest(sessionId))
    suspend fun gitPull(sessionId: String): GitRemoteActionResponse = post(Endpoint.GitPull, GitSessionRequest(sessionId))
    suspend fun gitPush(sessionId: String): GitRemoteActionResponse = post(Endpoint.GitPush, GitSessionRequest(sessionId))
    suspend fun gitCheckout(
        sessionId: String,
        ref: String,
        mode: String,
        newBranch: String? = null,
        track: Boolean? = null,
    ): GitCheckoutResponse = post(
        Endpoint.GitCheckout,
        GitCheckoutRequest(
            sessionId = sessionId,
            ref = ref,
            mode = if (mode == "local" && newBranch != null) "new" else mode,
            newBranch = newBranch,
            track = if (track == true) true else null,
            dirtyMode = "block",
        ),
    )
    suspend fun gitStashCheckout(
        sessionId: String,
        ref: String,
        mode: String,
        newBranch: String? = null,
        track: Boolean? = null,
    ): GitCheckoutResponse = post(
        Endpoint.GitStashCheckout,
        GitCheckoutRequest(
            sessionId = sessionId,
            ref = ref,
            mode = if (mode == "local" && newBranch != null) "new" else mode,
            newBranch = newBranch,
            track = if (track == true) true else null,
        ),
    )
    suspend fun gitStage(sessionId: String, paths: List<String>): GitMutationResponse = post(Endpoint.GitStage, GitPathsRequest(sessionId, paths))
    suspend fun gitUnstage(sessionId: String, paths: List<String>): GitMutationResponse = post(Endpoint.GitUnstage, GitPathsRequest(sessionId, paths))
    suspend fun gitDiscard(sessionId: String, paths: List<String>, deleteUntracked: Boolean): GitMutationResponse =
        post(Endpoint.GitDiscard, GitDiscardRequest(sessionId, paths, deleteUntracked))
    suspend fun gitCommit(sessionId: String, message: String): GitCommitResponse = post(Endpoint.GitCommit, GitCommitRequest(sessionId, message), timeoutSeconds = 120)
    suspend fun gitCommitSelected(sessionId: String, message: String, paths: List<String>): GitCommitResponse =
        post(Endpoint.GitCommitSelected, GitCommitSelectedRequest(sessionId, message, paths), timeoutSeconds = 120)
    suspend fun gitCommitMessage(sessionId: String): GitCommitMessageResponse = post(Endpoint.GitCommitMessage, GitSessionRequest(sessionId), timeoutSeconds = 120)
    suspend fun gitCommitMessageSelected(sessionId: String, paths: List<String>): GitCommitMessageResponse =
        post(Endpoint.GitCommitMessageSelected, GitPathsRequest(sessionId, paths), timeoutSeconds = 120)
    suspend fun synthesizeSpeech(text: String, voice: String): ByteArray =
        data(
            endpoint = Endpoint.Tts,
            method = "POST",
            encodedBody = json.encodeToString(TtsSynthesisRequest(text, voice)).toRequestBody(jsonMediaType),
            accept = "audio/mpeg",
        )

    suspend fun upload(sessionId: String, file: File, mimeType: String?): UploadResponse = withContext(Dispatchers.IO) {
        val mediaType = (mimeType ?: "application/octet-stream").toMediaType()
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("session_id", sessionId)
            .addFormDataPart("file", file.name, file.asRequestBody(mediaType))
            .build()
        val request = requestBuilder(Endpoint.Upload)
            .post(body)
            .header("Accept", "application/json")
            .build()
        executeAndDecode(request)
    }

    suspend fun transcribe(file: File, mimeType: String? = "audio/mp4"): TranscribeResponse = withContext(Dispatchers.IO) {
        val mediaType = (mimeType ?: "application/octet-stream").toMediaType()
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, file.asRequestBody(mediaType))
            .build()
        val request = requestBuilder(Endpoint.Transcribe)
            .post(body)
            .header("Accept", "application/json")
            .build()
        executeAndDecode(request)
    }

    fun streamUrl(streamId: String): HttpUrl = Endpoint.ChatStream(streamId).url(baseUrl)

    private suspend inline fun <reified Response : Any> get(endpoint: Endpoint): Response =
        request<Response>(endpoint, "GET", encodedBody = null)

    private suspend inline fun <reified Response : Any, reified Body : Any> post(
        endpoint: Endpoint,
        body: Body,
        timeoutSeconds: Long? = null,
    ): Response = request(endpoint, "POST", json.encodeToString(body).toRequestBody(jsonMediaType), timeoutSeconds)

    private suspend inline fun <reified Response : Any> request(
        endpoint: Endpoint,
        method: String,
        encodedBody: okhttp3.RequestBody?,
        timeoutSeconds: Long? = null,
    ): Response = withContext(Dispatchers.IO) {
        val builder = requestBuilder(endpoint)
            .method(method, encodedBody)
            .header("Accept", "application/json")
        val request = builder.build()
        val callClient = timeoutSeconds?.let {
            client.newBuilder().callTimeout(it.seconds.inWholeMilliseconds, java.util.concurrent.TimeUnit.MILLISECONDS).build()
        } ?: client
        executeAndDecode(callClient.newCall(request))
    }

    private suspend fun data(
        endpoint: Endpoint,
        method: String,
        encodedBody: okhttp3.RequestBody? = null,
        accept: String = "*/*",
    ): ByteArray = withContext(Dispatchers.IO) {
        val request = requestBuilder(endpoint)
            .method(method, encodedBody)
            .header("Accept", accept)
            .build()
        executeData(client.newCall(request))
    }

    private fun requestBuilder(endpoint: Endpoint): Request.Builder {
        val builder = Request.Builder().url(endpoint.url(baseUrl))
        customHeaders().applyTo(builder)
        return builder
    }

    private inline fun <reified Response : Any> executeAndDecode(request: Request): Response =
        executeAndDecode(client.newCall(request))

    private inline fun <reified Response : Any> executeAndDecode(call: Call): Response {
        val bytes = executeData(call)
        return try {
            json.decodeFromString<Response>(bytes.decodeToString())
        } catch (error: Throwable) {
            throw ApiError.Decoding(error)
        }
    }

    private fun executeData(call: Call): ByteArray {
        val response = try {
            call.execute()
        } catch (error: IOException) {
            throw ApiError.Network(error)
        }
        response.use {
            val bytes = it.body.bytes()
            if (it.code == 401) throw ApiError.Unauthorized
            if (!it.isSuccessful) throw ApiError.Http(it.code, bytes.decodeToString())
            return bytes
        }
    }
}
