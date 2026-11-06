import Foundation
import Security

struct Selection: Codable, Equatable { let id: String; let name: String; let generation: UUID }
struct Snapshot: Codable { let selection: Selection; let reading: Reading?; let error: String?; let attemptedAt: Date? }
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
    func snapshot() -> Snapshot? {
        guard let data = defaults.data(forKey: "snapshot") else { return nil }
        return try? JSONDecoder().decode(Snapshot.self, from: data)
    }
    func write(_ snapshot: Snapshot) {
        guard let data = try? JSONEncoder().encode(snapshot) else { return }
        defaults.set(data, forKey: "snapshot")
    }
    func select(_ device: ThinQDevice) {
        write(Snapshot(selection: Selection(id: device.id, name: device.name, generation: UUID()), reading: nil, error: nil, attemptedAt: nil))
    }
    func clear() throws {
        let status = SecItemDelete(query as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else { throw ThinQError.settings }
        defaults.removeObject(forKey: "snapshot")
    }
}
