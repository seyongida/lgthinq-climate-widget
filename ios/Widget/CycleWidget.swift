import ActivityKit
import AppIntents
import SwiftUI
import WidgetKit

private let ink = Color(RGB(18, 33, 30))
private let muted = Color(RGB(98, 113, 107))
private let accent = Color(RGB(24, 110, 90))
private let paper = Color(RGB(250, 248, 242))

struct ApplianceEntity: AppEntity {
    let id: String
    let name: String
    static var typeDisplayRepresentation: TypeDisplayRepresentation = "가전"
    static var defaultQuery = ApplianceQuery()
    var displayRepresentation: DisplayRepresentation { DisplayRepresentation(title: "\(name)") }
}
struct ApplianceQuery: EntityQuery {
    func entities(for identifiers: [String]) async throws -> [ApplianceEntity] { all().filter { identifiers.contains($0.id) } }
    func suggestedEntities() async throws -> [ApplianceEntity] { all() }
    /// Devices that run programs with a finish time, such as washers, dryers and dishwashers.
    private func all() -> [ApplianceEntity] {
        let store = ClimateStore(), states = store.states()
        return store.devices().filter { HomeCatalog.doneNoun[$0.type] != nil || !(states[$0.id]?.status?.cycles.isEmpty ?? true) }
            .map { ApplianceEntity(id: $0.id, name: $0.name) }
    }
}
enum CycleMode: String, AppEnum {
    case auto, selected
    static var typeDisplayRepresentation: TypeDisplayRepresentation = "표시 방식"
    static var caseDisplayRepresentations: [CycleMode: DisplayRepresentation] = [.auto: "자동 · 작동 중인 가전 모두", .selected: "선택한 가전만"]
}
enum ShowCount: Int, AppEnum {
    case one = 1, two, three
    static var typeDisplayRepresentation: TypeDisplayRepresentation = "최대 표시 수"
    static var caseDisplayRepresentations: [ShowCount: DisplayRepresentation] = [.one: "1대", .two: "2대", .three: "3대"]
}
struct CycleWidgetIntent: WidgetConfigurationIntent {
    static var title: LocalizedStringResource = "가전 타이머 설정"
    static var description = IntentDescription("어떤 가전을 몇 대까지 보여 줄지 고르세요. 작은 크기는 한 대를 크게 보여 줍니다.")
    @Parameter(title: "표시 방식", default: .auto) var mode: CycleMode
    @Parameter(title: "가전 1 · 선택한 가전만") var first: ApplianceEntity?
    @Parameter(title: "가전 2 · 선택한 가전만") var second: ApplianceEntity?
    @Parameter(title: "가전 3 · 선택한 가전만") var third: ApplianceEntity?
    @Parameter(title: "최대 표시 수", default: .three) var count: ShowCount
    @Parameter(title: "쉬고 있을 때 보여 줄 가전") var pinned: ApplianceEntity?
    @Parameter(title: "확인할 일 표시", default: true) var showAlerts: Bool
    @Parameter(title: "색상", default: .ivory) var palette: PaletteOption
}

struct CycleEntry: TimelineEntry {
    let date: Date
    let cycles: [ActiveCycle]
    let alerts: [HomeAlert]
    var pinned: ThinQDevice? = nil
    var pinnedStatus: HomeStatus? = nil
    var limit = 3
    var style = WidgetStyle.presets[0]
}
struct CycleProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> CycleEntry { sample }
    func snapshot(for configuration: CycleWidgetIntent, in context: Context) async -> CycleEntry {
        let entry = current(configuration, at: Date())
        return context.isPreview && entry.cycles.isEmpty && entry.alerts.isEmpty && entry.pinned == nil ? sample : entry
    }
    func timeline(for configuration: CycleWidgetIntent, in context: Context) async -> Timeline<CycleEntry> {
        await Refresher.refresh()
        let now = Date()
        let entry = current(configuration, at: now)
        let ends = entry.cycles.compactMap(\.endsAt).filter { $0 > now }.sorted()
        // Re-render at each expected finish, and check back soon after the first one ends.
        var entries = [entry]
        for end in ends.prefix(4) {
            entries.append(CycleEntry(date: end, cycles: entry.cycles, alerts: entry.alerts, pinned: entry.pinned,
                                      pinnedStatus: entry.pinnedStatus, limit: entry.limit, style: entry.style))
        }
        let next = min(now.addingTimeInterval(30 * 60), (ends.first ?? .distantFuture).addingTimeInterval(120))
        return Timeline(entries: entries, policy: .after(next))
    }
    private func current(_ configuration: CycleWidgetIntent, at date: Date) -> CycleEntry {
        let store = ClimateStore()
        let devices = store.devices(), states = store.states()
        let chosen = [configuration.first, configuration.second, configuration.third].compactMap { $0?.id }
        var cycles = HomeSummary.activeCycles(devices, states)
        if configuration.mode == .selected { cycles = cycles.filter { chosen.contains($0.device.id) } }
        let shown = Set((configuration.mode == .selected ? chosen : devices.map(\.id)))
        let alerts = configuration.showAlerts ? HomeSummary.alerts(devices.filter { shown.contains($0.id) }, states) : []
        let pinned = configuration.pinned.flatMap { p in devices.first { $0.id == p.id } }
        return CycleEntry(date: date, cycles: cycles, alerts: alerts, pinned: pinned, pinnedStatus: pinned.flatMap { states[$0.id]?.status },
                          limit: configuration.count.rawValue,
                          style: WidgetStyle.resolve(preset: configuration.palette.rawValue, customBackground: nil, customAccent: nil, opacity: 100))
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
    // The chosen palette; text colors follow the background for legibility.
    private var ink: Color { Color(entry.style.foreground) }
    private var muted: Color { Color(entry.style.muted) }
    private var accent: Color { Color(entry.style.accent) }
    private var paper: Color { entry.style.surface }
    private var shown: [ActiveCycle] { Array(entry.cycles.prefix(entry.limit)) }
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
                HStack(spacing: 4) {
                    header(item.device, title: item.title)
                    Spacer(minLength: 0)
                    // A small widget has room for one program; say how many others are running.
                    if entry.cycles.count > 1 {
                        Text("외 \(entry.cycles.count - 1)대").font(.system(size: 11, weight: .bold)).foregroundStyle(accent)
                            .padding(.horizontal, 6).padding(.vertical, 2).background(accent.opacity(0.15), in: Capsule())
                            .fixedSize()
                    }
                }
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
                ForEach(shown.prefix(3)) { item in
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
                if entry.cycles.count > shown.prefix(3).count {
                    Text("외 \(entry.cycles.count - shown.prefix(3).count)대 작동 중").font(.system(size: 11, weight: .semibold)).foregroundStyle(muted)
                } else if shown.count < 3, let alert = entry.alerts.first { alertRow(alert) }
                Spacer(minLength: 0)
            }
        }
    }
    private func idle(limit: Int) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            if let device = entry.pinned {
                header(device, title: device.name)
                Spacer(minLength: 0)
                Text(entry.pinnedStatus?.summary ?? "상태를 기다리고 있어요").font(.system(size: 20, weight: .bold, design: .rounded)).foregroundStyle(ink)
                    .lineLimit(2).minimumScaleFactor(0.7)
                if let alert = entry.alerts.first { Text(alert.text).font(.system(size: 12, weight: .semibold)).foregroundStyle(Color(entry.style.warning)).lineLimit(1) }
            } else if entry.alerts.isEmpty {
                Image(systemName: "checkmark.circle.fill").font(.system(size: 26)).foregroundStyle(accent)
                Spacer(minLength: 0)
                Text("진행 중인 가전이 없어요").font(.system(size: 16, weight: .bold)).foregroundStyle(ink)
                Text("세탁·건조·식기세척이 시작되면 여기서 남은 시간을 보여 드려요").font(.system(size: 11)).foregroundStyle(muted).lineLimit(3)
            } else {
                Text("확인해 주세요").font(.system(size: 13, weight: .bold)).foregroundStyle(Color(entry.style.warning))
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
            .frame(width: 34, height: 34).background(accent.opacity(0.15), in: Circle())
    }
}
struct CycleWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "ThinQCycle", intent: CycleWidgetIntent.self, provider: CycleProvider()) { CycleWidgetView(entry: $0) }
            .contentMarginsDisabled()
            .configurationDisplayName("가전 타이머")
            .description("세탁기·건조기·식기세척기가 언제 끝나는지 보여 줍니다. 길게 눌러 가전과 표시 수를 고를 수 있어요.")
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
