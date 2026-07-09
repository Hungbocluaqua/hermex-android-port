import Foundation

public enum HermexAPIError: Error, Equatable, Sendable {
    case network(String)
    case invalidResponse
    case unauthorized
    case http(statusCode: Int, body: String?)
    case decoding(String)
}

public protocol HermexHTTPTransport: Sendable {
    func data(for request: URLRequest) async throws -> (Data, HTTPURLResponse)
}

public final class HermexURLSessionTransport: HermexHTTPTransport, @unchecked Sendable {
    private let session: URLSession

    public init(session: URLSession = URLSession.shared) {
        self.session = session
    }

    public func data(for request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        do {
            let (data, response) = try await session.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse else {
                throw HermexAPIError.invalidResponse
            }
            return (data, httpResponse)
        } catch let error as HermexAPIError {
            throw error
        } catch {
            throw HermexAPIError.network(String(describing: error))
        }
    }
}

public struct HermexJSONObjectBody: Encodable, Equatable, Sendable {
    public var fields: [String: HermexJSONValue]

    public init(_ fields: [String: HermexJSONValue?] = [:]) {
        self.fields = fields.compactMapValues { $0 }
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(fields)
    }
}

public struct HermexAPIClient: @unchecked Sendable {
    private let requestBuilder: HermexAPIRequestBuilder
    private let transport: any HermexHTTPTransport
    private let encoder: JSONEncoder
    private let decoder: JSONDecoder

    public init(
        baseURL: URL,
        transport: any HermexHTTPTransport = HermexURLSessionTransport(),
        customHeaders: @escaping @Sendable () -> [HermexCustomHeader] = { [] }
    ) {
        self.requestBuilder = HermexAPIRequestBuilder(baseURL: baseURL, customHeaders: customHeaders)
        self.transport = transport

        let encoder = JSONEncoder()
        encoder.keyEncodingStrategy = JSONEncoder.KeyEncodingStrategy.convertToSnakeCase
        self.encoder = encoder

        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = JSONDecoder.KeyDecodingStrategy.convertFromSnakeCase
        self.decoder = decoder
    }

    public func health() async throws -> HermexJSONValue {
        try await sendJSON(endpoint: HermexEndpoints.health, method: "GET")
    }

    public func authStatus() async throws -> HermexJSONValue {
        try await sendJSON(endpoint: HermexEndpoints.authStatus, method: "GET")
    }

    public func login(password: String) async throws -> HermexJSONValue {
        try await sendJSON(
            endpoint: HermexEndpoints.login,
            method: "POST",
            body: HermexJSONObjectBody(["password": .string(password)])
        )
    }

    public func logout() async throws -> HermexJSONValue {
        try await sendJSON(endpoint: HermexEndpoints.logout, method: "POST", body: HermexJSONObjectBody())
    }

    public func sessions(includeArchived: Bool = false, archivedLimit: Int? = nil) async throws -> HermexSessionsResponse {
        try await sendSessionsResponse(endpoint: HermexEndpoints.sessions(includeArchived: includeArchived, archivedLimit: archivedLimit), method: "GET")
    }

    public func searchSessions(query: String, content: Bool = true, depth: Int = 5) async throws -> HermexJSONValue {
        try await sendJSON(endpoint: HermexEndpoints.sessionsSearch(query: query, content: content, depth: depth), method: "GET")
    }

    public func session(
        id: String,
        includeMessages: Bool = true,
        messageLimit: Int? = 50,
        messageBefore: Int? = nil,
        expandRenderable: Bool = false
    ) async throws -> HermexSessionResponse {
        try await sendSessionResponse(
            endpoint: HermexEndpoints.session(
                id: id,
                includeMessages: includeMessages,
                messageLimit: messageLimit,
                messageBefore: messageBefore,
                expandRenderable: expandRenderable
            ),
            method: "GET"
        )
    }

    public func sessionStatus(id: String) async throws -> HermexJSONValue {
        try await sendJSON(endpoint: HermexEndpoints.sessionStatus(id: id), method: "GET")
    }

    public func createSession(workspace: String? = nil, model: String? = nil, modelProvider: String? = nil, profile: String? = nil) async throws -> HermexSessionResponse {
        try await sendSessionResponse(
            endpoint: HermexEndpoints.newSession,
            method: "POST",
            body: HermexJSONObjectBody([
                "workspace": .stringOrNil(workspace),
                "model": .stringOrNil(model),
                "model_provider": .stringOrNil(modelProvider),
                "profile": .stringOrNil(profile)
            ])
        )
    }

    public func renameSession(id: String, title: String) async throws -> HermexJSONValue {
        try await postSessionMutation(HermexEndpoints.renameSession, fields: ["session_id": .string(id), "title": .string(title)])
    }

    public func deleteSession(id: String) async throws -> HermexJSONValue {
        try await postSessionID(HermexEndpoints.deleteSession, id: id)
    }

    public func pinSession(id: String, pinned: Bool) async throws -> HermexJSONValue {
        try await postSessionMutation(HermexEndpoints.pinSession, fields: ["session_id": .string(id), "pinned": .bool(pinned)])
    }

    public func archiveSession(id: String, archived: Bool) async throws -> HermexJSONValue {
        try await postSessionMutation(HermexEndpoints.archiveSession, fields: ["session_id": .string(id), "archived": .bool(archived)])
    }

    public func moveSession(id: String, projectID: String?) async throws -> HermexJSONValue {
        try await postSessionMutation(HermexEndpoints.moveSession, fields: ["session_id": .string(id), "project_id": .stringOrNil(projectID)])
    }

    public func branchSession(id: String, keepCount: Int? = nil, title: String? = nil) async throws -> HermexJSONValue {
        try await postSessionMutation(
            HermexEndpoints.branchSession,
            fields: ["session_id": .string(id), "keep_count": .intOrNil(keepCount), "title": .stringOrNil(title)]
        )
    }

    public func compressSession(id: String, focusTopic: String? = nil) async throws -> HermexJSONValue {
        try await sendJSON(
            endpoint: HermexEndpoints.compressSession,
            method: "POST",
            body: HermexJSONObjectBody(["session_id": .string(id), "focus_topic": .stringOrNil(focusTopic)]),
            timeout: 120
        )
    }

    public func undoSession(id: String) async throws -> HermexJSONValue {
        try await postSessionID(HermexEndpoints.undoSession, id: id)
    }

    public func retrySession(id: String) async throws -> HermexJSONValue {
        try await postSessionID(HermexEndpoints.retrySession, id: id)
    }

    public func truncateSession(id: String, keepCount: Int) async throws -> HermexSessionResponse {
        try await sendSessionResponse(
            endpoint: HermexEndpoints.truncateSession,
            method: "POST",
            body: HermexJSONObjectBody(["session_id": .string(id), "keep_count": .number(Double(keepCount))])
        )
    }

    public func updateSession(id: String, workspace: String? = nil, model: String? = nil, modelProvider: String? = nil) async throws -> HermexSessionResponse {
        try await sendSessionResponse(
            endpoint: HermexEndpoints.updateSession,
            method: "POST",
            body: HermexJSONObjectBody([
                "session_id": .string(id),
                "workspace": .stringOrNil(workspace),
                "model": .stringOrNil(model),
                "model_provider": .stringOrNil(modelProvider)
            ])
        )
    }

    public func sessionYolo(sessionID: String) async throws -> HermexJSONValue {
        try await sendJSON(endpoint: HermexEndpoints.sessionYolo(id: sessionID), method: "GET")
    }

    public func setSessionYolo(sessionID: String, enabled: Bool) async throws -> HermexJSONValue {
        try await sendJSON(
            endpoint: HermexEndpoints.sessionYolo(id: nil),
            method: "POST",
            body: HermexJSONObjectBody(["session_id": .string(sessionID), "enabled": .bool(enabled)])
        )
    }

    public func projects() async throws -> HermexJSONValue {
        try await sendJSON(endpoint: HermexEndpoints.projects, method: "GET")
    }

    public func createProject(name: String) async throws -> HermexJSONValue {
        try await sendJSON(endpoint: HermexEndpoints.createProject, method: "POST", body: HermexJSONObjectBody(["name": .string(name)]))
    }

    public func renameProject(projectID: String, name: String) async throws -> HermexJSONValue {
        try await sendJSON(
            endpoint: HermexEndpoints.renameProject,
            method: "POST",
            body: HermexJSONObjectBody(["project_id": .string(projectID), "name": .string(name)])
        )
    }

    public func deleteProject(projectID: String) async throws -> HermexJSONValue {
        try await sendJSON(endpoint: HermexEndpoints.deleteProject, method: "POST", body: HermexJSONObjectBody(["project_id": .string(projectID)]))
    }

    public func chatStart(
        sessionID: String? = nil,
        message: String,
        workspace: String? = nil,
        model: String? = nil,
        modelProvider: String? = nil,
        profile: String? = nil,
        explicitModelPick: Bool = false,
        attachments: [HermexJSONValue]? = nil
    ) async throws -> HermexJSONValue {
        var fields: [String: HermexJSONValue?] = [
            "session_id": .stringOrNil(sessionID),
            "message": .string(message),
            "workspace": .stringOrNil(workspace),
            "model": .stringOrNil(model),
            "model_provider": .stringOrNil(modelProvider),
            "profile": .stringOrNil(profile),
            "explicit_model_pick": .bool(explicitModelPick)
        ]
        if let attachments {
            fields["attachments"] = .array(attachments)
        }
        let body = HermexJSONObjectBody(fields)

        let response: HermexJSONValue = try await sendJSON(
            endpoint: HermexEndpoints.chatStart,
            method: "POST",
            body: body
        )
        return response
    }

    public func chatCancel(streamID: String) async throws -> HermexJSONValue {
        try await sendJSON(endpoint: HermexEndpoints.chatCancel(streamID: streamID), method: "GET")
    }

    public func chatStreamStatus(streamID: String) async throws -> HermexJSONValue {
        try await sendJSON(endpoint: HermexEndpoints.chatStreamStatus(streamID: streamID), method: "GET")
    }

    public func chatSteer(sessionID: String, text: String) async throws -> HermexJSONValue {
        try await sendJSON(
            endpoint: HermexEndpoints.chatSteer,
            method: "POST",
            body: HermexJSONObjectBody(["session_id": .string(sessionID), "text": .string(text)])
        )
    }

    public func submitGoal(sessionID: String, args: String, workspace: String? = nil, model: String? = nil, modelProvider: String? = nil, profile: String? = nil) async throws -> HermexJSONValue {
        try await sendJSON(
            endpoint: HermexEndpoints.submitGoal,
            method: "POST",
            body: HermexJSONObjectBody([
                "session_id": .string(sessionID),
                "args": .string(args),
                "workspace": .stringOrNil(workspace),
                "model": .stringOrNil(model),
                "model_provider": .stringOrNil(modelProvider),
                "profile": .stringOrNil(profile)
            ]),
            timeout: 120
        )
    }

    public func startBtw(sessionID: String, question: String) async throws -> HermexJSONValue {
        try await sendJSON(endpoint: HermexEndpoints.btw, method: "POST", body: HermexJSONObjectBody(["session_id": .string(sessionID), "question": .string(question)]))
    }

    public func startBackground(sessionID: String, prompt: String) async throws -> HermexJSONValue {
        try await sendJSON(endpoint: HermexEndpoints.background, method: "POST", body: HermexJSONObjectBody(["session_id": .string(sessionID), "prompt": .string(prompt)]))
    }

    public func backgroundStatus(sessionID: String) async throws -> HermexJSONValue {
        try await sendJSON(endpoint: HermexEndpoints.backgroundStatus(sessionID: sessionID), method: "GET")
    }

    public func approvalPending(sessionID: String) async throws -> HermexJSONValue {
        try await sendJSON(endpoint: HermexEndpoints.approvalPending(sessionID: sessionID), method: "GET")
    }

    public func approvalStreamURL(sessionID: String) -> URL {
        HermexEndpoints.approvalStream(sessionID: sessionID).url(relativeTo: requestBuilder.baseURL)
    }

    public func respondApproval(sessionID: String, choice: String, approvalID: String? = nil) async throws -> HermexJSONValue {
        try await sendJSON(
            endpoint: HermexEndpoints.approvalRespond,
            method: "POST",
            body: HermexJSONObjectBody(["session_id": .string(sessionID), "choice": .string(choice), "approval_id": .stringOrNil(approvalID)])
        )
    }

    public func clarifyPending(sessionID: String) async throws -> HermexJSONValue {
        try await sendJSON(endpoint: HermexEndpoints.clarifyPending(sessionID: sessionID), method: "GET")
    }

    public func clarifyStreamURL(sessionID: String) -> URL {
        HermexEndpoints.clarifyStream(sessionID: sessionID).url(relativeTo: requestBuilder.baseURL)
    }

    public func respondClarification(sessionID: String, response: String, clarifyID: String? = nil) async throws -> HermexJSONValue {
        try await sendJSON(
            endpoint: HermexEndpoints.clarifyRespond,
            method: "POST",
            body: HermexJSONObjectBody(["session_id": .string(sessionID), "clarify_id": .stringOrNil(clarifyID), "response": .string(response)])
        )
    }

    public func workspaces() async throws -> HermexJSONValue {
        try await sendJSON(endpoint: HermexEndpoints.workspaces, method: "GET")
    }

    public func workspacesResponse() async throws -> HermexWorkspacesResponse {
        try await sendWorkspacesResponse(endpoint: HermexEndpoints.workspaces, method: "GET")
    }

    public func workspaceSuggestions(prefix: String) async throws -> HermexJSONValue {
        try await sendJSON(endpoint: HermexEndpoints.workspaceSuggestions(prefix: prefix), method: "GET")
    }

    public func directoryList(sessionID: String, path: String? = nil) async throws -> HermexJSONValue {
        try await sendJSON(endpoint: HermexEndpoints.directoryList(sessionID: sessionID, path: path), method: "GET")
    }

    public func file(sessionID: String, path: String) async throws -> HermexJSONValue {
        try await sendJSON(endpoint: HermexEndpoints.file(sessionID: sessionID, path: path), method: "GET")
    }

    public func rawFile(sessionID: String, path: String) async throws -> Data {
        try await sendData(endpoint: HermexEndpoints.rawFile(sessionID: sessionID, path: path), method: "GET", accept: "*/*")
    }

    public func media(path: String) async throws -> Data {
        try await sendData(endpoint: HermexEndpoints.media(path: path), method: "GET", accept: "*/*")
    }

    public func gitInfo(sessionID: String) async throws -> HermexJSONValue {
        try await sendJSON(endpoint: HermexEndpoints.gitInfo(sessionID: sessionID), method: "GET")
    }

    public func gitStatus(sessionID: String) async throws -> HermexJSONValue {
        try await sendJSON(endpoint: HermexEndpoints.gitStatus(sessionID: sessionID), method: "GET")
    }

    public func gitBranches(sessionID: String) async throws -> HermexJSONValue {
        try await sendJSON(endpoint: HermexEndpoints.gitBranches(sessionID: sessionID), method: "GET")
    }

    public func gitDiff(sessionID: String, path: String, kind: String = "unstaged") async throws -> HermexJSONValue {
        try await sendJSON(endpoint: HermexEndpoints.gitDiff(sessionID: sessionID, path: path, kind: kind), method: "GET")
    }

    public func gitFetch(sessionID: String) async throws -> HermexJSONValue { try await gitSessionAction(HermexEndpoints.gitFetch, sessionID: sessionID) }
    public func gitPull(sessionID: String) async throws -> HermexJSONValue { try await gitSessionAction(HermexEndpoints.gitPull, sessionID: sessionID) }
    public func gitPush(sessionID: String) async throws -> HermexJSONValue { try await gitSessionAction(HermexEndpoints.gitPush, sessionID: sessionID) }

    public func gitStage(sessionID: String, paths: [String]) async throws -> HermexJSONValue {
        try await gitPathsAction(HermexEndpoints.gitStage, sessionID: sessionID, paths: paths)
    }

    public func gitUnstage(sessionID: String, paths: [String]) async throws -> HermexJSONValue {
        try await gitPathsAction(HermexEndpoints.gitUnstage, sessionID: sessionID, paths: paths)
    }

    public func gitDiscard(sessionID: String, paths: [String], deleteUntracked: Bool = false) async throws -> HermexJSONValue {
        try await sendJSON(
            endpoint: HermexEndpoints.gitDiscard,
            method: "POST",
            body: HermexJSONObjectBody(["session_id": .string(sessionID), "paths": .strings(paths), "delete_untracked": .bool(deleteUntracked)])
        )
    }

    public func gitCommit(sessionID: String, message: String) async throws -> HermexJSONValue {
        try await sendJSON(
            endpoint: HermexEndpoints.gitCommit,
            method: "POST",
            body: HermexJSONObjectBody(["session_id": .string(sessionID), "message": .string(message)]),
            timeout: 120
        )
    }

    public func gitCommitSelected(sessionID: String, message: String, paths: [String]) async throws -> HermexJSONValue {
        try await sendJSON(
            endpoint: HermexEndpoints.gitCommitSelected,
            method: "POST",
            body: HermexJSONObjectBody(["session_id": .string(sessionID), "message": .string(message), "paths": .strings(paths)]),
            timeout: 120
        )
    }

    public func gitCommitMessage(sessionID: String) async throws -> HermexJSONValue {
        try await sendJSON(endpoint: HermexEndpoints.gitCommitMessage, method: "POST", body: HermexJSONObjectBody(["session_id": .string(sessionID)]), timeout: 120)
    }

    public func gitCommitMessageSelected(sessionID: String, paths: [String]) async throws -> HermexJSONValue {
        try await sendJSON(endpoint: HermexEndpoints.gitCommitMessageSelected, method: "POST", body: HermexJSONObjectBody(["session_id": .string(sessionID), "paths": .strings(paths)]), timeout: 120)
    }

    public func models() async throws -> HermexModelsResponse {
        try await sendModelsResponse(endpoint: HermexEndpoints.models, method: "GET")
    }

    public func modelsLive() async throws -> HermexJSONValue { try await sendJSON(endpoint: HermexEndpoints.modelsLive, method: "GET") }
    public func commands() async throws -> HermexJSONValue { try await sendJSON(endpoint: HermexEndpoints.commands, method: "GET") }
    public func personalities() async throws -> HermexJSONValue { try await sendJSON(endpoint: HermexEndpoints.personalities, method: "GET") }
    public func profiles() async throws -> HermexJSONValue { try await sendJSON(endpoint: HermexEndpoints.profiles, method: "GET") }
    public func profilesResponse() async throws -> HermexProfilesResponse { try await sendProfilesResponse(endpoint: HermexEndpoints.profiles, method: "GET") }
    public func providers() async throws -> HermexJSONValue { try await sendJSON(endpoint: HermexEndpoints.providers, method: "GET") }
    public func settings() async throws -> HermexJSONValue { try await sendJSON(endpoint: HermexEndpoints.settings, method: "GET") }
    public func updatesCheck() async throws -> HermexJSONValue { try await sendJSON(endpoint: HermexEndpoints.updatesCheck, method: "GET") }

    public func switchProfile(name: String) async throws -> HermexJSONValue {
        try await sendJSON(endpoint: HermexEndpoints.switchProfile, method: "POST", body: HermexJSONObjectBody(["name": .string(name)]))
    }

    public func createProfile(name: String, cloneConfig: Bool = false, defaultModel: String? = nil, modelProvider: String? = nil, baseURL: String? = nil, apiKey: String? = nil) async throws -> HermexJSONValue {
        try await sendJSON(
            endpoint: HermexEndpoints.createProfile,
            method: "POST",
            body: HermexJSONObjectBody([
                "name": .string(name),
                "clone_config": .bool(cloneConfig),
                "default_model": .stringOrNil(defaultModel),
                "model_provider": .stringOrNil(modelProvider),
                "base_url": .stringOrNil(baseURL),
                "api_key": .stringOrNil(apiKey)
            ])
        )
    }

    public func saveDefaultModel(model: String) async throws -> HermexJSONValue {
        try await sendJSON(endpoint: HermexEndpoints.defaultModel, method: "POST", body: HermexJSONObjectBody(["model": .string(model)]))
    }

    public func reasoning(model: String? = nil, provider: String? = nil) async throws -> HermexJSONValue {
        try await sendJSON(endpoint: HermexEndpoints.reasoning(model: model, provider: provider), method: "GET")
    }

    public func reasoningResponse(model: String? = nil, provider: String? = nil) async throws -> HermexReasoningResponse {
        try await sendReasoningResponse(endpoint: HermexEndpoints.reasoning(model: model, provider: provider), method: "GET")
    }

    public func saveReasoningEffort(_ effort: String, model: String? = nil, provider: String? = nil) async throws -> HermexJSONValue {
        try await sendJSON(
            endpoint: HermexEndpoints.reasoning(model: model, provider: provider),
            method: "POST",
            body: HermexJSONObjectBody(["effort": .string(effort), "model": .stringOrNil(model), "provider": .stringOrNil(provider)])
        )
    }

    public func setPersonality(sessionID: String, name: String) async throws -> HermexJSONValue {
        try await sendJSON(endpoint: HermexEndpoints.setPersonality, method: "POST", body: HermexJSONObjectBody(["session_id": .string(sessionID), "name": .string(name)]))
    }

    public func updateSettings(showCliSessions: Bool) async throws -> HermexJSONValue {
        try await sendJSON(endpoint: HermexEndpoints.settings, method: "POST", body: HermexJSONObjectBody(["show_cli_sessions": .bool(showCliSessions)]))
    }

    public func updatesCheckForced() async throws -> HermexJSONValue {
        try await sendJSON(endpoint: HermexEndpoints.updatesCheck, method: "POST", body: HermexJSONObjectBody(["force": .bool(true)]))
    }

    public func applyUpdate(target: String = "webui") async throws -> HermexJSONValue {
        try await sendJSON(endpoint: HermexEndpoints.updatesApply, method: "POST", body: HermexJSONObjectBody(["target": .string(target)]))
    }

    public func insights(days: Int) async throws -> HermexJSONValue {
        try await sendJSON(endpoint: HermexEndpoints.insights(days: days), method: "GET")
    }

    public func crons() async throws -> HermexJSONValue { try await sendJSON(endpoint: HermexEndpoints.crons, method: "GET") }
    public func cronStatus(jobID: String? = nil) async throws -> HermexJSONValue { try await sendJSON(endpoint: HermexEndpoints.cronStatus(jobID: jobID), method: "GET") }
    public func cronOutput(jobID: String, limit: Int? = 5) async throws -> HermexJSONValue { try await sendJSON(endpoint: HermexEndpoints.cronOutput(jobID: jobID, limit: limit), method: "GET") }
    public func cronHistory(jobID: String, offset: Int? = nil, limit: Int? = 50) async throws -> HermexJSONValue { try await sendJSON(endpoint: HermexEndpoints.cronHistory(jobID: jobID, offset: offset, limit: limit), method: "GET") }
    public func cronDeliveryOptions() async throws -> HermexJSONValue { try await sendJSON(endpoint: HermexEndpoints.cronDeliveryOptions, method: "GET") }
    public func skills() async throws -> HermexJSONValue { try await sendJSON(endpoint: HermexEndpoints.skills, method: "GET") }
    public func skillContent(name: String, file: String? = nil) async throws -> HermexJSONValue { try await sendJSON(endpoint: HermexEndpoints.skillContent(name: name, file: file), method: "GET") }
    public func memory() async throws -> HermexJSONValue { try await sendJSON(endpoint: HermexEndpoints.memory, method: "GET") }

    public func toggleSkill(name: String, enabled: Bool) async throws -> HermexJSONValue {
        try await sendJSON(endpoint: HermexEndpoints.toggleSkill, method: "POST", body: HermexJSONObjectBody(["name": .string(name), "enabled": .bool(enabled)]))
    }

    public func writeMemory(section: String, content: String) async throws -> HermexJSONValue {
        try await sendJSON(endpoint: HermexEndpoints.memoryWrite, method: "POST", body: HermexJSONObjectBody(["section": .string(section), "content": .string(content)]))
    }

    public func synthesizeSpeech(text: String, voice: String) async throws -> Data {
        try await sendData(
            endpoint: HermexEndpoints.tts,
            method: "POST",
            body: HermexJSONObjectBody(["text": .string(text), "voice": .string(voice)]),
            accept: "audio/mpeg"
        )
    }

    public func uploadFile(sessionID: String, data: Data, filename: String, contentType: String = "application/octet-stream") async throws -> HermexUploadResponse {
        let form = HermexMultipartFormDataBuilder.build(
            textFields: ["session_id": sessionID],
            files: [HermexMultipartFile(filename: filename, data: data, contentType: contentType)]
        )
        let responseData = try await sendBodyData(
            endpoint: HermexEndpoints.upload,
            method: "POST",
            body: form.body,
            accept: "application/json",
            contentType: form.contentType
        )
        return try decodeUploadResponse(from: responseData)
    }

    public func transcribeAudio(data: Data, filename: String, contentType: String = "application/octet-stream") async throws -> HermexTranscribeResponse {
        let form = HermexMultipartFormDataBuilder.build(
            files: [HermexMultipartFile(filename: filename, data: data, contentType: contentType)]
        )
        let (responseData, response) = try await sendBodyDataReturningResponse(
            endpoint: HermexEndpoints.transcribe,
            method: "POST",
            body: form.body,
            accept: "application/json",
            contentType: form.contentType
        )

        if response.statusCode == 401 {
            throw HermexAPIError.unauthorized
        }

        if let decoded = try? decoder.decode(HermexTranscribeResponse.self, from: responseData) {
            return decoded
        }

        guard (200..<300).contains(response.statusCode) else {
            throw HermexAPIError.http(statusCode: response.statusCode, body: String(data: responseData, encoding: String.Encoding.utf8))
        }

        return try decodeTranscribeResponse(from: responseData)
    }

    public func streamURL(streamID: String, replayAfterSeq: Int? = nil) -> URL {
        HermexEndpoints.chatStream(id: streamID, replayAfterSeq: replayAfterSeq).url(relativeTo: requestBuilder.baseURL)
    }

    public func request(endpoint: HermexEndpoint, method: String, body: Data? = nil, accept: String = "application/json", timeout: TimeInterval? = nil) -> URLRequest {
        requestBuilder.request(endpoint: endpoint, method: method, body: body, accept: accept, timeout: timeout)
    }

    private func sendJSON(
        endpoint: HermexEndpoint,
        method: String,
        body: (any Encodable)? = nil,
        timeout: TimeInterval? = nil,
        accept: String = "application/json"
    ) async throws -> HermexJSONValue {
        let data = try await sendData(endpoint: endpoint, method: method, body: body, timeout: timeout, accept: accept)
        return try decodeJSONValue(from: data)
    }

    public func sendData(
        endpoint: HermexEndpoint,
        method: String,
        body: (any Encodable)? = nil,
        timeout: TimeInterval? = nil,
        accept: String = "application/json"
    ) async throws -> Data {
        let encodedBody: Data?
        do {
            encodedBody = try body.map { try encoder.encode(HermexAnyEncodable($0)) }
        } catch {
            throw HermexAPIError.decoding(String(describing: error))
        }

        let request = requestBuilder.request(endpoint: endpoint, method: method, body: encodedBody, accept: accept, timeout: timeout)
        let (data, response) = try await execute(request)

        if response.statusCode == 401 {
            throw HermexAPIError.unauthorized
        }

        guard (200..<300).contains(response.statusCode) else {
            throw HermexAPIError.http(statusCode: response.statusCode, body: String(data: data, encoding: String.Encoding.utf8))
        }

        return data
    }

    public func sendBodyData(
        endpoint: HermexEndpoint,
        method: String,
        body: Data?,
        accept: String = "application/json",
        contentType: String? = nil,
        timeout: TimeInterval? = nil
    ) async throws -> Data {
        let (data, response) = try await sendBodyDataReturningResponse(
            endpoint: endpoint,
            method: method,
            body: body,
            accept: accept,
            contentType: contentType,
            timeout: timeout
        )

        if response.statusCode == 401 {
            throw HermexAPIError.unauthorized
        }

        guard (200..<300).contains(response.statusCode) else {
            throw HermexAPIError.http(statusCode: response.statusCode, body: String(data: data, encoding: String.Encoding.utf8))
        }

        return data
    }

    public func sendBodyDataReturningResponse(
        endpoint: HermexEndpoint,
        method: String,
        body: Data?,
        accept: String = "application/json",
        contentType: String? = nil,
        timeout: TimeInterval? = nil
    ) async throws -> (Data, HTTPURLResponse) {
        let request = requestBuilder.request(
            endpoint: endpoint,
            method: method,
            body: body,
            accept: accept,
            contentType: contentType,
            timeout: timeout
        )
        return try await execute(request)
    }

    private func decodeJSONValue(from data: Data) throws -> HermexJSONValue {
        do {
            return try decoder.decode(HermexJSONValue.self, from: data)
        } catch {
            throw HermexAPIError.decoding(String(describing: error))
        }
    }

    private func sendSessionsResponse(
        endpoint: HermexEndpoint,
        method: String,
        body: (any Encodable)? = nil,
        timeout: TimeInterval? = nil,
        accept: String = "application/json"
    ) async throws -> HermexSessionsResponse {
        let data = try await sendData(endpoint: endpoint, method: method, body: body, timeout: timeout, accept: accept)
        return try decodeSessionsResponse(from: data)
    }

    private func decodeSessionsResponse(from data: Data) throws -> HermexSessionsResponse {
        do {
            return try decoder.decode(HermexSessionsResponse.self, from: data)
        } catch {
            throw HermexAPIError.decoding(String(describing: error))
        }
    }

    private func sendSessionResponse(
        endpoint: HermexEndpoint,
        method: String,
        body: (any Encodable)? = nil,
        timeout: TimeInterval? = nil,
        accept: String = "application/json"
    ) async throws -> HermexSessionResponse {
        let data = try await sendData(endpoint: endpoint, method: method, body: body, timeout: timeout, accept: accept)
        return try decodeSessionResponse(from: data)
    }

    private func decodeSessionResponse(from data: Data) throws -> HermexSessionResponse {
        do {
            return try decoder.decode(HermexSessionResponse.self, from: data)
        } catch {
            throw HermexAPIError.decoding(String(describing: error))
        }
    }

    private func sendWorkspacesResponse(
        endpoint: HermexEndpoint,
        method: String,
        body: (any Encodable)? = nil,
        timeout: TimeInterval? = nil,
        accept: String = "application/json"
    ) async throws -> HermexWorkspacesResponse {
        let data = try await sendData(endpoint: endpoint, method: method, body: body, timeout: timeout, accept: accept)
        return try decodeWorkspacesResponse(from: data)
    }

    private func decodeWorkspacesResponse(from data: Data) throws -> HermexWorkspacesResponse {
        do {
            return try decoder.decode(HermexWorkspacesResponse.self, from: data)
        } catch {
            throw HermexAPIError.decoding(String(describing: error))
        }
    }

    private func sendModelsResponse(
        endpoint: HermexEndpoint,
        method: String,
        body: (any Encodable)? = nil,
        timeout: TimeInterval? = nil,
        accept: String = "application/json"
    ) async throws -> HermexModelsResponse {
        let data = try await sendData(endpoint: endpoint, method: method, body: body, timeout: timeout, accept: accept)
        return try decodeModelsResponse(from: data)
    }

    private func decodeModelsResponse(from data: Data) throws -> HermexModelsResponse {
        do {
            return try decoder.decode(HermexModelsResponse.self, from: data)
        } catch {
            throw HermexAPIError.decoding(String(describing: error))
        }
    }

    private func sendProfilesResponse(
        endpoint: HermexEndpoint,
        method: String,
        body: (any Encodable)? = nil,
        timeout: TimeInterval? = nil,
        accept: String = "application/json"
    ) async throws -> HermexProfilesResponse {
        let data = try await sendData(endpoint: endpoint, method: method, body: body, timeout: timeout, accept: accept)
        return try decodeProfilesResponse(from: data)
    }

    private func decodeProfilesResponse(from data: Data) throws -> HermexProfilesResponse {
        do {
            return try decoder.decode(HermexProfilesResponse.self, from: data)
        } catch {
            throw HermexAPIError.decoding(String(describing: error))
        }
    }

    private func sendReasoningResponse(
        endpoint: HermexEndpoint,
        method: String,
        body: (any Encodable)? = nil,
        timeout: TimeInterval? = nil,
        accept: String = "application/json"
    ) async throws -> HermexReasoningResponse {
        let data = try await sendData(endpoint: endpoint, method: method, body: body, timeout: timeout, accept: accept)
        return try decodeReasoningResponse(from: data)
    }

    private func decodeReasoningResponse(from data: Data) throws -> HermexReasoningResponse {
        do {
            return try decoder.decode(HermexReasoningResponse.self, from: data)
        } catch {
            throw HermexAPIError.decoding(String(describing: error))
        }
    }

    private func decodeUploadResponse(from data: Data) throws -> HermexUploadResponse {
        do {
            return try decoder.decode(HermexUploadResponse.self, from: data)
        } catch {
            throw HermexAPIError.decoding(String(describing: error))
        }
    }

    private func decodeTranscribeResponse(from data: Data) throws -> HermexTranscribeResponse {
        do {
            return try decoder.decode(HermexTranscribeResponse.self, from: data)
        } catch {
            throw HermexAPIError.decoding(String(describing: error))
        }
    }

    private func execute(_ request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        do {
            return try await transport.data(for: request)
        } catch let error as HermexAPIError {
            throw error
        } catch {
            throw HermexAPIError.network(String(describing: error))
        }
    }

    private func postSessionID(_ endpoint: HermexEndpoint, id: String) async throws -> HermexJSONValue {
        try await postSessionMutation(endpoint, fields: ["session_id": .string(id)])
    }

    private func postSessionMutation(_ endpoint: HermexEndpoint, fields: [String: HermexJSONValue?]) async throws -> HermexJSONValue {
        try await sendJSON(endpoint: endpoint, method: "POST", body: HermexJSONObjectBody(fields))
    }

    private func gitSessionAction(_ endpoint: HermexEndpoint, sessionID: String) async throws -> HermexJSONValue {
        try await sendJSON(endpoint: endpoint, method: "POST", body: HermexJSONObjectBody(["session_id": .string(sessionID)]))
    }

    private func gitPathsAction(_ endpoint: HermexEndpoint, sessionID: String, paths: [String]) async throws -> HermexJSONValue {
        try await sendJSON(endpoint: endpoint, method: "POST", body: HermexJSONObjectBody(["session_id": .string(sessionID), "paths": .strings(paths)]))
    }
}

private struct HermexAnyEncodable: Encodable {
    private let value: any Encodable

    init(_ value: any Encodable) {
        self.value = value
    }

    func encode(to encoder: Encoder) throws {
        try value.encode(to: encoder)
    }
}

private extension HermexJSONValue {
    static func stringOrNil(_ value: String?) -> HermexJSONValue? {
        guard let value else { return nil }
        return .string(value)
    }

    static func intOrNil(_ value: Int?) -> HermexJSONValue? {
        guard let value else { return nil }
        return .number(Double(value))
    }

    static func strings(_ values: [String]) -> HermexJSONValue {
        .array(values.map { HermexJSONValue.string($0) })
    }
}
