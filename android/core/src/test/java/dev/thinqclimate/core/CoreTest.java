package dev.thinqclimate.core;
import java.util.*;
public final class CoreTest {
    private static int checks;
    static void check(boolean condition, String name) { checks++;if(!condition)throw new AssertionError(name); }
    static void invalid(Runnable action) { try {action.run();throw new AssertionError("accepted invalid input");}catch(IllegalArgumentException expected) {checks++;} }
    /** Minimal JSON reader for the shared fixtures, so the core library stays dependency-free. */
    static final class Json {
        private final String s; private int i;
        Json(String s){this.s=s;}
        static Object parse(String s){Json j=new Json(s);Object v=j.value();j.ws();if(j.i!=s.length())throw new IllegalArgumentException("trailing JSON");return v;}
        private void ws(){while(i<s.length()&&Character.isWhitespace(s.charAt(i)))i++;}
        private Object value(){
            ws();char c=s.charAt(i);
            if(c=='{'){i++;Map<String,Object> m=new LinkedHashMap<>();ws();if(s.charAt(i)=='}'){i++;return m;}
                while(true){ws();String k=(String)value();ws();i++;m.put(k,value());ws();if(s.charAt(i++)=='}')return m;}}
            if(c=='['){i++;List<Object> l=new ArrayList<>();ws();if(s.charAt(i)==']'){i++;return l;}
                while(true){l.add(value());ws();if(s.charAt(i++)==']')return l;}}
            if(c=='"'){StringBuilder b=new StringBuilder();i++;while(s.charAt(i)!='"'){char x=s.charAt(i++);if(x=='\\'){char e=s.charAt(i++);
                if(e=='u'){b.append((char)Integer.parseInt(s.substring(i,i+4),16));i+=4;}else b.append(e=='n'?'\n':e=='t'?'\t':e);}else b.append(x);}i++;return b.toString();}
            if(s.startsWith("true",i)){i+=4;return true;} if(s.startsWith("false",i)){i+=5;return false;} if(s.startsWith("null",i)){i+=4;return null;}
            int start=i;while(i<s.length()&&"+-0123456789.eE".indexOf(s.charAt(i))>=0)i++;String n=s.substring(start,i);
            return n.contains(".")||n.contains("e")||n.contains("E")?(Object)Double.parseDouble(n):(Object)Integer.parseInt(n);
        }
    }
    static Object fixture(String name) throws java.io.IOException {
        return Json.parse(java.nio.file.Files.readString(java.nio.file.Path.of("../../fixtures/thinq", name)));
    }
    static void sharedFixtures() throws java.io.IOException {
        Map<?,?> expected=(Map<?,?>)fixture("expected.json");
        check(expected.size()>=19,"fixture count");
        for(Map.Entry<?,?> e:expected.entrySet()){
            String file=(String)e.getKey();Map<?,?> want=(Map<?,?>)e.getValue();
            Home.Status s=Home.parse(fixture(file),(String)want.get("type"),1_000_000_000L);
            check(s.summary().equals(want.get("summary")),file+" summary: "+s.summary());
            check(s.alerts().equals(want.get("alerts")),file+" alerts: "+s.alerts());
            List<List<Object>> cycles=new ArrayList<>();
            for(Home.Cycle c:s.cycles())cycles.add(Arrays.asList(c.state(),c.phase().name().toLowerCase(Locale.ROOT),c.remaining(),c.total()));
            check(cycles.equals(want.get("cycles")),file+" cycles: "+cycles);
            if(want.get("humidity") instanceof Number n)check(s.humidity()==n.doubleValue(),file+" humidity");
            if(want.get("temperature") instanceof Number n)check(s.temperature()==n.doubleValue(),file+" temperature");
            if(want.get("pm25") instanceof Number n)check(s.pm25()==n.doubleValue(),file+" pm25");
            if(want.get("airLevel") instanceof String l)check(l.equals(s.airLevel()),file+" air level");
            if(want.get("power") instanceof Boolean b)check(b.equals(s.power()),file+" power");
        }
        Home.Status running=Home.parse(fixture("running_washer.json"),"DEVICE_WASHER",2_000_000L);
        check(running.endsAt(running.primaryCycle())==2_000_000L+1_380_000L,"countdown end time");
        Home.Status paused=Home.parse(fixture("paused_styler.json"),"DEVICE_STYLER",2_000_000L);
        check(paused.endsAt(paused.primaryCycle())==null,"paused cycles do not count down");
        check(Home.duration(1).equals("1분")&&Home.duration(61).equals("2분")&&Home.duration(3600).equals("1시간")&&Home.duration(3900).equals("1시간 5분"),"durations");
        for(String st:List.of("RINSING","INITIAL","FIRMWARE","DETECTING","RUNNING","END","PAUSE","SOAKING","RESERVED","SPINNING","POWER_OFF","ERROR","STEAM_SOFTENING","FROZEN_PREVENT_INITIAL","FROZEN_PREVENT_RUNNING","FROZEN_PREVENT_PAUSE","DISPENSING","RINSE_HOLD","PREWASH","DETERGENT_AMOUNT","ADD_DRAIN","REFRESHING","DRYING","COOLING","WRINKLE_CARE","DONE","CLEANING","PREFERENCE","PREHEATING","PAUSED","COOKING_IN_PROGRESS","PREHEATING_IS_DONE","COMPLETE","STAY","STERILIZE","RUNNING_END","PRESTEAM","NIGHT_DRY","SLEEP","STEAM","POWER_FAIL","CANCEL"))
            check(Home.phase(st)!=null&&!Home.stateLabel("DEVICE_WASHER",st).equals("작동 중"),"known state "+st);
    }
    static void energyFixtures() throws java.io.IOException {
        Map<?,?> e=(Map<?,?>)fixture("energy_expected.json");
        for(Object o:(List<?>)e.get("windows")){Map<?,?> row=(Map<?,?>)o;String d=(String)row.get("today");
            Energy.Window w=Energy.window(java.time.LocalDate.parse(d,java.time.format.DateTimeFormatter.BASIC_ISO_DATE));
            check(List.of(w.currentStart(),w.currentEnd()).equals(row.get("current"))&&List.of(w.previousStart(),w.previousEnd()).equals(row.get("previous")),"energy window "+d+": "+w);
            check(w.label().equals(row.get("label"))&&w.compareLabel().equals(row.get("compare")),"energy labels "+d);}
        for(Object o:(List<?>)e.get("comparisons")){Map<?,?> row=(Map<?,?>)o;
            String text=Energy.comparison((Integer)row.get("current"),(Integer)row.get("previous"),(String)row.get("compare"));
            check(Objects.equals(text,row.get("text")),"energy comparison "+row+": "+text);}
        for(Object o:(List<?>)e.get("kwh")){List<?> pair=(List<?>)o;check(Energy.kWh((Integer)pair.get(0)).equals(pair.get(1)),"kWh "+pair);}
        check(Energy.sum(Map.of("result",Map.of("dataList",List.of(Map.of("energyUsage",981),Map.of("energyUsage",true),Map.of("energyUsage",944)))))==1925,"energy sum");
        check(Energy.sum(Map.of("result",Map.of()))==null,"energy sum shape");
        check(Climate.error(401,"1309").contains("허용되지 않은")&&Climate.error(406,"1221").contains("지원하지 않")&&Climate.error(401,"").contains("인증"),"energy errors");
        var c=new Climate.Config("p","KR","kic","test");
        check(c.energyEndpoint("a/b?","20261101","20261111").toString().equals("https://api-kic.lgthinq.com/devices/energy/a%2Fb%3F/usage?property=energyUsage&period=DAILY&startDate=20261101&endDate=20261111"),"energy URL");
    }
    public static void main(String[] args) throws Exception {
        sharedFixtures();
        energyFixtures();
        var selected=List.of(new Climate.Device("fan","존 아마","DEVICE_AIR_PURIFIER_FAN"),new Climate.Device("hum","가습기"),new Climate.Device("gone","삭제됨","DEVICE_WASHER"));
        var found=List.of(new Climate.Device("new","새 기기","DEVICE_DRYER"),new Climate.Device("hum","하이드로타워"),new Climate.Device("fan","공기청정기","DEVICE_AIR_PURIFIER_FAN"));
        check(Climate.merge(selected,found).equals(List.of(new Climate.Device("fan","공기청정기","DEVICE_AIR_PURIFIER_FAN"),new Climate.Device("hum","하이드로타워"))),"renamed devices keep selection and order");
        check(Climate.merge(selected,List.of()).equals(selected),"empty answer keeps selection");
        var c = new Climate.Config(" test-pat "," kr ","kic","climate-test");
        check(Climate.Comfort.of(null)==Climate.Comfort.UNKNOWN,"comfort unknown");
        check(Climate.Comfort.of(39.9)==Climate.Comfort.DRY,"comfort dry");
        check(Climate.Comfort.of(40.0)==Climate.Comfort.GOOD&&Climate.Comfort.of(60.0)==Climate.Comfort.GOOD,"comfort bounds");
        check(Climate.Comfort.of(60.1)==Climate.Comfort.HUMID,"comfort humid");
        check(Climate.average(Arrays.asList(null,null))==null,"average of nothing");
        check(Climate.average(Arrays.asList(40.0,null,60.0))==50,"average skips missing values");
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
        check(devices.size()==3 && devices.get(0).name().equals("거실") && devices.get(1).name().equals("제습기") && devices.get(2).name().equals("가습기"),"every device type with aliases and type-name fallback");
        var mixed=Climate.devices(List.of(
            Map.of("deviceId","purifier","deviceInfo",Map.of("deviceType","DEVICE_AIR_PURIFIER","alias","거실 공기")),
            Map.of("deviceId","fan","deviceInfo",Map.of("deviceType","DEVICE_AIR_PURIFIER_FAN")),
            Map.of("deviceId","purifier","deviceInfo",Map.of("deviceType","DEVICE_AIR_PURIFIER")),
            Map.of("deviceId","washer","deviceInfo",Map.of("deviceType","DEVICE_WASHER"))));
        check(mixed.size()==3 && mixed.get(2).typeName().equals("세탁기"),"appliances accepted, duplicates rejected");
        check(mixed.get(0).typeName().equals("공기청정기") && mixed.get(0).name().equals("거실 공기"),"purifier name and type");
        check(mixed.get(1).typeName().equals("공기청정 팬"),"fan type");
        check(Climate.reading(Map.of("airQualitySensor",Map.of("humidity",43)),now).temperature()==null,"partial purifier sensors");
        invalid(() -> Climate.devices(Map.of()));
        check(Climate.error(200,"1103").contains("인증"),"API auth error");check(Climate.error(429,"").contains("한도"),"rate limit");check(Climate.error(200,"1306").contains("한도"),"API rate limit");check(Climate.error(200,"1222").contains("오프라인"),"offline");check(Climate.error(403,"").contains("접근"),"forbidden");
        System.out.println("Core: "+checks+" assertions passed");
    }
}
