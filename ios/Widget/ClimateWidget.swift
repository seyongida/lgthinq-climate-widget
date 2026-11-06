import SwiftUI
import WidgetKit

struct ClimateEntry: TimelineEntry { let date: Date; let snapshot: Snapshot?; var preview = false }
struct ClimateProvider: TimelineProvider {
    func placeholder(in context: Context) -> ClimateEntry { sample }
    func getSnapshot(in context: Context, completion: @escaping (ClimateEntry) -> Void) {
        completion(context.isPreview ? sample : ClimateEntry(date: Date(), snapshot: ClimateStore().snapshot()))
    }
    func getTimeline(in context: Context, completion: @escaping (Timeline<ClimateEntry>) -> Void) {
        Task {
            let snapshot = await Refresher.refresh()
            let now = Date()
            let next = now.addingTimeInterval(30*60)
            var entries = [ClimateEntry(date: now, snapshot: snapshot)]
            // Pre-render the stale transition even if the next network tick is delayed.
            if let r = snapshot?.reading {
                let staleAt = r.fetchedAt.addingTimeInterval(90*60)
                if staleAt > now { entries.append(ClimateEntry(date: staleAt, snapshot: snapshot)) }
            }
            completion(Timeline(entries: entries, policy: .after(next)))
        }
    }
    private var sample: ClimateEntry {
        ClimateEntry(date: Date(), snapshot: Snapshot(selection: Selection(id: "preview", name: "거실 가습기", generation: UUID()),
            reading: Reading(temperature: 23.4, humidity: 47, fetchedAt: Date()), error: nil, attemptedAt: nil), preview: true)
    }
}
struct ClimateWidgetView: View {
    let entry: ClimateEntry
    @Environment(\.widgetFamily) private var family
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack { Image(systemName: "humidity.fill").foregroundStyle(.mint); Text(entry.snapshot?.selection.name ?? "ThinQ Climate").font(.caption).lineLimit(1); Spacer() }
            if let snapshot = entry.snapshot {
                HStack(alignment: .firstTextBaseline) {
                    metric("온도", Reading.text(snapshot.reading?.temperature, unit: "°C"), color: .white)
                    if family != .systemSmall { Spacer() }
                    metric("습도", Reading.text(snapshot.reading?.humidity, unit: "%"), color: .mint)
                }
                if entry.preview { Text("미리보기").font(.caption2) }
                if let error = snapshot.error { Text(error).font(.caption2).foregroundStyle(.orange).lineLimit(2) }
                if let r = snapshot.reading {
                    HStack(spacing: 3) {
                        Text(r.stale(at: entry.date) ? "오래된 데이터 ·" : "수신 ·")
                        Text(r.fetchedAt, style: .time)
                    }.font(.caption2).foregroundStyle(r.stale(at: entry.date) ? Color.orange : Color.gray)
                } else { Text("센서 정보 대기 중").font(.caption2) }
            } else { Text("앱에서 가습기를\n연결해 주세요").font(.headline) }
        }
        .containerBackground(Color(red: 0.06, green: 0.16, blue: 0.20), for: .widget)
        .widgetURL(URL(string: "thinqclimate://open"))
    }
    private func metric(_ title: String, _ value: String, color: Color) -> some View {
        VStack(alignment: .leading, spacing: 2) { Text(title).font(.caption2).foregroundStyle(.secondary); Text(value).font(family == .systemSmall ? .title3 : .largeTitle).bold().monospacedDigit().minimumScaleFactor(0.6).foregroundStyle(color).lineLimit(1) }
        .accessibilityElement(children: .combine)
    }
}
@main struct ClimateWidget: Widget {
    let kind = "ThinQClimate"
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: ClimateProvider()) { ClimateWidgetView(entry: $0) }
            .configurationDisplayName("ThinQ Climate").description("가습기 온도·습도와 마지막 수신 시각")
            .supportedFamilies([.systemSmall, .systemMedium])
    }
}
