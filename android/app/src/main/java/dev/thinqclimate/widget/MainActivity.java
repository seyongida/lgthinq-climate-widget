package dev.thinqclimate.widget;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import androidx.work.WorkManager;
import dev.thinqclimate.core.Climate;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private LinearLayout layout;
    private TextView status;
    private EditText token, country;
    private Spinner region;
    private Button connect;
    private boolean busy;
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,WindowManager.LayoutParams.FLAG_SECURE);
        ScrollView scroll = new ScrollView(this); layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(24*getResources().getDisplayMetrics().density); layout.setPadding(pad,pad,pad,pad);
        layout.setBackgroundColor(Color.rgb(13,28,34)); scroll.addView(layout); setContentView(scroll);
        TextView title = text("ThinQ Climate",30); title.setPadding(0,20,0,16);
        text("가습기의 온도와 습도를 한눈에",18);
        text("1. ThinQ와 같은 LG 계정으로 PAT를 발급합니다.\n2. 기기 목록·상태 읽기 권한을 허용합니다.\n3. 연결 후 가습기를 선택하세요.",15);
        button("LG PAT 발급 페이지",v -> startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://connect-pat.lgthinq.com"))));
        token = input("Personal Access Token",true); country = input("LG 계정 국가 코드 (예: KR)",false); country.setText("KR");
        text("계정 서버 지역",14);
        region = new Spinner(this); region.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"kic · 한국/아시아", "aic · 미주", "eic · 유럽/기타"})); layout.addView(region);
        status = text("",15);
        try { Climate.Config c = new Store(this).config(); if(c!=null) { country.setText(c.country()); region.setSelection(List.of("kic","aic","eic").indexOf(c.region())); } }
        catch (Exception e) { status.setText("저장된 인증을 읽을 수 없습니다. 다시 연결해 주세요."); }
        connect = button("연결하고 가습기 찾기",v -> connect());
        button("지금 새로고침",v -> { RefreshWorker.refresh(this); status.setText("갱신 요청됨 · 잠시 후 아래 상태를 다시 확인하세요."); });
        button("현재 상태 확인",v -> showReading());
        button("연결 해제 및 데이터 삭제",v -> disconnect());
        text("홈 화면을 길게 누른 뒤 위젯 → ThinQ Climate를 추가하세요. 모든 위젯은 선택한 가습기 하나를 표시합니다.\n\n30분 간격으로 갱신을 요청하며 절전·네트워크 상태에 따라 지연될 수 있습니다. 값이 없으면 — 로 표시합니다. 90분 이상 지난 값은 오래된 데이터로 표시합니다.",14);
        showReading();
    }
    private TextView text(String value,int size) { TextView t = new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(Color.rgb(222,240,238));t.setPadding(0,10,0,10);layout.addView(t);return t; }
    private EditText input(String hint,boolean secret) {
        EditText e = new EditText(this);e.setHint(hint);e.setTextColor(Color.WHITE);e.setHintTextColor(Color.LTGRAY);e.setSingleLine(true);
        e.setInputType(secret ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_CLASS_TEXT);
        e.setSaveEnabled(false);e.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);layout.addView(e);return e;
    }
    private Button button(String name,View.OnClickListener action) { Button b = new Button(this);b.setText(name);b.setOnClickListener(action);layout.addView(b);return b; }
    private void connect() {
        if (busy) return;
        Store store = new Store(this);
        final Climate.Config cfg;
        try {
            Climate.Config old = store.config(); String pat = token.getText().toString();
            if (pat.isBlank() && old != null) pat = old.token();
            cfg = new Climate.Config(pat,country.getText().toString(),List.of("kic","aic","eic").get(region.getSelectedItemPosition()),
                old == null ? "climate-"+UUID.randomUUID() : old.clientId());
        } catch(Exception e) { status.setText("PAT와 국가 코드를 확인해 주세요.");return; }
        busy = true;connect.setEnabled(false);status.setText("가습기를 찾고 있습니다…");
        executor.execute(() -> {
            try {
                List<Climate.Device> devices = new ThinQ(cfg).devices();
                runOnUiThread(() -> {
                    if(isDestroyed()) return;
                    if(devices.isEmpty()) { busy=false;connect.setEnabled(true);status.setText("지원되는 가습기가 없습니다. PAT 권한과 ThinQ 등록 계정을 확인해 주세요.");return; }
                    new AlertDialog.Builder(this).setTitle("위젯에 표시할 가습기")
                        .setOnCancelListener(d -> { busy=false;connect.setEnabled(true); })
                        .setItems(devices.stream().map(Climate.Device::name).toArray(String[]::new),(d,index) -> {
                            busy=false;connect.setEnabled(true);
                            synchronized(RefreshWorker.LOCK) {
                                try { store.config(cfg);store.select(devices.get(index));token.setText(""); }
                                catch(Exception e) { status.setText("안전한 인증 저장에 실패했습니다.");return; }
                            }
                            ClimateWidget.renderAll(this);RefreshWorker.schedule(this);RefreshWorker.refresh(this);
                            status.setText(devices.get(index).name()+" 연결됨 · 위젯을 추가해 주세요.");
                        }).setNegativeButton("취소",(d,which) -> { busy=false;connect.setEnabled(true); }).show();
                });
            } catch(Exception e) { runOnUiThread(() -> { if(isDestroyed())return;busy=false;connect.setEnabled(true);status.setText(e instanceof java.io.IOException ? e.getMessage() : "연결에 실패했습니다. 설정을 확인해 주세요."); }); }
        });
    }
    private void showReading() {
        Store s = new Store(this);Climate.Reading r = s.reading();
        status.setText(s.name()+"\n"+(r==null ? "수신한 센서 정보 없음" : r.temperatureText()+"  /  "+r.humidityText()+"\n마지막 수신 "+new java.util.Date(r.fetchedAt())+(r.stale(System.currentTimeMillis()) ? " · 오래된 데이터" : ""))+"\n"+s.error());
    }
    private void disconnect() {
        if(busy) { status.setText("연결 확인 중입니다. 잠시 후 다시 시도해 주세요.");return; }
        synchronized(RefreshWorker.LOCK) { new Store(this).clear(); }
        WorkManager.getInstance(this).cancelUniqueWork("climate-periodic");WorkManager.getInstance(this).cancelUniqueWork("climate-now");
        token.setText("");ClimateWidget.renderAll(this);status.setText("연결과 저장 데이터를 삭제했습니다.");
    }
    @Override protected void onDestroy() { executor.shutdownNow();super.onDestroy(); }
}
