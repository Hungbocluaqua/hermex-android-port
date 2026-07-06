import Foundation

public struct HermexMultipartFile: Equatable, Sendable {
    public var fieldName: String
    public var filename: String
    public var data: Data
    public var contentType: String

    public init(fieldName: String = "file", filename: String, data: Data, contentType: String = "application/octet-stream") {
        self.fieldName = fieldName
        self.filename = filename
        self.data = data
        self.contentType = contentType
    }
}

public struct HermexMultipartFormData: Equatable, Sendable {
    public var boundary: String
    public var body: Data

    public var contentType: String {
        "multipart/form-data; boundary=\(boundary)"
    }
}

public enum HermexMultipartFormDataBuilder {
    public static func build(
        textFields: [String: String] = [:],
        files: [HermexMultipartFile],
        boundary: String = "Boundary-\(UUID().uuidString)"
    ) -> HermexMultipartFormData {
        var body = Data()
        for key in textFields.keys.sorted() {
            if let value = textFields[key] {
                body.appendMultipartTextField(name: key, value: value, boundary: boundary)
            }
        }
        for file in files {
            body.appendMultipartFileField(file, boundary: boundary)
        }
        body.appendMultipartClosingBoundary(boundary)
        return HermexMultipartFormData(boundary: boundary, body: body)
    }
}

private extension Data {
    mutating func appendMultipartTextField(name: String, value: String, boundary: String) {
        append(Data("--\(boundary)\r\n".utf8))
        append(Data("Content-Disposition: form-data; name=\"\(name)\"\r\n\r\n".utf8))
        append(Data("\(value)\r\n".utf8))
    }

    mutating func appendMultipartFileField(_ file: HermexMultipartFile, boundary: String) {
        append(Data("--\(boundary)\r\n".utf8))
        append(Data("Content-Disposition: form-data; name=\"\(file.fieldName)\"; filename=\"\(file.filename)\"\r\n".utf8))
        append(Data("Content-Type: \(file.contentType)\r\n\r\n".utf8))
        append(file.data)
        append(Data("\r\n".utf8))
    }

    mutating func appendMultipartClosingBoundary(_ boundary: String) {
        append(Data("--\(boundary)--\r\n".utf8))
    }
}
