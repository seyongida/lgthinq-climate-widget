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
    private Object get(String device) throws Exception {
        HttpURLConnection c = connections.open(config.endpoint(device).toURL());
        c.setConnectTimeout(15000); c.setReadTimeout(15000);
        c.setInstanceFollowRedirects(false); // Never forward credentials to a redirect target.
        config.headers().forEach(c::setRequestProperty);
        try {
            int status = c.getResponseCode();
            InputStream stream = status >= 200 && status < 300 ? c.getInputStream() : c.getErrorStream();
            if (stream == null) throw new IOException(Climate.error(status, ""));
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
                throw new IOException(Climate.error(status, error == null ? "" : error.optString("code")));
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
    List<Climate.Device> devices() throws Exception { return Climate.devices(get(null)); }
    Climate.Reading reading(String id) throws Exception {
        Object state = get(id);
        if (!(state instanceof Map<?,?> m)) throw new IOException("상태 응답 형식이 올바르지 않습니다.");
        return Climate.reading(m, System.currentTimeMillis());
    }
}
