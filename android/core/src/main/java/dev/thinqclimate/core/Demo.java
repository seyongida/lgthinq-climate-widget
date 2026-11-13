package dev.thinqclimate.core;

import java.util.*;

/**
 * Sample home for trying the app without an LG account. Generated on the device, makes no network
 * requests, and must always be labelled as sample data. Mirrors DemoData on iOS.
 */
public final class Demo {
    private Demo() {}
    public static final String PREFIX = "thinqclimate-demo";
    public static final List<Climate.Device> DEVICES = List.of(
        new Climate.Device(PREFIX + "-1", "체험용 가습기", "DEVICE_HUMIDIFIER"),
        new Climate.Device(PREFIX + "-2", "체험용 공기청정기", "DEVICE_AIR_PURIFIER_FAN"),
        new Climate.Device(PREFIX + "-3", "체험용 세탁기", "DEVICE_WASHER"),
        new Climate.Device(PREFIX + "-4", "체험용 건조기", "DEVICE_DRYER"),
        new Climate.Device(PREFIX + "-5", "체험용 식기세척기", "DEVICE_DISH_WASHER"),
        new Climate.Device(PREFIX + "-6", "체험용 냉장고", "DEVICE_REFRIGERATOR"));

    public static boolean isDemo(String id) { return id != null && id.startsWith(PREFIX); }

    /** A raw ThinQ-shaped state, so sample devices go through the same parser as real ones. */
    public static Object state(Climate.Device device, long now) {
        int index = Math.max(0, DEVICES.indexOf(device));
        switch (device.type()) {
            case "DEVICE_WASHER": {
                // A 45-minute wash that is always 5–45 minutes from finishing, so the sample shows a live countdown.
                int left = 2700 - (int) (now / 1000 % 2400);
                String run = left < 600 ? "SPINNING" : left < 1500 ? "RINSING" : "RUNNING";
                return List.of(Map.of("runState", Map.of("currentState", run),
                    "timer", Map.of("remainHour", left / 3600, "remainMinute", left % 3600 / 60, "totalHour", 0, "totalMinute", 45),
                    "location", Map.of("locationName", "MAIN")));
            }
            case "DEVICE_DRYER":
                return Map.of("runState", Map.of("currentState", "WRINKLE_CARE"),
                    "timer", Map.of("remainHour", 0, "remainMinute", 0, "totalHour", 1, "totalMinute", 50));
            case "DEVICE_DISH_WASHER":
                return Map.of("runState", Map.of("currentState", "RESERVED"), "doorStatus", Map.of("doorState", "CLOSE"),
                    "timer", Map.of("remainHour", 0, "remainMinute", 0, "totalHour", 2, "totalMinute", 10,
                        "relativeHourToStart", 2, "relativeMinuteToStart", 30));
            case "DEVICE_REFRIGERATOR":
                return Map.of("temperatureInUnits", List.of(Map.of("targetTemperatureC", 3, "locationName", "FRIDGE"),
                        Map.of("targetTemperatureC", -20, "locationName", "FREEZER")),
                    "doorStatus", List.of(Map.of("doorState", "CLOSE", "locationName", "MAIN")));
            default: {
                double phase = now / 3_600_000.0 * Math.PI / 6 + index;
                double temperature = Math.round((23 + 1.5 * Math.sin(phase)) * 10) / 10.0;
                double humidity = Math.round(47 + 5 * Math.cos(phase));
                String mode = device.type().equals("DEVICE_HUMIDIFIER") ? "humidifierOperationMode" : "airFanOperationMode";
                return Map.of("operation", Map.of(mode, "POWER_ON"),
                    "airQualitySensor", Map.of("temperature", temperature, "humidity", humidity,
                        "PM2", 6 + index * 3, "PM10", 11 + index * 4, "totalPollutionLevel", "GOOD"));
            }
        }
    }

    /** Sample electricity use for the fridge and washer only, like a real account where few models report it. */
    public static Energy.Usage energy(Climate.Device device, Energy.Window window, long now) {
        switch (device.type()) {
            case "DEVICE_REFRIGERATOR": return new Energy.Usage(11_400, 11_820, window.label(), window.compareLabel(), now);
            case "DEVICE_WASHER": return new Energy.Usage(5_560, 6_900, window.label(), window.compareLabel(), now);
            default: return null;
        }
    }
}
