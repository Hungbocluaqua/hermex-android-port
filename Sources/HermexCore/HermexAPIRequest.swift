import Foundation

public struct HermexAPIRequestBuilder: Sendable {
    public var baseURL: URL
    public var customHeaders: @Sendable () -> [HermexCustomHeader]

    public init(
        baseURL: URL,
        customHeaders: @escaping @Sendable () -> [HermexCustomHeader] = { [] }
    ) {
        self.baseURL = baseURL
        self.customHeaders = customHeaders
    }

    public func request(
        endpoint: HermexEndpoint,
        method: String,
        body: Data? = nil,
        accept: String = "application/json",
        contentType: String? = nil,
        timeout: TimeInterval? = nil
    ) -> URLRequest {
        var request = URLRequest(url: endpoint.url(relativeTo: baseURL))
        request.httpMethod = method
        request.cachePolicy = URLRequest.CachePolicy.reloadIgnoringLocalCacheData
        if let timeout {
            request.timeoutInterval = timeout
        }

        for header in customHeaders().sanitizedForClient() {
            request.setValue(header.sanitizedValue, forHTTPHeaderField: header.sanitizedName)
        }

        request.setValue(accept, forHTTPHeaderField: "Accept")
        if let body {
            request.setValue(contentType ?? "application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = body
        }

        request.setValue(nil, forHTTPHeaderField: "Origin")
        request.setValue(nil, forHTTPHeaderField: "Referer")
        return request
    }
}
