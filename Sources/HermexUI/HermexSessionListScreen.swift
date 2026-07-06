import SwiftUI
import HermexCore

public struct HermexSessionListScreen: View {
    private static let searchChromeIconVisualSize: CGFloat = 36
    private static let searchChromeIconHitTarget: CGFloat = 44

    private let state: HermexSessionListState
    private let onEvent: (HermexUIEvent) -> Void
    @State private var searchChromeIsExpanded: Bool
    @State private var searchText: String

    public init(state: HermexSessionListState, onEvent: @escaping (HermexUIEvent) -> Void = { _ in }) {
        self.state = state
        self.onEvent = onEvent
        _searchChromeIsExpanded = State(initialValue: !state.searchQuery.isEmpty)
        _searchText = State(initialValue: state.searchQuery)
    }

    public var body: some View {
        ZStack(alignment: .bottomTrailing) {
            HermexUIColors.systemBackground.ignoresSafeArea()

            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    header

                    HStack(alignment: .top, spacing: 20) {
                        utilityRail

                        VStack(alignment: .leading, spacing: 12) {
                            selectorRow(
                                icon: "person.crop.circle.badge.gearshape",
                                title: state.activeProfileName ?? "default",
                                subtitle: "Profile",
                                event: .selectProfile
                            )
                            selectorRow(
                                icon: "folder",
                                title: primaryWorkspace,
                                subtitle: "Workspace",
                                event: .selectWorkspace
                            )

                            if !state.searchQuery.isEmpty || state.isShowingArchived || state.isViewingCachedData {
                                statusRows
                            }

                            sessionContent
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
                .padding(.horizontal, HermexLayoutContract.sessionListHorizontalPadding)
                .padding(.top, HermexLayoutContract.sessionListTopPadding)
                .padding(.bottom, 112)
            }

            Button {
                onEvent(.newChat)
            } label: {
                Label("Chat", systemImage: "square.and.pencil")
                    .font(.headline.weight(.semibold))
                    .padding(.horizontal, 24)
                    .padding(.vertical, 16)
                    .background(Color.white, in: Capsule())
                    .foregroundStyle(Color.black)
                    .shadow(
                        color: .black.opacity(HermexLayoutContract.sessionListFloatingButtonShadowOpacity),
                        radius: HermexLayoutContract.sessionListFloatingButtonShadowRadius,
                        y: HermexLayoutContract.sessionListFloatingButtonShadowYOffset
                    )
            }
            .padding(.trailing, HermexLayoutContract.sessionListFloatingButtonTrailing)
            .padding(.bottom, HermexLayoutContract.sessionListFloatingButtonBottom)
        }
        .foregroundStyle(HermexUIColors.primaryText)
    }

    private var header: some View {
        HStack(alignment: .center, spacing: searchChromeIsExpanded ? 0.0 : 16.0) {
            HermexLogoMark()
                .frame(width: searchChromeIsExpanded ? 0.0 : HermexLayoutContract.sessionListLogoWidth, alignment: .leading)
                .opacity(searchChromeIsExpanded ? 0.0 : 1.0)
                .clipped()
            Spacer(minLength: 12)
            searchChrome
                .frame(maxWidth: .infinity, alignment: .trailing)
        }
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
                Image(systemName: "magnifyingglass")
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

            TextField("Search sessions", text: $searchText)
                .textFieldStyle(.plain)
                .font(.subheadline)
                .foregroundStyle(HermexUIColors.primaryText)
                .lineLimit(1)
                .frame(maxWidth: searchChromeIsExpanded ? .infinity : 0.0)
                .opacity(searchChromeIsExpanded ? 1.0 : 0.0)
                .clipped()

            if searchChromeIsExpanded && !searchText.isEmpty {
                Button {
                    searchText = ""
                    onEvent(.searchSessions(""))
                } label: {
                    Image(systemName: "xmark.circle.fill")
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
                    Image(systemName: "gearshape.fill")
                        .font(.system(size: 22, weight: .semibold))
                        .foregroundStyle(Color.black)
                        .frame(
                            width: Self.searchChromeIconVisualSize,
                            height: Self.searchChromeIconVisualSize
                        )
                        .background(HermexUIColors.gold, in: Circle())
                        .opacity(searchChromeIsExpanded ? 0.0 : 1.0)

                    Image(systemName: "xmark")
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

    private func submitSearch() {
        onEvent(.searchSessions(searchText.trimmingCharacters(in: .whitespacesAndNewlines)))
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

    @ViewBuilder
    private var sessionContent: some View {
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
        } else if state.sessions.isEmpty {
            HermexGlassPanel(cornerRadius: 18) {
                VStack(alignment: .leading, spacing: 10) {
                    Text("No sessions yet")
                        .font(.headline.weight(.semibold))
                    Text("Start a new chat from this server.")
                        .font(.caption)
                        .foregroundStyle(HermexUIColors.secondaryText)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(18)
            }
        } else {
            LazyVStack(spacing: 0) {
                ForEach(state.sessions) { session in
                    sessionRow(session)
                        .hermexContentShapeRectangle()
                        .onTapGesture {
                            onEvent(.openSession(session.id))
                        }
                }
            }
        }
    }

    private var utilityRail: some View {
        VStack(spacing: 24) {
            railButton("calendar.badge.clock", "Tasks", .selectPanel(.tasks))
            railButton("hammer", "Skills", .selectPanel(.skills))
            railButton("brain.head.profile", "Memory", .selectPanel(.memory))
            railButton("chart.bar", "Insights", .selectPanel(.insights))
        }
        .frame(width: HermexLayoutContract.sessionListUtilityRailWidth)
    }

    private func railButton(_ systemImage: String, _ label: String, _ event: HermexUIEvent) -> some View {
        Button {
            onEvent(event)
        } label: {
            Image(systemName: systemImage)
                .font(.system(size: 23, weight: .semibold))
                .frame(
                    width: HermexLayoutContract.sessionListUtilityIconSize,
                    height: HermexLayoutContract.sessionListUtilityIconSize
                )
                .foregroundStyle(HermexUIColors.primaryText)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }

    private func selectorRow(icon: String, title: String, subtitle: String, event: HermexUIEvent) -> some View {
        Button {
            onEvent(event)
        } label: {
            HStack(spacing: 16) {
                Image(systemName: icon)
                    .font(.system(size: 26, weight: .semibold))
                    .frame(width: 34)
                VStack(alignment: .leading, spacing: 3) {
                    Text(title)
                        .font(.title3.weight(.medium))
                        .foregroundStyle(HermexUIColors.primaryText)
                        .lineLimit(1)
                    Text(subtitle)
                        .font(.caption)
                        .foregroundStyle(HermexUIColors.secondaryText)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.headline.weight(.semibold))
                    .foregroundStyle(HermexUIColors.secondaryText)
            }
            .frame(minHeight: HermexLayoutContract.sessionListSelectorHeight)
            .hermexContentShapeRectangle()
        }
        .buttonStyle(.plain)
    }

    private func sessionRow(_ session: HermexSessionDTO) -> some View {
        let metadata = sessionMetadata(session)
        let hasSupplemental = !metadata.isEmpty

        return HStack(alignment: .center, spacing: HermexLayoutContract.sessionRowHorizontalSpacing) {
            VStack(alignment: .leading, spacing: HermexLayoutContract.sessionRowContentSpacing) {
                HStack(alignment: .firstTextBaseline, spacing: HermexLayoutContract.sessionRowTitleDateSpacing) {
                    HStack(alignment: .firstTextBaseline, spacing: HermexLayoutContract.sessionRowTitlePinSpacing) {
                        Text(session.title ?? "Untitled Session")
                            .font(.headline.weight(.semibold))
                            .foregroundStyle(HermexUIColors.primaryText)
                            .lineLimit(2)
                            .truncationMode(.tail)

                        if session.pinned == true {
                            Image(systemName: "pin.fill")
                                .font(.caption2.weight(.semibold))
                                .foregroundStyle(Color.accentColor)
                        }
                    }
                    .hermexLayoutPriority(2)

                    Spacer(minLength: HermexLayoutContract.sessionRowTitleDateSpacing)

                    if let relativeDate = relativeDate(session) {
                        Text(relativeDate)
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
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            Button {
                onEvent(.selectProject(session.projectId))
            } label: {
                Image(systemName: "ellipsis")
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
