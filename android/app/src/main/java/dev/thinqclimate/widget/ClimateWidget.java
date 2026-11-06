package dev.thinqclimate.widget;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.os.*;
import android.util.SizeF;
import android.view.View;
import android.widget.RemoteViews;
import dev.thinqclimate.core.Climate;
import java.text.DateFormat;
import java.util.*;

public class ClimateWidget extends AppWidgetProvider {
    static final String REFRESH="dev.thinqclimate.widget.REFRESH";
    @Override public void onUpdate(Context c,AppWidgetManager m,int[] ids) {
        for(int id:ids) render(c,id);RefreshWorker.schedule(c);RefreshWorker.refresh(c);
    }
    @Override public void onAppWidgetOptionsChanged(Context c,AppWidgetManager m,int id,Bundle options){render(c,id);}
    @Override public void onReceive(Context c,Intent i){super.onReceive(c,i);if(REFRESH.equals(i.getAction()))RefreshWorker.refresh(c);}
    @Override public void onDeleted(Context c,int[] ids){Store s=new Store(c);for(int id:ids)s.deleteWidget(id);}
    @Override public void onRestored(Context c,int[] oldIds,int[] newIds){Store s=new Store(c);for(int i=0;i<oldIds.length;i++)s.restoreWidget(oldIds[i],newIds[i]);super.onRestored(c,oldIds,newIds);}
    static List<Integer> allIds(Context c) {
        List<Integer> result=new ArrayList<>();AppWidgetManager m=AppWidgetManager.getInstance(c);
        for(Class<?> provider:List.of(ClimateWidget.class,PairClimateWidget.class,HorizontalClimateWidget.class))
            for(int id:m.getAppWidgetIds(new ComponentName(c,provider)))result.add(id);
        return result;
    }
    static void renderAll(Context c){for(int id:allIds(c))render(c,id);}
    static void render(Context c,int id) {
        AppWidgetManager m=AppWidgetManager.getInstance(c);Store store=new Store(c);
        List<String> selected=store.widgetDevices(id,2);
        if(Build.VERSION.SDK_INT>=31) {
            Map<SizeF,RemoteViews> sizes=new LinkedHashMap<>();
            RemoteViews tiny=single(c,store,id,selected,0);
            tiny.setViewVisibility(R.id.status,View.GONE);
            sizes.put(new SizeF(48,48),tiny);
            sizes.put(new SizeF(48,76),single(c,store,id,selected,0));
            sizes.put(new SizeF(48,120),pair(c,store,id,selected,false,true));
            sizes.put(new SizeF(120,48),pair(c,store,id,selected,true,true));
            sizes.put(new SizeF(48,152),pair(c,store,id,selected,false));
            sizes.put(new SizeF(152,76),pair(c,store,id,selected,true));
            m.updateAppWidget(id,new RemoteViews(sizes));
        } else {
            Bundle o=m.getAppWidgetOptions(id);int w=o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,60),h=o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,60);
            RemoteViews views;
            if(w>=120||h>=120)views=pair(c,store,id,selected,w>h,w>h?h<76:h<152);
            else { views=single(c,store,id,selected,0);if(h<76)views.setViewVisibility(R.id.status,View.GONE); }
            m.updateAppWidget(id,views);
        }
    }
    static RemoteViews pair(Context c,Store s,int widgetId,List<String> ids,boolean horizontal) {
        return pair(c,s,widgetId,ids,horizontal,false);
    }
    private static RemoteViews pair(Context c,Store s,int widgetId,List<String> ids,boolean horizontal,boolean dense) {
        return pair(c,s,widgetId,ids,horizontal,dense,s.widgetStyle(widgetId));
    }
    static RemoteViews pair(Context c,Store s,int widgetId,List<String> ids,boolean horizontal,boolean dense,WidgetStyle style) {
        RemoteViews v=new RemoteViews(c.getPackageName(),horizontal?R.layout.climate_widget_pair_horizontal:R.layout.climate_widget_pair);
        tint(v,R.id.pair_container,style.surface());
        v.removeAllViews(R.id.pair_container);
        for(int slot=0;slot<2;slot++) {
            RemoteViews card=single(c,s,widgetId,ids,slot,style,false);
            if(dense)card.setViewVisibility(R.id.status,View.GONE);
            v.addView(R.id.pair_container,card);
        }
        return v;
    }
    static String compact(Double value,String unit) {
        if(value==null)return "—";
        return String.format(Locale.KOREA,value==Math.rint(value)?"%.0f%s":"%.1f%s",value,unit);
    }
    static void tint(RemoteViews v,int id,int color) {
        if(Build.VERSION.SDK_INT>=31)v.setColorStateList(id,"setBackgroundTintList",android.content.res.ColorStateList.valueOf(color));
        else v.setInt(id,"setBackgroundColor",color);
    }
    static RemoteViews single(Context c,Store s,int widgetId,List<String> ids,int index) {
        return single(c,s,widgetId,ids,index,s.widgetStyle(widgetId),true);
    }
    static RemoteViews single(Context c,Store s,int widgetId,List<String> ids,int index,WidgetStyle style,boolean surface) {
        String id=index<ids.size()?ids.get(index):null;Climate.Device d=id==null?null:s.device(id);
        Climate.Reading r=id==null?null:s.reading(id);String error=id==null?"":s.error(id);
        RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.climate_widget);
        tint(v,R.id.widget_root,surface?style.surface():android.graphics.Color.TRANSPARENT);
        v.setTextColor(R.id.device_name,style.muted());
        v.setTextColor(R.id.status,style.muted());
        v.setTextViewText(R.id.device_name,d==null?"기기 선택  +":d.name());
        v.setTextViewText(R.id.temperature,r==null?"—":compact(r.temperature(),"°"));
        v.setTextViewText(R.id.humidity,r==null?"—":compact(r.humidity(),"%"));
        String status=d==null?"눌러서 설정":r==null?"수신 대기 ↻":new java.text.SimpleDateFormat("HH:mm",Locale.KOREA).format(new Date(r.fetchedAt()))+" ↻";
        if(!error.isEmpty())status="연결 오류 ↻";
        else if(r!=null&&r.stale(System.currentTimeMillis()))status="오래됨 ↻";
        v.setTextViewText(R.id.status,status);
        // Hosts can reapply a RemoteViews instance: reset presentation after a size/error change.
        v.setViewVisibility(R.id.status,View.VISIBLE);
        v.setTextColor(R.id.temperature,style.foreground());
        v.setTextColor(R.id.humidity,style.accent());
        if(d!=null && (!error.isEmpty() || (r!=null && r.stale(System.currentTimeMillis())))) {
            v.setTextViewText(R.id.device_name,"! "+d.name());
            v.setTextColor(R.id.temperature,style.warning());
            v.setTextColor(R.id.humidity,style.warning());
        }
        v.setContentDescription(R.id.widget_root,(d==null?"기기 선택":d.name())+", 온도 "+(r==null?"없음":r.temperatureText())+", 습도 "+(r==null?"없음":r.humidityText())+", "+status+(error.isEmpty()?"":", "+error));
        Intent configure=new Intent(c,WidgetConfigActivity.class).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,widgetId)
            .setData(android.net.Uri.parse("thinqclimate://widget/"+widgetId));
        v.setOnClickPendingIntent(R.id.widget_root,PendingIntent.getActivity(c,widgetId,configure,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT));
        Intent refresh=new Intent(c,ClimateWidget.class).setAction(REFRESH);
        v.setOnClickPendingIntent(R.id.status,PendingIntent.getBroadcast(c,0,refresh,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT));
        return v;
    }
}
