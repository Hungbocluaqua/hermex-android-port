import Foundation

public enum HermexMessageActionRole: String, Equatable, Sendable {
    case user
    case assistant
}

public struct HermexMessageActionContext: Equatable, Identifiable, Sendable {
    public let role: HermexMessageActionRole
    public let visibleIndex: Int
    public let fullHistoryIndex: Int
    public let keepCountThroughMessage: Int
    public let messageID: String
    public let copyText: String
    public let listenText: String?

    public var id: String { messageID }

    public init(
        role: HermexMessageActionRole,
        visibleIndex: Int,
        fullHistoryIndex: Int,
        keepCountThroughMessage: Int,
        messageID: String,
        copyText: String,
        listenText: String? = nil
    ) {
        self.role = role
        self.visibleIndex = visibleIndex
        self.fullHistoryIndex = fullHistoryIndex
        self.keepCountThroughMessage = keepCountThroughMessage
        self.messageID = messageID
        self.copyText = copyText
        self.listenText = listenText
    }
}

public enum HermexMessageActionContextResolver {
    public static func context(
        for message: HermexChatMessageDTO,
        visibleIndex: Int,
        messagesOffset: Int = 0
    ) -> HermexMessageActionContext? {
        guard visibleIndex >= 0,
              let role = HermexMessageActionRole(rawValue: message.role ?? "")
        else { return nil }

        let text = (message.content ?? message.text ?? "")
            .trimmingCharacters(in: CharacterSet.whitespacesAndNewlines)
        guard !text.isEmpty else { return nil }

        let fullHistoryIndex = max(0, messagesOffset) + visibleIndex
        let messageID = message.messageId ?? message.id ?? "(role.rawValue)-(fullHistoryIndex)"
        return HermexMessageActionContext(
            role: role,
            visibleIndex: visibleIndex,
            fullHistoryIndex: fullHistoryIndex,
            keepCountThroughMessage: fullHistoryIndex + 1,
            messageID: messageID,
            copyText: text,
            listenText: role == .assistant ? HermexSpeechTextNormalizer.normalizedAssistantText(text) : nil
        )
    }

    public static func precedingUserMessageText(
        in messages: [HermexChatMessageDTO],
        beforeVisibleIndex: Int
    ) -> String? {
        guard !messages.isEmpty, beforeVisibleIndex > 0 else { return nil }
        let startIndex = min(beforeVisibleIndex - 1, messages.count - 1)
        for index in stride(from: startIndex, through: 0, by: -1) {
            let message = messages[index]
            guard message.role == HermexMessageActionRole.user.rawValue else { continue }
            let text = (message.content ?? message.text ?? "")
                .trimmingCharacters(in: CharacterSet.whitespacesAndNewlines)
            if !text.isEmpty { return text }
        }
        return nil
    }
}

public enum HermexSpeechTextNormalizer {
    public static func normalizedAssistantText(_ text: String) -> String? {
        var lines: [String] = []
        for rawLine in text.split(separator: "\n", omittingEmptySubsequences: false) {
            var line = String(rawLine).trimmingCharacters(in: CharacterSet.whitespacesAndNewlines)
            while line.hasPrefix("#") {
                line = String(line.dropFirst())
            }
            line = line.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines)
            if line.hasPrefix(">") {
                line = String(line.dropFirst()).trimmingCharacters(in: CharacterSet.whitespacesAndNewlines)
            }
            if line.hasPrefix("- ") || line.hasPrefix("* ") || line.hasPrefix("+ ") {
                line = String(line.dropFirst(2))
            }
            if !line.isEmpty {
                lines.append(line.replacingOccurrences(of: "`", with: ""))
            }
        }

        let normalized = lines.joined(separator: "\n")
            .trimmingCharacters(in: CharacterSet.whitespacesAndNewlines)
        return normalized.isEmpty ? nil : normalized
    }
}
