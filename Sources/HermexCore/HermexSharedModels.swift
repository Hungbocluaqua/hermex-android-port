import Foundation

public struct HermexServerIdentity: Codable, Equatable, Sendable {
    public var baseURL: URL
    public var displayName: String
    public var customHeaders: [String: String]
    public var initials: String
    public var headerLogoColorHex: String

    public init(
        baseURL: URL,
        displayName: String,
        customHeaders: [String: String] = [:],
        initials: String = "",
        headerLogoColorHex: String = HermexAppearanceSettings.defaultHeaderLogoColorHex
    ) {
        self.baseURL = baseURL
        self.displayName = displayName
        self.customHeaders = customHeaders
        self.initials = HermexAppearanceSettings.normalizedInitials(initials)
        self.headerLogoColorHex = HermexAppearanceSettings.normalizedHex(headerLogoColorHex)
            ?? HermexAppearanceSettings.defaultHeaderLogoColorHex
    }

    private enum CodingKeys: String, CodingKey {
        case baseURL
        case displayName
        case customHeaders
        case initials
        case headerLogoColorHex
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let baseURL = try container.decode(URL.self, forKey: .baseURL)
        let displayName = try container.decodeIfPresent(String.self, forKey: .displayName) ?? ""
        let customHeaders = try container.decodeIfPresent([String: String].self, forKey: .customHeaders) ?? [:]
        let initials = try container.decodeIfPresent(String.self, forKey: .initials) ?? ""
        let headerLogoColorHex = try container.decodeIfPresent(String.self, forKey: .headerLogoColorHex)
            ?? HermexAppearanceSettings.defaultHeaderLogoColorHex
        self.init(
            baseURL: baseURL,
            displayName: displayName,
            customHeaders: customHeaders,
            initials: initials,
            headerLogoColorHex: headerLogoColorHex
        )
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(baseURL, forKey: .baseURL)
        try container.encode(displayName, forKey: .displayName)
        try container.encode(customHeaders, forKey: .customHeaders)
        try container.encode(initials, forKey: .initials)
        try container.encode(headerLogoColorHex, forKey: .headerLogoColorHex)
    }
}

public struct HermexSessionSummary: Codable, Identifiable, Equatable, Sendable {
    public var id: String
    public var title: String?
    public var messageCount: Int?
    public var workspace: String?
    public var isPinned: Bool?
    public var isArchived: Bool?

    public init(
        id: String,
        title: String? = nil,
        messageCount: Int? = nil,
        workspace: String? = nil,
        isPinned: Bool? = nil,
        isArchived: Bool? = nil
    ) {
        self.id = id
        self.title = title
        self.messageCount = messageCount
        self.workspace = workspace
        self.isPinned = isPinned
        self.isArchived = isArchived
    }
}

public struct HermexChatMessage: Codable, Identifiable, Equatable, Sendable {
    public var id: String
    public var role: String
    public var text: String
    public var createdAt: Date?

    public init(id: String, role: String, text: String, createdAt: Date? = nil) {
        self.id = id
        self.role = role
        self.text = text
        self.createdAt = createdAt
    }
}

public struct HermexWorkspaceRoot: Codable, Identifiable, Equatable, Sendable {
    public var id: String { path }
    public var path: String
    public var label: String?

    public init(path: String, label: String? = nil) {
        self.path = path
        self.label = label
    }
}

public struct HermexGitFileChange: Codable, Identifiable, Equatable, Sendable {
    public var id: String { path }
    public var path: String
    public var status: String
    public var additions: Int?
    public var deletions: Int?
    public var isStaged: Bool?

    public init(path: String, status: String, additions: Int? = nil, deletions: Int? = nil, isStaged: Bool? = nil) {
        self.path = path
        self.status = status
        self.additions = additions
        self.deletions = deletions
        self.isStaged = isStaged
    }
}

public struct HermexSharedDraft: Codable, Equatable, Sendable {
    public var text: String?
    public var attachmentURLs: [URL]

    public init(text: String? = nil, attachmentURLs: [URL] = []) {
        self.text = text
        self.attachmentURLs = attachmentURLs
    }
}
