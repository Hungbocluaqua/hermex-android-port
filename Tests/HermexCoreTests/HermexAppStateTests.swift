import XCTest
@testable import HermexCore

final class HermexAppStateTests: XCTestCase {
    func testWholeAppStateRoundTrips() throws {
        let server = HermexServerIdentity(
            baseURL: try XCTUnwrap(URL(string: "https://example.test")),
            displayName: "Example"
        )
        let state = HermexAppState(
            auth: .loggedIn(server: server),
            selectedSessionID: "session-1",
            route: .chat
        )

        let encoded = try JSONEncoder().encode(state)
        let decoded = try JSONDecoder().decode(HermexAppState.self, from: encoded)

        XCTAssertEqual(decoded, state)
    }

    func testLegacyServerIdentityDecodesAppearanceDefaults() throws {
        let data = Data("""
        {
          "baseURL": "https://example.test/",
          "displayName": "Example",
          "customHeaders": {}
        }
        """.utf8)

        let server = try JSONDecoder().decode(HermexServerIdentity.self, from: data)

        XCTAssertEqual(server.initials, "")
        XCTAssertEqual(server.headerLogoColorHex, HermexAppearanceSettings.defaultHeaderLogoColorHex)
    }

    func testAppearanceHelpersNormalizeIdentityAndColor() {
        XCTAssertEqual(HermexAppearanceSettings.normalizedInitials(" p-x! "), "PX")
        XCTAssertEqual(
            HermexAppearanceSettings.displayInitials(
                displayName: "Hermex Preview",
                storedInitials: "",
                fallbackFullName: "Fallback User"
            ),
            "HP"
        )
        XCTAssertEqual(HermexAppearanceSettings.normalizedHex(" 5b7cff "), "#5B7CFF")
        XCTAssertTrue(HermexAppearanceSettings.prefersDarkForeground(for: "#FFFFFF"))
        XCTAssertFalse(HermexAppearanceSettings.prefersDarkForeground(for: "#111111"))
    }

    func testChatStateDefaultsRepresentIdleComposer() {
        let state = HermexChatState()

        XCTAssertFalse(state.stream.isStreaming)
        XCTAssertEqual(state.composer.draft, "")
        XCTAssertTrue(state.composer.showsReasoningControl)
        XCTAssertTrue(state.messages.isEmpty)
    }

    func testPanelsStateCoversAllMajorPanels() {
        XCTAssertEqual(
            Set([HermexPanel.tasks, .skills, .memory, .insights].map(\.rawValue)),
            Set(["tasks", "skills", "memory", "insights"])
        )
    }

    func testPanelsStateDecodesLegacyMissingInsightsDays() throws {
        let data = Data("""
        {
          "tasks": [],
          "skills": [],
          "memory": [],
          "selectedPanel": "insights",
          "isLoading": false
        }
        """.utf8)

        let decoded = try JSONDecoder().decode(HermexPanelsState.self, from: data)

        XCTAssertEqual(decoded.selectedPanel, .insights)
        XCTAssertEqual(decoded.insightsDays, 30)
    }

    func testOnboardingStateNeverPersistsPassword() throws {
        let state = HermexOnboardingState(
            serverURLString: "https://example.test",
            displayName: "Example",
            password: "secret",
            customHeaderText: "CF-Access-Client-Id: abc"
        )

        let encoded = try JSONEncoder().encode(state)
        let raw = String(decoding: encoded, as: UTF8.self)
        let decoded = try JSONDecoder().decode(HermexOnboardingState.self, from: encoded)

        XCTAssertFalse(raw.contains("secret"))
        XCTAssertEqual(decoded.password, "")
        XCTAssertEqual(decoded.serverURLString, state.serverURLString)
        XCTAssertEqual(decoded.customHeaderText, state.customHeaderText)
    }
}
