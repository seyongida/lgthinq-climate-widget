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
        guard !token.isEmpty, !token.contains("\r"), !token.contains("\n"),
              country.range(of: "^[A-Z]{2}$", options: .regularExpression) != nil,
              ["kic", "aic", "eic"].contains(region),
              clientID.range(of: "^[a-zA-Z0-9_-]{1,100}$", options: .regularExpression) != nil else { throw ThinQError.settings }
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
struct ThinQDevice: Identifiable, Equatable {
    let id: String
    let name: String
    static func parse(_ response: Any) throws -> [ThinQDevice] {
        guard let list = response as? [Any] else { throw ThinQError.invalidResponse }
        return list.compactMap { item in
            guard let item = item as? [String: Any], let info = item["deviceInfo"] as? [String: Any],
                  info["deviceType"] as? String == "DEVICE_HUMIDIFIER",
                  let id = item["deviceId"] as? String, !id.isEmpty else { return nil }
            let alias = info["alias"] as? String ?? ""
            return ThinQDevice(id: id, name: alias.isEmpty ? "가습기" : alias)
        }
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
