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
    private TextView connection;
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
        scroll=new ScrollView(this);scroll.setClipToPadding(false);scroll.setFillViewport(true);
        content=Ui.column(this);content.setPadding(Ui.dp(this,24),Ui.dp(this,20),Ui.dp(this,24),Ui.dp(this,32));
        scroll.addView(content);outer.addView(scroll);
        LinearLayout top=Ui.row(this);
        TextView brand=Ui.text(this,"◌  ThinQ Climate",16,Ui.GREEN,true); Ui.weighted(top,brand);
        Button settings=Ui.button(this,"연결 설정",false,v->settings());settings.setTextSize(12);top.addView(settings);content.addView(top);
        Ui.gap(content,24);
        TextView heading=Ui.text(this,"우리 집의 공기, 한눈에.",27,Ui.INK,true);heading.setLineSpacing(Ui.dp(this,4),1);content.addView(heading);
        Ui.gap(content,12);content.addView(Ui.text(this,"공간마다 다른 온도와 습도를 확인하세요",14,Ui.MUTED,false));Ui.gap(content,26);
        LinearLayout overview=Ui.card(this,Color.rgb(226,237,221));
        overview.addView(Ui.text(this,"MY HOME",11,Ui.GREEN,true));Ui.gap(overview,10);
        connection=Ui.text(this,"기기를 연결하고 있습니다",18,Ui.INK,true);overview.addView(connection);Ui.gap(overview,7);
        overview.addView(Ui.text(this,"가습기 · 공기청정기  /  현재 센서값",12,Ui.GREEN,false));content.addView(overview);
        Ui.gap(content,28);
        LinearLayout controls=Ui.row(this);Ui.weighted(controls,Ui.text(this,"나의 기기",20,Ui.INK,true));
        Button manage=Ui.button(this,"기기 선택",false,v->discover(true,false));manage.setTextSize(12);controls.addView(manage);content.addView(controls);
        Ui.gap(content,12);deviceList=Ui.column(this);content.addView(deviceList);
        Button refresh=Ui.button(this,"센서값 새로고침",true,v->{RefreshWorker.refresh(this);Toast.makeText(this,"갱신 요청 · 1분 이내 요청은 합쳐집니다",Toast.LENGTH_SHORT).show();});
        content.addView(refresh);Ui.gap(content,28);
        content.addView(Ui.text(this,"홈 화면 위젯",20,Ui.INK,true));Ui.gap(content,8);
        content.addView(Ui.text(this,"크기를 고르고, 위젯을 눌러 기기와 색상을 설정하세요.",13,Ui.MUTED,false));Ui.gap(content,16);
        LinearLayout sizes=Ui.row(this);
        Ui.weighted(sizes,Ui.sizeTile(this,"1 × 1","기기 1대",1,1,v->pin(ClimateWidget.class)));
        sizes.addView(new View(this),new LinearLayout.LayoutParams(Ui.dp(this,10),1));
        Ui.weighted(sizes,Ui.sizeTile(this,"1 × 2","세로 2대",1,2,v->pin(PairClimateWidget.class)));
        sizes.addView(new View(this),new LinearLayout.LayoutParams(Ui.dp(this,10),1));
        Ui.weighted(sizes,Ui.sizeTile(this,"2 × 1","가로 2대",2,1,v->pin(HorizontalClimateWidget.class)));
        content.addView(sizes);Ui.gap(content,18);
        content.addView(Ui.text(this,"30분마다 갱신을 요청합니다. 절전 상태에서는 늦어질 수 있어요.\n센서 미지원 값은 —, 90분 이상 지난 값은 ‘오래됨’으로 표시합니다.",11,Ui.MUTED,false));
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
        if(!busy) connection.setText(devices.isEmpty()?"표시할 기기를 선택해 주세요":devices.size()+"개의 기기를 한곳에서");
        if(devices.isEmpty()) {
            LinearLayout empty=Ui.card(this,Color.WHITE);empty.addView(Ui.text(this,"아직 선택한 기기가 없어요",17,Ui.INK,true));Ui.gap(empty,10);
            empty.addView(Ui.text(this,"‘기기 선택’에서 가습기와 공기청정기를\n여러 대 선택할 수 있습니다.",13,Ui.MUTED,false));deviceList.addView(empty);Ui.gap(deviceList,12);
        }
        for(Climate.Device d:devices) {
            Climate.Reading r=store.reading(d.id());String error=store.error(d.id());
            LinearLayout card=Ui.card(this,Color.WHITE);LinearLayout title=Ui.row(this);
            TextView deviceTitle=Ui.text(this,d.name(),17,Ui.INK,true);deviceTitle.setMaxLines(1);deviceTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);Ui.weighted(title,deviceTitle);TextView type=Ui.text(this,d.typeName(),11,Ui.MUTED,false);title.addView(type);card.addView(title);Ui.gap(card,22);
            LinearLayout metrics=Ui.row(this);Ui.weighted(metrics,metric("온도",r==null?"—":r.temperatureText(),Ui.INK));
            Ui.weighted(metrics,metric("습도",r==null?"—":r.humidityText(),Ui.GREEN));card.addView(metrics);Ui.gap(card,18);
            String status=r==null?"센서값을 기다리고 있어요":DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(r.fetchedAt()))+" 수신"+(r.stale(System.currentTimeMillis())?" · 오래됨":" · 최신 수신값");
            card.addView(Ui.text(this,error.isEmpty()?"●  "+status:error+"\n"+status,11,error.isEmpty()?Ui.MUTED:Color.rgb(158,88,45),false));
            if(r!=null && r.temperature()==null && r.humidity()==null) {Ui.gap(card,6);card.addView(Ui.text(this,"이 모델은 현재 온도·습도를 제공하지 않습니다",11,Ui.MUTED,false));}
            deviceList.addView(card);Ui.gap(deviceList,12);
        }
    }
    private LinearLayout metric(String label,String value,int color) {
        LinearLayout l=Ui.column(this);l.addView(Ui.text(this,label,12,Ui.MUTED,false));Ui.gap(l,6);TextView t=Ui.text(this,value,30,color,true);
        t.setSingleLine(true);t.setAutoSizeTextTypeUniformWithConfiguration(18,30,1,android.util.TypedValue.COMPLEX_UNIT_SP);l.addView(t);return l;
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
        else new AlertDialog.Builder(this).setTitle("위젯 추가").setMessage("홈 화면을 길게 누른 뒤 위젯 → ThinQ Climate에서 크기를 고르세요.").setPositiveButton("확인",null).show();
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
