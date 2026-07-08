import SwiftUI
import HermexCore

public struct HermexRootScreen: View {
    private let appState: HermexAppState
    private let onboarding: HermexOnboardingState
    private let sessions: HermexSessionListState
    private let chat: HermexChatState
    private let settings: HermexSettingsState
    private let workspace: HermexWorkspaceState
    private let git: HermexGitState
    private let panels: HermexPanelsState
    private let prefersKeyboardVisible: Bool
    private let onEvent: (HermexUIEvent) -> Void

    public init(
        appState: HermexAppState,
        onboarding: HermexOnboardingState = HermexOnboardingState(),
        sessions: HermexSessionListState = HermexSessionListState(),
        chat: HermexChatState = HermexChatState(),
        settings: HermexSettingsState = HermexSettingsState(),
        workspace: HermexWorkspaceState = HermexWorkspaceState(),
        git: HermexGitState = HermexGitState(),
        panels: HermexPanelsState = HermexPanelsState(),
        prefersKeyboardVisible: Bool = false,
        onEvent: @escaping (HermexUIEvent) -> Void = { _ in }
    ) {
        self.appState = appState
        self.onboarding = onboarding
        self.sessions = sessions
        self.chat = chat
        self.settings = settings
        self.workspace = workspace
        self.git = git
        self.panels = panels
        self.prefersKeyboardVisible = prefersKeyboardVisible
        self.onEvent = onEvent
    }

    public var body: some View {
        Group {
            switch appState.route {
            case .onboarding:
                HermexOnboardingScreen(appState: appState, onboarding: onboarding, settings: settings, onEvent: onEvent)
            case .sessions:
                HermexSessionListScreen(state: sessions, onEvent: onEvent)
            case .chat:
                HermexChatScreen(state: chat, prefersComposerFocused: prefersKeyboardVisible, onEvent: onEvent)
            case .settings:
                HermexSettingsScreen(state: settings, onEvent: onEvent)
            case .workspace:
                HermexWorkspaceScreen(state: workspace, onEvent: onEvent)
            case .git:
                HermexGitScreen(state: git, onEvent: onEvent)
            case .panels:
                HermexPanelsScreen(state: panels, onEvent: onEvent)
            }
        }
        .background(HermexUIColors.systemBackground)
    }
}
