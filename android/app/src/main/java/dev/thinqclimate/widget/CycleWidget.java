package dev.thinqclimate.widget;

import android.app.PendingIntent;
import android.appwidget.*;
import android.content.*;
import android.os.*;
import android.util.SizeF;
import android.view.View;
import android.widget.RemoteViews;
import dev.thinqclimate.core.Climate;
import dev.thinqclimate.core.Home;
import java.util.*;

/** Home screen countdown for appliance programs. Chronometers tick on the launcher without refreshes. */
public class CycleWidget extends AppWidgetProvider {
    record Item(Climate.Device device, Home.Status status, Home.Cycle cycle) {
        String title() { return device.name() + (cycle.location() == null ? "" : " " + cycle.location()); }
        Long endsAt() { return status.endsAt(cycle); }
    }
    @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) { for (int id : ids) render(c, id); RefreshWorker.schedule(c); }
    @Override public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle o) { render(c, id); }
    static void renderAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        for (int id : m.getAppWidgetIds(new ComponentName(c, CycleWidget.class))) render(c, id);
    }
    /** Running programs first, soonest to finish first, then paused and scheduled ones. Same order as iOS. */
    static List<Item> active(Store store) {
        List<Item> items = new ArrayList<>();
        for (Climate.Device d : store.devices()) {
            Home.Status s = store.status(d.id()); if (s == null) continue;
            for (Home.Cycle cycle : s.cycles()) if (cycle.active()) items.add(new Item(d, s, cycle));
        }
        List<Home.Phase> order = List.of(Home.Phase.RUNNING, Home.Phase.PAUSED, Home.Phase.RESERVED);
        items.sort(Comparator.<Item>comparingInt(i -> order.indexOf(i.cycle().phase()))
            .thenComparingLong(i -> i.endsAt() == null ? Long.MAX_VALUE : i.endsAt()));
        return items;
    }
    static void render(Context c, int id) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        Store store = new Store(c);
        Content content = content(store, store.timerOptions(id));
        RemoteViews views;
        if (Build.VERSION.SDK_INT >= 31) {
            Map<SizeF, RemoteViews> sizes = new LinkedHashMap<>();
            for (int w : new int[]{110, WIDE}) for (int rows = 1; rows <= 3; rows++)
                sizes.put(new SizeF(w, rows == 1 ? 40 : rows == 2 ? 100 : 160), content.views(c, rows, w < WIDE));
            views = new RemoteViews(sizes);
        } else {
            Bundle o = m.getAppWidgetOptions(id);
            int h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 40), w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110);
            views = content.views(c, h >= 160 ? 3 : h >= 100 ? 2 : 1, w < WIDE);
        }
        m.updateAppWidget(id, views);
    }
    /** Below this width (dp) rows drop the icon, so the name keeps room next to the time. */
    static final int WIDE = 220;
    static RemoteViews forWidget(Context c, Store store, Store.TimerOptions o, int rows) { return forWidget(c, store, o, rows, false); }
    /** Applies one widget's options: which programs, how many, what to show when idle, and colors. */
    static RemoteViews forWidget(Context c, Store store, Store.TimerOptions o, int rows, boolean narrow) {
        return content(store, o).views(c, rows, narrow);
    }
    /** What one widget shows, read from the stored statuses once and then laid out for each size. */
    record Content(List<Item> items, List<String[]> alerts, Climate.Device pinned, Home.Status pinnedStatus, Store.TimerOptions options) {
        RemoteViews views(Context c, int rows, boolean narrow) {
            return CycleWidget.views(c, items, alerts, Math.min(rows, options.max()), pinned, pinnedStatus, options.style(), narrow);
        }
    }
    static Content content(Store store, Store.TimerOptions o) {
        List<Item> items = new ArrayList<>(active(store));
        if (o.selectedOnly()) items.removeIf(i -> !o.devices().contains(i.device().id()));
        List<String[]> alerts = new ArrayList<>();
        if (o.alerts()) for (Climate.Device d : store.devices()) {
            if (o.selectedOnly() && !o.devices().contains(d.id())) continue;
            Home.Status s = store.status(d.id()); if (s != null) for (String a : s.alerts()) alerts.add(new String[]{d.type(), d.name(), a});
        }
        Climate.Device pinned = o.pinned().isEmpty() ? null : store.device(o.pinned());
        return new Content(items, alerts, pinned, pinned == null ? null : store.status(pinned.id()), o);
    }
    static RemoteViews views(Context c, List<Item> items, List<String[]> alerts, int rows) {
        return views(c, items, alerts, rows, null, null, WidgetStyle.defaults());
    }
    static RemoteViews views(Context c, List<Item> items, List<String[]> alerts, int rows, Climate.Device pinned, Home.Status pinnedStatus, WidgetStyle style) {
        return views(c, items, alerts, rows, pinned, pinnedStatus, style, false);
    }
    static RemoteViews views(Context c, List<Item> items, List<String[]> alerts, int rows, Climate.Device pinned, Home.Status pinnedStatus, WidgetStyle style, boolean narrow) {
        int layout = narrow ? R.layout.cycle_widget_row_narrow : R.layout.cycle_widget_row;
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.cycle_widget);
        ClimateWidget.tint(v, R.id.cycle_root, style.surface());
        v.setTextColor(R.id.cycle_empty, style.foreground());
        v.removeAllViews(R.id.cycle_rows);
        int used = 0;
        for (Item item : items) {
            if (used == rows) break;
            // When the widget is too short for every program, the last row says how many others are running.
            int hidden = items.size() - (used + 1);
            v.addView(R.id.cycle_rows, row(c, layout, item, used == rows - 1 && hidden > 0 ? hidden : 0, style)); used++;
        }
        if (items.isEmpty() && pinned != null && used < rows) {
            String[] line = {pinned.type(), pinnedStatus == null ? "상태를 기다리고 있어요" : pinnedStatus.summary(), pinned.name()};
            v.addView(R.id.cycle_rows, alertRow(c, layout, line, style, false)); used++;
        }
        for (String[] a : alerts) { if (used == rows) break; v.addView(R.id.cycle_rows, alertRow(c, layout, new String[]{a[0], a[2], a[1]}, style, true)); used++; }
        v.setViewVisibility(R.id.cycle_empty, used == 0 ? View.VISIBLE : View.GONE);
        v.setViewVisibility(R.id.cycle_rows, used == 0 ? View.GONE : View.VISIBLE);
        Intent open = new Intent(c, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        v.setOnClickPendingIntent(R.id.cycle_root, PendingIntent.getActivity(c, 7, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        return v;
    }
    private static void colors(RemoteViews r, WidgetStyle style, int iconColor) {
        r.setInt(R.id.cycle_icon, "setColorFilter", iconColor);
        if (Build.VERSION.SDK_INT >= 31) r.setColorStateList(R.id.cycle_icon, "setBackgroundTintList", android.content.res.ColorStateList.valueOf(Ui.alpha(iconColor, 38)));
        r.setTextColor(R.id.cycle_name, style.foreground());
        r.setTextColor(R.id.cycle_detail, style.muted());
        r.setTextColor(R.id.cycle_timer, style.foreground());
        r.setTextColor(R.id.cycle_static, style.foreground());
        if (Build.VERSION.SDK_INT >= 31) r.setColorStateList(R.id.cycle_progress, "setProgressTintList", android.content.res.ColorStateList.valueOf(style.accent()));
    }
    private static RemoteViews row(Context c, int layout, Item item, int hidden, WidgetStyle style) {
        RemoteViews r = new RemoteViews(c.getPackageName(), layout);
        r.setImageViewResource(R.id.cycle_icon, Ui.typeIcon(item.device().type()));
        colors(r, style, style.accent());
        r.setTextViewText(R.id.cycle_name, item.title());
        r.setTextViewText(R.id.cycle_detail, detail(item) + (hidden > 0 ? " · 외 " + hidden + "대" : ""));
        Long end = item.endsAt();
        long now = System.currentTimeMillis();
        if (end != null && end > now) {
            r.setChronometer(R.id.cycle_timer, SystemClock.elapsedRealtime() + (end - now), null, true);
            r.setChronometerCountDown(R.id.cycle_timer, true);
            r.setViewVisibility(R.id.cycle_timer, View.VISIBLE); r.setViewVisibility(R.id.cycle_static, View.GONE);
        } else {
            r.setTextViewText(R.id.cycle_static, staticTime(item.cycle(), end));
            r.setViewVisibility(R.id.cycle_timer, View.GONE); r.setViewVisibility(R.id.cycle_static, View.VISIBLE);
        }
        // A still picture of the progress at this moment; CycleTicker redraws it while the program runs.
        Double progress = item.status().progress(item.cycle(), now);
        r.setProgressBar(R.id.cycle_progress, 1000, progress == null ? 0 : (int) Math.round(progress * 1000), false);
        r.setContentDescription(R.id.cycle_name, item.title() + ", " + item.cycle().label() + ", " + Ui.cycleDetail(item.status(), item.cycle()));
        return r;
    }
    /**
     * What the time box says when there is no live countdown: "곧 끝나요" past the expected end, how long until
     * a scheduled program starts (as on iOS), or the time left of a paused one.
     */
    static String staticTime(Home.Cycle cy, Long end) {
        if (end != null) return "곧 끝나요";
        if (cy.phase() == Home.Phase.RESERVED) return cy.startsIn() == null ? "예약" : Home.duration(cy.startsIn()) + " 후";
        return cy.remaining() == null ? "—" : Home.duration(cy.remaining());
    }
    /** "세탁 중 · 오후 3:42 완료 예정", without repeating the label when there is nothing else to add. */
    static String detail(Item item) {
        String label = item.cycle().label(), more = Ui.cycleDetail(item.status(), item.cycle());
        return more.equals(label) ? label : label + " · " + more;
    }
    /** A row without a countdown: an alert ({type, headline, device}) or the idle state of the pinned device. */
    private static RemoteViews alertRow(Context c, int layout, String[] line, WidgetStyle style, boolean warning) {
        RemoteViews r = new RemoteViews(c.getPackageName(), layout);
        r.setImageViewResource(R.id.cycle_icon, Ui.typeIcon(line[0]));
        colors(r, style, warning ? style.warning() : style.accent());
        r.setTextViewText(R.id.cycle_name, warning ? line[1] : line[2]);
        r.setTextViewText(R.id.cycle_detail, warning ? line[2] : line[1]);
        r.setViewVisibility(R.id.cycle_progress, View.GONE);
        r.setViewVisibility(R.id.cycle_timer, View.GONE);
        r.setViewVisibility(R.id.cycle_static, View.GONE);
        return r;
    }
}
