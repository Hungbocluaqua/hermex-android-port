import SwiftUI
import HermexCore

public struct HermexStoreRootScreen: View {
    @State private var store: HermexAppStore
    @State private var appState: HermexAppState
    @State private var onboarding: HermexOnboardingState
    @State private var sessions: HermexSessionListState
    @State private var chat: HermexChatState
    @State private var settings: HermexSettingsState
    @State private var workspace: HermexWorkspaceState
    @State private var git: HermexGitState
    @State private var panels: HermexPanelsState
    private let onUnhandledEvent: (HermexUIEvent) -> Void

    public init(
        store: HermexAppStore,
        onUnhandledEvent: @escaping (HermexUIEvent) -> Void = { _ in }
    ) {
        self._store = State(initialValue: store)
        self._appState = State(initialValue: store.appState)
        self._onboarding = State(initialValue: store.onboarding)
        self._sessions = State(initialValue: store.sessions)
        self._chat = State(initialValue: store.chat)
        self._settings = State(initialValue: store.settings)
        self._workspace = State(initialValue: store.workspace)
        self._git = State(initialValue: store.git)
        self._panels = State(initialValue: store.panels)
        self.onUnhandledEvent = onUnhandledEvent
    }

    public var body: some View {
        // Skip does not observe class property mutations. Mirror store snapshots into
        // @State so Compose/SwiftUI re-render after actions without remounting inputs.
        HermexRootScreen(
            appState: appState,
            onboarding: onboarding,
            sessions: sessions,
            chat: chat,
            settings: settings,
            workspace: workspace,
            git: git,
            panels: panels
        ) { event in
            if let action = event.appAction {
                Task { @MainActor in
                    await store.send(action)
                    syncFromStore()
                }
            } else {
                onUnhandledEvent(event)
            }
        }
        .task(id: appState.route) {
            if appState.route != .onboarding {
                await store.send(.refreshComposerConfiguration)
                syncFromStore()
            }
        }
    }

    @MainActor
    private func syncFromStore() {
        appState = store.appState
        onboarding = store.onboarding
        sessions = store.sessions
        chat = store.chat
        settings = store.settings
        workspace = store.workspace
        git = store.git
        panels = store.panels
    }
}
