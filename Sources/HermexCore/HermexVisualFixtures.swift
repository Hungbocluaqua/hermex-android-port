import Foundation

public enum HermexVisualFixtureOverlay: String, Equatable, Sendable {
    case none
    case slashMenu
    case attachmentPicker
}

public struct HermexVisualFixture: Equatable, Sendable {
    public var screenName: String
    public var appState: HermexAppState
    public var onboarding: HermexOnboardingState
    public var sessions: HermexSessionListState
    public var chat: HermexChatState
    public var settings: HermexSettingsState
    public var workspace: HermexWorkspaceState
    public var git: HermexGitState
    public var panels: HermexPanelsState
    public var prefersKeyboardVisible: Bool
    public var overlay: HermexVisualFixtureOverlay

    public init(
        screenName: String,
        appState: HermexAppState,
        onboarding: HermexOnboardingState = HermexOnboardingState(),
        sessions: HermexSessionListState = HermexSessionListState(),
        chat: HermexChatState = HermexChatState(),
        settings: HermexSettingsState = HermexSettingsState(),
        workspace: HermexWorkspaceState = HermexWorkspaceState(),
        git: HermexGitState = HermexGitState(),
        panels: HermexPanelsState = HermexPanelsState(),
        prefersKeyboardVisible: Bool = false,
        overlay: HermexVisualFixtureOverlay = .none
    ) {
        self.screenName = screenName
        self.appState = appState
        self.onboarding = onboarding
        self.sessions = sessions
        self.chat = chat
        self.settings = settings
        self.workspace = workspace
        self.git = git
        self.panels = panels
        self.prefersKeyboardVisible = prefersKeyboardVisible
        self.overlay = overlay
    }
}

public enum HermexVisualFixtureCatalog {
    public static let screenNames: [String] = [
        "onboarding-welcome",
        "onboarding-connect",
        "session-list",
        "session-list-search",
        "chat-empty",
        "chat-populated",
        "chat-streaming",
        "chat-keyboard-open",
        "chat-slash-menu",
        "chat-attachments",
        "chat-approval",
        "settings",
        "workspace-files",
        "git-status",
        "panels-tasks",
        "panels-skills",
        "panels-memory",
        "panels-insights",
    ]

    public static var all: [HermexVisualFixture] {
        screenNames.compactMap { fixture(named: $0) }
    }

    public static func fixture(named screenName: String) -> HermexVisualFixture? {
        switch screenName {
        case "onboarding-welcome":
            return onboardingWelcomeFixture()
        case "onboarding-connect":
            return onboardingConnectFixture()
        case "session-list":
            return routeFixture(screenName, route: .sessions)
        case "session-list-search":
            var fixture = routeFixture(screenName, route: .sessions)
            fixture.sessions.searchQuery = "workspace"
            return fixture
        case "chat-empty":
            var fixture = routeFixture(screenName, route: .chat)
            fixture.appState.selectedSessionID = "empty-chat"
            fixture.chat = emptyChatState()
            return fixture
        case "chat-populated":
            return routeFixture(screenName, route: .chat)
        case "chat-streaming":
            var fixture = routeFixture(screenName, route: .chat)
            fixture.chat.stream = HermexStreamState(
                streamID: "stream-visual-parity",
                isStreaming: true,
                isRecovering: false,
                liveReasoning: "Checking the shared SwiftUI layout before sending tokens.",
                liveToolActivity: "Reading workspace"
            )
            fixture.chat.messages.append(HermexChatMessageDTO(
                id: "assistant-stream",
                role: "assistant",
                content: "I am comparing the Android output against the iOS golden screens",
                timestamp: 1_783_383_000
            ))
            return fixture
        case "chat-keyboard-open":
            var fixture = routeFixture(screenName, route: .chat)
            fixture.chat.composer.draft = "Refine the Android composer so it reserves the same keyboard and safe-area space as iOS."
            fixture.prefersKeyboardVisible = true
            return fixture
        case "chat-slash-menu":
            var fixture = routeFixture(screenName, route: .chat)
            fixture.chat.composer.draft = "/"
            fixture.overlay = .slashMenu
            return fixture
        case "chat-attachments":
            var fixture = routeFixture(screenName, route: .chat)
            fixture.chat.composer.draft = "Review these screenshots."
            fixture.chat.composer.attachments = [
                HermexAttachmentDTO(name: "session-list.png", path: "/tmp/session-list.png", mime: "image/png", size: 284_672, isImage: true),
                HermexAttachmentDTO(name: "ui-notes.md", path: "/tmp/ui-notes.md", mime: "text/markdown", size: 4_096, isImage: false),
            ]
            fixture.overlay = .attachmentPicker
            return fixture
        case "chat-approval":
            var fixture = routeFixture(screenName, route: .chat)
            fixture.chat.pendingApproval = HermexApprovalPrompt(
                approvalID: "approval-visual-parity",
                title: "Run build command?",
                command: "swift test && ./gradlew assembleDebug",
                details: "Hermex wants permission to validate the Android parity build."
            )
            return fixture
        case "settings":
            return routeFixture(screenName, route: .settings)
        case "workspace-files":
            return routeFixture(screenName, route: .workspace)
        case "git-status":
            return routeFixture(screenName, route: .git)
        case "panels-tasks":
            return panelFixture(screenName, panel: .tasks)
        case "panels-skills":
            return panelFixture(screenName, panel: .skills)
        case "panels-memory":
            return panelFixture(screenName, panel: .memory)
        case "panels-insights":
            return panelFixture(screenName, panel: .insights)
        default:
            return nil
        }
    }

    private static func onboardingWelcomeFixture() -> HermexVisualFixture {
        HermexVisualFixture(
            screenName: "onboarding-welcome",
            appState: HermexAppState(auth: .unconfigured, route: .onboarding),
            onboarding: HermexOnboardingState()
        )
    }

    private static func onboardingConnectFixture() -> HermexVisualFixture {
        HermexVisualFixture(
            screenName: "onboarding-connect",
            appState: HermexAppState(auth: .unconfigured, route: .onboarding),
            onboarding: HermexOnboardingState(
                serverURLString: "https://hermes.example.com",
                displayName: "Hermex",
                password: "",
                customHeaderText: "CF-Access-Client-Id: example\nCF-Access-Client-Secret: secret",
                statusMessage: "Ready to test the connection."
            )
        )
    }

    private static func routeFixture(_ screenName: String, route: HermexRoute) -> HermexVisualFixture {
        HermexVisualFixture(
            screenName: screenName,
            appState: HermexAppState(auth: .loggedIn(server: previewServer), selectedSessionID: "preview-chat", route: route),
            sessions: sessionListState(),
            chat: populatedChatState(),
            settings: settingsState(),
            workspace: workspaceState(),
            git: gitState(),
            panels: panelsState()
        )
    }

    private static func panelFixture(_ screenName: String, panel: HermexPanel) -> HermexVisualFixture {
        var fixture = routeFixture(screenName, route: .panels)
        fixture.panels.selectedPanel = panel
        return fixture
    }

    private static let previewServer = HermexServerIdentity(
        baseURL: URL(string: "https://hermes.example.com")!,
        displayName: "Hermex",
        customHeaders: ["CF-Access-Client-Id": "example"]
    )

    private static let previewRoots = [
        HermexWorkspaceRootDTO(path: "Home", name: "Home", exists: true),
        HermexWorkspaceRootDTO(path: "workspace", name: "workspace", exists: true),
    ]

    private static let previewModels = [
        HermexModelOption(id: "gpt-5.5", name: "gpt-5.5", provider: "codex", label: "gpt-5.5"),
        HermexModelOption(id: "deepseek-v4-pro", name: "deepseek-v4-pro", provider: "deepseek", label: "deepseek-v4-pro"),
        HermexModelOption(id: "cx/gpt-5.5", name: "cx/gpt-5.5", provider: "codex", label: "cx/gpt-5.5"),
    ]

    private static let previewProfiles = [
        HermexProfileOption(name: "default", displayName: "default", isDefault: true, isActive: true, model: "gpt-5.5", provider: "codex"),
        HermexProfileOption(name: "research", displayName: "research", isDefault: false, isActive: false, model: "deepseek-v4-pro", provider: "deepseek"),
    ]

    private static func sessionListState() -> HermexSessionListState {
        HermexSessionListState(
            sessions: [
                HermexSessionDTO(sessionId: "preview-chat", title: "Skip Android parity preview", updatedAt: 1_783_382_400, messageCount: 8, workspace: "workspace", pinned: true, model: "gpt-5.5"),
                HermexSessionDTO(sessionId: "workspace-review", title: "Workspace and git review", updatedAt: 1_783_378_800, messageCount: 48, workspace: "workspace", model: "deepseek-v4-pro"),
                HermexSessionDTO(sessionId: "long-running-stream", title: "Long running stream", updatedAt: 1_783_314_000, messageCount: 54, workspace: "workspace", model: "cx/gpt-5.5"),
                HermexSessionDTO(sessionId: "share-intake", title: "Share intake and uploads", updatedAt: 1_783_227_600, messageCount: 12, workspace: "Home", model: "gpt-5.5"),
            ],
            projects: [
                HermexProjectDTO(projectId: "default", name: "default", color: "#f8d84a")
            ],
            activeProfileName: "default"
        )
    }

    private static func emptyChatState() -> HermexChatState {
        var state = populatedChatState()
        state.session = HermexSessionDTO(sessionId: "empty-chat", title: "New Chat", messageCount: 0, workspace: "workspace", model: "gpt-5.5")
        state.messages = []
        state.composer.draft = ""
        return state
    }

    private static func populatedChatState() -> HermexChatState {
        HermexChatState(
            session: HermexSessionDTO(sessionId: "preview-chat", title: "Skip Android parity preview", messageCount: 8, workspace: "workspace", model: "gpt-5.5"),
            messages: [
                HermexChatMessageDTO(id: "m1", role: "user", content: "Model hermes dang dung la gi?", timestamp: 1_783_381_000),
                HermexChatMessageDTO(id: "m2", role: "assistant", content: "Hermex in this chat is using the shared SwiftUI source as the canonical UI. Android should render the same chrome, transcript rhythm, and composer structure through Skip.", timestamp: 1_783_381_020),
                HermexChatMessageDTO(id: "m3", role: "user", content: "Reasoning effort?", timestamp: 1_783_381_120),
                HermexChatMessageDTO(id: "m4", role: "assistant", content: "The reasoning selector comes from the same composer state as iOS, with low, medium, and high options available.", reasoning: "Checking selected model/provider before showing the reasoning menu.", timestamp: 1_783_381_140),
            ],
            composer: HermexComposerState(
                draft: "",
                selectedModel: "gpt-5.5",
                selectedModelProvider: "codex",
                selectedWorkspace: "Home",
                selectedProfile: "default",
                selectedReasoningEffort: "medium",
                availableModels: previewModels,
                availableProfiles: previewProfiles,
                availableWorkspaces: previewRoots,
                supportedReasoningEfforts: ["low", "medium", "high"]
            )
        )
    }

    private static func settingsState() -> HermexSettingsState {
        HermexSettingsState(
            activeServer: previewServer,
            servers: [
                previewServer,
                HermexServerIdentity(baseURL: URL(string: "http://100.64.0.10:8787")!, displayName: "Tailscale")
            ],
            appTheme: "system",
            defaultModel: "gpt-5.5",
            defaultProfile: "default",
            hapticsEnabled: true,
            glassEnabled: true,
            notificationsEnabled: true
        )
    }

    private static func workspaceState() -> HermexWorkspaceState {
        HermexWorkspaceState(
            roots: previewRoots,
            currentPath: "workspace",
            entries: [
                HermexWorkspaceEntryDTO(name: "Sources", path: "workspace/Sources", type: "directory", isDirectory: true),
                HermexWorkspaceEntryDTO(name: "HermesMobile", path: "workspace/HermesMobile", type: "directory", isDirectory: true),
                HermexWorkspaceEntryDTO(name: "Package.swift", path: "workspace/Package.swift", type: "swift", isDirectory: false, size: 4_096),
                HermexWorkspaceEntryDTO(name: "README.md", path: "workspace/README.md", type: "markdown", isDirectory: false, size: 8_192),
            ],
            preview: HermexFilePreview(
                path: "workspace/README.md",
                content: "# Hermex\n\nShared SwiftUI fixtures drive iOS and Android visual parity captures.",
                mimeType: "text/markdown"
            )
        )
    }

    private static func gitState() -> HermexGitState {
        HermexGitState(
            isRepository: true,
            branch: "master",
            upstream: "fork/master",
            ahead: 1,
            behind: 0,
            files: [
                HermexGitFileChange(path: "Sources/HermexCore/HermexVisualFixtures.swift", status: "modified", additions: 240, deletions: 0, isStaged: true),
                HermexGitFileChange(path: "Sources/HermexUI/HermexChatScreen.swift", status: "modified", additions: 32, deletions: 8, isStaged: false),
                HermexGitFileChange(path: "ci/visual-goldens/hermex-screens.json", status: "modified", additions: 18, deletions: 0, isStaged: true),
            ],
            diffPath: "Sources/HermexCore/HermexVisualFixtures.swift",
            diffText: """
            diff --git a/Sources/HermexCore/HermexVisualFixtures.swift b/Sources/HermexCore/HermexVisualFixtures.swift
            +public enum HermexVisualFixtureCatalog {
            +    public static let screenNames: [String] = [...]
            +}
            """,
            commitMessage: "Add visual parity fixture catalog"
        )
    }

    private static func panelsState() -> HermexPanelsState {
        HermexPanelsState(
            tasks: [
                HermexTaskDTO(id: "weekly-graphics", title: "Weekly computer graphics research digest", status: "Active", schedule: "0 9 * * 1"),
                HermexTaskDTO(id: "android-parity", title: "Android visual parity screenshot pass", status: "Paused", schedule: "manual"),
            ],
            skills: [
                HermexSkillDTO(name: "coding-agent-delegation", enabled: true, summary: "Delegate coding tasks from Hermes to external coding agent CLIs."),
                HermexSkillDTO(name: "hermes-agent", enabled: true, summary: "Configure, extend, or contribute to Hermes Agent."),
                HermexSkillDTO(name: "architecture-diagram", enabled: false, summary: "Generate dark-themed architecture diagrams as HTML."),
                HermexSkillDTO(name: "ascii-art", enabled: false, summary: "ASCII art: pyfiglet, boxes, and image-to-ascii."),
            ],
            memory: [
                HermexMemorySectionDTO(section: "My Notes", content: "User is validating Android parity against the native iOS SwiftUI app. Preserve the HERMEX logo, glass composer, safe-area spacing, panels, workspace, git, and onboarding behavior."),
            ],
            insights: HermexJSONValue.dictionary([
                "sessions": .number(30),
                "messages": .number(1_312),
                "input_tokens": .number(5_957_098),
                "output_tokens": .number(246_393),
                "total_tokens": .number(6_203_491),
                "estimated_cost": .number(2.1145),
                "cache_hit_rate": .number(76),
                "models": .array([
                    .dictionary(["name": .string("deepseek-v4-pro"), "tokens": .number(1_531_842), "share": .number(25)]),
                    .dictionary(["name": .string("gpt-5.5"), "tokens": .number(4_477_168), "share": .number(72)]),
                ]),
            ]),
            selectedPanel: .tasks
        )
    }
}
