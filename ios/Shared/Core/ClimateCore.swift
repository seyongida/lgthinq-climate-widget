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
        var components = URLComponents(string: "https://api-\(region).lgthinq.com/devices")!
        if let id = deviceID {
            let allowed = CharacterSet(charactersIn: "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~")
            components.percentEncodedPath += "/" + id.addingPercentEncoding(withAllowedCharacters: allowed)! + "/state"
        }
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
    var typeName: String {
        switch type {
        case "DEVICE_AIR_PURIFIER": return "공기청정기"
        case "DEVICE_AIR_PURIFIER_FAN": return "공기청정 팬"
        default: return "가습기"
        }
    }
    static let supportedTypes: Set<String> = ["DEVICE_HUMIDIFIER", "DEVICE_AIR_PURIFIER", "DEVICE_AIR_PURIFIER_FAN"]
    static func parse(_ response: Any) throws -> [ThinQDevice] {
        guard let list = response as? [Any] else { throw ThinQError.invalidResponse }
        var result: [ThinQDevice] = []
        for item in list {
            guard let item = item as? [String: Any], let info = item["deviceInfo"] as? [String: Any],
                  let type = info["deviceType"] as? String, supportedTypes.contains(type),
                  let id = item["deviceId"] as? String, !id.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                  !result.contains(where: { $0.id == id }) else { continue }
            let alias = (info["alias"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            result.append(ThinQDevice(id: id, name: alias.isEmpty ? (type == "DEVICE_HUMIDIFIER" ? "가습기" : "공기청정기") : alias, type: type))
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
                          ThinQDevice(id: prefix + "-2", name: "체험용 공기청정기", type: "DEVICE_AIR_PURIFIER")]
    static func isDemo(_ id: String) -> Bool { id.hasPrefix(prefix) }
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
        self.background = background; self.accent = accent; self.opacity = max(40, min(100, opacity))
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
    case settings, invalidResponse, authentication, rateLimit, offline, forbidden, network
    var errorDescription: String? {
        switch self {
        case .settings: return "PAT, 국가 코드, 서버 지역을 확인해 주세요."
        case .invalidResponse: return "ThinQ 응답 형식이 올바르지 않습니다."
        case .authentication: return "인증 만료 · 앱에서 PAT를 다시 설정해 주세요."
        case .rateLimit: return "요청 한도 초과 · 잠시 후 다시 시도합니다."
        case .offline: return "가습기 오프라인 · 마지막 수신값입니다."
        case .forbidden: return "접근 거부 · PAT 읽기 권한과 국가/지역을 확인해 주세요."
        case .network: return "연결 실패 · 네트워크와 기기 상태를 확인해 주세요."
        }
    }
    static func api(status: Int, code: String) -> ThinQError {
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
    func get(deviceID: String? = nil) async throws -> Any {
        let delegate = NoRedirect()
        let settings = URLSessionConfiguration.ephemeral
        settings.httpShouldSetCookies = false
        let session = URLSession(configuration: settings, delegate: delegate, delegateQueue: nil)
        defer { session.invalidateAndCancel() }
        do {
            let (data, response) = try await session.data(for: config.request(deviceID: deviceID))
            guard let response = response as? HTTPURLResponse else { throw ThinQError.invalidResponse }
            return try Self.unwrap(data, status: response.statusCode)
        } catch let error as ThinQError { throw error }
        catch { throw ThinQError.network }
    }
    func devices() async throws -> [ThinQDevice] { try ThinQDevice.parse(await get()) }
    func reading(id: String) async throws -> Reading { try Reading.parse(await get(deviceID: id)) }
}
