import Foundation
import HermexCore

public struct HermexPlatformCoordinator: Sendable {
    private let services: HermexPlatformServiceBundle

    public init(services: HermexPlatformServiceBundle) {
        self.services = services
    }

    @MainActor
    public func consumePendingShare(into store: HermexAppStore) async {
        guard let shareIngress = services.shareIngress else { return }
        do {
            guard let draft = try await shareIngress.pendingSharedDraft() else { return }
            await store.send(.applySharedDraft(draft))
            if services.attachmentUploader != nil, !draft.attachmentURLs.isEmpty {
                await uploadAttachments(
                    draft.attachmentURLs,
                    into: store,
                    replacingLocalAttachments: true
                )
            }
            try await shareIngress.clearPendingSharedDraft()
        } catch {
            await store.send(.appendDraftText("Share import failed: \(error)"))
        }
    }

    @MainActor
    public func pickDocumentsAndUpload(into store: HermexAppStore) async {
        guard let picker = services.attachmentPicker else {
            await store.send(.appendDraftText("File picker unavailable."))
            return
        }
        do {
            await uploadAttachments(try await picker.pickDocuments(), into: store)
        } catch {
            await store.send(.appendDraftText("File import failed: \(error)"))
        }
    }

    @MainActor
    public func pickPhotosAndUpload(into store: HermexAppStore) async {
        guard let picker = services.attachmentPicker else {
            await store.send(.appendDraftText("Photo picker unavailable."))
            return
        }
        do {
            await uploadAttachments(try await picker.pickPhotos(), into: store)
        } catch {
            await store.send(.appendDraftText("Photo import failed: \(error)"))
        }
    }

    @MainActor
    public func hydrateCachedSessions(serverID: String, into store: HermexAppStore) async {
        guard let cache = services.cache else { return }
        do {
            let sessions = try await cache.cachedSessions(for: serverID)
            guard !sessions.isEmpty else { return }
            await store.send(.hydrateCachedSessions(sessions))
        } catch {
            await store.send(.appendDraftText("Offline cache unavailable: \(error)"))
        }
    }

    @MainActor
    public func hydrateCachedMessages(sessionID: String, serverID: String, into store: HermexAppStore) async {
        guard let cache = services.cache else { return }
        do {
            let messages = try await cache.cachedMessages(sessionID: sessionID, serverID: serverID)
            guard !messages.isEmpty else { return }
            await store.send(.hydrateCachedMessages(sessionID: sessionID, messages))
        } catch {
            await store.send(.appendDraftText("Offline transcript unavailable: \(error)"))
        }
    }

    @MainActor
    public func cacheSessions(serverID: String, from store: HermexAppStore) async {
        guard let cache = services.cache else { return }
        try? await cache.replaceCachedSessions(store.sessions.sessions, for: serverID)
    }

    @MainActor
    public func cacheMessages(serverID: String, from store: HermexAppStore) async {
        guard let cache = services.cache, let sessionID = store.appState.selectedSessionID else { return }
        try? await cache.replaceCachedMessages(store.chat.messages, sessionID: sessionID, serverID: serverID)
    }

    @MainActor
    public func startVoiceRecording(in store: HermexAppStore) async {
        guard let recorder = services.voiceRecorder else { return }
        do {
            try await recorder.start()
            await store.send(.setVoiceRecording(true))
        } catch {
            await store.send(.appendDraftText("Voice recording failed: \(error)"))
        }
    }

    @MainActor
    public func stopVoiceRecordingAndTranscribe(into store: HermexAppStore) async {
        guard let recorder = services.voiceRecorder else { return }
        do {
            let url = try await recorder.stop()
            await store.send(.setVoiceRecording(false))
            guard let transcriber = services.audioTranscriber else { return }
            let response = try await transcriber.transcribeAudio(at: url)
            if let transcript = response.transcript, !transcript.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines).isEmpty {
                await store.send(.appendDraftText(transcript))
                if services.attachmentUploader != nil,
                   store.appState.selectedSessionID != nil {
                    await uploadAttachments([url], into: store)
                }
            } else if let error = response.error {
                await store.send(.appendDraftText("Transcription failed: \(error)"))
            }
        } catch {
            await store.send(.setVoiceRecording(false))
            await store.send(.appendDraftText("Transcription failed: \(error)"))
        }
    }

    @MainActor
    public func cancelVoiceRecording(in store: HermexAppStore) async {
        guard let recorder = services.voiceRecorder else { return }
        await recorder.cancel()
        await store.send(.setVoiceRecording(false))
    }

    @MainActor
    public func speakLatestAssistantMessage(from store: HermexAppStore) async {
        guard let speech = services.speechSynthesizer else { return }
        guard let text = store.chat.messages.reversed().first(where: { $0.role == "assistant" })?.contentOrText else { return }
        await speech.speak(text)
    }

    public func copyMessage(_ context: HermexMessageActionContext) async {
        await services.clipboard?.copy(context.copyText)
    }

    public func speakMessage(_ context: HermexMessageActionContext) async {
        guard let speech = services.speechSynthesizer,
              let text = context.listenText
        else { return }
        await speech.speak(text)
    }

    @MainActor
    public func syncStatusNotification(from store: HermexAppStore) async {
        guard let notifier = services.statusNotifier, let sessionID = store.appState.selectedSessionID else { return }
        if store.chat.stream.isStreaming {
            await notifier.showRunning(
                sessionID: sessionID,
                streamID: store.chat.stream.streamID,
                preview: store.chat.messages.last?.contentOrText
            )
        } else {
            await notifier.showComplete(sessionID: sessionID)
        }
    }

    @MainActor
    public func applyStreamEvents(_ events: [HermexSSEEvent], into store: HermexAppStore) async {
        for event in events {
            await store.send(.applyStreamEvent(event))
        }
    }

    public func clearStatusNotification(sessionID: String) async {
        await services.statusNotifier?.clear(sessionID: sessionID)
    }

    @MainActor
    private func uploadAttachments(
        _ urls: [URL],
        into store: HermexAppStore,
        replacingLocalAttachments: Bool = false
    ) async {
        guard !urls.isEmpty else { return }
        guard let uploader = services.attachmentUploader else {
            await store.send(.appendDraftText("Attachment upload unavailable."))
            return
        }
        guard let sessionID = store.appState.selectedSessionID else {
            await store.send(.appendDraftText("The server did not provide a session ID."))
            return
        }

        await store.send(.setUploadingAttachment(true))
        for url in urls {
            let filename = url.lastPathComponent.isEmpty ? "attachment" : url.lastPathComponent
            do {
                let response = try await uploader.uploadAttachment(at: url, sessionID: sessionID)
                if let error = response.error, !error.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines).isEmpty {
                    if replacingLocalAttachments { await store.send(.removeAttachment(url.path)) }
                    await store.send(.appendDraftText("Attachment upload failed: \(error)"))
                    continue
                }
                guard let path = response.path, !path.isEmpty else {
                    if replacingLocalAttachments { await store.send(.removeAttachment(url.path)) }
                    await store.send(.appendDraftText("The server did not return the uploaded file path."))
                    continue
                }
                let attachment = HermexAttachmentDTO(
                    name: response.filename ?? filename,
                    path: path,
                    mime: response.mime,
                    size: response.size,
                    isImage: response.isImage
                )
                if replacingLocalAttachments {
                    await store.send(.replaceAttachment(id: url.path, with: attachment))
                } else {
                    await store.send(.addAttachment(attachment))
                }
            } catch {
                if replacingLocalAttachments { await store.send(.removeAttachment(url.path)) }
                await store.send(.appendDraftText("Attachment upload failed: \(error)"))
            }
        }
        await store.send(.setUploadingAttachment(false))
    }
}

private extension HermexChatMessageDTO {
    var contentOrText: String? {
        let primary = content?.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines)
        if let primary, !primary.isEmpty { return primary }
        let fallback = text?.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines)
        return fallback?.isEmpty == false ? fallback : nil
    }
}
