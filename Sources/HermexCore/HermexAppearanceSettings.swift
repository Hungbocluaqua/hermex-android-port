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
    private static let allowedHexCharacters = "0123456789abcdefABCDEF"

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
            hex = String(hex.dropFirst())
        }

        guard hex.count == 6 else { return nil }
        for character in hex {
            guard isAllowedHexCharacter(character) else { return nil }
        }
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
            guard isAllowedInitialCharacter(character) else { continue }
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
                current.append(contentsOf: String(character))
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

    private static func isAllowedInitialCharacter(_ character: Character) -> Bool {
        for allowedCharacter in allowedInitialCharacters {
            if allowedCharacter == character { return true }
        }
        return false
    }

    private static func isAllowedHexCharacter(_ character: Character) -> Bool {
        for allowedCharacter in allowedHexCharacters {
            if allowedCharacter == character { return true }
        }
        return false
    }

    public static func rgbComponents(for rawValue: String) -> (red: Double, green: Double, blue: Double)? {
        guard let hex = normalizedHex(rawValue) else {
            return rgbComponents(for: defaultHeaderLogoColorHex)
        }

        var digits: [Int] = []
        var skippedPrefix = false
        for character in hex {
            if !skippedPrefix {
                skippedPrefix = true
                continue
            }
            if let digit = hexDigitValue(character) {
                digits.append(digit)
            }
        }
        guard digits.count == 6 else { return rgbComponents(for: defaultHeaderLogoColorHex) }

        return (
            Double((digits[0] * 16) + digits[1]) / 255.0,
            Double((digits[2] * 16) + digits[3]) / 255.0,
            Double((digits[4] * 16) + digits[5]) / 255.0
        )
    }

    private static func hexDigitValue(_ character: Character) -> Int? {
        switch character {
        case "0": return 0
        case "1": return 1
        case "2": return 2
        case "3": return 3
        case "4": return 4
        case "5": return 5
        case "6": return 6
        case "7": return 7
        case "8": return 8
        case "9": return 9
        case "a", "A": return 10
        case "b", "B": return 11
        case "c", "C": return 12
        case "d", "D": return 13
        case "e", "E": return 14
        case "f", "F": return 15
        default: return nil
        }
    }
}
