package dev.thinqclimate.core;
import java.util.*;
public final class CoreTest {
    private static int checks;
    static void check(boolean condition, String name) { checks++;if(!condition)throw new AssertionError(name); }
    static void invalid(Runnable action) { try {action.run();throw new AssertionError("accepted invalid input");}catch(IllegalArgumentException expected) {checks++;} }
    public static void main(String[] args) {
        var c = new Climate.Config(" test-pat "," kr ","kic","climate-test");
        check(c.country().equals("KR"),"country normalization");
        check(c.endpoint(null).toString().equals("https://api-kic.lgthinq.com/devices"),"list URL");
        check(c.endpoint("a/b ?#").toString().endsWith("a%2Fb%20%3F%23/state"),"ID encoding");
        for(String region: List.of("kic","aic","eic")) check(new Climate.Config("p","US",region,"test").endpoint(null).getHost().equals("api-"+region+".lgthinq.com"),"region routing");
        check(!c.toString().contains("test-pat"),"credential redaction");
        for(String token: List.of("", " ", "x\r\ninjected: true", "x\ty", "x y", "xé")) invalid(() -> new Climate.Config(token,"KR","kic","test"));
        for(String country:List.of("K","KRR","K1","KR\nA")) invalid(() -> new Climate.Config("p",country,"kic","test"));
        invalid(() -> new Climate.Config("p","KR","evil.com","test"));
        invalid(() -> new Climate.Config("p","KR","kic","x\r\ny"));
        var seen = new HashSet<String>();
        for(int i=0;i<1000;i++) { var h=c.headers();String id=h.get("x-message-id");check(id.matches("[A-Za-z0-9_-]{22}"),"message id format");check(seen.add(id),"unique message id");check(h.get("Authorization").equals("Bearer test-pat"),"PAT header"); }
        var now=100000L;
        var r=Climate.reading(Map.of("airQualitySensor",Map.of("temperature",23.4,"humidity",47)),now);
        check(r.temperature()==23.4 && r.humidity()==47,"current sensors");
        check(!r.stale(now+Climate.STALE_MS-1),"fresh threshold");check(r.stale(now+Climate.STALE_MS),"stale threshold");check(r.stale(now-1),"future timestamp");
        check(r.humidityText().equals("47.0%"),"format");
        check(Climate.reading(Map.of("humidity",Map.of("targetHumidity",80)),now).humidity()==null,"never use target humidity");
        check(Climate.reading(Map.of("airQualitySensor","broken"),now).temperature()==null,"wrong resource type");
        for(Object raw: List.of("47",true,Double.NaN,Double.POSITIVE_INFINITY,101,-1)) check(Climate.sensor(raw,0,100)==null,"invalid humidity "+raw);
        check(Climate.sensor(null,0,100)==null,"null");
        for(int i=0;i<=100;i++) check(Climate.sensor(i,0,100).equals((double)i),"valid humidity "+i);
        check(Climate.sensor(-50,-50,80)==-50 && Climate.sensor(80,-50,80)==80,"temperature bounds");
        var devices=Climate.devices(List.of(Map.of("deviceId","a","deviceInfo",Map.of("deviceType","DEVICE_HUMIDIFIER","alias","거실")),Map.of("deviceId","b","deviceInfo",Map.of("deviceType","DEVICE_DEHUMIDIFIER")),Map.of("deviceId","c","deviceInfo",Map.of("deviceType","DEVICE_HUMIDIFIER")),Map.of("deviceInfo",Map.of("deviceType","DEVICE_HUMIDIFIER"))));
        check(devices.size()==2 && devices.get(0).name().equals("거실") && devices.get(1).name().equals("가습기"),"humidifier filter and alias");
        var mixed=Climate.devices(List.of(
            Map.of("deviceId","purifier","deviceInfo",Map.of("deviceType","DEVICE_AIR_PURIFIER","alias","거실 공기")),
            Map.of("deviceId","fan","deviceInfo",Map.of("deviceType","DEVICE_AIR_PURIFIER_FAN")),
            Map.of("deviceId","purifier","deviceInfo",Map.of("deviceType","DEVICE_AIR_PURIFIER")),
            Map.of("deviceId","washer","deviceInfo",Map.of("deviceType","DEVICE_WASHER"))));
        check(mixed.size()==2,"purifier and purifier fan accepted, duplicates rejected");
        check(mixed.get(0).typeName().equals("공기청정기") && mixed.get(0).name().equals("거실 공기"),"purifier name and type");
        check(mixed.get(1).typeName().equals("공기청정 팬"),"fan type");
        check(Climate.reading(Map.of("airQualitySensor",Map.of("humidity",43)),now).temperature()==null,"partial purifier sensors");
        invalid(() -> Climate.devices(Map.of()));
        check(Climate.error(200,"1103").contains("인증"),"API auth error");check(Climate.error(429,"").contains("한도"),"rate limit");check(Climate.error(200,"1306").contains("한도"),"API rate limit");check(Climate.error(200,"1222").contains("오프라인"),"offline");check(Climate.error(403,"").contains("접근"),"forbidden");
        System.out.println("Core: "+checks+" assertions passed");
    }
}
