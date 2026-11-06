package dev.thinqclimate.widget;

import android.content.*;
import android.security.keystore.*;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import org.json.*;
import dev.thinqclimate.core.Climate;

/** All credentials encrypted with a non-exportable Android Keystore key; backups disabled. */
final class Store {
    private final SharedPreferences prefs;
    Store(Context c) { prefs = c.getSharedPreferences("climate", Context.MODE_PRIVATE); }
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
        JSONObject o = new JSONObject().put("token",c.token()).put("country",c.country()).put("region",c.region()).put("client",c.clientId());
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key());
        byte[] encrypted = cipher.doFinal(o.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (!prefs.edit().putString("secret", Base64.encodeToString(encrypted,Base64.NO_WRAP))
            .putString("iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)).commit()) throw new java.io.IOException("설정 저장 실패");
    }
    Climate.Config config() throws Exception {
        if (!prefs.contains("secret")) return null;
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(prefs.getString("iv",""),Base64.NO_WRAP)));
        String json = new String(cipher.doFinal(Base64.decode(prefs.getString("secret",""),Base64.NO_WRAP)),java.nio.charset.StandardCharsets.UTF_8);
        JSONObject o = new JSONObject(json);
        return new Climate.Config(o.getString("token"),o.getString("country"),o.getString("region"),o.getString("client"));
    }
    String generation() { return prefs.getString("generation", ""); }
    String id() { return prefs.getString("device", ""); }
    String name() { return prefs.getString("name", "가습기"); }
    void select(Climate.Device d) { prefs.edit().remove("reading").remove("error").remove("attempt").putString("generation",java.util.UUID.randomUUID().toString()).putString("device",d.id()).putString("name",d.name()).commit(); }
    void reading(Climate.Reading r) throws Exception {
        JSONObject o = new JSONObject().put("t",r.temperature()).put("h",r.humidity()).put("at",r.fetchedAt());
        prefs.edit().putString("reading",o.toString()).remove("error").commit();
    }
    Climate.Reading reading() {
        try {
            JSONObject o = new JSONObject(prefs.getString("reading","{}"));
            if (!o.has("at")) return null;
            return new Climate.Reading(o.has("t") ? o.getDouble("t") : null, o.has("h") ? o.getDouble("h") : null,o.getLong("at"));
        } catch (JSONException e) { return null; }
    }
    long attempt() { return prefs.getLong("attempt",0); }
    void attempt(long now) { prefs.edit().putLong("attempt",now).commit(); }
    String error() { return prefs.getString("error", ""); }
    void error(String text) { prefs.edit().putString("error",text).commit(); }
    void clear() { prefs.edit().clear().commit(); }
}
