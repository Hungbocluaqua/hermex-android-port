import SwiftUI
import HermexCore

public struct HermexStoreRootScreen: View {
    @State private var store: HermexAppStore
    @State private var renderRevision = 0
    private let onUnhandledEvent: (HermexUIEvent) -> Void

    public init(
        store: HermexAppStore,
        onUnhandledEvent: @escaping (HermexUIEvent) -> Void = { _ in }
    ) {
        self._store = State(initialValue: store)
        self.onUnhandledEvent = onUnhandledEvent
    }

    public var body: some View {
        HermexRootScreen(
            appState: store.appState,
            onboarding: store.onboarding,
            sessions: store.sessions,
            chat: store.chat,
            settings: store.settings,
            workspace: store.workspace,
            git: store.git,
            panels: store.panels
        ) { event in
            if let action = event.appAction {
                Task { @MainActor in
                    await store.send(action)
                    renderRevision += 1
                }
            } else {
                onUnhandledEvent(event)
            }
        }
        .task(id: store.appState.route) {
            if store.appState.route != .onboarding {
                await store.send(.refreshComposerConfiguration)
                renderRevision += 1
            }
        }
    }
}
