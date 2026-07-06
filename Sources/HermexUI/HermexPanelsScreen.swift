import SwiftUI
import Foundation
import HermexCore

public struct HermexPanelsScreen: View {
    private let state: HermexPanelsState
    private let onEvent: (HermexUIEvent) -> Void

    @State private var skillSearchText = ""
    @State private var selectedInsightsRange = 30

    public init(state: HermexPanelsState, onEvent: @escaping (HermexUIEvent) -> Void = { _ in }) {
        self.state = state
        self.onEvent = onEvent
    }

    public var body: some View {
        ZStack {
            HermexUIColors.systemBackground.ignoresSafeArea()

            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    topBar
                    selectedPanelCard
                }
                .padding(.horizontal, 16)
                .padding(.top, 12)
                .padding(.bottom, 32)
            }
        }
        .foregroundStyle(HermexUIColors.primaryText)
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
            Button { } label: {
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
                VStack(alignment: .leading, spacing: 12) {
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
                    metricRow("Status", task.status ?? "Unknown")

                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            panelActionButton("Details", isProminent: true)
                            panelActionButton("Edit")
                            panelActionButton("Run")
                            panelActionButton((task.status ?? "").lowercased().contains("pause") ? "Resume" : "Pause")
                        }
                    }
                }
                .padding(.vertical, 10)
            }
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
                                panelActionButton(skill.enabled == true ? "Disable" : "Enable")
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
                VStack(alignment: .leading, spacing: 14) {
                    HStack(spacing: 10) {
                        Image(systemName: "brain.head.profile")
                            .foregroundStyle(HermexUIColors.primaryText)
                        Text(section.section)
                            .font(.title3.weight(.semibold))
                            .foregroundStyle(HermexUIColors.primaryText)
                        Spacer()
                        panelActionButton("Edit \(section.section)")
                    }

                    Text(section.content.isEmpty ? "No notes saved." : section.content)
                        .font(.title3)
                        .foregroundStyle(HermexUIColors.primaryText)
                        .fixedSize(horizontal: false, vertical: true)
                        .frame(maxWidth: .infinity, alignment: .leading)
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
                }
            }

            VStack(alignment: .leading, spacing: 10) {
                Text(insightsPeriodTitle)
                    .font(.headline.weight(.semibold))
                    .foregroundStyle(HermexUIColors.secondaryText)

                metricRow("Sessions", insightText("totalSessions"))
                metricRow("Messages", insightText("totalMessages"))
                metricRow("Input Tokens", insightText("totalInputTokens"))
                metricRow("Output Tokens", insightText("totalOutputTokens"))
                metricRow("Total Tokens", insightText("totalTokens"))
                metricRow("Estimated Cost", insightCurrencyText("totalCost"))
                metricRow("Cache Hit Rate", insightPercentText("totalCacheHitPercent"))
                metricRow("Cache Read Tokens", insightText("totalCacheReadTokens"))
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
        return state.skills.filter { skill in
            skill.name.localizedCaseInsensitiveContains(query) ||
                (skill.summary ?? "").localizedCaseInsensitiveContains(query)
        }
    }

    private var insightsFields: [String: HermexJSONValue] {
        guard case .dictionary(let fields) = state.insights else { return [:] }
        return fields
    }

    private var insightsPeriodTitle: String {
        if let period = insightNumber("periodDays") {
            return period == 1 ? "Today" : "Last \(Int(period)) Days"
        }
        return selectedInsightsRange == 1 ? "Today" : "Last \(selectedInsightsRange) Days"
    }

    private var insightModels: [HermexJSONValue] {
        guard case .array(let models) = insightsFields["models"] else { return [] }
        return models
    }

    private func insightsRangeButton(_ title: String, days: Int) -> some View {
        Button {
            selectedInsightsRange = days
        } label: {
            Text(title)
                .font(.headline.weight(.semibold))
                .foregroundStyle(selectedInsightsRange == days ? .white : HermexUIColors.primaryText)
                .padding(.horizontal, 18)
                .frame(height: 48)
                .background(selectedInsightsRange == days ? Color.blue : HermexUIColors.glassFillStrong, in: Capsule())
                .overlay {
                    Capsule().stroke(HermexUIColors.hairline, lineWidth: selectedInsightsRange == days ? 0 : 0.7)
                }
        }
        .buttonStyle(.plain)
    }

    private func panelIcon(_ systemImage: String) -> some View {
        Image(systemName: systemImage)
            .font(.title3.weight(.semibold))
            .foregroundStyle(HermexUIColors.primaryText)
            .frame(width: 54, height: 54)
            .background(HermexUIColors.glassFillStrong, in: Circle())
    }

    private func panelActionButton(_ title: String, isProminent: Bool = false) -> some View {
        Button { } label: {
            Text(title)
                .font(.headline.weight(.semibold))
                .foregroundStyle(isProminent ? .white : HermexUIColors.primaryText)
                .padding(.horizontal, 16)
                .frame(height: 46)
                .background(isProminent ? Color.blue : HermexUIColors.glassFillStrong, in: Capsule())
                .overlay {
                    Capsule().stroke(HermexUIColors.hairline, lineWidth: isProminent ? 0 : 0.7)
                }
        }
        .buttonStyle(.plain)
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
        let modelName = jsonText(fields["model"]) ?? "unknown"
        let tokens = jsonText(fields["totalTokens"]) ?? jsonText(fields["inputTokens"]) ?? "0"
        let sessions = jsonText(fields["sessions"]) ?? "0"
        let cost = jsonCurrencyText(fields["cost"]) ?? "$0"
        let share = jsonPercentText(fields["tokenShare"]) ?? jsonPercentText(fields["sessionShare"]) ?? "0%"
        let cache = jsonPercentText(fields["cacheHitPercent"]) ?? "0%"

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
        Label(text, systemImage: systemImage)
            .font(.subheadline.weight(.medium))
            .foregroundStyle(HermexUIColors.secondaryText)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(12)
            .background(HermexUIColors.glassFillStrong, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    private func insightText(_ key: String) -> String {
        jsonText(insightsFields[key]) ?? "-"
    }

    private func insightCurrencyText(_ key: String) -> String {
        jsonCurrencyText(insightsFields[key]) ?? "$0"
    }

    private func insightPercentText(_ key: String) -> String {
        jsonPercentText(insightsFields[key]) ?? "0%"
    }

    private func insightNumber(_ key: String) -> Double? {
        jsonNumber(insightsFields[key])
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
