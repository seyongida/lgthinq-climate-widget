package dev.thinqclimate.widget;

import android.content.*;
import android.security.keystore.*;
import android.util.Base64;
import java.security.KeyStore;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import org.json.*;
import dev.thinqclimate.core.Climate;

/** Credentials use Keystore; readings and widget assignments are independent per device/widget. */
final class Store {
    private final SharedPreferences prefs;
    Store(Context c) { this(c, "climate"); }
    Store(Context c, String preferenceName) {
        prefs = c.getSharedPreferences(preferenceName, Context.MODE_PRIVATE);
        synchronized (RefreshWorker.LOCK) {
            if (!prefs.contains("devices") && !prefs.getString("device", "").isEmpty()) {
                String id = prefs.getString("device", "");
                String oldReading = prefs.getString("reading", null);
                String oldError = prefs.getString("error", "");
                selectDevices(List.of(new Climate.Device(id, prefs.getString("name", "가습기"))));
                SharedPreferences.Editor e = prefs.edit();
                if (oldReading != null) e.putString(key("reading", id), oldReading);
                e.putString(key("error", id), oldError).remove("device").remove("name").remove("reading").remove("error").remove("attempt").commit();
            }
        }
    }
    private javax.crypto.SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        if (!ks.containsAlias("thinq.credentials")) {
            KeyGenerator g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            g.init(new KeyGenParameterSpec.Builder("thinq.credentials", KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            g.generateKey();
        }
        return (javax.crypto.SecretKey) ks.getKey("thinq.credentials", null);
    }
    void config(Climate.Config c) throws Exception {
        synchronized (RefreshWorker.LOCK) {
            JSONObject o = new JSONObject().put("token",c.token()).put("country",c.country()).put("region",c.region()).put("client",c.clientId());
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key());
            byte[] encrypted = cipher.doFinal(o.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            SharedPreferences.Editor e = prefs.edit().putString("secret", Base64.encodeToString(encrypted,Base64.NO_WRAP))
                .putString("iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)).putString("generation",UUID.randomUUID().toString());
            for (String k : prefs.getAll().keySet()) if (k.startsWith("attempt:")) e.remove(k);
            if (!e.commit()) throw new java.io.IOException("설정 저장 실패");
        }
    }
    Climate.Config config() throws Exception {
        synchronized (RefreshWorker.LOCK) {
            if (!prefs.contains("secret")) return null;
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(prefs.getString("iv",""),Base64.NO_WRAP)));
            String json = new String(cipher.doFinal(Base64.decode(prefs.getString("secret",""),Base64.NO_WRAP)),java.nio.charset.StandardCharsets.UTF_8);
            JSONObject o = new JSONObject(json);
            return new Climate.Config(o.getString("token"),o.getString("country"),o.getString("region"),o.getString("client"));
        }
    }
    boolean bootstrap() throws Exception {
        synchronized (RefreshWorker.LOCK) {
            if (!BuildConfig.DEBUG || BuildConfig.THINQ_PAT.isBlank()) return false;
            String stamp = Base64.encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(
                (BuildConfig.THINQ_PAT + "|" + BuildConfig.THINQ_COUNTRY + "|" + BuildConfig.THINQ_REGION)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)), Base64.NO_WRAP);
            if (stamp.equals(prefs.getString("build_credential", "")) && prefs.contains("secret")) return false;
            Climate.Config old = null;
            try { old = config(); } catch (Exception ignored) { /* Replace unreadable old credentials. */ }
            config(new Climate.Config(BuildConfig.THINQ_PAT, BuildConfig.THINQ_COUNTRY, BuildConfig.THINQ_REGION,
                old == null ? "climate-" + UUID.randomUUID() : old.clientId()));
            prefs.edit().putString("build_credential", stamp).commit();
            return true;
        }
    }
    /** True while the on-device sample home is shown instead of an LG account. */
    boolean demo() { return prefs.getBoolean("demo", false) && !prefs.contains("secret"); }
    void startDemo() {
        synchronized (RefreshWorker.LOCK) {
            prefs.edit().clear().putBoolean("demo", true).commit();
            catalog(dev.thinqclimate.core.Demo.DEVICES);
            selectDevices(dev.thinqclimate.core.Demo.DEVICES);
            fillDemo(System.currentTimeMillis());
        }
    }
    /** Writes fresh sample states; no network. */
    void fillDemo(long now) {
        synchronized (RefreshWorker.LOCK) {
            if (!demo()) return;
            dev.thinqclimate.core.Energy.Window w = dev.thinqclimate.core.Energy.window(java.time.LocalDate.now());
            for (Climate.Device d : devices()) {
                if (!dev.thinqclimate.core.Demo.isDemo(d.id())) continue;
                try {
                    status(d.id(), dev.thinqclimate.core.Demo.state(d, now), now);
                    dev.thinqclimate.core.Energy.Usage u = dev.thinqclimate.core.Demo.energy(d, w, now);
                    if (u != null) energy(d.id(), u);
                } catch (JSONException ignored) { /* Sample data is fixed and always serializable. */ }
            }
        }
    }
    void replaceCredentials(Climate.Config config, List<Climate.Device> discovered) throws Exception {
        synchronized (RefreshWorker.LOCK) {
            if (demo()) prefs.edit().clear().commit();
            config(config);
            SharedPreferences.Editor edit=prefs.edit();
            for(String key:prefs.getAll().keySet())
                if(key.startsWith("reading:") || key.startsWith("error:") || key.startsWith("attempt:") || key.startsWith("state:") || key.startsWith("energy"))edit.remove(key);
            edit.commit();
            catalog(discovered);
            selectDevices(discovered);
        }
    }
    private String key(String prefix, String id) {
        return prefix + ":" + Base64.encodeToString(id.getBytes(java.nio.charset.StandardCharsets.UTF_8), Base64.NO_WRAP | Base64.URL_SAFE);
    }
    String generation() { return prefs.getString("generation", ""); }
    List<Climate.Device> devices() { return decodeDevices(prefs.getString("devices", "[]")); }
    List<Climate.Device> catalog() { return decodeDevices(prefs.getString("catalog", prefs.getString("devices", "[]"))); }
    private List<Climate.Device> decodeDevices(String raw) {
        List<Climate.Device> result = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(raw);
            for (int i=0; i<a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                result.add(new Climate.Device(o.getString("id"), o.getString("name"), o.optString("type", "DEVICE_HUMIDIFIER")));
            }
        } catch (JSONException ignored) { /* Malformed cache is rediscovered. */ }
        return List.copyOf(result);
    }
    private String encodeDevices(List<Climate.Device> devices) {
        JSONArray a = new JSONArray();
        try { for (Climate.Device d : devices) a.put(new JSONObject().put("id",d.id()).put("name",d.name()).put("type",d.type())); }
        catch (JSONException e) { throw new IllegalArgumentException("기기 저장 실패", e); }
        return a.toString();
    }
    void catalog(List<Climate.Device> devices) { prefs.edit().putString("catalog", encodeDevices(devices)).putInt("catalogVersion", CATALOG_VERSION).commit(); }
    /** Version 2 lists every device type, not only climate devices. */
    static final int CATALOG_VERSION = 2;
    boolean catalogIsCurrent() { return prefs.getInt("catalogVersion", 0) >= CATALOG_VERSION; }
    void energy(String id, dev.thinqclimate.core.Energy.Usage u) throws JSONException {
        JSONObject o = new JSONObject().put("current",u.current()).put("label",u.label()).put("compare",u.compareLabel()).put("at",u.fetchedAt());
        if (u.previous() != null) o.put("previous",u.previous());
        prefs.edit().putString(key("energy",id),o.toString()).commit();
    }
    dev.thinqclimate.core.Energy.Usage energy(String id) {
        try {
            JSONObject o = new JSONObject(prefs.getString(key("energy",id),"{}"));
            if (!o.has("current")) return null;
            return new dev.thinqclimate.core.Energy.Usage(o.getInt("current"), o.has("previous") ? o.getInt("previous") : null, o.getString("label"), o.getString("compare"), o.getLong("at"));
        } catch (JSONException e) { return null; }
    }
    void clearEnergy(String id) { prefs.edit().remove(key("energy",id)).commit(); }
    long energySkip(String id) { return prefs.getLong(key("energySkip",id),0); }
    void energySkip(String id, long until) { prefs.edit().putLong(key("energySkip",id),until).commit(); }
    boolean liveUpdates() { return prefs.getBoolean("liveUpdates", true); }
    void liveUpdates(boolean on) { prefs.edit().putBoolean("liveUpdates", on).commit(); }
    boolean notificationAsked() { return prefs.getBoolean("notificationAsked", false); }
    void notificationAsked(boolean asked) { prefs.edit().putBoolean("notificationAsked", asked).commit(); }
    Set<String> shownCycles() { return new HashSet<>(prefs.getStringSet("shownCycles", Set.of())); }
    void shownCycles(Set<String> keys) { prefs.edit().putStringSet("shownCycles", new HashSet<>(keys)).commit(); }
    /** Stores the raw state; climate readings are kept alongside for the climate widgets. */
    void status(String id, Object rawState, long at) throws JSONException {
        Climate.Device d = device(id);
        dev.thinqclimate.core.Home.Status s = dev.thinqclimate.core.Home.parse(rawState, d == null ? "" : d.type(), at);
        SharedPreferences.Editor e = prefs.edit().putString(key("state",id), new JSONObject().put("at",at).put("raw",ThinQ.json(rawState)).toString()).remove(key("error",id));
        if (s.hasClimate()) e.putString(key("reading",id), new JSONObject().put("t",s.temperature()).put("h",s.humidity()).put("at",at).toString());
        e.commit();
    }
    dev.thinqclimate.core.Home.Status status(String id) {
        Climate.Device d = device(id);
        try {
            JSONObject o = new JSONObject(prefs.getString(key("state",id),"{}"));
            if (d == null || !o.has("raw")) return null;
            return dev.thinqclimate.core.Home.parse(ThinQ.parseJson(o.getString("raw")), d.type(), o.getLong("at"));
        } catch (JSONException e) { return null; }
    }
    boolean isClimate(Climate.Device d) {
        if (dev.thinqclimate.core.Home.CLIMATE_TYPES.contains(d.type())) return true;
        dev.thinqclimate.core.Home.Status s = status(d.id()); return s != null && s.hasClimate();
    }
    List<Climate.Device> climateDevices() { List<Climate.Device> r = new ArrayList<>(); for (Climate.Device d : devices()) if (isClimate(d)) r.add(d); return r; }
    boolean acceptStatus(String generation, String id, Object rawState, long at, String error) throws JSONException {
        synchronized (RefreshWorker.LOCK) {
            if (!generation.equals(generation()) || device(id) == null) return false;
            if (rawState != null) status(id, rawState, at); else error(id, error);
            return true;
        }
    }
    void selectDevices(List<Climate.Device> selected) {
        synchronized (RefreshWorker.LOCK) {
            LinkedHashMap<String,Climate.Device> unique = new LinkedHashMap<>();
            for (Climate.Device d : selected) unique.put(d.id(),d);
            SharedPreferences.Editor e = prefs.edit().putString("devices", encodeDevices(new ArrayList<>(unique.values())))
                .putString("generation",UUID.randomUUID().toString());
            for (Climate.Device old : devices()) if (!unique.containsKey(old.id())) {
                e.remove(key("reading",old.id())).remove(key("error",old.id())).remove(key("attempt",old.id())).remove(key("state",old.id()));
            }
            e.commit();
        }
    }
    /** Updates names and drops deleted devices without starting a new generation, so in-flight results stay valid. */
    void renameDevices(List<Climate.Device> merged) {
        synchronized (RefreshWorker.LOCK) {
            Set<String> keep = new HashSet<>(); for (Climate.Device d : merged) keep.add(d.id());
            SharedPreferences.Editor e = prefs.edit().putString("devices", encodeDevices(merged));
            for (Climate.Device old : devices()) if (!keep.contains(old.id()))
                e.remove(key("reading",old.id())).remove(key("error",old.id())).remove(key("attempt",old.id())).remove(key("state",old.id())).remove(key("energy",old.id()));
            e.commit();
        }
    }
    Climate.Device device(String id) { for (Climate.Device d : devices()) if (d.id().equals(id)) return d; return null; }
    void reading(String id, Climate.Reading r) throws JSONException {
        JSONObject o = new JSONObject().put("t",r.temperature()).put("h",r.humidity()).put("at",r.fetchedAt());
        prefs.edit().putString(key("reading",id),o.toString()).remove(key("error",id)).commit();
    }
    Climate.Reading reading(String id) {
        try {
            JSONObject o = new JSONObject(prefs.getString(key("reading",id),"{}"));
            if (!o.has("at")) return null;
            return new Climate.Reading(o.has("t") ? o.getDouble("t") : null, o.has("h") ? o.getDouble("h") : null,o.getLong("at"));
        } catch (JSONException e) { return null; }
    }
    boolean accept(String generation, String id, Climate.Reading reading, String error) throws JSONException {
        synchronized (RefreshWorker.LOCK) {
            if (!generation.equals(generation()) || device(id) == null) return false;
            if (reading != null) reading(id,reading); else error(id,error);
            return true;
        }
    }
    long attempt(String id) { return prefs.getLong(key("attempt",id),0); }
    void attempt(String id, long now) { prefs.edit().putLong(key("attempt",id),now).commit(); }
    String error(String id) { return prefs.getString(key("error",id), ""); }
    void error(String id, String text) { prefs.edit().putString(key("error",id),text).commit(); }
    List<String> widgetDevices(int widgetId, int capacity) {
        List<String> ids = new ArrayList<>();
        if (!prefs.contains("widget:"+widgetId)) {
            for (Climate.Device d : climateDevices()) { if(ids.size() == capacity) break; ids.add(d.id()); }
        } else try {
            JSONArray a = new JSONArray(prefs.getString("widget:"+widgetId,"[]"));
            for(int i=0;i<a.length() && ids.size()<capacity;i++) {
                String id=a.getString(i); if(device(id)!=null && !ids.contains(id)) ids.add(id);
            }
        } catch(JSONException ignored) { /* Configuration can be edited again. */ }
        return ids;
    }
    void widgetDevices(int widgetId, List<String> ids) {
        synchronized (RefreshWorker.LOCK) {
            List<String> clean = new ArrayList<>();
            for(String id : ids) if(device(id)!=null && !clean.contains(id) && clean.size()<2) clean.add(id);
            prefs.edit().putString("widget:"+widgetId,new JSONArray(clean).toString()).commit();
        }
    }
    /** Options for one appliance timer widget. */
    record TimerOptions(boolean selectedOnly, List<String> devices, int max, String pinned, boolean alerts, WidgetStyle style) {
        static TimerOptions defaults() { return new TimerOptions(false, List.of(), 3, "", true, WidgetStyle.defaults()); }
    }
    TimerOptions timerOptions(int id) {
        try {
            JSONObject o = new JSONObject(prefs.getString("timer:"+id, "{}"));
            if (!o.has("max")) return TimerOptions.defaults();
            List<String> ids = new ArrayList<>(); JSONArray a = o.optJSONArray("devices");
            if (a != null) for (int i=0;i<a.length();i++) ids.add(a.getString(i));
            return new TimerOptions(o.optBoolean("selectedOnly"), List.copyOf(ids), Math.max(1, Math.min(3, o.getInt("max"))), o.optString("pinned"),
                o.optBoolean("alerts", true), WidgetStyle.decode(o.optString("style")));
        } catch (JSONException e) { return TimerOptions.defaults(); }
    }
    void timerOptions(int id, TimerOptions t) throws JSONException {
        prefs.edit().putString("timer:"+id, new JSONObject().put("selectedOnly",t.selectedOnly()).put("devices",new JSONArray(t.devices()))
            .put("max",t.max()).put("pinned",t.pinned()).put("alerts",t.alerts()).put("style",t.style().encode()).toString()).commit();
    }
    List<Climate.Device> appliances() {
        List<Climate.Device> r = new ArrayList<>();
        for (Climate.Device d : devices()) { dev.thinqclimate.core.Home.Status s = status(d.id());
            if (dev.thinqclimate.core.Home.isAppliance(d.type()) || (s != null && !s.cycles().isEmpty())) r.add(d); }
        return r;
    }
    WidgetStyle widgetStyle(int id) { return WidgetStyle.decode(prefs.getString("style:"+id,"")); }
    void widgetStyle(int id, WidgetStyle style) { prefs.edit().putString("style:"+id,style.encode()).commit(); }
    void deleteWidget(int id) { prefs.edit().remove("widget:"+id).remove("style:"+id).remove("timer:"+id).commit(); }
    void restoreWidget(int oldId, int newId) {
        if (prefs.contains("style:"+oldId)) prefs.edit().putString("style:"+newId,prefs.getString("style:"+oldId,"")).remove("style:"+oldId).commit();
        if (prefs.contains("widget:"+oldId)) prefs.edit().putString("widget:"+newId,prefs.getString("widget:"+oldId,"[]")).remove("widget:"+oldId).commit();
    }
    void clear() { synchronized (RefreshWorker.LOCK) { prefs.edit().clear().commit(); } }
}
