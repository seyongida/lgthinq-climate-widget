import Foundation

// Interprets any ThinQ Connect device state into what a person needs to know at a glance.
// Android's dev.thinqclimate.core.Home applies the same rules; both are checked against fixtures/thinq.

enum CyclePhase: String, Codable { case idle, running, paused, reserved, done, error }

struct CycleStatus: Codable, Equatable {
    var location: String?
    var state: String
    var phase: CyclePhase
    var label: String
    var remaining: Int?
    var total: Int?
    var startsIn: Int?
    var isActive: Bool { phase == .running || phase == .paused || phase == .reserved }
}

struct HomeStatus: Codable, Equatable {
    var type: String
    var fetchedAt: Date
    var temperature: Double?
    var humidity: Double?
    var pm1: Double?
    var pm25: Double?
    var pm10: Double?
    var airLevel: String?
    var power: Bool?
    var targetHumidity: Double?
    var cycles: [CycleStatus] = []
    var openDoors: [String] = []
    var targets: [String] = []
    var activity: String?

    var hasClimate: Bool { temperature != nil || humidity != nil }
    /// The cycle that best describes the device right now.
    var primaryCycle: CycleStatus? {
        let order: [CyclePhase] = [.error, .running, .paused, .reserved, .done, .idle]
        return cycles.min { order.firstIndex(of: $0.phase)! < order.firstIndex(of: $1.phase)! }
    }
    func endsAt(_ cycle: CycleStatus) -> Date? {
        guard cycle.phase == .running, let remaining = cycle.remaining, remaining > 0 else { return nil }
        return fetchedAt.addingTimeInterval(TimeInterval(remaining))
    }
    func startedAt(_ cycle: CycleStatus) -> Date? {
        guard let end = endsAt(cycle), let total = cycle.total, total > (cycle.remaining ?? 0) else { return nil }
        return end.addingTimeInterval(-TimeInterval(total))
    }
    var airText: String? { airLevel.flatMap { HomeCatalog.airLevels[$0] }.map { "공기 " + $0 } }

    var summary: String {
        if let cycle = primaryCycle {
            switch cycle.phase {
            case .running, .paused:
                return cycle.remaining.map { "\(cycle.label) · \(HomeFormat.duration($0)) 남음" } ?? cycle.label
            case .reserved:
                return cycle.startsIn.map { "예약 · \(HomeFormat.duration($0)) 후 시작" } ?? "예약"
            case .done, .error, .idle:
                return cycle.label
            }
        }
        var parts: [String] = []
        if hasClimate {
            if let temperature { parts.append(Reading.compact(temperature, unit: "°")) }
            if let humidity { parts.append(Reading.compact(humidity, unit: "%")) }
            if let airText { parts.append(airText) }
        } else if !targets.isEmpty {
            parts += targets
        } else if let airText {
            parts.append(airText)
        }
        if !openDoors.isEmpty { parts.append("문 열림") }
        if parts.isEmpty, let activity { parts.append(activity) }
        return parts.isEmpty ? "연결됨" : parts.joined(separator: " · ")
    }

    /// Things worth interrupting someone for. Device names are added by the caller.
    var alerts: [String] {
        var result: [String] = []
        let noun = HomeCatalog.doneNoun[type] ?? "작동"
        for cycle in cycles {
            let prefix = cycle.location.map { $0 + " " } ?? ""
            if cycle.phase == .done {
                result.append(cycle.state == "PREHEATING_IS_DONE" ? "\(prefix)예열이 끝났어요" : "\(prefix)\(noun)\(HomeFormat.subject(noun)) 끝났어요")
            } else if cycle.phase == .error {
                result.append("\(prefix)\(HomeCatalog.name(type)) 오류가 발생했어요")
            }
        }
        if HomeCatalog.coldStorage.contains(type), !openDoors.isEmpty { result.append("\(HomeCatalog.name(type)) 문이 열려 있어요") }
        return result
    }

    static func parse(_ state: Any, type: String, now: Date) -> HomeStatus {
        var status = HomeStatus(type: type, fetchedAt: now)
        let parts: [[String: Any]] = (state as? [[String: Any]]) ?? ((state as? [String: Any]).map { [$0] } ?? [])
        let cycleParts = parts.filter { ($0["runState"] as? [String: Any])?["currentState"] is String && $0["timer"] is [String: Any] }
        for part in parts {
            if let air = part["airQualitySensor"] as? [String: Any] {
                status.temperature = status.temperature ?? number(air["temperature"], -50...80)
                status.humidity = status.humidity ?? number(air["humidity"], 0...100)
                status.pm1 = status.pm1 ?? number(air["PM1"], 0...1000)
                status.pm25 = status.pm25 ?? number(air["PM2"], 0...1000)
                status.pm10 = status.pm10 ?? number(air["PM10"], 0...1000)
                if let level = air["totalPollutionLevel"] as? String, HomeCatalog.airLevels[level] != nil { status.airLevel = level }
            }
            if let temperature = part["temperature"] as? [String: Any] {
                status.temperature = status.temperature ?? number(temperature["currentTemperature"], -50...80)
            }
            if let operation = part["operation"] as? [String: Any] {
                for (key, value) in operation where key.hasSuffix("OperationMode") {
                    if value as? String == "POWER_ON" { status.power = true } else if value as? String == "POWER_OFF" { status.power = false }
                }
            }
            if let humidity = part["humidity"] as? [String: Any] { status.targetHumidity = number(humidity["targetHumidity"], 0...100) }
            for door in doors(part["doorStatus"]) where door["doorState"] as? String == "OPEN" {
                status.openDoors.append(HomeCatalog.location(door["locationName"] as? String) ?? "문")
            }
            if let run = part["runState"] as? [String: Any], let current = run["currentState"] as? String,
               !cycleParts.contains(where: { NSDictionary(dictionary: $0).isEqual(to: part) }) {
                status.activity = HomeCatalog.stateLabel(type: type, state: current)
            }
            if let run = part["runState"] as? [String: Any], run["currentState"] == nil {
                if run["sterilizingState"] as? String == "ON" { status.activity = "살균 중" }
                else if run["cockState"] as? String == "CLEANING" { status.activity = "출수구 세척 중" }
                else if run["cockState"] != nil { status.activity = "정상" }
            }
        }
        if let first = parts.first {
            for target in (first["temperatureInUnits"] as? [[String: Any]]) ?? [] {
                if let value = number(target["targetTemperatureC"], -50...80), let name = HomeCatalog.location(target["locationName"] as? String) {
                    status.targets.append("\(name) \(Reading.compact(value, unit: "°"))")
                }
            }
        }
        let multiple = cycleParts.count > 1
        for part in cycleParts {
            let run = part["runState"] as! [String: Any]
            let timer = part["timer"] as! [String: Any]
            let state = run["currentState"] as! String
            let remaining = seconds(timer, "remain")
            var phase = HomeCatalog.phase(state)
            if phase == nil { phase = (remaining ?? 0) > 0 ? .running : .idle }
            let location = multiple ? HomeCatalog.location((part["location"] as? [String: Any])?["locationName"] as? String) : nil
            let total = seconds(timer, "total").flatMap { $0 > 0 ? $0 : nil }
            let startsIn = seconds(timer, "relative", suffix: "ToStart").flatMap { $0 > 0 ? $0 : nil }
            let keepsRemaining = phase == .running || phase == .paused
            status.cycles.append(CycleStatus(location: location, state: state, phase: phase!,
                                             label: HomeCatalog.stateLabel(type: type, state: state),
                                             remaining: keepsRemaining ? remaining.flatMap { $0 > 0 ? $0 : nil } : nil,
                                             total: phase == .idle ? nil : total, startsIn: phase == .reserved ? startsIn : nil))
        }
        return status
    }

    private static func doors(_ raw: Any?) -> [[String: Any]] {
        if let list = raw as? [[String: Any]] { return list }
        if let one = raw as? [String: Any] { return [one] }
        return []
    }
    private static func number(_ raw: Any?, _ range: ClosedRange<Double>) -> Double? { Reading.sensor(raw, range: range) }
    /// Reads `<prefix>Hour`, `<prefix>Minute` and optional `<prefix>Second`. Out-of-range parts make the value unknown.
    private static func seconds(_ timer: [String: Any], _ prefix: String, suffix: String = "") -> Int? {
        let hour = number(timer[prefix + "Hour" + suffix], 0...99)
        let minute = number(timer[prefix + "Minute" + suffix], 0...59)
        let second = number(timer[prefix + "Second" + suffix], 0...59)
        let present = [timer[prefix + "Hour" + suffix], timer[prefix + "Minute" + suffix], timer[prefix + "Second" + suffix]].contains { $0 != nil }
        guard present else { return nil }
        if (timer[prefix + "Hour" + suffix] != nil && hour == nil) || (timer[prefix + "Minute" + suffix] != nil && minute == nil)
            || (timer[prefix + "Second" + suffix] != nil && second == nil) { return nil }
        return Int((hour ?? 0) * 3600 + (minute ?? 0) * 60 + (second ?? 0))
    }
}

enum HomeFormat {
    /// "23분", "1시간 5분". Rounds up so a running cycle never shows 0분.
    /// Korean subject particle: 이 after a final consonant, otherwise 가.
    static func subject(_ word: String) -> String {
        guard let last = word.unicodeScalars.last?.value, (0xAC00...0xD7A3).contains(last) else { return "이" }
        return (last - 0xAC00) % 28 == 0 ? "가" : "이"
    }
    static func duration(_ seconds: Int) -> String {
        let minutes = max(1, (seconds + 59) / 60)
        let hours = minutes / 60, rest = minutes % 60
        if hours == 0 { return "\(rest)분" }
        return rest == 0 ? "\(hours)시간" : "\(hours)시간 \(rest)분"
    }
}

enum HomeCatalog {
    static let airLevels = ["GOOD": "좋음", "NORMAL": "보통", "BAD": "나쁨", "VERY_BAD": "매우 나쁨"]
    static let coldStorage: Set<String> = ["DEVICE_REFRIGERATOR", "DEVICE_KIMCHI_REFRIGERATOR", "DEVICE_WINE_CELLAR"]
    static let climateTypes: Set<String> = ["DEVICE_HUMIDIFIER", "DEVICE_AIR_PURIFIER", "DEVICE_AIR_PURIFIER_FAN", "DEVICE_DEHUMIDIFIER", "DEVICE_AIR_CONDITIONER"]
    static let doneNoun = ["DEVICE_WASHER": "세탁", "DEVICE_WASHTOWER": "세탁", "DEVICE_WASHTOWER_WASHER": "세탁", "DEVICE_WASHCOMBO_MAIN": "세탁",
                           "DEVICE_WASHCOMBO_MINI": "세탁", "DEVICE_DRYER": "건조", "DEVICE_WASHTOWER_DRYER": "건조", "DEVICE_STYLER": "스타일러 케어",
                           "DEVICE_DISH_WASHER": "세척", "DEVICE_OVEN": "조리", "DEVICE_MICROWAVE_OVEN": "조리"]
    static let names = [
        "DEVICE_AIR_CONDITIONER": "에어컨", "DEVICE_AIR_PURIFIER": "공기청정기", "DEVICE_AIR_PURIFIER_FAN": "공기청정 팬",
        "DEVICE_CEILING_FAN": "실링팬", "DEVICE_COOKTOP": "쿡탑", "DEVICE_DEHUMIDIFIER": "제습기", "DEVICE_DISH_WASHER": "식기세척기",
        "DEVICE_DRYER": "건조기", "DEVICE_HOME_BREW": "홈브루", "DEVICE_HOOD": "후드", "DEVICE_HUMIDIFIER": "가습기",
        "DEVICE_KIMCHI_REFRIGERATOR": "김치냉장고", "DEVICE_MICROWAVE_OVEN": "전자레인지", "DEVICE_OVEN": "오븐",
        "DEVICE_PLANT_CULTIVATOR": "식물재배기", "DEVICE_REFRIGERATOR": "냉장고", "DEVICE_ROBOT_CLEANER": "로봇청소기",
        "DEVICE_STICK_CLEANER": "무선청소기", "DEVICE_STYLER": "스타일러", "DEVICE_WASHER": "세탁기", "DEVICE_WASHCOMBO_MAIN": "워시콤보",
        "DEVICE_WASHCOMBO_MINI": "워시콤보 미니", "DEVICE_WASHTOWER": "워시타워", "DEVICE_WASHTOWER_WASHER": "워시타워 세탁기",
        "DEVICE_WASHTOWER_DRYER": "워시타워 건조기", "DEVICE_WATER_HEATER": "온수기", "DEVICE_WATER_PURIFIER": "정수기",
        "DEVICE_WINE_CELLAR": "와인셀러", "DEVICE_SYSTEM_BOILER": "보일러", "DEVICE_VENTILATOR": "환기장치"]
    static func name(_ type: String) -> String { names[type] ?? "기기" }
    /// Platform-neutral icon key; each app maps it to its own symbol.
    static func icon(_ type: String) -> String {
        switch type {
        case "DEVICE_HUMIDIFIER", "DEVICE_DEHUMIDIFIER": return "drop"
        case "DEVICE_AIR_PURIFIER", "DEVICE_AIR_PURIFIER_FAN", "DEVICE_VENTILATOR", "DEVICE_CEILING_FAN": return "air"
        case "DEVICE_AIR_CONDITIONER": return "snow"
        case "DEVICE_WASHER", "DEVICE_WASHTOWER", "DEVICE_WASHTOWER_WASHER", "DEVICE_WASHCOMBO_MAIN", "DEVICE_WASHCOMBO_MINI": return "washer"
        case "DEVICE_DRYER", "DEVICE_WASHTOWER_DRYER": return "dryer"
        case "DEVICE_STYLER": return "hanger"
        case "DEVICE_DISH_WASHER": return "dishes"
        case "DEVICE_OVEN", "DEVICE_MICROWAVE_OVEN", "DEVICE_COOKTOP", "DEVICE_HOOD": return "oven"
        case "DEVICE_REFRIGERATOR", "DEVICE_KIMCHI_REFRIGERATOR", "DEVICE_WINE_CELLAR": return "fridge"
        case "DEVICE_WATER_PURIFIER", "DEVICE_WATER_HEATER": return "water"
        case "DEVICE_ROBOT_CLEANER", "DEVICE_STICK_CLEANER": return "vacuum"
        default: return "device"
        }
    }
    static func location(_ raw: String?) -> String? {
        switch raw {
        case nil, "MAIN", "OVEN": return nil
        case "FRIDGE": return "냉장"
        case "FREEZER": return "냉동"
        case "MINI": return "미니"
        case "UPPER": return "위칸"
        case "LOWER": return "아래칸"
        case "CONVERTIBLE": return "변온"
        default: return raw
        }
    }
    static func phase(_ state: String) -> CyclePhase? {
        switch state {
        case "RUNNING", "RINSING", "SPINNING", "DRYING", "SOAKING", "STEAM_SOFTENING", "DETECTING", "PREWASH", "DISPENSING",
             "DETERGENT_AMOUNT", "ADD_DRAIN", "REFRESHING", "COOLING", "STERILIZE", "PRESTEAM", "STEAM", "NIGHT_DRY",
             "COOKING_IN_PROGRESS", "PREHEATING", "CLEANING", "FROZEN_PREVENT_RUNNING": return .running
        case "PAUSE", "PAUSED", "RINSE_HOLD", "FROZEN_PREVENT_PAUSE": return .paused
        case "RESERVED": return .reserved
        case "END", "COMPLETE", "RUNNING_END", "DONE", "WRINKLE_CARE", "STAY", "PREHEATING_IS_DONE": return .done
        case "ERROR", "POWER_FAIL": return .error
        case "POWER_OFF", "INITIAL", "SLEEP", "STANDBY", "FIRMWARE", "CANCEL", "FROZEN_PREVENT_INITIAL", "PREFERENCE": return .idle
        default: return nil
        }
    }
    static func stateLabel(type: String, state: String) -> String {
        let noun = doneNoun[type]
        switch state {
        case "RUNNING":
            switch noun { case "세탁": return "세탁 중"; case "건조": return "건조 중"; case "세척": return "세척 중"; case "스타일러 케어": return "케어 중"; default: return "작동 중" }
        case "END", "COMPLETE", "RUNNING_END", "DONE": return noun.map { ($0 == "스타일러 케어" ? "케어" : $0) + " 완료" } ?? "완료"
        default:
            return [
                "RINSING": "헹굼 중", "SPINNING": "탈수 중", "DRYING": "건조 중", "SOAKING": "불림 중", "STEAM_SOFTENING": "스팀 불림 중",
                "DETECTING": "감지 중", "PREWASH": "애벌 세탁 중", "DISPENSING": "세제 투입 중", "DETERGENT_AMOUNT": "세제량 감지 중",
                "ADD_DRAIN": "배수 중", "REFRESHING": "리프레시 중", "COOLING": "냉각 중", "WRINKLE_CARE": "건조 완료",
                "STERILIZE": "살균 중", "PRESTEAM": "스팀 준비 중", "STEAM": "스팀 중", "NIGHT_DRY": "야간 건조 중",
                "COOKING_IN_PROGRESS": "조리 중", "PREHEATING": "예열 중", "PREHEATING_IS_DONE": "예열 완료", "CLEANING": "청소 중",
                "FROZEN_PREVENT_RUNNING": "동결 방지 중", "PAUSE": "일시정지", "PAUSED": "일시정지", "RINSE_HOLD": "헹굼 대기",
                "FROZEN_PREVENT_PAUSE": "일시정지", "RESERVED": "예약됨", "STAY": "보관 중", "ERROR": "오류", "POWER_FAIL": "정전 감지",
                "POWER_OFF": "꺼짐", "INITIAL": "대기", "SLEEP": "대기", "STANDBY": "대기", "FIRMWARE": "업데이트 중", "CANCEL": "취소됨",
                "FROZEN_PREVENT_INITIAL": "대기", "PREFERENCE": "설정 중", "WORKING": "청소 중", "CHARGING": "충전 중",
                "CHARGING_COMPLETE": "충전 완료"][state] ?? "작동 중"
        }
    }
}
