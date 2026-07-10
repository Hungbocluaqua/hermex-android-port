import XCTest
@testable import HermexCore

final class HermexMessageActionTests: XCTestCase {
    func testContextMatchesAndroidHistorySemantics() throws {
        let messages = [
            HermexChatMessageDTO(role: "user", content: "Hello"),
            HermexChatMessageDTO(role: "assistant", content: "# Answer\n\n- Done")
        ]

        let context = try XCTUnwrap(
            HermexMessageActionContextResolver.context(
                for: messages[1],
                visibleIndex: 1,
                messagesOffset: 4
            )
        )

        XCTAssertEqual(context.role, .assistant)
        XCTAssertEqual(context.visibleIndex, 1)
        XCTAssertEqual(context.fullHistoryIndex, 5)
        XCTAssertEqual(context.keepCountThroughMessage, 6)
        XCTAssertEqual(context.copyText, "# Answer\n\n- Done")
        XCTAssertEqual(context.listenText, "Answer\nDone")
    }

    func testPrecedingUserMessageSkipsNonUserRows() {
        let messages = [
            HermexChatMessageDTO(role: "user", text: "First"),
            HermexChatMessageDTO(role: "assistant", content: "Response"),
            HermexChatMessageDTO(role: "tool", content: "Ignored")
        ]

        XCTAssertEqual(
            HermexMessageActionContextResolver.precedingUserMessageText(
                in: messages,
                beforeVisibleIndex: 3
            ),
            "First"
        )
    }
}
