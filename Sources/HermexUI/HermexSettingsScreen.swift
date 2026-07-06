import SwiftUI
import HermexCore

public struct HermexSettingsScreen: View {
    private let state: HermexSettingsState
    private let onEvent: (HermexUIEvent) -> Void

    public init(state: HermexSettingsState, onEvent: @escaping (HermexUIEvent) -> Void = { _ in }) {
        self.state = state
        self.onEvent = onEvent
    }

    public var body: some View {
        List {
            Section("Active Server") {
                Text(state.activeServer?.displayName ?? "No server")
                ForEach(state.servers, id: \.baseURL) { server in
                    Text(server.displayName)
                }
            }
            Section("Appearance") {
                row("Theme", state.appTheme)
                row("Glass", state.glassEnabled ? "On" : "Off")
                row("Haptics", state.hapticsEnabled ? "On" : "Off")
            }
            Section("Chat") {
                row("Default model", state.defaultModel ?? "Server default")
                row("Default profile", state.defaultProfile ?? "Active profile")
                row("Notifications", state.notificationsEnabled ? "On" : "Off")
            }
            Section("Account") {
                Button("Sign Out") { onEvent(.signOut) }
            }
        }
        .navigationTitle("Settings")
    }

    private func row(_ title: String, _ value: String) -> some View {
        HStack {
            Text(title)
            Spacer()
            Text(value).foregroundStyle(.secondary)
        }
    }
}
