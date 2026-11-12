package dev.thinqclimate.widget;

import android.app.Activity;
import android.app.Instrumentation;
import android.appwidget.AppWidgetManager;
import android.content.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;

/** Explicit visual QA only. Draws our own Activity views, without dismissing the keyguard. */
@RunWith(AndroidJUnit4.class)
public class DesignCaptureTest {
    private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
    private void dump(Activity activity,String name) {
        instrumentation.waitForIdleSync();
        instrumentation.runOnMainSync(()->{
            View root=activity.findViewById(android.R.id.content);
            Bitmap bitmap=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888);
            root.draw(new Canvas(bitmap));
            try(FileOutputStream out=new FileOutputStream(new File(activity.getCacheDir(),name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}
            catch(IOException e){throw new AssertionError(e);}finally{bitmap.recycle();}
        });
    }
    private ScrollView scroll(View view) {
        if(view instanceof ScrollView s)return s;
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){ScrollView result=scroll(group.getChildAt(i));if(result!=null)return result;}
        return null;
    }
    private Button button(View view,String text) {
        if(view instanceof Button b&&b.getText().toString().startsWith(text))return b;
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){Button result=button(group.getChildAt(i),text);if(result!=null)return result;}
        return null;
    }
    /** Draws the whole scrolling page into one tall image, not just the visible part. */
    private void dumpPage(Activity activity,String name) {
        instrumentation.waitForIdleSync();
        instrumentation.runOnMainSync(()->{
            ScrollView s=scroll(activity.findViewById(android.R.id.content));View page=s.getChildAt(0);
            Bitmap bitmap=Bitmap.createBitmap(page.getWidth(),page.getHeight(),Bitmap.Config.ARGB_8888);
            Canvas canvas=new Canvas(bitmap);canvas.drawColor(Ui.BG);page.draw(canvas);
            try(FileOutputStream out=new FileOutputStream(new File(activity.getCacheDir(),name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}
            catch(IOException e){throw new AssertionError(e);}finally{bitmap.recycle();}
        });
    }
    /** Renders the appliance timer widget the way a launcher would, for visual checks. */
    private void dumpWidget(Context context,String name) {
        instrumentation.runOnMainSync(()->{
            Store store=new Store(context);
            RemoteViews views=CycleWidget.views(context,CycleWidget.active(store),java.util.Collections.singletonList(new String[]{"DEVICE_DRYER","건조기","건조가 끝났어요"}),3);
            FrameLayout host=new FrameLayout(context);View v=views.apply(context,host);
            int w=Math.round(300*context.getResources().getDisplayMetrics().density),h=Math.round(170*context.getResources().getDisplayMetrics().density);
            v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));v.layout(0,0,w,h);
            Bitmap bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.drawColor(0xff5a7a72);v.draw(canvas);
            try(FileOutputStream out=new FileOutputStream(new File(context.getCacheDir(),name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}
            catch(IOException e){throw new AssertionError(e);}finally{bitmap.recycle();}
        });
    }
    /** Seeds sample devices on a build without a token, then captures the redesigned screens. */
    @Test public void captureSeededScreens() throws Exception {
        Assume.assumeTrue("seeded".equals(InstrumentationRegistry.getArguments().getString("designCapture")));
        Context context=instrumentation.getTargetContext();
        Store store=new Store(context);
        Assume.assumeTrue(store.config()==null);
        long now=System.currentTimeMillis();
        store.selectDevices(java.util.List.of(new dev.thinqclimate.core.Climate.Device("a","거실 가습기"),
            new dev.thinqclimate.core.Climate.Device("b","침실 공기청정기","DEVICE_AIR_PURIFIER_FAN"),
            new dev.thinqclimate.core.Climate.Device("w","트롬","DEVICE_WASHER"),new dev.thinqclimate.core.Climate.Device("d","건조기","DEVICE_DRYER"),
            new dev.thinqclimate.core.Climate.Device("x","식기세척기","DEVICE_DISH_WASHER"),new dev.thinqclimate.core.Climate.Device("f","냉장고","DEVICE_REFRIGERATOR")));
        store.status("a",ThinQ.parseJson("{\"airQualitySensor\":{\"temperature\":22.3,\"humidity\":43,\"PM2\":5,\"totalPollutionLevel\":\"GOOD\"}}"),now);
        store.status("b",ThinQ.parseJson("{\"airQualitySensor\":{\"temperature\":21.5,\"humidity\":47,\"PM2\":9,\"totalPollutionLevel\":\"NORMAL\"}}"),now);
        store.status("w",ThinQ.parseJson("[{\"runState\":{\"currentState\":\"RINSING\"},\"timer\":{\"remainHour\":0,\"remainMinute\":23,\"totalHour\":0,\"totalMinute\":58},\"location\":{\"locationName\":\"MAIN\"}}]"),now);
        store.status("d",ThinQ.parseJson("{\"runState\":{\"currentState\":\"WRINKLE_CARE\"},\"timer\":{\"remainHour\":0,\"remainMinute\":0,\"totalHour\":1,\"totalMinute\":50}}"),now);
        store.status("x",ThinQ.parseJson("{\"runState\":{\"currentState\":\"RESERVED\"},\"timer\":{\"remainHour\":0,\"remainMinute\":0,\"totalHour\":2,\"totalMinute\":10,\"relativeHourToStart\":2,\"relativeMinuteToStart\":30}}"),now);
        store.status("f",ThinQ.parseJson("{\"temperatureInUnits\":[{\"targetTemperatureC\":3,\"locationName\":\"FRIDGE\"},{\"targetTemperatureC\":-20,\"locationName\":\"FREEZER\"}],\"doorStatus\":[{\"doorState\":\"OPEN\",\"locationName\":\"MAIN\"}]}"),now);
        store.energy("f",new dev.thinqclimate.core.Energy.Usage(10900,11820,"이번 달 11일까지","지난달 같은 기간",now));
        store.energy("w",new dev.thinqclimate.core.Energy.Usage(5560,6900,"이번 달 11일까지","지난달 같은 기간",now));
        Instrumentation.ActivityMonitor monitor=instrumentation.addMonitor(CredentialsActivity.class.getName(),null,false);
        Activity main=instrumentation.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        Activity setup=monitor.waitForActivityWithTimeout(5000);
        try {
            if(setup!=null){dump(setup,"redesign-setup");Activity a=setup;instrumentation.runOnMainSync(a::finish);setup=null;}
            instrumentation.waitForIdleSync();
            dumpPage(main,"redesign-page");
            dumpWidget(context,"redesign-cycle-widget");
            CycleNotifier.sync(context);
            Activity timer=instrumentation.startActivitySync(new Intent(context,CycleConfigActivity.class)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,7777).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            dumpPage(timer,"redesign-timer-settings");
            instrumentation.runOnMainSync(timer::finish);
        } finally {
            instrumentation.removeMonitor(monitor);
            if(setup!=null){Activity a=setup;instrumentation.runOnMainSync(a::finish);}
            if(!main.isFinishing())instrumentation.runOnMainSync(main::finish);
        }
    }
    /** Manual QA with a real account on a build that has a personal token: waits for data, then captures the page. */
    @Test public void captureRealAccount() throws Exception {
        Assume.assumeTrue("real".equals(InstrumentationRegistry.getArguments().getString("designCapture")));
        Context context=instrumentation.getTargetContext();
        Activity main=instrumentation.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            // Wait until most devices have reported, up to two minutes.
            for(int i=0;i<24;i++){
                Thread.sleep(5000);Store s=new Store(context);int reported=0;
                for(dev.thinqclimate.core.Climate.Device d:s.devices())if(s.status(d.id())!=null||!s.error(d.id()).isEmpty())reported++;
                android.util.Log.i("ClimateQA","devices="+s.devices().size()+" reported="+reported);
                if(!s.devices().isEmpty()&&reported>=s.devices().size()-1)break;
            }
            // The phone may be locked, which pauses the screen; open it again so it is built from the stored data.
            instrumentation.runOnMainSync(main::finish);
            Activity fresh=instrumentation.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Thread.sleep(2000);
            dumpPage(fresh,"real-page");
            instrumentation.runOnMainSync(fresh::finish);
            dumpWidget(context,"real-cycle-widget");
        } finally { if(!main.isFinishing())instrumentation.runOnMainSync(main::finish); }
    }
    @Test public void captureOwnScreensWithoutChangingUserPreferences() {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("designCapture")));
        Context context=instrumentation.getTargetContext();
        Activity main=instrumentation.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            dump(main,"design-after-app");
            instrumentation.runOnMainSync(()->{ScrollView s=scroll(main.findViewById(android.R.id.content));s.scrollTo(0,s.getChildAt(0).getHeight());});
            dump(main,"design-after-buttons");
            String id=InstrumentationRegistry.getArguments().getString("widgetId");
            if(id!=null) {
                Activity editor=instrumentation.startActivitySync(new Intent(context,WidgetConfigActivity.class).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,Integer.parseInt(id)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                try {
                    dump(editor,"design-editor-top");
                    instrumentation.runOnMainSync(()->{View root=editor.findViewById(android.R.id.content);button(root,"오션").performClick();});
                    dump(editor,"design-editor-ocean");
                    instrumentation.runOnMainSync(()->{View root=editor.findViewById(android.R.id.content);button(root,"직접 색상 지정").performClick();});
                    instrumentation.waitForIdleSync();
                    instrumentation.runOnMainSync(()->{ScrollView s=scroll(editor.findViewById(android.R.id.content));s.scrollTo(0,s.getChildAt(0).getHeight());});
                    dump(editor,"design-editor-colors");
                }finally{instrumentation.runOnMainSync(editor::finish);}
            }
        }finally{instrumentation.runOnMainSync(main::finish);}
    }
}
