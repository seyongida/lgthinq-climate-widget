import ActivityKit
import SwiftUI

enum HomeIcon {
    static func symbol(_ type: String) -> String {
        switch HomeCatalog.icon(type) {
        case "drop": return "humidity.fill"
        case "air": return "air.purifier.fill"
        case "snow": return "snowflake"
        case "washer": return "washer.fill"
        case "dryer": return "dryer.fill"
        case "hanger": return "hanger"
        case "dishes": return "dishwasher.fill"
        case "oven": return "oven.fill"
        case "fridge": return "refrigerator.fill"
        case "water": return "spigot.fill"
        case "vacuum": return "bolt.batteryblock.fill"
        default: return "powerplug.fill"
        }
    }
}

/// One appliance program in progress. A device with two drums can contribute two.
struct ActiveCycle: Identifiable, Equatable {
    let device: ThinQDevice
    let status: HomeStatus
    let cycle: CycleStatus
    var id: String { device.id + "|" + (cycle.location ?? "") }
    var title: String { device.name + (cycle.location.map { " " + $0 } ?? "") }
    var endsAt: Date? { status.endsAt(cycle) }
    var startedAt: Date? { status.startedAt(cycle) }
}
struct HomeAlert: Identifiable, Equatable {
    let device: ThinQDevice
    let text: String
    var id: String { device.id + text }
}

enum HomeSummary {
    /// Running programs first, soonest to finish first, then paused and scheduled ones.
    static func activeCycles(_ devices: [ThinQDevice], _ states: [String: DeviceState]) -> [ActiveCycle] {
        let all = devices.flatMap { device -> [ActiveCycle] in
            guard let status = states[device.id]?.status else { return [] }
            return status.cycles.filter(\.isActive).map { ActiveCycle(device: device, status: status, cycle: $0) }
        }
        let order: [CyclePhase] = [.running, .paused, .reserved]
        return all.sorted {
            let a = order.firstIndex(of: $0.cycle.phase)!, b = order.firstIndex(of: $1.cycle.phase)!
            if a != b { return a < b }
            return ($0.endsAt ?? .distantFuture) < ($1.endsAt ?? .distantFuture)
        }
    }
    static func alerts(_ devices: [ThinQDevice], _ states: [String: DeviceState]) -> [HomeAlert] {
        devices.flatMap { device in (states[device.id]?.status?.alerts ?? []).map { HomeAlert(device: device, text: $0) } }
    }
}

/// Counts down on its own once rendered, so widgets and Live Activities stay current without network refreshes.
struct Countdown: View {
    let cycle: CycleStatus
    let endsAt: Date?
    var body: some View {
        if let endsAt, endsAt > Date() {
            Text(timerInterval: Date()...endsAt, countsDown: true).monospacedDigit()
        } else if endsAt != nil {
            Text("곧 끝나요")
        } else if cycle.phase == .reserved {
            Text(cycle.startsIn.map { HomeFormat.duration($0) + " 후" } ?? "예약")
        } else {
            Text(cycle.remaining.map { HomeFormat.duration($0) } ?? cycle.label)
        }
    }
}
struct CycleProgress: View {
    let startedAt: Date?
    let endsAt: Date?
    let tint: Color
    var track: Color = Color.black.opacity(0.08)
    var body: some View {
        if let startedAt, let endsAt, startedAt < endsAt {
            ProgressView(timerInterval: startedAt...endsAt, countsDown: false) { EmptyView() } currentValueLabel: { EmptyView() }
                .progressViewStyle(.linear).tint(tint)
        } else {
            Capsule().fill(track).frame(height: 4)
        }
    }
}
enum CycleText {
    /// "오후 3:42 완료 예정" for running programs, otherwise the state label.
    static func detail(_ item: ActiveCycle) -> String {
        if let end = item.endsAt { return "\(ClimateFormat.time(end)) 완료 예정" }
        if item.cycle.phase == .reserved, let startsIn = item.cycle.startsIn {
            return "\(ClimateFormat.time(item.status.fetchedAt.addingTimeInterval(TimeInterval(startsIn)))) 시작 예정"
        }
        return item.cycle.label
    }
}

struct CycleActivityAttributes: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        var label: String
        var endsAt: Date?
        var startedAt: Date?
        var remaining: Int?
        var done: Bool
    }
    var key: String
    var title: String
    var symbol: String
}
