import SwiftUI
import HermexUI

@main
struct HermexVisualFixturesApp: App {
    private let screenName: String
    private let colorScheme: ColorScheme

    init() {
        let arguments = ProcessInfo.processInfo.arguments
        screenName = Self.value(after: "--screen", in: arguments) ?? "session-list"
        colorScheme = (Self.value(after: "--state", in: arguments) ?? "dark") == "light" ? .light : .dark
    }

    var body: some Scene {
        WindowGroup {
            if let fixture = HermexVisualFixtureRootScreen(screenName: screenName) {
                fixture
                    .preferredColorScheme(colorScheme)
            } else {
                Text("Unknown visual fixture: \(screenName)")
                    .padding()
            }
        }
    }

    private static func value(after flag: String, in arguments: [String]) -> String? {
        guard let index = arguments.firstIndex(of: flag), arguments.indices.contains(index + 1) else {
            return nil
        }
        return arguments[index + 1]
    }
}
