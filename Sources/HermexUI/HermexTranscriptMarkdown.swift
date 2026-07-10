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
        let lines = normalized.split(separator: "\n", omittingEmptySubsequences: false).map(String.init)
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
            markdown.removeAll(keepingCapacity: true)
        }

        for line in lines {
            let trimmedStart = line.trimmingCharacters(in: CharacterSet.whitespaces)
            if let marker = fenceMarker {
                if trimmedStart.hasPrefix(marker) {
                    segments.append(.code(language: language, content: code.joined(separator: "\n")))
                    fenceMarker = nil
                    fenceOpening = ""
                    language = nil
                    code.removeAll(keepingCapacity: true)
                } else {
                    code.append(line)
                }
                continue
            }

            if trimmedStart.hasPrefix("```") || trimmedStart.hasPrefix("~~~") {
                flushMarkdown()
                let marker = trimmedStart.hasPrefix("```") ? "```" : "~~~"
                fenceMarker = marker
                fenceOpening = line
                let suffix = String(trimmedStart.dropFirst(marker.count))
                    .trimmingCharacters(in: CharacterSet.whitespaces)
                language = suffix.split(separator: " ", maxSplits: 1).first.map(String.init)
                code.removeAll(keepingCapacity: true)
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

private enum HermexInlineMarkdownStyle {
    case plain
    case bold
    case italic
    case code
    case link
}

private struct HermexInlineMarkdownRun {
    let text: String
    let style: HermexInlineMarkdownStyle
}

private enum HermexInlineMarkdownParser {
    static func runs(in value: String) -> [HermexInlineMarkdownRun] {
        var runs: [HermexInlineMarkdownRun] = []
        var plain = ""
        var remaining = value

        func flushPlain() {
            guard !plain.isEmpty else { return }
            runs.append(HermexInlineMarkdownRun(text: plain, style: .plain))
            plain.removeAll(keepingCapacity: true)
        }

        while !remaining.isEmpty {
            if let delimiter = matchingDelimiter(in: remaining),
               let close = closingDelimiter(delimiter, in: remaining) {
                flushPlain()
                let start = remaining.index(remaining.startIndex, offsetBy: delimiter.count)
                let inner = String(remaining[start..<close.lowerBound])
                if !inner.isEmpty {
                    runs.append(HermexInlineMarkdownRun(text: inner, style: delimiter.style))
                    remaining = String(remaining[close.upperBound...])
                    continue
                }
            }

            if let link = linkRun(in: remaining) {
                flushPlain()
                runs.append(link.run)
                remaining = link.remaining
                continue
            }

            plain.append(remaining.removeFirst())
        }

        flushPlain()
        return runs.isEmpty ? [HermexInlineMarkdownRun(text: value, style: .plain)] : runs
    }

    private static func matchingDelimiter(in value: String) -> (token: String, style: HermexInlineMarkdownStyle)? {
        if value.hasPrefix("**") { return ("**", .bold) }
        if value.hasPrefix("__") { return ("__", .bold) }
        if value.hasPrefix("`") { return ("`", .code) }
        if value.hasPrefix("*") { return ("*", .italic) }
        if value.hasPrefix("_") { return ("_", .italic) }
        return nil
    }

    private static func closingDelimiter(
        _ delimiter: (token: String, style: HermexInlineMarkdownStyle),
        in value: String
    ) -> Range<String.Index>? {
        let start = value.index(value.startIndex, offsetBy: delimiter.token.count)
        return value.range(of: delimiter.token, range: start..<value.endIndex)
    }

    private static func linkRun(in value: String) -> (run: HermexInlineMarkdownRun, remaining: String)? {
        guard value.first == "[",
              let labelEnd = value.firstIndex(of: "]") else { return nil }
        let openParen = value.index(after: labelEnd)
        guard openParen < value.endIndex, value[openParen] == "(",
              let urlEnd = value[openParen...].firstIndex(of: ")") else { return nil }
        let labelStart = value.index(after: value.startIndex)
        let label = String(value[labelStart..<labelEnd])
        guard !label.isEmpty else { return nil }
        let next = value.index(after: urlEnd)
        return (
            HermexInlineMarkdownRun(text: label, style: .link),
            String(value[next...])
        )
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
                Text(verbatim: "•")
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
        guard number.allSatisfy({ $0.isNumber }) else { return nil }
        let start = value.index(after: dot)
        guard start < value.endIndex, value[start] == " " else { return nil }
        return ("\(number).", String(value[value.index(after: start)...]))
    }

    private func inlineText(_ value: String) -> Text {
        HermexInlineMarkdownParser.runs(in: value).reduce(Text(verbatim: "")) { result, run in
            let text = Text(verbatim: run.text)
            switch run.style {
            case .plain:
                return result + text
            case .bold:
                return result + text.bold()
            case .italic:
                return result + text.italic()
            case .code:
                return result + text.font(.system(.body, design: .monospaced))
            case .link:
                return result + text.underline().foregroundColor(Color.accentColor)
            }
        }
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
                    .textSelection(.enabled)
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
