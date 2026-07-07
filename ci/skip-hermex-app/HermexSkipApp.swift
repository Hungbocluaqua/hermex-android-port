import Foundation
import SwiftUI
import HermexCore
import HermexUI

private let hermexVisualFixtureName: String? = nil
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
    private let connection = HermexSkipConnection()

    @MainActor
    lazy var store: HermexAppStore = HermexAppStore(
        environment: Self.environment(connection: connection)
    )

    @MainActor
    func handleUnhandledEvent(_ event: HermexUIEvent) {
        switch event {
        case .attach, .startVoice, .stopVoice:
            Task { await store.send(.refresh) }
        default:
            break
        }
    }

    private static func environment(connection: HermexSkipConnection) -> HermexAppEnvironment {
        HermexAppEnvironment(
            testServerConnection: { server in
                let client = await connection.client(for: server)
                return try await client.health()
            },
            loginToServer: { server, password in
                let client = await connection.client(for: server)
                let response = try await client.login(password: password)
                await connection.setActiveServer(server)
                return response
            },
            loadSessions: { includeArchived, archivedLimit in
                let repository = try await connection.currentSessionsRepository()
                return try await repository.list(
                    includeArchived: includeArchived,
                    archivedLimit: archivedLimit
                )
            },
            loadSession: { sessionID in
                let repository = try await connection.currentSessionsRepository()
                return try await repository.detail(id: sessionID)
            },
            startChat: { sessionID, message, workspace, model, modelProvider, profile, attachments in
                let repository = try await connection.currentChatRepository()
                return try await repository.start(
                    sessionID: sessionID,
                    message: message,
                    workspace: workspace,
                    model: model,
                    modelProvider: modelProvider,
                    profile: profile,
                    explicitModelPick: model != nil,
                    attachments: attachments
                )
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
            loadSkills: {
                let repository = try await connection.currentPanelsRepository()
                return try await repository.skills()
            },
            loadMemory: {
                let repository = try await connection.currentPanelsRepository()
                return try await repository.memory()
            },
            loadInsights: { days in
                let repository = try await connection.currentPanelsRepository()
                return try await repository.insights(days: days)
            },
            logout: {
                let repository = try await connection.currentAuthRepository()
                let response = try await repository.logout()
                await connection.clearActiveServer()
                return response
            }
        )
    }
}

private actor HermexSkipConnection {
    private var activeServer: HermexServerIdentity?

    func setActiveServer(_ server: HermexServerIdentity) {
        activeServer = server
    }

    func clearActiveServer() {
        activeServer = nil
    }

    func client(for server: HermexServerIdentity) -> HermexAPIClient {
        let headers = server.customHeaders
            .map { HermexCustomHeader(name: $0.key, value: $0.value) }
            .sanitizedForClient()

        return HermexAPIClient(
            baseURL: server.baseURL,
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
}
