import XCTest
@testable import HermexCore

@MainActor
final class HermexAppStoreTests: XCTestCase {
    func testRefreshLoadsSessionsIntoSharedState() async throws {
        let probe = StoreProbe()
        let store = HermexAppStore(appState: HermexAppState(route: .sessions), environment: environment(probe: probe))

        await store.send(.refresh)

        XCTAssertEqual(store.sessions.sessions.map(\.id), ["s1"])
        XCTAssertFalse(store.sessions.isLoading)
        XCTAssertNil(store.sessions.errorMessage)
    }

    func testOpenSessionLoadsTranscriptAndMovesToChatRoute() async throws {
        let probe = StoreProbe()
        let store = HermexAppStore(environment: environment(probe: probe))

        await store.send(.openSession("s1"))

        XCTAssertEqual(store.appState.route, .chat)
        XCTAssertEqual(store.appState.selectedSessionID, "s1")
        XCTAssertEqual(store.chat.session?.title, "Workspace")
        XCTAssertEqual(store.chat.messages.first?.content, "Hello")
        XCTAssertFalse(store.chat.isLoading)
    }

    func testSendDraftStartsChatAndUpdatesStreamState() async throws {
        let probe = StoreProbe()
        let store = HermexAppStore(
            appState: HermexAppState(selectedSessionID: "s1", route: .chat),
            chat: HermexChatState(composer: HermexComposerState(
                draft: "Build it",
                selectedModel: "gpt-5.5",
                selectedModelProvider: "codex",
                selectedWorkspace: "Home",
                selectedProfile: "default"
            )),
            environment: environment(probe: probe)
        )

        await store.send(.sendDraft)

        let request = await probe.startedChat
        XCTAssertEqual(request?.sessionID, "s1")
        XCTAssertEqual(request?.message, "Build it")
        XCTAssertEqual(request?.workspace, "Home")
        XCTAssertEqual(request?.model, "gpt-5.5")
        XCTAssertEqual(request?.modelProvider, "codex")
        XCTAssertEqual(request?.profile, "default")
        XCTAssertEqual(store.chat.composer.draft, "")
        XCTAssertEqual(store.chat.messages.last?.role, "user")
        XCTAssertEqual(store.chat.stream.streamID, "stream-1")
        XCTAssertTrue(store.chat.stream.isStreaming)
    }

    func testCancelStreamRoutesThroughEnvironment() async throws {
        let probe = StoreProbe()
        let store = HermexAppStore(
            chat: HermexChatState(stream: HermexStreamState(streamID: "stream-1", isStreaming: true)),
            environment: environment(probe: probe)
        )

        await store.send(.cancelStream)

        let cancelledStreamID = await probe.cancelledStreamID
        XCTAssertEqual(cancelledStreamID, "stream-1")
        XCTAssertFalse(store.chat.stream.isStreaming)
    }

    func testApplyStreamEventsUpdatesTranscriptAndStreamState() async throws {
        let probe = StoreProbe()
        let store = HermexAppStore(
            chat: HermexChatState(stream: HermexStreamState(streamID: "stream-1", isStreaming: true)),
            environment: environment(probe: probe)
        )

        await store.send(.applyStreamEvent(.token("Hel")))
        await store.send(.applyStreamEvent(.token("lo")))
        await store.send(.applyStreamEvent(.named(event: "reasoning", data: "thinking")))
        await store.send(.applyStreamEvent(.named(event: "tool", data: "git/status")))
        await store.send(.applyStreamEvent(.done(nil)))

        XCTAssertEqual(store.chat.messages.last?.role, "assistant")
        XCTAssertEqual(store.chat.messages.last?.content, "Hello")
        XCTAssertEqual(store.chat.stream.liveReasoning, "thinking")
        XCTAssertFalse(store.chat.stream.isStreaming)
        XCTAssertNil(store.chat.stream.liveToolActivity)
    }

    func testUndoRetryAndCompressMutateSelectedSessionAndReloadTranscript() async throws {
        let probe = StoreProbe()
        let store = HermexAppStore(
            appState: HermexAppState(selectedSessionID: "s1", route: .chat),
            environment: environment(probe: probe)
        )

        await store.send(.undo)
        await store.send(.retry)
        await store.send(.compress)
        let mutations = await probe.mutations
        let loadedSessionIDs = await probe.loadedSessionIDs

        XCTAssertEqual(mutations, ["undo:s1", "retry:s1", "compress:s1"])
        XCTAssertEqual(loadedSessionIDs, ["s1", "s1", "s1"])
        XCTAssertEqual(store.chat.messages.first?.content, "Hello")
    }

    func testComposerConfigurationLoadsAndSelectionsUpdateDraftContext() async throws {
        let probe = StoreProbe()
        let store = HermexAppStore(
            chat: HermexChatState(session: HermexSessionDTO(sessionId: "s1", workspace: "Repo", model: "cached-model")),
            environment: environment(probe: probe)
        )

        await store.send(.refreshComposerConfiguration)

        XCTAssertEqual(store.chat.composer.availableModels.map(\.id), ["gpt-5.5"])
        XCTAssertEqual(store.chat.composer.availableProfiles.map(\.name), ["default"])
        XCTAssertEqual(store.chat.composer.availableWorkspaces.map(\.path), ["/repo"])
        XCTAssertEqual(store.chat.composer.selectedModel, "gpt-5.5")
        XCTAssertEqual(store.chat.composer.selectedModelProvider, "codex")
        XCTAssertEqual(store.chat.composer.selectedProfile, "default")
        XCTAssertEqual(store.chat.composer.selectedWorkspace, "Repo")
        XCTAssertEqual(store.chat.composer.supportedReasoningEfforts, ["low", "medium", "high"])
        XCTAssertEqual(store.chat.composer.selectedReasoningEffort, "medium")

        await store.send(.selectModel(HermexModelOption(id: "fast", provider: "local", label: "Fast")))
        await store.send(.selectWorkspace(HermexWorkspaceRootDTO(path: "/tmp", name: "Temp")))
        await store.send(.selectProfile(HermexProfileOption(name: "ops", displayName: "Ops")))
        await store.send(.selectReasoningEffort("high"))

        XCTAssertEqual(store.chat.composer.selectedModel, "fast")
        XCTAssertEqual(store.chat.composer.selectedModelProvider, "local")
        XCTAssertEqual(store.chat.composer.selectedWorkspace, "/tmp")
        XCTAssertEqual(store.chat.composer.selectedProfile, "ops")
        XCTAssertEqual(store.chat.composer.selectedReasoningEffort, "high")
        let savedReasoningEffort = await probe.savedReasoningEffort
        XCTAssertEqual(savedReasoningEffort, "high")
    }

    func testOnboardingConnectionAndLoginNormalizeServerWithoutStoringPassword() async throws {
        let probe = StoreProbe()
        let store = HermexAppStore(
            onboarding: HermexOnboardingState(
                serverURLString: "Example.TEST/",
                displayName: "Example",
                password: "secret",
                customHeaderText: "Origin: bad\nCF-Access-Client-Id: abc\nCF-Access-Client-Secret: xyz"
            ),
            environment: environment(probe: probe)
        )

        await store.send(.testOnboardingConnection)
        let maybeTestedServer = await probe.testedServer
        let testedServer = try XCTUnwrap(maybeTestedServer)
        XCTAssertEqual(testedServer.baseURL.absoluteString, "https://example.test/")
        XCTAssertEqual(store.appState.auth, .loggedOut(server: testedServer))
        XCTAssertEqual(store.settings.servers.first?.customHeaders["CF-Access-Client-Id"], "abc")
        XCTAssertNil(store.settings.servers.first?.customHeaders["Origin"])

        await store.send(.connectOnboarding)

        let loginPassword = await probe.loginPassword
        XCTAssertEqual(loginPassword, "secret")
        XCTAssertEqual(store.onboarding.password, "")
        XCTAssertEqual(store.appState.route, .sessions)
        XCTAssertEqual(store.sessions.sessions.map(\.id), ["s1"])
        XCTAssertEqual(store.settings.activeServer?.displayName, "Example")
    }

    func testWorkspaceGitAndPanelsLoadThroughSharedEnvironment() async throws {
        let probe = StoreProbe()
        let store = HermexAppStore(
            appState: HermexAppState(selectedSessionID: "s1", route: .workspace),
            environment: environment(probe: probe)
        )

        await store.send(.refresh)
        XCTAssertEqual(store.workspace.entries.map(\.path), ["/repo/README.md"])

        await store.send(.openWorkspaceEntry(HermexWorkspaceEntryDTO(name: "README.md", path: "/repo/README.md", isDirectory: false)))
        XCTAssertEqual(store.workspace.preview?.content, "Hello")

        await store.send(.openRoute(.git))
        await store.send(.refresh)
        XCTAssertEqual(store.git.branch, "main")
        XCTAssertEqual(store.git.files.map(\.path), ["README.md"])

        await store.send(.gitAction("fetch"))
        let gitActions = await probe.gitActions
        XCTAssertEqual(gitActions, ["fetch"])

        await store.send(.gitCommand(.diff(path: "README.md", kind: "unstaged")))
        XCTAssertEqual(store.git.diffPath, "README.md")
        XCTAssertEqual(store.git.diffText, "diff --git README.md")
        await store.send(.gitCommand(.stage(path: "README.md")))
        await store.send(.updateGitCommitMessage("Update README"))
        await store.send(.gitCommand(.commit(message: store.git.commitMessage)))
        let gitCommands = await probe.gitCommands
        XCTAssertEqual(store.git.commitMessage, "")
        XCTAssertEqual(gitCommands, ["diff", "stage", "commit"])

        await store.send(.selectPanel(.tasks))
        XCTAssertEqual(store.panels.tasks.map(\.id), ["job-1"])
        await store.send(.selectPanel(.skills))
        XCTAssertEqual(store.panels.skills.map(\.name), ["swift"])
        await store.send(.selectPanel(.memory))
        XCTAssertEqual(store.panels.memory.map(\.section), ["memory", "user", "soul", "project_context"])
        await store.send(.selectPanel(.insights))
        XCTAssertEqual(store.panels.insightsDays, 30)
        if case .dictionary(let fields) = store.panels.insights {
            XCTAssertEqual(fields["period_days"], .number(30))
        } else {
            XCTFail("Expected insights dictionary")
        }
    }

    func testToggleSkillRoutesThroughEnvironmentAndRefreshesPanelState() async throws {
        let probe = StoreProbe()
        let store = HermexAppStore(
            panels: HermexPanelsState(
                skills: [HermexSkillDTO(name: "swift", enabled: true, summary: "Swift helper")],
                selectedPanel: .skills
            ),
            environment: environment(probe: probe)
        )

        await store.send(.toggleSkill(name: "swift", enabled: false))

        let toggles = await probe.skillToggles
        XCTAssertEqual(toggles.map(\.name), ["swift"])
        XCTAssertEqual(toggles.map(\.enabled), [false])
        XCTAssertEqual(store.panels.skills.first?.enabled, false)
        XCTAssertNil(store.panels.errorMessage)
    }

    func testTaskCommandsRouteThroughEnvironmentAndRefreshPanelState() async throws {
        let probe = StoreProbe()
        let store = HermexAppStore(
            panels: HermexPanelsState(
                tasks: [HermexTaskDTO(id: "job-1", title: "Morning", status: "active")],
                selectedPanel: .tasks
            ),
            environment: environment(probe: probe)
        )

        await store.send(.taskCommand(.run(jobID: "job-1")))
        XCTAssertEqual(store.panels.tasks.first?.status, "running")

        await store.send(.taskCommand(.pause(jobID: "job-1")))
        XCTAssertEqual(store.panels.tasks.first?.status, "paused")

        await store.send(.taskCommand(.resume(jobID: "job-1")))
        XCTAssertEqual(store.panels.tasks.first?.status, "active")

        let commands = await probe.taskCommands
        XCTAssertEqual(commands, [.run(jobID: "job-1"), .pause(jobID: "job-1"), .resume(jobID: "job-1")])
        XCTAssertNil(store.panels.errorMessage)
    }

    func testTaskEditorDetailsAndMutationCommandsRouteThroughEnvironment() async throws {
        let probe = StoreProbe()
        let store = HermexAppStore(
            panels: HermexPanelsState(
                tasks: [HermexTaskDTO(
                    id: "job-1",
                    title: "Morning",
                    status: "active",
                    schedule: "0 9 * * 1",
                    prompt: "Prepare the digest",
                    deliver: "local",
                    skills: ["research"],
                    model: "gpt-5.5",
                    profile: "default",
                    toastNotifications: true
                )],
                selectedPanel: .tasks
            ),
            environment: environment(probe: probe)
        )

        await store.send(.beginTaskEdit(jobID: "job-1"))
        XCTAssertEqual(store.panels.taskDraft?.editingJobID, "job-1")
        XCTAssertEqual(store.panels.taskDraft?.prompt, "Prepare the digest")

        let updatedDraft = HermexTaskDraft(
            editingJobID: "job-1",
            name: "Morning digest",
            prompt: "Prepare the updated digest",
            schedule: "0 10 * * 1",
            deliver: "local",
            skillsText: "research",
            model: "gpt-5.5",
            profile: "default",
            toastNotifications: false
        )
        await store.send(.updateTaskDraft(updatedDraft))
        await store.send(.taskCommand(.update(draft: updatedDraft)))
        XCTAssertNil(store.panels.taskDraft)

        await store.send(.taskCommand(.loadOutput(jobID: "job-1", limit: 5)))
        XCTAssertEqual(store.panels.selectedTaskID, "job-1")
        XCTAssertEqual(store.panels.taskOutput?.objectValue?["job_id"], .string("job-1"))

        await store.send(.beginTaskCreation)
        XCTAssertEqual(store.panels.taskDraft?.deliver, "local")
        let newDraft = HermexTaskDraft(prompt: "Create a new digest", schedule: "manual")
        await store.send(.taskCommand(.create(draft: newDraft)))
        XCTAssertNil(store.panels.taskDraft)

        await store.send(.requestTaskDeletion(jobID: "job-1"))
        XCTAssertEqual(store.panels.pendingTaskDeletionID, "job-1")
        await store.send(.confirmTaskDeletion)
        XCTAssertNil(store.panels.pendingTaskDeletionID)

        let commands = await probe.taskCommands
        XCTAssertTrue(commands.contains { command in
            if case .update = command { return true }
            return false
        })
        XCTAssertTrue(commands.contains { command in
            if case .loadOutput = command { return true }
            return false
        })
        XCTAssertTrue(commands.contains { command in
            if case .create = command { return true }
            return false
        })
        XCTAssertTrue(commands.contains { command in
            if case .delete = command { return true }
            return false
        })
    }

    func testWriteMemoryRoutesThroughEnvironmentAndRefreshesPanelState() async throws {
        let probe = StoreProbe()
        let store = HermexAppStore(
            panels: HermexPanelsState(
                memory: [HermexMemorySectionDTO(section: "memory", content: "Initial notes")],
                selectedPanel: .memory
            ),
            environment: environment(probe: probe)
        )

        await store.send(.writeMemory(section: "memory", content: "Updated notes"))

        let writes = await probe.memoryWrites
        XCTAssertEqual(writes.map(\.section), ["memory"])
        XCTAssertEqual(writes.map(\.content), ["Updated notes"])
        XCTAssertEqual(store.panels.memory.first(where: { $0.section == "memory" })?.content, "Updated notes")
        XCTAssertNil(store.panels.errorMessage)
    }

    func testSelectInsightsRangeRoutesThroughEnvironmentAndRefreshesPanelState() async throws {
        let probe = StoreProbe()
        let store = HermexAppStore(
            panels: HermexPanelsState(
                insightsDays: 30,
                selectedPanel: .insights
            ),
            environment: environment(probe: probe)
        )

        await store.send(.selectInsightsRange(days: 365))

        XCTAssertEqual(store.panels.selectedPanel, .insights)
        XCTAssertEqual(store.panels.insightsDays, 365)
        if case .dictionary(let fields) = store.panels.insights {
            XCTAssertEqual(fields["period_days"], .number(365))
            XCTAssertEqual(fields["total_sessions"], .number(3650))
        } else {
            XCTFail("Expected insights dictionary")
        }

        await store.send(.refresh)
        let loadedDays = await probe.loadedInsightsDays
        XCTAssertEqual(loadedDays, [365, 365])
        XCTAssertNil(store.panels.errorMessage)
    }

    private func environment(probe: StoreProbe) -> HermexAppEnvironment {
        HermexAppEnvironment(
            testServerConnection: { server in
                try await probe.testServerConnection(server: server)
            },
            loginToServer: { server, password in
                try await probe.loginToServer(server: server, password: password)
            },
            loadSessions: { includeArchived, archivedLimit in
                try await probe.loadSessions(includeArchived: includeArchived, archivedLimit: archivedLimit)
            },
            loadSession: { sessionID in
                try await probe.loadSession(sessionID: sessionID)
            },
            startChat: { sessionID, message, workspace, model, modelProvider, profile, attachments in
                try await probe.startChat(
                    sessionID: sessionID,
                    message: message,
                    workspace: workspace,
                    model: model,
                    modelProvider: modelProvider,
                    profile: profile,
                    attachments: attachments
                )
            },
            cancelStream: { streamID in
                try await probe.cancelStream(streamID: streamID)
            },
            respondApproval: { sessionID, choice, approvalID in
                try await probe.respondApproval(sessionID: sessionID, choice: choice, approvalID: approvalID)
            },
            respondClarification: { sessionID, response, clarifyID in
                try await probe.respondClarification(sessionID: sessionID, response: response, clarifyID: clarifyID)
            },
            undoSession: { sessionID in
                try await probe.undoSession(sessionID: sessionID)
            },
            retrySession: { sessionID in
                try await probe.retrySession(sessionID: sessionID)
            },
            compressSession: { sessionID, focusTopic in
                try await probe.compressSession(sessionID: sessionID, focusTopic: focusTopic)
            },
            loadModels: {
                try await probe.loadModels()
            },
            loadProfiles: {
                try await probe.loadProfiles()
            },
            loadWorkspaces: {
                try await probe.loadWorkspaces()
            },
            loadReasoning: { model, provider in
                try await probe.loadReasoning(model: model, provider: provider)
            },
            saveReasoningEffort: { effort, model, provider in
                try await probe.saveReasoningEffort(effort: effort, model: model, provider: provider)
            },
            loadDirectory: { sessionID, path in
                try await probe.loadDirectory(sessionID: sessionID, path: path)
            },
            loadFile: { sessionID, path in
                try await probe.loadFile(sessionID: sessionID, path: path)
            },
            loadGitStatus: { sessionID in
                try await probe.loadGitStatus(sessionID: sessionID)
            },
            performGitAction: { sessionID, action in
                try await probe.performGitAction(sessionID: sessionID, action: action)
            },
            performGitCommand: { sessionID, command in
                try await probe.performGitCommand(sessionID: sessionID, command: command)
            },
            loadTasks: {
                try await probe.loadTasks()
            },
            performTaskCommand: { command in
                try await probe.performTaskCommand(command)
            },
            loadSkills: {
                try await probe.loadSkills()
            },
            toggleSkill: { name, enabled in
                try await probe.toggleSkill(name: name, enabled: enabled)
            },
            loadMemory: {
                try await probe.loadMemory()
            },
            writeMemory: { section, content in
                try await probe.writeMemory(section: section, content: content)
            },
            loadInsights: { days in
                try await probe.loadInsights(days: days)
            },
            logout: {
                try await probe.logout()
            }
        )
    }
}

private actor StoreProbe {
    struct StartedChat: Equatable {
        var sessionID: String?
        var message: String
        var workspace: String?
        var model: String?
        var modelProvider: String?
        var profile: String?
    }

    struct SkillToggle: Equatable {
        var name: String
        var enabled: Bool
    }

    struct MemoryWrite: Equatable {
        var section: String
        var content: String
    }

    private(set) var startedChat: StartedChat?
    private(set) var cancelledStreamID: String?
    private(set) var loadedSessionIDs: [String] = []
    private(set) var mutations: [String] = []
    private(set) var savedReasoningEffort: String?
    private(set) var gitActions: [String] = []
    private(set) var gitCommands: [String] = []
    private(set) var taskCommands: [HermexTaskCommand] = []
    private(set) var skillToggles: [SkillToggle] = []
    private(set) var memoryWrites: [MemoryWrite] = []
    private(set) var loadedInsightsDays: [Int] = []
    private(set) var testedServer: HermexServerIdentity?
    private(set) var loginServer: HermexServerIdentity?
    private(set) var loginPassword: String?
    private var taskStatus = "active"
    private var swiftSkillEnabled = true
    private var memoryValues = [
        "memory": "Project notes",
        "user": "Likes concise answers",
        "soul": "Helpful",
        "project_context": "Read-only project context"
    ]

    func testServerConnection(server: HermexServerIdentity) async throws -> HermexJSONValue {
        testedServer = server
        return .dictionary(["ok": .bool(true)])
    }

    func loginToServer(server: HermexServerIdentity, password: String) async throws -> HermexJSONValue {
        loginServer = server
        loginPassword = password
        return .dictionary(["ok": .bool(true)])
    }

    func loadSessions(includeArchived: Bool, archivedLimit: Int?) async throws -> HermexSessionsResponse {
        HermexSessionsResponse(sessions: [
            HermexSessionDTO(sessionId: "s1", title: "Workspace", messageCount: 1, workspace: "Home")
        ])
    }

    func loadSession(sessionID: String) async throws -> HermexSessionResponse {
        loadedSessionIDs.append(sessionID)
        return HermexSessionResponse(
            session: HermexSessionDTO(sessionId: sessionID, title: "Workspace", messageCount: 1, workspace: "Home"),
            messages: [HermexChatMessageDTO(role: "assistant", content: "Hello")]
        )
    }

    func startChat(
        sessionID: String?,
        message: String,
        workspace: String?,
        model: String?,
        modelProvider: String?,
        profile: String?,
        attachments: [HermexJSONValue]?
    ) async throws -> HermexJSONValue {
        startedChat = StartedChat(
            sessionID: sessionID,
            message: message,
            workspace: workspace,
            model: model,
            modelProvider: modelProvider,
            profile: profile
        )
        return .dictionary([
            "session_id": .string(sessionID ?? "s1"),
            "stream_id": .string("stream-1")
        ])
    }

    func cancelStream(streamID: String) async throws -> HermexJSONValue {
        cancelledStreamID = streamID
        return .dictionary(["ok": .bool(true)])
    }

    func respondApproval(sessionID: String, choice: String, approvalID: String?) async throws -> HermexJSONValue {
        .dictionary(["ok": .bool(true)])
    }

    func respondClarification(sessionID: String, response: String, clarifyID: String?) async throws -> HermexJSONValue {
        .dictionary(["ok": .bool(true)])
    }

    func undoSession(sessionID: String) async throws -> HermexJSONValue {
        mutations.append("undo:\(sessionID)")
        return .dictionary(["ok": .bool(true)])
    }

    func retrySession(sessionID: String) async throws -> HermexJSONValue {
        mutations.append("retry:\(sessionID)")
        return .dictionary(["ok": .bool(true)])
    }

    func compressSession(sessionID: String, focusTopic: String?) async throws -> HermexJSONValue {
        mutations.append("compress:\(sessionID)")
        return .dictionary(["ok": .bool(true)])
    }

    func loadModels() async throws -> HermexModelsResponse {
        HermexModelsResponse(
            groups: nil,
            models: [
                .dictionary([
                    "id": .string("gpt-5.5"),
                    "provider": .string("codex"),
                    "label": .string("GPT 5.5")
                ])
            ],
            defaultModel: "gpt-5.5",
            activeProvider: "codex"
        )
    }

    func loadProfiles() async throws -> HermexProfilesResponse {
        HermexProfilesResponse(
            profiles: [HermexProfileOption(name: "default", displayName: "Default", isActive: true)],
            active: "default"
        )
    }

    func loadWorkspaces() async throws -> HermexWorkspacesResponse {
        HermexWorkspacesResponse(workspaces: [HermexWorkspaceRootDTO(path: "/repo", name: "Repo")], last: "Repo")
    }

    func loadReasoning(model: String?, provider: String?) async throws -> HermexReasoningResponse {
        HermexReasoningResponse(effort: "medium", supportedEfforts: ["low", "medium", "high"], supportsReasoningEffort: true)
    }

    func saveReasoningEffort(effort: String, model: String?, provider: String?) async throws -> HermexJSONValue {
        savedReasoningEffort = effort
        return .dictionary(["ok": .bool(true)])
    }

    func loadDirectory(sessionID: String, path: String?) async throws -> HermexJSONValue {
        .dictionary([
            "path": .string(path ?? "/repo"),
            "entries": .array([
                .dictionary([
                    "name": .string("README.md"),
                    "path": .string("/repo/README.md"),
                    "type": .string("file"),
                    "size": .number(42)
                ])
            ])
        ])
    }

    func loadFile(sessionID: String, path: String) async throws -> HermexJSONValue {
        .dictionary(["path": .string(path), "content": .string("Hello"), "mime_type": .string("text/markdown")])
    }

    func loadGitStatus(sessionID: String) async throws -> HermexJSONValue {
        .dictionary([
            "branch": .string("main"),
            "ahead": .number(1),
            "behind": .number(0),
            "files": .array([
                .dictionary(["path": .string("README.md"), "status": .string("M"), "additions": .number(2), "deletions": .number(1)])
            ])
        ])
    }

    func performGitAction(sessionID: String, action: String) async throws -> HermexJSONValue {
        gitActions.append(action)
        return .dictionary(["ok": .bool(true)])
    }

    func performGitCommand(sessionID: String, command: HermexGitCommand) async throws -> HermexJSONValue {
        switch command {
        case .diff:
            gitCommands.append("diff")
            return .dictionary(["diff": .string("diff --git README.md")])
        case .stage:
            gitCommands.append("stage")
        case .unstage:
            gitCommands.append("unstage")
        case .discard:
            gitCommands.append("discard")
        case .commit:
            gitCommands.append("commit")
        case .fetch:
            gitCommands.append("fetch")
        case .pull:
            gitCommands.append("pull")
        case .push:
            gitCommands.append("push")
        }
        return .dictionary(["ok": .bool(true)])
    }

    func loadTasks() async throws -> HermexJSONValue {
        .dictionary(["jobs": .array([.dictionary(["id": .string("job-1"), "title": .string("Morning"), "status": .string(taskStatus)])])])
    }

    func performTaskCommand(_ command: HermexTaskCommand) async throws -> HermexJSONValue {
        taskCommands.append(command)
        switch command {
        case .run(let jobID):
            taskStatus = "running"
            return .dictionary(["ok": .bool(true), "job_id": .string(jobID), "status": .string(taskStatus)])
        case .pause(let jobID):
            taskStatus = "paused"
            return .dictionary(["ok": .bool(true), "job": .dictionary(["id": .string(jobID), "status": .string(taskStatus)])])
        case .resume(let jobID):
            taskStatus = "active"
            return .dictionary(["ok": .bool(true), "job": .dictionary(["id": .string(jobID), "status": .string(taskStatus)])])
        case .create(let draft):
            return .dictionary([
                "ok": .bool(true),
                "job": .dictionary([
                    "id": .string("created-job"),
                    "name": .string(draft.trimmedName ?? "Created task"),
                    "prompt": .string(draft.trimmedPrompt),
                    "schedule": .string(draft.trimmedSchedule),
                    "state": .string("active")
                ])
            ])
        case .update(let draft):
            return .dictionary([
                "ok": .bool(true),
                "job": .dictionary([
                    "id": .string(draft.editingJobID ?? "job-1"),
                    "name": .string(draft.trimmedName ?? "Updated task"),
                    "prompt": .string(draft.trimmedPrompt),
                    "schedule": .string(draft.trimmedSchedule),
                    "state": .string("active")
                ])
            ])
        case .delete(let jobID):
            return .dictionary(["ok": .bool(true), "job_id": .string(jobID)])
        case .loadOutput(let jobID, _):
            return .dictionary([
                "job_id": .string(jobID),
                "outputs": .array([
                    .dictionary(["filename": .string("latest-run.md"), "content": .string("output")])
                ])
            ])
        }
    }

    func loadSkills() async throws -> HermexJSONValue {
        .dictionary(["skills": .array([.dictionary(["name": .string("swift"), "enabled": .bool(swiftSkillEnabled), "summary": .string("Swift helper")])])])
    }

    func toggleSkill(name: String, enabled: Bool) async throws -> HermexJSONValue {
        skillToggles.append(SkillToggle(name: name, enabled: enabled))
        swiftSkillEnabled = enabled
        return .dictionary(["ok": .bool(true), "name": .string(name), "enabled": .bool(enabled)])
    }

    func loadMemory() async throws -> HermexJSONValue {
        .dictionary([
            "memory": .string(memoryValues["memory"] ?? ""),
            "user": .string(memoryValues["user"] ?? ""),
            "soul": .string(memoryValues["soul"] ?? ""),
            "project_context": .string(memoryValues["project_context"] ?? ""),
            "memory_path": .string("/tmp/MEMORY.md")
        ])
    }

    func writeMemory(section: String, content: String) async throws -> HermexJSONValue {
        memoryWrites.append(MemoryWrite(section: section, content: content))
        memoryValues[section] = content
        return .dictionary(["ok": .bool(true), "section": .string(section), "path": .string("/tmp/\(section).md")])
    }

    func loadInsights(days: Int) async throws -> HermexJSONValue {
        loadedInsightsDays.append(days)
        return .dictionary([
            "period_days": .number(Double(days)),
            "total_sessions": .number(Double(days * 10)),
            "total_messages": .number(Double(days * 20)),
            "total_input_tokens": .number(Double(days * 100)),
            "total_output_tokens": .number(Double(days * 50)),
            "total_tokens": .number(Double(days * 150)),
            "total_cost": .number(Double(days) / 10.0),
            "total_cache_hit_percent": .number(75),
            "total_cache_read_tokens": .number(Double(days * 25)),
            "models": .array([
                .dictionary([
                    "model": .string("gpt-5.5"),
                    "sessions": .number(Double(days)),
                    "total_tokens": .number(Double(days * 150)),
                    "token_share": .number(100),
                    "cache_hit_percent": .number(75),
                    "cost": .number(Double(days) / 10.0)
                ])
            ])
        ])
    }

    func logout() async throws -> HermexJSONValue {
        .dictionary(["ok": .bool(true)])
    }
}
