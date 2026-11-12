package dev.thinqclimate.core;

import java.util.*;

/**
 * Interprets any ThinQ Connect device state into what a person needs to know at a glance.
 * The iOS HomeStatus applies the same rules; both are checked against fixtures/thinq.
 */
public final class Home {
    private Home() {}

    public enum Phase { IDLE, RUNNING, PAUSED, RESERVED, DONE, ERROR }

    public record Cycle(String location, String state, Phase phase, String label, Integer remaining, Integer total, Integer startsIn) {
        public boolean active() { return phase == Phase.RUNNING || phase == Phase.PAUSED || phase == Phase.RESERVED; }
    }

    public record Status(String type, long fetchedAt, Double temperature, Double humidity, Double pm1, Double pm25, Double pm10,
                         String airLevel, Boolean power, Double targetHumidity, List<Cycle> cycles, List<String> openDoors,
                         List<String> targets, String activity) {
        public boolean hasClimate() { return temperature != null || humidity != null; }
        public Cycle primaryCycle() {
            List<Phase> order = List.of(Phase.ERROR, Phase.RUNNING, Phase.PAUSED, Phase.RESERVED, Phase.DONE, Phase.IDLE);
            return cycles.stream().min(Comparator.comparingInt(c -> order.indexOf(c.phase()))).orElse(null);
        }
        /** Epoch millis when a running cycle should finish, or null. */
        public Long endsAt(Cycle c) {
            if (c.phase() != Phase.RUNNING || c.remaining() == null || c.remaining() <= 0) return null;
            return fetchedAt + c.remaining() * 1000L;
        }
        public String airText() { String l = airLevel == null ? null : AIR_LEVELS.get(airLevel); return l == null ? null : "공기 " + l; }
        public String summary() {
            Cycle c = primaryCycle();
            if (c != null) {
                return switch (c.phase()) {
                    case RUNNING, PAUSED -> c.remaining() == null ? c.label() : c.label() + " · " + duration(c.remaining()) + " 남음";
                    case RESERVED -> c.startsIn() == null ? "예약" : "예약 · " + duration(c.startsIn()) + " 후 시작";
                    default -> c.label();
                };
            }
            List<String> parts = new ArrayList<>();
            if (hasClimate()) {
                if (temperature != null) parts.add(compact(temperature, "°"));
                if (humidity != null) parts.add(compact(humidity, "%"));
                if (airText() != null) parts.add(airText());
            } else if (!targets.isEmpty()) parts.addAll(targets);
            else if (airText() != null) parts.add(airText());
            if (!openDoors.isEmpty()) parts.add("문 열림");
            if (parts.isEmpty() && activity != null) parts.add(activity);
            return parts.isEmpty() ? "연결됨" : String.join(" · ", parts);
        }
        /** Things worth interrupting someone for. Device names are added by the caller. */
        public List<String> alerts() {
            List<String> result = new ArrayList<>();
            String noun = DONE_NOUN.getOrDefault(type, "작동");
            for (Cycle c : cycles) {
                String prefix = c.location() == null ? "" : c.location() + " ";
                if (c.phase() == Phase.DONE)
                    result.add("PREHEATING_IS_DONE".equals(c.state()) ? prefix + "예열이 끝났어요" : prefix + noun + subject(noun) + " 끝났어요");
                else if (c.phase() == Phase.ERROR) result.add(prefix + name(type) + " 오류가 발생했어요");
            }
            if (COLD_STORAGE.contains(type) && !openDoors.isEmpty()) result.add(name(type) + " 문이 열려 있어요");
            return result;
        }
    }

    public static Status parse(Object state, String type, long now) {
        List<Map<?,?>> parts = new ArrayList<>();
        if (state instanceof List<?> list) { for (Object o : list) if (o instanceof Map<?,?> m) parts.add(m); }
        else if (state instanceof Map<?,?> m) parts.add(m);
        List<Map<?,?>> cycleParts = new ArrayList<>();
        for (Map<?,?> p : parts) if (map(p.get("runState")).get("currentState") instanceof String && p.get("timer") instanceof Map<?,?>) cycleParts.add(p);
        Double temperature = null, humidity = null, pm1 = null, pm25 = null, pm10 = null, target = null;
        String airLevel = null, activity = null; Boolean power = null;
        List<String> doors = new ArrayList<>(), targets = new ArrayList<>();
        for (Map<?,?> p : parts) {
            Map<?,?> air = map(p.get("airQualitySensor"));
            if (!air.isEmpty()) {
                if (temperature == null) temperature = Climate.sensor(air.get("temperature"), -50, 80);
                if (humidity == null) humidity = Climate.sensor(air.get("humidity"), 0, 100);
                if (pm1 == null) pm1 = Climate.sensor(air.get("PM1"), 0, 1000);
                if (pm25 == null) pm25 = Climate.sensor(air.get("PM2"), 0, 1000);
                if (pm10 == null) pm10 = Climate.sensor(air.get("PM10"), 0, 1000);
                if (air.get("totalPollutionLevel") instanceof String l && AIR_LEVELS.containsKey(l)) airLevel = l;
            }
            if (temperature == null) temperature = Climate.sensor(map(p.get("temperature")).get("currentTemperature"), -50, 80);
            for (Map.Entry<?,?> e : map(p.get("operation")).entrySet()) {
                if (!String.valueOf(e.getKey()).endsWith("OperationMode")) continue;
                if ("POWER_ON".equals(e.getValue())) power = true; else if ("POWER_OFF".equals(e.getValue())) power = false;
            }
            Map<?,?> hum = map(p.get("humidity"));
            if (!hum.isEmpty()) target = Climate.sensor(hum.get("targetHumidity"), 0, 100);
            for (Map<?,?> door : maps(p.get("doorStatus")))
                if ("OPEN".equals(door.get("doorState"))) { String l = location(str(door.get("locationName"))); doors.add(l == null ? "문" : l); }
            Map<?,?> run = map(p.get("runState"));
            if (run.get("currentState") instanceof String current && !cycleParts.contains(p)) activity = stateLabel(type, current);
            if (!run.isEmpty() && run.get("currentState") == null) {
                if ("ON".equals(run.get("sterilizingState"))) activity = "살균 중";
                else if ("CLEANING".equals(run.get("cockState"))) activity = "출수구 세척 중";
                else if (run.get("cockState") != null) activity = "정상";
            }
        }
        if (!parts.isEmpty()) for (Map<?,?> t : maps(parts.get(0).get("temperatureInUnits"))) {
            Double v = Climate.sensor(t.get("targetTemperatureC"), -50, 80); String n = location(str(t.get("locationName")));
            if (v != null && n != null) targets.add(n + " " + compact(v, "°"));
        }
        List<Cycle> cycles = new ArrayList<>();
        boolean multiple = cycleParts.size() > 1;
        for (Map<?,?> p : cycleParts) {
            String s = (String) map(p.get("runState")).get("currentState");
            Map<?,?> timer = map(p.get("timer"));
            Integer remaining = seconds(timer, "remain", "");
            Phase phase = phase(s);
            if (phase == null) phase = remaining != null && remaining > 0 ? Phase.RUNNING : Phase.IDLE;
            Integer total = positive(seconds(timer, "total", "")), startsIn = positive(seconds(timer, "relative", "ToStart"));
            boolean keeps = phase == Phase.RUNNING || phase == Phase.PAUSED;
            cycles.add(new Cycle(multiple ? location(str(map(p.get("location")).get("locationName"))) : null, s, phase, stateLabel(type, s),
                keeps ? positive(remaining) : null, phase == Phase.IDLE ? null : total, phase == Phase.RESERVED ? startsIn : null));
        }
        return new Status(type, now, temperature, humidity, pm1, pm25, pm10, airLevel, power, target,
            List.copyOf(cycles), List.copyOf(doors), List.copyOf(targets), activity);
    }

    private static Map<?,?> map(Object o) { return o instanceof Map<?,?> m ? m : Map.of(); }
    private static List<Map<?,?>> maps(Object o) {
        List<Map<?,?>> r = new ArrayList<>();
        if (o instanceof List<?> l) { for (Object x : l) if (x instanceof Map<?,?> m) r.add(m); }
        else if (o instanceof Map<?,?> m) r.add(m);
        return r;
    }
    private static String str(Object o) { return o instanceof String s ? s : null; }
    private static Integer positive(Integer v) { return v != null && v > 0 ? v : null; }
    /** Reads prefixHour/Minute/Second(+suffix). Out-of-range parts make the value unknown. */
    private static Integer seconds(Map<?,?> timer, String prefix, String suffix) {
        Object h = timer.get(prefix + "Hour" + suffix), m = timer.get(prefix + "Minute" + suffix), s = timer.get(prefix + "Second" + suffix);
        if (h == null && m == null && s == null) return null;
        Double hv = Climate.sensor(h, 0, 99), mv = Climate.sensor(m, 0, 59), sv = Climate.sensor(s, 0, 59);
        if ((h != null && hv == null) || (m != null && mv == null) || (s != null && sv == null)) return null;
        return (int) ((hv == null ? 0 : hv) * 3600 + (mv == null ? 0 : mv) * 60 + (sv == null ? 0 : sv));
    }

    public static String compact(double v, String unit) {
        return String.format(Locale.KOREA, v == Math.rint(v) ? "%.0f%s" : "%.1f%s", v, unit);
    }
    /** "23분", "1시간 5분". Rounds up so a running cycle never shows 0분. */
    public static String duration(int seconds) {
        int minutes = Math.max(1, (seconds + 59) / 60), hours = minutes / 60, rest = minutes % 60;
        if (hours == 0) return rest + "분";
        return rest == 0 ? hours + "시간" : hours + "시간 " + rest + "분";
    }
    /** Korean subject particle: 이 after a final consonant, otherwise 가. */
    public static String subject(String word) {
        if (word.isEmpty()) return "이";
        char last = word.charAt(word.length() - 1);
        if (last < 0xAC00 || last > 0xD7A3) return "이";
        return (last - 0xAC00) % 28 == 0 ? "가" : "이";
    }

    public static final Map<String,String> AIR_LEVELS = Map.of("GOOD", "좋음", "NORMAL", "보통", "BAD", "나쁨", "VERY_BAD", "매우 나쁨");
    public static final Set<String> COLD_STORAGE = Set.of("DEVICE_REFRIGERATOR", "DEVICE_KIMCHI_REFRIGERATOR", "DEVICE_WINE_CELLAR");
    public static final Set<String> CLIMATE_TYPES = Set.of("DEVICE_HUMIDIFIER", "DEVICE_AIR_PURIFIER", "DEVICE_AIR_PURIFIER_FAN", "DEVICE_DEHUMIDIFIER", "DEVICE_AIR_CONDITIONER");
    private static final Map<String,String> DONE_NOUN = Map.ofEntries(
        Map.entry("DEVICE_WASHER", "세탁"), Map.entry("DEVICE_WASHTOWER", "세탁"), Map.entry("DEVICE_WASHTOWER_WASHER", "세탁"),
        Map.entry("DEVICE_WASHCOMBO_MAIN", "세탁"), Map.entry("DEVICE_WASHCOMBO_MINI", "세탁"), Map.entry("DEVICE_DRYER", "건조"),
        Map.entry("DEVICE_WASHTOWER_DRYER", "건조"), Map.entry("DEVICE_STYLER", "스타일러 케어"), Map.entry("DEVICE_DISH_WASHER", "세척"),
        Map.entry("DEVICE_OVEN", "조리"), Map.entry("DEVICE_MICROWAVE_OVEN", "조리"));
    private static final Map<String,String> NAMES = Map.ofEntries(
        Map.entry("DEVICE_AIR_CONDITIONER", "에어컨"), Map.entry("DEVICE_AIR_PURIFIER", "공기청정기"), Map.entry("DEVICE_AIR_PURIFIER_FAN", "공기청정 팬"),
        Map.entry("DEVICE_CEILING_FAN", "실링팬"), Map.entry("DEVICE_COOKTOP", "쿡탑"), Map.entry("DEVICE_DEHUMIDIFIER", "제습기"),
        Map.entry("DEVICE_DISH_WASHER", "식기세척기"), Map.entry("DEVICE_DRYER", "건조기"), Map.entry("DEVICE_HOME_BREW", "홈브루"),
        Map.entry("DEVICE_HOOD", "후드"), Map.entry("DEVICE_HUMIDIFIER", "가습기"), Map.entry("DEVICE_KIMCHI_REFRIGERATOR", "김치냉장고"),
        Map.entry("DEVICE_MICROWAVE_OVEN", "전자레인지"), Map.entry("DEVICE_OVEN", "오븐"), Map.entry("DEVICE_PLANT_CULTIVATOR", "식물재배기"),
        Map.entry("DEVICE_REFRIGERATOR", "냉장고"), Map.entry("DEVICE_ROBOT_CLEANER", "로봇청소기"), Map.entry("DEVICE_STICK_CLEANER", "무선청소기"),
        Map.entry("DEVICE_STYLER", "스타일러"), Map.entry("DEVICE_WASHER", "세탁기"), Map.entry("DEVICE_WASHCOMBO_MAIN", "워시콤보"),
        Map.entry("DEVICE_WASHCOMBO_MINI", "워시콤보 미니"), Map.entry("DEVICE_WASHTOWER", "워시타워"), Map.entry("DEVICE_WASHTOWER_WASHER", "워시타워 세탁기"),
        Map.entry("DEVICE_WASHTOWER_DRYER", "워시타워 건조기"), Map.entry("DEVICE_WATER_HEATER", "온수기"), Map.entry("DEVICE_WATER_PURIFIER", "정수기"),
        Map.entry("DEVICE_WINE_CELLAR", "와인셀러"), Map.entry("DEVICE_SYSTEM_BOILER", "보일러"), Map.entry("DEVICE_VENTILATOR", "환기장치"));
    public static String name(String type) { return NAMES.getOrDefault(type, "기기"); }
    /** Devices that run programs with a finish time, such as washers, dryers and dishwashers. */
    public static boolean isAppliance(String type) { return DONE_NOUN.containsKey(type); }
    /** Platform-neutral icon key; each app maps it to its own drawable. */
    public static String icon(String type) {
        return switch (type) {
            case "DEVICE_HUMIDIFIER", "DEVICE_DEHUMIDIFIER" -> "drop";
            case "DEVICE_AIR_PURIFIER", "DEVICE_AIR_PURIFIER_FAN", "DEVICE_VENTILATOR", "DEVICE_CEILING_FAN" -> "air";
            case "DEVICE_AIR_CONDITIONER" -> "snow";
            case "DEVICE_WASHER", "DEVICE_WASHTOWER", "DEVICE_WASHTOWER_WASHER", "DEVICE_WASHCOMBO_MAIN", "DEVICE_WASHCOMBO_MINI" -> "washer";
            case "DEVICE_DRYER", "DEVICE_WASHTOWER_DRYER" -> "dryer";
            case "DEVICE_STYLER" -> "hanger";
            case "DEVICE_DISH_WASHER" -> "dishes";
            case "DEVICE_OVEN", "DEVICE_MICROWAVE_OVEN", "DEVICE_COOKTOP", "DEVICE_HOOD" -> "oven";
            case "DEVICE_REFRIGERATOR", "DEVICE_KIMCHI_REFRIGERATOR", "DEVICE_WINE_CELLAR" -> "fridge";
            case "DEVICE_WATER_PURIFIER", "DEVICE_WATER_HEATER" -> "water";
            case "DEVICE_ROBOT_CLEANER", "DEVICE_STICK_CLEANER" -> "vacuum";
            default -> "device";
        };
    }
    public static String location(String raw) {
        if (raw == null) return null;
        return switch (raw) {
            case "MAIN", "OVEN" -> null;
            case "FRIDGE" -> "냉장"; case "FREEZER" -> "냉동"; case "MINI" -> "미니";
            case "UPPER" -> "위칸"; case "LOWER" -> "아래칸"; case "CONVERTIBLE" -> "변온";
            default -> raw;
        };
    }
    public static Phase phase(String s) {
        return switch (s) {
            case "RUNNING", "RINSING", "SPINNING", "DRYING", "SOAKING", "STEAM_SOFTENING", "DETECTING", "PREWASH", "DISPENSING",
                 "DETERGENT_AMOUNT", "ADD_DRAIN", "REFRESHING", "COOLING", "STERILIZE", "PRESTEAM", "STEAM", "NIGHT_DRY",
                 "COOKING_IN_PROGRESS", "PREHEATING", "CLEANING", "FROZEN_PREVENT_RUNNING" -> Phase.RUNNING;
            case "PAUSE", "PAUSED", "RINSE_HOLD", "FROZEN_PREVENT_PAUSE" -> Phase.PAUSED;
            case "RESERVED" -> Phase.RESERVED;
            case "END", "COMPLETE", "RUNNING_END", "DONE", "WRINKLE_CARE", "STAY", "PREHEATING_IS_DONE" -> Phase.DONE;
            case "ERROR", "POWER_FAIL" -> Phase.ERROR;
            case "POWER_OFF", "INITIAL", "SLEEP", "STANDBY", "FIRMWARE", "CANCEL", "FROZEN_PREVENT_INITIAL", "PREFERENCE" -> Phase.IDLE;
            default -> null;
        };
    }
    private static final Map<String,String> LABELS = Map.ofEntries(
        Map.entry("RINSING", "헹굼 중"), Map.entry("SPINNING", "탈수 중"), Map.entry("DRYING", "건조 중"), Map.entry("SOAKING", "불림 중"),
        Map.entry("STEAM_SOFTENING", "스팀 불림 중"), Map.entry("DETECTING", "감지 중"), Map.entry("PREWASH", "애벌 세탁 중"),
        Map.entry("DISPENSING", "세제 투입 중"), Map.entry("DETERGENT_AMOUNT", "세제량 감지 중"), Map.entry("ADD_DRAIN", "배수 중"),
        Map.entry("REFRESHING", "리프레시 중"), Map.entry("COOLING", "냉각 중"), Map.entry("WRINKLE_CARE", "건조 완료"),
        Map.entry("STERILIZE", "살균 중"), Map.entry("PRESTEAM", "스팀 준비 중"), Map.entry("STEAM", "스팀 중"), Map.entry("NIGHT_DRY", "야간 건조 중"),
        Map.entry("COOKING_IN_PROGRESS", "조리 중"), Map.entry("PREHEATING", "예열 중"), Map.entry("PREHEATING_IS_DONE", "예열 완료"),
        Map.entry("CLEANING", "청소 중"), Map.entry("FROZEN_PREVENT_RUNNING", "동결 방지 중"), Map.entry("PAUSE", "일시정지"),
        Map.entry("PAUSED", "일시정지"), Map.entry("RINSE_HOLD", "헹굼 대기"), Map.entry("FROZEN_PREVENT_PAUSE", "일시정지"),
        Map.entry("RESERVED", "예약됨"), Map.entry("STAY", "보관 중"), Map.entry("ERROR", "오류"), Map.entry("POWER_FAIL", "정전 감지"),
        Map.entry("POWER_OFF", "꺼짐"), Map.entry("INITIAL", "대기"), Map.entry("SLEEP", "대기"), Map.entry("STANDBY", "대기"),
        Map.entry("FIRMWARE", "업데이트 중"), Map.entry("CANCEL", "취소됨"), Map.entry("FROZEN_PREVENT_INITIAL", "대기"),
        Map.entry("PREFERENCE", "설정 중"), Map.entry("WORKING", "청소 중"), Map.entry("CHARGING", "충전 중"), Map.entry("CHARGING_COMPLETE", "충전 완료"));
    public static String stateLabel(String type, String state) {
        String noun = DONE_NOUN.get(type);
        if ("RUNNING".equals(state)) {
            if (noun == null) return "작동 중";
            return switch (noun) { case "세탁" -> "세탁 중"; case "건조" -> "건조 중"; case "세척" -> "세척 중"; case "스타일러 케어" -> "케어 중"; default -> "작동 중"; };
        }
        if (Set.of("END", "COMPLETE", "RUNNING_END", "DONE").contains(state))
            return noun == null ? "완료" : (noun.equals("스타일러 케어") ? "케어" : noun) + " 완료";
        return LABELS.getOrDefault(state, "작동 중");
    }
}
