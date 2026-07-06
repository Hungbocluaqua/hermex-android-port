import SwiftUI
import HermexCore

public struct HermexPanelsScreen: View {
    private let state: HermexPanelsState
    private let onEvent: (HermexUIEvent) -> Void

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
                    panelPicker

                    if state.isLoading {
                        ProgressView()
                    }

                    if let error = state.errorMessage {
                        Text(error)
                            .foregroundStyle(.secondary)
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
                .padding(.horizontal, 16)
                .padding(.top, 12)
                .padding(.bottom, 32)
            }
        }
    }

    private var topBar: some View {
        HStack(spacing: 12) {
            HermexCircleIconButton(systemImage: "chevron.left", accessibilityLabel: "Back", action: { onEvent(.openRoute(.sessions)) })
            HermexScreenTitle("Panels", subtitle: state.selectedPanel.rawValue.capitalized)
            Spacer()
            HermexCircleIconButton(systemImage: "arrow.clockwise", accessibilityLabel: "Refresh", action: { onEvent(.refresh) })
        }
    }

    private var panelPicker: some View {
        Picker("Panel", selection: Binding(
            get: { state.selectedPanel },
            set: { onEvent(.selectPanel($0)) }
        )) {
            Text("Tasks").tag(HermexPanel.tasks)
            Text("Skills").tag(HermexPanel.skills)
            Text("Memory").tag(HermexPanel.memory)
            Text("Insights").tag(HermexPanel.insights)
        }
        .pickerStyle(.segmented)
    }

    private var taskRows: some View {
        VStack(alignment: .leading, spacing: 10) {
            ForEach(state.tasks) { task in
                panelRow(title: task.title ?? task.id, subtitle: [task.status, task.schedule].compactMap { $0 }.joined(separator: " * "), systemImage: "calendar.badge.clock")
            }
        }
    }

    private var skillRows: some View {
        VStack(alignment: .leading, spacing: 10) {
            ForEach(state.skills) { skill in
                panelRow(title: skill.name, subtitle: skill.summary ?? (skill.enabled == true ? "Enabled" : "Disabled"), systemImage: "hammer")
            }
        }
    }

    private var memoryRows: some View {
        VStack(alignment: .leading, spacing: 10) {
            ForEach(state.memory) { section in
                panelRow(title: section.section, subtitle: section.content, systemImage: "brain.head.profile")
            }
        }
    }

    private var insightsPanel: some View {
        HermexGlassPanel(cornerRadius: 14) {
            Text(String(describing: state.insights ?? .null))
                .font(.system(.caption, design: .monospaced))
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(14)
        }
    }

    private func panelRow(title: String, subtitle: String, systemImage: String) -> some View {
        Label {
            VStack(alignment: .leading, spacing: 3) {
                Text(title)
                    .font(.body.weight(.medium))
                if !subtitle.isEmpty {
                    Text(subtitle)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(2)
                }
            }
        } icon: {
            Image(systemName: systemImage)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.vertical, 8)
    }
}
