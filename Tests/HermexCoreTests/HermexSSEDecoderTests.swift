import XCTest
@testable import HermexCore

final class HermexSSEDecoderTests: XCTestCase {
    func testTokenEventDefaultsWhenNameIsMissing() {
        let decoder = HermexSSEDecoder()

        XCTAssertEqual(decoder.decode(block: "data: hello"), .token("hello"))
    }

    func testDoneEventCarriesOptionalPayload() {
        let decoder = HermexSSEDecoder()

        XCTAssertEqual(decoder.decode(block: "event: done\ndata: {\"ok\":true}"), .done("{\"ok\":true}"))
    }

    func testUnknownNamedEventIsPreserved() {
        let decoder = HermexSSEDecoder()

        XCTAssertEqual(decoder.decode(block: "event: custom\ndata: payload"), .named(event: "custom", data: "payload"))
    }
}
