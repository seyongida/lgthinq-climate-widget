package dev.thinqclimate.widget;
import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.*;
import org.junit.runner.RunWith;
import dev.thinqclimate.core.Climate;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class StoreTest {
    Store store;Context context;
    final Climate.Device first=new Climate.Device("one","거실"), second=new Climate.Device("two","침실","DEVICE_AIR_PURIFIER");
    @Before public void setup() {context=InstrumentationRegistry.getInstrumentation().getTargetContext();store=new Store(context,"climate-test");store.clear();}
    @After public void cleanup(){store.clear();}
    @Test public void encryptedCredentialsRoundTripAndClear() throws Exception {
        var config=new Climate.Config("instrumentation-only-pat","KR","kic","test");
        store.config(config);assertEquals(config,store.config());
        assertFalse(context.getSharedPreferences("climate-test",Context.MODE_PRIVATE).getAll().toString().contains(config.token()));
        store.clear();assertNull(store.config());
    }
    @Test public void readingsErrorsAndThrottlesAreIndependent() throws Exception {
        store.selectDevices(List.of(first,second));store.reading(first.id(),new Climate.Reading(23.4,47.0,100));
        store.reading(second.id(),new Climate.Reading(21.0,null,200));store.error(first.id(),"오프라인");store.attempt(first.id(),100);
        assertEquals(Double.valueOf(23.4),store.reading(first.id()).temperature());assertEquals(Double.valueOf(21),store.reading(second.id()).temperature());
        assertNull(store.reading(second.id()).humidity());assertEquals("",store.error(second.id()));assertEquals(0,store.attempt(second.id()));
    }
    @Test public void removingOneDevicePreservesOtherAndDeletesRemovedCache() throws Exception {
        store.selectDevices(List.of(first,second));store.reading(first.id(),new Climate.Reading(23.0,47.0,100));store.reading(second.id(),new Climate.Reading(21.0,50.0,200));
        store.error(first.id(),"old error");store.attempt(first.id(),100);store.selectDevices(List.of(second));
        assertNull(store.reading(first.id()));assertEquals("",store.error(first.id()));assertEquals(0,store.attempt(first.id()));
        assertEquals(Double.valueOf(21),store.reading(second.id()).temperature());
    }
    @Test public void missingSensorsRemainNull() throws Exception {
        store.reading(first.id(),new Climate.Reading(null,null,100));
        assertNull(store.reading(first.id()).temperature());assertNull(store.reading(first.id()).humidity());assertEquals(100,store.reading(first.id()).fetchedAt());
    }
    @Test public void lateResultCannotRestoreRemovedDisconnectedOrReconfiguredData() throws Exception {
        store.selectDevices(List.of(first,second));String generation=store.generation();var reading=new Climate.Reading(23.4,47.0,100);
        assertTrue(store.accept(generation,first.id(),reading,null));store.selectDevices(List.of(second));
        assertFalse(store.accept(generation,first.id(),reading,null));assertFalse(store.accept(generation,second.id(),null,"old error"));assertEquals("",store.error(second.id()));
        String next=store.generation();store.config(new Climate.Config("replacement-pat","KR","kic","test"));
        assertFalse(store.accept(next,second.id(),reading,null));String last=store.generation();store.clear();assertFalse(store.accept(last,second.id(),reading,null));
    }
    @Test public void independentWidgetSelectionsSurviveStoreRecreationAndResize() {
        store.selectDevices(List.of(first,second));store.widgetDevices(101,List.of(first.id(),second.id()));store.widgetDevices(102,List.of(second.id()));
        Store reopened=new Store(context,"climate-test");assertEquals(List.of(first.id()),reopened.widgetDevices(101,1));
        assertEquals(List.of(first.id(),second.id()),reopened.widgetDevices(101,2));assertEquals(List.of(second.id()),reopened.widgetDevices(102,2));
        store.selectDevices(List.of(first));assertTrue(store.widgetDevices(102,2).isEmpty());assertEquals(List.of(first.id()),store.widgetDevices(101,2));
    }
    @Test public void restoredWidgetRetainsSelectionAndDeletedIdResets() {
        store.selectDevices(List.of(first,second));store.widgetDevices(101,List.of(second.id(),first.id()));store.restoreWidget(101,201);
        assertEquals(List.of(second.id(),first.id()),store.widgetDevices(201,2));store.deleteWidget(201);assertEquals(List.of(first.id(),second.id()),store.widgetDevices(201,2));
    }
    @Test public void legacySingleDeviceAndReadingMigrate() {
        store.clear();context.getSharedPreferences("climate-test",Context.MODE_PRIVATE).edit().putString("device","legacy").putString("name","기존 가습기")
            .putString("reading","{\"t\":22.5,\"h\":40,\"at\":100}").putString("error","기존 오류").commit();
        Store migrated=new Store(context,"climate-test");assertEquals("legacy",migrated.devices().get(0).id());
        assertEquals(Double.valueOf(22.5),migrated.reading("legacy").temperature());assertEquals("기존 오류",migrated.error("legacy"));
    }
    @Test public void embeddedCredentialBootstrapsOnceAndNeverAppearsInPreferences() throws Exception {
        if(BuildConfig.THINQ_PAT.isBlank())return;
        assertTrue(store.bootstrap());assertTrue("embedded token loaded",BuildConfig.THINQ_PAT.equals(store.config().token()));String generation=store.generation();
        assertFalse(store.bootstrap());assertEquals(generation,store.generation());
        assertFalse(context.getSharedPreferences("climate-test",Context.MODE_PRIVATE).getAll().toString().contains(BuildConfig.THINQ_PAT));
    }
    @Test public void widgetColorsAreIndependentAndFollowWidgetRestore() {
        WidgetStyle custom=new WidgetStyle(WidgetStyle.parseColor("#102D44"),WidgetStyle.parseColor("83D9E8"),75);
        store.widgetStyle(101,custom);store.widgetStyle(102,WidgetStyle.PRESETS[4]);
        Store reopened=new Store(context,"climate-test");assertEquals(custom,reopened.widgetStyle(101));assertEquals(WidgetStyle.PRESETS[4],reopened.widgetStyle(102));
        store.restoreWidget(101,201);assertEquals(custom,store.widgetStyle(201));assertEquals(WidgetStyle.defaults(),store.widgetStyle(101));
        store.deleteWidget(201);assertEquals(WidgetStyle.defaults(),store.widgetStyle(201));
    }

    @Test public void replacingTokenClearsAccountCacheAndRejectsOldResults() throws Exception {
        store.selectDevices(List.of(first));store.reading(first.id(),new Climate.Reading(23.0,50.0,100));store.error(first.id(),"old");store.attempt(first.id(),100);
        store.widgetDevices(101,List.of(first.id()));store.widgetStyle(101,WidgetStyle.PRESETS[3]);String generation=store.generation();
        var next=new Climate.Config("new-test-token","KR","kic","test");store.replaceCredentials(next,List.of(second));
        assertEquals(next,store.config());assertEquals(List.of(second),store.devices());assertNull(store.reading(first.id()));
        assertEquals("",store.error(first.id()));assertEquals(0,store.attempt(first.id()));assertTrue(store.widgetDevices(101,2).isEmpty());
        assertEquals(WidgetStyle.PRESETS[3],store.widgetStyle(101));assertFalse(store.accept(generation,first.id(),new Climate.Reading(99.0,99.0,200),null));
    }
    @Test public void manualTokenIsNotOverwrittenBySameEmbeddedBuildOnRestart() throws Exception {
        if(BuildConfig.THINQ_PAT.isBlank())return;
        store.bootstrap();store.replaceCredentials(new Climate.Config("manual-test-token","KR","kic","test"),List.of(first));
        assertFalse(store.bootstrap());assertTrue("manual credential retained","manual-test-token".equals(store.config().token()));
    }

}
