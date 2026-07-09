import XCTest
@testable import HermexCore

final class HermexEndpointTests: XCTestCase {
    func testSessionsUsesIosQueryFlags() {
        let endpoint = HermexEndpoints.sessions(includeArchived: true, archivedLimit: 25)

        XCTAssertEqual(endpoint.path, "/api/sessions")
        XCTAssertEqual(endpoint.queryItems, [
            URLQueryItem(name: "include_archived", value: "1"),
            URLQueryItem(name: "archived_limit", value: "25")
        ])
    }

    func testSessionOmitsOptionalQueryItemsWhenUnset() {
        let endpoint = HermexEndpoints.session(id: "abc", includeMessages: true)

        XCTAssertEqual(endpoint.queryItems, [
            URLQueryItem(name: "session_id", value: "abc"),
            URLQueryItem(name: "messages", value: "1")
        ])
    }

    func testSessionUsageUsesSessionIDQuery() {
        let endpoint = HermexEndpoints.sessionUsage(id: "abc")

        XCTAssertEqual(endpoint.path, "/api/session/usage")
        XCTAssertEqual(endpoint.queryItems, [
            URLQueryItem(name: "session_id", value: "abc")
        ])
    }

    func testClearSessionUsesServerMutationPath() {
        XCTAssertEqual(HermexEndpoints.clearSession.path, "/api/session/clear")
        XCTAssertEqual(HermexEndpoints.clearSession.queryItems, [])
    }

    func testChatStreamReplayQuery() {
        let endpoint = HermexEndpoints.chatStream(id: "stream-1", replayAfterSeq: 42)

        XCTAssertEqual(endpoint.path, "/api/chat/stream")
        XCTAssertEqual(endpoint.queryItems, [
            URLQueryItem(name: "stream_id", value: "stream-1"),
            URLQueryItem(name: "replay", value: "1"),
            URLQueryItem(name: "after_seq", value: "42")
        ])
    }

    func testWorkspaceRoutesMirrorIosContract() {
        XCTAssertEqual(HermexEndpoints.workspaceSuggestions(prefix: "/tmp").path, "/api/workspaces/suggest")
        XCTAssertEqual(HermexEndpoints.directoryList(sessionID: "s1", path: "/repo").path, "/api/list")
        XCTAssertEqual(HermexEndpoints.file(sessionID: "s1", path: "README.md").path, "/api/file")
        XCTAssertEqual(HermexEndpoints.rawFile(sessionID: "s1", path: "README.md").path, "/api/file/raw")
        XCTAssertEqual(HermexEndpoints.media(path: "/tmp/a.png").path, "/api/media")
        XCTAssertEqual(HermexEndpoints.gitInfo(sessionID: "s1").path, "/api/git-info")
    }

    func testStreamingAndPromptRoutesCarryRequiredSessionQueries() {
        XCTAssertEqual(HermexEndpoints.chatCancel(streamID: "stream-1").queryItems, [
            URLQueryItem(name: "stream_id", value: "stream-1")
        ])
        XCTAssertEqual(HermexEndpoints.chatStreamStatus(streamID: "stream-1").queryItems, [
            URLQueryItem(name: "stream_id", value: "stream-1")
        ])
        XCTAssertEqual(HermexEndpoints.approvalPending(sessionID: "s1").queryItems, [
            URLQueryItem(name: "session_id", value: "s1")
        ])
        XCTAssertEqual(HermexEndpoints.clarifyStream(sessionID: "s1").queryItems, [
            URLQueryItem(name: "session_id", value: "s1")
        ])
        XCTAssertEqual(HermexEndpoints.backgroundStatus(sessionID: "s1").queryItems, [
            URLQueryItem(name: "session_id", value: "s1")
        ])
    }

    func testPanelRoutesCarryQueryItems() {
        XCTAssertEqual(HermexEndpoints.reasoning(model: "gpt-5.5", provider: "codex").queryItems, [
            URLQueryItem(name: "model", value: "gpt-5.5"),
            URLQueryItem(name: "provider", value: "codex")
        ])
        XCTAssertEqual(HermexEndpoints.insights(days: 14).queryItems, [
            URLQueryItem(name: "days", value: "14")
        ])
        XCTAssertEqual(HermexEndpoints.cronOutput(jobID: "job-1", limit: 7).queryItems, [
            URLQueryItem(name: "job_id", value: "job-1"),
            URLQueryItem(name: "limit", value: "7")
        ])
        XCTAssertEqual(HermexEndpoints.cronHistory(jobID: "job-1", offset: 10, limit: 50).path, "/api/crons/history")
        XCTAssertEqual(HermexEndpoints.cronHistory(jobID: "job-1", offset: 10, limit: 50).queryItems, [
            URLQueryItem(name: "job_id", value: "job-1"),
            URLQueryItem(name: "offset", value: "10"),
            URLQueryItem(name: "limit", value: "50")
        ])
        XCTAssertEqual(HermexEndpoints.cronHistory(jobID: "job-1").queryItems, [
            URLQueryItem(name: "job_id", value: "job-1")
        ])
        XCTAssertEqual(HermexEndpoints.cronDeliveryOptions.path, "/api/crons/delivery-options")
        XCTAssertEqual(HermexEndpoints.skillContent(name: "swift", file: "SKILL.md").queryItems, [
            URLQueryItem(name: "name", value: "swift"),
            URLQueryItem(name: "file", value: "SKILL.md")
        ])
    }

    func testServerIdNormalizesOriginScope() throws {
        let url = try XCTUnwrap(URL(string: "HTTPS://Example.COM:443/some/path?token=secret#frag"))

        XCTAssertEqual(HermexServerURLNormalizer.normalizedID(for: url), "https://example.com/")
    }

    func testServerIdKeepsNonDefaultPort() throws {
        let url = try XCTUnwrap(URL(string: "http://Example.COM:8787/path"))

        XCTAssertEqual(HermexServerURLNormalizer.normalizedID(for: url), "http://example.com:8787/")
    }
}
