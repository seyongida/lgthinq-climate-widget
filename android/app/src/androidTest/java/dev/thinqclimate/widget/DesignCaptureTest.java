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
    /** Seeds sample devices on a build without a token, then captures the redesigned screens. */
    @Test public void captureSeededScreens() throws Exception {
        Assume.assumeTrue("seeded".equals(InstrumentationRegistry.getArguments().getString("designCapture")));
        Context context=instrumentation.getTargetContext();
        Store store=new Store(context);
        Assume.assumeTrue(store.config()==null);
        store.selectDevices(java.util.List.of(new dev.thinqclimate.core.Climate.Device("a","거실 가습기"),new dev.thinqclimate.core.Climate.Device("b","침실 공기청정기","DEVICE_AIR_PURIFIER")));
        store.reading("a",new dev.thinqclimate.core.Climate.Reading(22.3,43.0,System.currentTimeMillis()));
        store.reading("b",new dev.thinqclimate.core.Climate.Reading(21.5,47.0,System.currentTimeMillis()));
        Instrumentation.ActivityMonitor monitor=instrumentation.addMonitor(CredentialsActivity.class.getName(),null,false);
        Activity main=instrumentation.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        Activity setup=monitor.waitForActivityWithTimeout(5000);
        try {
            if(setup!=null){dump(setup,"redesign-setup");Activity a=setup;instrumentation.runOnMainSync(a::finish);setup=null;}
            instrumentation.waitForIdleSync();
            dump(main,"redesign-home");
            instrumentation.runOnMainSync(()->{ScrollView s=scroll(main.findViewById(android.R.id.content));s.scrollTo(0,s.getChildAt(0).getHeight());});
            dump(main,"redesign-home-bottom");
        } finally {
            instrumentation.removeMonitor(monitor);
            if(setup!=null){Activity a=setup;instrumentation.runOnMainSync(a::finish);}
            if(!main.isFinishing())instrumentation.runOnMainSync(main::finish);
            store.clear();
        }
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
