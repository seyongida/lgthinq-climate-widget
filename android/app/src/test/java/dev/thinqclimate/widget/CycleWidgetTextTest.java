package dev.thinqclimate.widget;

import static org.junit.Assert.assertEquals;
import dev.thinqclimate.core.Home;
import java.util.Map;
import org.junit.Test;

public class CycleWidgetTextTest {
    private static Home.Cycle cycle(String state, Map<String, Object> timer) {
        return Home.parse(Map.of("runState", Map.of("currentState", state), "timer", timer), "DEVICE_DISH_WASHER", 0L).primaryCycle();
    }
    @Test public void timeBoxStaysShortAndNeverRepeatsTheLabel() {
        assertEquals("곧 끝나요", CycleWidget.staticTime(cycle("RUNNING", Map.of("remainMinute", 5)), 1L));
        assertEquals("3시간 30분 후", CycleWidget.staticTime(cycle("RESERVED", Map.of("relativeHourToStart", 3, "relativeMinuteToStart", 30)), null));
        assertEquals("19분", CycleWidget.staticTime(cycle("PAUSE", Map.of("remainMinute", 19)), null));
        assertEquals("—", CycleWidget.staticTime(cycle("RUNNING", Map.of("remainMinute", 0)), null));
    }
}
