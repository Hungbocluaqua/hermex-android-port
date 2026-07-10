import Foundation
import HermexCore
#if SKIP
import SkipKeychain
#endif

public final class HermexSecureDataStore: @unchecked Sendable {
#if SKIP
    private let keychain: Keychain

    public init(keychain: Keychain = .shared) {
        self.keychain = keychain
    }
#else
    private let defaults: UserDefaults

    public init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }
#endif

    public func data(for key: String) -> Data? {
#if SKIP
        do {
            guard let encoded = try keychain.string(forKey: key) else { return nil }
            return Data(base64Encoded: encoded)
        } catch {
            return nil
        }
#else
        return defaults.data(forKey: key)
#endif
    }

    public func setData(_ data: Data?, for key: String) {
#if SKIP
        do {
            if let data {
                try keychain.set(data.base64EncodedString(), forKey: key)
            } else {
                try keychain.removeValue(forKey: key)
            }
        } catch {
            return
        }
#else
        defaults.set(data, forKey: key)
#endif
    }
}

public protocol HermexSecretStore: Sendable {
    func value(for key: String, serverID: String) async throws -> Data?
    func setValue(_ value: Data?, for key: String, serverID: String) async throws
}

public struct HermexCookieRecord: Codable, Equatable, Sendable {
    public var name: String
    public var value: String
    public var domain: String?
    public var path: String
    public var expiresAt: Date?
    public var isSecure: Bool
    public var isHTTPOnly: Bool

    public init(
        name: String,
        value: String,
        domain: String? = nil,
        path: String = "/",
        expiresAt: Date? = nil,
        isSecure: Bool = false,
        isHTTPOnly: Bool = false
    ) {
        self.name = name
        self.value = value
        self.domain = domain
        self.path = path
        self.expiresAt = expiresAt
        self.isSecure = isSecure
        self.isHTTPOnly = isHTTPOnly
    }
}

public protocol HermexCookieStore: Sendable {
    func cookies(for serverID: String) async throws -> [HermexCookieRecord]
    func replaceCookies(_ cookies: [HermexCookieRecord], for serverID: String) async throws
    func clearCookies(for serverID: String) async throws
}

public protocol HermexCacheStore: Sendable {
    func cachedSessions(for serverID: String) async throws -> [HermexSessionDTO]
    func replaceCachedSessions(_ sessions: [HermexSessionDTO], for serverID: String) async throws
    func cachedMessages(sessionID: String, serverID: String) async throws -> [HermexChatMessageDTO]
    func replaceCachedMessages(_ messages: [HermexChatMessageDTO], sessionID: String, serverID: String) async throws
}

public protocol HermexAttachmentPicker: Sendable {
    func pickDocuments() async throws -> [URL]
    func pickPhotos() async throws -> [URL]
}

public protocol HermexAttachmentUploader: Sendable {
    func uploadAttachment(at url: URL, sessionID: String) async throws -> HermexUploadResponse
}

public protocol HermexAttachmentDataLoader: Sendable {
    func loadAttachmentData(sessionID: String, path: String) async throws -> Data
}

public protocol HermexAttachmentAudioPlayer: Sendable {
    func play(data: Data, filename: String) async -> Bool
    func stop() async
}

public protocol HermexVoiceRecorder: Sendable {
    func start() async throws
    func stop() async throws -> URL
    func cancel() async
}

public protocol HermexAudioTranscriber: Sendable {
    func transcribeAudio(at url: URL) async throws -> HermexTranscribeResponse
}

public protocol HermexSpeechSynthesizer: Sendable {
    func speak(_ text: String) async
    func stop() async
}

public protocol HermexClipboardService: Sendable {
    func copy(_ text: String) async
}

public protocol HermexStatusNotifier: Sendable {
    func showRunning(sessionID: String, streamID: String?, preview: String?) async
    func showComplete(sessionID: String) async
    func clear(sessionID: String) async
}

public protocol HermexShareIngress: Sendable {
    func pendingSharedDraft() async throws -> HermexSharedDraft?
    func clearPendingSharedDraft() async throws
}

public struct HermexPlatformServiceBundle: Sendable {
    public var cache: (any HermexCacheStore)?
    public var shareIngress: (any HermexShareIngress)?
    public var attachmentPicker: (any HermexAttachmentPicker)?
    public var attachmentUploader: (any HermexAttachmentUploader)?
    public var attachmentDataLoader: (any HermexAttachmentDataLoader)?
    public var attachmentAudioPlayer: (any HermexAttachmentAudioPlayer)?
    public var voiceRecorder: (any HermexVoiceRecorder)?
    public var audioTranscriber: (any HermexAudioTranscriber)?
    public var speechSynthesizer: (any HermexSpeechSynthesizer)?
    public var clipboard: (any HermexClipboardService)?
    public var statusNotifier: (any HermexStatusNotifier)?

    public init(
        cache: (any HermexCacheStore)? = nil,
        shareIngress: (any HermexShareIngress)? = nil,
        attachmentPicker: (any HermexAttachmentPicker)? = nil,
        attachmentUploader: (any HermexAttachmentUploader)? = nil,
        attachmentDataLoader: (any HermexAttachmentDataLoader)? = nil,
        attachmentAudioPlayer: (any HermexAttachmentAudioPlayer)? = nil,
        voiceRecorder: (any HermexVoiceRecorder)? = nil,
        audioTranscriber: (any HermexAudioTranscriber)? = nil,
        speechSynthesizer: (any HermexSpeechSynthesizer)? = nil,
        clipboard: (any HermexClipboardService)? = nil,
        statusNotifier: (any HermexStatusNotifier)? = nil
    ) {
        self.cache = cache
        self.shareIngress = shareIngress
        self.attachmentPicker = attachmentPicker
        self.attachmentUploader = attachmentUploader
        self.attachmentDataLoader = attachmentDataLoader
        self.attachmentAudioPlayer = attachmentAudioPlayer
        self.voiceRecorder = voiceRecorder
        self.audioTranscriber = audioTranscriber
        self.speechSynthesizer = speechSynthesizer
        self.clipboard = clipboard
        self.statusNotifier = statusNotifier
    }
}
