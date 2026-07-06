import SwiftUI
import HermexCore

public struct HermexOnboardingScreen: View {
    private static let connectPageIndex = 4
    private static let pageCount = 5

    private let appState: HermexAppState
    private let onboarding: HermexOnboardingState
    private let settings: HermexSettingsState
    private let onEvent: (HermexUIEvent) -> Void

    @State private var currentPage: Int
    @State private var hasCopiedAgentPrompt = false
    @State private var serverURLString: String
    @State private var displayName: String
    @State private var password: String
    @State private var customHeaderText: String

    public init(
        appState: HermexAppState,
        onboarding: HermexOnboardingState = HermexOnboardingState(),
        settings: HermexSettingsState = HermexSettingsState(),
        onEvent: @escaping (HermexUIEvent) -> Void = { _ in }
    ) {
        self.appState = appState
        self.onboarding = onboarding
        self.settings = settings
        self.onEvent = onEvent

        _currentPage = State(initialValue: appState.auth == .unconfigured ? 0 : Self.connectPageIndex)
        _serverURLString = State(initialValue: onboarding.serverURLString)
        _displayName = State(initialValue: onboarding.displayName)
        _password = State(initialValue: onboarding.password)
        _customHeaderText = State(initialValue: onboarding.customHeaderText)
    }

    public var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            VStack(spacing: 0) {
                pageContent
                    .frame(maxWidth: .infinity, maxHeight: .infinity)

                bottomBar
            }
        }
        .foregroundStyle(Color.white)
    }

    @ViewBuilder
    private var pageContent: some View {
        switch currentPage {
        case 0:
            welcomePage
        case 1:
            featuresPage
        case 2:
            agentPromptPage
        case 3:
            tailscalePage
        default:
            connectPage
        }
    }

    private var welcomePage: some View {
        VStack(alignment: .leading, spacing: 22) {
            Spacer(minLength: 24)

            VStack(alignment: .leading, spacing: 18) {
                HermexLogoMark()
                    .frame(width: min(HermexLayoutContract.sessionListLogoWidth, 240))

                Text("Control your Hermes agent from Android.")
                    .font(.system(size: 32, weight: .bold))
                    .lineLimit(nil)

                Text("Connect to your self-hosted Web UI over Tailscale.")
                    .font(.body)
                    .foregroundStyle(Color.white.opacity(0.58))
            }

            HStack(spacing: 8) {
                heroBadge("Password protected")
                heroBadge("Tailscale ready")
            }

            Spacer(minLength: 24)
        }
        .padding(.horizontal, 28)
        .padding(.top, 28)
    }

    private var featuresPage: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 28) {
                centeredHeader(
                    title: "What you get",
                    subtitle: "Your Hermes agent, reachable from Android over Tailscale."
                )

                featureRow(
                    title: "Chat with your Hermes agent from Android",
                    subtitle: "Drive conversations from anywhere on your tailnet."
                )
                featureRow(
                    title: "Manage sessions, tasks, and files remotely",
                    subtitle: "Browse workspaces and stay on top of agent work."
                )
                featureRow(
                    title: "Voice input and mobile-friendly composer controls",
                    subtitle: "Compose naturally with touch-first controls."
                )
                featureRow(
                    title: "Review approvals and clarifications inline",
                    subtitle: "Respond to agent prompts without switching apps."
                )
                featureRow(
                    title: "Self-hosted: your machine, your tailnet",
                    subtitle: "Your Hermes Web UI stays on hardware you control."
                )
            }
            .padding(.horizontal, 28)
            .padding(.top, 44)
            .padding(.bottom, 24)
        }
    }

    private var agentPromptPage: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                stepHeader(
                    number: "1",
                    title: "Set up Hermes Web UI",
                    description: "Send this prompt to your Hermes Agent. It installs Hermes Web UI, enables password auth, and configures Tailscale access."
                )

                HermexGlassPanel(cornerRadius: 12) {
                    VStack(alignment: .leading, spacing: 14) {
                        Text(Self.agentSetupPrompt)
                            .font(.system(size: 13, weight: .regular, design: .monospaced))
                            .lineSpacing(3)
                            .foregroundStyle(Color.white.opacity(0.82))
                            .frame(maxWidth: .infinity, alignment: .leading)

                        Button {
                            hasCopiedAgentPrompt = true
                        } label: {
                            Text(hasCopiedAgentPrompt ? "Copied" : "I copied the prompt")
                                .font(.headline.weight(.semibold))
                                .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.borderedProminent)
                    }
                    .padding(16)
                }
            }
            .padding(.horizontal, 28)
            .padding(.top, 36)
            .padding(.bottom, 24)
        }
    }

    private var tailscalePage: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                stepHeader(
                    number: "2",
                    title: "Install Tailscale on Android",
                    description: "Install Tailscale on your phone and sign into the same tailnet as your server. Your agent will reply with the exact URL to use on the next screen."
                )

                setupStep("1", "Install Tailscale from Google Play.")
                setupStep("2", "Sign in with the same account you used on your server.")
                setupStep("3", "Keep Tailscale connected while using Hermex.")

                Text("Then paste the Hermex server URL on the next screen.")
                    .font(.callout.weight(.semibold))
                    .foregroundStyle(Color(red: 1.0, green: 0.74, blue: 0.1))
            }
            .padding(.horizontal, 28)
            .padding(.top, 48)
            .padding(.bottom, 24)
        }
    }

    private var connectPage: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                HStack(alignment: .center) {
                    HermexLogoMark()
                        .frame(width: min(HermexLayoutContract.sessionListLogoWidth, 220))
                    Spacer()
                    if appState.auth != .unconfigured {
                        HermexCircleIconButton(systemImage: "xmark", accessibilityLabel: "Sessions") {
                            onEvent(.openRoute(.sessions))
                        }
                    }
                }

                VStack(alignment: .leading, spacing: 8) {
                    Text("Connect to Hermex")
                        .font(.system(size: 34, weight: .bold))
                    Text(statusText)
                        .font(.title3)
                        .foregroundStyle(Color.white.opacity(0.55))
                }

                serverPicker
                connectionForm
                statusBlock
            }
            .padding(.horizontal, 24)
            .padding(.top, 28)
            .padding(.bottom, 34)
        }
    }

    private var serverPicker: some View {
        Group {
            if !settings.servers.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Saved servers")
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(Color.white.opacity(0.55))
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(settings.servers, id: \.baseURL) { server in
                                Button {
                                    serverURLString = server.baseURL.absoluteString
                                    displayName = server.displayName
                                    customHeaderText = server.customHeaders
                                        .map { "\($0.key): \($0.value)" }
                                        .sorted()
                                        .joined(separator: "\n")
                                    onEvent(.selectServer(server))
                                } label: {
                                    Text(server.displayName)
                                        .font(.callout.weight(.semibold))
                                        .lineLimit(1)
                                }
                                .buttonStyle(.bordered)
                                .clipShape(Capsule())
                            }
                        }
                    }
                }
            }
        }
    }

    private var connectionForm: some View {
        HermexGlassPanel {
            VStack(alignment: .leading, spacing: 15) {
                fieldLabel("Server URL")
                TextField("https://hermes.example.com", text: serverURLBinding)
                    .hermexOnboardingInputStyle()

                fieldLabel("Name")
                TextField("Hermex", text: displayNameBinding)
                    .hermexOnboardingInputStyle()

                fieldLabel("Password")
                SecureField("Server password", text: passwordBinding)
                    .hermexOnboardingInputStyle()

                fieldLabel("Custom headers")
                TextEditor(text: customHeaderBinding)
                    .font(.footnote.monospaced())
                    .foregroundStyle(HermexUIColors.primaryText)
                    .frame(minHeight: 74)
                    .padding(10)
                    .background(HermexUIColors.glassFill, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                    .overlay(
                        RoundedRectangle(cornerRadius: 10)
                            .stroke(HermexUIColors.hairline, lineWidth: 0.6)
                    )

                HStack(spacing: 10) {
                    Button {
                        onEvent(.testOnboardingConnectionDraft(
                            serverURLString: serverURLString,
                            displayName: displayName,
                            password: password,
                            customHeaderText: customHeaderText
                        ))
                    } label: {
                        if onboarding.isTestingConnection {
                            ProgressView()
                        } else {
                            Text("Test")
                                .font(.headline.weight(.semibold))
                        }
                    }
                    .foregroundStyle(HermexUIColors.primaryText)
                    .padding(.horizontal, 22)
                    .padding(.vertical, 13)
                    .background(HermexUIColors.glassFillStrong, in: Capsule())
                    .overlay {
                        Capsule().stroke(HermexUIColors.hairline, lineWidth: 0.6)
                    }
                    .disabled(onboarding.isTestingConnection || onboarding.isSigningIn)

                    Button {
                        onEvent(.connectOnboardingDraft(
                            serverURLString: serverURLString,
                            displayName: displayName,
                            password: password,
                            customHeaderText: customHeaderText
                        ))
                    } label: {
                        if onboarding.isSigningIn {
                            ProgressView()
                                .frame(maxWidth: .infinity)
                        } else {
                            Text("Connect")
                                .font(.headline.weight(.semibold))
                                .frame(maxWidth: .infinity)
                        }
                    }
                    .foregroundStyle(Color.black)
                    .padding(.horizontal, 22)
                    .padding(.vertical, 13)
                    .background(HermexUIColors.primaryText, in: Capsule())
                    .disabled(onboarding.isTestingConnection || onboarding.isSigningIn)
                }
                .padding(.top, 4)
            }
            .padding(18)
        }
    }

    private var bottomBar: some View {
        VStack(spacing: 14) {
            pageIndicator

            HStack(spacing: 10) {
                if currentPage < Self.connectPageIndex {
                    Button {
                        currentPage = Self.connectPageIndex
                    } label: {
                        Text("I already have a server")
                            .font(.callout.weight(.semibold))
                    }
                    .buttonStyle(.bordered)
                } else {
                    Button {
                        currentPage = max(0, currentPage - 1)
                    } label: {
                        Text("Back")
                            .font(.callout.weight(.semibold))
                    }
                    .buttonStyle(.bordered)
                }

                Button {
                    advance()
                } label: {
                    Text(primaryButtonTitle)
                        .font(.headline.weight(.semibold))
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .disabled(currentPage == Self.connectPageIndex && serverURLString.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
        }
        .padding(.horizontal, 24)
        .padding(.top, 14)
        .padding(.bottom, 18)
        .background(Color.black.opacity(0.92))
    }

    private var pageIndicator: some View {
        HStack(spacing: 7) {
            ForEach(0..<Self.pageCount, id: \.self) { index in
                Circle()
                    .fill(index == currentPage ? Color.white : Color.white.opacity(0.24))
                    .frame(width: index == currentPage ? 8.0 : 6.0, height: index == currentPage ? 8.0 : 6.0)
            }
        }
    }

    @ViewBuilder
    private var statusBlock: some View {
        if let error = onboarding.errorMessage {
            Text(error)
                .font(.footnote)
                .foregroundStyle(.red)
                .padding(.horizontal, 4)
        } else if let status = onboarding.statusMessage {
            Text(status)
                .font(.footnote)
                .foregroundStyle(Color.white.opacity(0.6))
                .padding(.horizontal, 4)
        }
    }

    private func heroBadge(_ title: String) -> some View {
        Text(title)
            .font(.caption.weight(.semibold))
            .foregroundStyle(Color.white.opacity(0.72))
            .padding(.horizontal, 10)
            .padding(.vertical, 7)
            .background(Color.white.opacity(0.06), in: Capsule())
            .overlay {
                Capsule().stroke(Color.white.opacity(0.08), lineWidth: 1)
            }
    }

    private func centeredHeader(title: String, subtitle: String) -> some View {
        VStack(spacing: 10) {
            Text(title)
                .font(.system(size: 28, weight: .bold))
            Text(subtitle)
                .font(.body)
                .foregroundStyle(Color.white.opacity(0.45))
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
    }

    private func featureRow(title: String, subtitle: String) -> some View {
        HStack(alignment: .top, spacing: 16) {
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(Color(red: 1.0, green: 0.74, blue: 0.1).opacity(0.12))
                .frame(width: 40, height: 40)
                .overlay {
                    Circle()
                        .fill(Color(red: 1.0, green: 0.74, blue: 0.1))
                        .frame(width: 8, height: 8)
                }

            VStack(alignment: .leading, spacing: 3) {
                Text(title)
                    .font(.body.weight(.semibold))
                Text(subtitle)
                    .font(.subheadline)
                    .foregroundStyle(Color.white.opacity(0.42))
            }
        }
    }

    private func stepHeader(number: String, title: String, description: String) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Step \(number)")
                .font(.caption.weight(.bold))
                .foregroundStyle(Color(red: 1.0, green: 0.74, blue: 0.1))
                .textCase(.uppercase)
            Text(title)
                .font(.system(size: 30, weight: .bold))
            Text(description)
                .font(.body)
                .foregroundStyle(Color.white.opacity(0.52))
        }
    }

    private func setupStep(_ number: String, _ text: String) -> some View {
        HStack(alignment: .top, spacing: 14) {
            Text(number)
                .font(.caption.weight(.bold))
                .foregroundStyle(Color.black)
                .frame(width: 24, height: 24)
                .background(Color(red: 1.0, green: 0.74, blue: 0.1), in: Circle())

            Text(text)
                .font(.body.weight(.semibold))
                .foregroundStyle(Color.white.opacity(0.84))
        }
    }

    private func fieldLabel(_ title: String) -> some View {
        Text(title)
            .font(.footnote.weight(.semibold))
            .foregroundStyle(Color.white.opacity(0.62))
    }

    private func advance() {
        if currentPage < Self.connectPageIndex {
            currentPage += 1
        } else {
            onEvent(.connectOnboardingDraft(
                serverURLString: serverURLString,
                displayName: displayName,
                password: password,
                customHeaderText: customHeaderText
            ))
        }
    }

    private var primaryButtonTitle: String {
        if currentPage == 0 {
            return "Get Started"
        }
        if currentPage == 1 {
            return "Set Up"
        }
        if currentPage == Self.connectPageIndex {
            return "Connect"
        }
        return "Continue"
    }

    private var serverURLBinding: Binding<String> {
        Binding(
            get: { serverURLString },
            set: {
                serverURLString = $0
                onEvent(.updateOnboardingServerURL($0))
            }
        )
    }

    private var displayNameBinding: Binding<String> {
        Binding(
            get: { displayName },
            set: {
                displayName = $0
                onEvent(.updateOnboardingDisplayName($0))
            }
        )
    }

    private var passwordBinding: Binding<String> {
        Binding(
            get: { password },
            set: {
                password = $0
                onEvent(.updateOnboardingPassword($0))
            }
        )
    }

    private var customHeaderBinding: Binding<String> {
        Binding(
            get: { customHeaderText },
            set: {
                customHeaderText = $0
                onEvent(.updateOnboardingCustomHeaders($0))
            }
        )
    }

    private var statusText: String {
        switch appState.auth {
        case .unconfigured:
            return "Add your server to start chatting."
        case .loggedOut(let server):
            return "Sign in to \(server.displayName)."
        case .loggedIn(let server):
            return "Connected to \(server.displayName)."
        }
    }

    private static let agentSetupPrompt = """
Set up Hermes Web UI on this machine for access from my Android phone via Tailscale.

Clone and install https://github.com/nesquena/hermes-webui. It is a Node.js web app. Install dependencies and start it on port 8787.
Enable password authentication by setting the HERMES_WEBUI_PASSWORD environment variable. Generate a secure random password and save it.
Install Tailscale on this machine. Authenticate to my Tailscale account, then make the WebUI reachable over Tailscale.
Try tailscale serve --bg 8787 first. If Tailscale Serve is disabled, bind the server to 0.0.0.0 only after confirming password auth is active.
Set up auto-start so the WebUI survives reboots.
Verify it works: curl http://$(tailscale ip -4):8787/health should return a success response.
Reply with the exact server URL, the password, and any setup steps still needed on Android.
Do not use Cloudflare. Optimize for Tailscale + Android.
"""
}

private extension View {
    func hermexOnboardingInputStyle() -> some View {
        self
            .textFieldStyle(.plain)
            .font(.body)
            .foregroundStyle(HermexUIColors.primaryText)
            .padding(.horizontal, 12)
            .padding(.vertical, 13)
            .background(HermexUIColors.glassFill, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .stroke(HermexUIColors.hairline, lineWidth: 0.6)
            }
    }
}
