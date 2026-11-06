package dev.thinqclimate.widget;

import android.app.Activity;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import dev.thinqclimate.core.Climate;
import java.util.*;
import java.util.concurrent.*;

/** Public onboarding and token replacement. Never displays a stored token. */
public final class CredentialsActivity extends Activity {
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private EditText token,country;
    private Spinner region;
    private TextView status;
    private Button connect;
    private Store store;
    private boolean busy;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        store=new Store(this);Climate.Config existing=null;
        try{existing=store.config();}catch(Exception ignored){/* New input can replace unreadable credentials. */}
        LinearLayout outer=Ui.column(this);ScrollView scroll=new ScrollView(this);outer.addView(scroll);Ui.window(this,outer);
        LinearLayout content=Ui.column(this);content.setPadding(Ui.dp(this,24),Ui.dp(this,24),Ui.dp(this,24),Ui.dp(this,32));scroll.addView(content);
        content.addView(Ui.text(this,existing==null?"내 계정으로 시작하기":"연결 설정",27,Ui.INK,true));Ui.gap(content,12);
        content.addView(Ui.text(this,existing==null?"LG ThinQ 토큰으로 우리 집 기기를 연결하세요.":"새 토큰으로 언제든 연결을 바꿀 수 있어요.\n저장된 토큰은 화면에 표시하지 않습니다.",14,Ui.MUTED,false));Ui.gap(content,24);
        content.addView(Ui.button(this,"LG에서 토큰 발급하기",false,v->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://connect-pat.lgthinq.com")))));Ui.gap(content,20);
        content.addView(Ui.text(this,existing==null?"Personal Access Token":"새 Personal Access Token",13,Ui.INK,true));Ui.gap(content,8);
        token=new EditText(this);token.setHint(existing==null?"발급받은 토큰 입력":"토큰을 바꾸려면 새 토큰 입력");
        token.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);token.setSingleLine(true);token.setSaveEnabled(false);token.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);field(content,token);Ui.gap(content,18);
        content.addView(Ui.text(this,"LG 계정 국가 코드",13,Ui.INK,true));Ui.gap(content,8);
        country=new EditText(this);country.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);country.setSingleLine(true);country.setText(existing==null?"KR":existing.country());field(content,country);Ui.gap(content,18);
        content.addView(Ui.text(this,"LG 계정 서버 지역",13,Ui.INK,true));Ui.gap(content,8);
        region=new Spinner(this);region.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"kic · 한국/아시아","aic · 미주","eic · 유럽/기타"}));region.setBackground(Ui.touch(this,android.graphics.Color.WHITE,14,true));region.setPadding(Ui.dp(this,12),Ui.dp(this,12),Ui.dp(this,12),Ui.dp(this,12));
        if(existing!=null)region.setSelection(List.of("kic","aic","eic").indexOf(existing.region()));content.addView(region);Ui.gap(content,18);
        status=Ui.text(this,"기기 목록·상태 조회 권한이 있는 토큰을 사용하세요.\n토큰은 이 휴대폰의 암호화 저장소에 보관합니다.",12,Ui.MUTED,false);content.addView(status);Ui.gap(content,20);
        connect=Ui.button(this,existing==null?"연결하고 시작하기":"연결 확인 후 저장",true,v->save());content.addView(connect);Ui.gap(content,12);
        content.addView(Ui.button(this,"취소",false,v->finish()));
    }
    private void field(LinearLayout parent,EditText field){field.setTextSize(14);field.setTextColor(Ui.INK);field.setBackground(Ui.touch(this,android.graphics.Color.WHITE,14,true));field.setPadding(Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,14));parent.addView(field);}
    private void save() {
        if(busy)return;
        final Climate.Config config;
        try {
            Climate.Config old=null;try{old=store.config();}catch(Exception ignored){}
            String pat=token.getText().toString();
            if(pat.isBlank()&&old!=null)pat=old.token();
            config=new Climate.Config(pat,country.getText().toString(),List.of("kic","aic","eic").get(region.getSelectedItemPosition()),old==null?"climate-"+UUID.randomUUID():old.clientId());
        }catch(Exception e){status.setText("토큰과 국가 코드를 확인해 주세요");return;}
        busy=true;connect.setEnabled(false);status.setText("계정 연결을 확인하고 있어요…");
        executor.execute(()->{
            try {
                List<Climate.Device> devices=new ThinQ(config).devices();
                runOnUiThread(()->{
                    if(isDestroyed()||isFinishing())return;
                    try{store.replaceCredentials(config,devices);token.setText("");ClimateWidget.renderAll(this);RefreshWorker.schedule(this);RefreshWorker.refresh(this);setResult(RESULT_OK);finish();}
                    catch(Exception e){busy=false;connect.setEnabled(true);status.setText("안전한 인증 저장에 실패했습니다. 다시 시도해 주세요.");}
                });
            }catch(Exception e){runOnUiThread(()->{if(isDestroyed()||isFinishing())return;busy=false;connect.setEnabled(true);status.setText(e instanceof java.io.IOException?e.getMessage():"연결 설정을 확인해 주세요");});}
        });
    }
    @Override protected void onDestroy(){executor.shutdownNow();super.onDestroy();}
}
