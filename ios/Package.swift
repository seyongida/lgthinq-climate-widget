// swift-tools-version: 5.9
import PackageDescription
let package = Package(name: "ClimateCore", platforms: [.macOS(.v13), .iOS(.v17)], products: [.library(name: "ClimateCore", targets: ["ClimateCore"])], targets: [.target(name: "ClimateCore", path: "Shared/Core"), .testTarget(name: "ClimateCoreTests", dependencies: ["ClimateCore"], path: "Tests/Core")])
