package dev.thinqclimate.core;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Platform-independent contract. Only current humidifier sensors are accepted. */
public final class Climate {
    private Climate() {}
    public static final long STALE_MS = 90 * 60_000L;
    public static final String PUBLIC_CLIENT_KEY = "v6GFvkweNo7DK7yD3ylIZ9w52aKBU0eJ7wLXkSR3";
    public record Config(String token, String country, String region, String clientId) {
        public Config {
            token = token.trim(); country = country.trim().toUpperCase(Locale.ROOT);
            region = region.toLowerCase(Locale.ROOT);
            if (token.isEmpty() || token.contains("\n") || token.contains("\r") || !country.matches("[A-Z]{2}") ||
                !Set.of("kic", "aic", "eic").contains(region) || !clientId.matches("[a-zA-Z0-9_-]{1,100}"))
                throw new IllegalArgumentException("PAT, 국가 코드, 서버 지역을 확인해 주세요.");
        }
        @Override public String toString() { return "Config[redacted]"; }
        public Map<String,String> headers() {
            var bytes = new byte[16]; new java.security.SecureRandom().nextBytes(bytes);
            return Map.of("Authorization", "Bearer " + token, "x-country", country, "x-client-id", clientId,
                "x-message-id", Base64.getUrlEncoder().withoutPadding().encodeToString(bytes),
                "x-api-key", PUBLIC_CLIENT_KEY, "x-service-phase", "OP", "Accept", "application/json");
        }
        public URI endpoint(String id) {
            String path = id == null ? "devices" : "devices/" + URLEncoder.encode(id, StandardCharsets.UTF_8).replace("+", "%20") + "/state";
            return URI.create("https://api-" + region + ".lgthinq.com/" + path);
        }
    }
    public record Device(String id, String name) {}
    public record Reading(Double temperature, Double humidity, long fetchedAt) {
        public boolean stale(long now) { return now < fetchedAt || now - fetchedAt >= STALE_MS; }
        public String temperatureText() { return value(temperature, "°C"); }
        public String humidityText() { return value(humidity, "%"); }
    }
    public static String value(Double value, String unit) {
        return value == null ? "—" : String.format(Locale.KOREA, "%.1f%s", value, unit);
    }
    public static Double sensor(Object raw, double low, double high) {
        // Reject booleans and numeric strings: the official API supplies numbers.
        if (!(raw instanceof Number n)) return null;
        double v = n.doubleValue();
        return Double.isFinite(v) && v >= low && v <= high ? v : null;
    }
    public static Reading reading(Map<?,?> state, long now) {
        Object resource = state.get("airQualitySensor");
        Map<?,?> sensors = resource instanceof Map<?,?> m ? m : Map.of();
        return new Reading(sensor(sensors.get("temperature"), -50, 80), sensor(sensors.get("humidity"), 0, 100), now);
    }
    public static List<Device> devices(Object response) {
        if (!(response instanceof List<?> list)) throw new IllegalArgumentException("기기 목록 응답 형식이 올바르지 않습니다.");
        var result = new ArrayList<Device>();
        for (Object item : list) {
            if (!(item instanceof Map<?,?> m)) continue;
            Object infoRaw = m.get("deviceInfo");
            Map<?,?> info = infoRaw instanceof Map<?,?> i ? i : Map.of();
            if (!"DEVICE_HUMIDIFIER".equals(info.get("deviceType"))) continue;
            if (!(m.get("deviceId") instanceof String id) || id.isBlank()) continue;
            Object alias = info.get("alias");
            result.add(new Device(id, alias instanceof String s && !s.isBlank() ? s : "가습기"));
        }
        return List.copyOf(result);
    }
    public static String error(int status, String code) {
        if (status == 401 || Set.of("1103", "1218", "1302").contains(code)) return "인증 만료 · 앱에서 PAT를 다시 설정해 주세요.";
        if (status == 429 || "1306".equals(code)) return "요청 한도 초과 · 잠시 후 다시 시도합니다.";
        if ("1222".equals(code)) return "가습기 오프라인 · 마지막 수신값입니다.";
        if (status == 403) return "접근 거부 · PAT 읽기 권한과 국가/지역을 확인해 주세요.";
        return "연결 실패 · 네트워크와 기기 상태를 확인해 주세요.";
    }
}
