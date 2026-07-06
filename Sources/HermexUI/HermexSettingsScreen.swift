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
        ZStack {
            HermexUIColors.systemBackground.ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    topBar

                    settingsSection("Active Server") {
                        Text(state.activeServer?.displayName ?? "No server")
                            .foregroundStyle(HermexUIColors.primaryText)
                        ForEach(state.servers, id: \.baseURL) { server in
                            Text(server.displayName)
                                .foregroundStyle(HermexUIColors.secondaryText)
                        }
                    }

                    settingsSection("Appearance") {
                        row("Theme", state.appTheme)
                        row("Glass", state.glassEnabled ? "On" : "Off")
                        row("Haptics", state.hapticsEnabled ? "On" : "Off")
                    }

                    settingsSection("Chat") {
                        row("Default model", state.defaultModel ?? "Server default")
                        row("Default profile", state.defaultProfile ?? "Active profile")
                        row("Notifications", state.notificationsEnabled ? "On" : "Off")
                    }

                    Button("Sign Out") { onEvent(.signOut) }
                        .font(.headline.weight(.semibold))
                        .foregroundStyle(Color.red.opacity(0.92))
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(14)
                        .background(HermexUIColors.glassFill, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                        .overlay {
                            RoundedRectangle(cornerRadius: 14, style: .continuous)
                                .stroke(HermexUIColors.hairline, lineWidth: 0.6)
                        }
                }
                .padding(.horizontal, 16)
                .padding(.top, 12)
                .padding(.bottom, 32)
            }
        }
        .foregroundStyle(HermexUIColors.primaryText)
    }

    private var topBar: some View {
        HStack(spacing: 12) {
            HermexCircleIconButton(
                systemImage: "chevron.left",
                accessibilityLabel: "Back",
                action: { onEvent(.openRoute(.sessions)) }
            )
            HermexScreenTitle("Settings", subtitle: state.activeServer?.displayName)
            Spacer()
        }
    }

    private func settingsSection<Content: View>(
        _ title: String,
        @ViewBuilder content: () -> Content
    ) -> some View {
        HermexGlassPanel(cornerRadius: 14) {
            VStack(alignment: .leading, spacing: 12) {
                Text(title)
                    .font(.caption.weight(.semibold))
                    .textCase(.uppercase)
                    .foregroundStyle(HermexUIColors.secondaryText)
                content()
            }
            .padding(14)
        }
    }

    private func row(_ title: String, _ value: String) -> some View {
        HStack {
            Text(title)
                .foregroundStyle(HermexUIColors.primaryText)
            Spacer()
            Text(value).foregroundStyle(HermexUIColors.secondaryText)
        }
    }
}
