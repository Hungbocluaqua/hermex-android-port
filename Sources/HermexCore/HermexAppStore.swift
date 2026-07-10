import Foundation
#if !SKIP
import Observation
#endif

public enum HermexAppAction: Equatable, Sendable {
    case openRoute(HermexRoute)
    case refresh
    case updateOnboardingServerURL(String)
    case updateOnboardingDisplayName(String)
    case updateOnboardingPassword(String)
    case updateOnboardingCustomHeaders(String)
    case testOnboardingConnection
    case testOnboardingConnectionDraft(serverURLString: String, displayName: String, password: String, customHeaderText: String)
    case connectOnboarding
    case connectOnboardingDraft(serverURLString: String, displayName: String, password: String, customHeaderText: String)
    case selectServer(HermexServerIdentity)
    case selectProject(String?)
    case projectCommand(HermexProjectCommand)
    case openSession(String)
    case newChat
    case searchSessions(String)
    case toggleArchived
    case updateDraft(String)
    case appendDraftText(String)
    case applySharedDraft(HermexSharedDraft)
    case hydrateCachedSessions([HermexSessionDTO])
    case hydrateCachedMessages(sessionID: String, [HermexChatMessageDTO])
    case setUploadingAttachment(Bool)
    case addAttachment(HermexAttachmentDTO)
    case replaceAttachment(id: String, with: HermexAttachmentDTO)
    case removeAttachment(String)
    case setVoiceRecording(Bool)
    case refreshComposerConfiguration
    case selectModel(HermexModelOption)
    case selectWorkspace(HermexWorkspaceRootDTO)
    case selectProfile(HermexProfileOption)
    case selectReasoningEffort(String)
    case sendDraft
    case cancelStream
    case undo
    case retry
    case compress
    case clearConversation
    case forkMessage(HermexMessageActionContext)
    case editMessage(HermexMessageActionContext, String)
    case regenerateMessage(HermexMessageActionContext)
    case approval(String)
    case clarify(String)
    case applyStreamEvent(HermexSSEEvent)
    case openWorkspaceEntry(HermexWorkspaceEntryDTO)
    case openFile(String)
    case gitAction(String)
    case gitCommand(HermexGitCommand)
    case updateGitCommitMessage(String)
    case selectPanel(HermexPanel)
    case taskCommand(HermexTaskCommand)
    case beginTaskCreation
    case beginTaskEdit(jobID: String)
    case updateTaskDraft(HermexTaskDraft)
    case cancelTaskEditor
    case requestTaskDeletion(jobID: String)
    case cancelTaskDeletion
    case confirmTaskDeletion
    case dismissTaskDetails
    case toggleSkill(name: String, enabled: Bool)
    case openSkillDetail(name: String)
    case loadSkillFile(fileName: String)
    case dismissSkillDetail
    case dismissSkillFile
    case writeMemory(section: String, content: String)
    case selectInsightsRange(days: Int)
    case signOut
}

public enum HermexTaskCommand: Equatable, Sendable {
    case run(jobID: String)
    case pause(jobID: String)
    case resume(jobID: String)
    case create(draft: HermexTaskDraft)
    case update(draft: HermexTaskDraft)
    case delete(jobID: String)
    case loadOutput(jobID: String, limit: Int)
}

public enum HermexProjectCommand: Equatable, Sendable {
    case moveSession(sessionID: String, projectID: String?)
    case create(name: String, color: String?, moveSessionID: String?)
    case rename(projectID: String, name: String, color: String?)
    case delete(projectID: String)
}

public struct HermexAppEnvironment: Sendable {
    public var testServerConnection: @Sendable (_ server: HermexServerIdentity) async throws -> HermexJSONValue
    public var loginToServer: @Sendable (_ server: HermexServerIdentity, _ password: String) async throws -> HermexJSONValue
    public var loadSessions: @Sendable (_ includeArchived: Bool, _ archivedLimit: Int?) async throws -> HermexSessionsResponse
    public var loadSession: @Sendable (_ sessionID: String) async throws -> HermexSessionResponse
    public var performProjectCommand: @Sendable (_ command: HermexProjectCommand) async throws -> HermexJSONValue
    public var startChat: @Sendable (
        _ sessionID: String?,
        _ message: String,
        _ workspace: String?,
        _ model: String?,
        _ modelProvider: String?,
        _ profile: String?,
        _ attachments: [HermexJSONValue]?
    ) async throws -> HermexJSONValue
    public var cancelStream: @Sendable (_ streamID: String) async throws -> HermexJSONValue
    public var respondApproval: @Sendable (_ sessionID: String, _ choice: String, _ approvalID: String?) async throws -> HermexJSONValue
    public var respondClarification: @Sendable (_ sessionID: String, _ response: String, _ clarifyID: String?) async throws -> HermexJSONValue
    public var undoSession: @Sendable (_ sessionID: String) async throws -> HermexJSONValue
    public var retrySession: @Sendable (_ sessionID: String) async throws -> HermexJSONValue
    public var compressSession: @Sendable (_ sessionID: String, _ focusTopic: String?) async throws -> HermexJSONValue
    public var clearSession: @Sendable (_ sessionID: String) async throws -> HermexJSONValue
    public var loadModels: @Sendable () async throws -> HermexModelsResponse
    public var loadProfiles: @Sendable () async throws -> HermexProfilesResponse
    public var loadWorkspaces: @Sendable () async throws -> HermexWorkspacesResponse
    public var loadReasoning: @Sendable (_ model: String?, _ provider: String?) async throws -> HermexReasoningResponse
    public var saveReasoningEffort: @Sendable (_ effort: String, _ model: String?, _ provider: String?) async throws -> HermexJSONValue
    public var loadDirectory: @Sendable (_ sessionID: String, _ path: String?) async throws -> HermexJSONValue
    public var loadFile: @Sendable (_ sessionID: String, _ path: String) async throws -> HermexJSONValue
    public var loadGitStatus: @Sendable (_ sessionID: String) async throws -> HermexJSONValue
    public var performGitAction: @Sendable (_ sessionID: String, _ action: String) async throws -> HermexJSONValue
    public var performGitCommand: @Sendable (_ sessionID: String, _ command: HermexGitCommand) async throws -> HermexJSONValue
    public var loadTasks: @Sendable () async throws -> HermexJSONValue
    public var performTaskCommand: @Sendable (_ command: HermexTaskCommand) async throws -> HermexJSONValue
    public var loadSkills: @Sendable () async throws -> HermexJSONValue
    public var loadSkillContent: @Sendable (_ name: String, _ file: String?) async throws -> HermexJSONValue
    public var toggleSkill: @Sendable (_ name: String, _ enabled: Bool) async throws -> HermexJSONValue
    public var loadMemory: @Sendable () async throws -> HermexJSONValue
    public var writeMemory: @Sendable (_ section: String, _ content: String) async throws -> HermexJSONValue
    public var loadInsights: @Sendable (_ days: Int) async throws -> HermexJSONValue
    public var logout: @Sendable () async throws -> HermexJSONValue
    public var updateServerRuntime: @Sendable (_ server: HermexServerIdentity, _ authenticated: Bool) async -> Void
    public var branchSession: @Sendable (_ sessionID: String, _ keepCount: Int?) async throws -> HermexJSONValue
    public var truncateSession: @Sendable (_ sessionID: String, _ keepCount: Int) async throws -> HermexSessionResponse

    public init(
        testServerConnection: @escaping @Sendable (_ server: HermexServerIdentity) async throws -> HermexJSONValue,
        loginToServer: @escaping @Sendable (_ server: HermexServerIdentity, _ password: String) async throws -> HermexJSONValue,
        loadSessions: @escaping @Sendable (_ includeArchived: Bool, _ archivedLimit: Int?) async throws -> HermexSessionsResponse,
        loadSession: @escaping @Sendable (_ sessionID: String) async throws -> HermexSessionResponse,
        startChat: @escaping @Sendable (
            _ sessionID: String?,
            _ message: String,
            _ workspace: String?,
            _ model: String?,
            _ modelProvider: String?,
            _ profile: String?,
            _ attachments: [HermexJSONValue]?
        ) async throws -> HermexJSONValue,
        cancelStream: @escaping @Sendable (_ streamID: String) async throws -> HermexJSONValue,
        respondApproval: @escaping @Sendable (_ sessionID: String, _ choice: String, _ approvalID: String?) async throws -> HermexJSONValue,
        respondClarification: @escaping @Sendable (_ sessionID: String, _ response: String, _ clarifyID: String?) async throws -> HermexJSONValue,
        undoSession: @escaping @Sendable (_ sessionID: String) async throws -> HermexJSONValue,
        retrySession: @escaping @Sendable (_ sessionID: String) async throws -> HermexJSONValue,
        compressSession: @escaping @Sendable (_ sessionID: String, _ focusTopic: String?) async throws -> HermexJSONValue,
        clearSession: @escaping @Sendable (_ sessionID: String) async throws -> HermexJSONValue = { _ in
            .dictionary([
                "ok": .bool(false),
                "error": .string("Clearing conversations is unavailable.")
            ])
        },
        loadModels: @escaping @Sendable () async throws -> HermexModelsResponse,
        loadProfiles: @escaping @Sendable () async throws -> HermexProfilesResponse,
        loadWorkspaces: @escaping @Sendable () async throws -> HermexWorkspacesResponse,
        loadReasoning: @escaping @Sendable (_ model: String?, _ provider: String?) async throws -> HermexReasoningResponse,
        saveReasoningEffort: @escaping @Sendable (_ effort: String, _ model: String?, _ provider: String?) async throws -> HermexJSONValue,
        loadDirectory: @escaping @Sendable (_ sessionID: String, _ path: String?) async throws -> HermexJSONValue,
        loadFile: @escaping @Sendable (_ sessionID: String, _ path: String) async throws -> HermexJSONValue,
        loadGitStatus: @escaping @Sendable (_ sessionID: String) async throws -> HermexJSONValue,
        performGitAction: @escaping @Sendable (_ sessionID: String, _ action: String) async throws -> HermexJSONValue,
        performGitCommand: @escaping @Sendable (_ sessionID: String, _ command: HermexGitCommand) async throws -> HermexJSONValue,
        loadTasks: @escaping @Sendable () async throws -> HermexJSONValue,
        performTaskCommand: @escaping @Sendable (_ command: HermexTaskCommand) async throws -> HermexJSONValue,
        loadSkills: @escaping @Sendable () async throws -> HermexJSONValue,
        loadSkillContent: @escaping @Sendable (_ name: String, _ file: String?) async throws -> HermexJSONValue,
        toggleSkill: @escaping @Sendable (_ name: String, _ enabled: Bool) async throws -> HermexJSONValue,
        loadMemory: @escaping @Sendable () async throws -> HermexJSONValue,
        writeMemory: @escaping @Sendable (_ section: String, _ content: String) async throws -> HermexJSONValue,
        loadInsights: @escaping @Sendable (_ days: Int) async throws -> HermexJSONValue,
        logout: @escaping @Sendable () async throws -> HermexJSONValue,
        performProjectCommand: @escaping @Sendable (_ command: HermexProjectCommand) async throws -> HermexJSONValue = { _ in
            .dictionary([
                "ok": .bool(false),
                "error": .string("Project operations are unavailable.")
            ])
        },
        updateServerRuntime: @escaping @Sendable (_ server: HermexServerIdentity, _ authenticated: Bool) async -> Void = { _, _ in },
        branchSession: @escaping @Sendable (_ sessionID: String, _ keepCount: Int?) async throws -> HermexJSONValue = { _, _ in
            .dictionary([
                "ok": .bool(false),
                "error": .string("Forking messages is unavailable.")
            ])
        },
        truncateSession: @escaping @Sendable (_ sessionID: String, _ keepCount: Int) async throws -> HermexSessionResponse = { _, _ in
            HermexSessionResponse(error: "Editing messages is unavailable.")
        }
    ) {
        self.testServerConnection = testServerConnection
        self.loginToServer = loginToServer
        self.loadSessions = loadSessions
        self.loadSession = loadSession
        self.performProjectCommand = performProjectCommand
        self.startChat = startChat
        self.cancelStream = cancelStream
        self.respondApproval = respondApproval
        self.respondClarification = respondClarification
        self.undoSession = undoSession
        self.retrySession = retrySession
        self.compressSession = compressSession
        self.clearSession = clearSession
        self.loadModels = loadModels
        self.loadProfiles = loadProfiles
        self.loadWorkspaces = loadWorkspaces
        self.loadReasoning = loadReasoning
        self.saveReasoningEffort = saveReasoningEffort
        self.loadDirectory = loadDirectory
        self.loadFile = loadFile
        self.loadGitStatus = loadGitStatus
        self.performGitAction = performGitAction
        self.performGitCommand = performGitCommand
        self.loadTasks = loadTasks
        self.performTaskCommand = performTaskCommand
        self.loadSkills = loadSkills
        self.loadSkillContent = loadSkillContent
        self.toggleSkill = toggleSkill
        self.loadMemory = loadMemory
        self.writeMemory = writeMemory
        self.loadInsights = loadInsights
        self.logout = logout
        self.updateServerRuntime = updateServerRuntime
        self.branchSession = branchSession
        self.truncateSession = truncateSession
    }

    public static func live(client: HermexAPIClient) -> HermexAppEnvironment {
        let sessions = HermexSessionRepository(client: client)
        let projects = HermexProjectRepository(client: client)
        let chat = HermexChatRepository(client: client)
        let auth = HermexAuthRepository(client: client)
        let workspace = HermexWorkspaceRepository(client: client)
        let git = HermexGitRepository(client: client)
        let panels = HermexPanelsRepository(client: client)
        return HermexAppEnvironment(
            testServerConnection: { _ in
                try await client.health()
            },
            loginToServer: { _, password in
                try await auth.login(password: password)
            },
            loadSessions: { includeArchived, archivedLimit in
                try await sessions.list(includeArchived: includeArchived, archivedLimit: archivedLimit)
            },
            loadSession: { sessionID in
                try await sessions.detail(id: sessionID)
            },
            startChat: { sessionID, message, workspace, model, modelProvider, profile, attachments in
                try await chat.start(
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
                try await chat.cancel(streamID: streamID)
            },
            respondApproval: { sessionID, choice, approvalID in
                try await chat.respondApproval(sessionID: sessionID, choice: choice, approvalID: approvalID)
            },
            respondClarification: { sessionID, response, clarifyID in
                try await chat.respondClarification(sessionID: sessionID, response: response, clarifyID: clarifyID)
            },
            undoSession: { sessionID in
                try await sessions.undo(id: sessionID)
            },
            retrySession: { sessionID in
                try await sessions.retry(id: sessionID)
            },
            compressSession: { sessionID, focusTopic in
                try await sessions.compress(id: sessionID, focusTopic: focusTopic)
            },
            clearSession: { sessionID in
                try await sessions.clear(id: sessionID)
            },
            loadModels: {
                try await client.models()
            },
            loadProfiles: {
                try await client.profilesResponse()
            },
            loadWorkspaces: {
                try await client.workspacesResponse()
            },
            loadReasoning: { model, provider in
                try await client.reasoningResponse(model: model, provider: provider)
            },
            saveReasoningEffort: { effort, model, provider in
                try await client.saveReasoningEffort(effort, model: model, provider: provider)
            },
            loadDirectory: { sessionID, path in
                try await workspace.list(sessionID: sessionID, path: path)
            },
            loadFile: { sessionID, path in
                try await workspace.file(sessionID: sessionID, path: path)
            },
            loadGitStatus: { sessionID in
                try await git.status(sessionID: sessionID)
            },
            performGitAction: { sessionID, action in
                switch action {
                case "fetch": return try await git.fetch(sessionID: sessionID)
                case "pull": return try await git.pull(sessionID: sessionID)
                case "push": return try await git.push(sessionID: sessionID)
                default: return .dictionary(["ok": .bool(false), "error": .string("Unsupported git action")])
                }
            },
            performGitCommand: { sessionID, command in
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
                try await panels.crons()
            },
            performTaskCommand: { command in
                switch command {
                case .run(let jobID):
                    return try await panels.runCron(jobID: jobID)
                case .pause(let jobID):
                    return try await panels.pauseCron(jobID: jobID)
                case .resume(let jobID):
                    return try await panels.resumeCron(jobID: jobID)
                case .create(let draft):
                    return try await panels.createCron(
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
                    return try await panels.updateCron(
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
                    return try await panels.deleteCron(jobID: jobID)
                case .loadOutput(let jobID, let limit):
                    return try await panels.cronOutput(jobID: jobID, limit: limit)
                }
            },
            loadSkills: {
                try await panels.skills()
            },
            loadSkillContent: { name, file in
                try await panels.skillContent(name: name, file: file)
            },
            toggleSkill: { name, enabled in
                try await panels.toggleSkill(name: name, enabled: enabled)
            },
            loadMemory: {
                try await panels.memory()
            },
            writeMemory: { section, content in
                try await panels.writeMemory(section: section, content: content)
            },
            loadInsights: { days in
                try await panels.insights(days: days)
            },
            logout: {
                try await auth.logout()
            },
            performProjectCommand: { command in
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
            branchSession: { sessionID, keepCount in
                try await sessions.branch(id: sessionID, keepCount: keepCount)
            },
            truncateSession: { sessionID, keepCount in
                try await sessions.truncate(id: sessionID, keepCount: keepCount)
            }
        )
    }
}

#if HERMEX_ENABLE_DEMO_STORE && !SKIP
@MainActor
public final class HermexAppStore {
    public private(set) var appState: HermexAppState
    public private(set) var onboarding: HermexOnboardingState
    public private(set) var sessions: HermexSessionListState
    public private(set) var chat: HermexChatState
    public private(set) var settings: HermexSettingsState
    public private(set) var workspace: HermexWorkspaceState
    public private(set) var git: HermexGitState
    public private(set) var panels: HermexPanelsState

    public init(
        appState: HermexAppState = HermexAppState(),
        onboarding: HermexOnboardingState = HermexOnboardingState(),
        sessions: HermexSessionListState = HermexSessionListState(),
        chat: HermexChatState = HermexChatState(),
        settings: HermexSettingsState = HermexSettingsState(),
        workspace: HermexWorkspaceState = HermexWorkspaceState(),
        git: HermexGitState = HermexGitState(),
        panels: HermexPanelsState = HermexPanelsState(),
        environment: HermexAppEnvironment
    ) {
        let activeServer = Self.server(from: appState.auth) ?? settings.activeServer
        let shouldSeedPreviewData = !Self.isFreshInstallOnboarding(
            appState: appState,
            onboarding: onboarding,
            settings: settings
        )

        self.appState = appState
        self.onboarding = onboarding
        self.sessions = shouldSeedPreviewData && sessions.sessions.isEmpty ? Self.previewSessions() : sessions
        self.chat = shouldSeedPreviewData && chat.messages.isEmpty ? Self.previewChat() : chat
        self.settings = Self.previewSettings(settings, server: activeServer)
        self.workspace = shouldSeedPreviewData && workspace.entries.isEmpty ? Self.previewWorkspace() : workspace
        self.git = shouldSeedPreviewData && git.files.isEmpty ? Self.previewGit() : git
        self.panels = shouldSeedPreviewData && panels.tasks.isEmpty && panels.skills.isEmpty && panels.memory.isEmpty ? Self.previewPanels() : panels
    }

    public func send(_ action: HermexAppAction) async {
        switch action {
        case .openRoute(let route):
            appState.route = route
        case .refresh:
            break
        case .updateOnboardingServerURL(let value):
            onboarding.serverURLString = value
            onboarding.errorMessage = nil
            onboarding.statusMessage = nil
        case .updateOnboardingDisplayName(let value):
            onboarding.displayName = value
        case .updateOnboardingPassword(let value):
            onboarding.password = value
        case .updateOnboardingCustomHeaders(let value):
            onboarding.customHeaderText = value
        case .testOnboardingConnection:
            onboarding.statusMessage = "Preview server reachable"
            onboarding.errorMessage = nil
        case .testOnboardingConnectionDraft(let serverURLString, let displayName, let password, let customHeaderText):
            applyOnboardingDraft(
                serverURLString: serverURLString,
                displayName: displayName,
                password: password,
                customHeaderText: customHeaderText
            )
            onboarding.statusMessage = "Preview server reachable"
            onboarding.errorMessage = nil
        case .connectOnboarding:
            let server = Self.previewServer()
            appState.auth = .loggedIn(server: server)
            appState.route = .sessions
            settings.activeServer = server
            onboarding.password = ""
        case .connectOnboardingDraft(let serverURLString, let displayName, let password, let customHeaderText):
            applyOnboardingDraft(
                serverURLString: serverURLString,
                displayName: displayName,
                password: password,
                customHeaderText: customHeaderText
            )
            let server = Self.previewServer(from: onboarding)
            appState.auth = .loggedIn(server: server)
            appState.route = .sessions
            settings.activeServer = server
            upsertPreviewServer(server)
            onboarding.password = ""
        case .selectServer(let server):
            appState.auth = .loggedIn(server: server)
            settings.activeServer = server
            appState.route = .sessions
        case .selectProject(let projectID):
            sessions.selectedProjectID = projectID
        case .projectCommand(let command):
            applyPreviewProjectCommand(command)
        case .openSession(let sessionID):
            appState.selectedSessionID = sessionID
            appState.route = .chat
            chat.session = sessions.sessions.first(where: { $0.sessionId == sessionID }) ?? chat.session
        case .newChat:
            appState.selectedSessionID = nil
            appState.route = .chat
            chat = Self.previewChat()
            chat.messages = []
        case .searchSessions(let query):
            sessions.searchQuery = query
        case .toggleArchived:
            sessions.isShowingArchived.toggle()
        case .updateDraft(let draft):
            chat.composer.draft = draft
        case .appendDraftText(let text):
            appendDraftText(text)
        case .applySharedDraft(let draft):
            appState.pendingSharedDraft = draft
            appState.route = .chat
            if let text = draft.text {
                appendDraftText(text)
            }
        case .hydrateCachedSessions(let cachedSessions):
            sessions.sessions = cachedSessions
            sessions.isViewingCachedData = true
        case .hydrateCachedMessages(let sessionID, let messages):
            appState.selectedSessionID = sessionID
            appState.route = .chat
            chat.messages = messages
            chat.isViewingCachedData = true
        case .setUploadingAttachment(let isUploading):
            chat.composer.isUploadingAttachment = isUploading
        case .addAttachment(let attachment):
            chat.composer.attachments.append(attachment)
        case .replaceAttachment(let id, let attachment):
            if let index = chat.composer.attachments.firstIndex(where: { $0.id == id }) {
                chat.composer.attachments[index] = attachment
            } else {
                chat.composer.attachments.append(attachment)
            }
        case .removeAttachment(let id):
            chat.composer.attachments.removeAll { $0.id == id }
        case .setVoiceRecording(let isRecording):
            chat.composer.isRecordingVoice = isRecording
        case .refreshComposerConfiguration:
            chat.composer.isLoadingConfiguration = false
        case .selectModel(let model):
            chat.composer.selectedModel = model.id
            chat.composer.selectedModelProvider = model.provider
        case .selectWorkspace(let workspace):
            chat.composer.selectedWorkspace = workspace.path
        case .selectProfile(let profile):
            chat.composer.selectedProfile = profile.name
        case .selectReasoningEffort(let effort):
            chat.composer.selectedReasoningEffort = effort
        case .sendDraft:
            sendPreviewDraft()
        case .cancelStream:
            chat.stream = HermexStreamState()
        case .undo, .retry, .compress, .forkMessage(_), .editMessage(_, _), .regenerateMessage(_):
            chat.errorMessage = nil
        case .clearConversation:
            chat.messages = []
            chat.composer.draft = ""
            chat.composer.attachments = []
            chat.errorMessage = nil
        case .approval(_):
            chat.pendingApproval = nil
        case .clarify(_):
            chat.pendingClarification = nil
        case .applyStreamEvent(_):
            break
        case .openWorkspaceEntry(let entry):
            appState.route = .workspace
            if entry.isDirectory {
                workspace.currentPath = entry.path
                workspace.preview = nil
            } else {
                workspace.preview = HermexFilePreview(path: entry.path, content: "Preview content for \(entry.name)")
            }
        case .openFile(let path):
            appState.route = .workspace
            workspace.preview = HermexFilePreview(path: path, content: "Preview content for \(path)")
        case .gitAction(_):
            appState.route = .git
        case .gitCommand(let command):
            appState.route = .git
            if case .diff(let path, _) = command {
                git.diffPath = path
                git.diffText = "diff --git a/\(path) b/\(path)\n+Skip Android preview diff"
            }
        case .updateGitCommitMessage(let message):
            git.commitMessage = message
        case .selectPanel(let panel):
            panels.selectedPanel = panel
            appState.route = .panels
        case .taskCommand(let command):
            applyPreviewTaskCommand(command)
        case .beginTaskCreation:
            panels.taskDraft = HermexTaskDraft()
            panels.selectedTaskID = nil
            panels.taskOutput = nil
            panels.errorMessage = nil
        case .beginTaskEdit(let jobID):
            beginPreviewTaskEdit(jobID: jobID)
        case .updateTaskDraft(let draft):
            panels.taskDraft = draft
            panels.errorMessage = nil
        case .cancelTaskEditor:
            panels.taskDraft = nil
        case .requestTaskDeletion(let jobID):
            panels.pendingTaskDeletionID = jobID
            panels.errorMessage = nil
        case .cancelTaskDeletion:
            panels.pendingTaskDeletionID = nil
        case .confirmTaskDeletion:
            if let jobID = panels.pendingTaskDeletionID {
                panels.pendingTaskDeletionID = nil
                applyPreviewTaskCommand(.delete(jobID: jobID))
            }
        case .dismissTaskDetails:
            panels.selectedTaskID = nil
            panels.taskOutput = nil
            panels.isLoadingTaskOutput = false
        case .toggleSkill(let name, let enabled):
            updateSkill(named: name, enabled: enabled)
        case .openSkillDetail(let name):
            openPreviewSkillDetail(name: name)
        case .loadSkillFile(let fileName):
            loadPreviewSkillFile(fileName: fileName)
        case .dismissSkillDetail:
            clearSkillDetail()
        case .dismissSkillFile:
            panels.selectedSkillFileName = nil
            panels.selectedSkillFileContent = nil
            panels.isLoadingSkillFile = false
        case .writeMemory(let section, let content):
            updateMemorySection(section: section, content: content)
        case .selectInsightsRange(let days):
            panels.insightsDays = Self.normalizedInsightsDays(days)
            panels.selectedPanel = .insights
            panels.insights = Self.previewInsights(days: panels.insightsDays)
            panels.errorMessage = nil
            appState.route = .panels
        case .signOut:
            if let server = settings.activeServer {
                appState.auth = .loggedOut(server: server)
            } else {
                appState.auth = .unconfigured
            }
            appState.route = .onboarding
            onboarding.password = ""
        }
    }

    private func appendDraftText(_ text: String) {
        let trimmed = text.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        if chat.composer.draft.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines).isEmpty {
            chat.composer.draft = trimmed
        } else {
            chat.composer.draft += "\n\(trimmed)"
        }
    }

    private func sendPreviewDraft() {
        let draft = chat.composer.draft.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines)
        let attachments = chat.composer.attachments
        guard !draft.isEmpty || !attachments.isEmpty else { return }
        chat.composer.draft = ""
        chat.composer.attachments = []
        chat.messages.append(HermexChatMessageDTO(
            role: "user",
            content: draft.isEmpty ? nil : draft,
            attachments: attachments
        ))
        chat.messages.append(HermexChatMessageDTO(
            role: "assistant",
            content: "This Skip Android APK is rendering the shared SwiftUI Hermex interface. Live backend calls are intentionally stubbed in this preview build."
        ))
    }

    private func applyOnboardingDraft(
        serverURLString: String,
        displayName: String,
        password: String,
        customHeaderText: String
    ) {
        onboarding.serverURLString = serverURLString
        onboarding.displayName = displayName
        onboarding.password = password
        onboarding.customHeaderText = customHeaderText
        onboarding.errorMessage = nil
        onboarding.statusMessage = nil
    }

    private func upsertPreviewServer(_ server: HermexServerIdentity) {
        settings.activeServer = server
        if let existingIndex = settings.servers.firstIndex(where: {
            HermexServerURLNormalizer.normalizedID(for: $0.baseURL) == HermexServerURLNormalizer.normalizedID(for: server.baseURL)
        }) {
            settings.servers[existingIndex] = server
        } else {
            settings.servers.append(server)
        }
    }

    private func updateSkill(named name: String, enabled: Bool) {
        let normalizedName = name.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines)
        guard !normalizedName.isEmpty else { return }
        let comparableName = normalizedName.lowercased()
        if let index = panels.skills.firstIndex(where: { $0.name.lowercased() == comparableName }) {
            panels.skills[index].enabled = enabled
            panels.errorMessage = nil
        }
    }

    private func openPreviewSkillDetail(name: String) {
        let normalizedName = name.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines)
        guard !normalizedName.isEmpty else { return }
        panels.selectedSkillName = normalizedName
        panels.selectedSkillDetail = HermexSkillDetailDTO(
            name: normalizedName,
            content: "# \(normalizedName)\n\nPreview skill content for the shared Android UI.",
            linkedFiles: ["SKILL.md", "README.md"]
        )
        panels.selectedSkillFileName = nil
        panels.selectedSkillFileContent = nil
        panels.isLoadingSkillDetail = false
        panels.isLoadingSkillFile = false
        panels.errorMessage = nil
    }

    private func loadPreviewSkillFile(fileName: String) {
        guard panels.selectedSkillName != nil else { return }
        panels.selectedSkillFileName = fileName
        panels.selectedSkillFileContent = "Preview content for \(fileName)."
        panels.isLoadingSkillFile = false
        panels.errorMessage = nil
    }

    private func clearSkillDetail() {
        panels.selectedSkillName = nil
        panels.selectedSkillDetail = nil
        panels.selectedSkillFileName = nil
        panels.selectedSkillFileContent = nil
        panels.isLoadingSkillDetail = false
        panels.isLoadingSkillFile = false
    }

    private func applyPreviewTaskCommand(_ command: HermexTaskCommand) {
        switch command {
        case .run(let id):
            updateTask(jobID: id, status: "Running")
        case .pause(let id):
            updateTask(jobID: id, status: "Paused")
        case .resume(let id):
            updateTask(jobID: id, status: "Active")
        case .create(let draft):
            guard draft.validationMessage == nil else {
                panels.errorMessage = draft.validationMessage
                return
            }
            let jobID = "preview-task-\(panels.tasks.count + 1)"
            panels.tasks.append(HermexTaskDTO(
                id: jobID,
                title: draft.trimmedName ?? draft.trimmedPrompt,
                status: "Active",
                schedule: draft.trimmedSchedule,
                prompt: draft.trimmedPrompt,
                deliver: draft.trimmedDeliver,
                skills: draft.skills,
                model: draft.trimmedModel,
                profile: draft.trimmedProfile,
                toastNotifications: draft.toastNotifications
            ))
            panels.taskDraft = nil
            panels.errorMessage = nil
        case .update(let draft):
            guard draft.validationMessage == nil else {
                panels.errorMessage = draft.validationMessage
                return
            }
            guard let jobID = draft.editingJobID,
                  let index = panels.tasks.firstIndex(where: { $0.id == jobID })
            else {
                panels.errorMessage = "Task id unavailable."
                return
            }
            panels.tasks[index].title = draft.trimmedName ?? draft.trimmedPrompt
            panels.tasks[index].schedule = draft.trimmedSchedule
            panels.tasks[index].prompt = draft.trimmedPrompt
            panels.tasks[index].deliver = draft.trimmedDeliver
            panels.tasks[index].skills = draft.skills
            panels.tasks[index].model = draft.trimmedModel
            panels.tasks[index].profile = draft.trimmedProfile
            panels.tasks[index].toastNotifications = draft.toastNotifications
            panels.taskDraft = nil
            panels.errorMessage = nil
        case .delete(let jobID):
            panels.tasks.removeAll { $0.id == jobID }
            if panels.selectedTaskID == jobID {
                panels.selectedTaskID = nil
                panels.taskOutput = nil
            }
            panels.errorMessage = nil
        case .loadOutput(let jobID, _):
            panels.selectedTaskID = jobID
            panels.isLoadingTaskOutput = false
            panels.taskOutput = .dictionary([
                "job_id": .string(jobID),
                "outputs": .array([
                    .dictionary([
                        "filename": .string("latest-run.md"),
                        "content": .string("Preview output for \(jobID).")
                    ])
                ])
            ])
            panels.errorMessage = nil
        }
    }

    private func beginPreviewTaskEdit(jobID: String) {
        guard let task = panels.tasks.first(where: { $0.id == jobID }) else { return }
        panels.taskDraft = HermexTaskDraft(task: task)
        panels.selectedTaskID = nil
        panels.taskOutput = nil
        panels.errorMessage = nil
    }

    private func updateTask(jobID: String, status: String) {
        let normalizedJobID = jobID.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines)
        guard !normalizedJobID.isEmpty else { return }
        if let index = panels.tasks.firstIndex(where: { $0.id == normalizedJobID }) {
            panels.tasks[index].status = status
            panels.errorMessage = nil
        }
    }

    private func updateMemorySection(section: String, content: String) {
        let normalizedSection = section.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines)
        guard !normalizedSection.isEmpty else { return }
        if let index = panels.memory.firstIndex(where: { $0.section == normalizedSection }) {
            panels.memory[index].content = content
        } else {
            panels.memory.append(HermexMemorySectionDTO(section: normalizedSection, content: content))
        }
        panels.errorMessage = nil
    }

    private func applyPreviewProjectCommand(_ command: HermexProjectCommand) {
        switch command {
        case .moveSession(let sessionID, let projectID):
            if let index = sessions.sessions.firstIndex(where: { $0.id == sessionID }) {
                sessions.sessions[index].projectId = projectID
            }
        case .create(let name, let color, let moveSessionID):
            let projectID = "preview-project-" + String(sessions.projects.count + 1)
            sessions.projects.append(HermexProjectDTO(projectId: projectID, name: name, color: color))
            if let moveSessionID,
               let index = sessions.sessions.firstIndex(where: { $0.id == moveSessionID }) {
                sessions.sessions[index].projectId = projectID
            }
        case .rename(let projectID, let name, let color):
            if let index = sessions.projects.firstIndex(where: { $0.id == projectID }) {
                sessions.projects[index].name = name
                if let color {
                    sessions.projects[index].color = color
                }
            }
        case .delete(let projectID):
            sessions.projects.removeAll { $0.id == projectID }
            for index in sessions.sessions.indices where sessions.sessions[index].projectId == projectID {
                sessions.sessions[index].projectId = nil
            }
            if sessions.selectedProjectID == projectID {
                sessions.selectedProjectID = nil
            }
        }
    }

    private static func normalizedInsightsDays(_ days: Int) -> Int {
        switch days {
        case 1, 7, 30, 365:
            return days
        default:
            return 30
        }
    }

    private static func server(from auth: HermexAuthState) -> HermexServerIdentity? {
        switch auth {
        case .loggedIn(let server), .loggedOut(let server):
            return server
        case .unconfigured:
            return nil
        }
    }

    private static func isFreshInstallOnboarding(
        appState: HermexAppState,
        onboarding: HermexOnboardingState,
        settings: HermexSettingsState
    ) -> Bool {
        appState.auth == .unconfigured &&
            appState.route == .onboarding &&
            onboarding.serverURLString.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines).isEmpty &&
            settings.servers.isEmpty &&
            settings.activeServer == nil
    }

    private static func previewServer() -> HermexServerIdentity {
        HermexServerIdentity(
            baseURL: URL(string: "https://hermex.local")!,
            displayName: "Hermex Preview"
        )
    }

    private static func previewServer(from onboarding: HermexOnboardingState) -> HermexServerIdentity {
        let trimmed = onboarding.serverURLString.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines)
        let candidate = trimmed.contains("://") ? trimmed : "https://\(trimmed)"
        let url = URL(string: candidate) ?? URL(string: "https://hermex.local")!
        let normalizedURL = URL(string: HermexServerURLNormalizer.normalizedID(for: url)) ?? url
        let displayName = onboarding.displayName.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines)
        return HermexServerIdentity(
            baseURL: normalizedURL,
            displayName: displayName.isEmpty ? (normalizedURL.host ?? "Hermex") : displayName
        )
    }

    private static func previewSettings(_ settings: HermexSettingsState, server: HermexServerIdentity?) -> HermexSettingsState {
        var updated = settings
        if let server {
            updated.activeServer = updated.activeServer ?? server
            if updated.servers.isEmpty {
                updated.servers = [server]
            }
        }
        updated.defaultModel = updated.defaultModel ?? "gpt-5.5"
        updated.defaultProfile = updated.defaultProfile ?? "default"
        return updated
    }

    private static func previewSessions() -> HermexSessionListState {
        HermexSessionListState(
            sessions: [
                HermexSessionDTO(sessionId: "preview-chat", title: "Skip Android parity preview", updatedAt: 1783382400, messageCount: 8, workspace: "workspace", pinned: true, model: "gpt-5.5"),
                HermexSessionDTO(sessionId: "workspace-review", title: "Workspace and git review", updatedAt: 1783378800, messageCount: 48, workspace: "workspace", model: "deepseek-v4-pro"),
                HermexSessionDTO(sessionId: "long-running-stream", title: "Long running stream", updatedAt: 1783314000, messageCount: 54, workspace: "workspace", model: "cx/gpt-5.5")
            ],
            projects: [
                HermexProjectDTO(projectId: "default", name: "default", color: "#f8d84a")
            ],
            activeProfileName: "default"
        )
    }

    private static func previewChat() -> HermexChatState {
        HermexChatState(
            session: HermexSessionDTO(sessionId: "preview-chat", title: "Skip Android parity preview", messageCount: 4, workspace: "workspace", model: "gpt-5.5"),
            messages: [
                HermexChatMessageDTO(role: "user", content: "Model hermes đang dùng là gì?"),
                HermexChatMessageDTO(role: "assistant", content: "Hermex Skip Android is using the shared SwiftUI interface from the iOS app. This preview APK is ready for visual parity checks while Android platform services are wired behind shims."),
                HermexChatMessageDTO(role: "user", content: "Reasoning effort?"),
                HermexChatMessageDTO(role: "assistant", content: "Reasoning controls are rendered from the same composer state and picker contracts as iOS.")
            ],
            composer: HermexComposerState(
                selectedModel: "gpt-5.5",
                selectedWorkspace: "Home",
                selectedProfile: "default",
                selectedReasoningEffort: "medium",
                availableModels: [
                    HermexModelOption(id: "gpt-5.5", name: "gpt-5.5"),
                    HermexModelOption(id: "deepseek-v4-pro", name: "deepseek-v4-pro")
                ],
                availableProfiles: [
                    HermexProfileOption(name: "default", displayName: "default", isActive: true)
                ],
                availableWorkspaces: [
                    HermexWorkspaceRootDTO(path: "Home", name: "Home", exists: true),
                    HermexWorkspaceRootDTO(path: "workspace", name: "workspace", exists: true)
                ],
                supportedReasoningEfforts: ["low", "medium", "high"]
            )
        )
    }

    private static func previewWorkspace() -> HermexWorkspaceState {
        HermexWorkspaceState(
            roots: [
                HermexWorkspaceRootDTO(path: "Home", name: "Home", exists: true),
                HermexWorkspaceRootDTO(path: "workspace", name: "workspace", exists: true)
            ],
            currentPath: "workspace",
            entries: [
                HermexWorkspaceEntryDTO(name: "Sources", path: "workspace/Sources", type: "directory", isDirectory: true),
                HermexWorkspaceEntryDTO(name: "Package.swift", path: "workspace/Package.swift", type: "swift", isDirectory: false, size: 4096),
                HermexWorkspaceEntryDTO(name: "README.md", path: "workspace/README.md", type: "markdown", isDirectory: false, size: 8192)
            ]
        )
    }

    private static func previewGit() -> HermexGitState {
        HermexGitState(
            isRepository: true,
            branch: "master",
            upstream: "fork/master",
            ahead: 1,
            behind: 0,
            files: [
                HermexGitFileChange(path: "Sources/HermexCore/HermexAppStore.swift", status: "modified", additions: 212, deletions: 0, isStaged: false),
                HermexGitFileChange(path: "ci/skip-hermex-app/HermexSkipApp.swift", status: "modified", additions: 4, deletions: 1, isStaged: false)
            ],
            commitMessage: "Port Hermex SwiftUI through Skip"
        )
    }

    private static func previewPanels() -> HermexPanelsState {
        HermexPanelsState(
            tasks: [
                HermexTaskDTO(
                    id: "weekly-graphics",
                    title: "Weekly computer graphics research digest",
                    status: "Active",
                    schedule: "0 9 * * 1",
                    prompt: "Summarize the latest computer graphics papers.",
                    deliver: "local",
                    skills: ["architecture-diagram"],
                    model: "gpt-5.5",
                    profile: "default",
                    toastNotifications: true
                )
            ],
            skills: [
                HermexSkillDTO(name: "coding-agent-delegation", enabled: true, summary: "Delegate coding tasks to external agent CLIs."),
                HermexSkillDTO(name: "architecture-diagram", enabled: true, summary: "Generate dark-themed architecture diagrams.")
            ],
            memory: [
                HermexMemorySectionDTO(section: "My Notes", content: "Shared SwiftUI memory panel preview for Android visual parity.")
            ],
            insights: Self.previewInsights(days: 30),
            insightsDays: 30,
            selectedPanel: .tasks
        )
    }

    private static func previewInsights(days: Int) -> HermexJSONValue {
        HermexJSONValue.dictionary([
            "period_days": HermexJSONValue.number(Double(days)),
            "total_sessions": HermexJSONValue.number(30),
            "total_messages": HermexJSONValue.number(1_312),
            "total_input_tokens": HermexJSONValue.number(5_957_098),
            "total_output_tokens": HermexJSONValue.number(246_393),
            "total_tokens": HermexJSONValue.number(6_203_491),
            "total_cost": HermexJSONValue.number(2.1145),
            "total_cache_hit_percent": HermexJSONValue.number(76),
            "total_cache_read_tokens": HermexJSONValue.number(224_128),
            "models": HermexJSONValue.array([
                HermexJSONValue.dictionary([
                    "model": HermexJSONValue.string("deepseek-v4-pro"),
                    "sessions": HermexJSONValue.number(12),
                    "total_tokens": HermexJSONValue.number(1_531_842),
                    "cost": HermexJSONValue.number(0.482),
                    "token_share": HermexJSONValue.number(25),
                    "cache_hit_percent": HermexJSONValue.number(63)
                ]),
                HermexJSONValue.dictionary([
                    "model": HermexJSONValue.string("gpt-5.5"),
                    "sessions": HermexJSONValue.number(18),
                    "total_tokens": HermexJSONValue.number(4_477_168),
                    "cost": HermexJSONValue.number(1.6325),
                    "token_share": HermexJSONValue.number(72),
                    "cache_hit_percent": HermexJSONValue.number(81)
                ])
            ])
        ])
    }
}
#else
@MainActor
public final class HermexAppStore {
    public private(set) var appState: HermexAppState
    public private(set) var onboarding: HermexOnboardingState
    public private(set) var sessions: HermexSessionListState
    public private(set) var chat: HermexChatState
    public private(set) var settings: HermexSettingsState
    public private(set) var workspace: HermexWorkspaceState
    public private(set) var git: HermexGitState
    public private(set) var panels: HermexPanelsState

    private let environment: HermexAppEnvironment

    public init(
        appState: HermexAppState = HermexAppState(),
        onboarding: HermexOnboardingState = HermexOnboardingState(),
        sessions: HermexSessionListState = HermexSessionListState(),
        chat: HermexChatState = HermexChatState(),
        settings: HermexSettingsState = HermexSettingsState(),
        workspace: HermexWorkspaceState = HermexWorkspaceState(),
        git: HermexGitState = HermexGitState(),
        panels: HermexPanelsState = HermexPanelsState(),
        environment: HermexAppEnvironment
    ) {
        self.appState = appState
        self.onboarding = onboarding
        self.sessions = sessions
        self.chat = chat
        self.settings = settings
        self.workspace = workspace
        self.git = git
        self.panels = panels
        self.environment = environment
    }

    public func send(_ action: HermexAppAction) async {
        switch action {
        case .openRoute(let route):
            appState.route = route
        case .refresh:
            await refreshCurrentRoute()
        case .updateOnboardingServerURL(let value):
            onboarding.serverURLString = value
            onboarding.errorMessage = nil
            onboarding.statusMessage = nil
        case .updateOnboardingDisplayName(let value):
            onboarding.displayName = value
        case .updateOnboardingPassword(let value):
            onboarding.password = value
        case .updateOnboardingCustomHeaders(let value):
            onboarding.customHeaderText = value
        case .testOnboardingConnection:
            await testOnboardingConnection()
        case .testOnboardingConnectionDraft(let serverURLString, let displayName, let password, let customHeaderText):
            applyOnboardingDraft(
                serverURLString: serverURLString,
                displayName: displayName,
                password: password,
                customHeaderText: customHeaderText
            )
            await testOnboardingConnection()
        case .connectOnboarding:
            await connectOnboarding()
        case .connectOnboardingDraft(let serverURLString, let displayName, let password, let customHeaderText):
            applyOnboardingDraft(
                serverURLString: serverURLString,
                displayName: displayName,
                password: password,
                customHeaderText: customHeaderText
            )
            await connectOnboarding()
        case .selectServer(let server):
            await environment.updateServerRuntime(server, true)
            selectServer(server)
            appState.auth = .loggedIn(server: server)
            appState.route = .sessions
            await refreshSessions()
        case .selectProject(let projectID):
            sessions.selectedProjectID = projectID
        case .projectCommand(let command):
            await runProjectCommand(command)
        case .openSession(let sessionID):
            await openSession(sessionID)
        case .newChat:
            appState.selectedSessionID = nil
            appState.route = .chat
            chat = HermexChatState(composer: chat.composer)
        case .searchSessions(let query):
            sessions.searchQuery = query
            await refreshSessions()
        case .toggleArchived:
            sessions.isShowingArchived.toggle()
            await refreshSessions()
        case .updateDraft(let draft):
            chat.composer.draft = draft
        case .appendDraftText(let text):
            appendDraftText(text)
        case .applySharedDraft(let draft):
            applySharedDraft(draft)
        case .hydrateCachedSessions(let cachedSessions):
            sessions.sessions = cachedSessions
            sessions.isViewingCachedData = true
        case .hydrateCachedMessages(let sessionID, let messages):
            appState.selectedSessionID = sessionID
            appState.route = .chat
            chat.messages = messages
            chat.isViewingCachedData = true
        case .setUploadingAttachment(let isUploading):
            chat.composer.isUploadingAttachment = isUploading
        case .addAttachment(let attachment):
            chat.composer.attachments.append(attachment)
        case .replaceAttachment(let id, let attachment):
            if let index = chat.composer.attachments.firstIndex(where: { $0.id == id }) {
                chat.composer.attachments[index] = attachment
            } else {
                chat.composer.attachments.append(attachment)
            }
        case .removeAttachment(let id):
            chat.composer.attachments.removeAll { $0.id == id }
        case .setVoiceRecording(let isRecording):
            chat.composer.isRecordingVoice = isRecording
        case .refreshComposerConfiguration:
            await refreshComposerConfiguration()
        case .selectModel(let model):
            chat.composer.selectedModel = model.id
            chat.composer.selectedModelProvider = model.provider
            await refreshReasoningConfiguration()
        case .selectWorkspace(let workspace):
            chat.composer.selectedWorkspace = workspace.path
        case .selectProfile(let profile):
            chat.composer.selectedProfile = profile.name
        case .selectReasoningEffort(let effort):
            await selectReasoningEffort(effort)
        case .sendDraft:
            await sendDraft()
        case .cancelStream:
            await cancelStream()
        case .undo:
            await mutateCurrentSession(environment.undoSession)
        case .retry:
            await mutateCurrentSession(environment.retrySession)
        case .compress:
            await compressCurrentSession()
        case .clearConversation:
            await clearCurrentSession()
        case .forkMessage(let context):
            await forkMessage(context)
        case .editMessage(let context, let text):
            await editMessage(context, text: text)
        case .regenerateMessage(let context):
            await regenerateMessage(context)
        case .approval(let choice):
            await respondApproval(choice)
        case .clarify(let response):
            await respondClarification(response)
        case .applyStreamEvent(let event):
            applyStreamEvent(event)
        case .openWorkspaceEntry(let entry):
            if entry.isDirectory {
                await loadWorkspace(path: entry.path)
            } else {
                await loadFilePreview(path: entry.path)
            }
        case .openFile(let path):
            appState.route = .workspace
            await loadFilePreview(path: path)
        case .gitAction(let action):
            await runGitAction(action)
        case .gitCommand(let command):
            await runGitCommand(command)
        case .updateGitCommitMessage(let message):
            git.commitMessage = message
        case .selectPanel(let panel):
            panels.selectedPanel = panel
            appState.route = .panels
            await loadPanel(panel)
        case .taskCommand(let command):
            await runTaskCommand(command)
        case .beginTaskCreation:
            panels.taskDraft = HermexTaskDraft()
            panels.selectedTaskID = nil
            panels.taskOutput = nil
            panels.errorMessage = nil
        case .beginTaskEdit(let jobID):
            beginTaskEdit(jobID: jobID)
        case .updateTaskDraft(let draft):
            panels.taskDraft = draft
            panels.errorMessage = nil
        case .cancelTaskEditor:
            panels.taskDraft = nil
        case .requestTaskDeletion(let jobID):
            panels.pendingTaskDeletionID = jobID
            panels.errorMessage = nil
        case .cancelTaskDeletion:
            panels.pendingTaskDeletionID = nil
        case .confirmTaskDeletion:
            await confirmTaskDeletion()
        case .dismissTaskDetails:
            panels.selectedTaskID = nil
            panels.taskOutput = nil
            panels.isLoadingTaskOutput = false
        case .toggleSkill(let name, let enabled):
            await toggleSkill(name: name, enabled: enabled)
        case .openSkillDetail(let name):
            await openSkillDetail(name: name)
        case .loadSkillFile(let fileName):
            await loadSkillFile(fileName: fileName)
        case .dismissSkillDetail:
            clearSkillDetail()
        case .dismissSkillFile:
            panels.selectedSkillFileName = nil
            panels.selectedSkillFileContent = nil
            panels.isLoadingSkillFile = false
        case .writeMemory(let section, let content):
            await writeMemory(section: section, content: content)
        case .selectInsightsRange(let days):
            await selectInsightsRange(days: days)
        case .signOut:
            await signOut()
        }
    }

    private func refreshCurrentRoute() async {
        switch appState.route {
        case .onboarding:
            await testOnboardingConnection()
        case .sessions:
            await refreshSessions()
        case .chat:
            if let sessionID = appState.selectedSessionID {
                await openSession(sessionID)
            }
        case .workspace:
            await loadWorkspace(path: workspace.currentPath)
        case .git:
            await loadGitStatus()
        case .panels:
            await loadPanel(panels.selectedPanel)
        default:
            break
        }
    }

    private func applyOnboardingDraft(
        serverURLString: String,
        displayName: String,
        password: String,
        customHeaderText: String
    ) {
        onboarding.serverURLString = serverURLString
        onboarding.displayName = displayName
        onboarding.password = password
        onboarding.customHeaderText = customHeaderText
        onboarding.errorMessage = nil
        onboarding.statusMessage = nil
    }

    private func refreshSessions() async {
        sessions.isLoading = true
        sessions.errorMessage = nil
        do {
            let response = try await environment.loadSessions(sessions.isShowingArchived, sessions.isShowingArchived ? 50 : nil)
            sessions.sessions = response.sessions ?? []
            sessions.projects = response.projects ?? []
        } catch {
            sessions.errorMessage = String(describing: error)
        }
        sessions.isLoading = false
    }

    private func runProjectCommand(_ command: HermexProjectCommand) async {
        guard !sessions.isViewingCachedData else {
            sessions.errorMessage = "Project changes are unavailable while viewing cached data."
            return
        }

        sessions.isLoading = true
        sessions.errorMessage = nil
        do {
            let response = try await environment.performProjectCommand(command)
            if let error = projectCommandError(response) {
                throw HermexAPIError.network(error)
            }

            if case .create(_, _, let moveSessionID) = command,
               let moveSessionID,
               let projectID = projectID(from: response) {
                let moveResponse = try await environment.performProjectCommand(
                    .moveSession(sessionID: moveSessionID, projectID: projectID)
                )
                if let error = projectCommandError(moveResponse) {
                    throw HermexAPIError.network("Project created, but moving the session failed: " + error)
                }
            }

            if case .delete(let projectID) = command,
               sessions.selectedProjectID == projectID {
                sessions.selectedProjectID = nil
            }
            await refreshSessions()
        } catch {
            sessions.isLoading = false
            sessions.errorMessage = String(describing: error)
        }
    }

    private func projectCommandError(_ response: HermexJSONValue) -> String? {
        let fields = response.objectValue ?? [:]
        if fields["ok"]?.boolValue == false {
            return fields["error"]?.stringValue ?? "Project operation failed."
        }
        return fields["error"]?.stringValue
    }

    private func projectID(from response: HermexJSONValue) -> String? {
        let fields = response.objectValue ?? [:]
        if let project = fields["project"]?.objectValue {
            return project["project_id"]?.stringValue ?? project["id"]?.stringValue
        }
        return fields["project_id"]?.stringValue ?? fields["id"]?.stringValue
    }

    private func testOnboardingConnection() async {
        guard let server = onboardingServerIdentity() else { return }
        onboarding.isTestingConnection = true
        onboarding.errorMessage = nil
        onboarding.statusMessage = "Testing \(server.displayName)"
        do {
            _ = try await environment.testServerConnection(server)
            await environment.updateServerRuntime(server, false)
            onboarding.lastValidatedServer = server
            onboarding.statusMessage = "Connection OK"
            appState.auth = .loggedOut(server: server)
            upsertServer(server)
        } catch {
            onboarding.errorMessage = String(describing: error)
            onboarding.statusMessage = nil
        }
        onboarding.isTestingConnection = false
    }

    private func connectOnboarding() async {
        guard let server = onboardingServerIdentity() else { return }
        let password = onboarding.password
        guard !password.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines).isEmpty else {
            onboarding.errorMessage = "Password is required."
            return
        }

        onboarding.isSigningIn = true
        onboarding.errorMessage = nil
        onboarding.statusMessage = "Signing in"
        do {
            _ = try await environment.loginToServer(server, password)
            await environment.updateServerRuntime(server, true)
            onboarding.password = ""
            onboarding.lastValidatedServer = server
            onboarding.statusMessage = "Connected"
            appState.auth = .loggedIn(server: server)
            appState.route = .sessions
            settings.activeServer = server
            upsertServer(server)
            await refreshSessions()
        } catch {
            onboarding.errorMessage = String(describing: error)
            onboarding.statusMessage = nil
        }
        onboarding.isSigningIn = false
    }

    private func selectServer(_ server: HermexServerIdentity) {
        onboarding.serverURLString = server.baseURL.absoluteString
        onboarding.displayName = server.displayName
        let headerKeys = server.customHeaders.keys.sorted {
            $0.localizedCaseInsensitiveCompare($1) == ComparisonResult.orderedAscending
        }
        onboarding.customHeaderText = headerKeys
            .map { key in "\(key): \(server.customHeaders[key] ?? "")" }
            .joined(separator: "\n")
        onboarding.errorMessage = nil
        onboarding.statusMessage = nil
        appState.auth = .loggedOut(server: server)
        settings.activeServer = server
    }

    private func openSession(_ sessionID: String) async {
        appState.selectedSessionID = sessionID
        appState.route = .chat
        chat.isLoading = true
        chat.errorMessage = nil
        do {
            let response = try await environment.loadSession(sessionID)
            chat.session = response.session
            chat.messages = response.messages ?? []
        } catch {
            chat.errorMessage = String(describing: error)
        }
        chat.isLoading = false
    }

    private func sendDraft() async {
        let draft = chat.composer.draft.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines)
        let existingSessionID = appState.selectedSessionID
        let composer = chat.composer
        let message = Self.chatMessageText(draft: draft, attachments: composer.attachments)
        guard !message.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines).isEmpty else { return }
        chat.composer.draft = ""
        chat.messages.append(HermexChatMessageDTO(
            role: "user",
            content: draft.isEmpty ? nil : draft,
            timestamp: Date().timeIntervalSince1970,
            attachments: composer.attachments
        ))
        chat.stream = HermexStreamState(isStreaming: true, liveToolActivity: "Starting response")
        chat.errorMessage = nil

        do {
            let response = try await environment.startChat(
                existingSessionID,
                message,
                composer.selectedWorkspace,
                composer.selectedModel,
                composer.selectedModelProvider,
                composer.selectedProfile,
                composer.attachments.map(\.jsonValue)
            )
            chat.composer.attachments = []
            if let sessionID = response.stringValue(forKey: "session_id") ?? response.stringValue(forKey: "sessionId") {
                appState.selectedSessionID = sessionID
            }
            chat.stream.streamID = response.stringValue(forKey: "stream_id") ?? response.stringValue(forKey: "streamId")
        } catch {
            chat.stream.isStreaming = false
            chat.errorMessage = String(describing: error)
            chat.composer.draft = draft
        }
    }

    private func appendDraftText(_ text: String) {
        let trimmed = text.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        if chat.composer.draft.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines).isEmpty {
            chat.composer.draft = trimmed
        } else {
            chat.composer.draft += "\n\(trimmed)"
        }
    }

    private static func chatMessageText(draft: String, attachments: [HermexAttachmentDTO]) -> String {
        let references = attachments
            .compactMap { $0.path ?? $0.name }
            .map { $0.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
        guard !references.isEmpty else { return draft }
        return "\(draft)\n\n[Attached files: \(references.joined(separator: ", "))]"
    }

    private func applySharedDraft(_ draft: HermexSharedDraft) {
        appState.pendingSharedDraft = draft
        appState.route = .chat
        if let text = draft.text {
            appendDraftText(text)
        }
        let sharedAttachments = draft.attachmentURLs.map { url in
            HermexAttachmentDTO(
                name: url.lastPathComponent.isEmpty ? "shared-file" : url.lastPathComponent,
                path: url.path,
                mime: nil,
                size: nil,
                isImage: nil
            )
        }
        chat.composer.attachments.append(contentsOf: sharedAttachments)
    }

    private func refreshComposerConfiguration() async {
        chat.composer.isLoadingConfiguration = true
        chat.composer.configurationErrorMessage = nil
        do {
            async let models = environment.loadModels()
            async let profiles = environment.loadProfiles()
            async let workspaces = environment.loadWorkspaces()
            let loadedModels = try await models
            let loadedProfiles = try await profiles
            let loadedWorkspaces = try await workspaces

            chat.composer.availableModels = loadedModels.normalizedModels
            chat.composer.availableProfiles = loadedProfiles.profiles ?? []
            chat.composer.availableWorkspaces = loadedWorkspaces.normalizedRoots
            if chat.composer.selectedModel == nil {
                chat.composer.selectedModel = loadedModels.defaultModel ?? chat.session?.model
                chat.composer.selectedModelProvider = loadedModels.activeProvider ?? chat.session?.modelProvider
            }
            if chat.composer.selectedProfile == nil {
                chat.composer.selectedProfile = loadedProfiles.active
                    ?? loadedProfiles.profiles?.first(where: { $0.isActive == true })?.name
                    ?? loadedProfiles.profiles?.first?.name
            }
            if chat.composer.selectedWorkspace == nil {
                chat.composer.selectedWorkspace = chat.session?.workspace
                    ?? loadedWorkspaces.last
                    ?? loadedWorkspaces.normalizedRoots.first?.path
            }
            await refreshReasoningConfiguration()
        } catch {
            chat.composer.configurationErrorMessage = String(describing: error)
        }
        chat.composer.isLoadingConfiguration = false
    }

    private func refreshReasoningConfiguration() async {
        do {
            let response = try await environment.loadReasoning(chat.composer.selectedModel, chat.composer.selectedModelProvider)
            chat.composer.supportedReasoningEfforts = response.supportedEfforts ?? []
            chat.composer.showsReasoningControl = response.supportsReasoningEffort ?? !(response.supportedEfforts ?? []).isEmpty
            chat.composer.selectedReasoningEffort = response.effort ?? chat.composer.selectedReasoningEffort
        } catch {
            chat.composer.configurationErrorMessage = String(describing: error)
        }
    }

    private func selectReasoningEffort(_ effort: String) async {
        chat.composer.selectedReasoningEffort = effort
        do {
            _ = try await environment.saveReasoningEffort(
                effort,
                chat.composer.selectedModel,
                chat.composer.selectedModelProvider
            )
        } catch {
            chat.composer.configurationErrorMessage = String(describing: error)
        }
    }

    private func cancelStream() async {
        guard let streamID = chat.stream.streamID else {
            chat.stream.isStreaming = false
            return
        }
        do {
            _ = try await environment.cancelStream(streamID)
            chat.stream.isStreaming = false
        } catch {
            chat.errorMessage = String(describing: error)
        }
    }

    private func respondApproval(_ choice: String) async {
        guard let sessionID = appState.selectedSessionID else { return }
        do {
            _ = try await environment.respondApproval(sessionID, choice, chat.pendingApproval?.approvalID)
            chat.pendingApproval = nil
        } catch {
            chat.errorMessage = String(describing: error)
        }
    }

    private func mutateCurrentSession(_ mutation: @Sendable (_ sessionID: String) async throws -> HermexJSONValue) async {
        guard let sessionID = appState.selectedSessionID else { return }
        chat.errorMessage = nil
        do {
            _ = try await mutation(sessionID)
            await openSession(sessionID)
        } catch {
            chat.errorMessage = String(describing: error)
        }
    }

    private func forkMessage(_ context: HermexMessageActionContext) async {
        guard let sessionID = appState.selectedSessionID else { return }
        guard !chat.isViewingCachedData else {
            chat.errorMessage = "Reconnect to the server to fork a conversation."
            return
        }
        guard !chat.stream.isStreaming else {
            chat.errorMessage = "Wait for the current response to finish before forking."
            return
        }

        chat.errorMessage = nil
        do {
            let response = try await environment.branchSession(sessionID, context.keepCountThroughMessage)
            guard let forkedSessionID = response.stringValue(forKey: "session_id")
                ?? response.stringValue(forKey: "sessionId"),
                  !forkedSessionID.isEmpty
            else {
                chat.errorMessage = response.stringValue(forKey: "error") ?? "The server did not return the forked session ID."
                return
            }
            await openSession(forkedSessionID)
        } catch {
            chat.errorMessage = String(describing: error)
        }
    }

    private func editMessage(_ context: HermexMessageActionContext, text: String) async {
        guard context.role == .user else {
            chat.errorMessage = "Only user messages can be edited."
            return
        }
        let editedText = text.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines)
        guard !editedText.isEmpty else {
            chat.errorMessage = "The edited message cannot be empty."
            return
        }
        guard let sessionID = appState.selectedSessionID else { return }
        guard !chat.isViewingCachedData else {
            chat.errorMessage = "Reconnect to the server to edit a message."
            return
        }
        guard !chat.stream.isStreaming else {
            chat.errorMessage = "Wait for the current response to finish before editing."
            return
        }

        do {
            let truncated = try await environment.truncateSession(sessionID, context.fullHistoryIndex)
            applyHistoryMutationResponse(truncated)
            try await startMessageAfterHistoryMutation(
                sessionID: sessionID,
                text: editedText,
                appendOptimisticUser: true
            )
        } catch {
            chat.stream.isStreaming = false
            chat.errorMessage = String(describing: error)
        }
    }

    private func regenerateMessage(_ context: HermexMessageActionContext) async {
        guard context.role == .assistant else {
            chat.errorMessage = "Only assistant messages can be regenerated."
            return
        }
        guard let sessionID = appState.selectedSessionID else { return }
        guard !chat.isViewingCachedData else {
            chat.errorMessage = "Reconnect to the server to regenerate a response."
            return
        }
        guard !chat.stream.isStreaming else {
            chat.errorMessage = "Wait for the current response to finish before regenerating."
            return
        }
        guard let userText = HermexMessageActionContextResolver.precedingUserMessageText(
            in: chat.messages,
            beforeVisibleIndex: context.visibleIndex
        ) else {
            chat.errorMessage = "Load older messages before regenerating this response."
            return
        }

        do {
            let truncated = try await environment.truncateSession(sessionID, context.fullHistoryIndex)
            applyHistoryMutationResponse(truncated)
            try await startMessageAfterHistoryMutation(
                sessionID: sessionID,
                text: userText,
                appendOptimisticUser: false
            )
        } catch {
            chat.stream.isStreaming = false
            chat.errorMessage = String(describing: error)
        }
    }

    private func applyHistoryMutationResponse(_ response: HermexSessionResponse) {
        if let session = response.session {
            chat.session = session
        }
        if let messages = response.messages {
            chat.messages = messages
        }
        chat.errorMessage = response.error
        chat.stream = HermexStreamState()
    }

    private func startMessageAfterHistoryMutation(
        sessionID: String,
        text: String,
        appendOptimisticUser: Bool
    ) async throws {
        let composer = chat.composer
        chat.errorMessage = nil
        if appendOptimisticUser {
            chat.messages.append(HermexChatMessageDTO(
                role: "user",
                content: text,
                timestamp: Date().timeIntervalSince1970
            ))
        }
        chat.stream = HermexStreamState(isStreaming: true, liveToolActivity: "Starting response")
        let response = try await environment.startChat(
            sessionID,
            text,
            composer.selectedWorkspace,
            composer.selectedModel,
            composer.selectedModelProvider,
            composer.selectedProfile,
            nil
        )
        chat.stream.streamID = response.stringValue(forKey: "stream_id") ?? response.stringValue(forKey: "streamId")
    }

    private func compressCurrentSession() async {
        guard let sessionID = appState.selectedSessionID else { return }
        chat.errorMessage = nil
        do {
            _ = try await environment.compressSession(sessionID, nil)
            await openSession(sessionID)
        } catch {
            chat.errorMessage = String(describing: error)
        }
    }

    private func clearCurrentSession() async {
        guard let sessionID = appState.selectedSessionID else { return }
        guard !chat.isViewingCachedData else {
            chat.errorMessage = "Reconnect to the server to clear this conversation."
            return
        }
        guard !chat.stream.isStreaming else {
            chat.errorMessage = "Wait for the current response to finish before clearing."
            return
        }

        chat.errorMessage = nil
        chat.messages = []
        chat.pendingApproval = nil
        chat.pendingClarification = nil
        chat.stream = HermexStreamState()
        chat.composer.draft = ""
        chat.composer.attachments = []
        do {
            let response = try await environment.clearSession(sessionID)
            if let error = response.stringValue(forKey: "error"), !error.isEmpty {
                chat.errorMessage = error
                await openSession(sessionID)
                return
            }
            await openSession(sessionID)
        } catch {
            chat.errorMessage = String(describing: error)
            await openSession(sessionID)
        }
    }

    private func respondClarification(_ response: String) async {
        guard let sessionID = appState.selectedSessionID else { return }
        do {
            _ = try await environment.respondClarification(sessionID, response, chat.pendingClarification?.promptID)
            chat.pendingClarification = nil
        } catch {
            chat.errorMessage = String(describing: error)
        }
    }

    private func applyStreamEvent(_ event: HermexSSEEvent) {
        let kind = event.kind
        if kind == "token" {
            if let token = event.tokenText {
                appendAssistantToken(token)
            }
            chat.stream.liveToolActivity = nil
            return
        }
        if kind == "usage" {
            chat.stream.liveToolActivity = event.usageText
            return
        }
        if kind == "done" {
            chat.stream.isStreaming = false
            chat.stream.isRecovering = false
            chat.stream.liveToolActivity = nil
            return
        }
        if kind == "error" {
            chat.stream.isStreaming = false
            chat.stream.isRecovering = false
            chat.errorMessage = event.errorText
            return
        }
        if kind == "named" {
            applyNamedStreamEvent(event: event.namedEvent ?? "", data: event.namedData ?? "")
        }
    }

    private func applyNamedStreamEvent(event: String, data: String) {
        if event == "reasoning" || event == "thinking" {
            if chat.stream.liveReasoning.isEmpty {
                chat.stream.liveReasoning = data
            } else {
                chat.stream.liveReasoning += data
            }
            return
        }
        if event == "tool" || event == "tool_call" || event == "tool_status" {
            chat.stream.liveToolActivity = data
            return
        }
        if event == "done" {
            chat.stream.isStreaming = false
            chat.stream.isRecovering = false
            chat.stream.liveToolActivity = nil
            return
        }
        if event == "error" {
            chat.stream.isStreaming = false
            chat.errorMessage = data
            return
        }
        chat.stream.liveToolActivity = data
    }

    private func appendAssistantToken(_ token: String) {
        guard !token.isEmpty else { return }
        if chat.messages.isEmpty {
            chat.messages.append(HermexChatMessageDTO(role: "assistant", content: token, timestamp: Date().timeIntervalSince1970))
            return
        }
        let lastIndex = chat.messages.count - 1
        let lastMessage = chat.messages[lastIndex]
        if lastMessage.role == "assistant" {
            var updated = lastMessage
            updated.content = (lastMessage.content ?? "") + token
            chat.messages[lastIndex] = updated
        } else {
            chat.messages.append(HermexChatMessageDTO(role: "assistant", content: token, timestamp: Date().timeIntervalSince1970))
        }
    }

    private func loadWorkspace(path: String?) async {
        guard let sessionID = appState.selectedSessionID else { return }
        appState.route = .workspace
        workspace.isLoading = true
        workspace.errorMessage = nil
        do {
            let roots = try await environment.loadWorkspaces()
            let targetPath = path ?? workspace.currentPath ?? chat.composer.selectedWorkspace ?? roots.last ?? roots.normalizedRoots.first?.path
            let response = try await environment.loadDirectory(sessionID, targetPath)
            var mapped = HermexWorkspaceState.fromDirectoryResponse(response, fallbackPath: targetPath)
            mapped.roots = roots.normalizedRoots
            workspace = mapped
        } catch {
            workspace.errorMessage = String(describing: error)
        }
        workspace.isLoading = false
    }

    private func loadFilePreview(path: String) async {
        guard let sessionID = appState.selectedSessionID else { return }
        appState.route = .workspace
        workspace.isLoading = true
        workspace.errorMessage = nil
        do {
            let response = try await environment.loadFile(sessionID, path)
            workspace.preview = HermexFilePreview.fromJSON(response, fallbackPath: path)
            workspace.currentPath = path
        } catch {
            workspace.errorMessage = String(describing: error)
        }
        workspace.isLoading = false
    }

    private func loadGitStatus() async {
        guard let sessionID = appState.selectedSessionID else { return }
        appState.route = .git
        git.isMutating = true
        git.errorMessage = nil
        do {
            git = git.mergingStatus(from: try await environment.loadGitStatus(sessionID))
        } catch {
            git.errorMessage = String(describing: error)
        }
        git.isMutating = false
    }

    private func runGitAction(_ action: String) async {
        guard let sessionID = appState.selectedSessionID else { return }
        appState.route = .git
        git.isMutating = true
        git.errorMessage = nil
        do {
            _ = try await environment.performGitAction(sessionID, action)
            git = HermexGitState.fromStatusResponse(try await environment.loadGitStatus(sessionID))
        } catch {
            git.errorMessage = String(describing: error)
        }
        git.isMutating = false
    }

    private func runGitCommand(_ command: HermexGitCommand) async {
        guard let sessionID = appState.selectedSessionID else { return }
        appState.route = .git
        git.isMutating = true
        git.errorMessage = nil
        do {
            let response = try await environment.performGitCommand(sessionID, command)
            switch command {
            case .diff(let path, _):
                git.diffPath = path
                git.diffText = HermexGitState.diffText(from: response) ?? ""
            case .commit:
                git.commitMessage = ""
                git = git.mergingStatus(from: try await environment.loadGitStatus(sessionID))
            default:
                git = git.mergingStatus(from: try await environment.loadGitStatus(sessionID))
            }
        } catch {
            git.errorMessage = String(describing: error)
        }
        git.isMutating = false
    }

    private func loadPanel(_ panel: HermexPanel) async {
        appState.route = .panels
        panels.selectedPanel = panel
        panels.isLoading = true
        panels.errorMessage = nil
        do {
            switch panel {
            case .tasks:
                let mapped = HermexPanelsState.tasks(from: try await environment.loadTasks(), selectedPanel: panel)
                panels.tasks = mapped.tasks
                panels.errorMessage = mapped.errorMessage
            case .skills:
                let mapped = HermexPanelsState.skills(from: try await environment.loadSkills(), selectedPanel: panel)
                panels.skills = mapped.skills
                panels.errorMessage = mapped.errorMessage
            case .memory:
                let mapped = HermexPanelsState.memory(from: try await environment.loadMemory(), selectedPanel: panel)
                panels.memory = mapped.memory
                panels.errorMessage = mapped.errorMessage
            case .insights:
                panels.insightsDays = Self.normalizedInsightsDays(panels.insightsDays)
                panels.insights = try await environment.loadInsights(panels.insightsDays)
            }
        } catch {
            panels.errorMessage = String(describing: error)
        }
        panels.isLoading = false
    }

    private func selectInsightsRange(days: Int) async {
        panels.insightsDays = Self.normalizedInsightsDays(days)
        await loadPanel(.insights)
    }

    private func runTaskCommand(_ command: HermexTaskCommand) async {
        switch command {
        case .run, .pause, .resume:
            await runTaskStatusCommand(command)
        case .create(let draft):
            await saveTaskCommand(.create(draft: draft), draft: draft)
        case .update(let draft):
            await saveTaskCommand(.update(draft: draft), draft: draft)
        case .delete(let jobID):
            await deleteTask(jobID: jobID)
        case .loadOutput(let jobID, let limit):
            await loadTaskOutput(jobID: jobID, limit: limit)
        }
    }

    private func runTaskStatusCommand(_ command: HermexTaskCommand) async {
        let previousTasks = panels.tasks
        panels.errorMessage = nil
        panels.isMutating = true
        applyTaskCommandOptimistically(command)
        do {
            let response = try await environment.performTaskCommand(command)
            if let error = taskCommandError(response) {
                panels.tasks = previousTasks
                panels.errorMessage = error
                panels.isMutating = false
                return
            }
            if panels.selectedPanel == .tasks {
                await loadPanel(.tasks)
            }
        } catch {
            panels.tasks = previousTasks
            panels.errorMessage = String(describing: error)
        }
        panels.isMutating = false
    }

    private func saveTaskCommand(_ command: HermexTaskCommand, draft: HermexTaskDraft) async {
        guard draft.validationMessage == nil else {
            panels.errorMessage = draft.validationMessage
            return
        }
        if case .update = command, draft.editingJobID == nil {
            panels.errorMessage = "Task id unavailable."
            return
        }

        panels.errorMessage = nil
        panels.isMutating = true
        do {
            let response = try await environment.performTaskCommand(command)
            if let error = taskCommandError(response) {
                panels.errorMessage = error
                panels.isMutating = false
                return
            }
            panels.taskDraft = nil
            if panels.selectedPanel == .tasks {
                await loadPanel(.tasks)
            }
        } catch {
            panels.errorMessage = String(describing: error)
        }
        panels.isMutating = false
    }

    private func deleteTask(jobID: String) async {
        let normalizedJobID = jobID.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines)
        guard !normalizedJobID.isEmpty else {
            panels.errorMessage = "Task id unavailable."
            return
        }

        let previousTasks = panels.tasks
        panels.errorMessage = nil
        panels.isMutating = true
        panels.tasks.removeAll { $0.id == normalizedJobID }
        if panels.selectedTaskID == normalizedJobID {
            panels.selectedTaskID = nil
            panels.taskOutput = nil
        }
        do {
            let response = try await environment.performTaskCommand(.delete(jobID: normalizedJobID))
            if let error = taskCommandError(response) {
                panels.tasks = previousTasks
                panels.errorMessage = error
                panels.isMutating = false
                return
            }
            if panels.selectedPanel == .tasks {
                await loadPanel(.tasks)
            }
        } catch {
            panels.tasks = previousTasks
            panels.errorMessage = String(describing: error)
        }
        panels.isMutating = false
    }

    private func loadTaskOutput(jobID: String, limit: Int) async {
        let normalizedJobID = jobID.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines)
        guard !normalizedJobID.isEmpty else { return }
        panels.selectedTaskID = normalizedJobID
        panels.taskOutput = nil
        panels.isLoadingTaskOutput = true
        panels.errorMessage = nil
        do {
            panels.taskOutput = try await environment.performTaskCommand(.loadOutput(jobID: normalizedJobID, limit: max(1, min(50, limit))))
        } catch {
            panels.errorMessage = String(describing: error)
        }
        panels.isLoadingTaskOutput = false
    }

    private func beginTaskEdit(jobID: String) {
        guard let task = panels.tasks.first(where: { $0.id == jobID }) else { return }
        panels.taskDraft = HermexTaskDraft(task: task)
        panels.selectedTaskID = nil
        panels.taskOutput = nil
        panels.errorMessage = nil
    }

    private func confirmTaskDeletion() async {
        guard let jobID = panels.pendingTaskDeletionID else { return }
        panels.pendingTaskDeletionID = nil
        await runTaskCommand(.delete(jobID: jobID))
    }

    private func taskCommandError(_ response: HermexJSONValue) -> String? {
        let fields = response.objectValue ?? [:]
        if fields["ok"]?.boolValue == false {
            return fields["error"]?.stringValue ?? "Task operation failed."
        }
        return fields["error"]?.stringValue
    }

    private func updateTask(jobID: String, status: String) {
        let normalizedJobID = jobID.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines)
        guard !normalizedJobID.isEmpty else { return }
        if let index = panels.tasks.firstIndex(where: { $0.id == normalizedJobID }) {
            panels.tasks[index].status = status
            panels.errorMessage = nil
        }
    }

    private func applyTaskCommandOptimistically(_ command: HermexTaskCommand) {
        switch command {
        case .run(let jobID):
            updateTask(jobID: jobID, status: "Running")
        case .pause(let jobID):
            updateTask(jobID: jobID, status: "Paused")
        case .resume(let jobID):
            updateTask(jobID: jobID, status: "Active")
        case .create, .update, .delete, .loadOutput:
            break
        }
    }

    private func writeMemory(section: String, content: String) async {
        let normalizedSection = section.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines)
        guard !normalizedSection.isEmpty else { return }
        let previousMemory = panels.memory
        panels.errorMessage = nil
        updateMemorySection(section: normalizedSection, content: content)
        do {
            _ = try await environment.writeMemory(normalizedSection, content)
            if panels.selectedPanel == .memory {
                await loadPanel(.memory)
            }
        } catch {
            panels.memory = previousMemory
            panels.errorMessage = String(describing: error)
        }
    }

    private func updateMemorySection(section: String, content: String) {
        let normalizedSection = section.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines)
        guard !normalizedSection.isEmpty else { return }
        if let index = panels.memory.firstIndex(where: { $0.section == normalizedSection }) {
            panels.memory[index].content = content
        } else {
            panels.memory.append(HermexMemorySectionDTO(section: normalizedSection, content: content))
        }
        panels.errorMessage = nil
    }

    private static func normalizedInsightsDays(_ days: Int) -> Int {
        switch days {
        case 1, 7, 30, 365:
            return days
        default:
            return 30
        }
    }

    private func toggleSkill(name: String, enabled: Bool) async {
        let normalizedName = name.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines)
        guard !normalizedName.isEmpty else { return }
        let previousSkills = panels.skills
        panels.errorMessage = nil
        let comparableName = normalizedName.lowercased()
        if let index = panels.skills.firstIndex(where: { $0.name.lowercased() == comparableName }) {
            panels.skills[index].enabled = enabled
        }
        do {
            _ = try await environment.toggleSkill(normalizedName, enabled)
            if panels.selectedPanel == .skills {
                await loadPanel(.skills)
            }
        } catch {
            panels.skills = previousSkills
            panels.errorMessage = String(describing: error)
        }
    }

    private func openSkillDetail(name: String) async {
        let normalizedName = name.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines)
        guard !normalizedName.isEmpty else { return }
        panels.selectedSkillName = normalizedName
        panels.selectedSkillDetail = nil
        panels.selectedSkillFileName = nil
        panels.selectedSkillFileContent = nil
        panels.isLoadingSkillDetail = true
        panels.isLoadingSkillFile = false
        panels.errorMessage = nil

        do {
            let response = try await environment.loadSkillContent(normalizedName, nil)
            panels.selectedSkillDetail = HermexSkillDetailDTO.fromJSON(response)
                ?? HermexSkillDetailDTO(name: normalizedName, error: "Could not decode skill content.")
        } catch {
            panels.selectedSkillDetail = HermexSkillDetailDTO(name: normalizedName, error: String(describing: error))
            panels.errorMessage = String(describing: error)
        }
        panels.isLoadingSkillDetail = false
    }

    private func loadSkillFile(fileName: String) async {
        let normalizedFileName = fileName.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines)
        guard !normalizedFileName.isEmpty,
              let skillName = panels.selectedSkillName,
              !skillName.isEmpty
        else { return }

        panels.selectedSkillFileName = normalizedFileName
        panels.selectedSkillFileContent = nil
        panels.isLoadingSkillFile = true
        panels.errorMessage = nil
        do {
            let response = try await environment.loadSkillContent(skillName, normalizedFileName)
            let detail = HermexSkillDetailDTO.fromJSON(response)
            panels.selectedSkillFileContent = detail?.content ?? detail?.error ?? ""
        } catch {
            panels.selectedSkillFileContent = "Could not load file: \(String(describing: error))"
        }
        panels.isLoadingSkillFile = false
    }

    private func clearSkillDetail() {
        panels.selectedSkillName = nil
        panels.selectedSkillDetail = nil
        panels.selectedSkillFileName = nil
        panels.selectedSkillFileContent = nil
        panels.isLoadingSkillDetail = false
        panels.isLoadingSkillFile = false
    }

    private func signOut() async {
        do {
            _ = try await environment.logout()
        } catch {
            settings.notificationsEnabled = false
        }

        if let server = settings.activeServer {
            await environment.updateServerRuntime(server, false)
        }

        appState.selectedSessionID = nil
        if let server = settings.activeServer {
            appState.auth = .loggedOut(server: server)
            onboarding.serverURLString = server.baseURL.absoluteString
            onboarding.displayName = server.displayName
        } else {
            appState.auth = .unconfigured
        }
        appState.route = .onboarding
        onboarding.password = ""
        chat = HermexChatState()
        sessions = HermexSessionListState()
    }

    private func onboardingServerIdentity() -> HermexServerIdentity? {
        let trimmedURL = onboarding.serverURLString.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines)
        guard !trimmedURL.isEmpty else {
            onboarding.errorMessage = "Server URL is required."
            return nil
        }

        let candidate = trimmedURL.contains("://") ? trimmedURL : "https://\(trimmedURL)"
        guard let rawURL = URL(string: candidate),
              let scheme = rawURL.scheme?.lowercased(),
              ["http", "https"].contains(scheme),
              rawURL.host?.isEmpty == false
        else {
            onboarding.errorMessage = "Enter a valid HTTP or HTTPS server URL."
            return nil
        }

        let normalizedURL = URL(string: HermexServerURLNormalizer.normalizedID(for: rawURL)) ?? rawURL
        let displayName = onboarding.displayName.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines)
        let headers = parsedCustomHeaders()
        var customHeaders: [String: String] = [:]
        for header in headers {
            customHeaders[header.sanitizedName] = header.sanitizedValue
        }
        return HermexServerIdentity(
            baseURL: normalizedURL,
            displayName: displayName.isEmpty ? (normalizedURL.host ?? normalizedURL.absoluteString) : displayName,
            customHeaders: customHeaders
        )
    }

    private func parsedCustomHeaders() -> [HermexCustomHeader] {
        var headers: [HermexCustomHeader] = []
        for rawLine in onboarding.customHeaderText.split(separator: "\n") {
            let pieces = rawLine.split(separator: ":", maxSplits: 1, omittingEmptySubsequences: false)
            guard pieces.count == 2 else { continue }
            headers.append(HermexCustomHeader(name: String(pieces[0]), value: String(pieces[1])))
        }
        return headers.filter { $0.isSafeForClient }
    }

    private func upsertServer(_ server: HermexServerIdentity) {
        settings.activeServer = server
        if let existingIndex = settings.servers.firstIndex(where: {
            HermexServerURLNormalizer.normalizedID(for: $0.baseURL) == HermexServerURLNormalizer.normalizedID(for: server.baseURL)
        }) {
            settings.servers[existingIndex] = server
        } else {
            settings.servers.append(server)
        }
    }
}

private extension HermexAttachmentDTO {
    var jsonValue: HermexJSONValue {
        var fields: [String: HermexJSONValue] = [:]
        if let name = name { fields["name"] = .string(name) }
        if let path = path { fields["path"] = .string(path) }
        if let mime = mime { fields["mime"] = .string(mime) }
        if let size = size { fields["size"] = .number(Double(size)) }
        if let isImage = isImage { fields["is_image"] = .bool(isImage) }
        return .dictionary(fields)
    }
}

private extension HermexJSONValue {
    func stringValue(forKey key: String) -> String? {
        objectValue?[key]?.stringValue
    }
}
#endif
