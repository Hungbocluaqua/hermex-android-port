package com.uzairansar.hermex.core.network

import com.uzairansar.hermex.core.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer
import java.io.File
import java.io.IOException
import java.net.InetAddress
import kotlin.time.Duration.Companion.seconds

class HermesApiClient(
    val baseUrl: HttpUrl,
    client: OkHttpClient,
    private val json: Json = HermesJson,
    private val customHeaders: () -> List<CustomHeader> = { emptyList() },
    private val onUnauthorized: (HttpUrl) -> Unit = {},
    private val onProfileChanged: suspend (HttpUrl, String) -> Unit = { _, _ -> },
    private val publicMediaDns: Dns = PublicNetworkDns,
) {
    private val jsonMediaType = "application/json".toMediaType()
    @OptIn(ExperimentalSerializationApi::class)
    private val projectMutationJson = Json(json) { explicitNulls = true }
    @OptIn(ExperimentalSerializationApi::class)
    private val cronMutationJson = Json(json) {
        explicitNulls = true
        encodeDefaults = true
    }
    private val client: OkHttpClient = client.newBuilder()
        .addNetworkInterceptor(ServerTransportPolicyInterceptor())
        .addNetworkInterceptor(SameOriginCustomHeaderInterceptor(baseUrl, customHeaders))
        .build()
    private val publicMediaClient: OkHttpClient = this.client.newBuilder()
        .cookieJar(CookieJar.NO_COOKIES)
        .dns(publicMediaDns)
        .build()

    init {
        requireAllowedServerTransport(baseUrl)
    }

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
    suspend fun sessionUsage(sessionId: String): SessionUsageResponse = get(Endpoint.SessionUsage(sessionId))
    suspend fun newSession(request: NewSessionRequest = NewSessionRequest()): SessionMutationResponse = post(Endpoint.NewSession, request)
    suspend fun renameSession(sessionId: String, title: String): SessionMutationResponse = post(Endpoint.RenameSession, RenameSessionRequest(sessionId, title))
    suspend fun deleteSession(sessionId: String): SessionMutationResponse = post(Endpoint.DeleteSession, SessionIdRequest(sessionId))
    suspend fun clearSession(sessionId: String): SessionClearResponse = post(Endpoint.ClearSession, SessionIdRequest(sessionId))
    suspend fun pinSession(sessionId: String, pinned: Boolean): SessionMutationResponse = post(Endpoint.PinSession, PinSessionRequest(sessionId, pinned))
    suspend fun archiveSession(sessionId: String, archived: Boolean): SessionMutationResponse = post(Endpoint.ArchiveSession, ArchiveSessionRequest(sessionId, archived))
    suspend fun moveSession(sessionId: String, projectId: String?): SessionMutationResponse = post(Endpoint.MoveSession, MoveSessionRequest(sessionId, projectId))
    suspend fun branchSession(sessionId: String, keepCount: Int? = null, title: String? = null): SessionBranchResponse =
        post(Endpoint.BranchSession, BranchSessionRequest(sessionId, keepCount, title))
    suspend fun sessionYolo(sessionId: String): SessionYoloResponse = get(Endpoint.SessionYolo(sessionId))
    suspend fun setSessionYolo(sessionId: String, enabled: Boolean): SessionYoloResponse =
        post(Endpoint.SessionYolo(null), SessionYoloRequest(sessionId, enabled))
    suspend fun exportSession(
        sessionId: String,
        format: SessionExportFormat,
        fallbackTitle: String? = null,
        destinationDirectory: File? = null,
    ): SessionExportFile {
        val ownsDirectory = destinationDirectory == null
        val directory = destinationDirectory ?: createTemporaryExportDirectory()
        check(directory.mkdirs() || directory.isDirectory) { "Could not prepare the export directory." }
        val temporaryFile = File.createTempFile("download-", ".tmp", directory)
        try {
            val request = requestBuilder(Endpoint.ExportSession(sessionId, format.wireValue))
                .get()
                .header("Accept", "*/*")
                .build()
            val response = executeFileWithHeaders(
                client.newCall(request),
                destination = temporaryFile,
                headers = listOf("Content-Disposition"),
                maxResponseBytes = MAX_BINARY_RESPONSE_BYTES,
            )
            val filename = sessionExportFilename(
                contentDisposition = response.headers["Content-Disposition"],
                fallbackTitle = fallbackTitle,
                sessionId = sessionId,
                format = format,
            )
            val exportedFile = File(directory, filename)
            if (!temporaryFile.renameTo(exportedFile)) {
                temporaryFile.copyTo(exportedFile, overwrite = true)
                temporaryFile.delete()
            }
            return SessionExportFile(
                file = exportedFile,
                filename = filename,
                mimeType = format.mimeType,
            )
        } catch (error: Throwable) {
            temporaryFile.delete()
            if (ownsDirectory) directory.delete()
            throw error
        }
    }
    suspend fun compressSession(sessionId: String, focusTopic: String? = null): SessionCompressResponse =
        post(Endpoint.CompressSession, CompressSessionRequest(sessionId, focusTopic), timeoutSeconds = 120)
    suspend fun undoSession(sessionId: String): SessionUndoResponse = post(Endpoint.UndoSession, SessionIdRequest(sessionId))
    suspend fun retrySession(sessionId: String): SessionRetryResponse = post(Endpoint.RetrySession, SessionIdRequest(sessionId))
    suspend fun truncateSession(sessionId: String, keepCount: Int): SessionResponse =
        post(Endpoint.TruncateSession, TruncateSessionRequest(sessionId, keepCount))
    suspend fun updateSession(
        sessionId: String,
        workspace: String? = null,
        model: String? = null,
        modelProvider: String? = null,
    ): SessionResponse = post(Endpoint.UpdateSession, UpdateSessionRequest(sessionId, workspace, model, modelProvider))
    suspend fun projects(): ProjectsResponse = get(Endpoint.Projects)
    suspend fun createProject(name: String, color: String?): ProjectMutationResponse =
        post(Endpoint.CreateProject, CreateProjectRequest(name, color), bodyJson = projectMutationJson)
    suspend fun renameProject(projectId: String, name: String, color: String?): ProjectMutationResponse =
        post(Endpoint.RenameProject, RenameProjectRequest(projectId, name, color), bodyJson = projectMutationJson)
    suspend fun deleteProject(projectId: String): ProjectMutationResponse = post(Endpoint.DeleteProject, DeleteProjectRequest(projectId))
    suspend fun chatStart(request: ChatStartRequest): ChatStartResponse = post(Endpoint.ChatStart, request)
    suspend fun chatCancel(streamId: String): ChatCancelResponse = get(Endpoint.ChatCancel(streamId))
    suspend fun chatStreamStatus(streamId: String): SessionStatusResponse = get(Endpoint.ChatStreamStatus(streamId))
    suspend fun chatSteer(sessionId: String, text: String): ChatSteerResponse = post(Endpoint.ChatSteer, ChatSteerRequest(sessionId, text))
    suspend fun startBtw(sessionId: String, question: String): BtwStartResponse = post(Endpoint.Btw, BtwRequest(sessionId, question))
    suspend fun startBackground(sessionId: String, prompt: String): BackgroundStartResponse =
        post(Endpoint.Background, BackgroundRequest(sessionId, prompt))
    suspend fun backgroundStatus(sessionId: String): BackgroundStatusResponse = get(Endpoint.BackgroundStatus(sessionId))
    suspend fun submitGoal(
        sessionId: String,
        args: String,
        workspace: String? = null,
        model: String? = null,
        modelProvider: String? = null,
        profile: String? = null,
    ): GoalSubmissionResponse = post(
        Endpoint.SubmitGoal,
        GoalRequest(
            sessionId = sessionId,
            args = args,
            workspace = workspace,
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
    suspend fun addWorkspace(path: String, name: String? = null, create: Boolean? = null): WorkspaceMutationResponse =
        post(Endpoint.WorkspaceAdd, AddWorkspaceRequest(path, name, create))
    suspend fun removeWorkspace(path: String): WorkspaceMutationResponse =
        post(Endpoint.WorkspaceRemove, RemoveWorkspaceRequest(path))
    suspend fun renameWorkspace(path: String, name: String): WorkspaceMutationResponse =
        post(Endpoint.WorkspaceRename, RenameWorkspaceRequest(path, name))
    suspend fun reorderWorkspaces(paths: List<String>): WorkspaceMutationResponse =
        post(Endpoint.WorkspaceReorder, ReorderWorkspacesRequest(paths))
    suspend fun directoryList(sessionId: String, path: String?): DirectoryListResponse = get(Endpoint.DirectoryList(sessionId, path))
    suspend fun file(sessionId: String, path: String): FileResponse = get(Endpoint.File(sessionId, path))
    suspend fun rawFile(sessionId: String, path: String): ByteArray =
        data(Endpoint.RawFile(sessionId, path), "GET", maxResponseBytes = MAX_PREVIEW_RESPONSE_BYTES)
    suspend fun media(sessionId: String, path: String): ByteArray =
        data(Endpoint.Media(sessionId, path), "GET", maxResponseBytes = MAX_MEDIA_RESPONSE_BYTES)
    suspend fun transcriptMediaData(reference: TranscriptMediaReference, sessionId: String): ByteArray =
        when (val source = reference.source) {
            is TranscriptMediaSource.LocalPath -> media(sessionId, source.path)
            is TranscriptMediaSource.RemoteUrl -> remoteTranscriptMediaData(source.url)
        }
    suspend fun remoteTranscriptMediaData(url: HttpUrl): ByteArray {
        val requestUrl = rebaseLoopbackMediaUrl(url, baseUrl)
        val request = Request.Builder()
            .url(requestUrl)
            .get()
            .header("Accept", "*/*")
            .build()
        val callClient = if (requestUrl.isSameOriginAs(baseUrl)) client else publicMediaClient
        return executeData(callClient.newCall(request), MAX_REMOTE_MEDIA_RESPONSE_BYTES)
    }
    suspend fun models(): ModelCatalogResponse = get(Endpoint.Models)
    suspend fun modelsLive(): ModelsLiveResponse = get(Endpoint.ModelsLive)
    suspend fun commands(): CommandsResponse = get(Endpoint.Commands)
    suspend fun profiles(): ProfilesResponse = get(Endpoint.Profiles)
    suspend fun switchProfile(profile: String): ProfileSwitchResponse {
        val response: ProfileSwitchResponse = post(Endpoint.SwitchProfile, SwitchProfileRequest(profile))
        if (response.error.isNullOrBlank()) onProfileChanged(baseUrl, profile)
        return response
    }
    suspend fun createProfile(
        name: String,
        cloneConfig: Boolean = false,
        defaultModel: String? = null,
        modelProvider: String? = null,
        baseUrl: String? = null,
        apiKey: String? = null,
    ): ProfileCreateResponse = post(
        Endpoint.CreateProfile,
        ProfileCreateRequest(
            name = name,
            cloneConfig = cloneConfig,
            defaultModel = defaultModel,
            modelProvider = modelProvider,
            baseUrl = baseUrl,
            apiKey = apiKey,
        ),
    )
    suspend fun providers(): ProvidersResponse = get(Endpoint.Providers)
    suspend fun settings(): SettingsResponse = get(Endpoint.Settings)
    suspend fun updateSettings(showCliSessions: Boolean): SettingsResponse =
        post(Endpoint.Settings, UpdateSettingsRequest(showCliSessions = showCliSessions))
    suspend fun updateClaudeCodeSessionVisibility(enabled: Boolean): SettingsResponse =
        post(Endpoint.Settings, UpdateSettingsRequest(showClaudeCodeSessions = enabled))
    suspend fun updatesCheck(): UpdatesCheckResponse = get(Endpoint.UpdatesCheck)
    suspend fun updatesCheckForced(): UpdatesCheckResponse = post(Endpoint.UpdatesCheck, UpdatesCheckForceRequest(force = true))
    suspend fun applyUpdate(target: String = "webui"): UpdatesApplyResponse = post(Endpoint.UpdatesApply, UpdatesApplyRequest(target))
    suspend fun reasoning(model: String? = null, provider: String? = null): ReasoningResponse = get(Endpoint.Reasoning(model, provider))
    suspend fun setReasoning(effort: String, model: String? = null, provider: String? = null): ReasoningResponse =
        post(Endpoint.Reasoning(model, provider), ReasoningRequest(effort = effort, model = model, provider = provider))
    suspend fun setReasoningDisplay(display: String): ReasoningResponse =
        post(Endpoint.Reasoning(), ReasoningRequest(display = display))
    suspend fun personalities(): PersonalitiesResponse = get(Endpoint.Personalities)
    suspend fun setPersonality(sessionId: String, name: String): PersonalitySetResponse =
        post(Endpoint.SetPersonality, SetPersonalityRequest(sessionId, name))
    suspend fun defaultModel(model: String, provider: String? = null): DefaultModelResponse =
        post(Endpoint.DefaultModel, DefaultModelRequest(model, provider))
    suspend fun insights(days: Int): InsightsResponse = get(Endpoint.Insights(days))
    suspend fun crons(): CronsResponse = get(Endpoint.Crons)
    suspend fun createCron(request: CronCreateRequest): CronMutationResponse = post(Endpoint.CronCreate, request)
    suspend fun updateCron(request: CronUpdateRequest): CronMutationResponse =
        post(Endpoint.CronUpdate, request, bodyJson = cronMutationJson)
    suspend fun deleteCron(jobId: String): CronMutationResponse = post(Endpoint.CronDelete, CronJobIdRequest(jobId))
    suspend fun runCron(jobId: String): CronMutationResponse = post(Endpoint.CronRun, CronJobIdRequest(jobId))
    suspend fun pauseCron(jobId: String, reason: String? = null): CronMutationResponse = post(Endpoint.CronPause, CronJobIdRequest(jobId, reason))
    suspend fun resumeCron(jobId: String): CronMutationResponse = post(Endpoint.CronResume, CronJobIdRequest(jobId))
    suspend fun cronStatus(jobId: String? = null): CronStatusResponse = get(Endpoint.CronStatus(jobId))
    suspend fun cronOutput(jobId: String, limit: Int? = 5): CronOutputResponse = get(Endpoint.CronOutput(jobId, limit))
    suspend fun cronHistory(jobId: String, offset: Int? = null, limit: Int? = 50): CronHistoryResponse =
        get(Endpoint.CronHistory(jobId, offset, limit))
    suspend fun cronDeliveryOptions(): CronDeliveryOptionsResponse = get(Endpoint.CronDeliveryOptions)
    suspend fun kanbanConfiguration(): KanbanConfiguration = get(Endpoint.KanbanConfig)
    suspend fun kanbanBoards(): KanbanBoardsResponse = get(Endpoint.KanbanBoards)
    suspend fun kanbanBoard(
        board: String,
        tenant: String? = null,
        assignee: String? = null,
        includeArchived: Boolean = false,
        onlyMine: Boolean = false,
        since: Int? = null,
    ): KanbanBoardSnapshot = get(Endpoint.KanbanBoard(board, tenant, assignee, includeArchived, onlyMine, since))
    suspend fun kanbanStats(board: String): KanbanStats = get(Endpoint.KanbanStats(board))
    suspend fun kanbanAssignees(board: String): KanbanAssigneeHistory = get(Endpoint.KanbanAssignees(board))
    suspend fun kanbanEvents(board: String, since: Int, limit: Int = 200): KanbanEventsEnvelope =
        get(Endpoint.KanbanEvents(board, since, limit))
    fun kanbanEventsStreamUrl(board: String, since: Int): HttpUrl = Endpoint.KanbanEventsStream(board, since).url(baseUrl)
    suspend fun kanbanCardDetail(cardId: String, board: String): KanbanCardDetailEnvelope =
        get(Endpoint.KanbanCard(cardId, board))
    suspend fun kanbanWorkerLog(cardId: String, board: String, tailBytes: Int = 65_536): KanbanWorkerLog =
        get(Endpoint.KanbanCardLog(cardId, board, tailBytes))
    suspend fun addKanbanComment(cardId: String, board: String, body: String): KanbanAddCommentResponse =
        post(Endpoint.KanbanCardComments(cardId, board), KanbanCommentRequest(body))
    suspend fun createKanbanCard(board: String, body: KanbanCreateCardRequestBody): KanbanCardMutationEnvelope =
        post(Endpoint.KanbanCards(board), body)
    suspend fun editKanbanCard(cardId: String, board: String, body: KanbanEditCardRequestBody): KanbanCardMutationEnvelope {
        val payload = buildJsonObject {
            put("title", body.title)
            put("body", body.body)
            put("tenant", body.tenant?.let(::JsonPrimitive) ?: JsonNull)
            put("priority", body.priority)
            put("assignee", body.assignee?.let(::JsonPrimitive) ?: JsonNull)
            body.status?.let { put("status", it) }
        }
        return request(
            Endpoint.KanbanCard(cardId, board),
            "PATCH",
            json.encodeToString(payload).toRequestBody(jsonMediaType),
        )
    }
    suspend fun setKanbanCardStatus(cardId: String, board: String, status: String): KanbanCardMutationEnvelope {
        require(status.trim().lowercase() != "running") { "Running status requires the dispatcher." }
        return request(
            Endpoint.KanbanCard(cardId, board),
            "PATCH",
            json.encodeToString(KanbanStatusRequestBody(status)).toRequestBody(jsonMediaType),
        )
    }
    suspend fun blockKanbanCard(cardId: String, board: String, reason: String?): KanbanCardMutationEnvelope =
        post(Endpoint.KanbanCardBlock(cardId, board), KanbanCardActionRequestBody(reason))
    suspend fun unblockKanbanCard(cardId: String, board: String): KanbanCardMutationEnvelope =
        post(Endpoint.KanbanCardUnblock(cardId, board), KanbanCardActionRequestBody())
    suspend fun addKanbanDependency(board: String, body: KanbanDependencyRequestBody): KanbanDependencyMutationEnvelope =
        post(Endpoint.KanbanLinks(board), body)
    suspend fun removeKanbanDependency(board: String, body: KanbanDependencyRequestBody): KanbanDependencyMutationEnvelope =
        post(Endpoint.KanbanLinksDelete(board), body)
    suspend fun skills(): SkillsResponse = get(Endpoint.Skills)
    suspend fun skillContent(name: String, file: String? = null): SkillContentResponse = get(Endpoint.SkillContent(name, file))
    suspend fun toggleSkill(name: String, enabled: Boolean): ToggleSkillResponse = post(Endpoint.ToggleSkill, ToggleSkillRequest(name, enabled))
    suspend fun memory(): MemoryResponse = get(Endpoint.Memory)
    suspend fun writeMemory(section: String, content: String): MemoryWriteResponse = post(Endpoint.MemoryWrite, MemoryWriteRequest(section, content))
    suspend fun gitInfo(sessionId: String): GitInfoResponse = get(Endpoint.GitInfo(sessionId))
    suspend fun gitStatus(sessionId: String): GitStatusResponse {
        val response: GitStatusEnvelope = get(Endpoint.GitStatus(sessionId))
        return response.git ?: throw ApiError.InvalidResponse(response.error ?: "The server did not return Git status.")
    }
    suspend fun gitBranches(sessionId: String): GitBranchesResponse = get(Endpoint.GitBranches(sessionId))
    suspend fun gitDiff(sessionId: String, path: String, kind: String = "unstaged"): GitDiffResponse {
        val response: GitDiffEnvelope = get(Endpoint.GitDiff(sessionId, path, kind))
        return response.diff ?: throw ApiError.InvalidResponse(response.error ?: "The server did not return a Git diff.")
    }
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
            maxResponseBytes = MAX_MEDIA_RESPONSE_BYTES,
        )

    suspend fun upload(sessionId: String, file: File, mimeType: String?): UploadResponse {
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
        return executeAndDecode(request)
    }

    suspend fun transcribe(file: File, mimeType: String? = "audio/mp4"): TranscribeResponse {
        val mediaType = (mimeType ?: "application/octet-stream").toMediaType()
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, file.asRequestBody(mediaType))
            .build()
        val request = requestBuilder(Endpoint.Transcribe)
            .post(body)
            .header("Accept", "application/json")
            .build()
        return executeAndDecode(request)
    }

    fun streamUrl(streamId: String, replayAfterSeq: Int? = null): HttpUrl =
        Endpoint.ChatStream(streamId, replayAfterSeq).url(baseUrl)

    private suspend inline fun <reified Response : Any> get(endpoint: Endpoint): Response =
        request<Response>(endpoint, "GET", encodedBody = null)

    private suspend inline fun <reified Response : Any, reified Body : Any> post(
        endpoint: Endpoint,
        body: Body,
        timeoutSeconds: Long? = null,
        bodyJson: Json = json,
    ): Response = request(endpoint, "POST", bodyJson.encodeToString(body).toRequestBody(jsonMediaType), timeoutSeconds)

    private suspend inline fun <reified Response : Any> request(
        endpoint: Endpoint,
        method: String,
        encodedBody: okhttp3.RequestBody?,
        timeoutSeconds: Long? = null,
    ): Response {
        val builder = requestBuilder(endpoint)
            .method(method, encodedBody)
            .header("Accept", "application/json")
        val request = builder.build()
        val callClient = timeoutSeconds?.let {
            client.newBuilder().callTimeout(it.seconds.inWholeMilliseconds, java.util.concurrent.TimeUnit.MILLISECONDS).build()
        } ?: client
        return executeAndDecode(callClient.newCall(request))
    }

    private suspend fun data(
        endpoint: Endpoint,
        method: String,
        encodedBody: okhttp3.RequestBody? = null,
        accept: String = "*/*",
        maxResponseBytes: Long = MAX_BINARY_RESPONSE_BYTES,
    ): ByteArray {
        val request = requestBuilder(endpoint)
            .method(method, encodedBody)
            .header("Accept", accept)
            .build()
        return executeData(client.newCall(request), maxResponseBytes)
    }

    private fun requestBuilder(endpoint: Endpoint): Request.Builder {
        return Request.Builder().url(endpoint.url(baseUrl))
    }

    private suspend inline fun <reified Response : Any> executeAndDecode(request: Request): Response =
        executeAndDecode(client.newCall(request))

    private suspend inline fun <reified Response : Any> executeAndDecode(call: Call): Response {
        val bytes = executeData(call, MAX_JSON_RESPONSE_BYTES)
        return try {
            json.decodeFromString<Response>(bytes.decodeToString())
        } catch (error: Throwable) {
            throw ApiError.Decoding(error)
        }
    }

    private suspend fun executeData(call: Call, maxResponseBytes: Long): ByteArray {
        return executeDataWithHeaders(call, maxResponseBytes = maxResponseBytes).bytes
    }

    private suspend fun executeDataWithHeaders(
        call: Call,
        headers: List<String> = emptyList(),
        maxResponseBytes: Long = MAX_JSON_RESPONSE_BYTES,
    ): RawResponse {
        val response = call.awaitResponse()
        try {
            return withContext(Dispatchers.IO) {
                val body = if (response.isSuccessful) {
                    response.body.readLimited(maxResponseBytes, failOnOverflow = true)
                } else {
                    response.body.readLimited(MAX_ERROR_RESPONSE_BYTES, failOnOverflow = false)
                }
                val bytes = body.bytes
                if (response.code == 401 && response.request.url.isSameOriginAs(baseUrl)) {
                    onUnauthorized(baseUrl)
                    throw ApiError.Unauthorized
                }
                if (!response.isSuccessful) {
                    val suffix = if (body.truncated) "\n[response truncated]" else ""
                    throw ApiError.Http(response.code, bytes.decodeToString() + suffix)
                }
                RawResponse(
                    bytes = bytes,
                    headers = headers.associateWith { name -> response.header(name) }
                        .filterValues { value -> value != null }
                        .mapValues { entry -> requireNotNull(entry.value) },
                )
            }
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { response.close() }
        }
    }

    private suspend fun executeFileWithHeaders(
        call: Call,
        destination: File,
        headers: List<String>,
        maxResponseBytes: Long,
    ): FileDownloadResponse {
        val response = call.awaitResponse()
        try {
            return withContext(Dispatchers.IO) {
                if (response.code == 401 && response.request.url.isSameOriginAs(baseUrl)) {
                    onUnauthorized(baseUrl)
                    throw ApiError.Unauthorized
                }
                if (!response.isSuccessful) {
                    val body = response.body.readLimited(MAX_ERROR_RESPONSE_BYTES, failOnOverflow = false)
                    val suffix = if (body.truncated) "\n[response truncated]" else ""
                    throw ApiError.Http(response.code, body.bytes.decodeToString() + suffix)
                }
                if (response.body.contentLength() > maxResponseBytes) {
                    throw ApiError.ResponseTooLarge(maxResponseBytes)
                }
                var completed = false
                try {
                    var total = 0L
                    response.body.byteStream().use { input ->
                        destination.outputStream().buffered().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                if (count == 0) continue
                                total += count
                                if (total > maxResponseBytes) throw ApiError.ResponseTooLarge(maxResponseBytes)
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    completed = true
                } finally {
                    if (!completed) destination.delete()
                }
                FileDownloadResponse(
                    headers = headers.associateWith { name -> response.header(name) }
                        .filterValues { value -> value != null }
                        .mapValues { entry -> requireNotNull(entry.value) },
                )
            }
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { response.close() }
        }
    }

    private data class RawResponse(
        val bytes: ByteArray,
        val headers: Map<String, String> = emptyMap(),
    )

    private data class FileDownloadResponse(
        val headers: Map<String, String> = emptyMap(),
    )

    companion object {
        private const val MAX_JSON_RESPONSE_BYTES = 16L * 1024L * 1024L
        private const val MAX_PREVIEW_RESPONSE_BYTES = 16L * 1024L * 1024L
        private const val MAX_MEDIA_RESPONSE_BYTES = 20L * 1024L * 1024L
        private const val MAX_REMOTE_MEDIA_RESPONSE_BYTES = 16L * 1024L * 1024L
        private const val MAX_BINARY_RESPONSE_BYTES = 64L * 1024L * 1024L
        private const val MAX_ERROR_RESPONSE_BYTES = 64L * 1024L
    }
}

private suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                val error = (e as? InsecureTransportIOException)?.apiError ?: ApiError.Network(e)
                if (continuation.isActive) continuation.resumeWith(Result.failure(error))
            }

            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive) {
                    continuation.resumeWith(Result.success(response))
                } else {
                    response.close()
                }
            }
        },
    )
}

private data class LimitedResponseBody(
    val bytes: ByteArray,
    val truncated: Boolean,
)

private fun okhttp3.ResponseBody.readLimited(limitBytes: Long, failOnOverflow: Boolean): LimitedResponseBody {
    if (contentLength() > limitBytes) {
        if (failOnOverflow) throw ApiError.ResponseTooLarge(limitBytes)
        return LimitedResponseBody(ByteArray(0), truncated = true)
    }

    val source = source()
    val buffer = Buffer()
    var total = 0L
    while (true) {
        val read = source.read(buffer, minOf(8L * 1024L, limitBytes - total + 1L))
        if (read == -1L) return LimitedResponseBody(buffer.readByteArray(), truncated = false)
        total += read
        if (total > limitBytes) {
            if (failOnOverflow) throw ApiError.ResponseTooLarge(limitBytes)
            return LimitedResponseBody(buffer.readByteArray(limitBytes), truncated = true)
        }
    }
}

private fun sessionExportFilename(
    contentDisposition: String?,
    fallbackTitle: String?,
    sessionId: String,
    format: SessionExportFormat,
): String {
    filenameParameter(contentDisposition)?.toSafeFilename()?.let { return it }
    fallbackTitle?.toSafeFilenameStem()?.let { return "$it.${format.fileExtension}" }
    val safeId = sessionId.toSafeFilenameStem() ?: "session"
    return "hermes-$safeId.${format.fileExtension}"
}

private fun filenameParameter(contentDisposition: String?): String? {
    if (contentDisposition.isNullOrBlank()) return null
    return contentDisposition
        .split(";")
        .drop(1)
        .firstNotNullOfOrNull { parameter ->
            val parts = parameter.split("=", limit = 2)
            if (parts.size != 2) return@firstNotNullOfOrNull null
            val key = parts[0].trim().lowercase()
            if (key != "filename") return@firstNotNullOfOrNull null
            parts[1].trim().removeSurrounding("\"").takeIf { it.isNotBlank() }
        }
}

private fun String.toSafeFilename(): String? {
    val lastComponent = replace('\\', '/').substringAfterLast('/')
    val cleaned = lastComponent.replaceUnsafeFilenameCharacters()
    return cleaned.takeIf { it.isNotBlank() && it != "." && it != ".." }
}

private fun String.toSafeFilenameStem(): String? =
    replaceUnsafeFilenameCharacters()
        .take(80)
        .takeIf { it.isNotBlank() }

private fun String.replaceUnsafeFilenameCharacters(): String =
    map { char ->
        if (char == '/' || char == '\\' || char == ':' || char.code < 32) ' ' else char
    }
        .joinToString("")
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .joinToString(" ")

private fun HttpUrl.isSameOriginAs(other: HttpUrl): Boolean =
    scheme == other.scheme && host.equals(other.host, ignoreCase = true) && port == other.port

internal fun rebaseLoopbackMediaUrl(url: HttpUrl, serverBaseUrl: HttpUrl): HttpUrl {
    if (!url.host.isLoopbackReferenceHost()) return url
    val basePath = serverBaseUrl.encodedPath.trimEnd('/')
    val mediaPath = url.encodedPath.trimStart('/')
    val resolvedPath = if (basePath.isEmpty()) "/$mediaPath" else "$basePath/$mediaPath"
    return serverBaseUrl.newBuilder()
        .encodedPath(resolvedPath)
        .encodedQuery(url.encodedQuery)
        .fragment(url.fragment)
        .build()
}

private fun createTemporaryExportDirectory(): File {
    val root = File(System.getProperty("java.io.tmpdir") ?: ".")
    val marker = File.createTempFile("hermex-session-export-", ".tmp", root)
    check(marker.delete() && marker.mkdir()) { "Could not prepare the export directory." }
    return marker
}

private fun String.isLoopbackReferenceHost(): Boolean {
    val normalized = trim().trimEnd('.').lowercase()
    if (normalized == "localhost" || normalized.endsWith(".localhost")) return true
    val isAddressLiteral = ':' in normalized || normalized.all { it.isDigit() || it == '.' }
    if (!isAddressLiteral) return false
    return runCatching { InetAddress.getByName(normalized) }
        .getOrNull()
        ?.let { it.isLoopbackAddress || it.isAnyLocalAddress }
        ?: false
}
