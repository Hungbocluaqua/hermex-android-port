import XCTest
@testable import HermexCore

final class HermexVisualFixtureTests: XCTestCase {
    func testVisualFixtureCatalogMatchesScreenshotManifest() throws {
        let manifestURL = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .appendingPathComponent("ci/visual-goldens/hermex-screens.json")
        let data = try Data(contentsOf: manifestURL)
        let manifest = try JSONDecoder().decode(VisualScreenManifest.self, from: data)

        XCTAssertEqual(HermexVisualFixtureCatalog.screenNames, manifest.screens)
        XCTAssertEqual(HermexVisualFixtureCatalog.all.map(\.screenName), manifest.screens)
    }

    func testEachVisualFixtureHasExpectedRoute() {
        let expectedRoutes: [String: HermexRoute] = [
            "onboarding-welcome": .onboarding,
            "onboarding-connect": .onboarding,
            "session-list": .sessions,
            "session-list-search": .sessions,
            "chat-empty": .chat,
            "chat-populated": .chat,
            "chat-streaming": .chat,
            "chat-keyboard-open": .chat,
            "chat-slash-menu": .chat,
            "chat-attachments": .chat,
            "chat-approval": .chat,
            "settings": .settings,
            "workspace-files": .workspace,
            "git-status": .git,
            "panels-tasks": .panels,
            "panels-skills": .panels,
            "panels-memory": .panels,
            "panels-insights": .panels,
        ]

        for (screenName, route) in expectedRoutes {
            let fixture = HermexVisualFixtureCatalog.fixture(named: screenName)
            XCTAssertEqual(fixture?.appState.route, route, screenName)
        }
    }

    func testSpecialChatFixturesExposeCaptureHints() throws {
        let keyboard = try XCTUnwrap(HermexVisualFixtureCatalog.fixture(named: "chat-keyboard-open"))
        let slash = try XCTUnwrap(HermexVisualFixtureCatalog.fixture(named: "chat-slash-menu"))
        let attachments = try XCTUnwrap(HermexVisualFixtureCatalog.fixture(named: "chat-attachments"))
        let approval = try XCTUnwrap(HermexVisualFixtureCatalog.fixture(named: "chat-approval"))

        XCTAssertTrue(keyboard.prefersKeyboardVisible)
        XCTAssertEqual(slash.overlay, .slashMenu)
        XCTAssertEqual(attachments.overlay, .attachmentPicker)
        XCTAssertFalse(attachments.chat.composer.attachments.isEmpty)
        XCTAssertNotNil(approval.chat.pendingApproval)
    }
}

private struct VisualScreenManifest: Decodable {
    var screens: [String]
}
