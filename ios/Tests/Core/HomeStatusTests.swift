import XCTest
@testable import ClimateCore

/// Checks the shared fixtures in fixtures/thinq. Android's CoreTest checks the same expected.json.
final class HomeStatusTests: XCTestCase {
    private let folder = URL(fileURLWithPath: #filePath).deletingLastPathComponent().appendingPathComponent("../../../fixtures/thinq").standardized

    private func json(_ name: String) throws -> Any {
        try JSONSerialization.jsonObject(with: Data(contentsOf: folder.appendingPathComponent(name)))
    }

    func testSharedFixtures() throws {
        let expected = try XCTUnwrap(json("expected.json") as? [String: [String: Any]])
        XCTAssertGreaterThanOrEqual(expected.count, 19)
        let now = Date(timeIntervalSince1970: 1_000_000)
        for (file, want) in expected.sorted(by: { $0.key < $1.key }) {
            let status = HomeStatus.parse(try json(file), type: want["type"] as! String, now: now)
            XCTAssertEqual(status.summary, want["summary"] as? String, file)
            XCTAssertEqual(status.alerts, want["alerts"] as? [String], file)
            let cycles = (want["cycles"] as! [[Any]]).map { row in
                [row[0] as! String, row[1] as! String, (row[2] as? Int).map(String.init) ?? "nil", (row[3] as? Int).map(String.init) ?? "nil"]
            }
            XCTAssertEqual(status.cycles.map { [$0.state, $0.phase.rawValue, $0.remaining.map(String.init) ?? "nil", $0.total.map(String.init) ?? "nil"] }, cycles, file)
            if let humidity = want["humidity"] as? Double { XCTAssertEqual(status.humidity, humidity, file) }
            if let temperature = want["temperature"] as? Double { XCTAssertEqual(status.temperature, temperature, file) }
            if let pm25 = want["pm25"] as? Double { XCTAssertEqual(status.pm25, pm25, file) }
            if let level = want["airLevel"] as? String { XCTAssertEqual(status.airLevel, level, file) }
            if let power = want["power"] as? Bool { XCTAssertEqual(status.power, power, file) }
        }
    }

    func testCountdownTimesComeFromFetchTime() throws {
        let now = Date(timeIntervalSince1970: 2_000_000)
        let status = HomeStatus.parse(try json("running_washer.json"), type: "DEVICE_WASHER", now: now)
        let cycle = try XCTUnwrap(status.primaryCycle)
        XCTAssertEqual(status.endsAt(cycle), now.addingTimeInterval(1380))
        XCTAssertEqual(status.startedAt(cycle), now.addingTimeInterval(1380 - 3480))
        let paused = HomeStatus.parse(try json("paused_styler.json"), type: "DEVICE_STYLER", now: now)
        XCTAssertNil(paused.endsAt(try XCTUnwrap(paused.primaryCycle)), "A paused cycle must not count down")
    }

    func testDurationText() {
        XCTAssertEqual(HomeFormat.duration(1), "1분")
        XCTAssertEqual(HomeFormat.duration(60), "1분")
        XCTAssertEqual(HomeFormat.duration(61), "2분")
        XCTAssertEqual(HomeFormat.duration(3600), "1시간")
        XCTAssertEqual(HomeFormat.duration(3900), "1시간 5분")
    }

    func testStatusSurvivesStorage() throws {
        let status = HomeStatus.parse(try json("running_washtower.json"), type: "DEVICE_WASHTOWER", now: Date(timeIntervalSince1970: 5))
        XCTAssertEqual(try JSONDecoder().decode(HomeStatus.self, from: JSONEncoder().encode(status)), status)
    }

    func testEveryKnownStateHasAPhaseAndLabel() {
        let states = ["RINSING", "INITIAL", "FIRMWARE", "DETECTING", "RUNNING", "END", "PAUSE", "SOAKING", "RESERVED", "SPINNING",
                      "POWER_OFF", "ERROR", "STEAM_SOFTENING", "FROZEN_PREVENT_INITIAL", "FROZEN_PREVENT_RUNNING", "FROZEN_PREVENT_PAUSE",
                      "DISPENSING", "RINSE_HOLD", "PREWASH", "DETERGENT_AMOUNT", "ADD_DRAIN", "REFRESHING", "DRYING", "COOLING",
                      "WRINKLE_CARE", "DONE", "CLEANING", "PREFERENCE", "PREHEATING", "PAUSED", "COOKING_IN_PROGRESS",
                      "PREHEATING_IS_DONE", "COMPLETE", "STAY", "STERILIZE", "RUNNING_END", "PRESTEAM", "NIGHT_DRY", "SLEEP",
                      "STEAM", "POWER_FAIL", "CANCEL"]
        for state in states {
            XCTAssertNotNil(HomeCatalog.phase(state), state)
            XCTAssertNotEqual(HomeCatalog.stateLabel(type: "DEVICE_WASHER", state: state), "작동 중", state)
        }
    }
}
