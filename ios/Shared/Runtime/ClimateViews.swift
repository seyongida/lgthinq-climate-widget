import AppIntents
import SwiftUI
import WidgetKit

extension Color {
    init(_ rgb: RGB, opacity: Double = 1) {
        self.init(.sRGB, red: Double(rgb.r) / 255, green: Double(rgb.g) / 255, blue: Double(rgb.b) / 255, opacity: opacity)
    }
}
extension WidgetStyle {
    var surface: Color { Color(background, opacity: Double(opacity) / 100) }
}
struct RefreshIntent: AppIntent {
    static var title: LocalizedStringResource = "센서값 새로고침"
    static var isDiscoverable = false
    func perform() async throws -> some IntentResult {
        await Refresher.refresh()
        return .result()
    }
}
enum ClimateFormat {
    static func time(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.dateFormat = "HH:mm"
        return formatter.string(from: date)
    }
    // Same wording as the Android widget. Sample data is always labelled.
    static func status(device: ThinQDevice?, state: DeviceState?, now: Date) -> String {
        guard let device else { return "앱에서 기기 선택" }
        if let error = state?.error, !error.isEmpty { return "연결 오류 ↻" }
        guard let reading = state?.reading else { return "수신 대기 ↻" }
        if reading.stale(at: now) { return "오래됨 ↻" }
        return (DemoData.isDemo(device.id) ? "샘플 " : "") + time(reading.fetchedAt) + " ↻"
    }
    static func symbol(_ device: ThinQDevice) -> String {
        switch device.type {
        case "DEVICE_AIR_PURIFIER": return "wind"
        case "DEVICE_AIR_PURIFIER_FAN": return "fan.fill"
        default: return "humidity.fill"
        }
    }
    static func needsAttention(state: DeviceState?, now: Date) -> Bool {
        if let error = state?.error, !error.isEmpty { return true }
        return state?.reading?.stale(at: now) ?? false
    }
}
// Humidity on a 0–100 track, with the comfortable range highlighted.
struct ComfortGauge: View {
    let humidity: Double?
    let track: Color
    let zone: Color
    let marker: Color
    var height: CGFloat = 8
    var body: some View {
        GeometryReader { geometry in
            let width = geometry.size.width
            ZStack(alignment: .leading) {
                Capsule().fill(track)
                Capsule().fill(zone)
                    .frame(width: width * (Comfort.range.upperBound - Comfort.range.lowerBound) / 100)
                    .offset(x: width * Comfort.range.lowerBound / 100)
                if let humidity {
                    Circle().fill(marker)
                        .overlay(Circle().stroke(track, lineWidth: 1))
                        .frame(width: height + 6, height: height + 6)
                        .offset(x: min(max(0, width * max(0, min(100, humidity)) / 100 - (height + 6) / 2), width - height - 6))
                }
            }
            .frame(height: height)
            .frame(maxHeight: .infinity)
        }
        .frame(height: height + 6)
        .accessibilityHidden(true)
    }
}
// One device in a home screen widget. Large digits first, so it reads at a glance over any wallpaper.
struct ClimateColumn: View {
    let device: ThinQDevice?
    let state: DeviceState?
    let style: WidgetStyle
    let now: Date
    var interactive = true

    var body: some View {
        let warn = device != nil && ClimateFormat.needsAttention(state: state, now: now)
        let humidity = state?.reading?.humidity
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 5) {
                if let device { Image(systemName: ClimateFormat.symbol(device)).font(.system(size: 12, weight: .bold)).foregroundStyle(Color(style.accent)) }
                Text(device.map { (warn ? "! " : "") + $0.name } ?? "Climate Widget")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Color(style.foreground))
                    .lineLimit(1)
            }
            Spacer(minLength: 0)
            Text(Reading.compact(state?.reading?.temperature, unit: "°"))
                .font(.system(size: 40, weight: .bold, design: .rounded))
                .foregroundStyle(Color(warn ? style.warning : style.foreground))
                .lineLimit(1).minimumScaleFactor(0.6).monospacedDigit()
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                Text(Reading.compact(humidity, unit: "%"))
                    .font(.system(size: 26, weight: .bold, design: .rounded))
                    .foregroundStyle(Color(warn ? style.warning : style.accent))
                    .lineLimit(1).minimumScaleFactor(0.6).monospacedDigit()
                if humidity != nil {
                    Text(Comfort(humidity: humidity).label).font(.system(size: 12, weight: .bold)).foregroundStyle(Color(style.muted))
                }
            }
            ComfortGauge(humidity: humidity, track: Color(style.foreground).opacity(0.14), zone: Color(style.accent).opacity(0.4),
                         marker: Color(style.accent), height: 5)
                .padding(.top, 3)
            Spacer(minLength: 5)
            statusView
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }
    @ViewBuilder private var statusView: some View {
        let label = Text(ClimateFormat.status(device: device, state: state, now: now))
            .font(.system(size: 12, weight: .semibold))
            .foregroundStyle(Color(style.muted))
            .lineLimit(1).minimumScaleFactor(0.8)
        if interactive && device != nil {
            Button(intent: RefreshIntent()) { label }.buttonStyle(.plain)
        } else {
            label
        }
    }
}
// Shared by the real widget and the in-app preview so the preview cannot drift from the widget.
struct ClimateWidgetBody: View {
    let devices: [ThinQDevice]
    let states: [String: DeviceState]
    let style: WidgetStyle
    let now: Date
    let columns: Int
    var interactive = true

    var body: some View {
        let shown = Array(devices.prefix(columns))
        HStack(spacing: 14) {
            if shown.isEmpty {
                ClimateColumn(device: nil, state: nil, style: style, now: now, interactive: interactive)
            }
            ForEach(Array(shown.enumerated()), id: \.element.id) { index, device in
                if index > 0 { Rectangle().fill(Color(style.foreground).opacity(0.12)).frame(width: 1) }
                ClimateColumn(device: device, state: states[device.id], style: style, now: now, interactive: interactive)
            }
        }
        .padding(14)
    }
}
