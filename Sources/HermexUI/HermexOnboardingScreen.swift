import SwiftUI
import HermexCore

private enum HermexOnboardingConnectField: Hashable {
    case serverURL
    case password
}

public struct HermexOnboardingScreen: View {
    private static let connectPageIndex = 4
    private static let pageCount = 5

    private let appState: HermexAppState
    private let onboarding: HermexOnboardingState
    private let settings: HermexSettingsState
    private let onEvent: (HermexUIEvent) -> Void

    @State private var currentPage: Int
    @State private var hasCopiedAgentPrompt = false
    @State private var hasBypassedCopyReminder = false
    @State private var isShowingCopyReminder = false
    @State private var isShowingAdvanced = false
    @State private var serverURLString: String
    @State private var displayName: String
    @State private var password: String
    @State private var customHeaderText: String
    @FocusState private var focusedField: HermexOnboardingConnectField?

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

        let hasSavedServer = !onboarding.serverURLString
            .trimmingCharacters(in: CharacterSet.whitespacesAndNewlines)
            .isEmpty || !settings.servers.isEmpty
        _currentPage = State(initialValue: appState.auth == .unconfigured && !hasSavedServer ? 0 : Self.connectPageIndex)
        _serverURLString = State(initialValue: onboarding.serverURLString)
        _displayName = State(initialValue: onboarding.displayName)
        _password = State(initialValue: onboarding.password)
        _customHeaderText = State(initialValue: onboarding.customHeaderText)
    }

    private var canSubmitConnection: Bool {
        !serverURLString.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines).isEmpty
    }

    private var isEditingConnectionField: Bool {
        currentPage == Self.connectPageIndex && focusedField != nil
    }

    public var body: some View {
        ZStack {
            VStack(spacing: 0) {
                pageContent
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .contentShape(Rectangle())
                    .simultaneousGesture(
                        DragGesture(minimumDistance: 32)
                            .onEnded { value in
                                handleSwipe(value.translation)
                            }
                    )

                bottomBar
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.black.ignoresSafeArea())
        .foregroundStyle(Color.white)
        .hermexOnboardingKeyboardInset(isVisible: isEditingConnectionField) {
            keyboardActionBar
        }
        .animation(Animation.easeInOut(duration: 0.18), value: isEditingConnectionField)
        .alert("Copy the setup prompt first", isPresented: $isShowingCopyReminder) {
            Button("Stay Here", role: .cancel) {}
            Button("Continue Anyway") {
                hasBypassedCopyReminder = true
                advance()
            }
        } message: {
            Text("Copy the agent setup prompt on your desktop before continuing so Hermes Web UI and Tailscale are configured correctly.")
        }
        .onChange(of: onboarding.serverURLString) { _, newValue in
            if newValue != serverURLString {
                serverURLString = newValue
            }
        }
        .onChange(of: onboarding.displayName) { _, newValue in
            if newValue != displayName {
                displayName = newValue
            }
        }
        .onChange(of: onboarding.customHeaderText) { _, newValue in
            if newValue != customHeaderText {
                customHeaderText = newValue
            }
        }
        .onChange(of: serverURLString) { _, newValue in
            onEvent(.updateOnboardingServerURL(newValue))
        }
        .onChange(of: displayName) { _, newValue in
            onEvent(.updateOnboardingDisplayName(newValue))
        }
        .onChange(of: password) { _, newValue in
            onEvent(.updateOnboardingPassword(newValue))
        }
        .onChange(of: customHeaderText) { _, newValue in
            onEvent(.updateOnboardingCustomHeaders(newValue))
        }
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
        VStack(spacing: 0) {
            Spacer(minLength: 24)

            ZStack {
                RadialGradient(
                    colors: [
                        HermexUIColors.gold.opacity(0.55),
                        Color(red: 1.0, green: 0.62, blue: 0.08).opacity(0.22),
                        Color.clear
                    ],
                    center: .center,
                    startRadius: 8,
                    endRadius: 180
                )
                .frame(width: 320, height: 320)
                .blur(radius: 18)

                RadialGradient(
                    colors: [
                        Color(red: 1.0, green: 0.78, blue: 0.18).opacity(0.35),
                        Color.clear
                    ],
                    center: .center,
                    startRadius: 4,
                    endRadius: 116
                )
                .frame(width: 224, height: 224)

                HermexAppIconMark(size: 124)
            }

            Spacer(minLength: 32)

            VStack(alignment: .leading, spacing: 12) {
                Text(welcomeHeadline)
                    .font(.system(size: 31, weight: .bold))
                    .foregroundStyle(Color.white)
                    .lineLimit(3)
                    .minimumScaleFactor(0.86)
                    .fixedSize(horizontal: false, vertical: true)

                Text("Connect to your self-hosted Web UI over Tailscale.")
                    .font(.subheadline)
                    .foregroundStyle(Color.white.opacity(0.58))
                    .fixedSize(horizontal: false, vertical: true)

                HStack(spacing: 8) {
                    heroBadge("Password protected")
                    heroBadge("Tailscale ready")
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 28)
            .padding(.bottom, 16)
        }
    }

    private var featuresPage: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 28) {
                centeredHeader(
                    title: "What you get",
                    subtitle: featuresSubtitle
                )

                featureRow(
                    title: chatFeatureTitle,
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
        ScrollView(.vertical, showsIndicators: false) {
            VStack(alignment: .leading, spacing: 22) {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Connect")
                        .font(.title3.weight(.bold))
                    Text(connectPageSubtitle)
                        .font(.footnote)
                        .foregroundStyle(Color.white.opacity(0.5))
                        .fixedSize(horizontal: false, vertical: true)
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
            VStack(spacing: 12) {
                onboardingField(
                    systemImage: "link",
                    title: "Server URL",
                    onTap: { focusedField = .serverURL }
                ) {
                    ZStack(alignment: .leading) {
                        if serverURLString.isEmpty {
                            Text(verbatim: "http://100.64.0.1:8787")
                                .foregroundStyle(Color.white.opacity(0.38))
                                .allowsHitTesting(false)
                        }

                        TextField("", text: $serverURLString)
                            .font(.body.weight(.medium))
                            .foregroundStyle(Color.white)
                            .frame(minHeight: 44, alignment: .leading)
                            .hermexURLInputTraits()
                            .submitLabel(.go)
                            .tint(Color(red: 1.0, green: 0.74, blue: 0.10))
                            .focused($focusedField, equals: HermexOnboardingConnectField.serverURL)
                            .onSubmit {
                                submitConnection()
                            }
                    }
                }

                onboardingField(
                    systemImage: "key.fill",
                    title: "Password",
                    onTap: { focusedField = .password }
                ) {
                    ZStack(alignment: .leading) {
                        if password.isEmpty {
                            Text("Server password")
                                .foregroundStyle(Color.white.opacity(0.38))
                                .allowsHitTesting(false)
                        }
                        SecureField("", text: $password)
                            .font(.body.weight(.medium))
                            .foregroundStyle(Color.white)
                            .frame(minHeight: 44, alignment: .leading)
                            .textContentType(.password)
                            .submitLabel(.go)
                            .focused($focusedField, equals: HermexOnboardingConnectField.password)
                            .onSubmit {
                                submitConnection()
                            }
                    }
                }

                DisclosureGroup(
                    isExpanded: $isShowingAdvanced,
                    content: {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Custom headers")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(Color.white.opacity(0.5))

                            TextEditor(text: $customHeaderText)
                                .font(.footnote.monospaced())
                                .foregroundStyle(Color.white)
                                .frame(minHeight: 74)
                                .padding(10)
                                .background(Color.black.opacity(0.24), in: RoundedRectangle(cornerRadius: 8, style: .continuous))
                                .overlay {
                                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                                        .stroke(Color.white.opacity(0.08), lineWidth: 1)
                                }
                        }
                        .padding(.top, 10)
                    },
                    label: {
#if SKIP
                        Text("Advanced")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(Color.white.opacity(0.85))
#else
                        Label("Advanced", systemImage: HermexSystemImageName("slider.horizontal.3"))
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(Color.white.opacity(0.85))
#endif
                    }
                )
                .tint(Color.white.opacity(0.6))
            }
            .padding(14)
        }
    }

    private var bottomBar: some View {
        VStack(spacing: 14) {
            pageIndicator

            if currentPage == Self.connectPageIndex {
#if SKIP
                // PresentationRoot already applies imePadding, so keep the actions in
                // normal layout flow while the Android keyboard is visible.
                connectActionButtons
#else
                if !isEditingConnectionField {
                    connectActionButtons
                }
#endif
            } else {
                Button {
                    handlePrimaryAction()
                } label: {
                    Text(primaryButtonTitle)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.black)
                        .lineLimit(1)
                        .minimumScaleFactor(0.78)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 15)
                        .background(HermexUIColors.gold, in: RoundedRectangle(cornerRadius: 8, style: .continuous))
                }
                .buttonStyle(.plain)

                if currentPage < Self.connectPageIndex {
                    Button("Already have a server?") {
                        currentPage = Self.connectPageIndex
                    }
                    .font(.footnote.weight(.medium))
                    .foregroundStyle(Color.white.opacity(0.55))
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(.horizontal, 24)
        .padding(.top, 12)
        .padding(.bottom, 12)
        .background(
            LinearGradient(
                colors: [
                    Color.clear,
                    Color.black.opacity(0.6),
                    Color.black
                ],
                startPoint: .top,
                endPoint: .bottom
            )
        )
    }

    private var connectActionButtons: some View {
#if SKIP
        GeometryReader { proxy in
            let buttonWidth = max(CGFloat(0), (proxy.size.width - 10) / 2)
            HStack(spacing: 10) {
                connectionButton(
                    title: onboarding.isTestingConnection ? "Checking..." : "Test Connection",
                    systemImage: "network",
                    isPrimary: false,
                    isDisabled: onboarding.isTestingConnection || onboarding.isSigningIn || !canSubmitConnection
                ) {
                    onEvent(.testOnboardingConnectionDraft(
                        serverURLString: serverURLString,
                        displayName: displayName,
                        password: password,
                        customHeaderText: customHeaderText
                    ))
                }
                .frame(width: buttonWidth)

                connectionButton(
                    title: onboarding.isSigningIn ? "Connecting..." : "Connect",
                    systemImage: "checkmark.circle.fill",
                    isPrimary: true,
                    isDisabled: onboarding.isTestingConnection || onboarding.isSigningIn || !canSubmitConnection
                ) {
                    submitConnection()
                }
                .frame(width: buttonWidth)
            }
        }
        .frame(height: 52)
#else
        HStack(spacing: 10) {
            connectionButton(
                title: onboarding.isTestingConnection ? "Checking..." : "Test Connection",
                systemImage: "network",
                isPrimary: false,
                isDisabled: onboarding.isTestingConnection || onboarding.isSigningIn || !canSubmitConnection
            ) {
                onEvent(.testOnboardingConnectionDraft(
                    serverURLString: serverURLString,
                    displayName: displayName,
                    password: password,
                    customHeaderText: customHeaderText
                ))
            }

            connectionButton(
                title: onboarding.isSigningIn ? "Connecting..." : "Connect",
                systemImage: "checkmark.circle.fill",
                isPrimary: true,
                isDisabled: onboarding.isTestingConnection || onboarding.isSigningIn || !canSubmitConnection
            ) {
                submitConnection()
            }
        }
#endif
    }

    private func connectionButton(
        title: String,
        systemImage: String,
        isPrimary: Bool,
        isDisabled: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            connectionButtonLabel(title: title, systemImage: systemImage)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(isPrimary ? Color.black : Color.white.opacity(0.84))
                .lineLimit(1)
                .minimumScaleFactor(0.78)
                .frame(maxWidth: .infinity)
                .padding(.horizontal, 10)
                .padding(.vertical, 15)
                .background(
                    isPrimary ? HermexUIColors.gold : Color.white.opacity(0.065),
                    in: RoundedRectangle(cornerRadius: 8, style: .continuous)
                )
                .overlay {
                    if !isPrimary {
                        RoundedRectangle(cornerRadius: 8, style: .continuous)
                            .stroke(Color.white.opacity(0.1), lineWidth: 1)
                    }
                }
        }
        .buttonStyle(.plain)
        .disabled(isDisabled)
        .opacity(isDisabled ? 0.55 : 1.0)
    }

    @ViewBuilder
    private func connectionButtonLabel(title: String, systemImage: String) -> some View {
#if SKIP
        Text(title)
#else
        Label(title, systemImage: HermexSystemImageName(systemImage))
#endif
    }

    private var keyboardActionBar: some View {
        VStack(spacing: 10) {
            connectActionButtons
        }
        .padding(.horizontal, 22)
        .padding(.top, 10)
        .padding(.bottom, 8)
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
        if onboarding.isTestingConnection || onboarding.isSigningIn {
            onboardingStatusBanner(
                text: onboarding.isSigningIn ? "Connecting..." : "Checking server...",
                systemImage: "arrow.triangle.2.circlepath",
                tint: Color.white.opacity(0.7),
                showsProgress: true
            )
        } else if let error = onboarding.errorMessage {
            onboardingStatusBanner(
                text: error,
                systemImage: "exclamationmark.triangle.fill",
                tint: Color(red: 1.0, green: 0.47, blue: 0.34)
            )
        } else if let status = onboarding.statusMessage {
            onboardingStatusBanner(
                text: status,
                systemImage: "checkmark.circle.fill",
                tint: Color(red: 0.45, green: 0.92, blue: 0.56)
            )
        }
    }

    private func onboardingStatusBanner(
        text: String,
        systemImage: String,
        tint: Color,
        showsProgress: Bool = false
    ) -> some View {
        HStack(alignment: .center, spacing: 10) {
            if showsProgress {
                ProgressView()
                    .tint(tint)
            } else {
                Image(systemName: HermexSystemImageName(systemImage))
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(tint)
                    .frame(width: 20)
            }

            Text(text)
                .font(.footnote.weight(.medium))
                .foregroundStyle(Color.white.opacity(0.72))
                .fixedSize(horizontal: false, vertical: true)

            Spacer(minLength: 0)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 11)
        .background(Color.white.opacity(0.06), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .stroke(tint.opacity(0.28), lineWidth: 1)
        }
    }

    private func heroBadge(_ title: String) -> some View {
        Text(title)
            .font(.caption.weight(.medium))
            .foregroundStyle(Color.white.opacity(0.68))
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

    private func onboardingField<Content: View>(
        systemImage: String,
        title: String,
        onTap: (() -> Void)? = nil,
        @ViewBuilder content: () -> Content
    ) -> some View {
        HStack(spacing: 12) {
            Image(systemName: HermexSystemImageName(systemImage))
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(Color(red: 1.0, green: 0.74, blue: 0.10))
                .frame(width: 24)
                .accessibilityHidden(true)

            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(Color.white.opacity(0.5))

                content()
            }
        }
        .padding(.horizontal, 13)
        .padding(.vertical, 12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .contentShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
        .simultaneousGesture(
            TapGesture().onEnded {
                onTap?()
            }
        )
        .background(Color.black.opacity(0.24), in: RoundedRectangle(cornerRadius: 8, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 8, style: .continuous)
                .stroke(Color.white.opacity(0.08), lineWidth: 1)
        }
    }

    private func handlePrimaryAction() {
        if currentPage == 2 && !hasCopiedAgentPrompt && !hasBypassedCopyReminder {
            isShowingCopyReminder = true
            return
        }
        advance()
    }

    private func handleSwipe(_ translation: CGSize) {
        guard focusedField == nil else { return }

        let horizontalDistance = abs(translation.width)
        let verticalDistance = abs(translation.height)
        guard horizontalDistance >= 56, horizontalDistance > verticalDistance * 1.2 else { return }

        if translation.width < 0 {
            guard currentPage < Self.connectPageIndex else { return }
            handlePrimaryAction()
        } else if currentPage > 0 {
            currentPage -= 1
        }
    }

    private func advance() {
        if currentPage < Self.connectPageIndex {
            currentPage += 1
            if currentPage != Self.connectPageIndex {
                focusedField = nil
            }
        } else {
            submitConnection()
        }
    }

    private func submitConnection() {
        guard canSubmitConnection else { return }
        onEvent(.connectOnboardingDraft(
            serverURLString: serverURLString,
            displayName: displayName,
            password: password,
            customHeaderText: customHeaderText
        ))
    }

    private var welcomeHeadline: String {
#if SKIP
        "Control your Hermes agent from Android."
#else
        "Control your Hermes agent from iPhone."
#endif
    }

    private var featuresSubtitle: String {
#if SKIP
        "Your Hermes agent, reachable from Android over Tailscale."
#else
        "Your Hermes agent, reachable from iPhone over Tailscale."
#endif
    }

    private var chatFeatureTitle: String {
#if SKIP
        "Chat with your Hermes agent from Android"
#else
        "Chat with your Hermes agent from iPhone"
#endif
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

    private var connectPageSubtitle: String {
        switch appState.auth {
        case .unconfigured:
            return "Enter the Tailscale URL your agent returned, for example `http://<tailnet-ip>:8787`."
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
Reply with the exact server URL, the password, and any setup steps still needed on my Android phone.
Do not use Cloudflare. Optimize for Tailscale + Android.
"""
}

private extension View {
    @ViewBuilder
    func hermexURLInputTraits() -> some View {
#if canImport(UIKit)
        self
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
            .keyboardType(.URL)
#else
        self
            .autocorrectionDisabled()
#endif
    }

    @ViewBuilder
    func hermexOnboardingKeyboardInset<Inset: View>(
        isVisible: Bool,
        @ViewBuilder inset: () -> Inset
    ) -> some View {
#if SKIP
        // PresentationRoot reserves the IME area for every screen. An overlay here
        // can cover the focused field and cause the Android form to jump.
        self
#else
        self.safeAreaInset(edge: .bottom, spacing: 0) {
            if isVisible {
                inset()
            }
        }
#endif
    }
}
