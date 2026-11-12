import Foundation

// Electricity use as reported by LG ThinQ, in Wh. Compares complete days only, because today is still running.
// Android's dev.thinqclimate.core.Energy applies the same rules; both are checked against fixtures/thinq/energy_expected.json.

struct EnergyWindow: Equatable {
    let current: (String, String)
    let previous: (String, String)
    let label: String
    let compareLabel: String
    static func == (a: EnergyWindow, b: EnergyWindow) -> Bool {
        a.current == b.current && a.previous == b.previous && a.label == b.label && a.compareLabel == b.compareLabel
    }
    /// This month up to yesterday against the same days last month. On the 1st, last month against the month before.
    static func make(today: Date, calendar: Calendar = .current) -> EnergyWindow {
        let day = calendar.component(.day, from: today)
        let firstOfMonth = calendar.date(from: calendar.dateComponents([.year, .month], from: today))!
        func shift(_ date: Date, months: Int) -> Date { calendar.date(byAdding: .month, value: months, to: date)! }
        func lastDay(of first: Date) -> Date { calendar.date(byAdding: DateComponents(month: 1, day: -1), to: first)! }
        func text(_ date: Date) -> String {
            let c = calendar.dateComponents([.year, .month, .day], from: date)
            return String(format: "%04d%02d%02d", c.year!, c.month!, c.day!)
        }
        if day == 1 {
            let last = shift(firstOfMonth, months: -1), before = shift(firstOfMonth, months: -2)
            return EnergyWindow(current: (text(last), text(lastDay(of: last))), previous: (text(before), text(lastDay(of: before))),
                                label: "지난달", compareLabel: "그 전 달")
        }
        let yesterday = calendar.date(byAdding: .day, value: -1, to: today)!
        let previousFirst = shift(firstOfMonth, months: -1)
        let previousLastDay = calendar.component(.day, from: lastDay(of: previousFirst))
        let previousEnd = calendar.date(byAdding: .day, value: min(day - 1, previousLastDay) - 1, to: previousFirst)!
        return EnergyWindow(current: (text(firstOfMonth), text(yesterday)), previous: (text(previousFirst), text(previousEnd)),
                            label: "이번 달 \(day - 1)일까지", compareLabel: "지난달 같은 기간")
    }
}

struct EnergyUsage: Codable, Equatable {
    var current: Int
    var previous: Int?
    var label: String
    var compareLabel: String
    var fetchedAt: Date
}

enum EnergyText {
    static func kWh(_ wh: Int) -> String {
        if wh == 0 { return "0 kWh" }
        if wh < 100 { return String(format: "%.2f kWh", Double(wh) / 1000) }
        return String(format: "%.1f kWh", Double(wh) / 1000)
    }
    /// Differences under 5% are called similar; small swings are noise in daily metering.
    static func comparison(current: Int, previous: Int?, compareLabel: String) -> String? {
        guard let previous, previous > 0 else { return nil }
        let change = Double(current - previous) / Double(previous) * 100
        if abs(change) < 5 { return "\(compareLabel)과 비슷해요" }
        let percent = Int(change.rounded())
        return "\(compareLabel)보다 \(abs(percent))% \(percent > 0 ? "많아요" : "적어요")"
    }
    static func sum(_ response: Any) -> Int? {
        guard let result = (response as? [String: Any])?["result"] as? [String: Any], let list = result["dataList"] as? [[String: Any]] else { return nil }
        return list.reduce(0) { $0 + Int(Reading.sensor($1["energyUsage"], range: 0...10_000_000) ?? 0) }
    }
}
