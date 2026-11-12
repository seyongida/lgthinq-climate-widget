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
        // Names can change in the ThinQ app, so refresh the list before reading states.
        if store.devices().contains(where: { !DemoData.isDemo($0.id) }), let current = try? store.config(),
           let found = try? await ThinQClient(config: current).devices() {
            config = current; configLoaded = true
            store.setCatalog(found)
            let merged = ThinQDevice.merge(selected: store.devices(), found: found)
            if merged != store.devices() { store.selectDevices(merged) }
        }
        for device in store.devices() {
            if DemoData.isDemo(device.id) {
                store.write(accepted(HomeStatus.parse(DemoData.state(for: device, at: now), type: device.type, now: now), at: now), for: device.id)
                continue
            }
            let before = store.state(for: device.id)
            if let attempt = before.attemptedAt, now >= attempt, now.timeIntervalSince(attempt) < 60 { continue }
            var pending = before
            pending.attemptedAt = now
            store.write(pending, for: device.id)
            do {
                if !configLoaded { config = try store.config(); configLoaded = true }
                guard let config else { throw ThinQError.settings }
                let status = try await ThinQClient(config: config).status(id: device.id, type: device.type)
                store.write(accepted(status, at: now), for: device.id)
            } catch {
                pending.error = (error as? ThinQError ?? .network).localizedDescription
                store.write(pending, for: device.id)
            }
        }
    }
    /// Electricity use changes once a day, so each device is asked at most every 6 hours, and only from the app.
    static func refreshEnergy() async {
        let store = ClimateStore()
        let now = Date()
        let window = EnergyWindow.make(today: now)
        var energy = store.energy(), skip = store.energySkip()
        let devices = store.devices()
        energy = energy.filter { id, usage in devices.contains { $0.id == id } && usage.label == window.label || devices.contains { $0.id == id } && now.timeIntervalSince(usage.fetchedAt) < 6 * 3600 }
        var config: ThinQConfig?
        for device in devices {
            if DemoData.isDemo(device.id) {
                if let sample = DemoData.energy(for: device, window: window, now: now) { energy[device.id] = sample }
                continue
            }
            if let fetched = energy[device.id]?.fetchedAt, now.timeIntervalSince(fetched) < 6 * 3600, energy[device.id]?.label == window.label { continue }
            if let until = skip[device.id], until > now { continue }
            do {
                if config == nil { config = try store.config() }
                guard let config else { break }
                energy[device.id] = try await ThinQClient(config: config).energy(id: device.id, window: window, now: now)
            } catch ThinQError.unsupported {
                skip[device.id] = now.addingTimeInterval(7 * 86400); energy[device.id] = nil
            } catch ThinQError.notAllowed {
                // The token has no energy permission: stop asking for every device for a day.
                for d in devices { skip[d.id] = now.addingTimeInterval(86400) }
                break
            } catch {
                continue
            }
        }
        store.setEnergy(energy)
        store.setEnergySkip(skip)
    }
    private static func accepted(_ status: HomeStatus, at now: Date) -> DeviceState {
        DeviceState(reading: status.hasClimate ? Reading(temperature: status.temperature, humidity: status.humidity, fetchedAt: status.fetchedAt) : nil,
                    error: nil, attemptedAt: now, status: status)
    }
}
