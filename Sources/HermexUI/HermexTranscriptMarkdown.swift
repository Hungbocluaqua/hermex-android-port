import SwiftUI

enum HermexMarkdownSegment: Equatable {
    case markdown(String)
    case code(language: String?, content: String)
}

enum HermexMarkdownSegmentParser {
    static func segments(in content: String) -> [HermexMarkdownSegment] {
        let normalized = content
            .replacingOccurrences(of: "\r\n", with: "\n")
            .replacingOccurrences(of: "\r", with: "\n")
        let lines: [String] = normalized
            .split(separator: "\n", omittingEmptySubsequences: false)
            .map { String($0) }
        var segments: [HermexMarkdownSegment] = []
        var markdown: [String] = []
        var code: [String] = []
        var fenceMarker: String?
        var fenceOpening = ""
        var language: String?

        func flushMarkdown() {
            let value = markdown.joined(separator: "\n").trimmingCharacters(in: CharacterSet.newlines)
            if !value.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines).isEmpty {
                segments.append(.markdown(value))
            }
            markdown = []
        }

        lines.forEach { line in
            let trimmedStart = line.trimmingCharacters(in: CharacterSet.whitespaces)
            if let marker = fenceMarker {
                if trimmedStart.hasPrefix(marker) {
                    segments.append(.code(language: language, content: code.joined(separator: "\n")))
                    fenceMarker = nil
                    fenceOpening = ""
                    language = nil
                    code = []
                } else {
                    code.append(line)
                }
                return
            }

            if trimmedStart.hasPrefix("```") || trimmedStart.hasPrefix("~~~") {
                flushMarkdown()
                let marker = trimmedStart.hasPrefix("```") ? "```" : "~~~"
                fenceMarker = marker
                fenceOpening = line
                let suffix = String(trimmedStart.dropFirst(marker.count))
                    .trimmingCharacters(in: CharacterSet.whitespaces)
                language = suffix.split(separator: " ", maxSplits: 1).first.map { String($0) }
                code = []
            } else {
                markdown.append(line)
            }
        }

        if fenceMarker != nil {
            markdown.append(fenceOpening)
            markdown.append(contentsOf: code)
        }
        flushMarkdown()
        return segments.isEmpty ? [.markdown(normalized)] : segments
    }
}

struct HermexTranscriptMarkdown: View {
    let content: String

    var body: some View {
        let segments = HermexMarkdownSegmentParser.segments(in: content)
        VStack(alignment: .leading, spacing: 10) {
            ForEach(Array(segments.enumerated()), id: \.offset) { _, segment in
                switch segment {
                case .markdown(let value):
                    HermexMarkdownParagraph(text: value)
                case .code(let language, let value):
                    HermexMarkdownCodeBlock(language: language, content: value)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

struct HermexTranscriptAccessory: View {
    let title: String
    let text: String
    let systemImage: String
    @State private var isExpanded = false

    var body: some View {
        DisclosureGroup(
            isExpanded: $isExpanded,
            content: {
                Text(text)
                    .font(.caption)
                    .foregroundStyle(HermexUIColors.primaryText)
                    .hermexTextSelectionEnabled()
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.top, 5)
            },
            label: {
                HermexMappedLabel(title, systemImage: systemImage)
                    .font(.caption.weight(.semibold))
            }
        )
        .foregroundStyle(HermexUIColors.secondaryText)
        .padding(.horizontal, 10)
        .padding(.vertical, 9)
        .hermexThinMaterialBackground(in: RoundedRectangle(cornerRadius: 10, style: .continuous))
    }
}

private struct HermexMarkdownParagraph: View {
    let text: String

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            ForEach(Array(text.split(separator: "\n", omittingEmptySubsequences: false).enumerated()), id: \.offset) { _, line in
                HermexMarkdownLine(text: String(line))
            }
        }
    }
}

private struct HermexMarkdownLine: View {
    let text: String

    var body: some View {
        let value = text.trimmingCharacters(in: CharacterSet.whitespaces)
        if value.isEmpty {
            Color.clear.frame(height: 4)
        } else if value.hasPrefix("# ") {
            inlineText(String(value.dropFirst(2))).font(.title3.weight(.semibold))
        } else if value.hasPrefix("## ") {
            inlineText(String(value.dropFirst(3))).font(.headline.weight(.semibold))
        } else if value.hasPrefix("### ") {
            inlineText(String(value.dropFirst(4))).font(.subheadline.weight(.semibold))
        } else if value.hasPrefix("> ") {
            HStack(alignment: .top, spacing: 8) {
                Rectangle()
                    .fill(HermexUIColors.secondaryText)
                    .frame(width: 2)
                inlineText(String(value.dropFirst(2)))
                    .foregroundColor(HermexUIColors.secondaryText)
            }
        } else if value.hasPrefix("- ") || value.hasPrefix("* ") || value.hasPrefix("+ ") {
            HStack(alignment: .top, spacing: 7) {
                Text(verbatim: "-")
                inlineText(String(value.dropFirst(2)))
            }
        } else if let marker = orderedListMarker(in: value) {
            HStack(alignment: .top, spacing: 7) {
                Text(verbatim: marker.label)
                    .foregroundColor(HermexUIColors.secondaryText)
                inlineText(marker.text)
            }
        } else if value == "---" || value == "***" {
            Divider()
        } else {
            inlineText(value)
        }
    }

    private func orderedListMarker(in value: String) -> (label: String, text: String)? {
        guard let dot = value.firstIndex(of: "."), dot > value.startIndex else { return nil }
        let number = String(value[..<dot])
        guard !number.isEmpty, Int(number) != nil else { return nil }
        let start = value.index(after: dot)
        guard start < value.endIndex, value[start] == " " else { return nil }
        return ("\(number).", String(value[value.index(after: start)...]))
    }

    private func inlineText(_ value: String) -> Text {
        Text(verbatim: value)
    }
}

private struct HermexMarkdownCodeBlock: View {
    let language: String?
    let content: String

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(language?.isEmpty == false ? language! : "code")
                .font(.caption.weight(.semibold))
                .foregroundColor(HermexUIColors.secondaryText)

            ScrollView(.horizontal, showsIndicators: false) {
                Text(content.isEmpty ? " " : content)
                    .font(.system(.callout, design: .monospaced))
                    .foregroundColor(HermexUIColors.primaryText)
                    .hermexTextSelectionEnabled()
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(HermexUIColors.glassFill, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .stroke(HermexUIColors.hairline, lineWidth: 0.5)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
