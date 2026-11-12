import XCTest
@testable import ClimateCore

/// Same expectations as Android's CoreTest, from fixtures/thinq/energy_expected.json.
final class EnergyTests: XCTestCase {
    private var expected: [String: Any] {
        let url = URL(fileURLWithPath: #filePath).deletingLastPathComponent().appendingPathComponent("../../../fixtures/thinq/energy_expected.json").standardized
        return try! JSONSerialization.jsonObject(with: Data(contentsOf: url)) as! [String: Any]
    }
    func testWindows() {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Asia/Seoul")!
        let parser = DateFormatter(); parser.calendar = calendar; parser.timeZone = calendar.timeZone; parser.dateFormat = "yyyyMMdd"
        for case let row as [String: Any] in expected["windows"] as! [Any] {
            let today = parser.date(from: row["today"] as! String)!.addingTimeInterval(15 * 3600)
            let window = EnergyWindow.make(today: today, calendar: calendar)
            let current = row["current"] as! [String], previous = row["previous"] as! [String]
            XCTAssertEqual([window.current.0, window.current.1], current, row["today"] as! String)
            XCTAssertEqual([window.previous.0, window.previous.1], previous, row["today"] as! String)
            XCTAssertEqual(window.label, row["label"] as? String)
            XCTAssertEqual(window.compareLabel, row["compare"] as? String)
        }
    }
    func testComparisonAndUnits() {
        for case let row as [String: Any] in expected["comparisons"] as! [Any] {
            XCTAssertEqual(EnergyText.comparison(current: row["current"] as! Int, previous: row["previous"] as? Int, compareLabel: row["compare"] as! String),
                           row["text"] as? String)
        }
        for case let pair as [Any] in expected["kwh"] as! [Any] { XCTAssertEqual(EnergyText.kWh(pair[0] as! Int), pair[1] as? String) }
    }
    func testUsageSumAndPath() throws {
        let response: [String: Any] = ["result": ["dataList": [["usedDate": "20261101", "energyUsage": 981], ["usedDate": "20261102", "energyUsage": true], ["energyUsage": 944]]]]
        XCTAssertEqual(EnergyText.sum(response), 1925)
        XCTAssertNil(EnergyText.sum(["result": [:]]))
        let config = try ThinQConfig(token: "t", country: "KR", region: "kic", clientID: "c")
        let url = config.request(path: ["devices", "energy", "a/b?", "usage"], query: [URLQueryItem(name: "period", value: "DAILY")]).url?.absoluteString
        XCTAssertEqual(url, "https://api-kic.lgthinq.com/devices/energy/a%2Fb%3F/usage?period=DAILY")
        XCTAssertEqual(ThinQError.api(status: 401, code: "1309"), .notAllowed)
        XCTAssertEqual(ThinQError.api(status: 406, code: "1221"), .unsupported)
        XCTAssertEqual(ThinQError.api(status: 401, code: ""), .authentication)
    }
}
