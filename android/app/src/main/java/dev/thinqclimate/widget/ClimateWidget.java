package dev.thinqclimate.widget;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.widget.RemoteViews;
import dev.thinqclimate.core.Climate;
import java.text.DateFormat;
import java.util.Date;

public final class ClimateWidget extends AppWidgetProvider {
    static final String REFRESH = "dev.thinqclimate.widget.REFRESH";
    @Override public void onUpdate(Context c, AppWidgetManager manager, int[] ids) {
        renderAll(c); RefreshWorker.schedule(c); RefreshWorker.refresh(c);
    }
    @Override public void onReceive(Context c, Intent i) {
        super.onReceive(c,i);
        if (REFRESH.equals(i.getAction())) RefreshWorker.refresh(c);
    }
    @Override public void onDisabled(Context c) {
        // Periodic updates also serve the app; disconnect explicitly stops work.
    }
    static void renderAll(Context c) {
        Store s = new Store(c); Climate.Reading r = s.reading();
        RemoteViews v = new RemoteViews(c.getPackageName(),R.layout.climate_widget);
        v.setTextViewText(R.id.device_name,s.name());
        v.setTextViewText(R.id.temperature,r == null ? "—" : r.temperatureText());
        v.setTextViewText(R.id.humidity,r == null ? "—" : r.humidityText());
        String status = s.error();
        if (status.isEmpty()) status = r == null ? "앱에서 가습기를 연결해 주세요" :
            (r.stale(System.currentTimeMillis()) ? "오래된 데이터 · " : "수신 · ") + DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(r.fetchedAt()));
        else if (r != null) status += "\n마지막 수신 " + DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(r.fetchedAt()));
        v.setTextViewText(R.id.status,status);
        v.setOnClickPendingIntent(R.id.widget_root,PendingIntent.getActivity(c,0,new Intent(c,MainActivity.class),PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        v.setOnClickPendingIntent(R.id.refresh,PendingIntent.getBroadcast(c,1,new Intent(c,ClimateWidget.class).setAction(REFRESH),PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        m.updateAppWidget(new ComponentName(c,ClimateWidget.class),v);
    }
}
