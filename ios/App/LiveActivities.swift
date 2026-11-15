import ActivityKit
import Foundation

/// Mirrors running appliance programs onto the Lock Screen. There is no push server, so the countdown is
/// computed on the device from the finish time and the activity is corrected whenever the app refreshes.
@MainActor enum LiveActivities {
    /// `finished` holds keys of programs that have actually completed; `idle` holds programs now seen off, which
    /// count as finished when the activity's expected end had come (many dishwashers switch off by themselves).
    /// Anything else that disappears (demo ended, disconnected, stopped early, device hidden) is removed at once
    /// instead of claiming it finished.
    static func sync(_ cycles: [ActiveCycle], finished: Set<String> = [], idle: [String: (HomeStatus, CycleStatus)] = [:], enabled: Bool) async {
        guard ActivityAuthorizationInfo().areActivitiesEnabled else { return }
        var wanted: [String: ActiveCycle] = [:]
        if enabled { for item in cycles where item.cycle.phase != .reserved { wanted[item.id] = item } }
        for activity in Activity<CycleActivityAttributes>.activities {
            if let item = wanted.removeValue(forKey: activity.attributes.key) {
                await activity.update(content(item))
            } else if enabled && (finished.contains(activity.attributes.key) || quietlyFinished(activity, idle)) {
                var final = activity.content.state
                final.done = true; final.label = "끝났어요"; final.endsAt = nil; final.remaining = nil
                await activity.end(ActivityContent(state: final, staleDate: nil), dismissalPolicy: .after(Date().addingTimeInterval(15 * 60)))
            } else {
                await activity.end(nil, dismissalPolicy: .immediate)
            }
        }
        for item in wanted.values {
            let attributes = CycleActivityAttributes(key: item.id, title: item.title, symbol: HomeIcon.symbol(item.device.type))
            _ = try? Activity.request(attributes: attributes, content: content(item), pushType: nil)
        }
    }
    private static func quietlyFinished(_ activity: Activity<CycleActivityAttributes>, _ idle: [String: (HomeStatus, CycleStatus)]) -> Bool {
        guard let seen = idle[activity.attributes.key] else { return false }
        return seen.0.finishedQuietly(seen.1, expectedEnd: activity.content.state.endsAt)
    }
    private static func content(_ item: ActiveCycle) -> ActivityContent<CycleActivityAttributes.ContentState> {
        let state = CycleActivityAttributes.ContentState(label: item.cycle.label, endsAt: item.endsAt, startedAt: item.startedAt,
                                                         remaining: item.cycle.remaining, done: false, progress: item.pausedProgress)
        // Past the expected finish the system greys the activity until the next refresh confirms it.
        return ActivityContent(state: state, staleDate: item.endsAt?.addingTimeInterval(5 * 60))
    }
}
