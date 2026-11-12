package dev.thinqclimate.widget;

import android.app.*;
import android.appwidget.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import dev.thinqclimate.core.Climate;
import dev.thinqclimate.core.Home;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private LinearLayout content, deviceList, cyclesSection, cyclesList, alertsSection, alertsList, climateSection, grid, energySection, energyCard;
    private TextView energyLabel;
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
        cyclesSection=Ui.column(this);cyclesSection.addView(Ui.text(this,"곧 끝나요",21,Ui.INK,true));Ui.gap(cyclesSection,12);
        cyclesList=Ui.column(this);cyclesSection.addView(cyclesList);Ui.gap(cyclesSection,16);content.addView(cyclesSection);
        alertsSection=Ui.column(this);alertsSection.addView(Ui.text(this,"확인해 주세요",21,Ui.INK,true));Ui.gap(alertsSection,12);
        alertsList=Ui.card(this,Color.WHITE);alertsList.setPadding(Ui.dp(this,16),Ui.dp(this,8),Ui.dp(this,16),Ui.dp(this,8));alertsSection.addView(alertsList);Ui.gap(alertsSection,30);content.addView(alertsSection);
        climateSection=Ui.column(this);climateSection.addView(Ui.text(this,"공기",21,Ui.INK,true));Ui.gap(climateSection,12);
        deviceList=Ui.column(this);climateSection.addView(deviceList);Ui.gap(climateSection,16);content.addView(climateSection);
        energySection=Ui.column(this);LinearLayout energyHeader=Ui.row(this);Ui.weighted(energyHeader,Ui.text(this,"전기 사용량",21,Ui.INK,true));
        energyLabel=Ui.text(this,"",13,Ui.MUTED,false);energyHeader.addView(energyLabel);energySection.addView(energyHeader);Ui.gap(energySection,12);
        energyCard=Ui.card(this,Color.WHITE);energySection.addView(energyCard);Ui.gap(energySection,30);content.addView(energySection);
        LinearLayout controls=Ui.row(this);Ui.weighted(controls,Ui.text(this,"모든 기기",21,Ui.INK,true));
        controls.addView(Ui.pill(this,"기기 선택",v->discover(true,false)));content.addView(controls);
        Ui.gap(content,12);grid=Ui.column(this);content.addView(grid);Ui.gap(content,6);
        Button refresh=Ui.button(this,"↻  지금 새로고침",true,v->{RefreshWorker.refresh(this);Toast.makeText(this,"갱신 요청 · 1분 이내 요청은 합쳐집니다",Toast.LENGTH_SHORT).show();});
        content.addView(refresh);Ui.gap(content,34);
        content.addView(Ui.text(this,"홈 화면 위젯",21,Ui.INK,true));Ui.gap(content,4);
        content.addView(Ui.text(this,"공기 위젯은 눌러서 기기와 색상을 바꿀 수 있어요",14,Ui.MUTED,false));Ui.gap(content,16);
        LinearLayout sizes=Ui.row(this);
        Ui.weighted(sizes,Ui.sizeTile(this,"1 × 1","기기 1대",1,1,v->pin(ClimateWidget.class)));
        sizes.addView(new View(this),new LinearLayout.LayoutParams(Ui.dp(this,10),1));
        Ui.weighted(sizes,Ui.sizeTile(this,"1 × 2","세로 2대",1,2,v->pin(PairClimateWidget.class)));
        sizes.addView(new View(this),new LinearLayout.LayoutParams(Ui.dp(this,10),1));
        Ui.weighted(sizes,Ui.sizeTile(this,"2 × 1","가로 2대",2,1,v->pin(HorizontalClimateWidget.class)));
        content.addView(sizes);Ui.gap(content,10);
        LinearLayout timer=Ui.row(this);Ui.weighted(timer,Ui.sizeTile(this,"가전 타이머","남은 시간 · 2 × 1",2,1,v->pin(CycleWidget.class)));content.addView(timer);Ui.gap(content,20);
        content.addView(Ui.text(this,"앱은 LG ThinQ의 상태를 읽기만 하고 기기를 조작하지 않습니다. 30분마다 갱신을 요청하며 절전 상태에서는 늦어질 수 있어요. 남은 시간은 마지막으로 받은 값으로 계산한 예상입니다. 쾌적 구간은 습도 40~60%를 기준으로 한 안내입니다.",12,Ui.MUTED,false));
        updateCards();
        if(bootError!=null) connection.setText(bootError);
        try {
            if(store.config()!=null) {
                RefreshWorker.schedule(this);
                // Older builds listed only climate devices; rediscover once so appliances appear too.
                if(newBuild || store.catalog().isEmpty() || !store.catalogIsCurrent()) discover(false,newBuild || store.devices().isEmpty() || !store.catalogIsCurrent());
                else RefreshWorker.refresh(this);
            } else {connection.setText("연결 설정을 시작해 주세요");onboarding=true;if(saved==null)settings();}
        } catch(Exception e) { connection.setText("연결 설정을 확인해 주세요"); }
    }
    private String signature() {
        StringBuilder b=new StringBuilder(store.generation());
        for(Climate.Device d:store.devices()) { Home.Status s=store.status(d.id());b.append(d.id()).append(s).append(store.error(d.id())).append(store.energy(d.id())); }
        return b.toString();
    }
    private void updateCards() {
        if(grid==null) return;
        String signature=signature();if(signature.equals(snapshot))return;snapshot=signature;
        List<Climate.Device> devices=store.devices(),climate=store.climateDevices();
        updateHero(climate,devices);
        List<CycleWidget.Item> cycles=CycleWidget.active(store);
        cyclesList.removeAllViews();cyclesSection.setVisibility(cycles.isEmpty()?View.GONE:View.VISIBLE);
        for(CycleWidget.Item item:cycles){cyclesList.addView(cycleCard(item));Ui.gap(cyclesList,12);}
        alertsList.removeAllViews();int alerts=0;
        for(Climate.Device d:devices){Home.Status s=store.status(d.id());if(s==null)continue;
            for(String a:s.alerts()){if(alerts++>0){View line=new View(this);line.setBackgroundColor(Ui.LINE);alertsList.addView(line,new LinearLayout.LayoutParams(-1,Ui.dp(this,1)));}alertsList.addView(alertRow(d,a));}}
        alertsSection.setVisibility(alerts==0?View.GONE:View.VISIBLE);
        // Climate devices that have reported a value; offline ones stay in the device grid only.
        List<Climate.Device> cards=new ArrayList<>();for(Climate.Device d:climate)if(store.reading(d.id())!=null)cards.add(d);
        deviceList.removeAllViews();climateSection.setVisibility(cards.isEmpty()?View.GONE:View.VISIBLE);
        for(Climate.Device d:cards){deviceList.addView(climateCard(d));Ui.gap(deviceList,14);}
        updateEnergy(devices);
        grid.removeAllViews();
        if(devices.isEmpty()) {
            LinearLayout empty=Ui.card(this,Color.WHITE);empty.addView(Ui.text(this,"아직 선택한 기기가 없어요",17,Ui.INK,true));Ui.gap(empty,8);
            empty.addView(Ui.text(this,"‘기기 선택’에서 앱과 위젯에 보여 줄 기기를 고르세요.",14,Ui.MUTED,false));grid.addView(empty);Ui.gap(grid,12);
        }
        for(int i=0;i<devices.size();i+=2){
            LinearLayout row=Ui.row(this);row.setGravity(android.view.Gravity.TOP);Ui.weighted(row,tile(devices.get(i)));
            row.addView(new View(this),new LinearLayout.LayoutParams(Ui.dp(this,12),1));
            if(i+1<devices.size())Ui.weighted(row,tile(devices.get(i+1)));else Ui.weighted(row,new View(this));
            grid.addView(row);Ui.gap(grid,12);
        }
        if(!cycles.isEmpty()&&store.liveUpdates()&&!CycleNotifier.permitted(this)&&!store.notificationAsked()&&android.os.Build.VERSION.SDK_INT>=33){
            store.notificationAsked(true);requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},42);
        }
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){super.onRequestPermissionsResult(code,permissions,results);if(code==42)CycleNotifier.sync(this);}
    private void updateEnergy(List<Climate.Device> devices) {
        List<Climate.Device> rows=new ArrayList<>();for(Climate.Device d:devices){dev.thinqclimate.core.Energy.Usage u=store.energy(d.id());if(u!=null&&u.current()>0)rows.add(d);}
        rows.sort((a,b)->Integer.compare(store.energy(b.id()).current(),store.energy(a.id()).current()));
        energySection.setVisibility(rows.isEmpty()?View.GONE:View.VISIBLE);energyCard.removeAllViews();if(rows.isEmpty())return;
        int total=0,largest=1;Integer previous=0;String compare="";
        for(Climate.Device d:rows){dev.thinqclimate.core.Energy.Usage u=store.energy(d.id());total+=u.current();largest=Math.max(largest,u.current());compare=u.compareLabel();
            previous=previous==null||u.previous()==null?null:previous+u.previous();energyLabel.setText(u.label());}
        energyCard.addView(Ui.text(this,dev.thinqclimate.core.Energy.kWh(total),34,Ui.INK,true));
        String text=dev.thinqclimate.core.Energy.comparison(total,previous,compare);
        if(text!=null){Ui.gap(energyCard,4);energyCard.addView(Ui.text(this,(previous>=total?"↘ ":"↗ ")+text,14,previous>=total?Ui.GREEN:Ui.WARNING,true));}
        Ui.gap(energyCard,16);
        for(Climate.Device d:rows){
            dev.thinqclimate.core.Energy.Usage u=store.energy(d.id());
            LinearLayout line=Ui.row(this);Ui.weighted(line,Ui.text(this,d.name(),14,Ui.INK,true));line.addView(Ui.text(this,dev.thinqclimate.core.Energy.kWh(u.current()),14,Ui.INK,true));energyCard.addView(line);Ui.gap(energyCard,6);
            ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(1000);bar.setProgress((int)Math.max(8,1000L*u.current()/largest));
            bar.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.GREEN));bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.LINE));
            energyCard.addView(bar,new LinearLayout.LayoutParams(-1,Ui.dp(this,6)));Ui.gap(energyCard,12);
        }
        energyCard.addView(Ui.text(this,"LG ThinQ가 집계한 기기별 전력량이에요. 요금은 계약 조건에 따라 달라 표시하지 않아요.",12,Ui.MUTED,false));
    }
    private View cycleCard(CycleWidget.Item item) {
        LinearLayout card=Ui.card(this,Color.WHITE);LinearLayout top=Ui.row(this);
        top.addView(Ui.icon(this,Ui.typeIcon(item.device().type()),Ui.GREEN,Ui.TINT,42,.5f,false));top.addView(new View(this),new LinearLayout.LayoutParams(Ui.dp(this,12),1));
        LinearLayout names=Ui.column(this);TextView title=Ui.text(this,item.title(),17,Ui.INK,true);title.setMaxLines(1);title.setEllipsize(android.text.TextUtils.TruncateAt.END);names.addView(title);
        names.addView(Ui.text(this,item.cycle().label(),13,item.cycle().phase()==Home.Phase.RUNNING?Ui.GREEN:Ui.MUTED,true));Ui.weighted(top,names);
        Long end=item.endsAt();long now=System.currentTimeMillis();
        if(end!=null&&end>now){
            Chronometer timer=new Chronometer(this);timer.setTextSize(28);timer.setTextColor(Ui.INK);timer.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            timer.setBase(android.os.SystemClock.elapsedRealtime()+(end-now));timer.setCountDown(true);timer.start();top.addView(timer);
        } else {
            Home.Cycle c=item.cycle();
            top.addView(Ui.text(this,end!=null?"곧 끝나요":c.phase()==Home.Phase.RESERVED?(c.startsIn()==null?"예약":Home.duration(c.startsIn())+" 후"):c.remaining()==null?c.label():Home.duration(c.remaining()),20,Ui.INK,true));
        }
        card.addView(top);Ui.gap(card,14);
        ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(1000);
        Integer total=item.cycle().total(),remaining=item.cycle().remaining();
        bar.setProgress(total==null||remaining==null||total<=0?0:(int)Math.max(0,Math.min(1000,1000L*(total-remaining)/total)));
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.GREEN));bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.LINE));
        card.addView(bar,new LinearLayout.LayoutParams(-1,Ui.dp(this,6)));Ui.gap(card,10);
        card.addView(Ui.text(this,Ui.cycleDetail(item.status(),item.cycle())+(end==null?"":" · 남은 시간은 예상이에요"),12,Ui.MUTED,false));
        return card;
    }
    private View alertRow(Climate.Device d,String text) {
        LinearLayout row=Ui.row(this);row.setPadding(0,Ui.dp(this,10),0,Ui.dp(this,10));
        row.addView(Ui.icon(this,Ui.typeIcon(d.type()),Ui.WARNING,Ui.WARM_TINT,40,.5f,false));row.addView(new View(this),new LinearLayout.LayoutParams(Ui.dp(this,12),1));
        LinearLayout texts=Ui.column(this);texts.addView(Ui.text(this,text,15,Ui.INK,true));texts.addView(Ui.text(this,d.name(),12,Ui.MUTED,false));Ui.weighted(row,texts);
        return row;
    }
    private View tile(Climate.Device d) {
        Home.Status s=store.status(d.id());String error=store.error(d.id());
        boolean offline=s==null&&!error.isEmpty(),attention=s!=null&&!s.alerts().isEmpty();
        LinearLayout tile=Ui.column(this);tile.setPadding(Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,14));tile.setBackground(Ui.background(this,Color.WHITE,22));
        tile.addView(Ui.icon(this,Ui.typeIcon(d.type()),offline?Ui.MUTED:attention?Ui.WARNING:Ui.GREEN,offline?Ui.LINE:attention?Ui.WARM_TINT:Ui.TINT,34,.5f,false));Ui.gap(tile,12);
        TextView name=Ui.text(this,d.name(),15,Ui.INK,true);name.setMaxLines(1);name.setEllipsize(android.text.TextUtils.TruncateAt.END);tile.addView(name);Ui.gap(tile,2);
        String line;
        if(s!=null){boolean fresh=System.currentTimeMillis()-s.fetchedAt()<Climate.STALE_MS;line=fresh?s.summary():s.summary()+" · "+Ui.time(s.fetchedAt())+" 기준";}
        else if(!error.isEmpty())line=error.contains("오프라인")?"꺼져 있거나 연결 안 됨":error;
        else line=d.typeName();
        TextView summary=Ui.text(this,line,12,offline?Ui.MUTED:Ui.INK,false);summary.setLines(2);summary.setEllipsize(android.text.TextUtils.TruncateAt.END);tile.addView(summary);
        tile.setAlpha(offline?.7f:1f);tile.setContentDescription(d.name()+", "+line);
        return tile;
    }
    private View climateCard(Climate.Device d) {
        Climate.Reading r=store.reading(d.id());String error=store.error(d.id());Home.Status s=store.status(d.id());
        Double humidity=r==null?null:r.humidity();Climate.Comfort comfort=Climate.Comfort.of(humidity);
        LinearLayout card=Ui.card(this,Color.WHITE);LinearLayout title=Ui.row(this);
        title.addView(Ui.icon(this,Ui.typeIcon(d.type()),Ui.GREEN,Ui.TINT,42,.5f,false));title.addView(new View(this),new LinearLayout.LayoutParams(Ui.dp(this,12),1));
        LinearLayout names=Ui.column(this);TextView deviceTitle=Ui.text(this,d.name(),17,Ui.INK,true);deviceTitle.setMaxLines(1);deviceTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);names.addView(deviceTitle);names.addView(Ui.text(this,d.typeName(),12,Ui.MUTED,false));Ui.weighted(title,names);
        if(comfort!=Climate.Comfort.UNKNOWN) {
            boolean good=comfort==Climate.Comfort.GOOD;TextView badge=Ui.text(this,comfort.label(),12,good?Ui.GREEN:Ui.WARNING,true);badge.setPadding(Ui.dp(this,10),Ui.dp(this,5),Ui.dp(this,10),Ui.dp(this,5));
            badge.setBackground(Ui.background(this,good?Ui.TINT:Ui.WARM_TINT,14));title.addView(badge);
        }
        card.addView(title);Ui.gap(card,18);
        LinearLayout metrics=Ui.row(this);Ui.weighted(metrics,metric("습도",r==null?"—":ClimateWidget.compact(r.humidity(),"%"),Ui.GREEN));
        Ui.weighted(metrics,metric("온도",r==null?"—":ClimateWidget.compact(r.temperature(),"°"),Ui.INK));card.addView(metrics);
        if(humidity!=null){Ui.gap(card,14);Ui.Gauge gauge=new Ui.Gauge(this,Ui.LINE,Ui.alpha(Ui.GREEN,76),Ui.GREEN);gauge.humidity(humidity);card.addView(gauge);}
        if(s!=null&&(s.pm25()!=null||s.airText()!=null)){Ui.gap(card,12);card.addView(Ui.text(this,(s.pm25()==null?"":"PM2.5 "+Math.round(s.pm25())+"㎍/㎥"+(s.airText()==null?"":" · "))+(s.airText()==null?"":s.airText()),13,Ui.INK,false));}
        Ui.gap(card,10);
        String status=r==null?"센서값을 기다리고 있어요":DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(r.fetchedAt()))+" 수신"+(r.stale(System.currentTimeMillis())?" · 오래됨":"");
        card.addView(Ui.text(this,error.isEmpty()?status:error+"\n"+status,12,error.isEmpty()?Ui.MUTED:Ui.WARNING,false));
        return card;
    }
    private void updateHero(List<Climate.Device> climate,List<Climate.Device> devices) {
        long now=System.currentTimeMillis();Long latest=null;List<Double> humidities=new ArrayList<>(),temperatures=new ArrayList<>();
        String worst=null;Double pm=null;List<String> order=List.of("VERY_BAD","BAD","NORMAL","GOOD");
        for(Climate.Device d:climate) {
            Climate.Reading r=store.reading(d.id());if(r==null||r.stale(now))continue;
            humidities.add(r.humidity());temperatures.add(r.temperature());latest=latest==null?r.fetchedAt():Math.max(latest,r.fetchedAt());
            Home.Status s=store.status(d.id());
            if(s!=null&&s.airLevel()!=null&&(worst==null||order.indexOf(s.airLevel())<order.indexOf(worst)))worst=s.airLevel();
            if(s!=null&&s.pm25()!=null)pm=pm==null?s.pm25():Math.max(pm,s.pm25());
        }
        Double humidity=Climate.average(humidities),temperature=Climate.average(temperatures);Climate.Comfort comfort=Climate.Comfort.of(humidity);
        hero.setBackground(Ui.hero(this,comfort));
        averageHumidity.setText(humidity==null?"—":String.format(Locale.KOREA,"%.0f%%",humidity));
        averageTemperature.setText(temperature==null?"—":String.format(Locale.KOREA,"%.1f°",temperature));
        heroGauge.humidity(humidity);
        View numbers=(View)averageHumidity.getParent().getParent();
        numbers.setVisibility(climate.isEmpty()?View.GONE:View.VISIBLE);heroGauge.setVisibility(climate.isEmpty()?View.GONE:View.VISIBLE);
        if(climate.isEmpty()) {
            List<CycleWidget.Item> cycles=CycleWidget.active(store);int alertCount=0;for(Climate.Device d:devices){Home.Status s=store.status(d.id());if(s!=null)alertCount+=s.alerts().size();}
            headline.setText(alertCount>0?"확인할 일이 있어요":"모두 평온해요");
        } else headline.setText(comfort.headline());
        if(!busy) {
            String text=devices.isEmpty()?"표시할 기기를 선택해 주세요":"기기 "+devices.size()+"대";
            if(worst!=null)text="공기질 "+Home.AIR_LEVELS.get(worst)+(pm==null?"":" · PM2.5 최고 "+Math.round(pm)+"㎍/㎥")+"\n"+text;
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
    private void selected(){snapshot="";updateCards();ClimateWidget.renderAll(this);CycleWidget.renderAll(this);RefreshWorker.schedule(this);RefreshWorker.refresh(this);}
    private void pin(Class<? extends android.appwidget.AppWidgetProvider> provider) {
        if(provider!=CycleWidget.class&&store.climateDevices().isEmpty()){Toast.makeText(this,"온도·습도를 보여 주는 기기가 없어요",Toast.LENGTH_SHORT).show();return;}
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
