package dev.thinqclimate.widget;
import dev.thinqclimate.core.Climate;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Exercises the production HTTP adapter with deterministic connections, no LG credentials. */
public final class TransportContract {
    static int checks;
    static void check(boolean value,String name) {checks++;if(!value)throw new AssertionError(name);}
    static final Climate.Config CONFIG=new Climate.Config("test-only","KR","kic","test-client");
    static final class Fake extends HttpURLConnection {
        final String body;final int status;boolean closed;boolean readClosed;
        Fake(URL url,String body,int status) {super(url);this.body=body;this.status=status;}
        @Override public void disconnect() {closed=true;}
        @Override public boolean usingProxy() {return false;}
        @Override public void connect() {}
        @Override public int getResponseCode() {return status;}
        @Override public InputStream getInputStream() {return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)) { @Override public void close() throws IOException {readClosed=true;super.close();} };}
        @Override public InputStream getErrorStream() {return getInputStream();}
    }
    static void fails(String body,int status,String message) throws Exception {
        Fake f=new Fake(CONFIG.endpoint("test").toURL(),body,status);
        try {new ThinQ(CONFIG,u -> f).reading("test");throw new AssertionError("expected failure");}
        catch(IOException e) {check(e.getMessage().contains(message),"error category "+status);check(!e.getMessage().contains("test-only"),"no token in error");}
        check(f.closed,"connection closed on error");check(f.readClosed,"stream closed on error");
    }
    public static void main(String[] args) throws Exception {
        Fake f=new Fake(CONFIG.endpoint("test").toURL(),"{\"response\":{\"airQualitySensor\":{\"temperature\":23.4,\"humidity\":47},\"humidity\":{\"targetHumidity\":80}}}",200);
        var r=new ThinQ(CONFIG,u -> {check(u.getProtocol().equals("https"),"HTTPS");return f;}).reading("test");
        check(r.temperature()==23.4 && r.humidity()==47,"JSON sensor decoding");check(f.closed && f.readClosed,"closed success");
        check(f.getConnectTimeout()==15000 && f.getReadTimeout()==15000,"timeouts");check(!f.getInstanceFollowRedirects(),"redirect rejected");
        check(f.getRequestProperty("Authorization").equals("Bearer test-only"),"auth");check(f.getRequestProperty("x-country").equals("KR"),"country");check(f.getRequestProperty("x-client-id").equals("test-client"),"client id");check(f.getRequestProperty("x-service-phase").equals("OP"),"phase");check(f.getRequestProperty("x-message-id").length()==22,"message id");
        var list=new Fake(CONFIG.endpoint(null).toURL(),"{\"response\":[{\"deviceId\":\"one\",\"deviceInfo\":{\"deviceType\":\"DEVICE_HUMIDIFIER\",\"alias\":\"거실\"}},{\"deviceId\":\"two\",\"deviceInfo\":{\"deviceType\":\"DEVICE_WASHER\"}}]}",200);
        check(new ThinQ(CONFIG,u->list).devices().equals(List.of(new Climate.Device("one","거실"),new Climate.Device("two","세탁기","DEVICE_WASHER"))),"list decoding includes appliances with type-name fallback");
        for(String sensors:List.of("{}","{\"humidity\":null,\"temperature\":null}","{\"humidity\":true,\"temperature\":\"23\"}","{\"humidity\":101,\"temperature\":999}")) {
            var invalid=new Fake(CONFIG.endpoint("test").toURL(),"{\"response\":{\"airQualitySensor\":"+sensors+"}}",200);
            var missing=new ThinQ(CONFIG,u->invalid).reading("test");check(missing.temperature()==null && missing.humidity()==null,"invalid/missing sensors");
        }
        fails("{\"error\":{\"code\":\"1103\",\"message\":\"test-only\"}}",401,"인증");
        fails("{\"error\":{\"code\":\"1306\"}}",200,"한도");
        fails("{\"error\":{\"code\":\"1222\"}}",400,"오프라인");
        fails("{}",403,"권한");fails("{}",429,"한도");fails("{}",302,"연결");fails("not json",200,"형식");fails("{\"response\":[]}",200,"형식");fails("{\"response\":null}",200,"빈");
        fails("x".repeat(1_048_577),200,"너무");
        System.out.println("HTTP adapter: "+checks+" assertions passed");
    }
}
