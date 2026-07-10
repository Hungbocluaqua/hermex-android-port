import SwiftUI
import Foundation
import HermexCore

public struct HermexPanelsScreen: View {
    private let state: HermexPanelsState
    private let onEvent: (HermexUIEvent) -> Void

    @State private var skillSearchText = ""
    @State private var editingMemorySection: String?
    @State private var memoryDraft = ""

    public init(state: HermexPanelsState, onEvent: @escaping (HermexUIEvent) -> Void = { _ in }) {
        self.state = state
        self.onEvent = onEvent
    }

    public var body: some View {
        ZStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    topBar
                    selectedPanelCard
                }
                .padding(.horizontal, 16)
                .padding(.top, 12)
                .padding(.bottom, 32)
            }

            if state.taskDraft != nil {
                taskEditorOverlay
            }

            if let task = selectedTask {
                taskDetailOverlay(task)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(HermexUIColors.systemBackground.ignoresSafeArea())
        .foregroundStyle(HermexUIColors.primaryText)
        .alert("Delete Task?", isPresented: deleteConfirmationBinding) {
            Button("Cancel", role: .cancel) {
                onEvent(.cancelTaskDeletion)
            }
            Button("Delete", role: .destructive) {
                onEvent(.confirmTaskDeletion)
            }
        } message: {
            Text("This scheduled task will be removed from the Hermes server.")
        }
    }

    private var topBar: some View {
        HStack(spacing: 12) {
            HermexCircleIconButton(systemImage: "chevron.left", accessibilityLabel: "Back", action: { onEvent(.openRoute(.sessions)) })
            HermexScreenTitle("Server Panels", subtitle: state.selectedPanel.rawValue.capitalized)
            Spacer()
            HermexCircleIconButton(systemImage: "arrow.clockwise", accessibilityLabel: "Refresh", action: { onEvent(.refresh) })
        }
    }

    private var selectedPanelCard: some View {
        HermexGlassPanel(cornerRadius: 18) {
            VStack(alignment: .leading, spacing: 18) {
                Text(state.selectedPanel.rawValue.capitalized)
                    .font(.title.weight(.bold))
                    .foregroundStyle(HermexUIColors.primaryText)
                    .frame(maxWidth: .infinity, alignment: .leading)

                if state.isLoading {
                    ProgressView()
                }

                if let error = state.errorMessage, !error.isEmpty {
                    panelNotice(error, systemImage: "exclamationmark.triangle")
                }

                switch state.selectedPanel {
                case .tasks:
                    taskRows
                case .skills:
                    skillRows
                case .memory:
                    memoryRows
                case .insights:
                    insightsPanel
                }
            }
            .padding(16)
            .foregroundStyle(HermexUIColors.primaryText)
        }
    }

    private var taskRows: some View {
        VStack(alignment: .leading, spacing: 16) {
            Button {
                onEvent(.beginTaskCreation)
            } label: {
                Text("New Task")
                    .font(.headline.weight(.semibold))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 18)
                    .frame(height: 52)
                    .background(Color.blue, in: Capsule())
            }
            .buttonStyle(.plain)

            if state.tasks.isEmpty {
                panelNotice("Scheduled jobs from the Hermes server will appear here.", systemImage: "calendar.badge.clock")
            }

            ForEach(state.tasks) { task in
                VStack(alignment: .leading, spacing: 10) {
                    HStack(alignment: .firstTextBaseline, spacing: 10) {
                        Text(task.title ?? task.id)
                            .font(.headline.weight(.semibold))
                            .foregroundStyle(HermexUIColors.primaryText)
                            .fixedSize(horizontal: false, vertical: true)
                        Spacer(minLength: 8)
                        statusBadge(task.status ?? "Scheduled")
                    }

                    if let schedule = task.schedule, !schedule.isEmpty {
                        metricRow("Schedule", schedule)
                    }

                    if let prompt = task.prompt, !prompt.isEmpty {
                        Text(prompt)
                            .font(.subheadline)
                            .foregroundStyle(HermexUIColors.secondaryText)
                            .lineLimit(3)
                    }

                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            panelActionButton("Details", isProminent: true, disabled: state.isMutating) {
                                onEvent(.taskCommand(.loadOutput(jobID: task.id, limit: 5)))
                            }
                            panelActionButton("Edit", disabled: state.isMutating) {
                                onEvent(.beginTaskEdit(jobID: task.id))
                            }
                            panelActionButton("Run", disabled: state.isMutating) {
                                onEvent(.taskCommand(.run(jobID: task.id)))
                            }
                            panelActionButton(taskPauseResumeTitle(task), disabled: state.isMutating) {
                                onEvent(.taskCommand(taskPauseResumeCommand(task)))
                            }
                            panelActionButton("Output", disabled: state.isMutating) {
                                onEvent(.taskCommand(.loadOutput(jobID: task.id, limit: 5)))
                            }
                            panelActionButton("Delete", disabled: state.isMutating) {
                                onEvent(.requestTaskDeletion(jobID: task.id))
                            }
                        }
                        .padding(.trailing, 8)
                    }
                }
                .padding(.vertical, 8)
            }
        }
    }

    private var taskEditorOverlay: some View {
        let draft = taskDraftBinding

        return ZStack {
            Color.black.opacity(0.52)
                .ignoresSafeArea()

            ScrollView {
                HermexGlassPanel(cornerRadius: 18) {
                    VStack(alignment: .leading, spacing: 16) {
                        HStack {
                            Text(draft.wrappedValue.isEditing ? "Edit Task" : "New Task")
                                .font(.title2.weight(.bold))
                            Spacer()
                            panelActionButton("Cancel", disabled: state.isMutating) {
                                onEvent(.cancelTaskEditor)
                            }
                        }

                        taskEditorField("Name", text: taskDraftTextBinding(for: "name"))
                        taskEditorTextEditor("Prompt", text: taskDraftTextBinding(for: "prompt"), minHeight: 108)
                        taskEditorField("Schedule", text: taskDraftTextBinding(for: "schedule"))

                        Text("Delivery")
                            .font(.caption.weight(.bold))
                            .textCase(.uppercase)
                            .foregroundStyle(HermexUIColors.secondaryText)
                        taskEditorField("Deliver", text: taskDraftTextBinding(for: "deliver"))
                        Toggle("Toast Notifications", isOn: taskToastNotificationsBinding)
                            .tint(.blue)

                        Text("Configuration")
                            .font(.caption.weight(.bold))
                            .textCase(.uppercase)
                            .foregroundStyle(HermexUIColors.secondaryText)
                        taskEditorTextEditor("Skills", text: taskDraftTextBinding(for: "skillsText"), minHeight: 72)
                        taskEditorField("Model", text: taskDraftTextBinding(for: "model"))
                        taskEditorField("Profile", text: taskDraftTextBinding(for: "profile"))

                        if let validationMessage = draft.wrappedValue.validationMessage {
                            Text(validationMessage)
                                .font(.footnote)
                                .foregroundStyle(Color.orange)
                        }

                        HStack {
                            Spacer()
                            panelActionButton(
                                draft.wrappedValue.isEditing ? "Save Changes" : "Create",
                                isProminent: true,
                                disabled: state.isMutating || draft.wrappedValue.validationMessage != nil
                            ) {
                                let command: HermexTaskCommand = draft.wrappedValue.isEditing
                                    ? .update(draft: draft.wrappedValue)
                                    : .create(draft: draft.wrappedValue)
                                onEvent(.taskCommand(command))
                            }
                        }
                    }
                    .padding(18)
                }
                .padding(16)
            }
        }
    }

    private func taskDetailOverlay(_ task: HermexTaskDTO) -> some View {
        ZStack {
            Color.black.opacity(0.52)
                .ignoresSafeArea()

            ScrollView {
                HermexGlassPanel(cornerRadius: 18) {
                    VStack(alignment: .leading, spacing: 16) {
                        HStack(alignment: .firstTextBaseline, spacing: 10) {
                            Text(task.title ?? task.id)
                                .font(.title2.weight(.bold))
                                .fixedSize(horizontal: false, vertical: true)
                            Spacer(minLength: 8)
                            statusBadge(task.status ?? "Scheduled")
                        }

                        if let prompt = task.prompt, !prompt.isEmpty {
                            Text(prompt)
                                .font(.body)
                                .foregroundStyle(HermexUIColors.secondaryText)
                                .fixedSize(horizontal: false, vertical: true)
                        }

                        VStack(alignment: .leading, spacing: 8) {
                            Text("Details")
                                .font(.caption.weight(.bold))
                                .textCase(.uppercase)
                                .foregroundStyle(HermexUIColors.secondaryText)
                            if let schedule = task.schedule, !schedule.isEmpty {
                                metricRow("Schedule", schedule)
                            }
                            metricRow("Deliver", task.deliver ?? "local")
                            if let model = task.model, !model.isEmpty {
                                metricRow("Model", model)
                            }
                            if let profile = task.profile, !profile.isEmpty {
                                metricRow("Profile", profile)
                            }
                            if let skills = task.skills, !skills.isEmpty {
                                metricRow("Skills", skills.joined(separator: ", "))
                            }
                            metricRow("Toasts", task.toastNotifications == false ? "Off" : "On")
                        }

                        VStack(alignment: .leading, spacing: 8) {
                            Text("Recent Output")
                                .font(.caption.weight(.bold))
                                .textCase(.uppercase)
                                .foregroundStyle(HermexUIColors.secondaryText)
                            if state.isLoadingTaskOutput {
                                ProgressView("Loading output...")
                            } else if let outputError = taskOutputError {
                                Text(outputError)
                                    .font(.footnote)
                                    .foregroundStyle(Color.red)
                            } else if taskOutputItems.isEmpty {
                                Text("This task has not produced any output yet.")
                                    .font(.footnote)
                                    .foregroundStyle(HermexUIColors.secondaryText)
                            } else {
                                ForEach(Array(taskOutputItems.enumerated()), id: \.offset) { _, item in
                                    VStack(alignment: .leading, spacing: 6) {
                                        Text(jsonText(item["filename"]) ?? "Untitled")
                                            .font(.subheadline.weight(.semibold))
                                        Text(jsonText(item["content"]) ?? "Empty output")
                                            .font(.system(.footnote, design: .monospaced))
                                            .foregroundStyle(HermexUIColors.primaryText)
                                            .frame(maxWidth: .infinity, alignment: .leading)
                                            .padding(10)
                                            .background(HermexUIColors.glassFillStrong, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                                    }
                                }
                            }
                        }

                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                panelActionButton("Refresh", disabled: state.isLoadingTaskOutput) {
                                    onEvent(.taskCommand(.loadOutput(jobID: task.id, limit: 5)))
                                }
                                panelActionButton("Run Now", disabled: state.isMutating) {
                                    onEvent(.taskCommand(.run(jobID: task.id)))
                                }
                                panelActionButton(taskPauseResumeTitle(task), disabled: state.isMutating) {
                                    onEvent(.taskCommand(taskPauseResumeCommand(task)))
                                }
                                panelActionButton("Edit", disabled: state.isMutating) {
                                    onEvent(.beginTaskEdit(jobID: task.id))
                                }
                                panelActionButton("Delete", disabled: state.isMutating) {
                                    onEvent(.requestTaskDeletion(jobID: task.id))
                                }
                                panelActionButton("Done", isProminent: true) {
                                    onEvent(.dismissTaskDetails)
                                }
                            }
                        }
                    }
                    .padding(18)
                }
                .padding(16)
            }
        }
    }

    private func taskEditorField(_ title: String, text: Binding<String>) -> some View {
        TextField(title, text: text)
            .textFieldStyle(.plain)
            .foregroundStyle(HermexUIColors.primaryText)
            .padding(.horizontal, 14)
            .frame(minHeight: 50)
            .background(HermexUIColors.glassFillStrong, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .stroke(HermexUIColors.hairline, lineWidth: 0.7)
            }
    }

    private func taskEditorTextEditor(_ title: String, text: Binding<String>, minHeight: CGFloat) -> some View {
        TextEditor(text: text)
            .font(.body)
            .foregroundStyle(HermexUIColors.primaryText)
            .frame(minHeight: minHeight)
            .padding(8)
            .background(HermexUIColors.glassFillStrong, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            .overlay(alignment: .topLeading) {
                if text.wrappedValue.isEmpty {
                    Text(title)
                        .foregroundStyle(HermexUIColors.secondaryText)
                        .padding(.horizontal, 13)
                        .padding(.vertical, 15)
                        .allowsHitTesting(false)
                }
            }
            .overlay {
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .stroke(HermexUIColors.hairline, lineWidth: 0.7)
            }
    }

    private var skillRows: some View {
        VStack(alignment: .leading, spacing: 18) {
            TextField("Search skills...", text: $skillSearchText)
                .textFieldStyle(.plain)
                .font(.title3)
                .foregroundStyle(HermexUIColors.primaryText)
                .padding(.horizontal, 16)
                .frame(minHeight: 58)
                .background(HermexUIColors.glassFillStrong, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                .overlay {
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .stroke(HermexUIColors.hairline, lineWidth: 1)
                }

            if filteredSkills.isEmpty {
                panelNotice("Skills from the Hermes server will appear here.", systemImage: "hammer")
            } else {
                Text("Skills")
                    .font(.caption.weight(.bold))
                    .textCase(.uppercase)
                    .foregroundStyle(HermexUIColors.secondaryText)

                ForEach(filteredSkills) { skill in
                    HStack(alignment: .top, spacing: 14) {
                        panelIcon("hammer")

                        VStack(alignment: .leading, spacing: 8) {
                            Text(skill.name)
                                .font(.title3.weight(.semibold))
                                .foregroundStyle(HermexUIColors.primaryText)
                                .fixedSize(horizontal: false, vertical: true)

                            Text(skill.summary ?? (skill.enabled == true ? "Enabled" : "Disabled"))
                                .font(.body)
                                .foregroundStyle(HermexUIColors.secondaryText)
                                .lineLimit(3)

                            HStack(spacing: 8) {
                                panelActionButton("Open")
                                panelActionButton(skill.enabled == true ? "Disable" : "Enable") {
                                    onEvent(.toggleSkill(name: skill.name, enabled: skill.enabled != true))
                                }
                            }
                        }
                    }
                    .padding(.vertical, 10)
                }
            }
        }
    }

    private var memoryRows: some View {
        VStack(alignment: .leading, spacing: 18) {
            if state.memory.isEmpty {
                panelNotice("Saved memory from the Hermes server will appear here.", systemImage: "brain.head.profile")
            }

            ForEach(state.memory) { section in
                let sectionTitle = memorySectionTitle(section.section)
                let writableSection = writableMemorySection(section.section)

                VStack(alignment: .leading, spacing: 14) {
                    HStack(spacing: 10) {
                        Image(systemName: HermexSystemImageName("brain.head.profile"))
                            .foregroundStyle(HermexUIColors.primaryText)
                        Text(sectionTitle)
                            .font(.title3.weight(.semibold))
                            .foregroundStyle(HermexUIColors.primaryText)
                        Spacer()
                        if let writableSection {
                            panelActionButton("Edit \(sectionTitle)") {
                                editingMemorySection = writableSection
                                memoryDraft = section.content
                            }
                        }
                    }

                    Text(section.content.isEmpty ? "No notes saved." : section.content)
                        .font(.body)
                        .foregroundStyle(HermexUIColors.primaryText)
                        .fixedSize(horizontal: false, vertical: true)
                        .frame(maxWidth: .infinity, alignment: .leading)

                    if let writableSection, editingMemorySection == writableSection {
                        memoryEditor(section: writableSection, title: sectionTitle)
                    }
                }
                .padding(.vertical, 8)
            }
        }
    }

    private var insightsPanel: some View {
        VStack(alignment: .leading, spacing: 18) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    insightsRangeButton("Today", days: 1)
                    insightsRangeButton("Last 7 Days", days: 7)
                    insightsRangeButton("Last 30 Days", days: 30)
                    insightsRangeButton("All Time", days: 365)
                }
            }

            VStack(alignment: .leading, spacing: 10) {
                Text(insightsPeriodTitle)
                    .font(.headline.weight(.semibold))
                    .foregroundStyle(HermexUIColors.secondaryText)

                metricRow("Sessions", insightText("totalSessions", "total_sessions"))
                metricRow("Messages", insightText("totalMessages", "total_messages"))
                metricRow("Input Tokens", insightText("totalInputTokens", "total_input_tokens"))
                metricRow("Output Tokens", insightText("totalOutputTokens", "total_output_tokens"))
                metricRow("Total Tokens", insightText("totalTokens", "total_tokens"))
                metricRow("Estimated Cost", insightCurrencyText("totalCost", "total_cost"))
                metricRow("Cache Hit Rate", insightPercentText("totalCacheHitPercent", "total_cache_hit_percent"))
                metricRow("Cache Read Tokens", insightText("totalCacheReadTokens", "total_cache_read_tokens"))
            }

            if !insightModels.isEmpty {
                VStack(alignment: .leading, spacing: 12) {
                    Text("Models")
                        .font(.caption.weight(.bold))
                        .textCase(.uppercase)
                        .foregroundStyle(HermexUIColors.secondaryText)

                    ForEach(Array(insightModels.prefix(4).enumerated()), id: \.offset) { _, model in
                        insightModelRow(model)
                    }
                }
            }

            if state.insights == nil {
                panelNotice("Usage analytics from the Hermes server will appear here.", systemImage: "chart.bar")
            }
        }
    }

    private var filteredSkills: [HermexSkillDTO] {
        let query = skillSearchText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !query.isEmpty else { return state.skills }
        let normalizedQuery = query.lowercased()
        return state.skills.filter { skill in
            skill.name.lowercased().contains(normalizedQuery) ||
                (skill.summary ?? "").lowercased().contains(normalizedQuery)
        }
    }

    private func taskPauseResumeTitle(_ task: HermexTaskDTO) -> String {
        isPaused(task) ? "Resume" : "Pause"
    }

    private func taskPauseResumeCommand(_ task: HermexTaskDTO) -> HermexTaskCommand {
        isPaused(task) ? .resume(jobID: task.id) : .pause(jobID: task.id)
    }

    private func isPaused(_ task: HermexTaskDTO) -> Bool {
        (task.status ?? "").lowercased().contains("pause")
    }

    private var selectedTask: HermexTaskDTO? {
        guard let selectedTaskID = state.selectedTaskID else { return nil }
        return state.tasks.first(where: { $0.id == selectedTaskID })
    }

    private var taskDraftBinding: Binding<HermexTaskDraft> {
        Binding(
            get: { state.taskDraft ?? HermexTaskDraft() },
            set: { onEvent(.updateTaskDraft($0)) }
        )
    }

    private func taskDraftTextBinding(for field: String) -> Binding<String> {
        Binding(
            get: {
                let draft = state.taskDraft ?? HermexTaskDraft()
                switch field {
                case "name":
                    return draft.name
                case "prompt":
                    return draft.prompt
                case "schedule":
                    return draft.schedule
                case "deliver":
                    return draft.deliver
                case "skillsText":
                    return draft.skillsText
                case "model":
                    return draft.model
                case "profile":
                    return draft.profile
                default:
                    return ""
                }
            },
            set: { value in
                var draft = state.taskDraft ?? HermexTaskDraft()
                switch field {
                case "name":
                    draft.name = value
                case "prompt":
                    draft.prompt = value
                case "schedule":
                    draft.schedule = value
                case "deliver":
                    draft.deliver = value
                case "skillsText":
                    draft.skillsText = value
                case "model":
                    draft.model = value
                case "profile":
                    draft.profile = value
                default:
                    return
                }
                onEvent(.updateTaskDraft(draft))
            }
        )
    }

    private var taskToastNotificationsBinding: Binding<Bool> {
        Binding(
            get: { state.taskDraft?.toastNotifications ?? false },
            set: { value in
                var draft = state.taskDraft ?? HermexTaskDraft()
                draft.toastNotifications = value
                onEvent(.updateTaskDraft(draft))
            }
        )
    }

    private var deleteConfirmationBinding: Binding<Bool> {
        Binding(
            get: { state.pendingTaskDeletionID != nil },
            set: { isPresented in
                if !isPresented {
                    onEvent(.cancelTaskDeletion)
                }
            }
        )
    }

    private var taskOutputItems: [[String: HermexJSONValue]] {
        if let fields = state.taskOutput?.objectValue {
            if let outputs = fields["outputs"] {
                switch outputs {
                case .array(let values):
                    return values.compactMap { $0.objectValue }
                default:
                    return []
                }
            }
        }
        return []
    }

    private var taskOutputError: String? {
        state.taskOutput?.objectValue?.stringValue("error")
    }

    private func writableMemorySection(_ section: String) -> String? {
        let normalizedSection = section.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        switch normalizedSection {
        case "memory", "user", "soul":
            return normalizedSection
        default:
            return nil
        }
    }

    private func memorySectionTitle(_ section: String) -> String {
        switch section.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() {
        case "memory":
            return "My Notes"
        case "user":
            return "User Profile"
        case "soul":
            return "Agent Soul"
        case "project_context":
            return "Project Context"
        default:
            return section.replacingOccurrences(of: "_", with: " ").capitalized
        }
    }

    private func memoryEditor(section: String, title: String) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            TextEditor(text: $memoryDraft)
                .font(.system(.body, design: .monospaced))
                .foregroundStyle(HermexUIColors.primaryText)
                .frame(minHeight: 180)
                .padding(10)
                .background(HermexUIColors.glassFillStrong, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                .overlay {
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .stroke(HermexUIColors.hairline, lineWidth: 0.7)
                }
                .accessibilityLabel(title)

            HStack(spacing: 8) {
                panelActionButton("Cancel") {
                    editingMemorySection = nil
                    memoryDraft = ""
                }
                panelActionButton("Save", isProminent: true) {
                    onEvent(.writeMemory(section: section, content: memoryDraft))
                    editingMemorySection = nil
                }
            }
        }
    }

    private var insightsFields: [String: HermexJSONValue] {
        guard case .dictionary(let fields) = state.insights else { return [:] }
        return fields
    }

    private var insightsPeriodTitle: String {
        if let period = insightNumber("periodDays", "period_days") {
            if selectedInsightsDays == 365 {
                return "Last \(Int(period)) Days"
            }
            return insightsRangeTitle(days: Int(period))
        }
        return insightsRangeTitle(days: selectedInsightsDays)
    }

    private var insightModels: [HermexJSONValue] {
        guard case .array(let models) = insightsFields["models"] else { return [] }
        return models
    }

    private func insightsRangeButton(_ title: String, days: Int) -> some View {
        Button {
            onEvent(.selectInsightsRange(days: days))
        } label: {
            Text(title)
                .font(.headline.weight(.semibold))
                .foregroundStyle(selectedInsightsDays == days ? .white : HermexUIColors.primaryText)
                .padding(.horizontal, 18)
                .frame(height: 48)
                .background(selectedInsightsDays == days ? Color.blue : HermexUIColors.glassFillStrong, in: Capsule())
                .overlay {
                    Capsule().stroke(HermexUIColors.hairline, lineWidth: selectedInsightsDays == days ? 0.0 : 0.7)
                }
        }
        .buttonStyle(.plain)
    }

    private func panelIcon(_ systemImage: String) -> some View {
        Image(systemName: HermexSystemImageName(systemImage))
            .font(.title3.weight(.semibold))
            .foregroundStyle(HermexUIColors.primaryText)
            .frame(width: 54, height: 54)
            .background(HermexUIColors.glassFillStrong, in: Circle())
    }

    private func panelActionButton(
        _ title: String,
        isProminent: Bool = false,
        disabled: Bool = false,
        action: @escaping () -> Void = {}
    ) -> some View {
        Button(action: action) {
            Text(title)
                .font(.headline.weight(.semibold))
                .foregroundStyle(isProminent ? .white : HermexUIColors.primaryText)
                .padding(.horizontal, 16)
                .frame(height: 46)
                .background(isProminent ? Color.blue : HermexUIColors.glassFillStrong, in: Capsule())
                .overlay {
                    Capsule().stroke(HermexUIColors.hairline, lineWidth: isProminent ? 0.0 : 0.7)
                }
        }
        .buttonStyle(.plain)
        .disabled(disabled)
        .opacity(disabled ? 0.55 : 1.0)
    }

    private func statusBadge(_ status: String) -> some View {
        Text(status)
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(statusColor(status))
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(statusColor(status).opacity(0.12), in: Capsule())
    }

    private func metricRow(_ title: String, _ value: String) -> some View {
        HStack(alignment: .firstTextBaseline) {
            Text(title)
                .foregroundStyle(HermexUIColors.secondaryText)
            Spacer(minLength: 12)
            Text(value)
                .fontWeight(.semibold)
                .foregroundStyle(HermexUIColors.primaryText)
                .multilineTextAlignment(.trailing)
        }
        .font(.body)
    }

    private func insightModelRow(_ value: HermexJSONValue) -> some View {
        let fields = jsonFields(value)
        let modelName = jsonText(fields["model"]) ?? jsonText(fields["name"]) ?? "unknown"
        let tokens = jsonText(fields["totalTokens"])
            ?? jsonText(fields["total_tokens"])
            ?? jsonText(fields["inputTokens"])
            ?? jsonText(fields["input_tokens"])
            ?? "0"
        let sessions = jsonText(fields["sessions"]) ?? "0"
        let cost = jsonCurrencyText(fields["cost"]) ?? "$0"
        let share = jsonPercentText(fields["tokenShare"])
            ?? jsonPercentText(fields["token_share"])
            ?? jsonPercentText(fields["sessionShare"])
            ?? jsonPercentText(fields["session_share"])
            ?? "0%"
        let cache = jsonPercentText(fields["cacheHitPercent"]) ?? jsonPercentText(fields["cache_hit_percent"]) ?? "0%"

        return VStack(alignment: .leading, spacing: 4) {
            Text(modelName)
                .font(.headline.weight(.semibold))
                .foregroundStyle(HermexUIColors.primaryText)
            Text("\(tokens) tokens  \(sessions) sessions  \(cost)  \(share) share  \(cache) cache")
                .font(.subheadline)
                .foregroundStyle(HermexUIColors.secondaryText)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private func panelNotice(_ text: String, systemImage: String) -> some View {
        HermexMappedLabel(text, systemImage: systemImage)
            .font(.subheadline.weight(.medium))
            .foregroundStyle(HermexUIColors.secondaryText)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(12)
            .background(HermexUIColors.glassFillStrong, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    private var selectedInsightsDays: Int {
        normalizedInsightsDays(state.insightsDays)
    }

    private func insightText(_ primaryKey: String, _ fallbackKey: String? = nil) -> String {
        jsonText(insightValue(primaryKey, fallbackKey)) ?? "-"
    }

    private func insightCurrencyText(_ primaryKey: String, _ fallbackKey: String? = nil) -> String {
        jsonCurrencyText(insightValue(primaryKey, fallbackKey)) ?? "$0"
    }

    private func insightPercentText(_ primaryKey: String, _ fallbackKey: String? = nil) -> String {
        jsonPercentText(insightValue(primaryKey, fallbackKey)) ?? "0%"
    }

    private func insightNumber(_ primaryKey: String, _ fallbackKey: String? = nil) -> Double? {
        jsonNumber(insightValue(primaryKey, fallbackKey))
    }

    private func insightValue(_ primaryKey: String, _ fallbackKey: String? = nil) -> HermexJSONValue? {
        if let value = insightsFields[primaryKey] {
            return value
        }
        if let fallbackKey {
            return insightsFields[fallbackKey]
        }
        return nil
    }

    private func normalizedInsightsDays(_ days: Int) -> Int {
        switch days {
        case 1, 7, 30, 365:
            return days
        default:
            return 30
        }
    }

    private func insightsRangeTitle(days: Int) -> String {
        switch normalizedInsightsDays(days) {
        case 1:
            return "Today"
        case 7:
            return "Last 7 Days"
        case 30:
            return "Last 30 Days"
        case 365:
            return "All Time"
        default:
            return "Last 30 Days"
        }
    }

    private func jsonFields(_ value: HermexJSONValue) -> [String: HermexJSONValue] {
        guard case .dictionary(let fields) = value else { return [:] }
        return fields
    }

    private func jsonText(_ value: HermexJSONValue?) -> String? {
        switch value {
        case .string(let string):
            return string.isEmpty ? nil : string
        case .number(let number):
            return compactNumber(number)
        case .bool(let bool):
            return bool ? "true" : "false"
        default:
            return nil
        }
    }

    private func jsonCurrencyText(_ value: HermexJSONValue?) -> String? {
        guard let number = jsonNumber(value) else { return jsonText(value) }
        return "$\(compactNumber(number))"
    }

    private func jsonPercentText(_ value: HermexJSONValue?) -> String? {
        guard let number = jsonNumber(value) else { return jsonText(value) }
        return "\(compactNumber(number))%"
    }

    private func jsonNumber(_ value: HermexJSONValue?) -> Double? {
        switch value {
        case .number(let number):
            return number
        case .string(let string):
            return Double(string.replacingOccurrences(of: "$", with: "").replacingOccurrences(of: "%", with: ""))
        default:
            return nil
        }
    }

    private func compactNumber(_ value: Double) -> String {
        let rounded = value.rounded()
        if abs(value - rounded) < 0.0001 {
            return String(Int(rounded))
        }
        return String(value)
    }

    private func statusColor(_ status: String) -> Color {
        let value = status.lowercased()
        if value.contains("active") || value.contains("enabled") || value.contains("running") {
            return Color.green
        }
        if value.contains("fail") || value.contains("error") {
            return Color.red
        }
        if value.contains("pause") || value.contains("disabled") {
            return HermexUIColors.secondaryText
        }
        return Color.blue
    }
}
