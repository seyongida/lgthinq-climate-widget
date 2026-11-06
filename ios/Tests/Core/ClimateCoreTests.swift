import XCTest
@testable import ClimateCore

final class ClimateCoreTests: XCTestCase {
    func config() throws -> ThinQConfig { try ThinQConfig(token: " test-only ", country: "kr", region: "kic", clientID: "test") }
    func testRequestContract() throws {
        let c = try config(); let r = c.request()
        XCTAssertEqual(r.url?.absoluteString, "https://api-kic.lgthinq.com/devices")
        XCTAssertEqual(r.value(forHTTPHeaderField: "Authorization"), "Bearer test-only")
        XCTAssertEqual(r.value(forHTTPHeaderField: "x-country"), "KR")
        XCTAssertEqual(r.value(forHTTPHeaderField: "x-service-phase"), "OP")
        XCTAssertEqual(r.value(forHTTPHeaderField: "x-client-id"), "test")
        XCTAssertEqual(r.timeoutInterval, 15)
        XCTAssertEqual(c.request(deviceID: "a/b ?#").url?.absoluteString, "https://api-kic.lgthinq.com/devices/a%2Fb%20%3F%23/state")
        let ids = (0..<1000).map { _ in c.request().value(forHTTPHeaderField: "x-message-id")! }
        XCTAssertEqual(Set(ids).count, 1000)
        XCTAssertTrue(ids.allSatisfy { $0.range(of: "^[A-Za-z0-9_-]{22}$", options: .regularExpression) != nil })
    }
    func testValidation() {
        for token in ["", " ", "a\r\ninjected: true"] { XCTAssertThrowsError(try ThinQConfig(token: token, country: "KR", region: "kic")) }
        for country in ["K", "KRR", "K1"] { XCTAssertThrowsError(try ThinQConfig(token: "p", country: country, region: "kic")) }
        XCTAssertThrowsError(try ThinQConfig(token: "p", country: "KR", region: "evil.com"))
    }
    func testSensorPathsAndNoTargetFallback() throws {
        let r = try Reading.parse(["airQualitySensor": ["temperature": 23.4, "humidity": 47], "humidity": ["targetHumidity": 80]])
        XCTAssertEqual(r.temperature, 23.4); XCTAssertEqual(r.humidity, 47)
        XCTAssertNil(try Reading.parse(["humidity": ["targetHumidity": 80]]).humidity)
        XCTAssertNil(try Reading.parse(["airQualitySensor": "invalid"]).temperature)
        XCTAssertThrowsError(try Reading.parse([]))
    }
    func testSensorBoundsAndTypes() {
        for value: Any in [true, "47", NSNull(), 101, -1, Double.nan, Double.infinity] { XCTAssertNil(Reading.sensor(value, range: 0...100)) }
        for value in 0...100 { XCTAssertEqual(Reading.sensor(value, range: 0...100), Double(value)) }
        XCTAssertEqual(Reading.sensor(-50, range: -50...80), -50)
        XCTAssertEqual(Reading.sensor(80, range: -50...80), 80)
        XCTAssertEqual(Reading.text(nil, unit: "%"), "—")
    }
    func testFreshness() {
        let now = Date(timeIntervalSince1970: 100)
        let r = Reading(temperature: nil, humidity: nil, fetchedAt: now)
        XCTAssertFalse(r.stale(at: now.addingTimeInterval(5399)))
        XCTAssertTrue(r.stale(at: now.addingTimeInterval(5400)))
        XCTAssertTrue(r.stale(at: now.addingTimeInterval(-1)))
    }
    func testHumidifierFiltering() throws {
        let list: [[String: Any]] = [
            ["deviceId": "a", "deviceInfo": ["deviceType": "DEVICE_HUMIDIFIER", "alias": "거실"]],
            ["deviceId": "b", "deviceInfo": ["deviceType": "DEVICE_DEHUMIDIFIER"]],
            ["deviceId": "c", "deviceInfo": ["deviceType": "DEVICE_HUMIDIFIER"]],
            ["deviceInfo": ["deviceType": "DEVICE_HUMIDIFIER"]]]
        XCTAssertEqual(try ThinQDevice.parse(list), [ThinQDevice(id: "a", name: "거실"), ThinQDevice(id: "c", name: "가습기")])
        XCTAssertThrowsError(try ThinQDevice.parse([:]))
    }
    func testEnvelopeAndErrors() throws {
        func decode(_ text: String, _ status: Int = 200) throws -> Any { try ThinQClient.unwrap(Data(text.utf8), status: status) }
        XCTAssertNotNil(try decode("{\"response\":{}}"))
        for text in ["bad json", "{}", "{\"response\":null}", "[]"] { XCTAssertThrowsError(try decode(text)) }
        for (status, code, expected): (Int, String, ThinQError) in [(401,"",.authentication), (200,"1103",.authentication), (429,"",.rateLimit), (200,"1306",.rateLimit), (400,"1222",.offline), (403,"",.forbidden), (302,"",.network)] {
            XCTAssertThrowsError(try decode("{\"error\":{\"code\":\"\(code)\"}}", status)) { XCTAssertEqual($0 as? ThinQError, expected) }
        }
        XCTAssertThrowsError(try ThinQClient.unwrap(Data(repeating: 32, count: 1_048_577), status: 200))
    }
    func testReadingCodableKeepsMissingValues() throws {
        let r = Reading(temperature: nil, humidity: 0, fetchedAt: Date(timeIntervalSince1970: 100))
        XCTAssertEqual(try JSONDecoder().decode(Reading.self, from: JSONEncoder().encode(r)), r)
    }
}
