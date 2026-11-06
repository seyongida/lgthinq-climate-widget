import Foundation
import Darwin

// Advisory file lock serializes app and extension refreshes and configuration changes.
// Nonblocking acquisition avoids freezing the main actor; caller can retry on the next tick.
final class StoreLock {
    private let fd: Int32
    init?() {
        guard let directory = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: ClimateStore.group) else { return nil }
        fd = open(directory.appendingPathComponent("refresh.lock").path, O_CREAT | O_RDWR, S_IRUSR | S_IWUSR)
        guard fd >= 0 else { return nil }
        guard flock(fd, LOCK_EX | LOCK_NB) == 0 else { close(fd); return nil }
    }
    deinit { flock(fd, LOCK_UN); close(fd) }
}
enum Refresher {
    static func refresh() async {
        let store = ClimateStore()
        guard let lock = StoreLock() else { return }
        defer { withExtendedLifetime(lock) {} }
        let now = Date()
        var config: ThinQConfig?
        var configLoaded = false
        for (index, device) in store.devices().enumerated() {
            if DemoData.isDemo(device.id) {
                store.write(DeviceState(reading: DemoData.reading(at: now, offset: Double(index)), error: nil, attemptedAt: now), for: device.id)
                continue
            }
            let before = store.state(for: device.id)
            if let attempt = before.attemptedAt, now >= attempt, now.timeIntervalSince(attempt) < 60 { continue }
            store.write(DeviceState(reading: before.reading, error: before.error, attemptedAt: now), for: device.id)
            do {
                if !configLoaded { config = try store.config(); configLoaded = true }
                guard let config else { throw ThinQError.settings }
                let reading = try await ThinQClient(config: config).reading(id: device.id)
                store.write(DeviceState(reading: reading, error: nil, attemptedAt: now), for: device.id)
            } catch {
                store.write(DeviceState(reading: before.reading, error: (error as? ThinQError ?? .network).localizedDescription, attemptedAt: now), for: device.id)
            }
        }
    }
}
