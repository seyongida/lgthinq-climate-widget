import Foundation
import CoreFoundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

struct ThinQConfig: Codable, Equatable {
    let token: String
    let country: String
    let region: String
    let clientID: String
    init(token: String, country: String, region: String, clientID: String = "climate-" + UUID().uuidString) throws {
        let token = token.trimmingCharacters(in: .whitespacesAndNewlines)
        let country = country.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        guard !token.isEmpty, token.utf8.allSatisfy({ (33...126).contains($0) }),
              country.utf8.count == 2, country.utf8.allSatisfy({ (65...90).contains($0) }),
              ["kic", "aic", "eic"].contains(region), (1...100).contains(clientID.utf8.count),
              clientID.utf8.allSatisfy({ (65...90).contains($0) || (97...122).contains($0) || (48...57).contains($0) || $0 == 45 || $0 == 95 }) else { throw ThinQError.settings }
        self.token = token; self.country = country; self.region = region; self.clientID = clientID
    }
    func request(deviceID: String? = nil) -> URLRequest {
        request(path: deviceID.map { ["devices", $0, "state"] } ?? ["devices"])
    }
    /// Each path segment is percent-encoded, so an identifier can never add segments or a query.
    func request(path: [String], query: [URLQueryItem] = []) -> URLRequest {
        var components = URLComponents(string: "https://api-\(region).lgthinq.com")!
        let allowed = CharacterSet(charactersIn: "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~")
        components.percentEncodedPath = path.map { "/" + $0.addingPercentEncoding(withAllowedCharacters: allowed)! }.joined()
        if !query.isEmpty { components.queryItems = query }
        let url = components.url!
        var request = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 15)
        var uuid = UUID().uuid
        let messageID = withUnsafeBytes(of: &uuid) { Data($0) }.base64EncodedString()
            .replacingOccurrences(of: "+", with: "-").replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: "=", with: "")
        request.allHTTPHeaderFields = ["Authorization": "Bearer \(token)", "x-country": country,
            "x-client-id": clientID, "x-message-id": messageID, "x-service-phase": "OP",
            "x-api-key": "v6GFvkweNo7DK7yD3ylIZ9w52aKBU0eJ7wLXkSR3", "Accept": "application/json"]
        return request
    }
}
struct ThinQDevice: Identifiable, Equatable, Codable {
    let id: String
    let name: String
    let type: String
    init(id: String, name: String, type: String = "DEVICE_HUMIDIFIER") { self.id = id; self.name = name; self.type = type }
    var typeName: String { HomeCatalog.name(type) }
    /// Keeps the person's selection and order, but takes current names and types from ThinQ and drops deleted devices.
    /// New devices are not added here; they appear in the device picker through the catalog.
    static func merge(selected: [ThinQDevice], found: [ThinQDevice]) -> [ThinQDevice] {
        guard !found.isEmpty else { return selected }
        return selected.compactMap { old in
            if DemoData.isDemo(old.id) { return old }
            return found.first { $0.id == old.id }
        }
    }
    static func parse(_ response: Any) throws -> [ThinQDevice] {
        guard let list = response as? [Any] else { throw ThinQError.invalidResponse }
        var result: [ThinQDevice] = []
        for item in list {
            guard let item = item as? [String: Any], let info = item["deviceInfo"] as? [String: Any],
                  let type = info["deviceType"] as? String, type.hasPrefix("DEVICE_"),
                  let id = item["deviceId"] as? String, !id.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                  !result.contains(where: { $0.id == id }) else { continue }
            let alias = (info["alias"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            result.append(ThinQDevice(id: id, name: alias.isEmpty ? HomeCatalog.name(type) : alias, type: type))
        }
        return result
    }
}
struct Reading: Codable, Equatable {
    let temperature: Double?
    let humidity: Double?
    let fetchedAt: Date
    func stale(at date: Date) -> Bool { date < fetchedAt || date.timeIntervalSince(fetchedAt) >= 90 * 60 }
    static func sensor(_ raw: Any?, range: ClosedRange<Double>) -> Double? {
        guard let number = raw as? NSNumber, CFGetTypeID(number) != CFBooleanGetTypeID() else { return nil }
        let value = number.doubleValue
        return value.isFinite && range.contains(value) ? value : nil
    }
    static func parse(_ state: Any, now: Date = Date()) throws -> Reading {
        guard let state = state as? [String: Any] else { throw ThinQError.invalidResponse }
        let sensors = state["airQualitySensor"] as? [String: Any] ?? [:]
        return Reading(temperature: sensor(sensors["temperature"], range: -50...80),
                       humidity: sensor(sensors["humidity"], range: 0...100), fetchedAt: now)
    }
    static func text(_ number: Double?, unit: String) -> String {
        guard let number else { return "—" }
        return String(format: "%.1f%@", locale: Locale(identifier: "ko_KR"), number, unit)
    }
    // Widget format: whole numbers drop the decimal so the digits can be larger.
    static func compact(_ number: Double?, unit: String) -> String {
        guard let number else { return "—" }
        return String(format: number == number.rounded() ? "%.0f%@" : "%.1f%@", locale: Locale(identifier: "ko_KR"), number, unit)
    }
}
// Sample data for trying the app without an LG account. It is generated on-device, makes no
// network requests, and must always be labelled as sample data in the UI.
enum DemoData {
    static let prefix = "thinqclimate-demo"
    static let devices = [ThinQDevice(id: prefix + "-1", name: "체험용 가습기"),
                          ThinQDevice(id: prefix + "-2", name: "체험용 공기청정기", type: "DEVICE_AIR_PURIFIER_FAN"),
                          ThinQDevice(id: prefix + "-3", name: "체험용 세탁기", type: "DEVICE_WASHER"),
                          ThinQDevice(id: prefix + "-4", name: "체험용 건조기", type: "DEVICE_DRYER"),
                          ThinQDevice(id: prefix + "-5", name: "체험용 식기세척기", type: "DEVICE_DISH_WASHER"),
                          ThinQDevice(id: prefix + "-6", name: "체험용 냉장고", type: "DEVICE_REFRIGERATOR")]
    static func isDemo(_ id: String) -> Bool { id.hasPrefix(prefix) }
    static func energy(for device: ThinQDevice, window: EnergyWindow, now: Date) -> EnergyUsage? {
        let sample = ["DEVICE_REFRIGERATOR": (11_400, 11_820), "DEVICE_WASHER": (5_560, 6_900)][device.type]
        return sample.map { EnergyUsage(current: $0.0, previous: $0.1, label: window.label, compareLabel: window.compareLabel, fetchedAt: now) }
    }
    /// A raw ThinQ-shaped state, so sample devices go through the same parser as real ones.
    static func state(for device: ThinQDevice, at date: Date) -> Any {
        let index = Double(devices.firstIndex(of: device) ?? 0)
        let climate = reading(at: date, offset: index)
        switch device.type {
        case "DEVICE_WASHER":
            // A 45-minute wash that is always between 5 and 45 minutes from finishing, so the sample shows a live countdown.
            let left = 2700 - Int(date.timeIntervalSince1970) % 2400
            let state = left < 600 ? "SPINNING" : left < 1500 ? "RINSING" : "RUNNING"
            let remain = left
            return [["runState": ["currentState": state], "timer": ["remainHour": remain / 3600, "remainMinute": remain % 3600 / 60,
                     "totalHour": 0, "totalMinute": 45], "location": ["locationName": "MAIN"]]]
        case "DEVICE_DRYER":
            return ["runState": ["currentState": "WRINKLE_CARE"], "timer": ["remainHour": 0, "remainMinute": 0, "totalHour": 1, "totalMinute": 50]]
        case "DEVICE_DISH_WASHER":
            return ["runState": ["currentState": "RESERVED"], "doorStatus": ["doorState": "CLOSE"],
                    "timer": ["remainHour": 0, "remainMinute": 0, "totalHour": 2, "totalMinute": 10, "relativeHourToStart": 2, "relativeMinuteToStart": 30]]
        case "DEVICE_REFRIGERATOR":
            return ["temperatureInUnits": [["targetTemperatureC": 3, "locationName": "FRIDGE"], ["targetTemperatureC": -20, "locationName": "FREEZER"]],
                    "doorStatus": [["doorState": "CLOSE", "locationName": "MAIN"]]]
        default:
            return ["operation": [device.type == "DEVICE_HUMIDIFIER" ? "humidifierOperationMode" : "airFanOperationMode": "POWER_ON"],
                    "airQualitySensor": ["temperature": climate.temperature as Any, "humidity": climate.humidity as Any,
                                         "PM2": 6 + index * 3, "PM10": 11 + index * 4, "totalPollutionLevel": "GOOD"]]
        }
    }
    static func reading(at date: Date, offset: Double = 0) -> Reading {
        let phase = date.timeIntervalSince1970 / 3600 * .pi / 6 + offset
        return Reading(temperature: ((23 + 1.5 * sin(phase)) * 10).rounded() / 10,
                       humidity: (47 + 5 * cos(phase)).rounded(), fetchedAt: date)
    }
}
// Indoor humidity guidance: 40–60% is shown as comfortable.
enum Comfort: Equatable {
    case dry, good, humid, unknown
    static let range = 40.0...60.0
    init(humidity: Double?) {
        guard let humidity else { self = .unknown; return }
        self = humidity < Self.range.lowerBound ? .dry : humidity > Self.range.upperBound ? .humid : .good
    }
    var label: String {
        switch self {
        case .dry: return "건조"
        case .good: return "쾌적"
        case .humid: return "습함"
        case .unknown: return "—"
        }
    }
    var headline: String {
        switch self {
        case .dry: return "조금 건조해요"
        case .good: return "쾌적해요"
        case .humid: return "조금 습해요"
        case .unknown: return "센서값을 기다려요"
        }
    }
    static func average(_ values: [Double?]) -> Double? {
        let known = values.compactMap { $0 }
        return known.isEmpty ? nil : known.reduce(0, +) / Double(known.count)
    }
}
struct RGB: Equatable {
    let r: Int, g: Int, b: Int
    init(_ r: Int, _ g: Int, _ b: Int) { self.r = r; self.g = g; self.b = b }
    init?(hex: String) {
        var value = hex.trimmingCharacters(in: .whitespacesAndNewlines)
        if value.hasPrefix("#") { value.removeFirst() }
        guard value.utf8.count == 6, value.utf8.allSatisfy({ (48...57).contains($0) || (65...70).contains($0) || (97...102).contains($0) }),
              let number = Int(value, radix: 16) else { return nil }
        self.init(number >> 16, (number >> 8) & 255, number & 255)
    }
    var luminance: Double {
        func linear(_ c: Int) -> Double { let s = Double(c) / 255; return s <= 0.04045 ? s / 12.92 : pow((s + 0.055) / 1.055, 2.4) }
        return 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b)
    }
    func contrast(with other: RGB) -> Double {
        let x = luminance, y = other.luminance
        return (max(x, y) + 0.05) / (min(x, y) + 0.05)
    }
    func blended(with other: RGB, amount: Double) -> RGB {
        func mix(_ a: Int, _ b: Int) -> Int { Int((Double(a) * amount + Double(b) * (1 - amount)).rounded()) }
        return RGB(mix(r, other.r), mix(g, other.g), mix(b, other.b))
    }
}
// Same palette and legibility rules as Android's WidgetStyle.
struct WidgetStyle: Equatable {
    let background: RGB, accent: RGB, opacity: Int
    init(background: RGB, accent: RGB, opacity: Int = 100) {
        self.background = background; self.accent = accent; self.opacity = max(0, min(100, opacity))
    }
    static let names = ["아이보리", "포레스트", "슬레이트", "오션", "라벤더", "피치"]
    static let presets = [
        WidgetStyle(background: RGB(250, 248, 242), accent: RGB(47, 108, 83)),
        WidgetStyle(background: RGB(31, 59, 48), accent: RGB(183, 224, 170)),
        WidgetStyle(background: RGB(35, 43, 54), accent: RGB(173, 210, 236)),
        WidgetStyle(background: RGB(228, 241, 249), accent: RGB(43, 101, 145)),
        WidgetStyle(background: RGB(239, 233, 248), accent: RGB(111, 77, 151)),
        WidgetStyle(background: RGB(251, 234, 221), accent: RGB(158, 80, 54))]
    var foreground: RGB {
        let dark = RGB(34, 47, 42), light = RGB(247, 250, 248)
        let chosen = background.contrast(with: dark) >= background.contrast(with: light) ? dark : light
        if background.contrast(with: chosen) >= 4.5 { return chosen }
        return background.contrast(with: RGB(0, 0, 0)) >= background.contrast(with: RGB(255, 255, 255)) ? RGB(0, 0, 0) : RGB(255, 255, 255)
    }
    var muted: RGB {
        let value = foreground.blended(with: background, amount: 0.68)
        return background.contrast(with: value) >= 4.5 ? value : foreground
    }
    var warning: RGB { background.luminance > 0.35 ? RGB(151, 80, 28) : RGB(244, 195, 132) }
    // The system widget editor cannot show validation errors, so invalid input falls back to a legible color.
    static func resolve(preset: Int, customBackground: String?, customAccent: String?, opacity: Int) -> WidgetStyle {
        let base = presets[max(0, min(presets.count - 1, preset))]
        let background = customBackground.flatMap(RGB.init(hex:)) ?? base.background
        let accent = customAccent.flatMap(RGB.init(hex:)) ?? base.accent
        let style = WidgetStyle(background: background, accent: accent, opacity: opacity)
        return background.contrast(with: accent) >= 3 ? style : WidgetStyle(background: background, accent: style.foreground, opacity: opacity)
    }
}
enum ThinQError: LocalizedError, Equatable {
    case settings, invalidResponse, authentication, rateLimit, offline, forbidden, network, notAllowed, unsupported
    var errorDescription: String? {
        switch self {
        case .settings: return "PAT, 국가 코드, 서버 지역을 확인해 주세요."
        case .invalidResponse: return "ThinQ 응답 형식이 올바르지 않습니다."
        case .authentication: return "인증 만료 · 앱에서 PAT를 다시 설정해 주세요."
        case .rateLimit: return "요청 한도 초과 · 잠시 후 다시 시도합니다."
        case .offline: return "기기 오프라인 · 마지막 수신값입니다."
        case .forbidden: return "접근 거부 · PAT 읽기 권한과 국가/지역을 확인해 주세요."
        case .network: return "연결 실패 · 네트워크와 기기 상태를 확인해 주세요."
        case .notAllowed: return "이 토큰에 허용되지 않은 기능입니다."
        case .unsupported: return "이 모델은 지원하지 않는 기능입니다."
        }
    }
    static func api(status: Int, code: String) -> ThinQError {
        if ["1305", "1309"].contains(code) { return .notAllowed }
        if ["1219", "1220", "1221"].contains(code) { return .unsupported }
        if status == 401 || ["1103", "1218", "1302"].contains(code) { return .authentication }
        if status == 429 || code == "1306" { return .rateLimit }
        if code == "1222" { return .offline }
        if status == 403 { return .forbidden }
        return .network
    }
}
// Ephemeral sessions and redirect rejection prevent credential persistence/forwarding.
final class NoRedirect: NSObject, URLSessionTaskDelegate {
    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse,
                    newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) { completionHandler(nil) }
}
struct ThinQClient {
    let config: ThinQConfig
    static func unwrap(_ data: Data, status: Int) throws -> Any {
        guard data.count <= 1_048_576,
              let envelope = try JSONSerialization.jsonObject(with: data) as? [String: Any] else { throw ThinQError.invalidResponse }
        let error = envelope["error"] as? [String: Any]
        if !(200..<300).contains(status) || error != nil {
            throw ThinQError.api(status: status, code: error?["code"] as? String ?? "")
        }
        guard let response = envelope["response"], !(response is NSNull) else { throw ThinQError.invalidResponse }
        return response
    }
    func get(deviceID: String? = nil) async throws -> Any { try await get(config.request(deviceID: deviceID)) }
    /// Electricity use for the window, or nil when this model or token does not provide it (thrown as notAllowed/unsupported).
    func energy(id: String, window: EnergyWindow, now: Date = Date()) async throws -> EnergyUsage {
        func daily(_ range: (String, String)) async throws -> Int? {
            EnergyText.sum(try await get(config.request(path: ["devices", "energy", id, "usage"], query: [
                URLQueryItem(name: "property", value: "energyUsage"), URLQueryItem(name: "period", value: "DAILY"),
                URLQueryItem(name: "startDate", value: range.0), URLQueryItem(name: "endDate", value: range.1)])))
        }
        guard let current = try await daily(window.current) else { throw ThinQError.invalidResponse }
        let previous = try? await daily(window.previous)
        return EnergyUsage(current: current, previous: previous ?? nil, label: window.label, compareLabel: window.compareLabel, fetchedAt: now)
    }
    private func get(_ request: URLRequest) async throws -> Any {
        let delegate = NoRedirect()
        let settings = URLSessionConfiguration.ephemeral
        settings.httpShouldSetCookies = false
        let session = URLSession(configuration: settings, delegate: delegate, delegateQueue: nil)
        defer { session.invalidateAndCancel() }
        do {
            let (data, response) = try await session.data(for: request)
            guard let response = response as? HTTPURLResponse else { throw ThinQError.invalidResponse }
            return try Self.unwrap(data, status: response.statusCode)
        } catch let error as ThinQError { throw error }
        catch { throw ThinQError.network }
    }
    func devices() async throws -> [ThinQDevice] { try ThinQDevice.parse(await get()) }
    func status(id: String, type: String) async throws -> HomeStatus { HomeStatus.parse(try await get(deviceID: id), type: type, now: Date()) }
}
