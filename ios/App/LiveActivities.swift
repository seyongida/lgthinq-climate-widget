import ActivityKit
import Foundation

/// Mirrors running appliance programs onto the Lock Screen. There is no push server, so the countdown is
/// computed on the device from the finish time and the activity is corrected whenever the app refreshes.
@MainActor enum LiveActivities {
    /// `finished` holds keys of programs that have actually completed. Anything else that disappears
    /// (demo ended, disconnected, paused off, device hidden) is removed at once instead of claiming it finished.
    static func sync(_ cycles: [ActiveCycle], finished: Set<String> = [], enabled: Bool) async {
        guard ActivityAuthorizationInfo().areActivitiesEnabled else { return }
        var wanted: [String: ActiveCycle] = [:]
        if enabled { for item in cycles where item.cycle.phase != .reserved { wanted[item.id] = item } }
        for activity in Activity<CycleActivityAttributes>.activities {
            if let item = wanted.removeValue(forKey: activity.attributes.key) {
                await activity.update(content(item))
            } else if enabled && finished.contains(activity.attributes.key) {
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
    private static func content(_ item: ActiveCycle) -> ActivityContent<CycleActivityAttributes.ContentState> {
        let state = CycleActivityAttributes.ContentState(label: item.cycle.label, endsAt: item.endsAt, startedAt: item.startedAt,
                                                         remaining: item.cycle.remaining, done: false)
        // Past the expected finish the system greys the activity until the next refresh confirms it.
        return ActivityContent(state: state, staleDate: item.endsAt?.addingTimeInterval(5 * 60))
    }
}
