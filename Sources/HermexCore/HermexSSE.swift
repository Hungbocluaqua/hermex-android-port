import Foundation

public enum HermexSSEEvent: Equatable, Sendable {
    case token(String)
    case usage(String)
    case done(String?)
    case error(String)
    case named(event: String, data: String)
}

public struct HermexSSEDecoder: Sendable {
    public init() {}

    public func decode(block: String) -> HermexSSEEvent? {
        var eventName: String?
        var dataLines: [String] = []

        for rawLine in block.split(separator: "\n", omittingEmptySubsequences: false) {
            let line = rawLine.trimmingCharacters(in: CharacterSet.newlines)
            if line.hasPrefix("event:") {
                eventName = String(line.dropFirst("event:".count)).trimmingCharacters(in: CharacterSet.whitespaces)
            } else if line.hasPrefix("data:") {
                dataLines.append(String(line.dropFirst("data:".count)).trimmingCharacters(in: CharacterSet.whitespaces))
            }
        }

        guard !dataLines.isEmpty else {
            return nil
        }

        let data = dataLines.joined(separator: "\n")
        let name = eventName ?? "message"
        if name == "message" || name == "token" {
            return .token(data)
        }
        if name == "usage" {
            return .usage(data)
        }
        if name == "done" {
            return .done(data.isEmpty ? nil : data)
        }
        if name == "error" {
            return .error(data)
        }
        return .named(event: name, data: data)
    }

    public func decode(streamText: String) -> [HermexSSEEvent] {
        streamText
            .replacingOccurrences(of: "\r\n", with: "\n")
            .components(separatedBy: "\n\n")
            .compactMap { block in
                let trimmed = block.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines)
                guard !trimmed.isEmpty else { return nil }
                return decode(block: trimmed)
            }
    }
}

public struct HermexSSEStreamClient: Sendable {
    private let url: URL
    private let customHeaders: [HermexCustomHeader]
    private let decoder: HermexSSEDecoder

    public init(url: URL, customHeaders: [HermexCustomHeader] = [], decoder: HermexSSEDecoder = HermexSSEDecoder()) {
        self.url = url
        self.customHeaders = customHeaders
        self.decoder = decoder
    }

    public func events() -> AsyncThrowingStream<HermexSSEEvent, Error> {
        let url = self.url
        let customHeaders = self.customHeaders
        let decoder = self.decoder

        return AsyncThrowingStream { continuation in
            let task = Task {
                do {
                    var request = URLRequest(url: url)
                    request.httpMethod = "GET"
                    request.setValue("text/event-stream", forHTTPHeaderField: "Accept")
                    request.setValue("no-cache", forHTTPHeaderField: "Cache-Control")
                    for header in customHeaders {
                        request.setValue(header.sanitizedValue, forHTTPHeaderField: header.sanitizedName)
                    }

#if SKIP
                    // Skip currently lacks progressive URLSession.bytes support. Fall back to a
                    // blocking body read and emit decoded events so chat can still complete.
                    let (data, response) = try await URLSession.shared.data(for: request)
                    guard let http = response as? HTTPURLResponse else {
                        throw HermexAPIError.invalidResponse
                    }
                    if http.statusCode == 401 {
                        throw HermexAPIError.unauthorized
                    }
                    guard (200..<300).contains(http.statusCode) else {
                        throw HermexAPIError.http(
                            statusCode: http.statusCode,
                            body: String(data: data, encoding: String.Encoding.utf8)
                        )
                    }
                    let text = String(data: data, encoding: String.Encoding.utf8) ?? ""
                    for event in decoder.decode(streamText: text) {
                        continuation.yield(event)
                    }
#else
                    let (bytes, response) = try await URLSession.shared.bytes(for: request)
                    guard let http = response as? HTTPURLResponse else {
                        throw HermexAPIError.invalidResponse
                    }
                    if http.statusCode == 401 {
                        throw HermexAPIError.unauthorized
                    }
                    guard (200..<300).contains(http.statusCode) else {
                        throw HermexAPIError.http(statusCode: http.statusCode, body: nil)
                    }

                    var buffer = ""
                    for try await byte in bytes {
                        buffer.append(Character(UnicodeScalar(byte)))
                        while let range = buffer.range(of: "\n\n") {
                            let block = String(buffer[..<range.lowerBound])
                            buffer = String(buffer[range.upperBound...])
                            if let event = decoder.decode(block: block) {
                                continuation.yield(event)
                                if case .done = event {
                                    continuation.finish()
                                    return
                                }
                                if case .error = event {
                                    continuation.finish()
                                    return
                                }
                            }
                        }
                    }
                    if let event = decoder.decode(block: buffer) {
                        continuation.yield(event)
                    }
#endif
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: error)
                }
            }

            continuation.onTermination = { _ in
                task.cancel()
            }
        }
    }
}
