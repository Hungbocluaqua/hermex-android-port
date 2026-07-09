import Foundation
import SwiftUI
import HermexCore

public struct HermexGitScreen: View {
    private let state: HermexGitState
    private let onEvent: (HermexUIEvent) -> Void

    public init(state: HermexGitState, onEvent: @escaping (HermexUIEvent) -> Void = { _ in }) {
        self.state = state
        self.onEvent = onEvent
    }

    public var body: some View {
        ZStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    topBar
                    repositoryCard
                    commitBox
                    changesList

                    if let diffText = state.diffText {
                        diffPanel(diffText)
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
            HermexScreenTitle("Git", subtitle: state.branch ?? "Branch unavailable")
            Spacer()
            HermexCircleIconButton(systemImage: "arrow.clockwise", accessibilityLabel: "Refresh", action: { onEvent(.refresh) })
        }
    }

    private var repositoryCard: some View {
        HermexGlassPanel(cornerRadius: 18) {
            VStack(alignment: .leading, spacing: 14) {
                HStack(alignment: .top, spacing: 12) {
                    Image(systemName: HermexSystemImageName(state.isRepository ? "point.3.connected.trianglepath.dotted" : "exclamationmark.triangle"))
                        .font(.title3.weight(.semibold))
                        .foregroundStyle(HermexUIColors.primaryText)
                        .frame(width: 42, height: 42)
                        .background(HermexUIColors.glassFillStrong, in: Circle())

                    VStack(alignment: .leading, spacing: 6) {
                        Text(state.isRepository ? "Repository" : "Not a repository")
                            .font(.title3.weight(.semibold))
                            .foregroundStyle(HermexUIColors.primaryText)
                        Text(state.upstream ?? "No upstream configured")
                            .font(.caption)
                            .foregroundStyle(HermexUIColors.secondaryText)
                            .lineLimit(2)
                    }

                    Spacer(minLength: 8)
                    statusPill(state.branch ?? "No branch", tint: .blue)
                }

                HStack(spacing: 8) {
                    metricPill("Ahead", state.ahead ?? 0, tint: .green)
                    metricPill("Behind", state.behind ?? 0, tint: .orange)
                    metricPill("Files", state.files.count, tint: .blue)
                }

                remoteActionBar

                if let error = state.errorMessage, !error.isEmpty {
                    notice(error, systemImage: "exclamationmark.circle", tint: .red)
                }
            }
            .padding(16)
        }
    }

    private var remoteActionBar: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                gitActionButton("Fetch", systemImage: "arrow.down.circle", command: .fetch)
                gitActionButton("Pull", systemImage: "arrow.down", command: .pull)
                gitActionButton("Push", systemImage: "arrow.up", command: .push)
            }
        }
        .disabled(state.isMutating)
    }

    private var commitBox: some View {
        HermexGlassPanel(cornerRadius: 18) {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 8) {
                    Image(systemName: HermexSystemImageName("checkmark.seal"))
                        .foregroundStyle(HermexUIColors.primaryText)
                    Text("Commit")
                        .font(.headline.weight(.semibold))
                        .foregroundStyle(HermexUIColors.primaryText)
                    Spacer()
                    statusPill(stagedSummary, tint: .green)
                }

                TextField(
                    "Commit message",
                    text: Binding(
                        get: { state.commitMessage },
                        set: { onEvent(.updateGitCommitMessage($0)) }
                    )
                )
                .textFieldStyle(.plain)
                .font(.body)
                .foregroundStyle(HermexUIColors.primaryText)
                .padding(.horizontal, 12)
                .frame(minHeight: 48)
                .background(HermexUIColors.glassFillStrong, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                .overlay {
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .stroke(HermexUIColors.hairline, lineWidth: 0.7)
                }

                Button {
                    onEvent(.gitCommand(.commit(message: state.commitMessage)))
                } label: {
                    HermexMappedLabel("Commit", systemImage: "checkmark")
                        .font(.headline.weight(.semibold))
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 48)
                        .background(Color.blue, in: Capsule())
                }
                .buttonStyle(.plain)
                .disabled(state.commitMessage.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines).isEmpty || state.isMutating)
                .opacity(state.commitMessage.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines).isEmpty || state.isMutating ? 0.48 : 1.0)
            }
            .padding(16)
        }
    }

    private var changesList: some View {
        HermexGlassPanel(cornerRadius: 18) {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Text("Changes")
                        .font(.headline.weight(.semibold))
                        .foregroundStyle(HermexUIColors.primaryText)
                    Spacer()
                    statusPill("\(state.files.count)", tint: .blue)
                }

                if state.files.isEmpty {
                    notice("Working tree is clean.", systemImage: "checkmark.circle", tint: .green)
                } else {
#if SKIP
                    VStack(alignment: .leading, spacing: 0) {
                        ForEach(state.files) { file in
                            gitFileRow(file)

                            if file.id != state.files.last?.id {
                                Divider()
                                    .background(HermexUIColors.hairline)
                                    .padding(.leading, 54)
                            }
                        }
                    }
#else
                    LazyVStack(alignment: .leading, spacing: 0) {
                        ForEach(state.files) { file in
                            gitFileRow(file)

                            if file.id != state.files.last?.id {
                                Divider()
                                    .background(HermexUIColors.hairline)
                                    .padding(.leading, 54)
                            }
                        }
                    }
#endif
                }
            }
            .padding(16)
        }
    }

    private func gitFileRow(_ file: HermexGitFileChange) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 12) {
                Text(file.status)
                    .font(.system(.caption, design: .monospaced).weight(.semibold))
                    .foregroundStyle(HermexUIColors.primaryText)
                    .frame(width: 42, height: 34)
                    .background(HermexUIColors.glassFillStrong, in: RoundedRectangle(cornerRadius: 10, style: .continuous))

                VStack(alignment: .leading, spacing: 4) {
                    Text(file.path)
                        .font(.body.weight(.semibold))
                        .foregroundStyle(HermexUIColors.primaryText)
                        .lineLimit(1)

                    HStack(spacing: 6) {
                        if file.isStaged == true {
                            statusPill("Staged", tint: .green)
                        } else {
                            statusPill("Unstaged", tint: .orange)
                        }
                        if let additions = file.additions, let deletions = file.deletions {
                            Text("+\(additions) -\(deletions)")
                                .font(.caption.weight(.medium))
                                .foregroundStyle(HermexUIColors.secondaryText)
                        }
                    }
                }

                Spacer(minLength: 8)
            }

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    fileAction("Diff") {
                        onEvent(.gitCommand(.diff(path: file.path, kind: file.isStaged == true ? "staged" : "unstaged")))
                    }
                    if file.isStaged == true {
                        fileAction("Unstage") { onEvent(.gitCommand(.unstage(path: file.path))) }
                    } else {
                        fileAction("Stage") { onEvent(.gitCommand(.stage(path: file.path))) }
                    }
                    fileAction("Discard", role: .destructive) {
                        onEvent(.gitCommand(.discard(path: file.path, deleteUntracked: true)))
                    }
                }
            }
            .disabled(state.isMutating)

            if state.diffPath == file.path, state.diffText != nil {
                Text("Diff open")
                    .font(.caption2.weight(.medium))
                    .foregroundStyle(HermexUIColors.secondaryText)
            }
        }
        .padding(.vertical, 12)
    }

    private func diffPanel(_ text: String) -> some View {
        HermexGlassPanel(cornerRadius: 18) {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Text(state.diffPath ?? "Diff")
                        .font(.headline.weight(.semibold))
                        .foregroundStyle(HermexUIColors.primaryText)
                        .lineLimit(1)
                    Spacer()
                    statusPill("Unified diff", tint: .blue)
                }

                ScrollView(.horizontal, showsIndicators: true) {
                    Text(text)
                        .font(.system(.caption, design: .monospaced))
                        .foregroundStyle(HermexUIColors.primaryText)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(12)
                }
                .background(HermexUIColors.glassFillStrong, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
            .padding(16)
        }
    }

    private var stagedSummary: String {
        let count = state.files.filter { $0.isStaged == true }.count
        return count == 1 ? "1 staged" : "\(count) staged"
    }

    private func gitActionButton(_ title: String, systemImage: String, command: HermexGitCommand) -> some View {
        Button {
            onEvent(.gitCommand(command))
        } label: {
            HermexMappedLabel(title, systemImage: systemImage)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(HermexUIColors.primaryText)
                .padding(.horizontal, 14)
                .frame(height: 42)
                .background(HermexUIColors.glassFillStrong, in: Capsule())
                .overlay {
                    Capsule().stroke(HermexUIColors.hairline, lineWidth: 0.7)
                }
        }
        .buttonStyle(.plain)
    }

    private func fileAction(_ title: String, role: ButtonRole? = nil, action: @escaping () -> Void) -> some View {
        Button(role: role, action: action) {
            Text(title)
                .font(.caption.weight(.semibold))
                .foregroundStyle(role == .destructive ? Color.red.opacity(0.95) : HermexUIColors.primaryText)
                .padding(.horizontal, 12)
                .frame(height: 34)
                .background(HermexUIColors.glassFillStrong, in: Capsule())
                .overlay {
                    Capsule().stroke(role == .destructive ? Color.red.opacity(0.25) : HermexUIColors.hairline, lineWidth: 0.7)
                }
        }
        .buttonStyle(.plain)
    }

    private func metricPill(_ title: String, _ value: Int, tint: Color) -> some View {
        HStack(spacing: 5) {
            Text(title)
                .foregroundStyle(HermexUIColors.secondaryText)
            Text("\(value)")
                .fontWeight(.semibold)
                .foregroundStyle(tint)
        }
        .font(.caption)
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(HermexUIColors.glassFillStrong, in: Capsule())
    }

    private func statusPill(_ label: String, tint: Color) -> some View {
        Text(label)
            .font(.caption.weight(.semibold))
            .foregroundStyle(tint)
            .padding(.horizontal, 9)
            .padding(.vertical, 5)
            .background(tint.opacity(0.12), in: Capsule())
    }

    private func notice(_ text: String, systemImage: String, tint: Color) -> some View {
        HermexMappedLabel(text, systemImage: systemImage)
            .font(.subheadline.weight(.medium))
            .foregroundStyle(tint)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(12)
            .background(tint.opacity(0.10), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}
