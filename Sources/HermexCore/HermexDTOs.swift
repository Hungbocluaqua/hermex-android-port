import Foundation

public struct HermexSessionsResponse: Codable, Equatable, Sendable {
    public var sessions: [HermexSessionDTO]?
    public var projects: [HermexProjectDTO]?
    public var error: String?

    public init(sessions: [HermexSessionDTO]? = nil, projects: [HermexProjectDTO]? = nil, error: String? = nil) {
        self.sessions = sessions
        self.projects = projects
        self.error = error
    }
}

public struct HermexSessionResponse: Codable, Equatable, Sendable {
    public var session: HermexSessionDTO?
    public var messages: [HermexChatMessageDTO]?
    public var error: String?

    public init(session: HermexSessionDTO? = nil, messages: [HermexChatMessageDTO]? = nil, error: String? = nil) {
        self.session = session
        self.messages = messages
        self.error = error
    }
}

public struct HermexSessionDTO: Codable, Identifiable, Equatable, Sendable {
    public var sessionId: String?
    public var title: String?
    public var updatedAt: Double?
    public var createdAt: Double?
    public var lastMessageAt: Double?
    public var messageCount: Int?
    public var workspace: String?
    public var projectId: String?
    public var pinned: Bool?
    public var archived: Bool?
    public var branch: String?
    public var model: String?
    public var modelProvider: String?
    public var messages: [HermexChatMessageDTO]?

    public var id: String { sessionId ?? title ?? "session" }

    public init(
        sessionId: String? = nil,
        title: String? = nil,
        updatedAt: Double? = nil,
        createdAt: Double? = nil,
        lastMessageAt: Double? = nil,
        messageCount: Int? = nil,
        workspace: String? = nil,
        projectId: String? = nil,
        pinned: Bool? = nil,
        archived: Bool? = nil,
        branch: String? = nil,
        model: String? = nil,
        modelProvider: String? = nil,
        messages: [HermexChatMessageDTO]? = nil
    ) {
        self.sessionId = sessionId
        self.title = title
        self.updatedAt = updatedAt
        self.createdAt = createdAt
        self.lastMessageAt = lastMessageAt
        self.messageCount = messageCount
        self.workspace = workspace
        self.projectId = projectId
        self.pinned = pinned
        self.archived = archived
        self.branch = branch
        self.model = model
        self.modelProvider = modelProvider
        self.messages = messages
    }

    enum CodingKeys: String, CodingKey {
        case sessionId = "session_id"
        case title
        case updatedAt = "updated_at"
        case createdAt = "created_at"
        case lastMessageAt = "last_message_at"
        case messageCount = "message_count"
        case workspace
        case projectId = "project_id"
        case pinned
        case archived
        case branch
        case model
        case modelProvider = "model_provider"
        case messages
    }
}

public struct HermexProjectDTO: Codable, Identifiable, Equatable, Sendable {
    public var projectId: String?
    public var name: String?
    public var color: String?
    public var createdAt: Double?

    public var id: String { projectId ?? name ?? "project" }

    enum CodingKeys: String, CodingKey {
        case projectId = "project_id"
        case name
        case color
        case createdAt = "created_at"
    }
}

public struct HermexModelOption: Codable, Identifiable, Equatable, Sendable {
    public var id: String
    public var name: String?
    public var provider: String?
    public var label: String?

    public var displayName: String { label ?? name ?? id }

    public init(id: String, name: String? = nil, provider: String? = nil, label: String? = nil) {
        self.id = id
        self.name = name
        self.provider = provider
        self.label = label
    }
}

public struct HermexProfileOption: Codable, Identifiable, Equatable, Sendable {
    public var name: String
    public var displayName: String?
    public var path: String?
    public var isDefault: Bool?
    public var isActive: Bool?
    public var model: String?
    public var provider: String?

    public var id: String { name }
    public var title: String { displayName ?? name }

    public init(
        name: String,
        displayName: String? = nil,
        path: String? = nil,
        isDefault: Bool? = nil,
        isActive: Bool? = nil,
        model: String? = nil,
        provider: String? = nil
    ) {
        self.name = name
        self.displayName = displayName
        self.path = path
        self.isDefault = isDefault
        self.isActive = isActive
        self.model = model
        self.provider = provider
    }

    enum CodingKeys: String, CodingKey {
        case name
        case displayName = "display_name"
        case path
        case isDefault = "is_default"
        case isActive = "is_active"
        case model
        case provider
    }
}

public struct HermexChatMessageDTO: Codable, Identifiable, Equatable, Sendable {
    public var id: String?
    public var messageId: String?
    public var role: String?
    public var content: String?
    public var text: String?
    public var timestamp: Double?
    public var reasoning: String?
    public var toolCalls: [HermexJSONValue]?
    public var attachments: [HermexAttachmentDTO]?

    public var stableId: String { messageId ?? id ?? "\(role ?? "message"):\(timestamp ?? 0)" }

    public init(
        id: String? = nil,
        messageId: String? = nil,
        role: String? = nil,
        content: String? = nil,
        text: String? = nil,
        timestamp: Double? = nil,
        reasoning: String? = nil,
        toolCalls: [HermexJSONValue]? = nil,
        attachments: [HermexAttachmentDTO]? = nil
    ) {
        self.id = id
        self.messageId = messageId
        self.role = role
        self.content = content
        self.text = text
        self.timestamp = timestamp
        self.reasoning = reasoning
        self.toolCalls = toolCalls
        self.attachments = attachments
    }

    enum CodingKeys: String, CodingKey {
        case id
        case messageId = "message_id"
        case role
        case content
        case text
        case timestamp
        case reasoning
        case toolCalls = "tool_calls"
        case attachments
    }
}

public struct HermexAttachmentDTO: Codable, Identifiable, Equatable, Sendable {
    public var id: String { path ?? name ?? "attachment" }
    public var name: String?
    public var path: String?
    public var mime: String?
    public var size: Int?
    public var isImage: Bool?

    public init(name: String? = nil, path: String? = nil, mime: String? = nil, size: Int? = nil, isImage: Bool? = nil) {
        self.name = name
        self.path = path
        self.mime = mime
        self.size = size
        self.isImage = isImage
    }

    enum CodingKeys: String, CodingKey {
        case name
        case path
        case mime
        case size
        case isImage = "is_image"
    }
}

public struct HermexModelsResponse: Codable, Equatable, Sendable {
    public var groups: [HermexJSONValue]?
    public var models: [HermexJSONValue]?
    public var defaultModel: String?
    public var activeProvider: String?

    public init(
        groups: [HermexJSONValue]? = nil,
        models: [HermexJSONValue]? = nil,
        defaultModel: String? = nil,
        activeProvider: String? = nil
    ) {
        self.groups = groups
        self.models = models
        self.defaultModel = defaultModel
        self.activeProvider = activeProvider
    }

    enum CodingKeys: String, CodingKey {
        case groups
        case models
        case defaultModel = "default_model"
        case activeProvider = "active_provider"
    }

    public var normalizedModels: [HermexModelOption] {
        var options: [HermexModelOption] = []
        for model in models ?? [] {
            options.append(contentsOf: model.modelOptions(inheritedProvider: activeProvider))
        }
        for group in groups ?? [] {
            options.append(contentsOf: group.modelOptions(inheritedProvider: activeProvider))
        }
        return options.uniqueModels()
    }
}

public struct HermexProfilesResponse: Codable, Equatable, Sendable {
    public var profiles: [HermexProfileOption]?
    public var active: String?
    public var singleProfileMode: Bool?
    public var error: String?

    public init(
        profiles: [HermexProfileOption]? = nil,
        active: String? = nil,
        singleProfileMode: Bool? = nil,
        error: String? = nil
    ) {
        self.profiles = profiles
        self.active = active
        self.singleProfileMode = singleProfileMode
        self.error = error
    }

    enum CodingKeys: String, CodingKey {
        case profiles
        case active
        case singleProfileMode = "single_profile_mode"
        case error
    }
}

public struct HermexWorkspaceRootDTO: Codable, Identifiable, Equatable, Sendable {
    public var path: String
    public var name: String?
    public var exists: Bool?
    public var id: String { path }

    public init(path: String, name: String? = nil, exists: Bool? = nil) {
        self.path = path
        self.name = name
        self.exists = exists
    }

    public init(from decoder: Decoder) throws {
        if let stringValue = try? decoder.singleValueContainer().decode(String.self) {
            path = stringValue
            name = nil
            exists = nil
            return
        }

        let container = try decoder.container(keyedBy: CodingKeys.self)
        path = try container.decodeIfPresent(String.self, forKey: CodingKeys.path) ?? ""
        name = try container.decodeIfPresent(String.self, forKey: CodingKeys.name)
        exists = try container.decodeIfPresent(Bool.self, forKey: CodingKeys.exists)
    }

    enum CodingKeys: String, CodingKey {
        case path
        case name
        case exists
    }
}

public struct HermexWorkspacesResponse: Codable, Equatable, Sendable {
    public var workspaces: [HermexWorkspaceRootDTO]?
    public var roots: [HermexWorkspaceRootDTO]?
    public var last: String?
    public var error: String?

    public init(
        workspaces: [HermexWorkspaceRootDTO]? = nil,
        roots: [HermexWorkspaceRootDTO]? = nil,
        last: String? = nil,
        error: String? = nil
    ) {
        self.workspaces = workspaces
        self.roots = roots
        self.last = last
        self.error = error
    }

    public var normalizedRoots: [HermexWorkspaceRootDTO] {
        (workspaces ?? roots ?? []).filter { !$0.path.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines).isEmpty }
    }
}

public struct HermexReasoningResponse: Codable, Equatable, Sendable {
    public var effort: String?
    public var supportedEfforts: [String]?
    public var supportsReasoningEffort: Bool?
    public var error: String?

    public init(
        effort: String? = nil,
        supportedEfforts: [String]? = nil,
        supportsReasoningEffort: Bool? = nil,
        error: String? = nil
    ) {
        self.effort = effort
        self.supportedEfforts = supportedEfforts
        self.supportsReasoningEffort = supportsReasoningEffort
        self.error = error
    }

    enum CodingKeys: String, CodingKey {
        case effort
        case supportedEfforts = "supported_efforts"
        case supportsReasoningEffort = "supports_reasoning_effort"
        case error
    }
}

public struct HermexUploadResponse: Codable, Equatable, Sendable {
    public var filename: String?
    public var path: String?
    public var mime: String?
    public var size: Int?
    public var isImage: Bool?
    public var error: String?

    public init(filename: String? = nil, path: String? = nil, mime: String? = nil, size: Int? = nil, isImage: Bool? = nil, error: String? = nil) {
        self.filename = filename
        self.path = path
        self.mime = mime
        self.size = size
        self.isImage = isImage
        self.error = error
    }

    enum CodingKeys: String, CodingKey {
        case filename
        case path
        case mime
        case size
        case isImage = "is_image"
        case error
    }
}

public struct HermexTranscribeResponse: Codable, Equatable, Sendable {
    public var ok: Bool?
    public var transcript: String?
    public var error: String?

    public init(ok: Bool? = nil, transcript: String? = nil, error: String? = nil) {
        self.ok = ok
        self.transcript = transcript
        self.error = error
    }
}

private extension HermexJSONValue {
    func modelOptions(inheritedProvider: String?) -> [HermexModelOption] {
        switch self {
        case .array(let values):
            var options: [HermexModelOption] = []
            for value in values {
                options.append(contentsOf: value.modelOptions(inheritedProvider: inheritedProvider))
            }
            return options
        case .dictionary(let fields):
            let provider = fields.stringValue("provider") ?? fields.stringValue("provider_id") ?? inheritedProvider
            let nestedKeys = ["models", "items", "slash_autocomplete_models"]
            var nested: [HermexModelOption] = []
            for key in nestedKeys {
                if let nestedValue = fields[key] {
                    nested.append(contentsOf: nestedValue.modelOptions(inheritedProvider: provider))
                }
            }
            if let id = fields.stringValue("id") ?? fields.stringValue("model") ?? fields.stringValue("name") {
                var options = [HermexModelOption(
                    id: id,
                    name: fields.stringValue("name"),
                    provider: provider,
                    label: fields.stringValue("label") ?? fields.stringValue("display_name")
                )]
                options.append(contentsOf: nested)
                return options
            }
            return nested
        case .string(let value):
            let trimmed = value.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines)
            return trimmed.isEmpty ? [] : [HermexModelOption(id: trimmed, provider: inheritedProvider)]
        default:
            return []
        }
    }
}

private extension Array where Element == HermexModelOption {
    func uniqueModels() -> [HermexModelOption] {
        var seen: Set<String> = []
        return filter { option in
            let key = [option.provider, option.id].compactMap { $0 }.joined(separator: ":")
            guard !seen.contains(key) else { return false }
            seen.insert(key)
            return true
        }
    }
}

public struct HermexSkillsResponse: Codable, Equatable, Sendable {
    public var skills: [HermexAPISkillDTO]?
    public var error: String?

    public var jsonValue: HermexJSONValue {
        hermexJSONDictionary([
            "skills": HermexJSONValue.array((skills ?? []).map { $0.jsonValue }),
            "error": hermexJSONString(error)
        ])
    }
}

public struct HermexAPISkillDTO: Codable, Equatable, Sendable {
    public var name: String?
    public var description: String?
    public var category: String?
    public var disabled: Bool?
    public var enabled: Bool?
    public var path: String?
    public var tags: [String]?
    public var relatedSkills: [String]?

    enum CodingKeys: String, CodingKey {
        case name
        case description
        case category
        case disabled
        case enabled
        case path
        case tags
        case relatedSkills = "related_skills"
    }

    public var jsonValue: HermexJSONValue {
        hermexJSONDictionary([
            "name": hermexJSONString(name),
            "description": hermexJSONString(description),
            "category": hermexJSONString(category),
            "disabled": hermexJSONBool(disabled),
            "enabled": hermexJSONBool(enabled),
            "path": hermexJSONString(path),
            "tags": hermexJSONStrings(tags),
            "related_skills": hermexJSONStrings(relatedSkills)
        ])
    }
}

public struct HermexMemoryResponse: Codable, Equatable, Sendable {
    public var memory: String?
    public var user: String?
    public var soul: String?
    public var projectContext: String?
    public var error: String?

    enum CodingKeys: String, CodingKey {
        case memory
        case user
        case soul
        case projectContext = "project_context"
        case error
    }

    public var jsonValue: HermexJSONValue {
        hermexJSONDictionary([
            "memory": hermexJSONString(memory),
            "user": hermexJSONString(user),
            "soul": hermexJSONString(soul),
            "project_context": hermexJSONString(projectContext),
            "error": hermexJSONString(error)
        ])
    }
}

public struct HermexCronsResponse: Codable, Equatable, Sendable {
    public var jobs: [HermexAPICronDTO]?
    public var error: String?

    public var jsonValue: HermexJSONValue {
        hermexJSONDictionary([
            "jobs": HermexJSONValue.array((jobs ?? []).map { $0.jsonValue }),
            "error": hermexJSONString(error)
        ])
    }
}

public struct HermexAPICronDTO: Codable, Equatable, Sendable {
    public var id: String?
    public var name: String?
    public var prompt: String?
    public var skills: [String]?
    public var model: String?
    public var schedule: String?
    public var enabled: Bool?
    public var state: String?
    public var lastStatus: String?
    public var deliver: String?
    public var profile: String?
    public var toastNotifications: Bool?

    enum CodingKeys: String, CodingKey {
        case id
        case name
        case prompt
        case skills
        case model
        case schedule
        case enabled
        case state
        case lastStatus = "last_status"
        case deliver
        case profile
        case toastNotifications = "toast_notifications"
    }

    public var jsonValue: HermexJSONValue {
        hermexJSONDictionary([
            "id": hermexJSONString(id),
            "name": hermexJSONString(name),
            "prompt": hermexJSONString(prompt),
            "skills": hermexJSONStrings(skills),
            "model": hermexJSONString(model),
            "schedule": hermexJSONString(schedule),
            "enabled": hermexJSONBool(enabled),
            "state": hermexJSONString(state),
            "last_status": hermexJSONString(lastStatus),
            "deliver": hermexJSONString(deliver),
            "profile": hermexJSONString(profile),
            "toast_notifications": hermexJSONBool(toastNotifications)
        ])
    }
}

public struct HermexInsightsResponse: Codable, Equatable, Sendable {
    public var periodDays: Int?
    public var totalSessions: Int?
    public var totalMessages: Int?
    public var totalInputTokens: Int?
    public var totalOutputTokens: Int?
    public var totalCacheReadTokens: Int?
    public var totalCacheHitPercent: Double?
    public var totalTokens: Int?
    public var totalCost: Double?
    public var models: [HermexInsightModelDTO]?
    public var dailyTokens: [HermexInsightDailyDTO]?
    public var activityByDay: [HermexInsightDayDTO]?
    public var activityByHour: [HermexInsightHourDTO]?
    public var error: String?

    enum CodingKeys: String, CodingKey {
        case periodDays = "period_days"
        case totalSessions = "total_sessions"
        case totalMessages = "total_messages"
        case totalInputTokens = "total_input_tokens"
        case totalOutputTokens = "total_output_tokens"
        case totalCacheReadTokens = "total_cache_read_tokens"
        case totalCacheHitPercent = "total_cache_hit_percent"
        case totalTokens = "total_tokens"
        case totalCost = "total_cost"
        case models
        case dailyTokens = "daily_tokens"
        case activityByDay = "activity_by_day"
        case activityByHour = "activity_by_hour"
        case error
    }

    public var jsonValue: HermexJSONValue {
        hermexJSONDictionary([
            "period_days": hermexJSONInt(periodDays),
            "total_sessions": hermexJSONInt(totalSessions),
            "total_messages": hermexJSONInt(totalMessages),
            "total_input_tokens": hermexJSONInt(totalInputTokens),
            "total_output_tokens": hermexJSONInt(totalOutputTokens),
            "total_cache_read_tokens": hermexJSONInt(totalCacheReadTokens),
            "total_cache_hit_percent": hermexJSONDouble(totalCacheHitPercent),
            "total_tokens": hermexJSONInt(totalTokens),
            "total_cost": hermexJSONDouble(totalCost),
            "models": HermexJSONValue.array((models ?? []).map { $0.jsonValue }),
            "daily_tokens": HermexJSONValue.array((dailyTokens ?? []).map { $0.jsonValue }),
            "activity_by_day": HermexJSONValue.array((activityByDay ?? []).map { $0.jsonValue }),
            "activity_by_hour": HermexJSONValue.array((activityByHour ?? []).map { $0.jsonValue }),
            "error": hermexJSONString(error)
        ])
    }
}

public struct HermexInsightModelDTO: Codable, Equatable, Sendable {
    public var model: String?
    public var sessions: Int?
    public var inputTokens: Int?
    public var outputTokens: Int?
    public var cacheReadTokens: Int?
    public var cacheHitPercent: Double?
    public var totalTokens: Int?
    public var cost: Double?

    enum CodingKeys: String, CodingKey {
        case model
        case sessions
        case inputTokens = "input_tokens"
        case outputTokens = "output_tokens"
        case cacheReadTokens = "cache_read_tokens"
        case cacheHitPercent = "cache_hit_percent"
        case totalTokens = "total_tokens"
        case cost
    }

    public var jsonValue: HermexJSONValue {
        hermexJSONDictionary([
            "model": hermexJSONString(model),
            "sessions": hermexJSONInt(sessions),
            "input_tokens": hermexJSONInt(inputTokens),
            "output_tokens": hermexJSONInt(outputTokens),
            "cache_read_tokens": hermexJSONInt(cacheReadTokens),
            "cache_hit_percent": hermexJSONDouble(cacheHitPercent),
            "total_tokens": hermexJSONInt(totalTokens),
            "cost": hermexJSONDouble(cost)
        ])
    }
}

public struct HermexInsightDailyDTO: Codable, Equatable, Sendable {
    public var date: String?
    public var inputTokens: Int?
    public var outputTokens: Int?
    public var cacheReadTokens: Int?
    public var sessions: Int?
    public var cost: Double?

    enum CodingKeys: String, CodingKey {
        case date
        case inputTokens = "input_tokens"
        case outputTokens = "output_tokens"
        case cacheReadTokens = "cache_read_tokens"
        case sessions
        case cost
    }

    public var jsonValue: HermexJSONValue {
        hermexJSONDictionary([
            "date": hermexJSONString(date),
            "input_tokens": hermexJSONInt(inputTokens),
            "output_tokens": hermexJSONInt(outputTokens),
            "cache_read_tokens": hermexJSONInt(cacheReadTokens),
            "sessions": hermexJSONInt(sessions),
            "cost": hermexJSONDouble(cost)
        ])
    }
}

public struct HermexInsightDayDTO: Codable, Equatable, Sendable {
    public var day: String?
    public var sessions: Int?

    public var jsonValue: HermexJSONValue {
        hermexJSONDictionary([
            "day": hermexJSONString(day),
            "sessions": hermexJSONInt(sessions)
        ])
    }
}

public struct HermexInsightHourDTO: Codable, Equatable, Sendable {
    public var hour: Int?
    public var sessions: Int?

    public var jsonValue: HermexJSONValue {
        hermexJSONDictionary([
            "hour": hermexJSONInt(hour),
            "sessions": hermexJSONInt(sessions)
        ])
    }
}

private func hermexJSONDictionary(_ fields: [String: HermexJSONValue?]) -> HermexJSONValue {
    HermexJSONValue.dictionary(fields.compactMapValues { $0 })
}

private func hermexJSONString(_ value: String?) -> HermexJSONValue? {
    guard let value else { return nil }
    return HermexJSONValue.string(value)
}

private func hermexJSONBool(_ value: Bool?) -> HermexJSONValue? {
    guard let value else { return nil }
    return HermexJSONValue.bool(value)
}

private func hermexJSONInt(_ value: Int?) -> HermexJSONValue? {
    guard let value else { return nil }
    return HermexJSONValue.number(Double(value))
}

private func hermexJSONDouble(_ value: Double?) -> HermexJSONValue? {
    guard let value else { return nil }
    return HermexJSONValue.number(value)
}

private func hermexJSONStrings(_ values: [String]?) -> HermexJSONValue? {
    guard let values else { return nil }
    return HermexJSONValue.array(values.map { HermexJSONValue.string($0) })
}
