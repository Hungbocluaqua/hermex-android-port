import Foundation
import SwiftUI
import HermexCore
#if !SKIP && canImport(UIKit)
import UIKit
private typealias HermexDecodedAttachmentImage = UIImage
#elseif !SKIP && canImport(AppKit)
import AppKit
private typealias HermexDecodedAttachmentImage = NSImage
#elseif SKIP
private typealias HermexDecodedAttachmentImage = UIImage
#endif

public struct HermexChatScreen: View {
    private let state: HermexChatState
    private let prefersComposerFocused: Bool
    private let loadAttachmentData: @Sendable (_ sessionID: String, _ path: String) async -> Data?
    private let playAttachment: @Sendable (_ sessionID: String, _ path: String, _ filename: String) async -> Bool
    private let stopAttachmentPlayback: @Sendable () async -> Void
    private let onEvent: (HermexUIEvent) -> Void
    @State private var composerInset: CGFloat = HermexLayoutContract.composerFallbackInset
    @State private var editingMessageContext: HermexMessageActionContext?
    @State private var editingMessageText = ""
    @State private var selectedMessageText: String?
    @State private var showsClearConversationConfirmation = false

    public init(
        state: HermexChatState,
        prefersComposerFocused: Bool = false,
        loadAttachmentData: @escaping @Sendable (_ sessionID: String, _ path: String) async -> Data? = { _, _ in nil },
        playAttachment: @escaping @Sendable (_ sessionID: String, _ path: String, _ filename: String) async -> Bool = { _, _, _ in false },
        stopAttachmentPlayback: @escaping @Sendable () async -> Void = {},
        onEvent: @escaping (HermexUIEvent) -> Void = { _ in }
    ) {
        self.state = state
        self.prefersComposerFocused = prefersComposerFocused
        self.loadAttachmentData = loadAttachmentData
        self.playAttachment = playAttachment
        self.stopAttachmentPlayback = stopAttachmentPlayback
        self.onEvent = onEvent
    }

    public var body: some View {
        ZStack(alignment: .bottom) {
            VStack(spacing: 0) {
                chatHeader

                ScrollView {
#if SKIP
                    VStack(alignment: .leading, spacing: HermexLayoutContract.chatTranscriptMessageSpacing) {
                        ForEach(Array(state.messages.enumerated()), id: \.offset) { index, message in
                            messageBlock(message, visibleIndex: index)
                        }

                        if state.stream.isStreaming {
                            streamingIndicator
                        }
                    }
                    .padding(.horizontal, HermexLayoutContract.chatTranscriptHorizontalPadding)
                    .padding(.top, HermexLayoutContract.chatTranscriptTopPadding)
#else
                    LazyVStack(alignment: .leading, spacing: HermexLayoutContract.chatTranscriptMessageSpacing) {
                        ForEach(Array(state.messages.enumerated()), id: \.offset) { index, message in
                            messageBlock(message, visibleIndex: index)
                        }

                        if state.stream.isStreaming {
                            streamingIndicator
                        }
                    }
                    .padding(.horizontal, HermexLayoutContract.chatTranscriptHorizontalPadding)
                    .padding(.top, HermexLayoutContract.chatTranscriptTopPadding)
#endif
                }
                .hermexComposerBottomReserve(composerInset)
            }

            LinearGradient(
                colors: [.clear, HermexUIColors.systemBackground.opacity(0.94), HermexUIColors.systemBackground],
                startPoint: .top,
                endPoint: .bottom
            )
            .frame(height: composerInset + HermexLayoutContract.composerGradientTopPadding)
            .allowsHitTesting(false)

            HermexMeasuredBottomInset(
                measuredHeight: $composerInset,
                minimumHeight: HermexLayoutContract.composerFallbackInset
            ) {
                VStack(spacing: HermexLayoutContract.pendingPromptBottomSpacing) {
                    pendingPromptStack
                    HermexComposerSurface(
                        state: state.composer,
                        stream: state.stream,
                        showsTurnActions: state.messages.last?.role == "assistant" && !state.stream.isStreaming,
                        prefersFocused: prefersComposerFocused,
                        onEvent: onEvent
                    )
                }
            }

            if let context = editingMessageContext {
                editMessagePanel(context: context)
                    .padding(.horizontal, 16)
                    .padding(.bottom, composerInset + 18)
            }

            if let selectedMessageText {
                selectMessagePanel(text: selectedMessageText)
                    .padding(.horizontal, 16)
                    .padding(.bottom, composerInset + 18)
            }

            if showsClearConversationConfirmation {
                clearConversationPanel
                    .padding(.horizontal, 16)
                    .padding(.bottom, composerInset + 18)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(HermexUIColors.systemBackground.ignoresSafeArea())
        .foregroundStyle(HermexUIColors.primaryText)
    }

    private var chatHeader: some View {
        HStack(spacing: 12) {
            HermexCircleIconButton(
                systemImage: "chevron.left",
                accessibilityLabel: "Back",
                action: { onEvent(.openRoute(.sessions)) }
            )

            VStack(alignment: .leading, spacing: 2) {
                Text(state.composer.selectedWorkspace ?? state.session?.workspace ?? "workspace")
                    .font(.body.weight(.medium))
                    .foregroundStyle(HermexUIColors.secondaryText)
                    .lineLimit(1)
                if state.isViewingCachedData {
                    Text("Cached transcript")
                        .font(.caption)
                        .foregroundStyle(HermexUIColors.secondaryText)
                }
            }

            Spacer(minLength: 8)

            HermexIconCluster {
                HermexCircleIconButton(
                    systemImage: "folder",
                    accessibilityLabel: "Files",
                    action: { onEvent(.openRoute(.workspace)) }
                )
                HermexCircleIconButton(
                    systemImage: "arrow.triangle.branch",
                    accessibilityLabel: "Git",
                    action: { onEvent(.openRoute(.git)) }
                )
                HermexCircleIconButton(
                    systemImage: "arrow.clockwise",
                    accessibilityLabel: "Refresh",
                    action: { onEvent(.refresh) }
                )
                HermexCircleIconButton(
                    systemImage: "trash",
                    accessibilityLabel: "Clear conversation",
                    action: {
                        if canClearConversation {
                            showsClearConversationConfirmation = true
                        }
                    }
                )
                .opacity(canClearConversation ? 1.0 : 0.42)
            }
        }
        .padding(.horizontal, HermexLayoutContract.chatTopBarHorizontalPadding)
        .padding(.vertical, HermexLayoutContract.chatTopBarVerticalPadding)
        .frame(minHeight: HermexLayoutContract.chatTopBarHeight)
    }

    private func messageBlock(_ message: HermexChatMessageDTO, visibleIndex: Int) -> some View {
        let actionContext = HermexMessageActionContextResolver.context(
            for: message,
            visibleIndex: visibleIndex
        )
        return VStack(alignment: message.role == "user" ? .trailing : .leading, spacing: 7) {
            if let reasoning = message.reasoning, !reasoning.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines).isEmpty {
                transcriptAccessory(title: "Thinking", text: reasoning, systemImage: "brain.head.profile")
            }

            if let toolCalls = message.toolCalls, !toolCalls.isEmpty {
                transcriptAccessory(title: "Tool calls", text: toolCallSummary(toolCalls), systemImage: "hammer")
            }

            if let attachments = message.attachments, !attachments.isEmpty {
                messageAttachmentStrip(attachments, sessionID: state.session?.sessionId)
            }

            let content = message.content ?? message.text ?? ""
            if !content.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines).isEmpty {
                HStack {
                    if let actionContext, message.role != "user" {
                        messageActionMenu(actionContext)
                    }
                    if message.role == "user" { Spacer(minLength: 52) }
                    HermexTranscriptMarkdown(content: content)
                        .font(.body)
                        .foregroundStyle(HermexUIColors.primaryText)
                        .hermexTextSelectionEnabled()
                        .padding(.horizontal, message.role == "user" ? 14.0 : 0.0)
                        .padding(.vertical, message.role == "user" ? 8.0 : 0.0)
                        .background(
                            message.role == "user"
                                ? HermexUIColors.glassFillStrong
                                : Color.clear,
                            in: RoundedRectangle(cornerRadius: 20, style: .continuous)
                        )
                        .overlay {
                            if message.role == "user" {
                                RoundedRectangle(cornerRadius: 20, style: .continuous)
                                    .stroke(HermexUIColors.hairline, lineWidth: 0.5)
                            }
                        }
                    if let actionContext, message.role == "user" {
                        messageActionMenu(actionContext)
                    }
                    if message.role != "user" { Spacer(minLength: 52) }
                }
            }

            if let url = previewURL(in: content) {
                linkPreviewCard(url)
            }
        }
        .frame(maxWidth: .infinity, alignment: message.role == "user" ? .trailing : .leading)
    }

    private func messageActionMenu(_ context: HermexMessageActionContext) -> some View {
        Menu {
            Button("Copy") {
                onEvent(.copyMessage(context))
            }

            if context.role == .assistant {
                Button("Listen") {
                    onEvent(.listenMessage(context))
                }
                Button("Select Text") {
                    selectedMessageText = context.copyText
                }
                Button("Regenerate Response") {
                    onEvent(.regenerateMessage(context))
                }
            }

            if context.role == .user {
                Button("Edit Message") {
                    editingMessageContext = context
                    editingMessageText = context.copyText
                }
            }

            Button("Fork From Here") {
                onEvent(.forkMessage(context))
            }
        } label: {
            Image(systemName: HermexSystemImageName("ellipsis.circle"))
                .font(.caption.weight(.semibold))
                .foregroundStyle(HermexUIColors.secondaryText)
                .frame(width: 28, height: 28)
        }
        .buttonStyle(.plain)
    }

    private func editMessagePanel(context: HermexMessageActionContext) -> some View {
        HermexGlassPanel(cornerRadius: 16) {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Text("Edit Message")
                        .font(.headline.weight(.semibold))
                    Spacer()
                    Button("Cancel") {
                        editingMessageContext = nil
                    }
                    .font(.caption.weight(.medium))
                }

#if SKIP
                TextField("Message", text: $editingMessageText)
                    .padding(10)
                    .background(HermexUIColors.glassFillStrong, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
#else
                TextEditor(text: $editingMessageText)
                    .frame(minHeight: 90, maxHeight: 150)
                    .padding(6)
                    .background(HermexUIColors.glassFillStrong, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
#endif

                HStack {
                    Spacer()
                    Button("Save") {
                        let text = editingMessageText
                        editingMessageContext = nil
                        onEvent(.editMessage(context, text))
                    }
                    .fontWeight(.semibold)
                }
            }
            .padding(14)
        }
    }

    private func selectMessagePanel(text: String) -> some View {
        HermexGlassPanel(cornerRadius: 16) {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Text("Select Text")
                        .font(.headline.weight(.semibold))
                    Spacer()
                    Button("Done") {
                        selectedMessageText = nil
                    }
                    .font(.caption.weight(.medium))
                }

                ScrollView {
                    Text(verbatim: text)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .hermexTextSelectionEnabled()
                }
                .frame(minHeight: 90, maxHeight: 190)
            }
            .padding(14)
        }
    }

    private var clearConversationPanel: some View {
        HermexGlassPanel(cornerRadius: 16) {
            VStack(alignment: .leading, spacing: 10) {
                Text("Clear conversation")
                    .font(.headline.weight(.semibold))

                Text("Clear all messages? This cannot be undone.")
                    .font(.callout)
                    .foregroundStyle(HermexUIColors.secondaryText)

                HStack {
                    Button("Cancel") {
                        showsClearConversationConfirmation = false
                    }
                    Spacer()
                    Button("Clear") {
                        showsClearConversationConfirmation = false
                        onEvent(.clearConversation)
                    }
                    .fontWeight(.semibold)
                    .foregroundStyle(.red)
                }
            }
            .padding(14)
        }
    }

    private var canClearConversation: Bool {
        !state.isLoading && !state.isViewingCachedData && !state.stream.isStreaming
    }

    private func transcriptAccessory(title: String, text: String, systemImage: String) -> some View {
        HermexTranscriptAccessory(title: title, text: text, systemImage: systemImage)
    }

    private func toolCallSummary(_ calls: [HermexJSONValue]) -> String {
        calls.enumerated().map { index, value in
            "\(index + 1). \(jsonPreview(value))"
        }.joined(separator: "\n")
    }

    private func jsonPreview(_ value: HermexJSONValue) -> String {
        switch value {
        case .string(let text):
            return text
        case .number(let number):
            return String(number)
        case .bool(let flag):
            return flag ? "true" : "false"
        case .null:
            return "null"
        case .array(let values):
            return "[\(values.count) item(s)]"
        case .dictionary(let fields):
            for key in ["name", "tool", "function", "command", "path"] {
                if case .string(let text)? = fields[key], !text.isEmpty {
                    return text
                }
            }
            return "{\(fields.count) field(s)}"
        }
    }

    private func messageAttachmentStrip(_ attachments: [HermexAttachmentDTO], sessionID: String?) -> some View {
        let audioAttachments = attachments.filter(\.hermexIsAudio)
        let gridAttachments = attachments.filter { !$0.hermexIsAudio }

        return VStack(alignment: .leading, spacing: 8) {
            ForEach(audioAttachments) { attachment in
                HermexInlineAudioAttachment(
                    attachment: attachment,
                    sessionID: sessionID,
                    play: playAttachment,
                    stop: stopAttachmentPlayback
                )
            }

            if !gridAttachments.isEmpty {
                HStack(spacing: 8) {
                    ForEach(gridAttachments) { attachment in
                        Button {
                            if let path = attachment.path ?? attachment.name {
                                onEvent(.openFile(path))
                            }
                        } label: {
                            HermexAttachmentTile(
                                attachment: attachment,
                                sessionID: sessionID,
                                loadData: loadAttachmentData
                            )
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    private func previewURL(in text: String) -> URL? {
        let normalizedText = text
            .replacingOccurrences(of: "\n", with: " ")
            .replacingOccurrences(of: "\t", with: " ")
        for token in normalizedText.split(separator: " ") {
            let candidate = token
                .trimmingCharacters(in: CharacterSet(charactersIn: "()[]{}<>\"'.,;"))
            guard let url = URL(string: candidate),
                  let scheme = url.scheme?.lowercased(),
                  scheme == "http" || scheme == "https",
                  url.host != nil
            else { continue }
            return url
        }
        return nil
    }

    private func linkPreviewCard(_ url: URL) -> some View {
        Button {
            onEvent(.openExternalURL(url.absoluteString))
        } label: {
            HStack(spacing: 10) {
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(HermexUIColors.gold)
                    .frame(width: 3, height: 42)

                VStack(alignment: .leading, spacing: 3) {
                    Text(url.host ?? url.absoluteString)
                        .font(.caption.weight(.semibold))
                        .lineLimit(1)
                    Text(url.path.isEmpty ? url.absoluteString : url.path)
                        .font(.caption2)
                        .foregroundStyle(HermexUIColors.secondaryText)
                        .lineLimit(2)
                }

                Spacer(minLength: 4)
                Image(systemName: HermexSystemImageName("arrow.up.right"))
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(HermexUIColors.gold)
            }
            .frame(maxWidth: 300, alignment: .leading)
            .padding(10)
            .hermexThinMaterialBackground(in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
        .buttonStyle(.plain)
    }

    private var streamingIndicator: some View {
        HStack(spacing: 8) {
            Image(systemName: HermexSystemImageName("sparkles"))
            Text(state.stream.liveToolActivity ?? "Responding")
        }
            .font(.caption)
            .foregroundStyle(HermexUIColors.secondaryText)
            .padding(.vertical, 8)
    }

    @ViewBuilder
    private var pendingPromptStack: some View {
        VStack(spacing: 8) {
            if let approval = state.pendingApproval {
                approvalPrompt(approval)
            }
            if let clarification = state.pendingClarification {
                clarificationPrompt(clarification)
            }
        }
        .padding(.horizontal, HermexLayoutContract.pendingPromptHorizontalPadding)
    }

    private func approvalPrompt(_ approval: HermexApprovalPrompt) -> some View {
        HermexGlassPanel(cornerRadius: HermexLayoutContract.pendingPromptCornerRadius) {
            VStack(alignment: .leading, spacing: 10) {
                HermexMappedLabel(approval.title ?? "Approval required", systemImage: "checkmark.shield")
                    .font(.headline.weight(.semibold))
                if let command = approval.command, !command.isEmpty {
                    Text(command)
                        .font(.system(.callout, design: .monospaced))
                        .lineLimit(3)
                }
                if let details = approval.details, !details.isEmpty {
                    Text(details)
                        .font(.caption)
                        .foregroundStyle(HermexUIColors.secondaryText)
                        .lineLimit(4)
                }
                HStack {
                    Button("Deny") { onEvent(.approval("deny")) }
                    Spacer()
                    Button("Approve") { onEvent(.approval("approve")) }
                        .fontWeight(.semibold)
                }
            }
            .padding(14)
        }
    }

    private func clarificationPrompt(_ clarification: HermexClarificationPrompt) -> some View {
        HermexGlassPanel(cornerRadius: HermexLayoutContract.pendingPromptCornerRadius) {
            VStack(alignment: .leading, spacing: 10) {
                HermexMappedLabel("Clarification", systemImage: "questionmark.bubble")
                    .font(.headline.weight(.semibold))
                Text(clarification.question)
                    .font(.callout)
                if !clarification.options.isEmpty {
                    HStack {
                        ForEach(clarification.options, id: \.self) { option in
                            Button(option) { onEvent(.clarify(option)) }
                        }
                    }
                }
                if !clarification.draft.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines).isEmpty {
                    Button("Send draft") { onEvent(.clarify(clarification.draft)) }
                }
            }
            .padding(14)
        }
    }
}

private extension HermexAttachmentDTO {
    var hermexIsImage: Bool {
        if isImage == true { return true }
        if let mime, mime.lowercased().hasPrefix("image/") { return true }
        return ["jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "tif", "tiff"]
            .contains(hermexFileExtension)
    }

    var hermexIsAudio: Bool {
        if let mime, mime.lowercased().hasPrefix("audio/") { return true }
        return ["aac", "flac", "m4a", "mp3", "ogg", "opus", "wav", "webm"]
            .contains(hermexFileExtension)
    }

    var hermexDisplayName: String {
        if let name = name?.trimmingCharacters(in: .whitespacesAndNewlines), !name.isEmpty {
            return name
        }
        if let path = path?.trimmingCharacters(in: .whitespacesAndNewlines), !path.isEmpty {
            let lastPathComponent = URL(fileURLWithPath: path).lastPathComponent
            return lastPathComponent.isEmpty ? path : lastPathComponent
        }
        return "Attachment"
    }

    var hermexFileExtension: String {
        URL(fileURLWithPath: name ?? path ?? "").pathExtension.lowercased()
    }
}

private struct HermexAttachmentTile: View {
    let attachment: HermexAttachmentDTO
    let sessionID: String?
    let loadData: @Sendable (_ sessionID: String, _ path: String) async -> Data?

    var body: some View {
        if attachment.hermexIsImage,
           let sessionID,
           let path = attachment.path ?? attachment.name {
            HermexRemoteAttachmentImage(path: path) {
                await loadData(sessionID, path)
            }
            .frame(width: 118, height: 118)
        } else if attachment.hermexIsImage {
            fallbackTile(systemImage: "photo")
        } else {
            fallbackTile(systemImage: "doc")
        }
    }

    private func fallbackTile(systemImage: String) -> some View {
        VStack(spacing: 6) {
            Image(systemName: HermexSystemImageName(systemImage))
                .font(.title2.weight(.semibold))
                .foregroundStyle(HermexUIColors.gold)
            Text(attachment.hermexDisplayName)
                .font(.caption2.weight(.medium))
                .lineLimit(2)
                .multilineTextAlignment(.center)
            if !attachment.hermexFileExtension.isEmpty {
                Text(String(attachment.hermexFileExtension.prefix(5)).uppercased())
                    .font(.system(size: 10, weight: .bold, design: .monospaced))
                    .foregroundStyle(HermexUIColors.secondaryText)
            }
        }
        .frame(width: 118, height: 118)
        .padding(8)
        .hermexThinMaterialBackground(in: RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}

private struct HermexRemoteAttachmentImage: View {
    let path: String
    let loadData: @Sendable () async -> Data?
    @State private var image: HermexDecodedAttachmentImage?
    @State private var didAttempt = false

    var body: some View {
        ZStack {
            if let image {
                decodedImage(image)
                    .resizable()
                    .scaledToFill()
            } else if didAttempt {
                Image(systemName: HermexSystemImageName("photo"))
                    .font(.title2)
                    .foregroundStyle(HermexUIColors.secondaryText)
            } else {
                ProgressView()
            }
        }
        .frame(width: 118, height: 118)
        .clipped()
        .hermexThinMaterialBackground(in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        .task(id: path) {
            let data = await loadData()
            guard !Task.isCancelled else { return }
            image = data.flatMap { HermexDecodedAttachmentImage(data: $0) }
            didAttempt = true
        }
    }

    private func decodedImage(_ image: HermexDecodedAttachmentImage) -> Image {
#if SKIP || canImport(UIKit)
        return Image(uiImage: image)
#else
        return Image(nsImage: image)
#endif
    }
}

private struct HermexInlineAudioAttachment: View {
    let attachment: HermexAttachmentDTO
    let sessionID: String?
    let play: @Sendable (_ sessionID: String, _ path: String, _ filename: String) async -> Bool
    let stop: @Sendable () async -> Void
    @State private var isPlaying = false
    @State private var isLoading = false

    var body: some View {
        HStack(spacing: 10) {
            Button {
                togglePlayback()
            } label: {
                Group {
                    if isLoading {
                        ProgressView()
                    } else {
                        Image(systemName: HermexSystemImageName(isPlaying ? "pause.fill" : "play.fill"))
                            .font(.caption.weight(.bold))
                    }
                }
                .frame(width: 34, height: 34)
                .foregroundStyle(isPlaying ? Color.black : HermexUIColors.primaryText)
                .background(isPlaying ? HermexUIColors.gold : HermexUIColors.glassFillStrong, in: Circle())
            }
            .buttonStyle(.plain)
            .disabled(isLoading || sessionID == nil || (attachment.path ?? attachment.name) == nil)

            VStack(alignment: .leading, spacing: 3) {
                Text(attachment.hermexDisplayName)
                    .font(.caption.weight(.semibold))
                    .lineLimit(1)
                Text(isPlaying ? "Playing" : "Audio attachment")
                    .font(.caption2)
                    .foregroundStyle(HermexUIColors.secondaryText)
            }
            Spacer(minLength: 4)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
        .hermexThinMaterialBackground(in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        .onDisappear {
            if isPlaying {
                Task { await stop() }
            }
        }
    }

    private func togglePlayback() {
        guard let sessionID, let path = attachment.path ?? attachment.name else { return }
        if isPlaying {
            isPlaying = false
            Task { await stop() }
            return
        }

        isLoading = true
        Task {
            let started = await play(sessionID, path, attachment.hermexDisplayName)
            guard !Task.isCancelled else { return }
            isLoading = false
            isPlaying = started
        }
    }
}

public extension View {
    @ViewBuilder
    func hermexComposerBottomReserve(_ height: CGFloat) -> some View {
        let reserved = max(height, HermexLayoutContract.composerFallbackInset)
#if SKIP
        // Skip does not support safeAreaInset yet; reserve space with bottom padding.
        self.padding(.bottom, reserved)
#else
        self.safeAreaInset(edge: .bottom, spacing: 0) {
            Color.clear.frame(height: reserved)
        }
#endif
    }

    @ViewBuilder
    func hermexTextSelectionEnabled() -> some View {
#if SKIP
        self
#else
        self.textSelection(.enabled)
#endif
    }
}

public struct HermexComposerSurface: View {
    private let state: HermexComposerState
    private let stream: HermexStreamState
    private let showsTurnActions: Bool
    private let prefersFocused: Bool
    private let onEvent: (HermexUIEvent) -> Void
    @FocusState private var isDraftFocused: Bool
    @State private var localDraft: String

    public init(
        state: HermexComposerState,
        stream: HermexStreamState,
        showsTurnActions: Bool = false,
        prefersFocused: Bool = false,
        onEvent: @escaping (HermexUIEvent) -> Void = { _ in }
    ) {
        self.state = state
        self.stream = stream
        self.showsTurnActions = showsTurnActions
        self.prefersFocused = prefersFocused
        self.onEvent = onEvent
        self._localDraft = State(initialValue: state.draft)
    }

    public var body: some View {
        VStack(spacing: HermexLayoutContract.composerContainerSpacing) {
            composerStatusStack

            if showsSlashCommandHint {
                slashCommandHint
            }

            HermexGlassPanel(cornerRadius: composerCornerRadius) {
                VStack(spacing: 0) {
                    if !state.attachments.isEmpty {
                        attachmentStrip
                    }

                    composerTextInput

                    HStack(spacing: HermexLayoutContract.composerControlSpacing) {
                        Menu {
                            Button("Files") { onEvent(.attach) }
                            Button("Photos") { onEvent(.attachPhotos) }
                        } label: {
                            composerControlGlyph("plus", size: HermexLayoutContract.composerPlusButtonSize)
                        }
                        modelControl
                        if state.showsReasoningControl {
                            reasoningControl
                        }
                        Spacer()
                        Button { onEvent(state.isRecordingVoice ? .stopVoice : .startVoice) } label: {
                            composerControlGlyph("waveform", size: HermexLayoutContract.composerActionButtonSize)
                        }
                        Button { onEvent(stream.isStreaming ? .cancelStream : .sendDraft) } label: {
                            composerActionGlyph
                        }
                    }
                    .buttonStyle(.plain)
                    .foregroundStyle(HermexUIColors.primaryText)
                    .padding(.horizontal, HermexLayoutContract.composerSurfaceHorizontalPadding)
                    .padding(.top, HermexLayoutContract.composerSurfaceTopPadding)
                    .padding(.bottom, HermexLayoutContract.composerSurfaceBottomPadding)
                }
            }

            HStack(spacing: HermexLayoutContract.composerSecondaryBarSpacing) {
                workspaceControl
                profileControl
                Spacer()
            }

            if showsTurnActions {
                HStack(spacing: HermexLayoutContract.composerQuickActionSpacing) {
                    quickAction("Undo", .undo)
                    quickAction("Retry", .retry)
                    quickAction("Compress", .compress)
                    Spacer()
                }
            }
        }
        .padding(.horizontal)
        .padding(.bottom, HermexLayoutContract.composerBottomAccessorySpacing)
        .foregroundStyle(HermexUIColors.primaryText)
        .onChange(of: state.draft) { _, newValue in
            if newValue != localDraft {
                localDraft = newValue
            }
        }
    }

    @ViewBuilder
    private var composerStatusStack: some View {
        VStack(spacing: 6) {
            if state.isRecordingVoice {
                composerStatusBar(text: "Recording voice", systemImage: "waveform", isError: false)
            }
            if state.isUploadingAttachment {
                composerStatusBar(text: "Uploading attachment", systemImage: "paperclip", isError: false)
            }
            if state.isLoadingConfiguration {
                composerStatusBar(text: "Loading composer settings", systemImage: "gearshape", isError: false)
            }
            if stream.isRecovering {
                composerStatusBar(text: "Reconnecting stream", systemImage: "arrow.clockwise", isError: false)
            }
            if let error = state.configurationErrorMessage, !error.isEmpty {
                composerStatusBar(text: error, systemImage: "exclamationmark.triangle", isError: true)
            }
        }
    }

    private var showsSlashCommandHint: Bool {
        state.draft.trimmingCharacters(in: .whitespacesAndNewlines).hasPrefix("/")
    }

    private var slashCommandHint: some View {
        HermexGlassPanel(cornerRadius: 14) {
            VStack(alignment: .leading, spacing: 8) {
                Text("Commands")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(HermexUIColors.secondaryText)
                    .textCase(.uppercase)
                Text("Continue typing a slash command from this server.")
                    .font(.footnote)
                    .foregroundStyle(HermexUIColors.primaryText)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
        }
        .padding(.horizontal)
    }

    private var isExpanded: Bool {
        composerTextInputHeight > HermexLayoutContract.composerTextInputCollapsedContentHeight
    }

    private var composerCornerRadius: CGFloat {
        isExpanded ? HermexLayoutContract.composerCornerRadiusExpanded : HermexLayoutContract.composerCornerRadiusCollapsed
    }

    private var textVerticalPadding: CGFloat {
        isExpanded ? HermexLayoutContract.composerTextVerticalPaddingExpanded : HermexLayoutContract.composerTextVerticalPaddingCollapsed
    }

    private var draftBinding: Binding<String> {
        Binding(
            get: { localDraft },
            set: { newValue in
                localDraft = newValue
                onEvent(.updateDraft(newValue))
            }
        )
    }

    private var composerTextInput: some View {
        ZStack(alignment: .topLeading) {
#if SKIP
            TextField("", text: draftBinding)
                .font(.title3)
                .foregroundStyle(HermexUIColors.primaryText)
                .frame(height: composerTextInputHeight)
                .padding(.horizontal, HermexLayoutContract.composerSurfaceHorizontalPadding)
                .padding(.vertical, textVerticalPadding)
                .background(Color.clear)
                .accessibilityIdentifier("hermex-composer-draft-input")
#else
            TextEditor(text: draftBinding)
                .font(.title3)
                .foregroundStyle(HermexUIColors.primaryText)
                .frame(height: composerTextInputHeight)
                .padding(.horizontal, HermexLayoutContract.composerSurfaceHorizontalPadding)
                .padding(.vertical, textVerticalPadding)
                .background(Color.clear)
                .accessibilityIdentifier("hermex-composer-draft-input")
                .focused($isDraftFocused)
                .onAppear {
                    requestDraftFocusIfPreferred()
                }
                .onChange(of: prefersFocused) { _, newValue in
                    if newValue {
                        requestDraftFocusIfPreferred()
                    }
                }
#endif

            if localDraft.isEmpty {
                Text("Ask anything... /commands")
                    .font(.title3)
                    .foregroundStyle(HermexUIColors.tertiaryText)
                    .padding(.horizontal, HermexLayoutContract.composerSurfaceHorizontalPadding)
                    .padding(.vertical, textVerticalPadding)
                    .allowsHitTesting(false)
            }
        }
        .frame(minHeight: HermexLayoutContract.composerTextInputMinimumHeight, alignment: .topLeading)
    }

    private func requestDraftFocusIfPreferred() {
        guard prefersFocused else { return }
        isDraftFocused = true
    }

    private var composerTextInputHeight: CGFloat {
        let explicitLineCount = localDraft
            .split(separator: "\n", omittingEmptySubsequences: false)
            .count
        let wrappedLineCount = max(1, (localDraft.count / HermexLayoutContract.composerTextInputWrapColumn) + 1)
        let lineCount = max(explicitLineCount, wrappedLineCount)
        let measuredHeight = CGFloat(lineCount) * HermexLayoutContract.composerTextInputLineHeight
        return min(
            HermexLayoutContract.composerTextInputMaximumHeight,
            max(HermexLayoutContract.composerTextInputCollapsedContentHeight, measuredHeight)
        )
    }

    private func composerControlGlyph(_ systemImage: String, size: CGFloat) -> some View {
        Image(systemName: HermexSystemImageName(systemImage))
            .font(.system(size: size == HermexLayoutContract.composerPlusButtonSize ? 24.0 : 17.0, weight: .semibold))
            .frame(width: size, height: size)
            .frame(width: HermexLayoutContract.chatToolbarActionSlotSize, height: HermexLayoutContract.chatToolbarActionSlotSize)
    }

    private var composerActionGlyph: some View {
        Image(systemName: HermexSystemImageName(stream.isStreaming ? "stop.fill" : "arrow.up"))
            .font(.system(size: 15, weight: .semibold))
            .frame(
                width: HermexLayoutContract.composerActionButtonSize,
                height: HermexLayoutContract.composerActionButtonSize
            )
            .background(HermexUIColors.primaryText, in: Circle())
            .foregroundStyle(HermexUIColors.systemBackground)
            .frame(
                width: HermexLayoutContract.chatToolbarActionSlotSize,
                height: HermexLayoutContract.chatToolbarActionSlotSize
            )
    }

    @ViewBuilder
    private var modelControl: some View {
        if state.availableModels.isEmpty {
            Button { onEvent(.selectModel) } label: {
                modelLabel
            }
        } else {
            Menu {
                ForEach(state.availableModels) { model in
                    Button(model.displayName) {
                        onEvent(.chooseModel(model))
                    }
                }
            } label: {
                modelLabel
            }
        }
    }

    private var modelLabel: some View {
        HStack(spacing: 4) {
            Text(state.selectedModel ?? "model")
                .lineLimit(1)
            Image(systemName: HermexSystemImageName("chevron.down"))
                .font(.caption2.weight(.semibold))
        }
        .foregroundStyle(HermexUIColors.primaryText.opacity(0.84))
        .frame(maxWidth: HermexLayoutContract.composerModelControlMaxWidth, alignment: .leading)
    }

    @ViewBuilder
    private var reasoningControl: some View {
        if state.supportedReasoningEfforts.isEmpty {
            Button { onEvent(.selectReasoning) } label: {
                reasoningLabel
            }
        } else {
            Menu {
                ForEach(state.supportedReasoningEfforts, id: \.self) { effort in
                    Button(effort) {
                        onEvent(.chooseReasoningEffort(effort))
                    }
                }
            } label: {
                reasoningLabel
            }
        }
    }

    private var reasoningLabel: some View {
        HStack(spacing: 5) {
            Image(systemName: HermexSystemImageName("brain"))
            Text(state.selectedReasoningEffort ?? "Reasoning")
                .lineLimit(1)
            Image(systemName: HermexSystemImageName("chevron.down"))
                .font(.caption2.weight(.semibold))
        }
        .foregroundStyle(HermexUIColors.primaryText.opacity(0.88))
        .frame(width: HermexLayoutContract.composerReasoningControlWidth, alignment: .leading)
    }

    @ViewBuilder
    private var workspaceControl: some View {
        if state.availableWorkspaces.isEmpty {
            Button { onEvent(.selectWorkspace) } label: {
                HermexPillLabel(state.selectedWorkspace ?? "Home", systemImage: "folder")
            }
        } else {
            Menu {
                ForEach(state.availableWorkspaces) { workspace in
                    Button(workspace.name ?? workspace.path) {
                        onEvent(.chooseWorkspace(workspace))
                    }
                }
            } label: {
                HermexPillLabel(state.selectedWorkspace ?? "Home", systemImage: "folder")
            }
        }
    }

    @ViewBuilder
    private var profileControl: some View {
        if state.availableProfiles.isEmpty {
            Button { onEvent(.selectProfile) } label: {
                HermexPillLabel(state.selectedProfile ?? "default", systemImage: "person.crop.circle")
            }
        } else {
            Menu {
                ForEach(state.availableProfiles) { profile in
                    Button(profile.title) {
                        onEvent(.chooseProfile(profile))
                    }
                }
            } label: {
                HermexPillLabel(state.selectedProfile ?? "default", systemImage: "person.crop.circle")
            }
        }
    }

    private func composerStatusBar(text: String, systemImage: String, isError: Bool) -> some View {
        HermexMappedLabel(text, systemImage: systemImage)
            .font(.caption.weight(.medium))
            .foregroundStyle(isError ? Color.red.opacity(0.95) : HermexUIColors.secondaryText)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 10)
            .padding(.vertical, 8)
            .background(
                isError ? Color.red.opacity(0.10) : HermexUIColors.glassFillStrong,
                in: RoundedRectangle(cornerRadius: 10, style: .continuous)
            )
            .overlay {
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .stroke(isError ? Color.red.opacity(0.28) : HermexUIColors.hairline, lineWidth: 0.6)
            }
            .padding(.horizontal)
    }

    private var attachmentStrip: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 7) {
                ForEach(state.attachments) { attachment in
                    HStack(spacing: 3) {
                        Button {
                            if let path = attachment.path ?? attachment.name {
                                onEvent(.openFile(path))
                            }
                        } label: {
                            HermexMappedLabel(
                                attachment.name ?? attachment.path ?? "Attachment",
                                systemImage: attachment.isImage == true ? "photo" : "doc"
                            )
                            .font(.caption.weight(.medium))
                            .lineLimit(1)
                        }
                        .buttonStyle(.plain)

                        Button {
                            onEvent(.removeAttachment(attachment.id))
                        } label: {
                            Image(systemName: HermexSystemImageName("xmark"))
                                .font(.caption2.weight(.bold))
                                .frame(width: 22, height: 22)
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Remove attachment")
                    }
                    .padding(.leading, 9)
                    .padding(.trailing, 3)
                    .frame(height: HermexLayoutContract.composerAttachmentStripHeight)
                    .foregroundStyle(HermexUIColors.primaryText)
                    .hermexThinMaterialBackground(in: Capsule())
                }
            }
            .padding(.horizontal, HermexLayoutContract.composerSurfaceHorizontalPadding)
            .padding(.top, 9)
        }
    }

    private func quickAction(_ title: String, _ event: HermexUIEvent) -> some View {
        Button {
            onEvent(event)
        } label: {
            Text(title)
                .font(.subheadline.weight(.medium))
                .foregroundStyle(HermexUIColors.primaryText)
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .background(HermexUIColors.glassFillStrong, in: Capsule())
                .overlay {
                    Capsule().stroke(HermexUIColors.hairline, lineWidth: 0.6)
                }
        }
        .buttonStyle(.plain)
    }
}
