// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "MetronomeCore",
    platforms: [.iOS(.v15), .macOS(.v13)],
    products: [
        .library(name: "MetronomeCore", targets: ["MetronomeCore"])
    ],
    targets: [
        .target(
            name: "MetronomeCore",
            path: ".",
            exclude: [
                "ExpoPrecisionMetronome.podspec",
                "ExpoPrecisionMetronomeModule.swift",
                "ExpoPrecisionMetronomeView.swift",
                "MetronomeEngine.swift",
                "Tests"
            ]
        ),
        .testTarget(
            name: "MetronomeCoreTests",
            dependencies: ["MetronomeCore"],
            path: "Tests"
        )
    ]
)
