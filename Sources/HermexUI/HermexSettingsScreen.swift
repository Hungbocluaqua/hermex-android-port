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
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    topBar
                    activeServerSection
                    serverManagementSection
                    appearanceSection
                    chatSection
                    securitySection
                    signOutButton
                }
                .padding(.horizontal, 16)
                .padding(.top, 12)
                .padding(.bottom, 32)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(HermexUIColors.systemBackground.ignoresSafeArea())
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

    private var activeServerSection: some View {
        settingsSection("Active Server") {
            if let server = state.activeServer {
                HStack(alignment: .top, spacing: 12) {
                    serverIcon(systemImage: "server.rack")

                    VStack(alignment: .leading, spacing: 8) {
                        HStack(alignment: .firstTextBaseline, spacing: 8) {
                            Text(server.displayName)
                                .font(.title3.weight(.semibold))
                                .foregroundStyle(HermexUIColors.primaryText)
                                .lineLimit(2)
                            Spacer(minLength: 8)
                            statusPill("Connected", tint: .green)
                        }

                        Text(server.baseURL.absoluteString)
                            .font(.footnote)
                            .foregroundStyle(HermexUIColors.secondaryText)
                            .lineLimit(2)

                        if !server.customHeaders.isEmpty {
                            Label("\(server.customHeaders.count) custom headers", systemImage: "key.horizontal")
                                .font(.caption.weight(.medium))
                                .foregroundStyle(HermexUIColors.secondaryText)
                        }
                    }
                }
            } else {
                emptyState("No server is selected.", systemImage: "exclamationmark.triangle")
            }
        }
    }

    private var serverManagementSection: some View {
        settingsSection("Servers") {
            if state.servers.isEmpty {
                emptyState("Add a Hermex server to start chatting.", systemImage: "server.rack")
            } else {
                ForEach(state.servers, id: \.baseURL) { server in
                    serverCard(server)
                }
            }

            Button {
                onEvent(.openRoute(.onboarding))
            } label: {
                Label("Add Server", systemImage: "plus")
                    .font(.headline.weight(.semibold))
                    .foregroundStyle(HermexUIColors.primaryText)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 14)
                    .frame(height: 48)
                    .background(HermexUIColors.glassFillStrong, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                    .overlay {
                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .stroke(HermexUIColors.hairline, lineWidth: 0.7)
                    }
            }
            .buttonStyle(.plain)
        }
    }

    private var appearanceSection: some View {
        settingsSection("Appearance") {
            settingValueRow(systemImage: "circle.lefthalf.filled", title: "Theme", value: state.appTheme.capitalized)
            settingValueRow(systemImage: "sparkles", title: "Glass", value: state.glassEnabled ? "On" : "Off")
            settingValueRow(systemImage: "hand.tap", title: "Haptics", value: state.hapticsEnabled ? "On" : "Off")
        }
    }

    private var chatSection: some View {
        settingsSection("Chat Defaults") {
            settingValueRow(systemImage: "cpu", title: "Default Model", value: state.defaultModel ?? "Server default")
            settingValueRow(systemImage: "person.crop.circle", title: "Default Profile", value: state.defaultProfile ?? "Active profile")
            settingValueRow(systemImage: "bell", title: "Notifications", value: state.notificationsEnabled ? "On" : "Off")
        }
    }

    private var securitySection: some View {
        let headerCount = state.activeServer?.customHeaders.count ?? 0

        return settingsSection("Privacy & Security") {
            settingValueRow(systemImage: "lock", title: "Passwords", value: "Not stored")
            settingValueRow(systemImage: "key.horizontal", title: "Custom Headers", value: headerCount == 0 ? "None" : "\(headerCount)")
            settingValueRow(systemImage: "externaldrive.badge.checkmark", title: "Server Scope", value: "Per URL")
        }
    }

    private var signOutButton: some View {
        Button {
            onEvent(.signOut)
        } label: {
            HStack(spacing: 12) {
                Image(systemName: "rectangle.portrait.and.arrow.right")
                    .font(.headline.weight(.semibold))
                Text("Sign Out")
                    .font(.headline.weight(.semibold))
                Spacer()
            }
            .foregroundStyle(Color.red.opacity(0.95))
            .padding(16)
            .background(Color.red.opacity(0.10), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .stroke(Color.red.opacity(0.25), lineWidth: 0.8)
            }
        }
        .buttonStyle(.plain)
    }

    private func serverCard(_ server: HermexServerIdentity) -> some View {
        Button {
            onEvent(.selectServer(server))
        } label: {
            HStack(alignment: .top, spacing: 12) {
                serverIcon(systemImage: isActive(server) ? "checkmark.circle.fill" : "server.rack")

                VStack(alignment: .leading, spacing: 5) {
                    Text(server.displayName)
                        .font(.headline.weight(.semibold))
                        .foregroundStyle(HermexUIColors.primaryText)
                        .lineLimit(2)

                    Text(server.baseURL.absoluteString)
                        .font(.caption)
                        .foregroundStyle(HermexUIColors.secondaryText)
                        .lineLimit(2)

                    if !server.customHeaders.isEmpty {
                        Text("\(server.customHeaders.count) custom headers")
                            .font(.caption2.weight(.medium))
                            .foregroundStyle(HermexUIColors.secondaryText)
                    }
                }

                Spacer(minLength: 8)

                if isActive(server) {
                    statusPill("Active", tint: .blue)
                } else {
                    Image(systemName: "chevron.right")
                        .font(.caption.weight(.bold))
                        .foregroundStyle(HermexUIColors.secondaryText)
                }
            }
            .padding(12)
            .background(HermexUIColors.glassFillStrong, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .stroke(isActive(server) ? Color.blue.opacity(0.45) : HermexUIColors.hairline, lineWidth: 0.8)
            }
        }
        .buttonStyle(.plain)
    }

    private func settingsSection<Content: View>(
        _ title: String,
        @ViewBuilder content: () -> Content
    ) -> some View {
        HermexGlassPanel(cornerRadius: 18) {
            VStack(alignment: .leading, spacing: 12) {
                Text(title)
                    .font(.caption.weight(.bold))
                    .textCase(.uppercase)
                    .foregroundStyle(HermexUIColors.secondaryText)
                content()
            }
            .padding(16)
            .foregroundStyle(HermexUIColors.primaryText)
        }
    }

    private func settingValueRow(systemImage: String, title: String, value: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: systemImage)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(HermexUIColors.primaryText)
                .frame(width: 24)

            Text(title)
                .foregroundStyle(HermexUIColors.primaryText)
            Spacer(minLength: 12)
            Text(value)
                .font(.subheadline.weight(.medium))
                .foregroundStyle(HermexUIColors.secondaryText)
                .multilineTextAlignment(.trailing)
        }
        .font(.body)
        .frame(minHeight: 36)
    }

    private func statusPill(_ label: String, tint: Color) -> some View {
        Text(label)
            .font(.caption.weight(.semibold))
            .foregroundStyle(tint)
            .padding(.horizontal, 9)
            .padding(.vertical, 5)
            .background(tint.opacity(0.12), in: Capsule())
    }

    private func serverIcon(systemImage: String) -> some View {
        Image(systemName: systemImage)
            .font(.headline.weight(.semibold))
            .foregroundStyle(HermexUIColors.primaryText)
            .frame(width: 38, height: 38)
            .background(HermexUIColors.glassFillStrong, in: Circle())
    }

    private func emptyState(_ text: String, systemImage: String) -> some View {
        Label(text, systemImage: systemImage)
            .font(.subheadline.weight(.medium))
            .foregroundStyle(HermexUIColors.secondaryText)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(12)
            .background(HermexUIColors.glassFillStrong, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    private func isActive(_ server: HermexServerIdentity) -> Bool {
        state.activeServer?.baseURL == server.baseURL
    }
}
