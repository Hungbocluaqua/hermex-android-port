import SwiftUI
import HermexCore

public struct HermexSessionListScreen: View {
    private static let searchChromeIconVisualSize: CGFloat = 36
    private static let searchChromeIconHitTarget: CGFloat = 44
    private static let projectColorPalette = [
        "#7cb9ff", "#f5c542", "#e94560", "#50c878",
        "#c084fc", "#fb923c", "#67e8f9", "#f472b6"
    ]

    private struct ProjectEditorState: Equatable {
        var projectID: String?
        var sessionID: String?
        var name: String
        var color: String?
    }

    private let state: HermexSessionListState
    private let settings: HermexSettingsState
    private let onEvent: (HermexUIEvent) -> Void
    @State private var searchChromeIsExpanded: Bool
    @State private var searchText: String
    @State private var projectsAreExpanded = false
    @State private var projectPickerSessionID: String?
    @State private var projectEditor: ProjectEditorState?
    @State private var projectEditorError = ""
    @State private var projectPendingDeletionID: String?

    public init(
        state: HermexSessionListState,
        settings: HermexSettingsState = HermexSettingsState(),
        onEvent: @escaping (HermexUIEvent) -> Void = { _ in }
    ) {
        self.state = state
        self.settings = settings
        self.onEvent = onEvent
        _searchChromeIsExpanded = State(initialValue: !state.searchQuery.isEmpty)
        _searchText = State(initialValue: state.searchQuery)
    }

    public var body: some View {
        ZStack(alignment: .bottomTrailing) {
            scrollContent

            newSessionButton
            .padding(.trailing, HermexLayoutContract.sessionListFloatingButtonTrailing)
            .padding(.bottom, HermexLayoutContract.sessionListFloatingButtonBottom)

            if let sessionID = projectPickerSessionID {
                projectPickerOverlay(sessionID: sessionID)
            }

            if projectEditor != nil {
                projectEditorOverlay
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(HermexUIColors.systemBackground.ignoresSafeArea())
        .foregroundStyle(HermexUIColors.primaryText)
        .alert("Delete Project?", isPresented: projectDeletionBinding) {
            Button("Cancel", role: .cancel) {
                projectPendingDeletionID = nil
            }
            Button("Delete", role: .destructive) {
                if let projectID = projectPendingDeletionID {
                    onEvent(.projectCommand(.delete(projectID: projectID)))
                }
                projectPendingDeletionID = nil
            }
        } message: {
            Text("Sessions in this project will be moved to No project. The sessions themselves will not be deleted.")
        }
    }

    private var scrollContent: some View {
        ScrollView {
#if SKIP
            VStack(alignment: .leading, spacing: 0) {
                scrollStackContent
            }
#else
            LazyVStack(alignment: .leading, spacing: 0) {
                scrollStackContent
            }
#endif
        }
    }

    @ViewBuilder
    private var scrollStackContent: some View {
        header
            .padding(.horizontal, HermexLayoutContract.sessionListHorizontalPadding)
            .padding(.top, HermexLayoutContract.sessionListTopPadding)
            .padding(.bottom, HermexLayoutContract.sessionListTopChromeBottomPadding)

        if state.isViewingCachedData {
            statusRow(
                title: "Cached data",
                description: "Showing locally cached sessions.",
                systemImage: "wifi.slash"
            )
            .padding(.horizontal, HermexLayoutContract.sessionListHorizontalPadding)
            .padding(.bottom, HermexLayoutContract.sessionListUtilityRowSpacing)
        }

        sidebarUtilitySection

        if !state.searchQuery.isEmpty || state.isShowingArchived {
            statusRows
                .padding(.horizontal, HermexLayoutContract.sessionListHorizontalPadding)
                .padding(.top, 8)
        }

        sessionsSectionHeaderBlock

        sessionContent

        Color.clear
            .frame(height: HermexLayoutContract.sessionListBottomSpacerHeight)
            .accessibilityHidden(true)
    }

    private var header: some View {
#if SKIP
        HStack(alignment: .center, spacing: 16.0) {
            if !searchChromeIsExpanded {
                HermexLogoMark(accent: HermexUIColors.color(for: settingsColorHex))
            }
            Spacer(minLength: 12)
            searchChrome
                .frame(maxWidth: .infinity, alignment: .trailing)
        }
#else
        HStack(alignment: .center, spacing: searchChromeIsExpanded ? 0.0 : 16.0) {
            HermexLogoMark(accent: HermexUIColors.color(for: settingsColorHex))
                .frame(width: searchChromeIsExpanded ? 0.0 : HermexLayoutContract.sessionListLogoWidth, alignment: .leading)
                .opacity(searchChromeIsExpanded ? 0.0 : 1.0)
                .clipped()
            Spacer(minLength: 12)
            searchChrome
                .frame(maxWidth: .infinity, alignment: .trailing)
        }
#endif
    }

    private var newSessionButton: some View {
        Button {
            onEvent(.newChat)
        } label: {
            HStack(spacing: 10) {
                Image(systemName: HermexSystemImageName("square.and.pencil"))
                    .font(.title3.weight(.semibold))

                Text("Chat")
                    .font(.headline.weight(.semibold))
            }
            .foregroundStyle(Color.black)
            .padding(.horizontal, 22)
            .frame(height: HermexLayoutContract.sessionListFloatingButtonHeight)
            .background(Color.white, in: Capsule())
            .shadow(
                color: .black.opacity(HermexLayoutContract.sessionListFloatingButtonShadowOpacity),
                radius: HermexLayoutContract.sessionListFloatingButtonShadowRadius,
                y: HermexLayoutContract.sessionListFloatingButtonShadowYOffset
            )
        }
        .buttonStyle(.plain)
        .accessibilityLabel("New Session")
    }

    private var searchChrome: some View {
        HStack(spacing: searchChromeIsExpanded ? 8.0 : 4.0) {
            Button {
                if searchChromeIsExpanded {
                    submitSearch()
                } else {
                    searchChromeIsExpanded = true
                }
            } label: {
                Image(systemName: HermexSystemImageName("magnifyingglass"))
                    .font(.system(size: 22, weight: .semibold))
                    .foregroundStyle(searchChromeIsExpanded ? HermexUIColors.secondaryText : HermexUIColors.primaryText)
                    .frame(
                        width: Self.searchChromeIconVisualSize,
                        height: Self.searchChromeIconVisualSize
                    )
                    .frame(
                        width: Self.searchChromeIconHitTarget,
                        height: Self.searchChromeIconHitTarget
                    )
            }
            .buttonStyle(.plain)
            .accessibilityLabel(searchChromeIsExpanded ? "Search sessions" : "Open session search")

#if SKIP
            if searchChromeIsExpanded {
                searchTextField
                    .frame(maxWidth: .infinity)
            }
#else
            searchTextField
                .frame(maxWidth: searchChromeIsExpanded ? .infinity : 0.0)
                .opacity(searchChromeIsExpanded ? 1.0 : 0.0)
                .clipped()
#endif

            if searchChromeIsExpanded && !searchText.isEmpty {
                Button {
                    searchText = ""
                    onEvent(.searchSessions(""))
                } label: {
                    Image(systemName: HermexSystemImageName("xmark.circle.fill"))
                        .font(.subheadline)
                        .foregroundStyle(HermexUIColors.secondaryText)
                        .frame(
                            width: Self.searchChromeIconVisualSize,
                            height: Self.searchChromeIconVisualSize
                        )
                        .frame(
                            width: Self.searchChromeIconHitTarget,
                            height: Self.searchChromeIconHitTarget
                        )
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Clear search")
            }

            Button {
                if searchChromeIsExpanded {
                    searchChromeIsExpanded = false
                    searchText = ""
                    onEvent(.searchSessions(""))
                } else {
                    onEvent(.openRoute(.settings))
                }
            } label: {
                ZStack {
                    Text(settingsInitials)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(HermexUIColors.prefersDarkForeground(for: settingsColorHex) ? Color.black : Color.white)
                        .frame(
                            width: Self.searchChromeIconVisualSize,
                            height: Self.searchChromeIconVisualSize
                        )
                        .background(HermexUIColors.color(for: settingsColorHex), in: Circle())
                        .overlay {
                            Circle().stroke(HermexUIColors.hairline, lineWidth: 0.7)
                        }
                        .opacity(searchChromeIsExpanded ? 0.0 : 1.0)

                    Image(systemName: HermexSystemImageName("xmark"))
                        .font(.system(size: 22, weight: .medium))
                        .foregroundStyle(HermexUIColors.primaryText)
                        .frame(
                            width: Self.searchChromeIconVisualSize,
                            height: Self.searchChromeIconVisualSize
                        )
                        .opacity(searchChromeIsExpanded ? 1.0 : 0.0)
                }
                .frame(
                    width: Self.searchChromeIconHitTarget,
                    height: Self.searchChromeIconHitTarget
                )
            }
            .buttonStyle(.plain)
            .accessibilityLabel(searchChromeIsExpanded ? "Close search" : "Settings")
        }
        .padding(.vertical, 2)
        .frame(maxWidth: searchChromeIsExpanded ? .infinity : nil, alignment: .trailing)
        .background(HermexUIColors.glassFill, in: Capsule())
        .overlay {
            Capsule().stroke(HermexUIColors.hairline, lineWidth: 0.6)
        }
        .clipShape(Capsule())
    }

    private var searchTextField: some View {
        TextField("Search sessions", text: $searchText)
            .textFieldStyle(.plain)
            .font(.subheadline)
            .foregroundStyle(HermexUIColors.primaryText)
            .lineLimit(1)
    }

    private var settingsColorHex: String {
        settings.activeServer?.headerLogoColorHex ?? HermexAppearanceSettings.defaultHeaderLogoColorHex
    }

    private var settingsInitials: String {
        let server = settings.activeServer
        return HermexAppearanceSettings.displayInitials(
            displayName: server?.displayName ?? "",
            storedInitials: server?.initials ?? "",
            fallbackFullName: server?.baseURL.host ?? "Hermex"
        )
    }

    private func submitSearch() {
        onEvent(.searchSessions(searchText.trimmingCharacters(in: .whitespacesAndNewlines)))
    }

    private var sessionsSectionHeaderBlock: some View {
        sessionsSectionHeader
            .padding(.horizontal, HermexLayoutContract.sessionListHorizontalPadding)
            .padding(.top, sessionsSectionHeaderTopPadding)
            .padding(.bottom, 12)
    }

    private var sessionsSectionHeaderTopPadding: CGFloat {
        searchChromeIsExpanded ? 16.0 : 28.0
    }

    private var sessionsSectionHeader: some View {
        HStack(spacing: 10) {
            if state.searchQuery.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                Text("Sessions")
                    .font(.title3.bold())
                    .foregroundStyle(HermexUIColors.primaryText)
            }
            Spacer(minLength: 0)
            if state.isLoading {
                ProgressView()
                    .tint(HermexUIColors.secondaryText)
            }
        }
    }

    @ViewBuilder
    private var statusRows: some View {
        VStack(alignment: .leading, spacing: 6) {
            if state.isShowingArchived {
                Text("Archived sessions")
                    .foregroundStyle(HermexUIColors.secondaryText)
            }
            if state.isViewingCachedData {
                Text("Cached data")
                    .foregroundStyle(HermexUIColors.secondaryText)
            }
            if !state.searchQuery.isEmpty {
                Text("Searching \"\(state.searchQuery)\"")
                    .foregroundStyle(HermexUIColors.secondaryText)
            }
        }
        .font(.caption)
        .padding(.vertical, 4)
    }

    private func statusRow(title: String, description: String?, systemImage: String) -> some View {
        HStack(alignment: .top, spacing: 14) {
            Image(systemName: HermexSystemImageName(systemImage))
                .font(.body)
                .foregroundStyle(HermexUIColors.secondaryText)
                .frame(width: 24)
                .accessibilityHidden(true)

            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(HermexUIColors.secondaryText)
                    .lineLimit(2)

                if let description, !description.isEmpty {
                    Text(description)
                        .font(.footnote)
                        .foregroundStyle(HermexUIColors.secondaryText)
                        .lineLimit(2)
                }
            }

            Spacer(minLength: 0)
        }
        .frame(minHeight: 42)
    }

    @ViewBuilder
    private var sessionContent: some View {
        let visibleSessions = filteredSessions

        if state.isLoading {
            HStack(spacing: 10) {
                ProgressView()
                Text("Loading sessions")
                    .foregroundStyle(HermexUIColors.secondaryText)
            }
            .font(.caption)
            .padding(.vertical, 24)
        } else if let errorMessage = state.errorMessage, !errorMessage.isEmpty {
            HermexGlassPanel(cornerRadius: 18) {
                VStack(alignment: .leading, spacing: 10) {
                    Text("Could not load sessions")
                        .font(.headline.weight(.semibold))
                    Text(errorMessage)
                        .font(.caption)
                        .foregroundStyle(HermexUIColors.secondaryText)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(18)
            }
        } else if visibleSessions.isEmpty {
            HermexGlassPanel(cornerRadius: 18) {
                VStack(alignment: .leading, spacing: 10) {
                    Text(state.selectedProjectID == nil ? "No sessions yet" : "No sessions in this project")
                        .font(.headline.weight(.semibold))
                    Text(state.selectedProjectID == nil ? "Tap Chat to start." : "Choose another project or tap Chat to start.")
                        .font(.caption)
                        .foregroundStyle(HermexUIColors.secondaryText)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(18)
            }
        } else {
            VStack(spacing: 0) {
                ForEach(visibleSessions, id: \.id) { session in
                    sessionRow(session)
                        .padding(.horizontal, HermexLayoutContract.sessionListHorizontalPadding)
                        .hermexContentShapeRectangle()
                        .onTapGesture {
                            onEvent(.openSession(session.id))
                        }
                }
            }
        }
    }

    @ViewBuilder
    private var sidebarUtilitySection: some View {
        if !searchChromeIsExpanded {
            utilityRows
                .padding(.horizontal, HermexLayoutContract.sessionListHorizontalPadding)
                .padding(.top, HermexLayoutContract.sessionListUtilityTopPadding)
                .padding(.bottom, HermexLayoutContract.sessionListUtilityRowSpacing)

            projectsSection
                .padding(.horizontal, HermexLayoutContract.sessionListHorizontalPadding)
                .padding(.top, HermexLayoutContract.sessionListUtilityRowSpacing)

            selectorRow(
                icon: "person.crop.circle.badge.gearshape",
                title: state.activeProfileName ?? "default",
                subtitle: "Profile",
                event: .selectProfile
            )
            .padding(.horizontal, HermexLayoutContract.sessionListHorizontalPadding)
            .padding(.top, HermexLayoutContract.sessionListUtilityRowSpacing)

            selectorRow(
                icon: "folder",
                title: primaryWorkspace,
                subtitle: "Workspace",
                event: .selectWorkspace
            )
            .padding(.horizontal, HermexLayoutContract.sessionListHorizontalPadding)
            .padding(.top, HermexLayoutContract.sessionListUtilityRowSpacing)
        }
    }

    private var utilityRows: some View {
        VStack(alignment: .leading, spacing: HermexLayoutContract.sessionListUtilityRowSpacing) {
            sidebarNavigationRow("calendar.badge.clock", "Tasks", .selectPanel(.tasks))
            sidebarNavigationRow("hammer", "Skills", .selectPanel(.skills))
            sidebarNavigationRow("brain.head.profile", "Memory", .selectPanel(.memory))
            sidebarNavigationRow("chart.bar", "Insights", .selectPanel(.insights))
        }
    }

    private var projectsSection: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack(spacing: 8) {
                Button {
                    projectsAreExpanded.toggle()
                } label: {
                    HStack(spacing: 18) {
                        HermexIconView("folder", size: HermexLayoutContract.sessionListUtilityIconSize)
                            .frame(width: HermexLayoutContract.sessionListUtilityIconSlotWidth)

                        Text("Projects")
                            .font(.body.weight(.semibold))

                        Image(systemName: HermexSystemImageName(projectsAreExpanded ? "chevron.down" : "chevron.forward"))
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(HermexUIColors.secondaryText)
                    }
                    .frame(minHeight: HermexLayoutContract.sessionListUtilityRowMinimumHeight)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(projectsAreExpanded ? "Collapse projects" : "Expand projects")

                Spacer(minLength: 0)

                if projectsAreExpanded {
                    Button {
                        beginProjectEditor(sessionID: nil)
                    } label: {
                        Image(systemName: HermexSystemImageName("plus"))
                            .font(.body.weight(.semibold))
                            .frame(width: 36, height: 36)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Add project")
                    .disabled(state.isViewingCachedData)
                }

                if state.selectedProjectID != nil {
                    Button {
                        onEvent(.selectProject(nil))
                    } label: {
                        Text("All")
                            .font(.footnote.weight(.medium))
                            .padding(.horizontal, 10)
                            .frame(minHeight: 32)
                            .background(HermexUIColors.glassFillStrong, in: Capsule())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Show all projects")
                }
            }

            if projectsAreExpanded {
                if state.projects.isEmpty {
                    Text("No projects")
                        .font(.footnote)
                        .foregroundStyle(HermexUIColors.secondaryText)
                        .padding(.leading, HermexLayoutContract.sessionListUtilityIconSlotWidth + 18)
                        .padding(.vertical, 8)
                } else {
                    ForEach(state.projects, id: \.id) { project in
                        projectRow(project)
                    }
                }
            }
        }
    }

    private func projectRow(_ project: HermexProjectDTO) -> some View {
        let projectID = project.id
        let isSelected = state.selectedProjectID == projectID
        let count = state.sessions.filter { $0.projectId == projectID }.count

        return HStack(spacing: 6) {
            Button {
                onEvent(.selectProject(isSelected ? nil : projectID))
            } label: {
                HStack(spacing: 12) {
                    Circle()
                        .fill(projectColor(project.color))
                        .frame(width: 8, height: 8)

                    Text(project.name ?? "Untitled project")
                        .font(.body)
                        .lineLimit(1)

                    Spacer(minLength: 0)

                    if count > 0 {
                        Text("\(count)")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(HermexUIColors.secondaryText)
                    }

                    if isSelected {
                        Image(systemName: HermexSystemImageName("checkmark"))
                            .font(.caption.weight(.bold))
                            .foregroundStyle(HermexUIColors.gold)
                    }
                }
                .padding(.leading, HermexLayoutContract.sessionListUtilityIconSlotWidth + 18)
                .frame(minHeight: 40, alignment: .leading)
            }
            .buttonStyle(.plain)
            .disabled(state.isViewingCachedData)

            Menu {
                Button {
                    beginProjectEditor(project: project)
                } label: {
                    Label("Rename Project", systemImage: HermexSystemImageName("pencil"))
                }
                .disabled(state.isViewingCachedData)

                Button(role: .destructive) {
                    projectPendingDeletionID = projectID
                } label: {
                    Label("Delete Project", systemImage: HermexSystemImageName("trash"))
                }
                .disabled(state.isViewingCachedData)
            } label: {
                Image(systemName: HermexSystemImageName("ellipsis"))
                    .font(.body.weight(.semibold))
                    .frame(width: 36, height: 36)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Project actions")
        }
    }

    private func sidebarNavigationRow(_ systemImage: String, _ title: String, _ event: HermexUIEvent) -> some View {
        Button {
            onEvent(event)
        } label: {
            HStack(spacing: 18) {
                HermexIconView(systemImage, size: HermexLayoutContract.sessionListUtilityIconSize)
                    .frame(width: HermexLayoutContract.sessionListUtilityIconSlotWidth)
                    .foregroundStyle(HermexUIColors.primaryText)
                    .accessibilityHidden(true)

                Text(title)
                    .font(.body.weight(.semibold))
                    .foregroundStyle(HermexUIColors.primaryText)
                    .lineLimit(1)

                Spacer(minLength: 0)
            }
            .frame(minHeight: HermexLayoutContract.sessionListUtilityRowMinimumHeight)
            .hermexContentShapeRectangle()
        }
        .buttonStyle(.plain)
        .accessibilityLabel(title)
    }

    private func selectorRow(icon: String, title: String, subtitle: String, event: HermexUIEvent) -> some View {
        Button {
            onEvent(event)
        } label: {
            HStack(alignment: .center, spacing: 18) {
                HermexIconView(icon, size: HermexLayoutContract.sessionListUtilityIconSize)
                    .frame(width: HermexLayoutContract.sessionListUtilityIconSlotWidth)
                    .accessibilityHidden(true)

                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.body.weight(.semibold))
                        .foregroundStyle(HermexUIColors.primaryText)
                        .lineLimit(1)
                    Text(subtitle)
                        .font(.footnote)
                        .foregroundStyle(HermexUIColors.secondaryText)
                }

                Spacer(minLength: 0)

                Image(systemName: HermexSystemImageName("chevron.forward"))
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(HermexUIColors.secondaryText)
                    .frame(width: 24, height: 40)
                    .accessibilityHidden(true)
            }
            .frame(maxWidth: .infinity, minHeight: HermexLayoutContract.sessionListSelectorHeight, alignment: .leading)
            .hermexContentShapeRectangle()
        }
        .buttonStyle(.plain)
    }

    private func sessionRow(_ session: HermexSessionDTO) -> some View {
        let metadata = sessionMetadata(session)
        let hasSupplemental = !metadata.isEmpty
        let title = session.title ?? "Untitled Session"
        let relative = relativeDate(session)

        return HStack(alignment: .center, spacing: HermexLayoutContract.sessionRowHorizontalSpacing) {
            VStack(alignment: .leading, spacing: HermexLayoutContract.sessionRowContentSpacing) {
#if SKIP
                Text(title)
                    .font(.headline.weight(.semibold))
                    .foregroundStyle(HermexUIColors.primaryText)
                    .lineLimit(2)

                HStack(alignment: .center, spacing: HermexLayoutContract.sessionRowMetadataSpacing) {
                    if hasSupplemental {
                        Text(metadata)
                            .font(.caption)
                            .foregroundStyle(HermexUIColors.secondaryText)
                            .lineLimit(1)
                    }
                    Spacer(minLength: 0)
                    if let relative {
                        Text(relative)
                            .font(.caption)
                            .foregroundStyle(HermexUIColors.secondaryText)
                            .lineLimit(1)
                    }
                }
#else
                HStack(alignment: .firstTextBaseline, spacing: HermexLayoutContract.sessionRowTitleDateSpacing) {
                    HStack(alignment: .firstTextBaseline, spacing: HermexLayoutContract.sessionRowTitlePinSpacing) {
                        Text(title)
                            .font(.headline.weight(.semibold))
                            .foregroundStyle(HermexUIColors.primaryText)
                            .lineLimit(2)
                            .truncationMode(.tail)

                        if session.pinned == true {
                            Image(systemName: HermexSystemImageName("pin.fill"))
                                .font(.caption2.weight(.semibold))
                                .foregroundStyle(Color.accentColor)
                        }
                    }
                    .hermexLayoutPriority(2)

                    Spacer(minLength: HermexLayoutContract.sessionRowTitleDateSpacing)

                    if let relative {
                        Text(relative)
                            .font(.caption)
                            .foregroundStyle(HermexUIColors.secondaryText)
                            .lineLimit(1)
                    }
                }

                if hasSupplemental {
                    HStack(alignment: .firstTextBaseline, spacing: HermexLayoutContract.sessionRowMetadataSpacing) {
                        Text(metadata)
                            .font(.caption)
                            .foregroundStyle(HermexUIColors.secondaryText)
                            .lineLimit(1)
                            .truncationMode(.middle)
                    }
                }
#endif
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            Button {
                projectPickerSessionID = session.id
            } label: {
                Image(systemName: HermexSystemImageName("ellipsis"))
                    .font(.headline.weight(.semibold))
                    .frame(
                        width: HermexLayoutContract.sessionListRowActionSize,
                        height: HermexLayoutContract.sessionListRowActionSize
                    )
                    .foregroundStyle(HermexUIColors.primaryText)
                    .background(HermexUIColors.glassFillStrong, in: Circle())
                    .overlay {
                        Circle().stroke(HermexUIColors.hairline, lineWidth: 0.6)
                    }
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Session actions")
        }
        .padding(.horizontal, HermexLayoutContract.sessionRowHorizontalPadding)
        .padding(.vertical, HermexLayoutContract.sessionRowVerticalPadding)
        .frame(
            minHeight: hasSupplemental
                ? HermexLayoutContract.sessionRowSupplementalMinimumHeight
                : HermexLayoutContract.sessionRowMinimumHeight,
            alignment: .center
        )
        .overlay(alignment: .bottom) {
            Rectangle()
                .fill(HermexUIColors.faintHairline)
                .frame(height: 0.6)
        }
        .hermexContentShapeRectangle()
    }

    private var filteredSessions: [HermexSessionDTO] {
        let query = state.searchQuery.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        return state.sessions.filter { session in
            if let selectedProjectID = state.selectedProjectID,
               session.projectId != selectedProjectID {
                return false
            }
            guard !query.isEmpty else { return true }
            return [session.title, session.workspace, session.sessionId]
                .compactMap { $0?.lowercased() }
                .contains { $0.contains(query) }
        }
    }

    private var projectDeletionBinding: Binding<Bool> {
        Binding(
            get: { projectPendingDeletionID != nil },
            set: { isPresented in
                if !isPresented {
                    projectPendingDeletionID = nil
                }
            }
        )
    }

    private func projectPickerOverlay(sessionID: String) -> some View {
        ZStack {
            Color.black.opacity(0.52)
                .ignoresSafeArea()

            HermexGlassPanel(cornerRadius: 18) {
                VStack(alignment: .leading, spacing: 12) {
                    HStack {
                        Text("Move to Project")
                            .font(.title3.weight(.bold))
                        Spacer()
                        Button {
                            projectPickerSessionID = nil
                        } label: {
                            Image(systemName: HermexSystemImageName("xmark"))
                                .frame(width: 36, height: 36)
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Close project picker")
                    }

                    projectPickerRow(title: "No project", color: HermexUIColors.secondaryText) {
                        onEvent(.projectCommand(.moveSession(sessionID: sessionID, projectID: nil)))
                        projectPickerSessionID = nil
                    }

                    ForEach(state.projects, id: \.id) { project in
                        projectPickerRow(title: project.name ?? "Untitled project", color: projectColor(project.color)) {
                            onEvent(.projectCommand(.moveSession(sessionID: sessionID, projectID: project.id)))
                            projectPickerSessionID = nil
                        }
                    }

                    Button {
                        projectPickerSessionID = nil
                        beginProjectEditor(sessionID: sessionID)
                    } label: {
                        Label("New Project", systemImage: HermexSystemImageName("plus"))
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .buttonStyle(.plain)
                    .disabled(state.isViewingCachedData)
                }
                .padding(18)
            }
            .padding(16)
        }
    }

    private func projectPickerRow(title: String, color: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Circle()
                    .fill(color)
                    .frame(width: 9, height: 9)
                Text(title)
                    .font(.body)
                Spacer(minLength: 0)
                Image(systemName: HermexSystemImageName("chevron.forward"))
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(HermexUIColors.secondaryText)
            }
            .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private var projectEditorOverlay: some View {
        if let editor = projectEditor {
            ZStack {
                Color.black.opacity(0.52)
                    .ignoresSafeArea()

                ScrollView {
                    HermexGlassPanel(cornerRadius: 18) {
                        VStack(alignment: .leading, spacing: 16) {
                            HStack {
                                Text(editor.projectID == nil ? "New Project" : "Rename Project")
                                    .font(.title2.weight(.bold))
                                Spacer()
                                Button {
                                    projectEditor = nil
                                    projectEditorError = ""
                                } label: {
                                    Image(systemName: HermexSystemImageName("xmark"))
                                        .frame(width: 36, height: 36)
                                }
                                .buttonStyle(.plain)
                                .accessibilityLabel("Cancel project editor")
                            }

                            TextField("Project name", text: projectNameBinding)
                                .textFieldStyle(.plain)
                                .foregroundStyle(HermexUIColors.primaryText)
                                .padding(.horizontal, 14)
                                .padding(.vertical, 12)
                                .background(HermexUIColors.glassFillStrong, in: RoundedRectangle(cornerRadius: 10, style: .continuous))

                            Text("Color")
                                .font(.caption.weight(.bold))
                                .foregroundStyle(HermexUIColors.secondaryText)

                            HStack(spacing: 10) {
                                ForEach(Self.projectColorPalette, id: \.self) { color in
                                    Button {
                                        updateProjectColor(color)
                                    } label: {
                                        Circle()
                                            .fill(projectColor(color))
                                            .frame(width: 24, height: 24)
                                            .overlay {
                                                if editor.color == color {
                                                    Circle().stroke(HermexUIColors.primaryText, lineWidth: 2)
                                                }
                                            }
                                    }
                                    .buttonStyle(.plain)
                                    .accessibilityLabel("Choose project color")
                                }
                            }

                            if !projectEditorError.isEmpty {
                                Text(projectEditorError)
                                    .font(.footnote)
                                    .foregroundStyle(Color.orange)
                            }

                            HStack {
                                Spacer()
                                Button(editor.projectID == nil ? "Create" : "Save") {
                                    saveProjectEditor()
                                }
                                .font(.headline.weight(.semibold))
                                .disabled(editor.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                            }
                        }
                        .padding(18)
                    }
                    .padding(16)
                }
            }
        }
    }

    private var projectNameBinding: Binding<String> {
        Binding(
            get: { projectEditor?.name ?? "" },
            set: { value in
                projectEditor?.name = value
                projectEditorError = ""
            }
        )
    }

    private func beginProjectEditor(project: HermexProjectDTO? = nil, sessionID: String? = nil) {
        projectEditorError = ""
        projectEditor = ProjectEditorState(
            projectID: project?.id,
            sessionID: sessionID,
            name: project?.name ?? "",
            color: project?.color ?? Self.projectColorPalette.first
        )
    }

    private func updateProjectColor(_ color: String) {
        guard var editor = projectEditor else { return }
        editor.color = color
        projectEditor = editor
    }

    private func saveProjectEditor() {
        guard let editor = projectEditor else { return }
        let name = editor.name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !name.isEmpty else {
            projectEditorError = "Enter a project name."
            return
        }
        if let projectID = editor.projectID {
            onEvent(.projectCommand(.rename(projectID: projectID, name: name, color: editor.color)))
        } else {
            onEvent(.projectCommand(.create(name: name, color: editor.color, moveSessionID: editor.sessionID)))
        }
        projectEditor = nil
        projectEditorError = ""
    }

    private func projectColor(_ hex: String?) -> Color {
        switch hex?.lowercased() {
        case "#7cb9ff": return Color(red: 0.49, green: 0.73, blue: 1.0)
        case "#f5c542": return Color(red: 0.96, green: 0.77, blue: 0.26)
        case "#e94560": return Color(red: 0.91, green: 0.27, blue: 0.38)
        case "#50c878": return Color(red: 0.31, green: 0.78, blue: 0.47)
        case "#c084fc": return Color(red: 0.75, green: 0.52, blue: 0.99)
        case "#fb923c": return Color(red: 0.98, green: 0.57, blue: 0.24)
        case "#67e8f9": return Color(red: 0.40, green: 0.91, blue: 0.98)
        case "#f472b6": return Color(red: 0.96, green: 0.45, blue: 0.71)
        default: return HermexUIColors.secondaryText
        }
    }

    private func sessionMetadata(_ session: HermexSessionDTO) -> String {
        let count = session.messageCount.map { "\($0) messages" }
        return [count, session.workspace].compactMap { $0 }.joined(separator: " • ")
    }

    private var primaryWorkspace: String {
        state.sessions.first(where: { $0.workspace?.isEmpty == false })?.workspace ?? "workspace"
    }

    private func relativeDate(_ session: HermexSessionDTO) -> String? {
        guard let timestamp = session.lastMessageAt ?? session.updatedAt ?? session.createdAt else { return nil }
        let seconds = max(0.0, Date().timeIntervalSince1970 - timestamp)
        if seconds < 60 { return "now" }
        if seconds < 3_600 { return "\(Int(seconds / 60))m ago" }
        if seconds < 86_400 { return "\(Int(seconds / 3_600))h ago" }
        return "\(Int(seconds / 86_400))d ago"
    }
}
