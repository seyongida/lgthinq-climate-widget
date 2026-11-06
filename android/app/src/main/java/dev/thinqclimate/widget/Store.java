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
    void replaceCredentials(Climate.Config config, List<Climate.Device> discovered) throws Exception {
        synchronized (RefreshWorker.LOCK) {
            config(config);
            SharedPreferences.Editor edit=prefs.edit();
            for(String key:prefs.getAll().keySet())
                if(key.startsWith("reading:") || key.startsWith("error:") || key.startsWith("attempt:"))edit.remove(key);
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
    void catalog(List<Climate.Device> devices) { prefs.edit().putString("catalog", encodeDevices(devices)).commit(); }
    void selectDevices(List<Climate.Device> selected) {
        synchronized (RefreshWorker.LOCK) {
            LinkedHashMap<String,Climate.Device> unique = new LinkedHashMap<>();
            for (Climate.Device d : selected) unique.put(d.id(),d);
            SharedPreferences.Editor e = prefs.edit().putString("devices", encodeDevices(new ArrayList<>(unique.values())))
                .putString("generation",UUID.randomUUID().toString());
            for (Climate.Device old : devices()) if (!unique.containsKey(old.id())) {
                e.remove(key("reading",old.id())).remove(key("error",old.id())).remove(key("attempt",old.id()));
            }
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
            for (Climate.Device d : devices()) { if(ids.size() == capacity) break; ids.add(d.id()); }
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
    WidgetStyle widgetStyle(int id) { return WidgetStyle.decode(prefs.getString("style:"+id,"")); }
    void widgetStyle(int id, WidgetStyle style) { prefs.edit().putString("style:"+id,style.encode()).commit(); }
    void deleteWidget(int id) { prefs.edit().remove("widget:"+id).remove("style:"+id).commit(); }
    void restoreWidget(int oldId, int newId) {
        if (prefs.contains("style:"+oldId)) prefs.edit().putString("style:"+newId,prefs.getString("style:"+oldId,"")).remove("style:"+oldId).commit();
        if (prefs.contains("widget:"+oldId)) prefs.edit().putString("widget:"+newId,prefs.getString("widget:"+oldId,"[]")).remove("widget:"+oldId).commit();
    }
    void clear() { synchronized (RefreshWorker.LOCK) { prefs.edit().clear().commit(); } }
}
