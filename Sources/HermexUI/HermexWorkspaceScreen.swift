import SwiftUI
import HermexCore

public struct HermexWorkspaceScreen: View {
    private let state: HermexWorkspaceState
    private let onEvent: (HermexUIEvent) -> Void

    public init(state: HermexWorkspaceState, onEvent: @escaping (HermexUIEvent) -> Void = { _ in }) {
        self.state = state
        self.onEvent = onEvent
    }

    public var body: some View {
        ZStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    topBar
                    rootScroller
                    directoryCard

                    if let preview = state.preview {
                        previewPanel(preview)
                    }
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
            HermexCircleIconButton(systemImage: "chevron.left", accessibilityLabel: "Back", action: { onEvent(.openRoute(.chat)) })
            HermexScreenTitle("Workspace", subtitle: state.currentPath ?? "Files")
            Spacer()
            HermexIconCluster {
                HermexCircleIconButton(systemImage: "arrow.up.doc", accessibilityLabel: "Parent Folder", action: { openParentFolder() })
                HermexCircleIconButton(systemImage: "arrow.clockwise", accessibilityLabel: "Refresh", action: { onEvent(.refresh) })
            }
        }
    }

    @ViewBuilder
    private var rootScroller: some View {
        if !state.roots.isEmpty {
            VStack(alignment: .leading, spacing: 10) {
                Text("Roots")
                    .font(.caption.weight(.bold))
                    .textCase(.uppercase)
                    .foregroundStyle(HermexUIColors.secondaryText)

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(state.roots) { root in
                            Button {
                                onEvent(.openWorkspaceEntry(HermexWorkspaceEntryDTO(
                                    name: root.name ?? root.path,
                                    path: root.path,
                                    type: "dir",
                                    isDirectory: true
                                )))
                            } label: {
                                HermexPillHermexMappedLabel(root.name ?? root.path, systemImage: "folder")
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
        }
    }

    private var directoryCard: some View {
        HermexGlassPanel(cornerRadius: 18) {
            VStack(alignment: .leading, spacing: 12) {
                HStack(alignment: .firstTextBaseline, spacing: 10) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(currentDirectoryTitle)
                            .font(.title3.weight(.semibold))
                            .foregroundStyle(HermexUIColors.primaryText)
                            .lineLimit(1)

                        Text(directorySubtitle)
                            .font(.caption)
                            .foregroundStyle(HermexUIColors.secondaryText)
                            .lineLimit(1)
                    }

                    Spacer()

                    statusPill("\(state.entries.count) items")
                }

                if state.isLoading {
                    ProgressView()
                }

                if let error = state.errorMessage, !error.isEmpty {
                    emptyState(error, systemImage: "exclamationmark.triangle")
                } else if state.entries.isEmpty && !state.isLoading {
                    emptyState("No files to show.", systemImage: "folder")
                } else {
                    LazyVStack(alignment: .leading, spacing: 0) {
                        ForEach(state.entries) { entry in
                            Button {
                                onEvent(.openWorkspaceEntry(entry))
                            } label: {
                                workspaceRow(entry)
                            }
                            .buttonStyle(.plain)

                            if entry.id != state.entries.last?.id {
                                Divider()
                                    .background(HermexUIColors.hairline)
                                    .padding(.leading, 50)
                            }
                        }
                    }
                }
            }
            .padding(16)
        }
    }

    private func workspaceRow(_ entry: HermexWorkspaceEntryDTO) -> some View {
        HStack(spacing: 12) {
            Image(systemName: HermexSystemImageName(entry.isDirectory ? "folder" : fileIcon(for: entry)))
                .font(.title3.weight(.semibold))
                .foregroundStyle(HermexUIColors.primaryText)
                .frame(width: 38, height: 38)
                .background(HermexUIColors.glassFillStrong, in: Circle())

            VStack(alignment: .leading, spacing: 4) {
                Text(entry.name)
                    .font(.body.weight(.semibold))
                    .foregroundStyle(HermexUIColors.primaryText)
                    .lineLimit(1)

                Text(entry.path)
                    .font(.caption)
                    .foregroundStyle(HermexUIColors.secondaryText)
                    .lineLimit(1)
            }

            Spacer(minLength: 8)

            if entry.isDirectory {
                Image(systemName: HermexSystemImageName("chevron.right"))
                    .font(.caption.weight(.bold))
                    .foregroundStyle(HermexUIColors.secondaryText)
            } else if let size = entry.size {
                Text(formattedSize(size))
                    .font(.caption.weight(.medium))
                    .foregroundStyle(HermexUIColors.secondaryText)
            }
        }
        .padding(.vertical, 11)
        .hermexContentShapeRectangle()
    }

    private func previewPanel(_ preview: HermexFilePreview) -> some View {
        HermexGlassPanel(cornerRadius: 18) {
            VStack(alignment: .leading, spacing: 12) {
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Image(systemName: HermexSystemImageName(preview.isBinary ? "doc.badge.gearshape" : "doc.text"))
                        .foregroundStyle(HermexUIColors.primaryText)
                    Text(preview.path)
                        .font(.headline.weight(.semibold))
                        .foregroundStyle(HermexUIColors.primaryText)
                        .lineLimit(1)
                    Spacer()
                    if let mimeType = preview.mimeType, !mimeType.isEmpty {
                        statusPill(mimeType)
                    }
                }

                Text(preview.content ?? preview.mimeType ?? "Binary file")
                    .font(.system(.body, design: .monospaced))
                    .foregroundStyle(HermexUIColors.primaryText)
                    .lineLimit(22)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(12)
                    .background(HermexUIColors.glassFillStrong, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
            .padding(16)
        }
    }

    private var currentDirectoryTitle: String {
        let path = state.currentPath?.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let path, !path.isEmpty else { return "Files" }
        return path.replacingOccurrences(of: "\\", with: "/").split(separator: "/").last.map { component in
            String(component)
        } ?? path
    }

    private var directorySubtitle: String {
        state.currentPath ?? "Workspace root"
    }

    private func openParentFolder() {
        guard let currentPath = state.currentPath?.trimmingCharacters(in: .whitespacesAndNewlines), !currentPath.isEmpty else {
            onEvent(.refresh)
            return
        }
        let normalized = currentPath.replacingOccurrences(of: "\\", with: "/")
        let parent = normalized.split(separator: "/").dropLast().joined(separator: "/")
        let path = parent.isEmpty ? "/" : parent
        onEvent(.openWorkspaceEntry(HermexWorkspaceEntryDTO(name: path, path: path, type: "dir", isDirectory: true)))
    }

    private func emptyState(_ text: String, systemImage: String) -> some View {
        HermexMappedLabel(text, systemImage: systemImage)
            .font(.subheadline.weight(.medium))
            .foregroundStyle(HermexUIColors.secondaryText)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(12)
            .background(HermexUIColors.glassFillStrong, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    private func statusPill(_ value: String) -> some View {
        Text(value)
            .font(.caption.weight(.semibold))
            .foregroundStyle(HermexUIColors.secondaryText)
            .padding(.horizontal, 9)
            .padding(.vertical, 5)
            .background(HermexUIColors.glassFillStrong, in: Capsule())
    }

    private func fileIcon(for entry: HermexWorkspaceEntryDTO) -> String {
        let path = entry.path.lowercased()
        if path.hasSuffix(".swift") || path.hasSuffix(".kt") || path.hasSuffix(".py") || path.hasSuffix(".js") || path.hasSuffix(".ts") {
            return "chevron.left.forwardslash.chevron.right"
        }
        if path.hasSuffix(".png") || path.hasSuffix(".jpg") || path.hasSuffix(".jpeg") || path.hasSuffix(".webp") {
            return "photo"
        }
        return "doc.text"
    }

    private func formattedSize(_ bytes: Int) -> String {
        if bytes < 1024 { return "\(bytes) B" }
        if bytes < 1024 * 1024 { return "\(bytes / 1024) KB" }
        return "\(bytes / (1024 * 1024)) MB"
    }
}
