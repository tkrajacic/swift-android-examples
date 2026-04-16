// swift-tools-version: 6.3
// The swift-tools-version declares the minimum version of Swift required to build this package.

import CompilerPluginSupport
import PackageDescription
import Foundation.NSProcessInfo

let isSwiftJavaBuild = ProcessInfo.processInfo.environment["SWIFT_JAVA_BUILD"] != nil

let commonSwiftSettings: [SwiftSetting] = [
    .enableUpcomingFeature("ExistentialAny"),
    // .enableUpcomingFeature("InternalImportsByDefault"),
    .enableUpcomingFeature("MemberImportVisibility"),
    .enableUpcomingFeature("InferIsolatedConformances"),
    .enableUpcomingFeature("NonisolatedNonsendingByDefault"),
    .enableUpcomingFeature("ImmutableWeakCaptures"),
]

let package = Package(
  name: "WeatherLibrary",
  platforms: [.macOS(.v15), .iOS(.v13)],
  products: [
    .library(
      name: "WeatherLibrary",
      type: .dynamic,
      targets: ["WeatherLibrary"]
    )
  ],
  dependencies: [
    .package(url: "https://github.com/swiftlang/swift-java", from: "0.1.2"),
    .package(url: "https://github.com/apple/swift-openapi-generator", from: "1.6.0"),
    .package(url: "https://github.com/apple/swift-openapi-runtime", from: "1.7.0"),
    .package(url: "https://github.com/apple/swift-openapi-urlsession", from: "1.0.0"),
    .package(url: "https://github.com/swift-server/swift-openapi-async-http-client", from: "1.0.0"),
  ],
  targets: [
    .target(
        name: "WeatherLibrary",
        dependencies: [
            .product(name: "OpenAPIRuntime", package: "swift-openapi-runtime"),
            .product(name: "OpenAPIURLSession", package: "swift-openapi-urlsession", condition: .when(platforms: [.macOS, .iOS])),
            .product(name: "OpenAPIAsyncHTTPClient", package: "swift-openapi-async-http-client", condition: .when(platforms: [.android])),
        ],
        swiftSettings: commonSwiftSettings + [.swiftLanguageMode(.v5)],
        plugins: [
            .plugin(name: "OpenAPIGenerator", package: "swift-openapi-generator"),
        ]
    )
  ]
)

if isSwiftJavaBuild {
    package.targets.first?.dependencies.append(.product(name: "SwiftJava", package: "swift-java"))
    package.targets.first?.plugins?.append(.plugin(name: "JExtractSwiftPlugin", package: "swift-java"))
}
