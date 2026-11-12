package dev.thinqclimate.widget;

import dev.thinqclimate.core.Climate;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;

final class ThinQ {
    interface ConnectionFactory { HttpURLConnection open(URL url) throws IOException; }
    private final Climate.Config config;
    private final ConnectionFactory connections;
    ThinQ(Climate.Config config) { this(config, url -> (HttpURLConnection) url.openConnection()); }
    ThinQ(Climate.Config config, ConnectionFactory connections) { this.config = config; this.connections = connections; }
    /** Carries the ThinQ error code so callers can tell an unsupported model from a missing permission. */
    static final class ApiException extends IOException {
        final String code;
        ApiException(int status, String code) { super(Climate.error(status, code)); this.code = code; }
        boolean unsupported() { return Set.of("1219", "1220", "1221").contains(code); }
        boolean notAllowed() { return Set.of("1305", "1309").contains(code); }
    }
    private Object get(String device) throws Exception { return get(config.endpoint(device)); }
    private Object get(java.net.URI uri) throws Exception {
        HttpURLConnection c = connections.open(uri.toURL());
        c.setConnectTimeout(15000); c.setReadTimeout(15000);
        c.setInstanceFollowRedirects(false); // Never forward credentials to a redirect target.
        config.headers().forEach(c::setRequestProperty);
        try {
            int status = c.getResponseCode();
            InputStream stream = status >= 200 && status < 300 ? c.getInputStream() : c.getErrorStream();
            if (stream == null) throw new ApiException(status, "");
            String body;
            try (stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096]; int count;
                while ((count = stream.read(buffer)) != -1) {
                    if (out.size() + count > 1_048_576) throw new IOException("응답이 너무 큽니다.");
                    out.write(buffer, 0, count);
                }
                body = out.toString("UTF-8");
            }
            JSONObject envelope;
            try { envelope = new JSONObject(body); }
            catch (JSONException e) { throw new IOException("응답 형식이 올바르지 않습니다."); }
            JSONObject error = envelope.optJSONObject("error");
            if (status < 200 || status >= 300 || error != null)
                throw new ApiException(status, error == null ? "" : error.optString("code"));
            if (!envelope.has("response") || envelope.isNull("response")) throw new IOException("빈 응답입니다.");
            return plain(envelope.get("response"));
        } finally { c.disconnect(); }
    }
    static Object plain(Object value) throws JSONException {
        if (value instanceof JSONObject o) {
            Map<String,Object> m = new HashMap<>();
            Iterator<String> keys = o.keys();
            while (keys.hasNext()) { String key = keys.next(); m.put(key, plain(o.get(key))); }
            return m;
        }
        if (value instanceof JSONArray a) {
            List<Object> l = new ArrayList<>(); for (int i=0; i<a.length(); i++) l.add(plain(a.get(i))); return l;
        }
        return value == JSONObject.NULL ? null : value;
    }
    List<Climate.Device> devices() throws Exception { return Climate.devices(get((String) null)); }
    /** The device's raw state as plain maps and lists, interpreted later by Home.parse. */
    Object state(String id) throws Exception { return get(id); }
    /** Electricity use for the window; the previous period is optional. */
    dev.thinqclimate.core.Energy.Usage energy(String id, dev.thinqclimate.core.Energy.Window w) throws Exception {
        Integer current = dev.thinqclimate.core.Energy.sum(get(config.energyEndpoint(id, w.currentStart(), w.currentEnd())));
        if (current == null) throw new IOException("응답 형식이 올바르지 않습니다.");
        Integer previous = null;
        try { previous = dev.thinqclimate.core.Energy.sum(get(config.energyEndpoint(id, w.previousStart(), w.previousEnd()))); } catch (IOException ignored) { /* Comparison is optional. */ }
        return new dev.thinqclimate.core.Energy.Usage(current, previous, w.label(), w.compareLabel(), System.currentTimeMillis());
    }
    static String json(Object plain) { return String.valueOf(JSONObject.wrap(plain)); }
    static Object parseJson(String text) throws JSONException { return plain(new JSONTokener(text).nextValue()); }
    Climate.Reading reading(String id) throws Exception {
        Object state = get(id);
        if (!(state instanceof Map<?,?> m)) throw new IOException("상태 응답 형식이 올바르지 않습니다.");
        return Climate.reading(m, System.currentTimeMillis());
    }
}
