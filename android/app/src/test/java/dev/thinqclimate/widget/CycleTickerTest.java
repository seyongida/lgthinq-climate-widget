package dev.thinqclimate.widget;

import static org.junit.Assert.*;
import dev.thinqclimate.core.Home;
import java.util.Map;
import org.junit.Test;

public class CycleTickerTest {
    private static Home.Status washer(int remainMinute, int totalMinute, String state) {
        return Home.parse(Map.of("runState", Map.of("currentState", state),
            "timer", Map.of("remainMinute", remainMinute, "totalMinute", totalMinute)), "DEVICE_WASHER", 0L);
    }
    @Test public void redrawsAboutOncePerPercent() {
        Home.Status s = washer(30, 58, "RUNNING");
        assertEquals(60_000L, CycleTicker.next(s, s.primaryCycle(), 0L));
        Home.Status dish = washer(59, 59, "RUNNING");
        Home.Status longer = Home.parse(Map.of("runState", Map.of("currentState", "RUNNING"),
            "timer", Map.of("remainHour", 3, "totalHour", 3)), "DEVICE_DISH_WASHER", 0L);
        assertEquals(108_000L, CycleTicker.next(longer, longer.primaryCycle(), 0L));
        assertEquals(60_000L, CycleTicker.next(dish, dish.primaryCycle(), 0L));
    }
    @Test public void landsRightAfterTheExpectedEnd() {
        Home.Status s = washer(1, 58, "RUNNING");
        long end = 60_000L, now = 0L, at;
        // Even if every inexact alarm arrives 75% of its delay late, none of them passes the end.
        while ((at = CycleTicker.next(s, s.primaryCycle(), now)) < end) {
            assertTrue(now + (at - now) * 7 / 4 <= end);
            now = now + (at - now) * 7 / 4;
        }
        assertEquals(end + 500L, at);
        assertEquals(Long.MAX_VALUE, CycleTicker.next(s, s.primaryCycle(), end + 500L));
    }
    @Test public void pausedProgramsNeedNoRedraw() {
        Home.Status s = washer(19, 48, "PAUSE");
        assertEquals(Long.MAX_VALUE, CycleTicker.next(s, s.primaryCycle(), 0L));
    }
}
