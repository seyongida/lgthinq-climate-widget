package dev.thinqclimate.widget;

import android.app.*;
import android.content.Intent;
import android.view.*;
import android.widget.*;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Fresh public/tokenless builds must ask the installer for their own PAT. */
@RunWith(AndroidJUnit4.class)
public class OnboardingTest {
    private EditText secret(View view) {
        if(view instanceof EditText e && (e.getInputType()&android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD)!=0)return e;
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){EditText e=secret(group.getChildAt(i));if(e!=null)return e;}
        return null;
    }
    @Test public void firstLaunchRequestsTokenWithoutDisplayingStoredCredentials() throws Exception {
        Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
        Assume.assumeTrue(BuildConfig.THINQ_PAT.isEmpty());
        Assume.assumeTrue(new Store(instrumentation.getTargetContext()).config()==null);
        Instrumentation.ActivityMonitor monitor=instrumentation.addMonitor(CredentialsActivity.class.getName(),null,false);
        Activity main=null,setup=null;
        try {
            main=instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            setup=monitor.waitForActivityWithTimeout(5000);assertNotNull("first launch should open token setup",setup);
            Activity activity=setup;instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(()->{
                EditText field=secret(activity.findViewById(android.R.id.content));assertNotNull(field);assertEquals("",field.getText().toString());assertFalse(field.isSaveEnabled());
                assertTrue((activity.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_SECURE)!=0);
            });
        } finally {
            instrumentation.removeMonitor(monitor);
            if(setup!=null){Activity a=setup;instrumentation.runOnMainSync(a::finish);}
            if(main!=null){Activity a=main;instrumentation.runOnMainSync(a::finish);}
        }
    }
}
