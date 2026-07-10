import Foundation

#if SKIP
public protocol HermexStateCodable {}
#else
public typealias HermexStateCodable = Codable
#endif

public enum HermexAuthState: HermexStateCodable, Equatable, Sendable {
    case unconfigured
    case loggedOut(server: HermexServerIdentity)
    case loggedIn(server: HermexServerIdentity)

#if !SKIP
    private enum CodingKeys: String, CodingKey {
        case kind
        case server
    }

    private enum Kind: String, Codable {
        case unconfigured
        case loggedOut
        case loggedIn
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let kind = try container.decodeIfPresent(Kind.self, forKey: .kind) ?? .unconfigured

        switch kind {
        case .unconfigured:
            self = .unconfigured
        case .loggedOut:
            let server = try container.decode(HermexServerIdentity.self, forKey: .server)
            self = .loggedOut(server: server)
        case .loggedIn:
            let server = try container.decode(HermexServerIdentity.self, forKey: .server)
            self = .loggedIn(server: server)
        }
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)

        switch self {
        case .unconfigured:
            try container.encode(Kind.unconfigured, forKey: .kind)
        case .loggedOut(let server):
            try container.encode(Kind.loggedOut, forKey: .kind)
            try container.encode(server, forKey: .server)
        case .loggedIn(let server):
            try container.encode(Kind.loggedIn, forKey: .kind)
            try container.encode(server, forKey: .server)
        }
    }
#endif
}

public struct HermexAppState: HermexStateCodable, Equatable, Sendable {
    public var auth: HermexAuthState
    public var selectedSessionID: String?
    public var pendingSharedDraft: HermexSharedDraft?
    public var route: HermexRoute

    public init(
        auth: HermexAuthState = .unconfigured,
        selectedSessionID: String? = nil,
        pendingSharedDraft: HermexSharedDraft? = nil,
        route: HermexRoute = .onboarding
    ) {
        self.auth = auth
        self.selectedSessionID = selectedSessionID
        self.pendingSharedDraft = pendingSharedDraft
        self.route = route
    }
}

public struct HermexOnboardingState: HermexStateCodable, Equatable, Sendable {
    public var serverURLString: String
    public var displayName: String
    public var password: String
    public var customHeaderText: String
    public var lastValidatedServer: HermexServerIdentity?
    public var isTestingConnection: Bool
    public var isSigningIn: Bool
    public var statusMessage: String?
    public var errorMessage: String?

    public init(
        serverURLString: String = "",
        displayName: String = "",
        password: String = "",
        customHeaderText: String = "",
        lastValidatedServer: HermexServerIdentity? = nil,
        isTestingConnection: Bool = false,
        isSigningIn: Bool = false,
        statusMessage: String? = nil,
        errorMessage: String? = nil
    ) {
        self.serverURLString = serverURLString
        self.displayName = displayName
        self.password = password
        self.customHeaderText = customHeaderText
        self.lastValidatedServer = lastValidatedServer
        self.isTestingConnection = isTestingConnection
        self.isSigningIn = isSigningIn
        self.statusMessage = statusMessage
        self.errorMessage = errorMessage
    }

    private enum CodingKeys: String, CodingKey {
        case serverURLString
        case displayName
        case customHeaderText
        case lastValidatedServer
        case isTestingConnection
        case isSigningIn
        case statusMessage
        case errorMessage
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.serverURLString = try container.decodeIfPresent(String.self, forKey: CodingKeys.serverURLString) ?? ""
        self.displayName = try container.decodeIfPresent(String.self, forKey: CodingKeys.displayName) ?? ""
        self.password = ""
        self.customHeaderText = try container.decodeIfPresent(String.self, forKey: CodingKeys.customHeaderText) ?? ""
        self.lastValidatedServer = try container.decodeIfPresent(HermexServerIdentity.self, forKey: CodingKeys.lastValidatedServer)
        self.isTestingConnection = try container.decodeIfPresent(Bool.self, forKey: CodingKeys.isTestingConnection) ?? false
        self.isSigningIn = try container.decodeIfPresent(Bool.self, forKey: CodingKeys.isSigningIn) ?? false
        self.statusMessage = try container.decodeIfPresent(String.self, forKey: CodingKeys.statusMessage)
        self.errorMessage = try container.decodeIfPresent(String.self, forKey: CodingKeys.errorMessage)
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(serverURLString, forKey: CodingKeys.serverURLString)
        try container.encode(displayName, forKey: CodingKeys.displayName)
        try container.encode(customHeaderText, forKey: CodingKeys.customHeaderText)
        try container.encodeIfPresent(lastValidatedServer, forKey: CodingKeys.lastValidatedServer)
        try container.encode(isTestingConnection, forKey: CodingKeys.isTestingConnection)
        try container.encode(isSigningIn, forKey: CodingKeys.isSigningIn)
        try container.encodeIfPresent(statusMessage, forKey: CodingKeys.statusMessage)
        try container.encodeIfPresent(errorMessage, forKey: CodingKeys.errorMessage)
    }
}

public enum HermexRoute: String, HermexStateCodable, Equatable, Sendable {
    case onboarding
    case sessions
    case chat
    case settings
    case workspace
    case git
    case panels
}

public struct HermexSessionListState: HermexStateCodable, Equatable, Sendable {
    public var sessions: [HermexSessionDTO]
    public var projects: [HermexProjectDTO]
    public var searchQuery: String
    public var selectedProjectID: String?
    public var activeProfileName: String?
    public var isLoading: Bool
    public var isShowingArchived: Bool
    public var isViewingCachedData: Bool
    public var errorMessage: String?

    public init(
        sessions: [HermexSessionDTO] = [],
        projects: [HermexProjectDTO] = [],
        searchQuery: String = "",
        selectedProjectID: String? = nil,
        activeProfileName: String? = nil,
        isLoading: Bool = false,
        isShowingArchived: Bool = false,
        isViewingCachedData: Bool = false,
        errorMessage: String? = nil
    ) {
        self.sessions = sessions
        self.projects = projects
        self.searchQuery = searchQuery
        self.selectedProjectID = selectedProjectID
        self.activeProfileName = activeProfileName
        self.isLoading = isLoading
        self.isShowingArchived = isShowingArchived
        self.isViewingCachedData = isViewingCachedData
        self.errorMessage = errorMessage
    }
}

public struct HermexChatState: HermexStateCodable, Equatable, Sendable {
    public var session: HermexSessionDTO?
    public var messages: [HermexChatMessageDTO]
    public var composer: HermexComposerState
    public var stream: HermexStreamState
    public var pendingApproval: HermexApprovalPrompt?
    public var pendingClarification: HermexClarificationPrompt?
    public var isLoading: Bool
    public var isViewingCachedData: Bool
    public var errorMessage: String?

    public init(
        session: HermexSessionDTO? = nil,
        messages: [HermexChatMessageDTO] = [],
        composer: HermexComposerState = HermexComposerState(),
        stream: HermexStreamState = HermexStreamState(),
        pendingApproval: HermexApprovalPrompt? = nil,
        pendingClarification: HermexClarificationPrompt? = nil,
        isLoading: Bool = false,
        isViewingCachedData: Bool = false,
        errorMessage: String? = nil
    ) {
        self.session = session
        self.messages = messages
        self.composer = composer
        self.stream = stream
        self.pendingApproval = pendingApproval
        self.pendingClarification = pendingClarification
        self.isLoading = isLoading
        self.isViewingCachedData = isViewingCachedData
        self.errorMessage = errorMessage
    }
}

public struct HermexComposerState: HermexStateCodable, Equatable, Sendable {
    public var draft: String
    public var selectedModel: String?
    public var selectedModelProvider: String?
    public var selectedWorkspace: String?
    public var selectedProfile: String?
    public var selectedReasoningEffort: String?
    public var availableModels: [HermexModelOption]
    public var availableProfiles: [HermexProfileOption]
    public var availableWorkspaces: [HermexWorkspaceRootDTO]
    public var supportedReasoningEfforts: [String]
    public var attachments: [HermexAttachmentDTO]
    public var isUploadingAttachment: Bool
    public var isRecordingVoice: Bool
    public var isLoadingConfiguration: Bool
    public var configurationErrorMessage: String?
    public var showsReasoningControl: Bool

    public init(
        draft: String = "",
        selectedModel: String? = nil,
        selectedModelProvider: String? = nil,
        selectedWorkspace: String? = nil,
        selectedProfile: String? = nil,
        selectedReasoningEffort: String? = nil,
        availableModels: [HermexModelOption] = [],
        availableProfiles: [HermexProfileOption] = [],
        availableWorkspaces: [HermexWorkspaceRootDTO] = [],
        supportedReasoningEfforts: [String] = [],
        attachments: [HermexAttachmentDTO] = [],
        isUploadingAttachment: Bool = false,
        isRecordingVoice: Bool = false,
        isLoadingConfiguration: Bool = false,
        configurationErrorMessage: String? = nil,
        showsReasoningControl: Bool = true
    ) {
        self.draft = draft
        self.selectedModel = selectedModel
        self.selectedModelProvider = selectedModelProvider
        self.selectedWorkspace = selectedWorkspace
        self.selectedProfile = selectedProfile
        self.selectedReasoningEffort = selectedReasoningEffort
        self.availableModels = availableModels
        self.availableProfiles = availableProfiles
        self.availableWorkspaces = availableWorkspaces
        self.supportedReasoningEfforts = supportedReasoningEfforts
        self.attachments = attachments
        self.isUploadingAttachment = isUploadingAttachment
        self.isRecordingVoice = isRecordingVoice
        self.isLoadingConfiguration = isLoadingConfiguration
        self.configurationErrorMessage = configurationErrorMessage
        self.showsReasoningControl = showsReasoningControl
    }
}

public struct HermexStreamState: HermexStateCodable, Equatable, Sendable {
    public var streamID: String?
    public var isStreaming: Bool
    public var isRecovering: Bool
    public var liveReasoning: String
    public var liveToolActivity: String?

    public init(
        streamID: String? = nil,
        isStreaming: Bool = false,
        isRecovering: Bool = false,
        liveReasoning: String = "",
        liveToolActivity: String? = nil
    ) {
        self.streamID = streamID
        self.isStreaming = isStreaming
        self.isRecovering = isRecovering
        self.liveReasoning = liveReasoning
        self.liveToolActivity = liveToolActivity
    }
}

public struct HermexApprovalPrompt: HermexStateCodable, Equatable, Sendable {
    public var approvalID: String?
    public var title: String?
    public var command: String?
    public var details: String?

    public init(approvalID: String? = nil, title: String? = nil, command: String? = nil, details: String? = nil) {
        self.approvalID = approvalID
        self.title = title
        self.command = command
        self.details = details
    }
}

public struct HermexClarificationPrompt: HermexStateCodable, Equatable, Sendable {
    public var promptID: String?
    public var question: String
    public var options: [String]
    public var draft: String

    public init(promptID: String? = nil, question: String, options: [String] = [], draft: String = "") {
        self.promptID = promptID
        self.question = question
        self.options = options
        self.draft = draft
    }
}

public struct HermexWorkspaceState: HermexStateCodable, Equatable, Sendable {
    public var roots: [HermexWorkspaceRootDTO]
    public var currentPath: String?
    public var entries: [HermexWorkspaceEntryDTO]
    public var preview: HermexFilePreview?
    public var isLoading: Bool
    public var errorMessage: String?

    public init(
        roots: [HermexWorkspaceRootDTO] = [],
        currentPath: String? = nil,
        entries: [HermexWorkspaceEntryDTO] = [],
        preview: HermexFilePreview? = nil,
        isLoading: Bool = false,
        errorMessage: String? = nil
    ) {
        self.roots = roots
        self.currentPath = currentPath
        self.entries = entries
        self.preview = preview
        self.isLoading = isLoading
        self.errorMessage = errorMessage
    }
}

public struct HermexWorkspaceEntryDTO: HermexStateCodable, Identifiable, Equatable, Sendable {
    public var id: String { path }
    public var name: String
    public var path: String
    public var type: String?
    public var isDirectory: Bool
    public var size: Int?

    public init(name: String, path: String, type: String? = nil, isDirectory: Bool, size: Int? = nil) {
        self.name = name
        self.path = path
        self.type = type
        self.isDirectory = isDirectory
        self.size = size
    }
}

public struct HermexFilePreview: HermexStateCodable, Equatable, Sendable {
    public var path: String
    public var content: String?
    public var mimeType: String?
    public var isBinary: Bool

    public init(path: String, content: String? = nil, mimeType: String? = nil, isBinary: Bool = false) {
        self.path = path
        self.content = content
        self.mimeType = mimeType
        self.isBinary = isBinary
    }
}

public struct HermexGitState: HermexStateCodable, Equatable, Sendable {
    public var isRepository: Bool
    public var branch: String?
    public var upstream: String?
    public var ahead: Int?
    public var behind: Int?
    public var files: [HermexGitFileChange]
    public var diffPath: String?
    public var diffText: String?
    public var commitMessage: String
    public var isMutating: Bool
    public var errorMessage: String?

    public init(
        isRepository: Bool = false,
        branch: String? = nil,
        upstream: String? = nil,
        ahead: Int? = nil,
        behind: Int? = nil,
        files: [HermexGitFileChange] = [],
        diffPath: String? = nil,
        diffText: String? = nil,
        commitMessage: String = "",
        isMutating: Bool = false,
        errorMessage: String? = nil
    ) {
        self.isRepository = isRepository
        self.branch = branch
        self.upstream = upstream
        self.ahead = ahead
        self.behind = behind
        self.files = files
        self.diffPath = diffPath
        self.diffText = diffText
        self.commitMessage = commitMessage
        self.isMutating = isMutating
        self.errorMessage = errorMessage
    }
}

public enum HermexGitCommand: Equatable, Sendable {
    case fetch
    case pull
    case push
    case diff(path: String, kind: String)
    case stage(path: String)
    case unstage(path: String)
    case discard(path: String, deleteUntracked: Bool)
    case commit(message: String)
}

public struct HermexPanelsState: HermexStateCodable, Equatable, Sendable {
    public var tasks: [HermexTaskDTO]
    public var taskDraft: HermexTaskDraft?
    public var selectedTaskID: String?
    public var taskOutput: HermexJSONValue?
    public var isLoadingTaskOutput: Bool
    public var pendingTaskDeletionID: String?
    public var isMutating: Bool
    public var skills: [HermexSkillDTO]
    public var selectedSkillName: String?
    public var selectedSkillDetail: HermexSkillDetailDTO?
    public var selectedSkillFileName: String?
    public var selectedSkillFileContent: String?
    public var isLoadingSkillDetail: Bool
    public var isLoadingSkillFile: Bool
    public var memory: [HermexMemorySectionDTO]
    public var insights: HermexJSONValue?
    public var insightsDays: Int
    public var selectedPanel: HermexPanel
    public var isLoading: Bool
    public var errorMessage: String?

    public init(
        tasks: [HermexTaskDTO] = [],
        taskDraft: HermexTaskDraft? = nil,
        selectedTaskID: String? = nil,
        taskOutput: HermexJSONValue? = nil,
        isLoadingTaskOutput: Bool = false,
        pendingTaskDeletionID: String? = nil,
        isMutating: Bool = false,
        skills: [HermexSkillDTO] = [],
        selectedSkillName: String? = nil,
        selectedSkillDetail: HermexSkillDetailDTO? = nil,
        selectedSkillFileName: String? = nil,
        selectedSkillFileContent: String? = nil,
        isLoadingSkillDetail: Bool = false,
        isLoadingSkillFile: Bool = false,
        memory: [HermexMemorySectionDTO] = [],
        insights: HermexJSONValue? = nil,
        insightsDays: Int = 30,
        selectedPanel: HermexPanel = .tasks,
        isLoading: Bool = false,
        errorMessage: String? = nil
    ) {
        self.tasks = tasks
        self.taskDraft = taskDraft
        self.selectedTaskID = selectedTaskID
        self.taskOutput = taskOutput
        self.isLoadingTaskOutput = isLoadingTaskOutput
        self.pendingTaskDeletionID = pendingTaskDeletionID
        self.isMutating = isMutating
        self.skills = skills
        self.selectedSkillName = selectedSkillName
        self.selectedSkillDetail = selectedSkillDetail
        self.selectedSkillFileName = selectedSkillFileName
        self.selectedSkillFileContent = selectedSkillFileContent
        self.isLoadingSkillDetail = isLoadingSkillDetail
        self.isLoadingSkillFile = isLoadingSkillFile
        self.memory = memory
        self.insights = insights
        self.insightsDays = insightsDays
        self.selectedPanel = selectedPanel
        self.isLoading = isLoading
        self.errorMessage = errorMessage
    }

#if !SKIP
    private enum CodingKeys: String, CodingKey {
        case tasks
        case taskDraft
        case selectedTaskID
        case taskOutput
        case isLoadingTaskOutput
        case pendingTaskDeletionID
        case isMutating
        case skills
        case selectedSkillName
        case selectedSkillDetail
        case selectedSkillFileName
        case selectedSkillFileContent
        case isLoadingSkillDetail
        case isLoadingSkillFile
        case memory
        case insights
        case insightsDays
        case selectedPanel
        case isLoading
        case errorMessage
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.tasks = try container.decodeIfPresent([HermexTaskDTO].self, forKey: .tasks) ?? []
        self.taskDraft = try container.decodeIfPresent(HermexTaskDraft.self, forKey: .taskDraft)
        self.selectedTaskID = try container.decodeIfPresent(String.self, forKey: .selectedTaskID)
        self.taskOutput = try container.decodeIfPresent(HermexJSONValue.self, forKey: .taskOutput)
        self.isLoadingTaskOutput = try container.decodeIfPresent(Bool.self, forKey: .isLoadingTaskOutput) ?? false
        self.pendingTaskDeletionID = try container.decodeIfPresent(String.self, forKey: .pendingTaskDeletionID)
        self.isMutating = try container.decodeIfPresent(Bool.self, forKey: .isMutating) ?? false
        self.skills = try container.decodeIfPresent([HermexSkillDTO].self, forKey: .skills) ?? []
        self.selectedSkillName = try container.decodeIfPresent(String.self, forKey: .selectedSkillName)
        self.selectedSkillDetail = try container.decodeIfPresent(HermexSkillDetailDTO.self, forKey: .selectedSkillDetail)
        self.selectedSkillFileName = try container.decodeIfPresent(String.self, forKey: .selectedSkillFileName)
        self.selectedSkillFileContent = try container.decodeIfPresent(String.self, forKey: .selectedSkillFileContent)
        self.isLoadingSkillDetail = try container.decodeIfPresent(Bool.self, forKey: .isLoadingSkillDetail) ?? false
        self.isLoadingSkillFile = try container.decodeIfPresent(Bool.self, forKey: .isLoadingSkillFile) ?? false
        self.memory = try container.decodeIfPresent([HermexMemorySectionDTO].self, forKey: .memory) ?? []
        self.insights = try container.decodeIfPresent(HermexJSONValue.self, forKey: .insights)
        self.insightsDays = try container.decodeIfPresent(Int.self, forKey: .insightsDays) ?? 30
        self.selectedPanel = try container.decodeIfPresent(HermexPanel.self, forKey: .selectedPanel) ?? .tasks
        self.isLoading = try container.decodeIfPresent(Bool.self, forKey: .isLoading) ?? false
        self.errorMessage = try container.decodeIfPresent(String.self, forKey: .errorMessage)
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(tasks, forKey: .tasks)
        try container.encodeIfPresent(taskDraft, forKey: .taskDraft)
        try container.encodeIfPresent(selectedTaskID, forKey: .selectedTaskID)
        try container.encodeIfPresent(taskOutput, forKey: .taskOutput)
        try container.encode(isLoadingTaskOutput, forKey: .isLoadingTaskOutput)
        try container.encodeIfPresent(pendingTaskDeletionID, forKey: .pendingTaskDeletionID)
        try container.encode(isMutating, forKey: .isMutating)
        try container.encode(skills, forKey: .skills)
        try container.encodeIfPresent(selectedSkillName, forKey: .selectedSkillName)
        try container.encodeIfPresent(selectedSkillDetail, forKey: .selectedSkillDetail)
        try container.encodeIfPresent(selectedSkillFileName, forKey: .selectedSkillFileName)
        try container.encodeIfPresent(selectedSkillFileContent, forKey: .selectedSkillFileContent)
        try container.encode(isLoadingSkillDetail, forKey: .isLoadingSkillDetail)
        try container.encode(isLoadingSkillFile, forKey: .isLoadingSkillFile)
        try container.encode(memory, forKey: .memory)
        try container.encodeIfPresent(insights, forKey: .insights)
        try container.encode(insightsDays, forKey: .insightsDays)
        try container.encode(selectedPanel, forKey: .selectedPanel)
        try container.encode(isLoading, forKey: .isLoading)
        try container.encodeIfPresent(errorMessage, forKey: .errorMessage)
    }
#endif
}

public enum HermexPanel: String, HermexStateCodable, Equatable, Sendable {
    case tasks
    case skills
    case memory
    case insights
}

public struct HermexTaskDTO: HermexStateCodable, Identifiable, Equatable, Sendable {
    public var id: String
    public var title: String?
    public var status: String?
    public var schedule: String?
    public var prompt: String?
    public var deliver: String?
    public var skills: [String]?
    public var model: String?
    public var profile: String?
    public var toastNotifications: Bool?

    public init(
        id: String,
        title: String? = nil,
        status: String? = nil,
        schedule: String? = nil,
        prompt: String? = nil,
        deliver: String? = nil,
        skills: [String]? = nil,
        model: String? = nil,
        profile: String? = nil,
        toastNotifications: Bool? = nil
    ) {
        self.id = id
        self.title = title
        self.status = status
        self.schedule = schedule
        self.prompt = prompt
        self.deliver = deliver
        self.skills = skills
        self.model = model
        self.profile = profile
        self.toastNotifications = toastNotifications
    }
}

public struct HermexTaskDraft: HermexStateCodable, Equatable, Sendable {
    public var editingJobID: String?
    public var name: String
    public var prompt: String
    public var schedule: String
    public var deliver: String
    public var skillsText: String
    public var model: String
    public var profile: String
    public var toastNotifications: Bool

    public init(
        editingJobID: String? = nil,
        name: String = "",
        prompt: String = "",
        schedule: String = "",
        deliver: String = "local",
        skillsText: String = "",
        model: String = "",
        profile: String = "",
        toastNotifications: Bool = true
    ) {
        self.editingJobID = editingJobID
        self.name = name
        self.prompt = prompt
        self.schedule = schedule
        self.deliver = deliver
        self.skillsText = skillsText
        self.model = model
        self.profile = profile
        self.toastNotifications = toastNotifications
    }

    public init(task: HermexTaskDTO) {
        self.init(
            editingJobID: task.id,
            name: task.title ?? "",
            prompt: task.prompt ?? "",
            schedule: task.schedule ?? "",
            deliver: task.deliver ?? "local",
            skillsText: task.skills?.joined(separator: ", ") ?? "",
            model: task.model ?? "",
            profile: task.profile ?? "",
            toastNotifications: task.toastNotifications ?? true
        )
    }

    public var isEditing: Bool { editingJobID != nil }

    public var trimmedName: String? { Self.nonEmpty(name) }

    public var trimmedPrompt: String {
        prompt.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines)
    }

    public var trimmedSchedule: String {
        schedule.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines)
    }

    public var trimmedDeliver: String? { Self.nonEmpty(deliver) }

    public var trimmedModel: String? { Self.nonEmpty(model) }

    public var trimmedProfile: String? { Self.nonEmpty(profile) }

    public var skills: [String] {
        skillsText
            .replacingOccurrences(of: "\n", with: ",")
            .components(separatedBy: ",")
            .map { $0.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
    }

    public var validationMessage: String? {
        if trimmedPrompt.isEmpty { return "Prompt is required." }
        if trimmedSchedule.isEmpty { return "Schedule is required." }
        return nil
    }

    private static func nonEmpty(_ value: String) -> String? {
        let trimmed = value.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}

public struct HermexSkillDTO: HermexStateCodable, Identifiable, Equatable, Sendable {
    public var id: String { name }
    public var name: String
    public var enabled: Bool?
    public var summary: String?
    public var category: String?
    public var description: String?
    public var path: String?
    public var disabled: Bool?
    public var tags: [String]?
    public var relatedSkills: [String]?

    public var isEnabled: Bool? {
        if let enabled { return enabled }
        if let disabled { return !disabled }
        return nil
    }

    public init(
        name: String,
        enabled: Bool? = nil,
        summary: String? = nil,
        category: String? = nil,
        description: String? = nil,
        path: String? = nil,
        disabled: Bool? = nil,
        tags: [String]? = nil,
        relatedSkills: [String]? = nil
    ) {
        self.name = name
        self.enabled = enabled
        self.summary = summary
        self.category = category
        self.description = description
        self.path = path
        self.disabled = disabled
        self.tags = tags
        self.relatedSkills = relatedSkills
    }
}

public struct HermexSkillDetailDTO: HermexStateCodable, Equatable, Sendable {
    public var name: String?
    public var content: String?
    public var linkedFiles: [String]
    public var error: String?

    public init(
        name: String? = nil,
        content: String? = nil,
        linkedFiles: [String] = [],
        error: String? = nil
    ) {
        self.name = name
        self.content = content
        self.linkedFiles = linkedFiles
        self.error = error
    }
}

public struct HermexMemorySectionDTO: HermexStateCodable, Identifiable, Equatable, Sendable {
    public var id: String { section }
    public var section: String
    public var content: String

    public init(section: String, content: String) {
        self.section = section
        self.content = content
    }
}

public struct HermexLocalSettings: Codable, Equatable, Sendable {
    public var appTheme: String
    public var hapticsEnabled: Bool
    public var glassEnabled: Bool
    public var notificationsEnabled: Bool

    public init(
        appTheme: String = "system",
        hapticsEnabled: Bool = true,
        glassEnabled: Bool = true,
        notificationsEnabled: Bool = false
    ) {
        let normalizedTheme = appTheme.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines).lowercased()
        self.appTheme = ["system", "light", "dark"].contains(normalizedTheme) ? normalizedTheme : "system"
        self.hapticsEnabled = hapticsEnabled
        self.glassEnabled = glassEnabled
        self.notificationsEnabled = notificationsEnabled
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let rawTheme = try container.decodeIfPresent(String.self, forKey: .appTheme) ?? "system"
        let normalizedTheme = rawTheme.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines).lowercased()
        self.appTheme = ["system", "light", "dark"].contains(normalizedTheme) ? normalizedTheme : "system"
        self.hapticsEnabled = try container.decodeIfPresent(Bool.self, forKey: .hapticsEnabled) ?? true
        self.glassEnabled = try container.decodeIfPresent(Bool.self, forKey: .glassEnabled) ?? true
        self.notificationsEnabled = try container.decodeIfPresent(Bool.self, forKey: .notificationsEnabled) ?? false
    }

    public init(settings: HermexSettingsState) {
        self.init(
            appTheme: settings.appTheme,
            hapticsEnabled: settings.hapticsEnabled,
            glassEnabled: settings.glassEnabled,
            notificationsEnabled: settings.notificationsEnabled
        )
    }

    private enum CodingKeys: String, CodingKey {
        case appTheme
        case hapticsEnabled
        case glassEnabled
        case notificationsEnabled
    }
}

public struct HermexSettingsState: HermexStateCodable, Equatable, Sendable {
    public var activeServer: HermexServerIdentity?
    public var servers: [HermexServerIdentity]
    public var appTheme: String
    public var defaultModel: String?
    public var defaultProfile: String?
    public var availableModels: [HermexModelOption]?
    public var availableProfiles: [HermexProfileOption]?
    public var isSingleProfileMode: Bool?
    public var showDefaultModelPicker: Bool?
    public var showDefaultProfilePicker: Bool?
    public var isLoadingDefaultConfiguration: Bool?
    public var isSavingDefaultConfiguration: Bool?
    public var showClearCacheConfirmation: Bool?
    public var isClearingCache: Bool?
    public var cacheStatusMessage: String?
    public var hapticsEnabled: Bool
    public var glassEnabled: Bool
    public var notificationsEnabled: Bool
    public var showCliSessions: Bool
    public var isSavingShowCliSessions: Bool
    public var isSavingServer: Bool
    public var settingsErrorMessage: String?

    public init(
        activeServer: HermexServerIdentity? = nil,
        servers: [HermexServerIdentity] = [],
        appTheme: String = "system",
        defaultModel: String? = nil,
        defaultProfile: String? = nil,
        availableModels: [HermexModelOption]? = nil,
        availableProfiles: [HermexProfileOption]? = nil,
        isSingleProfileMode: Bool? = nil,
        showDefaultModelPicker: Bool? = nil,
        showDefaultProfilePicker: Bool? = nil,
        isLoadingDefaultConfiguration: Bool? = nil,
        isSavingDefaultConfiguration: Bool? = nil,
        showClearCacheConfirmation: Bool? = nil,
        isClearingCache: Bool? = nil,
        cacheStatusMessage: String? = nil,
        hapticsEnabled: Bool = true,
        glassEnabled: Bool = true,
        notificationsEnabled: Bool = false,
        showCliSessions: Bool = true,
        isSavingShowCliSessions: Bool = false,
        isSavingServer: Bool = false,
        settingsErrorMessage: String? = nil
    ) {
        self.activeServer = activeServer
        self.servers = servers
        self.appTheme = appTheme
        self.defaultModel = defaultModel
        self.defaultProfile = defaultProfile
        self.availableModels = availableModels
        self.availableProfiles = availableProfiles
        self.isSingleProfileMode = isSingleProfileMode
        self.showDefaultModelPicker = showDefaultModelPicker
        self.showDefaultProfilePicker = showDefaultProfilePicker
        self.isLoadingDefaultConfiguration = isLoadingDefaultConfiguration
        self.isSavingDefaultConfiguration = isSavingDefaultConfiguration
        self.showClearCacheConfirmation = showClearCacheConfirmation
        self.isClearingCache = isClearingCache
        self.cacheStatusMessage = cacheStatusMessage
        self.hapticsEnabled = hapticsEnabled
        self.glassEnabled = glassEnabled
        self.notificationsEnabled = notificationsEnabled
        self.showCliSessions = showCliSessions
        self.isSavingShowCliSessions = isSavingShowCliSessions
        self.isSavingServer = isSavingServer
        self.settingsErrorMessage = settingsErrorMessage
    }
}
