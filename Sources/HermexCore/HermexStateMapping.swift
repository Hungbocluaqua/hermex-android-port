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
        for section in fields.arrayValue("sections", "items") {
            if let mappedSection = HermexMemorySectionDTO.fromJSON(section) {
                sections.append(mappedSection)
            }
        }

        func appendSection(_ section: String, keys: [String]) {
            guard !sections.contains(where: { $0.section == section }) else { return }
            for key in keys {
                guard let value = fields[key] else { continue }
                if case .string(let content) = value {
                    sections.append(HermexMemorySectionDTO(section: section, content: content))
                    return
                }
            }
        }

        appendSection("memory", keys: ["memory"])
        appendSection("user", keys: ["user"])
        appendSection("soul", keys: ["soul"])
        appendSection("project_context", keys: ["project_context", "projectContext"])

        return HermexPanelsState(memory: sections, selectedPanel: selectedPanel, errorMessage: fields.stringValue("error"))
    }
}

public extension HermexTaskDTO {
    static func fromJSON(_ value: HermexJSONValue) -> HermexTaskDTO? {
        guard let fields = value.objectValue else { return nil }
        guard let id = fields.stringValue("id", "job_id", "name") else { return nil }
        let scheduleFields = fields["schedule"]?.objectValue ?? [:]
        let schedule = fields.stringValue("schedule", "cron", "next_run")
            ?? scheduleFields.stringValue("expression", "expr", "run_at", "runAt", "every", "kind")
        let status = fields.stringValue("status", "state", "last_status")
            ?? (fields.boolValue("paused") == true ? "paused" : nil)
        return HermexTaskDTO(
            id: id,
            title: fields.stringValue("title", "name", "command"),
            status: status,
            schedule: schedule,
            prompt: fields.stringValue("prompt"),
            deliver: fields.stringValue("deliver"),
            skills: fields.arrayValue("skills").compactMap { $0.stringValue },
            model: fields.stringValue("model"),
            profile: fields.stringValue("profile"),
            toastNotifications: fields.boolValue("toast_notifications", "toastNotifications")
        )
    }
}

public extension HermexSkillDTO {
    static func fromJSON(_ value: HermexJSONValue) -> HermexSkillDTO? {
        guard let fields = value.objectValue else { return nil }
        guard let name = fields.stringValue("name", "id") else { return nil }
        let disabled = fields.boolValue("disabled", "is_disabled")
        let enabled: Bool?
        if let explicitEnabled = fields.boolValue("enabled", "is_enabled") {
            enabled = explicitEnabled
        } else if let disabled {
            enabled = !disabled
        } else {
            enabled = nil
        }
        return HermexSkillDTO(
            name: name,
            enabled: enabled,
            summary: fields.stringValue("summary", "description"),
            category: fields.stringValue("category"),
            description: fields.stringValue("description", "summary"),
            path: fields.stringValue("path"),
            disabled: disabled,
            tags: fields.arrayValue("tags").compactMap { $0.stringValue },
            relatedSkills: fields.arrayValue("related_skills", "relatedSkills").compactMap { $0.stringValue }
        )
    }
}

public extension HermexSkillDetailDTO {
    static func fromJSON(_ value: HermexJSONValue) -> HermexSkillDetailDTO? {
        guard let fields = value.objectValue else { return nil }
        var linkedFiles = fields.arrayValue("files").compactMap { $0.stringValue }
        if let linked = fields["linked_files"] {
            linkedFiles.append(contentsOf: skillLinkedFileNames(from: linked))
        }

        return HermexSkillDetailDTO(
            name: fields.stringValue("name"),
            content: fields.stringValue("content"),
            linkedFiles: uniqueSkillFileNames(linkedFiles),
            error: fields.stringValue("error")
        )
    }
}

func skillLinkedFileNames(from value: HermexJSONValue) -> [String] {
    switch value {
    case .string(let name):
        let trimmed = name.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines)
        return trimmed.isEmpty ? [] : [trimmed]
    case .array(let values):
        return values.flatMap { skillLinkedFileNames(from: $0) }
    case .dictionary(let fields):
        var names: [String] = []
        for (key, nested) in fields {
            switch nested {
            case .string:
                let trimmed = key.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines)
                if !trimmed.isEmpty { names.append(trimmed) }
            default:
                names.append(contentsOf: skillLinkedFileNames(from: nested))
            }
        }
        return names
    default:
        return []
    }
}

func uniqueSkillFileNames(_ names: [String]) -> [String] {
    var unique: [String] = []
    for name in names {
        if !unique.contains(name) {
            unique.append(name)
        }
    }
    return unique.sorted()
}

public extension HermexMemorySectionDTO {
    static func fromJSON(_ value: HermexJSONValue) -> HermexMemorySectionDTO? {
        guard let fields = value.objectValue else { return nil }
        guard let section = fields.stringValue("section", "name", "id") else { return nil }
        return HermexMemorySectionDTO(section: section, content: fields.stringValue("content", "text", "value") ?? "")
    }
}
