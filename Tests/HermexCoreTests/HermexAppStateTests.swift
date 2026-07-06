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
