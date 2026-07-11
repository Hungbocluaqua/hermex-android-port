import Foundation
import SwiftUI
import HermexCore

public struct HermexRootScreen: View {
    @Environment(\.colorScheme) private var colorScheme
    private let appState: HermexAppState
    private let onboarding: HermexOnboardingState
    private let sessions: HermexSessionListState
    private let chat: HermexChatState
    private let settings: HermexSettingsState
    private let workspace: HermexWorkspaceState
    private let git: HermexGitState
    private let panels: HermexPanelsState
    private let prefersKeyboardVisible: Bool
    private let onboardingServerURLBinding: Binding<String>?
    private let onboardingDisplayNameBinding: Binding<String>?
    private let onboardingPasswordBinding: Binding<String>?
    private let onboardingCustomHeaderBinding: Binding<String>?
    private let loadAttachmentData: @Sendable (_ sessionID: String, _ path: String) async -> Data?
    private let playAttachment: @Sendable (_ sessionID: String, _ path: String, _ filename: String) async -> Bool
    private let stopAttachmentPlayback: @Sendable () async -> Void
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
        onboardingServerURLBinding: Binding<String>? = nil,
        onboardingDisplayNameBinding: Binding<String>? = nil,
        onboardingPasswordBinding: Binding<String>? = nil,
        onboardingCustomHeaderBinding: Binding<String>? = nil,
        loadAttachmentData: @escaping @Sendable (_ sessionID: String, _ path: String) async -> Data? = { _, _ in nil },
        playAttachment: @escaping @Sendable (_ sessionID: String, _ path: String, _ filename: String) async -> Bool = { _, _, _ in false },
        stopAttachmentPlayback: @escaping @Sendable () async -> Void = {},
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
        self.onboardingServerURLBinding = onboardingServerURLBinding
        self.onboardingDisplayNameBinding = onboardingDisplayNameBinding
        self.onboardingPasswordBinding = onboardingPasswordBinding
        self.onboardingCustomHeaderBinding = onboardingCustomHeaderBinding
        self.loadAttachmentData = loadAttachmentData
        self.playAttachment = playAttachment
        self.stopAttachmentPlayback = stopAttachmentPlayback
        self.onEvent = onEvent
    }

    public var body: some View {
        Group {
            switch appState.route {
            case .onboarding:
                HermexOnboardingScreen(
                    appState: appState,
                    onboarding: onboarding,
                    settings: settings,
                    serverURLDraftBinding: onboardingServerURLBinding,
                    displayNameDraftBinding: onboardingDisplayNameBinding,
                    passwordDraftBinding: onboardingPasswordBinding,
                    customHeaderDraftBinding: onboardingCustomHeaderBinding,
                    onEvent: onEvent
                )
            case .sessions:
                HermexSessionListScreen(state: sessions, settings: settings, onEvent: onEvent)
            case .chat:
                HermexChatScreen(
                    state: chat,
                    prefersComposerFocused: prefersKeyboardVisible,
                    loadAttachmentData: loadAttachmentData,
                    playAttachment: playAttachment,
                    stopAttachmentPlayback: stopAttachmentPlayback,
                    onEvent: onEvent
                )
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
        .background(rootBackground)
        .environment(\.hermexGlassEnabled, settings.glassEnabled)
        .preferredColorScheme(preferredColorScheme)
    }

    private var rootBackground: Color {
        switch settings.appTheme.lowercased() {
        case "light": return Color.white
        case "dark": return Color.black
        default: return colorScheme == .dark ? Color.black : Color.white
        }
    }

    private var preferredColorScheme: ColorScheme? {
        switch settings.appTheme.lowercased() {
        case "light": return .light
        case "dark": return .dark
        default: return nil
        }
    }
}
