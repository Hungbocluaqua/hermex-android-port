import Foundation

public enum HermexServerURLNormalizer {
    public static func normalizedID(for url: URL) -> String {
        guard var components = URLComponents(url: url, resolvingAgainstBaseURL: false) else {
            return url.absoluteString
        }

        components.path = "/"
        components.query = nil
        components.fragment = nil

        if let scheme = components.scheme {
            components.scheme = scheme.lowercased()
        }
        if let host = components.host {
            components.host = host.lowercased()
        }
        if (components.scheme == "https" && components.port == 443) ||
            (components.scheme == "http" && components.port == 80) {
            components.port = nil
        }

        return components.url?.absoluteString ?? url.absoluteString
    }
}
