package dev.thinqclimate.widget;

import android.content.Context;
import android.view.*;
import android.widget.*;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import dev.thinqclimate.core.Climate;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class WidgetLayoutTest {
    Context context;Store store;
    @Before public void setup() throws Exception {
        context=InstrumentationRegistry.getInstrumentation().getTargetContext();store=new Store(context,"widget-layout-test");store.clear();
        store.selectDevices(List.of(new Climate.Device("humidifier","거실 가습기"),new Climate.Device("purifier","침실 공기청정기","DEVICE_AIR_PURIFIER")));
        store.reading("humidifier",new Climate.Reading(22.0,52.0,System.currentTimeMillis()));store.reading("purifier",new Climate.Reading(24.0,47.0,System.currentTimeMillis()));
    }
    @After public void cleanup(){store.clear();}
    private View inflate(RemoteViews views,int width,int height) {
        FrameLayout parent=new FrameLayout(context);View view=views.apply(context,parent);
        view.measure(View.MeasureSpec.makeMeasureSpec(Ui.dp(context,width),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(Ui.dp(context,height),View.MeasureSpec.EXACTLY));
        view.layout(0,0,view.getMeasuredWidth(),view.getMeasuredHeight());return view;
    }
    private void readable(View root) {
        TextView temp=root.findViewById(R.id.temperature), humidity=root.findViewById(R.id.humidity);
        assertTrue("temperature line fits its allotted pixels",temp.getLayout().getLineBottom(0)<=temp.getHeight());
        assertTrue("humidity line fits its allotted pixels",humidity.getLayout().getLineBottom(0)<=humidity.getHeight());
        assertTrue("temperature text fits width",temp.getPaint().measureText(temp.getText().toString())<=temp.getWidth());
        assertTrue("humidity text fits width",humidity.getPaint().measureText(humidity.getText().toString())<=humidity.getWidth());
        assertEquals(0,temp.getLayout().getEllipsisCount(0));assertEquals(0,humidity.getLayout().getEllipsisCount(0));
    }
    @Test public void providersDeclareSmallResizableWidgets() {
        android.appwidget.AppWidgetManager manager=android.appwidget.AppWidgetManager.getInstance(context);
        int found=0;
        for(android.appwidget.AppWidgetProviderInfo info:manager.getInstalledProviders()) {
            if(!info.provider.getPackageName().equals(context.getPackageName()))continue;
            assertEquals(android.appwidget.AppWidgetProviderInfo.RESIZE_BOTH,info.resizeMode);
            if(info.provider.getClassName().endsWith("CycleWidget")) {
                // Countdowns need two cells of width; one row of height still fits a program.
                if(android.os.Build.VERSION.SDK_INT>=31){assertEquals(2,info.targetCellWidth);assertEquals(1,info.targetCellHeight);}
                assertTrue(info.minResizeHeight<=Ui.dp(context,56));found++;continue;
            }
            if(android.os.Build.VERSION.SDK_INT>=31) {
                assertEquals(info.provider.getClassName().endsWith("HorizontalClimateWidget")?2:1,info.targetCellWidth);
                assertEquals(info.provider.getClassName().endsWith("PairClimateWidget")?2:1,info.targetCellHeight);
            }
            assertTrue(info.minResizeWidth<=Ui.dp(context,56));assertTrue(info.minResizeHeight<=Ui.dp(context,56));found++;
        }
        assertEquals(4,found);
    }
    @Test public void compactValuesFitOneCell() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            RemoteViews tiny=ClimateWidget.single(context,store,101,List.of("humidifier"),0);tiny.setViewVisibility(R.id.status,View.GONE);
            View small=inflate(tiny,56,56);readable(small);
            View normal=inflate(ClimateWidget.single(context,store,101,List.of("humidifier"),0),72,84);readable(normal);
            assertEquals("22°",((TextView)normal.findViewById(R.id.temperature)).getText().toString());
        });
    }
    @Test public void twoDevicesFitVerticalAndHorizontalWithoutMixingValues() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            for(boolean horizontal:List.of(false,true)) {
                View root=inflate(ClimateWidget.pair(context,store,102,List.of("humidifier","purifier"),horizontal),horizontal?144:72,horizontal?84:168);
                LinearLayout children=root.findViewById(R.id.pair_container);assertEquals(2,children.getChildCount());
                View first=children.getChildAt(0),second=children.getChildAt(1);readable(first);readable(second);
                assertEquals("22°",((TextView)first.findViewById(R.id.temperature)).getText().toString());
                assertEquals("47%",((TextView)second.findViewById(R.id.humidity)).getText().toString());
            }
        });
    }
    @Test public void palettesStayReadableAndCenteredAndGenerateVisualProof() {
        for(WidgetStyle palette:WidgetStyle.PRESETS) {
            assertTrue(WidgetStyle.contrast(palette.background(),palette.foreground())>=4.5);
            assertTrue(WidgetStyle.contrast(palette.background(),palette.accent())>=3.0);
        }
        for(int gray=0;gray<=255;gray++) {
            WidgetStyle custom=new WidgetStyle(android.graphics.Color.rgb(gray,gray,gray),android.graphics.Color.BLUE,100);
            assertTrue("custom foreground contrast",WidgetStyle.contrast(custom.background(),custom.foreground())>=4.5);
            assertTrue("custom label contrast",WidgetStyle.contrast(custom.background(),custom.muted())>=4.5);
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            int width=Ui.dp(context,244),height=Ui.dp(context,680);
            android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(width,height,android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas=new android.graphics.Canvas(image);canvas.drawColor(android.graphics.Color.rgb(228,233,226));
            for(int i=0;i<WidgetStyle.PRESETS.length;i++) {
                store.widgetStyle(102,WidgetStyle.PRESETS[i]);
                View root=inflate(ClimateWidget.pair(context,store,102,List.of("humidifier","purifier"),true),188,95);
                LinearLayout cards=root.findViewById(R.id.pair_container);
                for(int n=0;n<2;n++) {
                    View card=cards.getChildAt(n);readable(card);
                    TextView temp=card.findViewById(R.id.temperature);
                    assertEquals(android.view.Gravity.CENTER,temp.getGravity());
                    assertEquals(WidgetStyle.PRESETS[i].foreground(),temp.getCurrentTextColor());
                }
                canvas.save();canvas.translate(Ui.dp(context,28),Ui.dp(context,12+i*110));root.draw(canvas);canvas.restore();
            }
            try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(context.getCacheDir(),"widget-palettes.png"))) {
                image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);
            }catch(java.io.IOException e){throw new AssertionError(e);}finally{image.recycle();}
        });
    }

    @Test public void largerSystemFontFitsActualSamsungCellSizes() {
        android.content.res.Configuration config=new android.content.res.Configuration(context.getResources().getConfiguration());config.fontScale=1.3f;
        context=context.createConfigurationContext(config);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            readable(inflate(ClimateWidget.single(context,store,101,List.of("humidifier"),0),81,95));
            LinearLayout pair=(LinearLayout)inflate(ClimateWidget.pair(context,store,102,List.of("humidifier","purifier"),true),187,95);
            readable(pair.getChildAt(0));readable(pair.getChildAt(1));
        });
    }

}
