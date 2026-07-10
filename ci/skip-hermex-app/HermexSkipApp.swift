import Foundation
import SwiftUI
import HermexCore
import HermexPlatform
import HermexUI

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
                onUnhandledEvent: runtime.handleUnhandledEvent
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

private final class HermexSkipRuntime {
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
        self.connection = HermexSkipConnection(persistence: persistence, cookieStore: cookieStore)
        self.coordinator = HermexPlatformCoordinator(services: HermexPlatformServiceBundle(cache: cacheStore))
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
    }

    @MainActor
    func handleUnhandledEvent(_ event: HermexUIEvent) {
        switch event {
        case .attach, .startVoice, .stopVoice:
            Task { await store.send(.refresh) }
        default:
            break
        }
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
        activeServer.map { serverID(for: $0) }
    }

    func currentCookieHeader() async -> String? {
        guard let activeServer else { return nil }
        return try? await cookieStore.cookieHeader(for: activeServer.baseURL, serverID: serverID(for: activeServer))
    }

    func currentCustomHeaders() throws -> [HermexCustomHeader] {
        guard let activeServer else {
            throw HermexAPIError.network("No active Hermex server. Connect to a server first.")
        }
        return activeServer.customHeaders
            .map { HermexCustomHeader(name: $0.key, value: $0.value) }
            .sanitizedForClient()
    }

    func client(for server: HermexServerIdentity) -> HermexAPIClient {
        let headers = server.customHeaders
            .map { HermexCustomHeader(name: $0.key, value: $0.value) }
            .sanitizedForClient()

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
    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
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
        defaults.data(forKey: key)
    }

    func setData(_ data: Data?, for key: String) {
        defaults.set(data, forKey: key)
    }

    func scopedKey(_ prefix: String, _ components: String...) -> String {
        let safe = components.joined(separator: "::")
            .replacingOccurrences(of: "://", with: "_")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: ":", with: "_")
        return "hermex.skip.\(prefix).\(safe)"
    }

    private func loadServers() -> HermexSkipPersistedServers {
        guard let data = defaults.data(forKey: Self.serverStateKey),
              let snapshot = try? JSONDecoder().decode(HermexSkipPersistedServers.self, from: data)
        else {
            return HermexSkipPersistedServers()
        }
        return snapshot
    }

    private func saveServers(_ snapshot: HermexSkipPersistedServers) {
        if let data = try? JSONEncoder().encode(snapshot) {
            defaults.set(data, forKey: Self.serverStateKey)
        }
    }

    private func headerText(for server: HermexServerIdentity?) -> String {
        guard let server else { return "" }
        return server.customHeaders
            .sorted { $0.key.localizedCaseInsensitiveCompare($1.key) == ComparisonResult.orderedAscending }
            .map { "\($0.key): \($0.value)" }
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
        decode([HermexSessionDTO].self, key: persistence.scopedKey("sessions", serverID)) ?? []
    }

    func replaceCachedSessions(_ sessions: [HermexSessionDTO], for serverID: String) async throws {
        encode(sessions, key: persistence.scopedKey("sessions", serverID))
    }

    func cachedMessages(sessionID: String, serverID: String) async throws -> [HermexChatMessageDTO] {
        decode(
            [HermexChatMessageDTO].self,
            key: persistence.scopedKey("messages", serverID, sessionID)
        ) ?? []
    }

    func replaceCachedMessages(_ messages: [HermexChatMessageDTO], sessionID: String, serverID: String) async throws {
        encode(messages, key: persistence.scopedKey("messages", serverID, sessionID))
    }

    private func encode<T: Encodable>(_ value: T, key: String) {
        if let data = try? JSONEncoder().encode(value) {
            persistence.setData(data, for: key)
        }
    }

    private func decode<T: Decodable>(_ type: T.Type, key: String) -> T? {
        guard let data = persistence.data(for: key) else { return nil }
        return try? JSONDecoder().decode(type, from: data)
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
        return [parseCookie(header, url: url)].compactMap { $0 }
    }
}

private func parseCookie(_ raw: String, url: URL) -> HermexCookieRecord? {
    let parts = raw.split(separator: ";", omittingEmptySubsequences: true).map(String.init)
    guard let first = parts.first else { return nil }
    let pair = first.split(separator: "=", maxSplits: 1, omittingEmptySubsequences: false)
    guard pair.count == 2 else { return nil }

    var domain = url.host
    var path = "/"
    var expiresAt: Date?
    var isSecure = false
    var isHTTPOnly = false

    for attribute in parts.dropFirst() {
        let pieces = attribute.split(separator: "=", maxSplits: 1, omittingEmptySubsequences: false)
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
