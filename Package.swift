// swift-tools-version: 6.1

import PackageDescription

let package = Package(
    name: "HermexShared",
    platforms: [
        .iOS(.v17),
        .macOS(.v14)
    ],
    products: [
        .library(name: "HermexCore", type: .dynamic, targets: ["HermexCore"]),
        .library(name: "HermexPlatform", targets: ["HermexPlatform"]),
        .library(name: "HermexUI", type: .dynamic, targets: ["HermexUI"])
    ],
    dependencies: [
        .package(url: "https://source.skip.tools/skip-foundation.git", from: "1.4.1"),
        .package(url: "https://source.skip.tools/skip.git", from: "1.7.2"),
        .package(url: "https://source.skip.tools/skip-ui.git", from: "1.0.0"),
        .package(url: "https://source.skip.dev/skip-keychain.git", "0.0.0"..<"2.0.0")
    ],
    targets: [
        .target(
            name: "HermexCore",
            dependencies: [
                .product(name: "SkipFoundation", package: "skip-foundation")
            ],
            path: "Sources/HermexCore",
            plugins: [.plugin(name: "skipstone", package: "skip")]
        ),
        .target(
            name: "HermexPlatform",
            dependencies: [
                "HermexCore",
                .product(name: "SkipFoundation", package: "skip-foundation"),
                .product(name: "SkipKeychain", package: "skip-keychain")
            ],
            path: "Sources/HermexPlatform",
            plugins: [.plugin(name: "skipstone", package: "skip")]
        ),
        .target(
            name: "HermexUI",
            dependencies: [
                "HermexCore",
                .product(name: "SkipFoundation", package: "skip-foundation"),
                .product(name: "SkipUI", package: "skip-ui")
            ],
            path: "Sources/HermexUI",
            resources: [.process("Resources")],
            plugins: [.plugin(name: "skipstone", package: "skip")]
        ),
        .testTarget(
            name: "HermexCoreTests",
            dependencies: [
                "HermexCore",
                .product(name: "SkipTest", package: "skip")
            ],
            path: "Tests/HermexCoreTests",
            plugins: [.plugin(name: "skipstone", package: "skip")]
        ),
        .testTarget(
            name: "HermexUITests",
            dependencies: [
                "HermexCore",
                "HermexUI",
                .product(name: "SkipTest", package: "skip")
            ],
            path: "Tests/HermexUITests",
            plugins: [.plugin(name: "skipstone", package: "skip")]
        ),
        .testTarget(
            name: "HermexPlatformTests",
            dependencies: [
                "HermexCore",
                "HermexPlatform",
                .product(name: "SkipTest", package: "skip")
            ],
            path: "Tests/HermexPlatformTests",
            plugins: [.plugin(name: "skipstone", package: "skip")]
        )
    ]
)

if Context.environment["SKIP_ZERO"] ?? "0" != "0" {
    package.targets.forEach { target in
        target.plugins?.removeAll(where: { plugin in
            if case .plugin(let name, _) = plugin {
                return name == "skipstone"
            }
            return false
        })
        target.dependencies.removeAll(where: { dependency in
            if case .productItem(_, let package, _, _) = dependency {
                return package == "skip" || package?.hasPrefix("skip-") == true
            }
            return false
        })
    }
    package.dependencies.removeAll(where: { dependency in
        if case .sourceControl(_, let url, _) = dependency.kind {
            return url.hasPrefix("https://source.skip.dev/") || url.hasPrefix("https://source.skip.tools/")
        }
        return false
    })
}
