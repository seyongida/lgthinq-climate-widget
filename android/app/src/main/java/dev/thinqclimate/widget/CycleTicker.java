package dev.thinqclimate.widget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.*;
import dev.thinqclimate.core.Climate;
import dev.thinqclimate.core.Home;

/**
 * Keeps the timer widget and the ongoing notification in step with the countdown between network refreshes.
 * Chronometers tick on their own, but a progress bar is a still picture, and a countdown keeps going below
 * zero. While a program runs, this redraws both from the stored state (no network) about once per 1% of the
 * program and right after the expected end, so the bar moves with the time and "곧 끝나요" replaces "−0:12".
 * The alarm does not wake the phone: a redraw nobody can see waits until the screen is on.
 */
public class CycleTicker extends BroadcastReceiver {
    private static final long MIN_STEP = 60_000L, MAX_STEP = 5 * 60_000L;
    /** Also receives boot and app updates, which clear alarms, so a program already running keeps being redrawn. */
    @Override public void onReceive(Context c, Intent intent) {
        PendingResult result = goAsync();
        new Thread(() -> { try { refresh(c); } finally { result.finish(); } }).start();
    }
    /** For the main thread: the redraw reads and writes preferences and may wait for a tick in progress. */
    static void refreshAsync(Context c) {
        Context app = c.getApplicationContext();
        new Thread(() -> refresh(app)).start();
    }
    /** Redraws everything that shows a countdown and plans the next redraw. */
    static void refresh(Context c) {
        // The alarm and a network refresh can both get here; one at a time keeps the shown notifications consistent.
        synchronized (CycleTicker.class) {
            CycleWidget.renderAll(c);
            CycleNotifier.sync(c);
            schedule(c);
        }
    }
    static void schedule(Context c) {
        Store store = new Store(c);
        long now = System.currentTimeMillis();
        // Nobody would see a redraw: no timer widget and no countdown notifications.
        boolean widgets = android.appwidget.AppWidgetManager.getInstance(c).getAppWidgetIds(new ComponentName(c, CycleWidget.class)).length > 0;
        boolean notices = store.liveUpdates() && CycleNotifier.permitted(c);
        Long next = widgets || notices ? next(store, now) : null;
        AlarmManager alarms = c.getSystemService(AlarmManager.class);
        PendingIntent tick = PendingIntent.getBroadcast(c, 9, new Intent(c, CycleTicker.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        if (next == null) { alarms.cancel(tick); return; }
        // Inexact alarms may arrive up to 75% of the delay late, so the delays shrink near the end
        // and the redraw that replaces the countdown lands within seconds after it reaches zero.
        alarms.set(AlarmManager.RTC, next, tick);
    }
    static Long next(Store store, long now) {
        long next = Long.MAX_VALUE;
        for (Climate.Device d : store.devices()) {
            Home.Status s = store.status(d.id()); if (s == null) continue;
            for (Home.Cycle cycle : s.cycles()) next = Math.min(next, next(s, cycle, now));
        }
        return next == Long.MAX_VALUE ? null : next;
    }
    static long next(Home.Status s, Home.Cycle cycle, long now) {
        Long end = s.endsAt(cycle);
        if (end == null || end <= now) return Long.MAX_VALUE;
        long left = end - now;
        if (left <= 10_000L) return end + 500L;
        long step = cycle.total() == null ? MAX_STEP : Math.max(MIN_STEP, Math.min(MAX_STEP, cycle.total() * 10L));
        return now + Math.min(step, left * 4 / 7);
    }
}
