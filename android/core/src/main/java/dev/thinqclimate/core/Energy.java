package dev.thinqclimate.core;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Electricity use as reported by LG ThinQ, in Wh. Compares complete days only, because today is still running.
 * The iOS Energy.swift applies the same rules; both are checked against fixtures/thinq/energy_expected.json.
 */
public final class Energy {
    private Energy() {}
    public record Window(String currentStart, String currentEnd, String previousStart, String previousEnd, String label, String compareLabel) {}
    public record Usage(int current, Integer previous, String label, String compareLabel, long fetchedAt) {}
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    /** This month up to yesterday against the same days last month. On the 1st, last month against the month before. */
    public static Window window(LocalDate today) {
        LocalDate first = today.withDayOfMonth(1);
        if (today.getDayOfMonth() == 1) {
            LocalDate last = first.minusMonths(1), before = first.minusMonths(2);
            return new Window(last.format(DAY), last.withDayOfMonth(last.lengthOfMonth()).format(DAY),
                before.format(DAY), before.withDayOfMonth(before.lengthOfMonth()).format(DAY), "지난달", "그 전 달");
        }
        LocalDate previous = first.minusMonths(1);
        int days = today.getDayOfMonth() - 1;
        return new Window(first.format(DAY), today.minusDays(1).format(DAY), previous.format(DAY),
            previous.withDayOfMonth(Math.min(days, previous.lengthOfMonth())).format(DAY), "이번 달 " + days + "일까지", "지난달 같은 기간");
    }
    public static String kWh(int wh) {
        if (wh == 0) return "0 kWh";
        return String.format(Locale.ROOT, wh < 100 ? "%.2f kWh" : "%.1f kWh", wh / 1000.0);
    }
    /** Differences under 5% are called similar; small swings are noise in daily metering. */
    public static String comparison(int current, Integer previous, String compareLabel) {
        if (previous == null || previous <= 0) return null;
        double change = (current - previous) * 100.0 / previous;
        if (Math.abs(change) < 5) return compareLabel + "과 비슷해요";
        long percent = Math.round(change);
        return compareLabel + "보다 " + Math.abs(percent) + "% " + (percent > 0 ? "많아요" : "적어요");
    }
    /** Sums energyUsage in a usage response, or null if the shape is unexpected. */
    public static Integer sum(Object response) {
        if (!(response instanceof Map<?,?> m) || !(m.get("result") instanceof Map<?,?> result) || !(result.get("dataList") instanceof List<?> list)) return null;
        int total = 0;
        for (Object o : list) if (o instanceof Map<?,?> row) { Double v = Climate.sensor(row.get("energyUsage"), 0, 10_000_000); if (v != null) total += v.intValue(); }
        return total;
    }
}
