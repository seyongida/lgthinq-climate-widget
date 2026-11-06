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
    static func refresh() async -> Snapshot? {
        let store = ClimateStore()
        guard let lock = StoreLock() else { return store.snapshot() }
        defer { withExtendedLifetime(lock) {} }
        guard let before = store.snapshot() else { return nil }
        let now = Date()
        if before.selection.id == DemoData.deviceID {
            store.write(Snapshot(selection: before.selection, reading: DemoData.reading(at: now), error: nil, attemptedAt: now))
            return store.snapshot()
        }
        if let attempt = before.attemptedAt, now >= attempt, now.timeIntervalSince(attempt) < 60 { return before }
        let pending = Snapshot(selection: before.selection, reading: before.reading, error: before.error, attemptedAt: now)
        store.write(pending)
        do {
            guard let config = try store.config() else { throw ThinQError.settings }
            let reading = try await ThinQClient(config: config).reading(id: before.selection.id)
            store.write(Snapshot(selection: before.selection, reading: reading, error: nil, attemptedAt: now))
        } catch {
            store.write(Snapshot(selection: before.selection, reading: before.reading,
                                 error: (error as? ThinQError ?? .network).localizedDescription, attemptedAt: now))
        }
        return store.snapshot()
    }
}
