import ActivityKit
import SwiftUI
import WidgetKit

private let ink = Color(RGB(18, 33, 30))
private let muted = Color(RGB(98, 113, 107))
private let accent = Color(RGB(24, 110, 90))
private let paper = Color(RGB(250, 248, 242))

struct CycleEntry: TimelineEntry {
    let date: Date
    let cycles: [ActiveCycle]
    let alerts: [HomeAlert]
}
struct CycleProvider: TimelineProvider {
    func placeholder(in context: Context) -> CycleEntry { sample }
    func getSnapshot(in context: Context, completion: @escaping (CycleEntry) -> Void) {
        let entry = current(Date())
        completion(context.isPreview && entry.cycles.isEmpty && entry.alerts.isEmpty ? sample : entry)
    }
    func getTimeline(in context: Context, completion: @escaping (Timeline<CycleEntry>) -> Void) {
        Task {
            await Refresher.refresh()
            let now = Date()
            let entry = current(now)
            let ends = entry.cycles.compactMap(\.endsAt).filter { $0 > now }.sorted()
            // Re-render at each expected finish, and check back soon after the first one ends.
            var entries = [entry]
            for end in ends.prefix(4) { entries.append(CycleEntry(date: end, cycles: entry.cycles, alerts: entry.alerts)) }
            let next = min(now.addingTimeInterval(30 * 60), (ends.first ?? .distantFuture).addingTimeInterval(120))
            completion(Timeline(entries: entries, policy: .after(next)))
        }
    }
    private func current(_ date: Date) -> CycleEntry {
        let store = ClimateStore()
        let devices = store.devices(), states = store.states()
        return CycleEntry(date: date, cycles: HomeSummary.activeCycles(devices, states), alerts: HomeSummary.alerts(devices, states))
    }
    private var sample: CycleEntry {
        let washer = ThinQDevice(id: "preview", name: "세탁기", type: "DEVICE_WASHER")
        let status = HomeStatus.parse([["runState": ["currentState": "RINSING"], "timer": ["remainHour": 0, "remainMinute": 23, "totalHour": 0, "totalMinute": 58]]],
                                      type: washer.type, now: Date())
        return CycleEntry(date: Date(), cycles: [ActiveCycle(device: washer, status: status, cycle: status.cycles[0])], alerts: [])
    }
}

struct CycleWidgetView: View {
    let entry: CycleEntry
    @Environment(\.widgetFamily) private var family
    var body: some View {
        content.widgetURL(URL(string: "thinqclimate://open"))
    }
    @ViewBuilder private var content: some View {
        switch family {
        case .accessoryCircular:
            if let item = entry.cycles.first, let start = item.startedAt, let end = item.endsAt {
                ProgressView(timerInterval: start...end, countsDown: false) { Image(systemName: HomeIcon.symbol(item.device.type)) } currentValueLabel: {
                    Image(systemName: HomeIcon.symbol(item.device.type)).font(.system(size: 16, weight: .semibold))
                }
                .progressViewStyle(.circular)
                .containerBackground(for: .widget) { Color.clear }
            } else {
                Image(systemName: entry.cycles.first.map { HomeIcon.symbol($0.device.type) } ?? "checkmark.circle")
                    .font(.system(size: 22, weight: .semibold))
                    .containerBackground(for: .widget) { Color.clear }
            }
        case .accessoryRectangular:
            VStack(alignment: .leading, spacing: 0) {
                if let item = entry.cycles.first {
                    Text(item.title).font(.system(size: 13, weight: .semibold)).lineLimit(1)
                    Countdown(cycle: item.cycle, endsAt: item.endsAt).font(.system(size: 24, weight: .bold, design: .rounded)).lineLimit(1)
                    Text(item.cycle.label).font(.system(size: 12)).lineLimit(1)
                } else if let alert = entry.alerts.first {
                    Text(alert.device.name).font(.system(size: 13, weight: .semibold)).lineLimit(1)
                    Text(alert.text).font(.system(size: 15, weight: .bold)).lineLimit(2)
                } else {
                    Text("가전").font(.system(size: 13, weight: .semibold))
                    Text("모두 쉬고 있어요").font(.system(size: 15, weight: .bold))
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .containerBackground(for: .widget) { Color.clear }
        case .systemSmall:
            small.padding(14).containerBackground(paper, for: .widget)
        default:
            medium.padding(14).containerBackground(paper, for: .widget)
        }
    }
    @ViewBuilder private var small: some View {
        if let item = entry.cycles.first {
            VStack(alignment: .leading, spacing: 0) {
                header(item.device, title: item.title)
                Spacer(minLength: 0)
                Text(item.cycle.label).font(.system(size: 13, weight: .semibold)).foregroundStyle(accent).lineLimit(1)
                Countdown(cycle: item.cycle, endsAt: item.endsAt)
                    .font(.system(size: 34, weight: .bold, design: .rounded)).foregroundStyle(ink).lineLimit(1).minimumScaleFactor(0.5)
                CycleProgress(startedAt: item.startedAt, endsAt: item.endsAt, tint: accent).padding(.vertical, 6)
                Text(CycleText.detail(item)).font(.system(size: 12, weight: .medium)).foregroundStyle(muted).lineLimit(1)
            }
        } else {
            idle(limit: 2)
        }
    }
    @ViewBuilder private var medium: some View {
        if entry.cycles.isEmpty {
            idle(limit: 3)
        } else {
            VStack(alignment: .leading, spacing: 8) {
                ForEach(entry.cycles.prefix(3)) { item in
                    HStack(spacing: 10) {
                        icon(item.device.type)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(item.title).font(.system(size: 14, weight: .semibold)).foregroundStyle(ink).lineLimit(1)
                            CycleProgress(startedAt: item.startedAt, endsAt: item.endsAt, tint: accent)
                            Text("\(item.cycle.label) · \(CycleText.detail(item))").font(.system(size: 11, weight: .medium)).foregroundStyle(muted).lineLimit(1)
                        }
                        Countdown(cycle: item.cycle, endsAt: item.endsAt)
                            .font(.system(size: 22, weight: .bold, design: .rounded)).foregroundStyle(ink)
                            .lineLimit(1).minimumScaleFactor(0.6).frame(width: 96, alignment: .trailing)
                    }
                }
                if entry.cycles.count < 3, let alert = entry.alerts.first { alertRow(alert) }
                Spacer(minLength: 0)
            }
        }
    }
    private func idle(limit: Int) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            if entry.alerts.isEmpty {
                Image(systemName: "checkmark.circle.fill").font(.system(size: 26)).foregroundStyle(accent)
                Spacer(minLength: 0)
                Text("진행 중인 가전이 없어요").font(.system(size: 16, weight: .bold)).foregroundStyle(ink)
                Text("세탁·건조·식기세척이 시작되면 여기서 남은 시간을 보여 드려요").font(.system(size: 11)).foregroundStyle(muted).lineLimit(3)
            } else {
                Text("확인해 주세요").font(.system(size: 13, weight: .bold)).foregroundStyle(Color(RGB(158, 88, 45)))
                ForEach(entry.alerts.prefix(limit)) { alertRow($0) }
                Spacer(minLength: 0)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }
    private func alertRow(_ alert: HomeAlert) -> some View {
        HStack(spacing: 10) {
            icon(alert.device.type)
            VStack(alignment: .leading, spacing: 1) {
                Text(alert.device.name).font(.system(size: 13, weight: .semibold)).foregroundStyle(ink).lineLimit(1)
                Text(alert.text).font(.system(size: 12)).foregroundStyle(muted).lineLimit(1)
            }
        }
    }
    private func header(_ device: ThinQDevice, title: String) -> some View {
        HStack(spacing: 6) {
            Image(systemName: HomeIcon.symbol(device.type)).font(.system(size: 13, weight: .bold)).foregroundStyle(accent)
            Text(title).font(.system(size: 14, weight: .semibold)).foregroundStyle(ink).lineLimit(1)
        }
    }
    private func icon(_ type: String) -> some View {
        Image(systemName: HomeIcon.symbol(type)).font(.system(size: 15, weight: .semibold)).foregroundStyle(accent)
            .frame(width: 34, height: 34).background(Color(RGB(226, 240, 233)), in: Circle())
    }
}
struct CycleWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "ThinQCycle", provider: CycleProvider()) { CycleWidgetView(entry: $0) }
            .contentMarginsDisabled()
            .configurationDisplayName("가전 타이머")
            .description("세탁기·건조기·식기세척기 같은 가전이 언제 끝나는지 보여 줍니다.")
            .supportedFamilies([.systemSmall, .systemMedium, .accessoryRectangular, .accessoryCircular])
    }
}

struct CycleLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: CycleActivityAttributes.self) { context in
            HStack(spacing: 14) {
                Image(systemName: context.attributes.symbol).font(.system(size: 20, weight: .semibold)).foregroundStyle(.white)
                    .frame(width: 46, height: 46).background(accent, in: Circle())
                VStack(alignment: .leading, spacing: 4) {
                    Text(context.attributes.title).font(.system(size: 15, weight: .semibold)).foregroundStyle(ink)
                    Text(context.state.label).font(.system(size: 13, weight: .medium)).foregroundStyle(accent)
                    CycleProgress(startedAt: context.state.startedAt, endsAt: context.state.endsAt, tint: accent)
                }
                activityCountdown(context.state).font(.system(size: 30, weight: .bold, design: .rounded)).foregroundStyle(ink)
                    .frame(minWidth: 86, alignment: .trailing)
            }
            .padding(16)
            .activityBackgroundTint(paper)
            .activitySystemActionForegroundColor(ink)
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Label(context.attributes.title, systemImage: context.attributes.symbol).font(.system(size: 14, weight: .semibold)).lineLimit(1)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    activityCountdown(context.state).font(.system(size: 20, weight: .bold, design: .rounded)).frame(maxWidth: 90, alignment: .trailing)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(context.state.label).font(.system(size: 13, weight: .medium)).foregroundStyle(.secondary)
                        CycleProgress(startedAt: context.state.startedAt, endsAt: context.state.endsAt, tint: .mint, track: .white.opacity(0.2))
                    }
                }
            } compactLeading: {
                Image(systemName: context.attributes.symbol).foregroundStyle(.mint)
            } compactTrailing: {
                activityCountdown(context.state).monospacedDigit().frame(maxWidth: 52).foregroundStyle(.mint)
            } minimal: {
                Image(systemName: context.attributes.symbol).foregroundStyle(.mint)
            }
            .widgetURL(URL(string: "thinqclimate://open"))
        }
    }
    @ViewBuilder private func activityCountdown(_ state: CycleActivityAttributes.ContentState) -> some View {
        if state.done {
            Image(systemName: "checkmark.circle.fill")
        } else if let end = state.endsAt, end > Date() {
            Text(timerInterval: Date()...end, countsDown: true).monospacedDigit()
        } else if let remaining = state.remaining {
            Text(HomeFormat.duration(remaining))
        } else {
            Text("곧 끝나요")
        }
    }
}

@main struct ClimateWidgetBundle: WidgetBundle {
    var body: some Widget {
        ClimateWidget()
        CycleWidget()
        CycleLiveActivity()
    }
}
