import Foundation
import SwiftUI
import HermexCore
import HermexPlatform
import HermexUI

#if SKIP
import android.media.MediaRecorder
import android.webkit.MimeTypeMap
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.registerForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
#endif

private let hermexVisualFixtureName: String? = nil
private let hermexRuntimeVisualFixturesEnabled = false
private let hermexRuntimeVisualFixtureFileName = "hermex_visual_fixture.txt"
private let hermexAndroidRuntimeVisualFixturePath = "/data/data/com.uzairansar.hermex/files/hermex_visual_fixture.txt"

private func resolvedHermexVisualFixtureName() -> String? {
    if let fixtureName = hermexVisualFixtureName?.trimmingCharacters(in: .whitespacesAndNewlines),
       !fixtureName.isEmpty {
        return fixtureName
    }

    if let fixtureName = ProcessInfo.processInfo.environment["HERMEX_VISUAL_FIXTURE_NAME"]?.trimmingCharacters(in: .whitespacesAndNewlines),
       !fixtureName.isEmpty {
        return fixtureName
    }

    if hermexRuntimeVisualFixturesEnabled {
        #if SKIP
        let selectorURL = URL(fileURLWithPath: hermexAndroidRuntimeVisualFixturePath)
        if let selectorData = try? Data(contentsOf: selectorURL),
           let fixtureName = String(data: selectorData, encoding: String.Encoding.utf8)?
            .trimmingCharacters(in: .whitespacesAndNewlines),
           !fixtureName.isEmpty {
            return fixtureName
        }
        #else
        if let documentDirectory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first {
            let selectorURL = documentDirectory.appendingPathComponent(hermexRuntimeVisualFixtureFileName)
            if let fixtureName = try? String(contentsOf: selectorURL, encoding: String.Encoding.utf8)
                .trimmingCharacters(in: .whitespacesAndNewlines),
               !fixtureName.isEmpty {
                return fixtureName
            }
        }
        #endif
    }

    return nil
}

public struct ContentView: View {
    public init() {}

    public var body: some View {
        HermexSkipAppRootView()
    }
}

public struct HermexSkipAppRootView: View {
    @State private var runtime = HermexSkipRuntime()

    public init() {}

    public var body: some View {
        if let fixtureName = resolvedHermexVisualFixtureName(),
           let fixture = HermexVisualFixtureCatalog.fixture(named: fixtureName) {
            HermexVisualFixtureRootScreen(fixture: fixture)
        } else {
            HermexStoreRootScreen(
                store: runtime.store,
                onUnhandledEvent: runtime.handleUnhandledEvent,
                onActionCompleted: runtime.handleActionCompleted
            )
            .task {
                await runtime.bootstrap()
            }
        }
    }
}

public final class HermexSkipAppAppDelegate: Sendable {
    public static let shared = HermexSkipAppAppDelegate()

    private init() {}

    public func onInit() {}
    public func onLaunch() {}
    public func onResume() {}
    public func onPause() {}
    public func onStop() {}
    public func onDestroy() {}
    public func onLowMemory() {}
}

public typealias HermexSkipRootView = HermexSkipAppRootView
public final class HermexSkipAppDelegate: Sendable {
    public static let shared = HermexSkipAppDelegate()

    private init() {}

    public func onInit() {
        HermexSkipAppAppDelegate.shared.onInit()
    }

    public func onLaunch() {
        HermexSkipAppAppDelegate.shared.onLaunch()
    }

    public func onResume() {
        HermexSkipAppAppDelegate.shared.onResume()
    }

    public func onPause() {
        HermexSkipAppAppDelegate.shared.onPause()
    }

    public func onStop() {
        HermexSkipAppAppDelegate.shared.onStop()
    }

    public func onDestroy() {
        HermexSkipAppAppDelegate.shared.onDestroy()
    }

    public func onLowMemory() {
        HermexSkipAppAppDelegate.shared.onLowMemory()
    }
}

#if SKIP
private final class HermexSkipAttachmentPicker: HermexAttachmentPicker, @unchecked Sendable {
    private var documentLauncher: ActivityResultLauncher<kotlin.Array<String>>?
    private var photoLauncher: ActivityResultLauncher<kotlin.Array<String>>?
    private let documentContinuations: MutableList<Continuation<[URL]>> = mutableListOf<Continuation<[URL]>>()
    private let photoContinuations: MutableList<Continuation<[URL]>> = mutableListOf<Continuation<[URL]>>()

    init() {
        guard let activity = UIApplication.shared.androidActivity else { return }
        documentLauncher = activity.registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris in
            self.finish(uris, continuations: self.documentContinuations)
        }
        photoLauncher = activity.registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris in
            self.finish(uris, continuations: self.photoContinuations)
        }
    }

    func pickDocuments() async throws -> [URL] {
        guard let launcher = documentLauncher else {
            throw HermexAPIError.network("Android file picker is unavailable.")
        }
        return await suspendCoroutine { continuation in
            documentContinuations.add(continuation)
            launcher.launch(arrayOf("*/*"))
        }
    }

    func pickPhotos() async throws -> [URL] {
        guard let launcher = photoLauncher else {
            throw HermexAPIError.network("Android photo picker is unavailable.")
        }
        return await suspendCoroutine { continuation in
            photoContinuations.add(continuation)
            launcher.launch(arrayOf("image/*"))
        }
    }

    private func finish(
        _ uris: List<android.net.Uri>,
        continuations: MutableList<Continuation<[URL]>>
    ) {
        var waiting: ArrayList<Continuation<[URL]>>? = nil
        synchronized(continuations) {
            waiting = ArrayList(continuations)
            continuations.clear()
        }
        let files = uris.mapNotNull { copyToCache($0) }
        waiting?.forEach { $0.resume(files) }
    }

    private func copyToCache(_ uri: android.net.Uri) -> URL? {
        guard let sourceURL = URL(string: uri.toString()),
              let data = try? Data(contentsOf: sourceURL)
        else { return nil }

        let rawName = uri.getLastPathSegment() ?? "attachment"
        let name = rawName.replacingOccurrences(of: "/", with: "_")
        let file = java.io.File(
            ProcessInfo.processInfo.androidContext.getCacheDir(),
            "hermex-attachment-\(UUID().uuidString)-\(name)"
        )
        let destination = URL(fileURLWithPath: file.absolutePath)
        guard (try? data.write(to: destination)) != nil else { return nil }
        return destination
    }
}

private final class HermexSkipAttachmentUploader: HermexAttachmentUploader, @unchecked Sendable {
    private let connection: HermexSkipConnection

    init(connection: HermexSkipConnection) {
        self.connection = connection
    }

    func uploadAttachment(at url: URL, sessionID: String) async throws -> HermexUploadResponse {
        let data = try Data(contentsOf: url)
        let filename = url.lastPathComponent.isEmpty ? "attachment" : url.lastPathComponent
        let contentType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(url.pathExtension.lowercased())
            ?? "application/octet-stream"
        let repository = try await connection.currentChatRepository()
        return try await repository.upload(
            sessionID: sessionID,
            data: data,
            filename: filename,
            contentType: contentType
        )
    }
}

private final class HermexSkipVoiceRecorder: HermexVoiceRecorder, @unchecked Sendable {
    private var recorder: MediaRecorder?
    private var outputURL: URL?

    func start() async throws {
        guard await UIApplication.shared.requestPermission("android.permission.RECORD_AUDIO") else {
            throw HermexAPIError.network("Microphone permission was denied.")
        }
        await cancel()

        let file = java.io.File(
            ProcessInfo.processInfo.androidContext.getCacheDir(),
            "hermex-voice-\(UUID().uuidString).m4a"
        )
        let next = MediaRecorder()
        next.setAudioSource(MediaRecorder.AudioSource.MIC)
        next.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        next.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        next.setAudioEncodingBitRate(96_000)
        next.setAudioSamplingRate(44_100)
        next.setOutputFile(file.absolutePath)
        next.prepare()
        next.start()
        recorder = next
        outputURL = URL(fileURLWithPath: file.absolutePath)
    }

    func stop() async throws -> URL {
        let url = outputURL
        let current = recorder
        recorder = nil
        outputURL = nil
        if let current {
            current.stop()
            current.reset()
            current.release()
        }
        guard let url,
              FileManager.default.fileExists(atPath: url.path)
        else {
            throw HermexAPIError.network("Voice recording was empty.")
        }
        return url
    }

    func cancel() async {
        let url = outputURL
        let current = recorder
        recorder = nil
        outputURL = nil
        if let current {
            current.reset()
            current.release()
        }
        if let url {
            try? FileManager.default.removeItem(at: url)
        }
    }
}

private final class HermexSkipAudioTranscriber: HermexAudioTranscriber, @unchecked Sendable {
    private let connection: HermexSkipConnection

    init(connection: HermexSkipConnection) {
        self.connection = connection
    }

    func transcribeAudio(at url: URL) async throws -> HermexTranscribeResponse {
        let data = try Data(contentsOf: url)
        let repository = try await connection.currentChatRepository()
        return try await repository.transcribe(
            data: data,
            filename: url.lastPathComponent.isEmpty ? "voice-note.m4a" : url.lastPathComponent,
            contentType: "audio/mp4"
        )
    }
}

private struct HermexSkipSharedAttachmentPayload: Codable {
    var uri: String?
    var displayName: String?
    var mimeType: String?
    var cachedPath: String?
}

private struct HermexSkipSharedDraftPayload: Codable {
    var text: String?
    var attachments: [HermexSkipSharedAttachmentPayload]?
    var uris: [String]?
}

private final class HermexSkipShareIngress: HermexShareIngress, @unchecked Sendable {
    private let key = "pending_share_draft"

    func pendingSharedDraft() async throws -> HermexSharedDraft? {
        let preferences = ProcessInfo.processInfo.androidContext.getSharedPreferences(
            "hermex_share",
            android.content.Context.MODE_PRIVATE
        )
        guard let encoded = preferences.getString(key, nil),
              let data = encoded.data(using: String.Encoding.utf8)
        else { return nil }

        let payload = try JSONDecoder().decode(HermexSkipSharedDraftPayload.self, from: data)
        let attachmentPayloads = payload.attachments ?? []
        let urls: [URL]
        if !attachmentPayloads.isEmpty {
            urls = attachmentPayloads.compactMap { attachment in
                if let cachedPath = attachment.cachedPath?.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines),
                   !cachedPath.isEmpty {
                    return URL(fileURLWithPath: cachedPath)
                }
                guard let uri = attachment.uri else { return nil }
                return URL(string: uri)
            }
        } else {
            urls = (payload.uris ?? []).compactMap(URL.init(string:))
        }
        let text = payload.text?.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines)
        guard text?.isEmpty == false || !urls.isEmpty else { return nil }
        return HermexSharedDraft(text: text?.isEmpty == true ? nil : text, attachmentURLs: urls)
    }

    func clearPendingSharedDraft() async throws {
        let preferences = ProcessInfo.processInfo.androidContext.getSharedPreferences(
            "hermex_share",
            android.content.Context.MODE_PRIVATE
        )
        preferences.edit().remove(key).apply()
    }
}
#endif

@MainActor
private final class HermexSkipRuntime: @unchecked Sendable {
    private let persistence: HermexSkipPersistence
    private let cookieStore: HermexSkipCookieStore
    private let cacheStore: HermexSkipCacheStore
    private let connection: HermexSkipConnection
    private let coordinator: HermexPlatformCoordinator
    private var streamTask: Task<Void, Never>?
    private var didBootstrap = false

    init() {
        let persistence = HermexSkipPersistence()
        let cookieStore = HermexSkipCookieStore(persistence: persistence)
        let cacheStore = HermexSkipCacheStore(persistence: persistence)
        self.persistence = persistence
        self.cookieStore = cookieStore
        self.cacheStore = cacheStore
        let connection = HermexSkipConnection(persistence: persistence, cookieStore: cookieStore)
        self.connection = connection
#if SKIP
        self.coordinator = HermexPlatformCoordinator(services: HermexPlatformServiceBundle(
            cache: cacheStore,
            shareIngress: HermexSkipShareIngress(),
            attachmentPicker: HermexSkipAttachmentPicker(),
            attachmentUploader: HermexSkipAttachmentUploader(connection: connection),
            voiceRecorder: HermexSkipVoiceRecorder(),
            audioTranscriber: HermexSkipAudioTranscriber(connection: connection)
        ))
#else
        self.coordinator = HermexPlatformCoordinator(services: HermexPlatformServiceBundle(cache: cacheStore))
#endif
    }

    @MainActor
    lazy var store: HermexAppStore = {
        let restored = persistence.restoredStoreState()
        return HermexAppStore(
            appState: restored.appState,
            onboarding: restored.onboarding,
            settings: restored.settings,
            environment: Self.environment(connection: connection, cacheStore: cacheStore, onStreamStarted: { [weak self] streamID in
            self?.beginStreaming(streamID: streamID)
            })
        )
    }()

    @MainActor
    func bootstrap() async {
        guard !didBootstrap else { return }
        didBootstrap = true
        guard store.appState.route != .onboarding,
              let serverID = await connection.currentServerID()
        else { return }

        await coordinator.hydrateCachedSessions(serverID: serverID, into: store)
        await store.send(.refresh)
        if store.appState.selectedSessionID != nil {
            await coordinator.consumePendingShare(into: store)
        }
    }

    @MainActor
    func handleUnhandledEvent(_ event: HermexUIEvent) async {
        switch event {
        case .attach:
            await coordinator.pickDocumentsAndUpload(into: store)
        case .attachPhotos:
            await coordinator.pickPhotosAndUpload(into: store)
        case .startVoice:
            await coordinator.startVoiceRecording(in: store)
        case .stopVoice:
            await coordinator.stopVoiceRecordingAndTranscribe(into: store)
        default:
            break
        }
    }

    @MainActor
    func handleActionCompleted() {
        guard store.appState.route == .chat,
              store.appState.selectedSessionID != nil
        else { return }
        Task { await coordinator.consumePendingShare(into: store) }
    }

    @MainActor
    private func beginStreaming(streamID: String) {
        streamTask?.cancel()
        streamTask = Task { @MainActor in
            do {
                let client = try await connection.currentClient()
                let headers = try await connection.currentCustomHeaders()
                let cookieHeader = await connection.currentCookieHeader()
                let streamURL = client.streamURL(streamID: streamID)
                var streamHeaders = headers
                if let cookieHeader {
                    streamHeaders.append(HermexCustomHeader(name: "Cookie", value: cookieHeader))
                }
                let streamClient = HermexSSEStreamClient(url: streamURL, customHeaders: streamHeaders)
                for try await event in streamClient.events() {
                    if Task.isCancelled { break }
                    await store.send(.applyStreamEvent(event))
                    if case .done = event { break }
                    if case .error = event { break }
                }
            } catch {
                await store.send(.applyStreamEvent(.error(String(describing: error))))
            }
        }
    }

    private static func environment(
        connection: HermexSkipConnection,
        cacheStore: HermexSkipCacheStore,
        onStreamStarted: @escaping @MainActor (String) -> Void
    ) -> HermexAppEnvironment {
        HermexAppEnvironment(
            testServerConnection: { server in
                let client = await connection.client(for: server)
                return try await client.health()
            },
            loginToServer: { server, password in
                let client = await connection.client(for: server)
                return try await client.login(password: password)
            },
            loadSessions: { includeArchived, archivedLimit in
                let repository = try await connection.currentSessionsRepository()
                do {
                    let response = try await repository.list(
                        includeArchived: includeArchived,
                        archivedLimit: archivedLimit
                    )
                    if let serverID = await connection.currentServerID() {
                        try? await cacheStore.replaceCachedSessions(response.sessions ?? [], for: serverID)
                    }
                    return response
                } catch {
                    if let serverID = await connection.currentServerID(),
                       let cached = try? await cacheStore.cachedSessions(for: serverID),
                       !cached.isEmpty {
                        return HermexSessionsResponse(sessions: cached)
                    }
                    throw error
                }
            },
            loadSession: { sessionID in
                let repository = try await connection.currentSessionsRepository()
                do {
                    let response = try await repository.detail(id: sessionID)
                    if let serverID = await connection.currentServerID(),
                       let messages = response.messages {
                        try? await cacheStore.replaceCachedMessages(messages, sessionID: sessionID, serverID: serverID)
                    }
                    return response
                } catch {
                    if let serverID = await connection.currentServerID(),
                       let cached = try? await cacheStore.cachedMessages(sessionID: sessionID, serverID: serverID),
                       !cached.isEmpty {
                        return HermexSessionResponse(messages: cached)
                    }
                    throw error
                }
            },
            startChat: { sessionID, message, workspace, model, modelProvider, profile, attachments in
                let repository = try await connection.currentChatRepository()
                let response = try await repository.start(
                    sessionID: sessionID,
                    message: message,
                    workspace: workspace,
                    model: model,
                    modelProvider: modelProvider,
                    profile: profile,
                    explicitModelPick: model != nil,
                    attachments: attachments
                )
                if let streamID = response.stringValue(forKey: "stream_id") ?? response.stringValue(forKey: "streamId") {
                    await MainActor.run {
                        onStreamStarted(streamID)
                    }
                }
                return response
            },
            cancelStream: { streamID in
                let repository = try await connection.currentChatRepository()
                return try await repository.cancel(streamID: streamID)
            },
            respondApproval: { sessionID, choice, approvalID in
                let repository = try await connection.currentChatRepository()
                return try await repository.respondApproval(
                    sessionID: sessionID,
                    choice: choice,
                    approvalID: approvalID
                )
            },
            respondClarification: { sessionID, response, clarifyID in
                let repository = try await connection.currentChatRepository()
                return try await repository.respondClarification(
                    sessionID: sessionID,
                    response: response,
                    clarifyID: clarifyID
                )
            },
            undoSession: { sessionID in
                let repository = try await connection.currentSessionsRepository()
                return try await repository.undo(id: sessionID)
            },
            retrySession: { sessionID in
                let repository = try await connection.currentSessionsRepository()
                return try await repository.retry(id: sessionID)
            },
            compressSession: { sessionID, focusTopic in
                let repository = try await connection.currentSessionsRepository()
                return try await repository.compress(id: sessionID, focusTopic: focusTopic)
            },
            loadModels: {
                let client = try await connection.currentClient()
                return try await client.models()
            },
            loadProfiles: {
                let client = try await connection.currentClient()
                return try await client.profilesResponse()
            },
            loadWorkspaces: {
                let client = try await connection.currentClient()
                return try await client.workspacesResponse()
            },
            loadReasoning: { model, provider in
                let client = try await connection.currentClient()
                return try await client.reasoningResponse(model: model, provider: provider)
            },
            saveReasoningEffort: { effort, model, provider in
                let client = try await connection.currentClient()
                return try await client.saveReasoningEffort(effort, model: model, provider: provider)
            },
            loadDirectory: { sessionID, path in
                let repository = try await connection.currentWorkspaceRepository()
                return try await repository.list(sessionID: sessionID, path: path)
            },
            loadFile: { sessionID, path in
                let repository = try await connection.currentWorkspaceRepository()
                return try await repository.file(sessionID: sessionID, path: path)
            },
            loadGitStatus: { sessionID in
                let repository = try await connection.currentGitRepository()
                return try await repository.status(sessionID: sessionID)
            },
            performGitAction: { sessionID, action in
                let git = try await connection.currentGitRepository()
                switch action {
                case "fetch":
                    return try await git.fetch(sessionID: sessionID)
                case "pull":
                    return try await git.pull(sessionID: sessionID)
                case "push":
                    return try await git.push(sessionID: sessionID)
                default:
                    return HermexJSONValue.dictionary([
                        "ok": HermexJSONValue.bool(false),
                        "error": HermexJSONValue.string("Unsupported git action")
                    ])
                }
            },
            performGitCommand: { sessionID, command in
                let git = try await connection.currentGitRepository()
                switch command {
                case .fetch:
                    return try await git.fetch(sessionID: sessionID)
                case .pull:
                    return try await git.pull(sessionID: sessionID)
                case .push:
                    return try await git.push(sessionID: sessionID)
                case .diff(let path, let kind):
                    return try await git.diff(sessionID: sessionID, path: path, kind: kind)
                case .stage(let path):
                    return try await git.stage(sessionID: sessionID, paths: [path])
                case .unstage(let path):
                    return try await git.unstage(sessionID: sessionID, paths: [path])
                case .discard(let path, let deleteUntracked):
                    return try await git.discard(sessionID: sessionID, paths: [path], deleteUntracked: deleteUntracked)
                case .commit(let message):
                    return try await git.commit(sessionID: sessionID, message: message)
                }
            },
            loadTasks: {
                let repository = try await connection.currentPanelsRepository()
                return try await repository.crons()
            },
            performTaskCommand: { command in
                let repository = try await connection.currentPanelsRepository()
                switch command {
                case .run(let jobID):
                    return try await repository.runCron(jobID: jobID)
                case .pause(let jobID):
                    return try await repository.pauseCron(jobID: jobID)
                case .resume(let jobID):
                    return try await repository.resumeCron(jobID: jobID)
                case .create(let draft):
                    return try await repository.createCron(
                        prompt: draft.trimmedPrompt,
                        schedule: draft.trimmedSchedule,
                        name: draft.trimmedName,
                        deliver: draft.trimmedDeliver,
                        skills: draft.skills,
                        model: draft.trimmedModel,
                        profile: draft.trimmedProfile,
                        toastNotifications: draft.toastNotifications
                    )
                case .update(let draft):
                    return try await repository.updateCron(
                        jobID: draft.editingJobID ?? "",
                        prompt: draft.trimmedPrompt,
                        schedule: draft.trimmedSchedule,
                        name: draft.trimmedName,
                        deliver: draft.trimmedDeliver,
                        skills: draft.skills,
                        model: draft.trimmedModel,
                        profile: draft.trimmedProfile,
                        toastNotifications: draft.toastNotifications
                    )
                case .delete(let jobID):
                    return try await repository.deleteCron(jobID: jobID)
                case .loadOutput(let jobID, let limit):
                    return try await repository.cronOutput(jobID: jobID, limit: limit)
                }
            },
            loadSkills: {
                let repository = try await connection.currentPanelsRepository()
                return try await repository.skills()
            },
            loadSkillContent: { name, file in
                let repository = try await connection.currentPanelsRepository()
                return try await repository.skillContent(name: name, file: file)
            },
            toggleSkill: { name, enabled in
                let repository = try await connection.currentPanelsRepository()
                return try await repository.toggleSkill(name: name, enabled: enabled)
            },
            loadMemory: {
                let repository = try await connection.currentPanelsRepository()
                return try await repository.memory()
            },
            writeMemory: { section, content in
                let repository = try await connection.currentPanelsRepository()
                return try await repository.writeMemory(section: section, content: content)
            },
            loadInsights: { days in
                let repository = try await connection.currentPanelsRepository()
                return try await repository.insights(days: days)
            },
            logout: {
                let repository = try await connection.currentAuthRepository()
                do {
                    let response = try await repository.logout()
                    await connection.clearActiveServer()
                    return response
                } catch {
                    await connection.clearActiveServer()
                    throw error
                }
            },
            performProjectCommand: { command in
                let sessions = try await connection.currentSessionsRepository()
                let projects = try await connection.currentProjectRepository()
                switch command {
                case .moveSession(let sessionID, let projectID):
                    return try await sessions.move(id: sessionID, projectID: projectID)
                case .create(let name, let color, _):
                    return try await projects.create(name: name, color: color)
                case .rename(let projectID, let name, let color):
                    return try await projects.rename(projectID: projectID, name: name, color: color)
                case .delete(let projectID):
                    return try await projects.delete(projectID: projectID)
                }
            },
            updateServerRuntime: { server, authenticated in
                if authenticated {
                    await connection.activateServer(server)
                } else {
                    await connection.rememberServer(server)
                }
            }
        )
    }
}

private actor HermexSkipConnection {
    private let persistence: HermexSkipPersistence
    private let cookieStore: HermexSkipCookieStore
    private var activeServer: HermexServerIdentity?

    init(persistence: HermexSkipPersistence, cookieStore: HermexSkipCookieStore) {
        self.persistence = persistence
        self.cookieStore = cookieStore
        let restored = persistence.restoredStoreState()
        if case .loggedIn(let server) = restored.appState.auth {
            self.activeServer = server
        } else {
            self.activeServer = nil
        }
    }

    func activateServer(_ server: HermexServerIdentity) {
        activeServer = server
        persistence.rememberServer(server, authenticated: true)
    }

    func rememberServer(_ server: HermexServerIdentity) {
        persistence.rememberServer(server, authenticated: false)
    }

    func clearActiveServer() async {
        if let activeServer {
            persistence.rememberServer(activeServer, authenticated: false)
            try? await cookieStore.clearCookies(for: serverID(for: activeServer))
        }
        activeServer = nil
    }

    func currentServerID() -> String? {
        guard let activeServer else { return nil }
        return serverID(for: activeServer)
    }

    func currentCookieHeader() async -> String? {
        guard let activeServer else { return nil }
        return try? await cookieStore.cookieHeader(for: activeServer.baseURL, serverID: serverID(for: activeServer))
    }

    func currentCustomHeaders() throws -> [HermexCustomHeader] {
        guard let activeServer else {
            throw HermexAPIError.network("No active Hermex server. Connect to a server first.")
        }
        let headers = activeServer.customHeaders
            .map { HermexCustomHeader(name: $0.key, value: $0.value) }
        return hermexSanitizedForClient(headers)
    }

    func client(for server: HermexServerIdentity) -> HermexAPIClient {
        let rawHeaders = server.customHeaders
            .map { HermexCustomHeader(name: $0.key, value: $0.value) }
        let headers = hermexSanitizedForClient(rawHeaders)

        return HermexAPIClient(
            baseURL: server.baseURL,
            transport: HermexSkipCookieTransport(
                serverID: serverID(for: server),
                persistence: persistence,
                cookieStore: cookieStore
            ),
            customHeaders: { headers }
        )
    }

    func currentClient() throws -> HermexAPIClient {
        guard let activeServer else {
            throw HermexAPIError.network("No active Hermex server. Connect to a server first.")
        }
        return client(for: activeServer)
    }

    func currentAuthRepository() throws -> HermexAuthRepository {
        HermexAuthRepository(client: try currentClient())
    }

    func currentSessionsRepository() throws -> HermexSessionRepository {
        HermexSessionRepository(client: try currentClient())
    }

    func currentProjectRepository() throws -> HermexProjectRepository {
        HermexProjectRepository(client: try currentClient())
    }

    func currentChatRepository() throws -> HermexChatRepository {
        HermexChatRepository(client: try currentClient())
    }

    func currentWorkspaceRepository() throws -> HermexWorkspaceRepository {
        HermexWorkspaceRepository(client: try currentClient())
    }

    func currentGitRepository() throws -> HermexGitRepository {
        HermexGitRepository(client: try currentClient())
    }

    func currentPanelsRepository() throws -> HermexPanelsRepository {
        HermexPanelsRepository(client: try currentClient())
    }

    private func serverID(for server: HermexServerIdentity) -> String {
        HermexServerURLNormalizer.normalizedID(for: server.baseURL)
    }
}

private struct HermexSkipPersistedServers: Codable {
    var servers: [HermexServerIdentity] = []
    var activeServerID: String?
    var authenticated = false
}

private struct HermexSkipRestoredStoreState {
    let appState: HermexAppState
    let onboarding: HermexOnboardingState
    let settings: HermexSettingsState
}

private final class HermexSkipPersistence: @unchecked Sendable {
    private let storage: HermexSecureDataStore

    init(storage: HermexSecureDataStore = HermexSecureDataStore()) {
        self.storage = storage
    }

    func restoredStoreState() -> HermexSkipRestoredStoreState {
        let snapshot = loadServers()
        let active = snapshot.servers.first { serverID(for: $0) == snapshot.activeServerID }
        let onboarding = HermexOnboardingState(
            serverURLString: active?.baseURL.absoluteString ?? "",
            displayName: active?.displayName ?? "",
            customHeaderText: headerText(for: active)
        )
        let settings = HermexSettingsState(activeServer: active, servers: snapshot.servers)

        guard let active else {
            return HermexSkipRestoredStoreState(
                appState: HermexAppState(),
                onboarding: onboarding,
                settings: settings
            )
        }

        let auth: HermexAuthState = snapshot.authenticated
            ? .loggedIn(server: active)
            : .loggedOut(server: active)
        let route: HermexRoute = snapshot.authenticated ? .sessions : .onboarding
        return HermexSkipRestoredStoreState(
            appState: HermexAppState(auth: auth, route: route),
            onboarding: onboarding,
            settings: settings
        )
    }

    func rememberServer(_ server: HermexServerIdentity, authenticated: Bool) {
        var snapshot = loadServers()
        let id = serverID(for: server)
        if let index = snapshot.servers.firstIndex(where: { serverID(for: $0) == id }) {
            snapshot.servers[index] = server
        } else {
            snapshot.servers.append(server)
        }
        snapshot.activeServerID = id
        snapshot.authenticated = authenticated
        saveServers(snapshot)
    }

    func markUnauthenticated(serverID: String) {
        var snapshot = loadServers()
        guard snapshot.activeServerID == serverID else { return }
        snapshot.authenticated = false
        saveServers(snapshot)
    }

    func data(for key: String) -> Data? {
        storage.data(for: key)
    }

    func setData(_ data: Data?, for key: String) {
        storage.setData(data, for: key)
    }

    func scopedKey(_ prefix: String, _ components: String...) -> String {
        let safe = components.joined(separator: "::")
            .replacingOccurrences(of: "://", with: "_")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: ":", with: "_")
        return "hermex.skip.\(prefix).\(safe)"
    }

    private func loadServers() -> HermexSkipPersistedServers {
        guard let data = storage.data(for: Self.serverStateKey),
              let snapshot = try? JSONDecoder().decode(HermexSkipPersistedServers.self, from: data)
        else {
            return HermexSkipPersistedServers()
        }
        return snapshot
    }

    private func saveServers(_ snapshot: HermexSkipPersistedServers) {
        if let data = try? JSONEncoder().encode(snapshot) {
            storage.setData(data, for: Self.serverStateKey)
        }
    }

    private func headerText(for server: HermexServerIdentity?) -> String {
        guard let server else { return "" }
        let headerKeys = server.customHeaders.keys.sorted {
            $0.localizedCaseInsensitiveCompare($1) == ComparisonResult.orderedAscending
        }
        return headerKeys
            .map { key in "\(key): \(server.customHeaders[key] ?? "")" }
            .joined(separator: "\n")
    }

    private func serverID(for server: HermexServerIdentity) -> String {
        HermexServerURLNormalizer.normalizedID(for: server.baseURL)
    }

    private static let serverStateKey = "hermex.skip.servers"
}

private actor HermexSkipCookieStore: HermexCookieStore {
    private let persistence: HermexSkipPersistence
    private var loadedServerIDs: Set<String> = []
    private var recordsByServer: [String: [HermexCookieRecord]] = [:]

    init(persistence: HermexSkipPersistence) {
        self.persistence = persistence
    }

    func cookies(for serverID: String) async throws -> [HermexCookieRecord] {
        loadIfNeeded(serverID)
        let now = Date()
        let fresh = recordsByServer[serverID, default: []].filter { record in
            record.expiresAt == nil || record.expiresAt! > now
        }
        recordsByServer[serverID] = fresh
        persist(serverID)
        return fresh
    }

    func replaceCookies(_ cookies: [HermexCookieRecord], for serverID: String) async throws {
        loadedServerIDs.insert(serverID)
        recordsByServer[serverID] = cookies
        persist(serverID)
    }

    func clearCookies(for serverID: String) async throws {
        loadedServerIDs.insert(serverID)
        recordsByServer[serverID] = []
        persist(serverID)
    }

    func merge(_ incoming: [HermexCookieRecord], for serverID: String) async {
        guard !incoming.isEmpty else { return }
        let existing = (try? await cookies(for: serverID)) ?? []
        var merged = existing
        for cookie in incoming {
            merged.removeAll { current in
                current.name == cookie.name &&
                    current.domain == cookie.domain &&
                    current.path == cookie.path
            }
            if cookie.expiresAt == nil || cookie.expiresAt! > Date() {
                merged.append(cookie)
            }
        }
        try? await replaceCookies(merged, for: serverID)
    }

    func cookieHeader(for url: URL, serverID: String) async throws -> String? {
        let host = url.host?.lowercased() ?? ""
        let path = url.path.isEmpty ? "/" : url.path
        let secureConnection = url.scheme?.lowercased() == "https"
        let records = try await cookies(for: serverID)
        let matching = records.filter { cookie in
            guard cookie.expiresAt == nil || cookie.expiresAt! > Date() else { return false }
            if cookie.isSecure && !secureConnection { return false }
            if let domain = cookie.domain?.lowercased(),
               host != domain,
               !host.hasSuffix(".\(domain.trimmingCharacters(in: CharacterSet(charactersIn: ".")))") {
                return false
            }
            return path.hasPrefix(cookie.path.isEmpty ? "/" : cookie.path)
        }
        guard !matching.isEmpty else { return nil }
        return matching.map { "\($0.name)=\($0.value)" }.joined(separator: "; ")
    }

    private func loadIfNeeded(_ serverID: String) {
        guard loadedServerIDs.insert(serverID).inserted else { return }
        let key = persistence.scopedKey("cookies", serverID)
        if let data = persistence.data(for: key),
           let records = try? JSONDecoder().decode([HermexCookieRecord].self, from: data) {
            recordsByServer[serverID] = records
        } else {
            recordsByServer[serverID] = []
        }
    }

    private func persist(_ serverID: String) {
        guard let records = recordsByServer[serverID],
              let data = try? JSONEncoder().encode(records)
        else { return }
        persistence.setData(data, for: persistence.scopedKey("cookies", serverID))
    }
}

private actor HermexSkipCacheStore: HermexCacheStore {
    private let persistence: HermexSkipPersistence

    init(persistence: HermexSkipPersistence) {
        self.persistence = persistence
    }

    func cachedSessions(for serverID: String) async throws -> [HermexSessionDTO] {
        let key = persistence.scopedKey("sessions", serverID)
        guard let data = persistence.data(for: key) else { return [] }
        return (try? JSONDecoder().decode([HermexSessionDTO].self, from: data)) ?? []
    }

    func replaceCachedSessions(_ sessions: [HermexSessionDTO], for serverID: String) async throws {
        guard let data = try? JSONEncoder().encode(sessions) else { return }
        persistence.setData(data, for: persistence.scopedKey("sessions", serverID))
    }

    func cachedMessages(sessionID: String, serverID: String) async throws -> [HermexChatMessageDTO] {
        let key = persistence.scopedKey("messages", serverID, sessionID)
        guard let data = persistence.data(for: key) else { return [] }
        return (try? JSONDecoder().decode([HermexChatMessageDTO].self, from: data)) ?? []
    }

    func replaceCachedMessages(_ messages: [HermexChatMessageDTO], sessionID: String, serverID: String) async throws {
        guard let data = try? JSONEncoder().encode(messages) else { return }
        persistence.setData(data, for: persistence.scopedKey("messages", serverID, sessionID))
    }
}

private final class HermexSkipCookieTransport: HermexHTTPTransport, @unchecked Sendable {
    private let serverID: String
    private let persistence: HermexSkipPersistence
    private let cookieStore: HermexSkipCookieStore
    private let baseTransport: HermexURLSessionTransport

    init(serverID: String, persistence: HermexSkipPersistence, cookieStore: HermexSkipCookieStore) {
        self.serverID = serverID
        self.persistence = persistence
        self.cookieStore = cookieStore
        self.baseTransport = HermexURLSessionTransport()
    }

    func data(for request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        var mutableRequest = request
        if let url = mutableRequest.url,
           let cookieHeader = try? await cookieStore.cookieHeader(for: url, serverID: serverID),
           !cookieHeader.isEmpty {
            mutableRequest.setValue(cookieHeader, forHTTPHeaderField: "Cookie")
        }

        let result = try await baseTransport.data(for: mutableRequest)
        if result.1.statusCode == 401 {
            persistence.markUnauthenticated(serverID: serverID)
        }
        if let url = mutableRequest.url {
            let received = responseCookies(result.1, url: url)
            await cookieStore.merge(received, for: serverID)
        }
        return result
    }

    private func responseCookies(_ response: HTTPURLResponse, url: URL) -> [HermexCookieRecord] {
        guard let header = response.value(forHTTPHeaderField: "Set-Cookie") else { return [] }
        guard let cookie = parseCookie(header, url: url) else { return [] }
        return [cookie]
    }
}

private func parseCookie(_ raw: String, url: URL) -> HermexCookieRecord? {
    let parts = raw.split(separator: ";", omittingEmptySubsequences: true).map { String($0) }
    guard let first = parts.first else { return nil }
    let pair = first.split(separator: "=", maxSplits: 1, omittingEmptySubsequences: false).map { String($0) }
    guard pair.count == 2 else { return nil }

    var domain = url.host
    var path = "/"
    var expiresAt: Date?
    var isSecure = false
    var isHTTPOnly = false

    for attribute in parts.dropFirst() {
        let pieces = attribute.split(separator: "=", maxSplits: 1, omittingEmptySubsequences: false).map { String($0) }
        let name = pieces[0].trimmingCharacters(in: CharacterSet.whitespacesAndNewlines).lowercased()
        let value = pieces.count > 1
            ? pieces[1].trimmingCharacters(in: CharacterSet.whitespacesAndNewlines)
            : ""
        switch name {
        case "domain":
            domain = value
        case "path":
            path = value.isEmpty ? "/" : value
        case "max-age":
            if let seconds = Double(value) {
                expiresAt = Date(timeIntervalSinceNow: seconds)
            }
        case "secure":
            isSecure = true
        case "httponly":
            isHTTPOnly = true
        default:
            break
        }
    }

    return HermexCookieRecord(
        name: String(pair[0]).trimmingCharacters(in: CharacterSet.whitespacesAndNewlines),
        value: String(pair[1]),
        domain: domain,
        path: path,
        expiresAt: expiresAt,
        isSecure: isSecure,
        isHTTPOnly: isHTTPOnly
    )
}


private extension HermexJSONValue {
    func stringValue(forKey key: String) -> String? {
        objectValue?[key]?.stringValue
    }
}
