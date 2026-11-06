import Foundation
import Security

struct DeviceState: Codable, Equatable {
    var reading: Reading?
    var error: String?
    var attemptedAt: Date?
}
struct ClimateStore {
    // Both targets expand these from the same build settings.
    static let group = Bundle.main.object(forInfoDictionaryKey: "ClimateAppGroup") as! String
    static let keychainGroup = Bundle.main.object(forInfoDictionaryKey: "ClimateKeychainGroup") as! String
    private var defaults: UserDefaults { UserDefaults(suiteName: Self.group)! }
    private var query: [String: Any] { [kSecClass as String: kSecClassGenericPassword,
        kSecAttrService as String: "dev.thinqclimate.credentials", kSecAttrAccount as String: "pat",
        kSecAttrAccessGroup as String: Self.keychainGroup] }
    func config() throws -> ThinQConfig? {
        var q = query; q[kSecReturnData as String] = true
        var result: CFTypeRef?
        let status = SecItemCopyMatching(q as CFDictionary, &result)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess, let data = result as? Data else { throw ThinQError.settings }
        return try JSONDecoder().decode(ThinQConfig.self, from: data)
    }
    func save(_ config: ThinQConfig) throws {
        let data = try JSONEncoder().encode(config)
        let attributes: [String: Any] = [kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly]
        let status = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
        if status == errSecItemNotFound {
            var q = query; attributes.forEach { q[$0.key] = $0.value }
            guard SecItemAdd(q as CFDictionary, nil) == errSecSuccess else { throw ThinQError.settings }
        } else if status != errSecSuccess { throw ThinQError.settings }
    }
    // Call while holding StoreLock so an old refresh cannot restore the previous account cache.
    func replaceCredentials(_ config: ThinQConfig) throws {
        try save(config)
        clearCache()
    }
    func devices() -> [ThinQDevice] { load("devices") ?? [] }
    func selectDevices(_ devices: [ThinQDevice]) {
        store(devices, "devices")
        let ids = Set(devices.map(\.id))
        store(states().filter { ids.contains($0.key) }, "states")
    }
    func catalog() -> [ThinQDevice] { load("catalog") ?? [] }
    func setCatalog(_ devices: [ThinQDevice]) { store(devices, "catalog") }
    func states() -> [String: DeviceState] { load("states") ?? [:] }
    func state(for id: String) -> DeviceState { states()[id] ?? DeviceState() }
    func write(_ state: DeviceState, for id: String) {
        var all = states()
        all[id] = state
        store(all, "states")
    }
    func clear() throws {
        let status = SecItemDelete(query as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else { throw ThinQError.settings }
        clearCache()
    }
    private func clearCache() {
        // "snapshot" is the single-device format used by earlier builds.
        for key in ["devices", "catalog", "states", "snapshot"] { defaults.removeObject(forKey: key) }
    }
    private func load<T: Decodable>(_ key: String) -> T? {
        guard let data = defaults.data(forKey: key) else { return nil }
        return try? JSONDecoder().decode(T.self, from: data)
    }
    private func store<T: Encodable>(_ value: T, _ key: String) {
        guard let data = try? JSONEncoder().encode(value) else { return }
        defaults.set(data, forKey: key)
    }
}
