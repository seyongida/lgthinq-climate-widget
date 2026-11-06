package dev.thinqclimate.widget;

import android.app.*;
import android.appwidget.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import dev.thinqclimate.core.Climate;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private LinearLayout content, deviceList;
    private TextView connection,headline,averageHumidity,averageTemperature;
    private LinearLayout hero;
    private Ui.Gauge heroGauge;
    private ScrollView scroll;
    private Store store;
    private boolean busy;
    private String snapshot="";
    private final Runnable poll=new Runnable() { @Override public void run() { if(!isDestroyed()) { updateCards();handler.postDelayed(this,1500); } } };
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); store=new Store(this);
        boolean newBuild=false;
        String bootError=null;
        try { newBuild=store.bootstrap(); } catch(Exception e) { bootError="연결 설정을 확인해 주세요"; }
        LinearLayout outer=Ui.column(this); Ui.window(this,outer);
        scroll=new ScrollView(this);scroll.setClipToPadding(false);scroll.setClipChildren(false);scroll.setFillViewport(true);
        content=Ui.column(this);content.setPadding(Ui.dp(this,20),Ui.dp(this,10),Ui.dp(this,20),Ui.dp(this,36));
        scroll.addView(content);outer.addView(scroll);
        LinearLayout top=Ui.row(this);
        top.addView(Ui.logo(this,38));top.addView(new View(this),new LinearLayout.LayoutParams(Ui.dp(this,10),1));
        LinearLayout brand=Ui.column(this);brand.addView(Ui.text(this,"Climate Widget",17,Ui.INK,true));brand.addView(Ui.text(this,"for ThinQ",12,Ui.MUTED,false));Ui.weighted(top,brand);
        ImageView gear=Ui.icon(this,R.drawable.ic_settings,Ui.INK,Color.WHITE,44,.52f,false);gear.setContentDescription("연결 설정");gear.setOnClickListener(v->settings());top.addView(gear);content.addView(top);
        Ui.gap(content,18);
        LinearLayout heroCard=Ui.column(this);heroCard.setPadding(Ui.dp(this,22),Ui.dp(this,22),Ui.dp(this,22),Ui.dp(this,22));heroCard.setClipToOutline(true);heroCard.setElevation(Ui.dp(this,6));hero=heroCard;
        heroCard.addView(Ui.text(this,"지금 우리 집은",15,0xd9ffffff,false));Ui.gap(heroCard,4);
        headline=Ui.text(this,"",31,Color.WHITE,true);heroCard.addView(headline);Ui.gap(heroCard,14);
        LinearLayout numbers=Ui.row(this);numbers.setGravity(android.view.Gravity.BOTTOM);
        LinearLayout humidityColumn=Ui.column(this);averageHumidity=Ui.text(this,"—",68,Color.WHITE,true);humidityColumn.addView(averageHumidity);humidityColumn.addView(Ui.text(this,"평균 습도",12,0xd9ffffff,false));numbers.addView(humidityColumn);
        numbers.addView(new View(this),new LinearLayout.LayoutParams(Ui.dp(this,26),1));
        LinearLayout temperatureColumn=Ui.column(this);averageTemperature=Ui.text(this,"—",38,Color.WHITE,true);temperatureColumn.addView(averageTemperature);temperatureColumn.addView(Ui.text(this,"평균 온도",12,0xd9ffffff,false));numbers.addView(temperatureColumn);
        heroCard.addView(numbers);Ui.gap(heroCard,14);
        heroGauge=new Ui.Gauge(this,0x38ffffff,0x6bffffff,Color.WHITE);heroCard.addView(heroGauge);Ui.gap(heroCard,10);
        connection=Ui.text(this,"기기를 연결하고 있습니다",12,0xd9ffffff,false);heroCard.addView(connection);
        content.addView(heroCard);Ui.gap(content,30);
        LinearLayout controls=Ui.row(this);Ui.weighted(controls,Ui.text(this,"나의 기기",21,Ui.INK,true));
        controls.addView(Ui.pill(this,"기기 선택",v->discover(true,false)));content.addView(controls);
        Ui.gap(content,14);deviceList=Ui.column(this);content.addView(deviceList);
        Button refresh=Ui.button(this,"↻  센서값 새로고침",true,v->{RefreshWorker.refresh(this);Toast.makeText(this,"갱신 요청 · 1분 이내 요청은 합쳐집니다",Toast.LENGTH_SHORT).show();});
        content.addView(refresh);Ui.gap(content,34);
        content.addView(Ui.text(this,"홈 화면 위젯",21,Ui.INK,true));Ui.gap(content,4);
        content.addView(Ui.text(this,"크기를 고르고, 위젯을 눌러 기기와 색상을 설정하세요",14,Ui.MUTED,false));Ui.gap(content,16);
        LinearLayout sizes=Ui.row(this);
        Ui.weighted(sizes,Ui.sizeTile(this,"1 × 1","기기 1대",1,1,v->pin(ClimateWidget.class)));
        sizes.addView(new View(this),new LinearLayout.LayoutParams(Ui.dp(this,10),1));
        Ui.weighted(sizes,Ui.sizeTile(this,"1 × 2","세로 2대",1,2,v->pin(PairClimateWidget.class)));
        sizes.addView(new View(this),new LinearLayout.LayoutParams(Ui.dp(this,10),1));
        Ui.weighted(sizes,Ui.sizeTile(this,"2 × 1","가로 2대",2,1,v->pin(HorizontalClimateWidget.class)));
        content.addView(sizes);Ui.gap(content,20);
        content.addView(Ui.text(this,"30분마다 갱신을 요청하며 절전 상태에서는 늦어질 수 있어요. 지원하지 않는 값은 —, 90분 이상 지난 값은 ‘오래됨’으로 표시합니다. 쾌적 구간은 습도 40~60%를 기준으로 한 안내입니다.",12,Ui.MUTED,false));
        updateCards();
        if(bootError!=null) connection.setText(bootError);
        try {
            if(store.config()!=null) {
                RefreshWorker.schedule(this);
                if(newBuild || store.catalog().isEmpty()) discover(false,newBuild || store.devices().isEmpty());
                else RefreshWorker.refresh(this);
            } else {connection.setText("연결 설정을 시작해 주세요");onboarding=true;if(saved==null)settings();}
        } catch(Exception e) { connection.setText("연결 설정을 확인해 주세요"); }
    }
    private String signature() {
        StringBuilder b=new StringBuilder(store.generation());
        for(Climate.Device d:store.devices()) { Climate.Reading r=store.reading(d.id());b.append(d.id()).append(r).append(r!=null&&r.stale(System.currentTimeMillis())).append(store.error(d.id())); }
        return b.toString();
    }
    private void updateCards() {
        if(deviceList==null) return;
        String signature=signature();if(signature.equals(snapshot))return;snapshot=signature;
        deviceList.removeAllViews();List<Climate.Device> devices=store.devices();
        updateHero(devices);
        if(devices.isEmpty()) {
            LinearLayout empty=Ui.card(this,Color.WHITE);empty.addView(Ui.text(this,"아직 선택한 기기가 없어요",17,Ui.INK,true));Ui.gap(empty,8);
            empty.addView(Ui.text(this,"‘기기 선택’에서 가습기와 공기청정기를 여러 대 고를 수 있어요.",14,Ui.MUTED,false));deviceList.addView(empty);Ui.gap(deviceList,14);
        }
        for(Climate.Device d:devices) {
            Climate.Reading r=store.reading(d.id());String error=store.error(d.id());
            Double humidity=r==null?null:r.humidity();Climate.Comfort comfort=Climate.Comfort.of(humidity);
            LinearLayout card=Ui.card(this,Color.WHITE);LinearLayout title=Ui.row(this);
            title.addView(Ui.icon(this,d.type().equals("DEVICE_HUMIDIFIER")?R.drawable.ic_drop:R.drawable.ic_air,Ui.GREEN,Ui.TINT,42,.5f,false));title.addView(new View(this),new LinearLayout.LayoutParams(Ui.dp(this,12),1));
            LinearLayout names=Ui.column(this);TextView deviceTitle=Ui.text(this,d.name(),17,Ui.INK,true);deviceTitle.setMaxLines(1);deviceTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);names.addView(deviceTitle);names.addView(Ui.text(this,d.typeName(),12,Ui.MUTED,false));Ui.weighted(title,names);
            if(comfort!=Climate.Comfort.UNKNOWN) {
                boolean good=comfort==Climate.Comfort.GOOD;TextView badge=Ui.text(this,comfort.label(),12,good?Ui.GREEN:Ui.WARNING,true);badge.setPadding(Ui.dp(this,10),Ui.dp(this,5),Ui.dp(this,10),Ui.dp(this,5));
                badge.setBackground(Ui.background(this,good?Ui.TINT:Color.rgb(250,236,222),14));title.addView(badge);
            }
            card.addView(title);Ui.gap(card,18);
            LinearLayout metrics=Ui.row(this);Ui.weighted(metrics,metric("습도",r==null?"—":ClimateWidget.compact(r.humidity(),"%"),Ui.GREEN));
            Ui.weighted(metrics,metric("온도",r==null?"—":ClimateWidget.compact(r.temperature(),"°"),Ui.INK));card.addView(metrics);
            if(humidity!=null){Ui.gap(card,14);Ui.Gauge gauge=new Ui.Gauge(this,Ui.LINE,Ui.alpha(Ui.GREEN,76),Ui.GREEN);gauge.humidity(humidity);card.addView(gauge);}
            Ui.gap(card,12);
            String status=r==null?"센서값을 기다리고 있어요":DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(r.fetchedAt()))+" 수신"+(r.stale(System.currentTimeMillis())?" · 오래됨":"");
            card.addView(Ui.text(this,error.isEmpty()?status:error+"\n"+status,12,error.isEmpty()?Ui.MUTED:Ui.WARNING,false));
            if(r!=null && r.temperature()==null && r.humidity()==null) {Ui.gap(card,4);card.addView(Ui.text(this,"이 모델은 현재 온도·습도를 제공하지 않습니다",12,Ui.MUTED,false));}
            deviceList.addView(card);Ui.gap(deviceList,14);
        }
    }
    private void updateHero(List<Climate.Device> devices) {
        long now=System.currentTimeMillis();Long latest=null;List<Double> humidities=new ArrayList<>(),temperatures=new ArrayList<>();
        for(Climate.Device d:devices) {
            Climate.Reading r=store.reading(d.id());if(r==null||r.stale(now))continue;
            humidities.add(r.humidity());temperatures.add(r.temperature());latest=latest==null?r.fetchedAt():Math.max(latest,r.fetchedAt());
        }
        Double humidity=Climate.average(humidities),temperature=Climate.average(temperatures);Climate.Comfort comfort=Climate.Comfort.of(humidity);
        hero.setBackground(Ui.hero(this,comfort));headline.setText(comfort.headline());
        averageHumidity.setText(humidity==null?"—":String.format(Locale.KOREA,"%.0f%%",humidity));
        averageTemperature.setText(temperature==null?"—":String.format(Locale.KOREA,"%.1f°",temperature));
        heroGauge.humidity(humidity);
        if(!busy) {
            String text=devices.isEmpty()?"표시할 기기를 선택해 주세요":devices.size()+"개의 기기를 한곳에서";
            connection.setText(latest==null?text:text+" · "+DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(latest))+" 수신");
        }
    }
    private LinearLayout metric(String label,String value,int color) {
        LinearLayout l=Ui.column(this);l.addView(Ui.text(this,label,12,Ui.MUTED,false));Ui.gap(l,2);TextView t=Ui.text(this,value,32,color,true);
        t.setSingleLine(true);t.setAutoSizeTextTypeUniformWithConfiguration(18,32,1,android.util.TypedValue.COMPLEX_UNIT_SP);l.addView(t);return l;
    }
    private void discover(boolean choose,boolean selectAll) {
        if(busy)return;
        final Climate.Config cfg;
        try {cfg=store.config();if(cfg==null){settings();return;}}catch(Exception e){settings();return;}
        busy=true;connection.setText("우리 집 기기를 찾고 있어요…");
        final String generation=store.generation();
        executor.execute(()->{
            try {
                List<Climate.Device> devices=new ThinQ(cfg).devices();
                runOnUiThread(()->{
                    if(isDestroyed())return;busy=false;
                    if(!generation.equals(store.generation())) {snapshot="";updateCards();return;}
                    store.catalog(devices);
                    if(devices.isEmpty()){connection.setText("지원 기기가 없습니다 · 계정과 권한을 확인해 주세요");return;}
                    if(choose) chooseDevices(devices);
                    else {
                        Set<String> old=new HashSet<>();for(Climate.Device d:store.devices())old.add(d.id());
                        List<Climate.Device> next=new ArrayList<>();for(Climate.Device d:devices)if(selectAll||old.contains(d.id()))next.add(d);
                        store.selectDevices(next);selected();
                    }
                });
            }catch(Exception e){runOnUiThread(()->{if(!isDestroyed()){busy=false;connection.setText(e instanceof java.io.IOException?e.getMessage():"기기를 찾지 못했습니다 · 다시 시도해 주세요");}});}
        });
    }
    private void chooseDevices(List<Climate.Device> devices) {
        Set<String> selected=new HashSet<>();for(Climate.Device d:store.devices())selected.add(d.id());
        boolean[] checked=new boolean[devices.size()];String[] labels=new String[devices.size()];
        for(int i=0;i<devices.size();i++){Climate.Device d=devices.get(i);checked[i]=selected.contains(d.id());labels[i]=d.name()+" · "+d.typeName();}
        new AlertDialog.Builder(this).setTitle("함께 볼 기기 선택")
            .setMultiChoiceItems(labels,checked,(dialog,index,value)->checked[index]=value)
            .setPositiveButton("선택 완료",(dialog,which)->{List<Climate.Device> next=new ArrayList<>();for(int i=0;i<checked.length;i++)if(checked[i])next.add(devices.get(i));store.selectDevices(next);selected();})
            .setNegativeButton("취소",(d,w)->{snapshot="";updateCards();}).setOnCancelListener(d->{snapshot="";updateCards();}).show();
    }
    private void selected(){snapshot="";updateCards();ClimateWidget.renderAll(this);RefreshWorker.schedule(this);RefreshWorker.refresh(this);}
    private void pin(Class<? extends ClimateWidget> provider) {
        if(store.devices().isEmpty()){Toast.makeText(this,"먼저 표시할 기기를 선택해 주세요",Toast.LENGTH_SHORT).show();return;}
        AppWidgetManager manager=AppWidgetManager.getInstance(this);
        if(manager.isRequestPinAppWidgetSupported()) manager.requestPinAppWidget(new ComponentName(this,provider),null,null);
        else new AlertDialog.Builder(this).setTitle("위젯 추가").setMessage("홈 화면을 길게 누른 뒤 위젯 → Climate Widget에서 크기를 고르세요.").setPositiveButton("확인",null).show();
    }
    private static final int CREDENTIALS=41;
    private boolean onboarding;
    private void settings() { startActivityForResult(new Intent(this,CredentialsActivity.class),CREDENTIALS); }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request!=CREDENTIALS)return;
        if(result==RESULT_OK){onboarding=false;selected();}
        else if(onboarding){finish();}
    }
    @Override protected void onResume(){super.onResume();snapshot="";updateCards();handler.post(poll);}
    @Override protected void onPause(){handler.removeCallbacks(poll);super.onPause();}
    @Override protected void onDestroy(){handler.removeCallbacks(poll);executor.shutdownNow();super.onDestroy();}
}
