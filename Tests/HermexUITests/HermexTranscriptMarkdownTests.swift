import XCTest
@testable import HermexUI

final class HermexTranscriptMarkdownTests: XCTestCase {
    func testSegmentsPreserveMarkdownAndFencedCodeLanguage() {
        let segments = HermexMarkdownSegmentParser.segments(in: "Intro\n```swift\nlet answer = 42\n```\nDone")

        XCTAssertEqual(
            segments,
            [
                .markdown("Intro"),
                .code(language: "swift", content: "let answer = 42"),
                .markdown("Done")
            ]
        )
    }

    func testUnclosedFenceFallsBackToMarkdown() {
        let segments = HermexMarkdownSegmentParser.segments(in: "Before\n```swift\nlet answer = 42")

        XCTAssertEqual(segments, [.markdown("Before\n```swift\nlet answer = 42")])
    }
}
