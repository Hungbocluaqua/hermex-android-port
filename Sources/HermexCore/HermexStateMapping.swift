import Foundation

public extension HermexJSONValue {
    var objectValue: [String: HermexJSONValue]? {
        guard case .dictionary(let value) = self else { return nil }
        return value
    }

    var arrayValue: [HermexJSONValue]? {
        guard case .array(let value) = self else { return nil }
        return value
    }

    var stringValue: String? {
        guard case .string(let value) = self else { return nil }
        let trimmed = value.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }

    var boolValue: Bool? {
        guard case .bool(let value) = self else { return nil }
        return value
    }

    var intValue: Int? {
        switch self {
        case .number(let value):
            return Int(value)
        case .string(let value):
            return Int(value.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines))
        default:
            return nil
        }
    }
}

public extension Dictionary where Key == String, Value == HermexJSONValue {
    func stringValue(_ keys: String...) -> String? {
        for key in keys {
            if let value = self[key]?.stringValue { return value }
        }
        return nil
    }

    func boolValue(_ keys: String...) -> Bool? {
        for key in keys {
            if let value = self[key]?.boolValue { return value }
        }
        return nil
    }

    func intValue(_ keys: String...) -> Int? {
        for key in keys {
            if let value = self[key]?.intValue { return value }
        }
        return nil
    }

    func arrayValue(_ keys: String...) -> [HermexJSONValue] {
        for key in keys {
            if let array = self[key]?.arrayValue { return array }
        }
        return []
    }
}

public extension HermexWorkspaceState {
    static func fromDirectoryResponse(_ response: HermexJSONValue, fallbackPath: String?) -> HermexWorkspaceState {
        let fields = response.objectValue ?? [:]
        var entries: [HermexWorkspaceEntryDTO] = []
        for entry in fields.arrayValue("entries", "files", "items") {
            if let mappedEntry = HermexWorkspaceEntryDTO.fromJSON(entry) {
                entries.append(mappedEntry)
            }
        }
        return HermexWorkspaceState(
            currentPath: fields.stringValue("path", "workspace") ?? fallbackPath,
            entries: entries,
            isLoading: false,
            errorMessage: fields.stringValue("error")
        )
    }
}

public extension HermexWorkspaceEntryDTO {
    static func fromJSON(_ value: HermexJSONValue) -> HermexWorkspaceEntryDTO? {
        guard let fields = value.objectValue else { return nil }
        let path = fields.stringValue("path", "workspace_path")
        let name = fields.stringValue("name") ?? path?.split(separator: "/").last.map { String($0) }
        guard let resolvedPath = path, let resolvedName = name else { return nil }
        let type = fields.stringValue("type", "kind")
        let isDirectory = fields.boolValue("is_directory", "is_dir", "directory")
            ?? (type == "dir" || type == "directory")
        return HermexWorkspaceEntryDTO(
            name: resolvedName,
            path: resolvedPath,
            type: type,
            isDirectory: isDirectory,
            size: fields.intValue("size")
        )
    }
}

public extension HermexFilePreview {
    static func fromJSON(_ value: HermexJSONValue, fallbackPath: String) -> HermexFilePreview {
        let fields = value.objectValue ?? [:]
        return HermexFilePreview(
            path: fields.stringValue("path", "name") ?? fallbackPath,
            content: fields.stringValue("content", "text"),
            mimeType: fields.stringValue("mime_type", "mime", "language"),
            isBinary: fields.boolValue("is_binary", "binary") ?? false
        )
    }
}

public extension HermexGitState {
    static func fromStatusResponse(_ response: HermexJSONValue) -> HermexGitState {
        let fields = response.objectValue ?? [:]
        var files: [HermexGitFileChange] = []
        for file in fields.arrayValue("files", "changes", "status") {
            if let mappedFile = HermexGitFileChange.fromJSON(file) {
                files.append(mappedFile)
            }
        }
        return HermexGitState(
            isRepository: fields.boolValue("is_repo", "is_repository", "repository") ?? true,
            branch: fields.stringValue("branch", "current_branch", "head"),
            upstream: fields.stringValue("upstream", "tracking"),
            ahead: fields.intValue("ahead"),
            behind: fields.intValue("behind"),
            files: files,
            errorMessage: fields.stringValue("error")
        )
    }

    static func diffText(from response: HermexJSONValue) -> String? {
        if let text = response.stringValue { return text }
        let fields = response.objectValue ?? [:]
        return fields.stringValue("diff", "patch", "text", "content")
    }

    func mergingStatus(from response: HermexJSONValue) -> HermexGitState {
        var updated = HermexGitState.fromStatusResponse(response)
        updated.diffPath = diffPath
        updated.diffText = diffText
        updated.commitMessage = commitMessage
        return updated
    }
}

public extension HermexGitFileChange {
    static func fromJSON(_ value: HermexJSONValue) -> HermexGitFileChange? {
        guard let fields = value.objectValue else { return nil }
        guard let path = fields.stringValue("path", "file", "workspace_path") else { return nil }
        return HermexGitFileChange(
            path: path,
            status: fields.stringValue("status", "code") ?? "changed",
            additions: fields.intValue("additions", "added"),
            deletions: fields.intValue("deletions", "removed"),
            isStaged: fields.boolValue("staged", "is_staged")
        )
    }
}

public extension HermexPanelsState {
    static func tasks(from response: HermexJSONValue, selectedPanel: HermexPanel = .tasks) -> HermexPanelsState {
        let fields = response.objectValue ?? [:]
        var tasks: [HermexTaskDTO] = []
        for task in fields.arrayValue("jobs", "crons", "tasks") {
            if let mappedTask = HermexTaskDTO.fromJSON(task) {
                tasks.append(mappedTask)
            }
        }
        return HermexPanelsState(tasks: tasks, selectedPanel: selectedPanel, errorMessage: fields.stringValue("error"))
    }

    static func skills(from response: HermexJSONValue, selectedPanel: HermexPanel = .skills) -> HermexPanelsState {
        let fields = response.objectValue ?? [:]
        var skills: [HermexSkillDTO] = []
        for skill in fields.arrayValue("skills", "items") {
            if let mappedSkill = HermexSkillDTO.fromJSON(skill) {
                skills.append(mappedSkill)
            }
        }
        return HermexPanelsState(skills: skills, selectedPanel: selectedPanel, errorMessage: fields.stringValue("error"))
    }

    static func memory(from response: HermexJSONValue, selectedPanel: HermexPanel = .memory) -> HermexPanelsState {
        let fields = response.objectValue ?? [:]
        var sections: [HermexMemorySectionDTO] = []
        for section in fields.arrayValue("sections", "memory") {
            if let mappedSection = HermexMemorySectionDTO.fromJSON(section) {
                sections.append(mappedSection)
            }
        }
        var objectSections: [HermexMemorySectionDTO] = []
        for (key, value) in fields {
            if !["sections", "memory", "error"].contains(key), let content = value.stringValue {
                objectSections.append(HermexMemorySectionDTO(section: key, content: content))
            }
        }
        return HermexPanelsState(memory: sections + objectSections, selectedPanel: selectedPanel, errorMessage: fields.stringValue("error"))
    }
}

public extension HermexTaskDTO {
    static func fromJSON(_ value: HermexJSONValue) -> HermexTaskDTO? {
        guard let fields = value.objectValue else { return nil }
        guard let id = fields.stringValue("id", "job_id", "name") else { return nil }
        return HermexTaskDTO(
            id: id,
            title: fields.stringValue("title", "name", "command"),
            status: fields.stringValue("status", "state"),
            schedule: fields.stringValue("schedule", "cron", "next_run")
        )
    }
}

public extension HermexSkillDTO {
    static func fromJSON(_ value: HermexJSONValue) -> HermexSkillDTO? {
        guard let fields = value.objectValue else { return nil }
        guard let name = fields.stringValue("name", "id") else { return nil }
        return HermexSkillDTO(
            name: name,
            enabled: fields.boolValue("enabled", "is_enabled"),
            summary: fields.stringValue("summary", "description")
        )
    }
}

public extension HermexMemorySectionDTO {
    static func fromJSON(_ value: HermexJSONValue) -> HermexMemorySectionDTO? {
        guard let fields = value.objectValue else { return nil }
        guard let section = fields.stringValue("section", "name", "id") else { return nil }
        return HermexMemorySectionDTO(section: section, content: fields.stringValue("content", "text", "value") ?? "")
    }
}
