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
        List<Item> items = active(store);
        List<String[]> alerts = new ArrayList<>();
        for (Climate.Device d : store.devices()) { Home.Status s = store.status(d.id()); if (s != null) for (String a : s.alerts()) alerts.add(new String[]{d.type(), d.name(), a}); }
        if (Build.VERSION.SDK_INT >= 31) {
            Map<SizeF, RemoteViews> sizes = new LinkedHashMap<>();
            sizes.put(new SizeF(110, 40), views(c, items, alerts, 1));
            sizes.put(new SizeF(110, 100), views(c, items, alerts, 2));
            sizes.put(new SizeF(110, 160), views(c, items, alerts, 3));
            m.updateAppWidget(id, new RemoteViews(sizes));
        } else {
            int h = m.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 40);
            m.updateAppWidget(id, views(c, items, alerts, h >= 160 ? 3 : h >= 100 ? 2 : 1));
        }
    }
    static RemoteViews views(Context c, List<Item> items, List<String[]> alerts, int rows) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.cycle_widget);
        v.removeAllViews(R.id.cycle_rows);
        int used = 0;
        for (Item item : items) { if (used == rows) break; v.addView(R.id.cycle_rows, row(c, item)); used++; }
        for (String[] a : alerts) { if (used == rows) break; v.addView(R.id.cycle_rows, alertRow(c, a)); used++; }
        v.setViewVisibility(R.id.cycle_empty, used == 0 ? View.VISIBLE : View.GONE);
        v.setViewVisibility(R.id.cycle_rows, used == 0 ? View.GONE : View.VISIBLE);
        Intent open = new Intent(c, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        v.setOnClickPendingIntent(R.id.cycle_root, PendingIntent.getActivity(c, 7, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        return v;
    }
    private static RemoteViews row(Context c, Item item) {
        RemoteViews r = new RemoteViews(c.getPackageName(), R.layout.cycle_widget_row);
        r.setImageViewResource(R.id.cycle_icon, Ui.typeIcon(item.device().type()));
        r.setInt(R.id.cycle_icon, "setColorFilter", Ui.GREEN);
        r.setTextViewText(R.id.cycle_name, item.title());
        r.setTextViewText(R.id.cycle_detail, item.cycle().label() + " · " + Ui.cycleDetail(item.status(), item.cycle()));
        Long end = item.endsAt();
        long now = System.currentTimeMillis();
        if (end != null && end > now) {
            r.setChronometer(R.id.cycle_timer, SystemClock.elapsedRealtime() + (end - now), null, true);
            r.setChronometerCountDown(R.id.cycle_timer, true);
            r.setViewVisibility(R.id.cycle_timer, View.VISIBLE); r.setViewVisibility(R.id.cycle_static, View.GONE);
        } else {
            Home.Cycle cy = item.cycle();
            String text = end != null ? "곧 끝나요" : cy.phase() == Home.Phase.RESERVED ? (cy.startsIn() == null ? "예약" : Home.duration(cy.startsIn()) + " 후")
                : cy.remaining() == null ? cy.label() : Home.duration(cy.remaining());
            r.setTextViewText(R.id.cycle_static, text);
            r.setViewVisibility(R.id.cycle_timer, View.GONE); r.setViewVisibility(R.id.cycle_static, View.VISIBLE);
        }
        Integer total = item.cycle().total(), remaining = item.cycle().remaining();
        int progress = total == null || remaining == null || total <= 0 ? 0 : (int) Math.max(0, Math.min(1000, 1000L * (total - remaining) / total));
        r.setProgressBar(R.id.cycle_progress, 1000, progress, false);
        r.setContentDescription(R.id.cycle_name, item.title() + ", " + item.cycle().label() + ", " + Ui.cycleDetail(item.status(), item.cycle()));
        return r;
    }
    private static RemoteViews alertRow(Context c, String[] alert) {
        RemoteViews r = new RemoteViews(c.getPackageName(), R.layout.cycle_widget_row);
        r.setImageViewResource(R.id.cycle_icon, Ui.typeIcon(alert[0]));
        r.setInt(R.id.cycle_icon, "setColorFilter", Ui.WARNING);
        r.setTextViewText(R.id.cycle_name, alert[2]);
        r.setTextViewText(R.id.cycle_detail, alert[1]);
        r.setViewVisibility(R.id.cycle_progress, View.GONE);
        r.setViewVisibility(R.id.cycle_timer, View.GONE);
        r.setViewVisibility(R.id.cycle_static, View.GONE);
        return r;
    }
}
