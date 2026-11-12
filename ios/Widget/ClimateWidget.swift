import AppIntents
import SwiftUI
import WidgetKit

struct DeviceEntity: AppEntity {
    let id: String
    let name: String
    static var typeDisplayRepresentation: TypeDisplayRepresentation = "기기"
    static var defaultQuery = DeviceQuery()
    var displayRepresentation: DisplayRepresentation { DisplayRepresentation(title: "\(name)") }
}
struct DeviceQuery: EntityQuery {
    func entities(for identifiers: [String]) async throws -> [DeviceEntity] {
        all().filter { identifiers.contains($0.id) }
    }
    func suggestedEntities() async throws -> [DeviceEntity] { all() }
    private func all() -> [DeviceEntity] {
        let store = ClimateStore(), states = store.states()
        return store.devices().filter { $0.isClimate(states[$0.id]) }.map { DeviceEntity(id: $0.id, name: $0.name) }
    }
}
enum PaletteOption: Int, AppEnum {
    case ivory, forest, slate, ocean, lavender, peach
    static var typeDisplayRepresentation: TypeDisplayRepresentation = "색상"
    static var caseDisplayRepresentations: [PaletteOption: DisplayRepresentation] = [
        .ivory: "아이보리", .forest: "포레스트", .slate: "슬레이트", .ocean: "오션", .lavender: "라벤더", .peach: "피치"]
}
struct ClimateWidgetIntent: WidgetConfigurationIntent {
    static var title: LocalizedStringResource = "위젯 꾸미기"
    static var description = IntentDescription("기기와 색상을 고르세요. 작은 크기는 첫 번째 기기만 표시합니다.")
    @Parameter(title: "첫 번째 기기") var first: DeviceEntity?
    @Parameter(title: "두 번째 기기 · 중간 크기") var second: DeviceEntity?
    @Parameter(title: "색상", default: .ivory) var palette: PaletteOption
    @Parameter(title: "배경 불투명도", default: 100, controlStyle: .slider, inclusiveRange: (40, 100)) var opacity: Double
    @Parameter(title: "직접 배경 색상 (#RRGGBB)") var customBackground: String?
    @Parameter(title: "직접 습도 색상 (#RRGGBB)") var customAccent: String?
}
struct ClimateEntry: TimelineEntry {
    let date: Date
    let devices: [ThinQDevice]
    let states: [String: DeviceState]
    let style: WidgetStyle
}
struct ClimateProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> ClimateEntry { sample }
    func snapshot(for configuration: ClimateWidgetIntent, in context: Context) async -> ClimateEntry {
        let entry = current(configuration, at: Date())
        return context.isPreview && entry.devices.isEmpty ? sample : entry
    }
    func timeline(for configuration: ClimateWidgetIntent, in context: Context) async -> Timeline<ClimateEntry> {
        await Refresher.refresh()
        let now = Date()
        let entry = current(configuration, at: now)
        var entries = [entry]
        // Pre-render the stale transition even if the next network tick is delayed.
        let staleAt = entry.states.values.compactMap { $0.reading?.fetchedAt.addingTimeInterval(90 * 60) }.filter { $0 > now }.min()
        if let staleAt { entries.append(ClimateEntry(date: staleAt, devices: entry.devices, states: entry.states, style: entry.style)) }
        return Timeline(entries: entries, policy: .after(now.addingTimeInterval(30 * 60)))
    }
    private func current(_ configuration: ClimateWidgetIntent, at date: Date) -> ClimateEntry {
        let store = ClimateStore()
        let states = store.states()
        let selected = store.devices().filter { $0.isClimate(states[$0.id]) }
        // Chosen devices come first, then the rest of the app's list fills any empty slot.
        var devices: [ThinQDevice] = []
        for id in [configuration.first?.id, configuration.second?.id].compactMap({ $0 }) {
            if let device = selected.first(where: { $0.id == id }), !devices.contains(device) { devices.append(device) }
        }
        devices += selected.filter { !devices.contains($0) }
        let style = WidgetStyle.resolve(preset: configuration.palette.rawValue, customBackground: configuration.customBackground,
                                        customAccent: configuration.customAccent, opacity: Int(configuration.opacity.rounded()))
        return ClimateEntry(date: date, devices: devices, states: states, style: style)
    }
    private var sample: ClimateEntry {
        let room = ThinQDevice(id: "preview", name: "거실")
        return ClimateEntry(date: Date(), devices: [room],
                            states: [room.id: DeviceState(reading: Reading(temperature: 24, humidity: 47, fetchedAt: Date()))],
                            style: WidgetStyle.presets[0])
    }
}
struct ClimateWidgetView: View {
    let entry: ClimateEntry
    @Environment(\.widgetFamily) private var family
    var body: some View {
        content
            .widgetURL(URL(string: "thinqclimate://open"))
    }
    @ViewBuilder private var content: some View {
        switch family {
        case .accessoryCircular:
            lockScreen {
                VStack(spacing: 0) {
                    Text(Reading.compact(first?.reading?.temperature, unit: "°")).font(.system(size: 18, weight: .bold))
                    Text(Reading.compact(first?.reading?.humidity, unit: "%")).font(.system(size: 15, weight: .semibold))
                }.minimumScaleFactor(0.6).lineLimit(1)
            }
        case .accessoryRectangular:
            lockScreen {
                VStack(alignment: .leading, spacing: 0) {
                    Text(entry.devices.first?.name ?? "Climate Widget").font(.system(size: 13, weight: .semibold)).lineLimit(1)
                    Text("\(Reading.compact(first?.reading?.temperature, unit: "°"))  \(Reading.compact(first?.reading?.humidity, unit: "%"))")
                        .font(.system(size: 24, weight: .bold)).minimumScaleFactor(0.7).lineLimit(1)
                    Text(ClimateFormat.status(device: entry.devices.first, state: first, now: entry.date)).font(.system(size: 11)).lineLimit(1)
                }.frame(maxWidth: .infinity, alignment: .leading)
            }
        default:
            ClimateWidgetBody(devices: entry.devices, states: entry.states, style: entry.style, now: entry.date,
                              columns: family == .systemSmall ? 1 : 2)
                .containerBackground(entry.style.surface, for: .widget)
        }
    }
    private var first: DeviceState? { entry.devices.first.flatMap { entry.states[$0.id] } }
    private func lockScreen<V: View>(@ViewBuilder _ view: () -> V) -> some View {
        view().containerBackground(for: .widget) { Color.clear }
    }
}
struct ClimateWidget: Widget {
    let kind = "ThinQClimate"
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: kind, intent: ClimateWidgetIntent.self, provider: ClimateProvider()) { ClimateWidgetView(entry: $0) }
            .contentMarginsDisabled()
            .configurationDisplayName("Climate Widget")
            .description("기기의 온도·습도와 마지막 수신 시각. 길게 눌러 기기와 색상을 바꿀 수 있습니다.")
            .supportedFamilies([.systemSmall, .systemMedium, .accessoryCircular, .accessoryRectangular])
    }
}
