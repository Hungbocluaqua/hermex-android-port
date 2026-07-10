import SwiftUI
import HermexCore

public struct HermexSettingsScreen: View {
    private let state: HermexSettingsState
    private let onEvent: (HermexUIEvent) -> Void
    @State private var isEditingServer = false
    @State private var serverDisplayNameDraft: String
    @State private var serverInitialsDraft: String
    @State private var serverHeaderLogoColorDraft: String
    @State private var serverCustomHeaderTextDraft: String
    @State private var defaultModelSearchText = ""
    @State private var defaultProfileSearchText = ""
    @State private var customDefaultModelDraft = ""

    public init(state: HermexSettingsState, onEvent: @escaping (HermexUIEvent) -> Void = { _ in }) {
        self.state = state
        self.onEvent = onEvent
        _serverDisplayNameDraft = State(initialValue: state.activeServer?.displayName ?? "")
        _serverInitialsDraft = State(initialValue: state.activeServer?.initials ?? "")
        _serverHeaderLogoColorDraft = State(initialValue: state.activeServer?.headerLogoColorHex ?? HermexAppearanceSettings.defaultHeaderLogoColorHex)
        _serverCustomHeaderTextDraft = State(initialValue: Self.headerText(for: state.activeServer))
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
                    sessionsSection
                    securitySection
                    signOutButton
                }
                .padding(.horizontal, 16)
                .padding(.top, 12)
                .padding(.bottom, 32)
            }

            if isEditingServer {
                serverEditorOverlay
            }
            if state.showDefaultModelPicker == true {
                defaultModelPickerOverlay
            }
            if state.showDefaultProfilePicker == true {
                defaultProfilePickerOverlay
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
                VStack(alignment: .leading, spacing: 12) {
                    HStack(alignment: .top, spacing: 12) {
                        serverAvatar(server)

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
                                HermexMappedLabel("\(server.customHeaders.count) custom headers", systemImage: "key.horizontal")
                                    .font(.caption.weight(.medium))
                                    .foregroundStyle(HermexUIColors.secondaryText)
                            }
                        }
                    }

                    Button {
                        beginServerEditing(server)
                    } label: {
                        HermexMappedLabel("Edit Server", systemImage: "pencil")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(HermexUIColors.primaryText)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.horizontal, 12)
                            .frame(minHeight: 42)
                            .background(HermexUIColors.glassFillStrong, in: RoundedRectangle(cornerRadius: 11, style: .continuous))
                            .overlay {
                                RoundedRectangle(cornerRadius: 11, style: .continuous)
                                    .stroke(HermexUIColors.hairline, lineWidth: 0.7)
                            }
                    }
                    .buttonStyle(.plain)
                    .disabled(state.isSavingServer)
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
                HermexMappedLabel("Add Server", systemImage: "plus")
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
            Button {
                defaultModelSearchText = ""
                customDefaultModelDraft = ""
                onEvent(.openDefaultModelPicker)
            } label: {
                settingValueRow(systemImage: "cpu", title: "Default Model", value: state.defaultModel ?? "Server default")
            }
            .buttonStyle(.plain)

            Button {
                defaultProfileSearchText = ""
                onEvent(.openDefaultProfilePicker)
            } label: {
                settingValueRow(systemImage: "person.crop.circle", title: "Default Profile", value: state.defaultProfile ?? "Server default")
            }
            .buttonStyle(.plain)

            settingValueRow(systemImage: "bell", title: "Notifications", value: state.notificationsEnabled ? "On" : "Off")
        }
    }

    private var sessionsSection: some View {
        settingsSection("Sessions") {
            Toggle(
                "CLI Sessions",
                isOn: Binding(
                    get: { state.showCliSessions },
                    set: { onEvent(.updateShowCliSessions($0)) }
                )
            )
            .tint(.blue)
            .disabled(state.isSavingShowCliSessions)

            if state.isSavingShowCliSessions {
                HStack(spacing: 8) {
                    ProgressView()
                    Text("Saving...")
                }
                .font(.caption)
                .foregroundStyle(HermexUIColors.secondaryText)
            } else if let errorMessage = state.settingsErrorMessage {
                Text(errorMessage)
                    .font(.caption)
                    .foregroundStyle(Color.red.opacity(0.9))
            } else {
                Text("CLI session visibility is synced with this server, so the WebUI follows it too.")
                    .font(.caption)
                    .foregroundStyle(HermexUIColors.secondaryText)
            }
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
                Image(systemName: HermexSystemImageName("rectangle.portrait.and.arrow.right"))
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
                serverAvatar(server)

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
                    Image(systemName: HermexSystemImageName("chevron.right"))
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
            Image(systemName: HermexSystemImageName(systemImage))
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

    private var defaultModelPickerOverlay: some View {
        ZStack {
            Color.black.opacity(0.52)
                .ignoresSafeArea()

            ScrollView {
                HermexGlassPanel(cornerRadius: 18) {
                    VStack(alignment: .leading, spacing: 14) {
                        pickerHeader("Default Model") {
                            onEvent(.dismissDefaultModelPicker)
                        }

                        pickerTextField("Search models", text: $defaultModelSearchText)

                        VStack(alignment: .leading, spacing: 8) {
                            Text("Custom")
                                .font(.caption.weight(.bold))
                                .textCase(.uppercase)
                                .foregroundStyle(HermexUIColors.secondaryText)

                            pickerTextField("Custom model ID", text: $customDefaultModelDraft)

                            Text("Type a model ID exactly as the server expects it.")
                                .font(.caption)
                                .foregroundStyle(HermexUIColors.secondaryText)

                            settingsEditorButton("Save Custom Model", isProminent: true) {
                                onEvent(.chooseDefaultModel(customDefaultModelDraft))
                            }
                            .disabled(customDefaultModelDraft.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines).isEmpty || state.isSavingDefaultConfiguration == true)
                        }

                        if state.isLoadingDefaultConfiguration == true {
                            HStack(spacing: 8) {
                                ProgressView()
                                Text("Loading models...")
                            }
                            .font(.caption)
                            .foregroundStyle(HermexUIColors.secondaryText)
                        }

                        if let errorMessage = state.settingsErrorMessage, !errorMessage.isEmpty {
                            Text(errorMessage)
                                .font(.caption)
                                .foregroundStyle(Color.red.opacity(0.9))
                        }

                        if filteredDefaultModels.isEmpty && state.isLoadingDefaultConfiguration != true {
                            Text("No matching models.")
                                .font(.subheadline)
                                .foregroundStyle(HermexUIColors.secondaryText)
                        } else {
                            VStack(spacing: 0) {
                                ForEach(filteredDefaultModels) { model in
                                    Button {
                                        onEvent(.chooseDefaultModel(model.id))
                                    } label: {
                                        HStack(spacing: 10) {
                                            VStack(alignment: .leading, spacing: 3) {
                                                Text(model.displayName)
                                                    .font(.subheadline.weight(.medium))
                                                    .foregroundStyle(HermexUIColors.primaryText)
                                                    .lineLimit(2)
                                                if model.id != model.displayName {
                                                    Text(model.id)
                                                        .font(.caption)
                                                        .foregroundStyle(HermexUIColors.secondaryText)
                                                        .lineLimit(2)
                                                }
                                                if let provider = model.provider, !provider.isEmpty {
                                                    Text(provider)
                                                        .font(.caption2)
                                                        .foregroundStyle(HermexUIColors.tertiaryText)
                                                }
                                            }
                                            Spacer(minLength: 8)
                                            if state.defaultModel == model.id {
                                                Text("Selected")
                                                    .font(.caption.weight(.semibold))
                                                    .foregroundStyle(HermexUIColors.gold)
                                            }
                                        }
                                        .padding(.vertical, 10)
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                    }
                                    .buttonStyle(.plain)
                                    .disabled(state.isSavingDefaultConfiguration == true)
                                }
                            }
                        }
                    }
                    .padding(18)
                }
                .padding(16)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var defaultProfilePickerOverlay: some View {
        ZStack {
            Color.black.opacity(0.52)
                .ignoresSafeArea()

            ScrollView {
                HermexGlassPanel(cornerRadius: 18) {
                    VStack(alignment: .leading, spacing: 14) {
                        pickerHeader("Default Profile") {
                            onEvent(.dismissDefaultProfilePicker)
                        }

                        pickerTextField("Search profiles", text: $defaultProfileSearchText)

                        if state.isSingleProfileMode == true {
                            Text("This server is running in single-profile mode.")
                                .font(.caption)
                                .foregroundStyle(HermexUIColors.secondaryText)
                        }

                        if state.isLoadingDefaultConfiguration == true {
                            HStack(spacing: 8) {
                                ProgressView()
                                Text("Loading profiles...")
                            }
                            .font(.caption)
                            .foregroundStyle(HermexUIColors.secondaryText)
                        }

                        if let errorMessage = state.settingsErrorMessage, !errorMessage.isEmpty {
                            Text(errorMessage)
                                .font(.caption)
                                .foregroundStyle(Color.red.opacity(0.9))
                        }

                        if filteredDefaultProfiles.isEmpty && state.isLoadingDefaultConfiguration != true {
                            Text("No matching profiles.")
                                .font(.subheadline)
                                .foregroundStyle(HermexUIColors.secondaryText)
                        } else {
                            VStack(spacing: 0) {
                                ForEach(filteredDefaultProfiles) { profile in
                                    Button {
                                        onEvent(.chooseDefaultProfile(profile.name))
                                    } label: {
                                        HStack(spacing: 10) {
                                            VStack(alignment: .leading, spacing: 3) {
                                                Text(profile.title)
                                                    .font(.subheadline.weight(.medium))
                                                    .foregroundStyle(HermexUIColors.primaryText)
                                                    .lineLimit(2)
                                                if let details = profileDetails(profile) {
                                                    Text(details)
                                                        .font(.caption)
                                                        .foregroundStyle(HermexUIColors.secondaryText)
                                                        .lineLimit(2)
                                                }
                                            }
                                            Spacer(minLength: 8)
                                            if state.defaultProfile == profile.name {
                                                Text("Selected")
                                                    .font(.caption.weight(.semibold))
                                                    .foregroundStyle(HermexUIColors.gold)
                                            } else if profile.isDefault == true {
                                                Text("Server Default")
                                                    .font(.caption)
                                                    .foregroundStyle(HermexUIColors.secondaryText)
                                            }
                                        }
                                        .padding(.vertical, 10)
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                    }
                                    .buttonStyle(.plain)
                                    .disabled(state.isSavingDefaultConfiguration == true)
                                }
                            }
                        }
                    }
                    .padding(18)
                }
                .padding(16)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func pickerHeader(_ title: String, onClose: @escaping () -> Void) -> some View {
        HStack(spacing: 10) {
            Text(title)
                .font(.title2.weight(.bold))
                .foregroundStyle(HermexUIColors.primaryText)
            Spacer(minLength: 8)
            HermexCircleIconButton(
                systemImage: "xmark",
                accessibilityLabel: "Close",
                size: 34,
                action: onClose
            )
        }
    }

    private func pickerTextField(_ placeholder: String, text: Binding<String>) -> some View {
        TextField(placeholder, text: text)
            .textFieldStyle(.plain)
            .foregroundStyle(HermexUIColors.primaryText)
            .padding(.horizontal, 14)
            .frame(minHeight: 44)
            .background(HermexUIColors.glassFillStrong, in: RoundedRectangle(cornerRadius: 11, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 11, style: .continuous)
                    .stroke(HermexUIColors.hairline, lineWidth: 0.7)
            }
    }

    private var filteredDefaultModels: [HermexModelOption] {
        let models = state.availableModels ?? []
        let query = defaultModelSearchText.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines).lowercased()
        guard !query.isEmpty else { return models }
        return models.filter { model in
            model.id.lowercased().contains(query)
                || model.displayName.lowercased().contains(query)
                || (model.provider?.lowercased().contains(query) ?? false)
        }
    }

    private var filteredDefaultProfiles: [HermexProfileOption] {
        let profiles = state.availableProfiles ?? []
        let query = defaultProfileSearchText.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines).lowercased()
        guard !query.isEmpty else { return profiles }
        return profiles.filter { profile in
            profile.name.lowercased().contains(query)
                || profile.title.lowercased().contains(query)
                || (profile.model?.lowercased().contains(query) ?? false)
                || (profile.provider?.lowercased().contains(query) ?? false)
        }
    }

    private func profileDetails(_ profile: HermexProfileOption) -> String? {
        var details: [String] = []
        if let model = profile.model, !model.isEmpty { details.append(model) }
        if let provider = profile.provider, !provider.isEmpty { details.append(provider) }
        return details.isEmpty ? nil : details.joined(separator: " / ")
    }

    private var serverEditorOverlay: some View {
        ZStack {
            Color.black.opacity(0.52)
                .ignoresSafeArea()

            ScrollView {
                HermexGlassPanel(cornerRadius: 18) {
                    VStack(alignment: .leading, spacing: 14) {
                        HStack {
                            Text("Edit Server")
                                .font(.title2.weight(.bold))
                                .foregroundStyle(HermexUIColors.primaryText)
                            Spacer()
                            statusPill("Secure", tint: .green)
                        }

                        TextField("Display name", text: $serverDisplayNameDraft)
                            .textFieldStyle(.plain)
                            .foregroundStyle(HermexUIColors.primaryText)
                            .padding(.horizontal, 14)
                            .frame(minHeight: 50)
                            .background(HermexUIColors.glassFillStrong, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                            .overlay {
                                RoundedRectangle(cornerRadius: 12, style: .continuous)
                                    .stroke(HermexUIColors.hairline, lineWidth: 0.7)
                            }

                        HStack(spacing: 10) {
                            TextField("Initials", text: $serverInitialsDraft)
                                .textFieldStyle(.plain)
                                .foregroundStyle(HermexUIColors.primaryText)
                                .textCase(.uppercase)
                                .padding(.horizontal, 14)
                                .frame(minHeight: 50)
                                .background(HermexUIColors.glassFillStrong, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                                .overlay {
                                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                                        .stroke(HermexUIColors.hairline, lineWidth: 0.7)
                                }

                            Text("\(HermexAppearanceSettings.displayInitials(displayName: serverDisplayNameDraft, storedInitials: serverInitialsDraft, fallbackFullName: state.activeServer?.baseURL.host ?? "Hermex"))")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(HermexUIColors.prefersDarkForeground(for: serverHeaderLogoColorDraft) ? Color.black : Color.white)
                                .frame(width: 42, height: 42)
                                .background(HermexUIColors.color(for: serverHeaderLogoColorDraft), in: Circle())
                                .overlay {
                                    Circle().stroke(HermexUIColors.hairline, lineWidth: 0.7)
                                }
                        }

                        headerLogoColorEditor

                        Text("Custom headers")
                            .font(.caption.weight(.bold))
                            .textCase(.uppercase)
                            .foregroundStyle(HermexUIColors.secondaryText)

                        TextEditor(text: $serverCustomHeaderTextDraft)
                            .font(.footnote.monospaced())
                            .foregroundStyle(HermexUIColors.primaryText)
                            .frame(minHeight: 110)
                            .padding(10)
                            .background(HermexUIColors.glassFillStrong, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                            .overlay {
                                RoundedRectangle(cornerRadius: 12, style: .continuous)
                                    .stroke(HermexUIColors.hairline, lineWidth: 0.7)
                            }

                        Text("One header per line. Invalid or restricted headers are ignored.")
                            .font(.caption)
                            .foregroundStyle(HermexUIColors.secondaryText)

                        HStack(spacing: 8) {
                            settingsEditorButton("Cancel") {
                                isEditingServer = false
                            }
                            settingsEditorButton("Save", isProminent: true) {
                                onEvent(.updateActiveServer(
                                    displayName: serverDisplayNameDraft,
                                    initials: serverInitialsDraft,
                                    headerLogoColorHex: serverHeaderLogoColorDraft,
                                    customHeaderText: serverCustomHeaderTextDraft
                                ))
                                isEditingServer = false
                            }
                        }
                    }
                    .padding(18)
                }
                .padding(16)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func settingsEditorButton(
        _ title: String,
        isProminent: Bool = false,
        action: @escaping () -> Void
    ) -> some View {
        Button(title, action: action)
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(isProminent ? HermexUIColors.primaryText : HermexUIColors.secondaryText)
            .frame(maxWidth: .infinity)
            .frame(minHeight: 44)
            .background(
                isProminent ? HermexUIColors.gold : HermexUIColors.glassFillStrong,
                in: RoundedRectangle(cornerRadius: 11, style: .continuous)
            )
            .overlay {
                RoundedRectangle(cornerRadius: 11, style: .continuous)
                    .stroke(HermexUIColors.hairline, lineWidth: 0.7)
            }
    }

    private func statusPill(_ label: String, tint: Color) -> some View {
        Text(label)
            .font(.caption.weight(.semibold))
            .foregroundStyle(tint)
            .padding(.horizontal, 9)
            .padding(.vertical, 5)
            .background(tint.opacity(0.12), in: Capsule())
    }

    private func serverAvatar(_ server: HermexServerIdentity) -> some View {
        let fallbackName = server.baseURL.host ?? server.baseURL.absoluteString
        let initials = HermexAppearanceSettings.displayInitials(
            displayName: server.displayName,
            storedInitials: server.initials,
            fallbackFullName: fallbackName
        )
        let color = HermexUIColors.color(for: server.headerLogoColorHex)
        return Text(initials)
            .font(.caption.weight(.semibold))
            .foregroundStyle(HermexUIColors.prefersDarkForeground(for: server.headerLogoColorHex) ? Color.black : Color.white)
            .frame(width: 38, height: 38)
            .background(color, in: Circle())
            .overlay {
                Circle().stroke(HermexUIColors.hairline, lineWidth: 0.7)
            }
    }

    private func emptyState(_ text: String, systemImage: String) -> some View {
        HermexMappedLabel(text, systemImage: systemImage)
            .font(.subheadline.weight(.medium))
            .foregroundStyle(HermexUIColors.secondaryText)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(12)
            .background(HermexUIColors.glassFillStrong, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    private func isActive(_ server: HermexServerIdentity) -> Bool {
        state.activeServer?.baseURL == server.baseURL
    }

    private func beginServerEditing(_ server: HermexServerIdentity) {
        serverDisplayNameDraft = server.displayName
        serverInitialsDraft = server.initials
        serverHeaderLogoColorDraft = server.headerLogoColorHex
        serverCustomHeaderTextDraft = Self.headerText(for: server)
        isEditingServer = true
    }

    private var headerLogoColorEditor: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("Header Logo Color")
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(HermexUIColors.primaryText)
                Spacer(minLength: 8)
                Text(HermexAppearanceSettings.headerLogoColorPresets.first {
                    $0.hex == HermexAppearanceSettings.normalizedHex(serverHeaderLogoColorDraft)
                }?.name ?? "Custom")
                    .font(.caption)
                    .foregroundStyle(HermexUIColors.secondaryText)
            }

            HStack(spacing: 10) {
                ForEach(HermexAppearanceSettings.headerLogoColorPresets) { preset in
                    Button {
                        serverHeaderLogoColorDraft = preset.hex
                    } label: {
                        Circle()
                            .fill(HermexUIColors.color(for: preset.hex))
                            .frame(width: 30, height: 30)
                            .overlay {
                                if HermexAppearanceSettings.normalizedHex(serverHeaderLogoColorDraft) == preset.hex {
                                    Circle().stroke(HermexUIColors.primaryText, lineWidth: 2.0)
                                } else {
                                    Circle().stroke(HermexUIColors.hairline, lineWidth: 0.7)
                                }
                            }
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(preset.name)
                }
            }

            TextField("Hex color", text: $serverHeaderLogoColorDraft)
                .textFieldStyle(.plain)
                .foregroundStyle(HermexUIColors.primaryText)
                .font(.footnote.monospaced())
                .padding(.horizontal, 14)
                .frame(minHeight: 44)
                .background(HermexUIColors.glassFillStrong, in: RoundedRectangle(cornerRadius: 11, style: .continuous))
                .overlay {
                    RoundedRectangle(cornerRadius: 11, style: .continuous)
                        .stroke(HermexUIColors.hairline, lineWidth: 0.7)
                }
        }
    }

    private static func headerText(for server: HermexServerIdentity?) -> String {
        guard let server else { return "" }
        return server.customHeaders.keys.sorted {
            $0.localizedCaseInsensitiveCompare($1) == ComparisonResult.orderedAscending
        }.map { key in
            "\(key): \(server.customHeaders[key] ?? "")"
        }.joined(separator: "\n")
    }
}
