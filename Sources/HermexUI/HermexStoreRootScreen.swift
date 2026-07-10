import Foundation
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
    private let loadAttachmentData: @Sendable (_ sessionID: String, _ path: String) async -> Data?
    private let playAttachment: @Sendable (_ sessionID: String, _ path: String, _ filename: String) async -> Bool
    private let stopAttachmentPlayback: @Sendable () async -> Void
    private let onUnhandledEvent: @MainActor (HermexUIEvent) async -> Void
    private let onActionCompleted: @MainActor () -> Void

    public init(
        store: HermexAppStore,
        loadAttachmentData: @escaping @Sendable (_ sessionID: String, _ path: String) async -> Data? = { _, _ in nil },
        playAttachment: @escaping @Sendable (_ sessionID: String, _ path: String, _ filename: String) async -> Bool = { _, _, _ in false },
        stopAttachmentPlayback: @escaping @Sendable () async -> Void = {},
        onUnhandledEvent: @escaping @MainActor (HermexUIEvent) async -> Void = { _ in },
        onActionCompleted: @escaping @MainActor () -> Void = {}
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
        self.loadAttachmentData = loadAttachmentData
        self.playAttachment = playAttachment
        self.stopAttachmentPlayback = stopAttachmentPlayback
        self.onUnhandledEvent = onUnhandledEvent
        self.onActionCompleted = onActionCompleted
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
            panels: panels,
            loadAttachmentData: loadAttachmentData,
            playAttachment: playAttachment,
            stopAttachmentPlayback: stopAttachmentPlayback
        ) { event in
            if let action = event.appAction {
                Task { @MainActor in
                    await store.send(action)
                    syncFromStore()
                    onActionCompleted()
                }
            } else {
                Task { @MainActor in
                    await onUnhandledEvent(event)
                    syncFromStore()
                }
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
