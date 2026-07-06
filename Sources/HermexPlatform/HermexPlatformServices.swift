import Foundation
import HermexCore

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
    public var voiceRecorder: (any HermexVoiceRecorder)?
    public var audioTranscriber: (any HermexAudioTranscriber)?
    public var speechSynthesizer: (any HermexSpeechSynthesizer)?
    public var statusNotifier: (any HermexStatusNotifier)?

    public init(
        cache: (any HermexCacheStore)? = nil,
        shareIngress: (any HermexShareIngress)? = nil,
        voiceRecorder: (any HermexVoiceRecorder)? = nil,
        audioTranscriber: (any HermexAudioTranscriber)? = nil,
        speechSynthesizer: (any HermexSpeechSynthesizer)? = nil,
        statusNotifier: (any HermexStatusNotifier)? = nil
    ) {
        self.cache = cache
        self.shareIngress = shareIngress
        self.voiceRecorder = voiceRecorder
        self.audioTranscriber = audioTranscriber
        self.speechSynthesizer = speechSynthesizer
        self.statusNotifier = statusNotifier
    }
}
