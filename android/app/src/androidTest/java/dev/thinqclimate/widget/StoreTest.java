package dev.thinqclimate.widget;
import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.*;
import org.junit.runner.RunWith;
import dev.thinqclimate.core.Climate;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class StoreTest {
    Store store;
    @Before public void setup() { store=new Store(InstrumentationRegistry.getInstrumentation().getTargetContext());store.clear(); }
    @After public void cleanup() {store.clear();}
    @Test public void encryptedCredentialsRoundTripAndClear() throws Exception {
        var config=new Climate.Config("instrumentation-only-pat","KR","kic","test");
        store.config(config);assertEquals(config,store.config());
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        var prefs=c.getSharedPreferences("climate",Context.MODE_PRIVATE);
        assertFalse(prefs.getAll().toString().contains(config.token()));
        store.clear();assertNull(store.config());
    }
    @Test public void selectionClearsOldReadingAndError() throws Exception {
        store.select(new Climate.Device("one","거실"));
        store.reading(new Climate.Reading(23.4,47.0,100));store.error("오프라인");store.attempt(100);
        assertEquals(Double.valueOf(23.4),store.reading().temperature());
        store.select(new Climate.Device("two","침실"));
        assertNull(store.reading());assertEquals("",store.error());assertEquals(0,store.attempt());assertEquals("two",store.id());
    }
    @Test public void missingSensorsRemainNull() throws Exception {
        store.reading(new Climate.Reading(null,null,100));
        assertNull(store.reading().temperature());assertNull(store.reading().humidity());assertEquals(100,store.reading().fetchedAt());
    }
    @Test public void lateResultCannotRestoreSwitchedOrDisconnectedData() throws Exception {
        store.select(new Climate.Device("one","거실"));String generation=store.generation();
        var reading=new Climate.Reading(23.4,47.0,100);
        assertTrue(store.accept(generation,reading,null));
        store.select(new Climate.Device("two","침실"));
        assertFalse(store.accept(generation,reading,null));assertNull(store.reading());
        assertFalse(store.accept(generation,null,"old request error"));assertEquals("",store.error());
        String second=store.generation();store.clear();
        assertFalse(store.accept(second,reading,null));assertNull(store.reading());assertEquals("",store.id());
    }
}
