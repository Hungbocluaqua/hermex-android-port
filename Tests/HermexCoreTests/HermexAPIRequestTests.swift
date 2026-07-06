import XCTest
@testable import HermexCore

final class HermexAPIRequestTests: XCTestCase {
    func testPostRequestOmitsOriginAndReferer() throws {
        let builder = HermexAPIRequestBuilder(
            baseURL: try XCTUnwrap(URL(string: "https://example.test")),
            customHeaders: {
                [
                    HermexCustomHeader(name: "Origin", value: "https://evil.test"),
                    HermexCustomHeader(name: "Referer", value: "https://evil.test/path"),
                    HermexCustomHeader(name: "X-Proxy-Token", value: " secret ")
                ]
            }
        )

        let request = builder.request(
            endpoint: HermexEndpoints.login,
            method: "POST",
            body: Data("{}".utf8)
        )

        XCTAssertNil(request.value(forHTTPHeaderField: "Origin"))
        XCTAssertNil(request.value(forHTTPHeaderField: "Referer"))
        XCTAssertEqual(request.value(forHTTPHeaderField: "X-Proxy-Token"), "secret")
        XCTAssertEqual(request.value(forHTTPHeaderField: "Accept"), "application/json")
        XCTAssertEqual(request.value(forHTTPHeaderField: "Content-Type"), "application/json")
    }

    func testBuiltInHeadersWinOverCustomHeaders() throws {
        let builder = HermexAPIRequestBuilder(
            baseURL: try XCTUnwrap(URL(string: "https://example.test")),
            customHeaders: {
                [
                    HermexCustomHeader(name: "Accept", value: "text/plain"),
                    HermexCustomHeader(name: "Content-Type", value: "text/plain")
                ]
            }
        )

        let request = builder.request(
            endpoint: HermexEndpoints.sessions(),
            method: "POST",
            body: Data("{}".utf8),
            accept: "application/json"
        )

        XCTAssertEqual(request.value(forHTTPHeaderField: "Accept"), "application/json")
        XCTAssertEqual(request.value(forHTTPHeaderField: "Content-Type"), "application/json")
    }
}
