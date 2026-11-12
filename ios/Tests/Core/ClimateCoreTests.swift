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
        for token in ["", " ", "a\r\ninjected: true", "a\tb", "a b", "aé"] { XCTAssertThrowsError(try ThinQConfig(token: token, country: "KR", region: "kic")) }
        XCTAssertThrowsError(try ThinQConfig(token: "p", country: "KR", region: "kic", clientID: "test\n"))
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
    func testDemoReadingIsPlausibleAndFresh() {
        for hour in 0..<48 {
            let now = Date(timeIntervalSince1970: Double(hour) * 3600)
            let r = DemoData.reading(at: now)
            XCTAssertEqual(r.fetchedAt, now)
            XCTAssertFalse(r.stale(at: now))
            XCTAssertTrue((21...25).contains(r.temperature!))
            XCTAssertTrue((42...52).contains(r.humidity!))
        }
    }
    func testFreshness() {
        let now = Date(timeIntervalSince1970: 100)
        let r = Reading(temperature: nil, humidity: nil, fetchedAt: now)
        XCTAssertFalse(r.stale(at: now.addingTimeInterval(5399)))
        XCTAssertTrue(r.stale(at: now.addingTimeInterval(5400)))
        XCTAssertTrue(r.stale(at: now.addingTimeInterval(-1)))
    }
    func testDeviceFilteringMatchesAndroid() throws {
        let list: [[String: Any]] = [
            ["deviceId": "a", "deviceInfo": ["deviceType": "DEVICE_HUMIDIFIER", "alias": "거실"]],
            ["deviceId": "b", "deviceInfo": ["deviceType": "DEVICE_WASHER", "alias": "트롬"]],
            ["deviceId": "x", "deviceInfo": ["deviceType": "NOT_A_DEVICE"]],
            ["deviceId": "c", "deviceInfo": ["deviceType": "DEVICE_HUMIDIFIER", "alias": "  "]],
            ["deviceId": "d", "deviceInfo": ["deviceType": "DEVICE_AIR_PURIFIER"]],
            ["deviceId": "e", "deviceInfo": ["deviceType": "DEVICE_AIR_PURIFIER_FAN", "alias": "안방"]],
            ["deviceId": "a", "deviceInfo": ["deviceType": "DEVICE_HUMIDIFIER", "alias": "중복"]],
            ["deviceInfo": ["deviceType": "DEVICE_HUMIDIFIER"]]]
        let devices = try ThinQDevice.parse(list)
        XCTAssertEqual(devices, [ThinQDevice(id: "a", name: "거실"), ThinQDevice(id: "b", name: "트롬", type: "DEVICE_WASHER"), ThinQDevice(id: "c", name: "가습기"),
                                 ThinQDevice(id: "d", name: "공기청정기", type: "DEVICE_AIR_PURIFIER"),
                                 ThinQDevice(id: "e", name: "안방", type: "DEVICE_AIR_PURIFIER_FAN")])
        XCTAssertEqual(devices.map(\.typeName), ["가습기", "세탁기", "가습기", "공기청정기", "공기청정 팬"])
        XCTAssertThrowsError(try ThinQDevice.parse([:]))
    }
    func testRenamedDevicesKeepSelectionAndOrder() {
        let selected = [ThinQDevice(id: "fan", name: "존 아마", type: "DEVICE_AIR_PURIFIER_FAN"), ThinQDevice(id: "hum", name: "가습기"),
                        ThinQDevice(id: "gone", name: "삭제됨", type: "DEVICE_WASHER")]
        let found = [ThinQDevice(id: "new", name: "새 기기", type: "DEVICE_DRYER"), ThinQDevice(id: "hum", name: "하이드로타워"),
                     ThinQDevice(id: "fan", name: "공기청정기", type: "DEVICE_AIR_PURIFIER_FAN")]
        XCTAssertEqual(ThinQDevice.merge(selected: selected, found: found),
                       [ThinQDevice(id: "fan", name: "공기청정기", type: "DEVICE_AIR_PURIFIER_FAN"), ThinQDevice(id: "hum", name: "하이드로타워")])
        XCTAssertEqual(ThinQDevice.merge(selected: selected, found: []), selected, "An empty answer must not wipe the selection")
    }
    func testCompactFormatMatchesAndroidWidget() {
        XCTAssertEqual(Reading.compact(24.5, unit: "°"), "24.5°")
        XCTAssertEqual(Reading.compact(47, unit: "%"), "47%")
        XCTAssertEqual(Reading.compact(nil, unit: "%"), "—")
    }
    func testComfortBoundaries() {
        XCTAssertEqual(Comfort(humidity: nil), .unknown)
        XCTAssertEqual(Comfort(humidity: 39.9), .dry)
        XCTAssertEqual(Comfort(humidity: 40), .good)
        XCTAssertEqual(Comfort(humidity: 60), .good)
        XCTAssertEqual(Comfort(humidity: 60.1), .humid)
        XCTAssertNil(Comfort.average([nil, nil]))
        XCTAssertEqual(Comfort.average([40, nil, 60]), 50)
    }
    func testColorParsingAndContrast() {
        XCTAssertEqual(RGB(hex: "#FAF8F2"), RGB(250, 248, 242))
        XCTAssertEqual(RGB(hex: " 2f6c53 "), RGB(47, 108, 83))
        for bad in ["", "#FFF", "GGGGGG", "#1234567", "+12345"] { XCTAssertNil(RGB(hex: bad), bad) }
        XCTAssertEqual(RGB(0, 0, 0).contrast(with: RGB(255, 255, 255)), 21, accuracy: 0.001)
    }
    func testEveryPresetIsLegible() {
        XCTAssertEqual(WidgetStyle.names.count, WidgetStyle.presets.count)
        for style in WidgetStyle.presets {
            XCTAssertGreaterThanOrEqual(style.background.contrast(with: style.foreground), 4.5)
            XCTAssertGreaterThanOrEqual(style.background.contrast(with: style.muted), 4.5)
            XCTAssertGreaterThanOrEqual(style.background.contrast(with: style.accent), 3)
            XCTAssertGreaterThanOrEqual(style.background.contrast(with: style.warning), 3)
        }
    }
    func testStyleResolutionFallsBackToLegibleColors() {
        XCTAssertEqual(WidgetStyle.resolve(preset: 3, customBackground: nil, customAccent: nil, opacity: 100), WidgetStyle.presets[3])
        XCTAssertEqual(WidgetStyle.resolve(preset: 99, customBackground: "bad", customAccent: "bad", opacity: 100), WidgetStyle.presets[5])
        XCTAssertEqual(WidgetStyle.resolve(preset: 0, customBackground: nil, customAccent: nil, opacity: 0).opacity, 0)
        XCTAssertEqual(WidgetStyle.resolve(preset: 0, customBackground: nil, customAccent: nil, opacity: -5).opacity, 0)
        XCTAssertEqual(WidgetStyle.resolve(preset: 0, customBackground: nil, customAccent: nil, opacity: 500).opacity, 100)
        let custom = WidgetStyle.resolve(preset: 0, customBackground: "#112233", customAccent: "#FFEEDD", opacity: 80)
        XCTAssertEqual(custom, WidgetStyle(background: RGB(17, 34, 51), accent: RGB(255, 238, 221), opacity: 80))
        let clash = WidgetStyle.resolve(preset: 0, customBackground: "#FFFFFF", customAccent: "#FFFFFE", opacity: 100)
        XCTAssertEqual(clash.accent, clash.foreground)
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
