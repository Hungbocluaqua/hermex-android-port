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
        rawValue
            .filter { $0.isLetter || $0.isNumber }
            .prefix(3)
            .map { String($0).uppercased() }
            .joined()
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
        rawName
            .split(whereSeparator: { $0.isWhitespace || $0 == "-" || $0 == "_" })
            .compactMap(\.first)
            .prefix(2)
            .map { String($0).uppercased() }
            .joined()
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
