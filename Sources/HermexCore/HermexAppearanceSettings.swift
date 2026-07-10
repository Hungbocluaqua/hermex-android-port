import Foundation

public struct HermexColorPreset: Equatable, Sendable, Identifiable {
    public let name: String
    public let hex: String

    public var id: String { hex }

    public init(name: String, hex: String) {
        self.name = name
        self.hex = hex
    }
}

public enum HermexAppearanceSettings {
    public static let defaultHeaderLogoColorHex = "#FFD700"
    private static let allowedInitialCharacters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

    public static let headerLogoColorPresets = [
        HermexColorPreset(name: "Yellow", hex: "#FFD700"),
        HermexColorPreset(name: "Blue", hex: "#5B7CFF"),
        HermexColorPreset(name: "Purple", hex: "#AF52DE"),
        HermexColorPreset(name: "Red", hex: "#FF3B30"),
        HermexColorPreset(name: "Green", hex: "#34C759"),
        HermexColorPreset(name: "White", hex: "#FFFFFF")
    ]

    public static func normalizedHex(_ rawValue: String) -> String? {
        var hex = rawValue.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines)
        if hex.hasPrefix("#") {
            hex.removeFirst()
        }

        guard hex.count == 6, hex.allSatisfy(\.isHexDigit) else { return nil }
        return "#\(hex.uppercased())"
    }

    public static func prefersDarkForeground(for rawValue: String) -> Bool {
        guard let components = rgbComponents(for: rawValue) else { return true }
        let luminance = (0.2126 * components.red) + (0.7152 * components.green) + (0.0722 * components.blue)
        return luminance > 0.62
    }

    public static func normalizedInitials(_ rawValue: String) -> String {
        var result = ""
        for character in rawValue {
            guard allowedInitialCharacters.contains(character) else { continue }
            result.append(contentsOf: String(character).uppercased())
            if result.count == 3 { break }
        }
        return result
    }

    public static func displayInitials(
        displayName: String,
        storedInitials: String,
        fallbackFullName: String
    ) -> String {
        let normalizedStoredInitials = normalizedInitials(storedInitials)
        if !normalizedStoredInitials.isEmpty { return normalizedStoredInitials }

        let displayNameInitials = initials(from: displayName)
        if !displayNameInitials.isEmpty { return displayNameInitials }

        let fallbackInitials = initials(from: fallbackFullName)
        return fallbackInitials.isEmpty ? "UZ" : fallbackInitials
    }

    private static func initials(from rawName: String) -> String {
        var words: [String] = []
        var current = ""
        for character in rawName {
            if character == " " || character == "\t" || character == "-" || character == "_" {
                if !current.isEmpty {
                    words.append(current)
                    current = ""
                }
            } else {
                current.append(character)
            }
        }
        if !current.isEmpty { words.append(current) }

        var result = ""
        for word in words {
            if let first = word.first {
                result.append(contentsOf: String(first).uppercased())
            }
            if result.count == 2 { break }
        }
        return result
    }

    private static func rgbComponents(for rawValue: String) -> (red: Double, green: Double, blue: Double)? {
        guard let hex = normalizedHex(rawValue),
              let value = UInt32(String(hex.dropFirst()), radix: 16)
        else {
            return rgbComponents(for: defaultHeaderLogoColorHex)
        }

        return (
            Double((value & 0xFF0000) >> 16) / 255.0,
            Double((value & 0x00FF00) >> 8) / 255.0,
            Double(value & 0x0000FF) / 255.0
        )
    }
}
