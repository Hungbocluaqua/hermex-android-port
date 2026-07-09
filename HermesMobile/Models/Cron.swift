import Foundation

struct CronJobsResponse: Decodable, Equatable {
    let jobs: [CronJob]?
}

struct CronMutationResponse: Decodable, Equatable {
    let ok: Bool?
    let job: CronJob?
    let error: String?
}

struct CronStatusResponse: Decodable, Equatable {
    let jobId: String?
    let running: Bool?
    let elapsed: Double?
    let runningJobs: [String: Double]?
    let error: String?

    enum CodingKeys: String, CodingKey {
        case jobId
        case running
        case elapsed
        case error
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        jobId = try container.decodeIfPresent(String.self, forKey: .jobId)
        elapsed = try container.decodeFlexibleDoubleIfPresent(forKey: .elapsed)
        error = try container.decodeIfPresent(String.self, forKey: .error)

        running = (try? container.decodeIfPresent(Bool.self, forKey: .running)) ?? nil
        runningJobs = (try? container.decodeIfPresent([String: Double].self, forKey: .running)) ?? nil
    }
}

struct CronJob: Decodable, Equatable, Identifiable {
    var id: String {
        jobId ?? name ?? UUID().uuidString
    }

    let jobId: String?
    let name: String?
    let prompt: String?
    let schedule: CronSchedule?
    let scheduleDisplay: String?
    let enabled: Bool?
    let state: String?
    let nextRunAt: CronDateValue?
    let lastRunAt: CronDateValue?
    let lastStatus: String?
    let lastError: String?
    let lastDeliveryError: String?
    let repeatInfo: CronRepeat?
    let deliver: String?
    let skills: [String]?
    let model: String?
    let profile: String?
    let toastNotifications: Bool?

    enum CodingKeys: String, CodingKey {
        case id
        case jobId
        case name
        case prompt
        case schedule
        case scheduleDisplay
        case enabled
        case state
        case nextRunAt
        case lastRunAt
        case lastStatus
        case lastError
        case lastDeliveryError
        case repeatInfo = "repeat"
        case deliver
        case skills
        case model
        case profile
        case toastNotifications
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        jobId = container.decodeLossyStringIfPresent(forKey: .id)
            ?? container.decodeLossyStringIfPresent(forKey: .jobId)
        name = container.decodeLossyStringIfPresent(forKey: .name)
        prompt = container.decodeLossyStringIfPresent(forKey: .prompt)
        schedule = (try? container.decodeIfPresent(CronSchedule.self, forKey: .schedule)) ?? nil
        scheduleDisplay = container.decodeLossyStringIfPresent(forKey: .scheduleDisplay)
        enabled = container.decodeLossyBoolIfPresent(forKey: .enabled)
        state = container.decodeLossyStringIfPresent(forKey: .state)
        nextRunAt = (try? container.decodeIfPresent(CronDateValue.self, forKey: .nextRunAt)) ?? nil
        lastRunAt = (try? container.decodeIfPresent(CronDateValue.self, forKey: .lastRunAt)) ?? nil
        lastStatus = container.decodeLossyStringIfPresent(forKey: .lastStatus)
        lastError = container.decodeLossyStringIfPresent(forKey: .lastError)
        lastDeliveryError = container.decodeLossyStringIfPresent(forKey: .lastDeliveryError)
        repeatInfo = (try? container.decodeIfPresent(CronRepeat.self, forKey: .repeatInfo)) ?? nil
        deliver = container.decodeLossyStringIfPresent(forKey: .deliver)
        skills = (try? container.decodeIfPresent([String].self, forKey: .skills)) ?? nil
        model = container.decodeLossyStringIfPresent(forKey: .model)
        profile = container.decodeLossyStringIfPresent(forKey: .profile)
        toastNotifications = container.decodeLossyBoolIfPresent(forKey: .toastNotifications)
    }

    var displayName: String {
        if let name, !name.isEmpty {
            return name
        }

        if let scheduleText, !scheduleText.isEmpty {
            return scheduleText
        }

        return String(localized: "Untitled Task")
    }

    var scheduleText: String? {
        scheduleDisplay ?? schedule?.displayText
    }

    var editableScheduleText: String? {
        schedule?.expression ?? schedule?.expr ?? schedule?.runAt ?? schedule?.every ?? scheduleDisplay
    }

    var status: CronJobStatus {
        if isRecurring,
           repeatInfo?.times == nil,
           enabled == false,
           state == "completed",
           nextRunAt == nil {
            return .needsAttention
        }

        if isRecurring,
           nextRunAt == nil,
           state == "error" || lastStatus == "error" {
            return .needsAttention
        }

        if state == "paused" {
            return .paused
        }

        if enabled == false {
            return .off
        }

        if lastStatus == "error" {
            return .error
        }

        return .active
    }

    private var isRecurring: Bool {
        schedule?.kind == "cron" || schedule?.kind == "interval"
    }
}

struct CronSchedule: Decodable, Equatable {
    let kind: String?
    let expression: String?
    let expr: String?
    let runAt: String?
    let every: String?

    enum CodingKeys: String, CodingKey {
        case kind
        case expression
        case expr
        case runAt
        case every
    }

    init(from decoder: Decoder) throws {
        if let container = try? decoder.singleValueContainer(),
           let value = try? container.decode(String.self) {
            kind = nil
            expression = value
            expr = nil
            runAt = nil
            every = nil
            return
        }

        let container = try decoder.container(keyedBy: CodingKeys.self)
        kind = container.decodeLossyStringIfPresent(forKey: .kind)
        expression = container.decodeLossyStringIfPresent(forKey: .expression)
        expr = container.decodeLossyStringIfPresent(forKey: .expr)
        runAt = container.decodeLossyStringIfPresent(forKey: .runAt)
        every = container.decodeLossyStringIfPresent(forKey: .every)
    }

    var displayText: String? {
        expression ?? expr ?? runAt ?? every ?? kind
    }
}

struct CronRepeat: Decodable, Equatable {
    let times: Int?
    let completed: Int?
}

struct CronOutputResponse: Decodable, Equatable {
    let jobId: String?
    let outputs: [CronOutputItem]?

    enum CodingKeys: String, CodingKey {
        case jobId
        case outputs
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        jobId = try container.decodeIfPresent(String.self, forKey: .jobId)
        outputs = (try? container.decodeIfPresent([CronOutputItem].self, forKey: .outputs)) ?? nil
    }
}

struct CronOutputItem: Decodable, Equatable, Identifiable {
    var id: String { filename ?? UUID().uuidString }

    let filename: String?
    let content: String?

    enum CodingKeys: String, CodingKey {
        case filename
        case content
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        filename = try container.decodeIfPresent(String.self, forKey: .filename)
        content = try container.decodeIfPresent(String.self, forKey: .content)
    }
}

struct CronHistoryResponse: Decodable, Equatable {
    let jobId: String?
    let runs: [CronRunSummary]?
    let total: Int?
    let offset: Int?
    let error: String?

    enum CodingKeys: String, CodingKey {
        case jobId
        case runs
        case total
        case offset
        case error
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        jobId = container.decodeLossyStringIfPresent(forKey: .jobId)
        runs = (try? container.decodeIfPresent([CronRunSummary].self, forKey: .runs)) ?? nil
        total = container.decodeFlexibleIntIfPresent(forKey: .total)
        offset = container.decodeFlexibleIntIfPresent(forKey: .offset)
        error = container.decodeLossyStringIfPresent(forKey: .error)
    }
}

struct CronRunSummary: Decodable, Equatable, Identifiable {
    var id: String { filename ?? UUID().uuidString }

    let filename: String?
    let size: Int?
    let modified: Double?
    let usage: CronRunUsage?

    enum CodingKeys: String, CodingKey {
        case filename
        case size
        case modified
        case usage
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        filename = container.decodeLossyStringIfPresent(forKey: .filename)
        size = container.decodeFlexibleIntIfPresent(forKey: .size)
        modified = try container.decodeFlexibleDoubleIfPresent(forKey: .modified)
        usage = (try? container.decodeIfPresent(CronRunUsage.self, forKey: .usage)) ?? nil
    }
}

struct CronRunUsage: Decodable, Equatable {
    let model: String?
    let provider: String?
    let estimatedCostUsd: Double?
    let durationSeconds: Double?
    let inputTokens: Int?
    let outputTokens: Int?
    let totalTokens: Int?

    enum CodingKeys: String, CodingKey {
        case model
        case provider
        case estimatedCostUsd
        case durationSeconds
        case inputTokens
        case outputTokens
        case totalTokens
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        model = container.decodeLossyStringIfPresent(forKey: .model)
        provider = container.decodeLossyStringIfPresent(forKey: .provider)
        estimatedCostUsd = try container.decodeFlexibleDoubleIfPresent(forKey: .estimatedCostUsd)
        durationSeconds = try container.decodeFlexibleDoubleIfPresent(forKey: .durationSeconds)
        inputTokens = container.decodeFlexibleIntIfPresent(forKey: .inputTokens)
        outputTokens = container.decodeFlexibleIntIfPresent(forKey: .outputTokens)
        totalTokens = container.decodeFlexibleIntIfPresent(forKey: .totalTokens)
    }
}

struct CronDeliveryOptionsResponse: Decodable, Equatable {
    let platforms: [CronDeliveryPlatform]?
    let error: String?
}

struct CronDeliveryPlatform: Decodable, Equatable, Identifiable {
    var id: String { value ?? label ?? UUID().uuidString }

    let value: String?
    let label: String?
}

enum CronJobStatus: Equatable {
    case active
    case paused
    case off
    case error
    case needsAttention

    var label: String {
        switch self {
        case .active:
            return String(localized: "Active")
        case .paused:
            return String(localized: "Paused")
        case .off:
            return String(localized: "Off")
        case .error:
            return String(localized: "Error")
        case .needsAttention:
            return String(localized: "Needs Attention")
        }
    }
}

struct CronJobEditorDraft: Equatable {
    var name: String
    var prompt: String
    var schedule: String
    var deliver: String
    var skillsText: String
    var model: String
    var profile: String
    var toastNotifications: Bool

    init(
        name: String = "",
        prompt: String = "",
        schedule: String = "",
        deliver: String = "local",
        skillsText: String = "",
        model: String = "",
        profile: String = "",
        toastNotifications: Bool = true
    ) {
        self.name = name
        self.prompt = prompt
        self.schedule = schedule
        self.deliver = deliver
        self.skillsText = skillsText
        self.model = model
        self.profile = profile
        self.toastNotifications = toastNotifications
    }

    init(job: CronJob) {
        self.init(
            name: job.name ?? "",
            prompt: job.prompt ?? "",
            schedule: job.editableScheduleText ?? "",
            deliver: job.deliver ?? "local",
            skillsText: job.skills?.joined(separator: ", ") ?? "",
            model: job.model ?? "",
            profile: job.profile ?? "",
            toastNotifications: job.toastNotifications ?? true
        )
    }

    var trimmedName: String? {
        Self.nonEmpty(name)
    }

    var trimmedPrompt: String {
        prompt.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var trimmedSchedule: String {
        schedule.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var trimmedDeliver: String? {
        Self.nonEmpty(deliver)
    }

    var trimmedModel: String? {
        Self.nonEmpty(model)
    }

    var trimmedProfile: String? {
        Self.nonEmpty(profile)
    }

    var skills: [String] {
        skillsText
            .split { character in
                character == "," || character == "\n"
            }
            .map { String($0).trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
    }

    var validationMessage: String? {
        if trimmedPrompt.isEmpty {
            return String(localized: "Prompt is required.")
        }

        if trimmedSchedule.isEmpty {
            return String(localized: "Schedule is required.")
        }

        return nil
    }

    private static func nonEmpty(_ value: String) -> String? {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}

struct CronDateValue: Decodable, Equatable {
    let date: Date

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()

        if let timestamp = try? container.decode(Double.self) {
            date = Date(timeIntervalSince1970: timestamp)
            return
        }

        let stringValue = try container.decode(String.self)
        if let timestamp = Double(stringValue) {
            date = Date(timeIntervalSince1970: timestamp)
            return
        }

        if let parsed = Self.isoFormatter.date(from: stringValue)
            ?? Self.fractionalISOFormatter.date(from: stringValue) {
            date = parsed
            return
        }

        throw DecodingError.dataCorruptedError(
            in: container,
            debugDescription: "Unsupported cron date value"
        )
    }

    var formatted: String {
        Self.displayFormatter.string(from: date)
    }

    private static let isoFormatter: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter
    }()

    private static let fractionalISOFormatter: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
    }()

    private static let displayFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        return formatter
    }()
}

extension KeyedDecodingContainer {
    func decodeFlexibleDoubleIfPresent(forKey key: Key) throws -> Double? {
        if let value = try? decodeIfPresent(Double.self, forKey: key) {
            return value
        }

        if let value = try? decodeIfPresent(Int.self, forKey: key) {
            return Double(value)
        }

        if let stringValue = try? decodeIfPresent(String.self, forKey: key) {
            return Double(stringValue.trimmingCharacters(in: .whitespacesAndNewlines))
        }

        return nil
    }

    func decodeFlexibleIntIfPresent(forKey key: Key) -> Int? {
        if let value = try? decodeIfPresent(Int.self, forKey: key) {
            return value
        }

        if let value = try? decodeIfPresent(Double.self, forKey: key) {
            return Int(value)
        }

        if let stringValue = try? decodeIfPresent(String.self, forKey: key) {
            return Int(stringValue.trimmingCharacters(in: .whitespacesAndNewlines))
        }

        return nil
    }
}
