import XCTest
@testable import HermexCore

final class HermexDTOTests: XCTestCase {
    func testSessionDecodesMissingOptionalFields() throws {
        let data = Data("""
        {
          "sessions": [
            {"session_id": "abc", "title": "Planning", "unknown": {"safe": true}},
            {"title": "Untitled"}
          ]
        }
        """.utf8)

        let response = try JSONDecoder().decode(HermexSessionsResponse.self, from: data)

        XCTAssertEqual(response.sessions?.count, 2)
        XCTAssertEqual(response.sessions?.first?.sessionId, "abc")
        XCTAssertEqual(response.sessions?.last?.id, "Untitled")
    }

    func testChatMessagePreservesToolCallJson() throws {
        let data = Data("""
        {
          "messages": [
            {
              "message_id": "m1",
              "role": "assistant",
              "content": "Done",
              "tool_calls": [{"name": "shell", "args": ["pwd"]}]
            }
          ]
        }
        """.utf8)

        let response = try JSONDecoder().decode(HermexSessionResponse.self, from: data)

        XCTAssertEqual(response.messages?.first?.stableId, "m1")
        XCTAssertEqual(response.messages?.first?.toolCalls?.count, 1)
    }

    func testModelsResponseDecodesLooseJsonArrays() throws {
        let data = Data("""
        {
          "groups": [{"provider": "local", "models": ["codex"]}],
          "models": ["codex"],
          "default_model": "codex",
          "active_provider": "local"
        }
        """.utf8)

        let response = try JSONDecoder().decode(HermexModelsResponse.self, from: data)

        XCTAssertEqual(response.defaultModel, "codex")
        XCTAssertEqual(response.activeProvider, "local")
        XCTAssertEqual(response.groups?.count, 1)
        XCTAssertEqual(response.normalizedModels.map(\.id), ["codex"])
        XCTAssertEqual(response.normalizedModels.first?.provider, "local")
    }

    func testComposerOptionResponsesDecodeTolerantly() throws {
        let profiles = try JSONDecoder().decode(HermexProfilesResponse.self, from: Data("""
        {
          "active": "default",
          "single_profile_mode": false,
          "profiles": [
            {"name": "default", "display_name": "Default", "is_active": true, "model": "codex"}
          ]
        }
        """.utf8))
        let workspaces = try JSONDecoder().decode(HermexWorkspacesResponse.self, from: Data("""
        {
          "last": "/repo",
          "workspaces": ["/repo", {"path": "/tmp", "name": "Temp", "exists": true}]
        }
        """.utf8))
        let reasoning = try JSONDecoder().decode(HermexReasoningResponse.self, from: Data("""
        {
          "effort": "medium",
          "supported_efforts": ["low", "medium", "high"],
          "supports_reasoning_effort": true
        }
        """.utf8))

        XCTAssertEqual(profiles.profiles?.first?.title, "Default")
        XCTAssertEqual(profiles.active, "default")
        XCTAssertEqual(workspaces.normalizedRoots.map(\.path), ["/repo", "/tmp"])
        XCTAssertEqual(workspaces.normalizedRoots.last?.name, "Temp")
        XCTAssertEqual(reasoning.supportedEfforts, ["low", "medium", "high"])
    }

    func testSharedStateMappersExtractWorkspaceGitAndPanelState() throws {
        let directory = HermexWorkspaceState.fromDirectoryResponse(.dictionary([
            "path": .string("/repo"),
            "entries": .array([
                .dictionary(["name": .string("Sources"), "path": .string("/repo/Sources"), "type": .string("dir")]),
                .dictionary(["name": .string("README.md"), "path": .string("/repo/README.md"), "size": .number(42)])
            ])
        ]), fallbackPath: nil)
        let git = HermexGitState.fromStatusResponse(.dictionary([
            "branch": .string("main"),
            "files": .array([
                .dictionary(["path": .string("README.md"), "status": .string("M"), "additions": .number(2), "deletions": .number(1)])
            ])
        ]))
        let tasks = HermexPanelsState.tasks(from: .dictionary([
            "jobs": .array([.dictionary([
                "id": .string("job-1"),
                "name": .string("Morning"),
                "state": .string("paused"),
                "schedule": .dictionary(["expression": .string("0 9 * * 1")]),
                "prompt": .string("Prepare the digest"),
                "deliver": .string("local"),
                "skills": .array([.string("research")]),
                "model": .string("gpt-5.5"),
                "profile": .string("default"),
                "toast_notifications": .bool(false)
            ])])
        ]))
        let skills = HermexPanelsState.skills(from: .dictionary([
            "skills": .array([.dictionary(["name": .string("swift"), "enabled": .bool(true)])])
        ]))
        let memory = HermexPanelsState.memory(from: .dictionary([
            "memory": .string("Notes"),
            "user": .string("Concise"),
            "memory_path": .string("/repo/MEMORY.md")
        ]))

        XCTAssertEqual(directory.currentPath, "/repo")
        XCTAssertEqual(directory.entries.first?.isDirectory, true)
        XCTAssertEqual(directory.entries.last?.size, 42)
        XCTAssertEqual(git.branch, "main")
        XCTAssertEqual(git.files.first?.additions, 2)
        XCTAssertEqual(tasks.tasks.first?.id, "job-1")
        XCTAssertEqual(tasks.tasks.first?.status, "paused")
        XCTAssertEqual(tasks.tasks.first?.schedule, "0 9 * * 1")
        XCTAssertEqual(tasks.tasks.first?.prompt, "Prepare the digest")
        XCTAssertEqual(tasks.tasks.first?.skills, ["research"])
        XCTAssertEqual(tasks.tasks.first?.toastNotifications, false)
        XCTAssertEqual(skills.skills.first?.enabled, true)
        XCTAssertEqual(memory.memory.map(\.section), ["memory", "user"])
        XCTAssertEqual(memory.memory.first?.content, "Notes")
    }
}
