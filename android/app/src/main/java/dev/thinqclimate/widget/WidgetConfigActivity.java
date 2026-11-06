package dev.thinqclimate.widget;

import android.app.*;
import android.appwidget.*;
import android.os.Bundle;
import android.content.*;
import android.graphics.Color;
import android.text.*;
import android.view.*;
import android.widget.*;
import dev.thinqclimate.core.Climate;
import java.util.*;

/** Per-widget device selection and appearance, with a preview of the actual RemoteViews. */
public final class WidgetConfigActivity extends Activity {
    private int widgetId;
    private Store store;
    private List<Climate.Device> devices;
    private Spinner first,second;
    private EditText background,accent;
    private SeekBar opacity;
    private TextView opacityLabel,error;
    private FrameLayout previewHost;
    private WidgetStyle style;
    private boolean horizontal,paired,updating;
    private final List<Button> swatches=new ArrayList<>();
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);setResult(RESULT_CANCELED);
        widgetId=getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID);
        AppWidgetProviderInfo info=AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId);
        if(info==null || !getPackageName().equals(info.provider.getPackageName())){finish();return;}
        horizontal=info.provider.getClassName().equals(HorizontalClimateWidget.class.getName());
        paired=horizontal||info.provider.getClassName().equals(PairClimateWidget.class.getName());
        Bundle options=AppWidgetManager.getInstance(this).getAppWidgetOptions(widgetId);
        int width=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,horizontal?188:82);
        int height=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,paired&&!horizontal?192:96);
        if(width>=120||height>=120){paired=true;horizontal=width>height;}
        store=new Store(this);devices=store.devices();style=store.widgetStyle(widgetId);
        if(saved!=null)style=WidgetStyle.decode(saved.getString("appearance",style.encode()));
        LinearLayout outer=Ui.column(this);ScrollView scroll=new ScrollView(this);outer.addView(scroll);Ui.window(this,outer);
        LinearLayout content=Ui.column(this);content.setPadding(Ui.dp(this,24),Ui.dp(this,22),Ui.dp(this,24),Ui.dp(this,28));scroll.addView(content);
        LinearLayout heading=Ui.row(this);Ui.weighted(heading,Ui.text(this,"위젯 꾸미기",25,Ui.INK,true));
        heading.addView(Ui.button(this,"닫기",false,v->finish()));content.addView(heading);Ui.gap(content,8);
        content.addView(Ui.text(this,"기기와 색상을 고르면 미리보기에 바로 반영돼요.",13,Ui.MUTED,false));Ui.gap(content,20);
        FrameLayout stage=new FrameLayout(this);stage.setBackground(Ui.background(this,Color.rgb(232,237,230),24));
        previewHost=new FrameLayout(this);
        int previewWidth=Math.min(horizontal?268:108,Math.max(56,width)),previewHeight=Math.min(paired&&!horizontal?196:108,Math.max(56,height));
        FrameLayout.LayoutParams previewSize=new FrameLayout.LayoutParams(Ui.dp(this,previewWidth),Ui.dp(this,previewHeight),Gravity.CENTER);
        stage.addView(previewHost,previewSize);content.addView(stage,new LinearLayout.LayoutParams(-1,Ui.dp(this,paired&&!horizontal?228:140)));
        Ui.gap(content,22);
        if(devices.isEmpty()) {
            content.addView(Ui.text(this,"먼저 앱에서 기기를 선택해 주세요",17,Ui.INK,true));Ui.gap(content,16);
            content.addView(Ui.button(this,"앱에서 기기 선택",true,v->{startActivity(new Intent(this,MainActivity.class));finish();}));return;
        }
        content.addView(Ui.text(this,"표시할 기기",17,Ui.INK,true));Ui.gap(content,12);
        first=picker(content,"첫 번째",false);second=picker(content,"두 번째 · 큰 크기에서 표시",true);
        List<String> selected=store.widgetDevices(widgetId,2);
        for(int i=0;i<devices.size();i++) {
            if(!selected.isEmpty()&&selected.get(0).equals(devices.get(i).id()))first.setSelection(i);
            if(selected.size()>1&&selected.get(1).equals(devices.get(i).id()))second.setSelection(i+1);
        }
        if(saved!=null){first.setSelection(saved.getInt("first",first.getSelectedItemPosition()));second.setSelection(saved.getInt("second",second.getSelectedItemPosition()));}
        AdapterView.OnItemSelectedListener listener=new AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(AdapterView<?> p,View v,int index,long id){preview();}
            @Override public void onNothingSelected(AdapterView<?> p){}
        };
        first.setOnItemSelectedListener(listener);second.setOnItemSelectedListener(listener);
        Ui.gap(content,8);content.addView(Ui.text(this,"색상",17,Ui.INK,true));Ui.gap(content,12);
        for(int row=0;row<2;row++) {
            LinearLayout colors=Ui.row(this);
            for(int col=0;col<3;col++) {
                final int index=row*3+col;
                if(col>0)colors.addView(new View(this),new LinearLayout.LayoutParams(Ui.dp(this,8),1));
                Button choice=Ui.button(this,WidgetStyle.NAMES[index],false,v->{
                    style=new WidgetStyle(WidgetStyle.PRESETS[index].background(),WidgetStyle.PRESETS[index].accent(),style.opacity());syncFields();preview();
                });choice.setTextSize(12);swatches.add(choice);Ui.weighted(colors,choice);
            }
            content.addView(colors);Ui.gap(content,8);
        }
        Ui.gap(content,8);
        LinearLayout custom=Ui.column(this);custom.setVisibility(View.GONE);
        Button customToggle=Ui.button(this,"직접 색상 지정  +",false,v->{boolean expanded=custom.getVisibility()!=View.VISIBLE;custom.setVisibility(expanded?View.VISIBLE:View.GONE);((Button)v).setText(expanded?"직접 색상 지정  −":"직접 색상 지정  +");});
        content.addView(customToggle);Ui.gap(content,10);content.addView(custom);
        background=colorField(custom,"배경 색상","#FAF8F2");accent=colorField(custom,"습도 강조 색상","#2F6C53");
        custom.addView(Ui.text(this,"온도와 이름 색상은 배경에 맞춰 자동으로 조정됩니다.",11,Ui.MUTED,false));Ui.gap(custom,12);
        opacityLabel=Ui.text(this,"",12,Ui.MUTED,false);content.addView(opacityLabel);
        opacity=new SeekBar(this);opacity.setMax(60);opacity.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.GREEN));content.addView(opacity);
        opacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar bar,int value,boolean fromUser){if(fromUser){style=new WidgetStyle(style.background(),style.accent(),value+40);preview();}}
            @Override public void onStartTrackingTouch(SeekBar bar){}
            @Override public void onStopTrackingTouch(SeekBar bar){}
        });
        error=Ui.text(this,"",12,Color.rgb(158,88,45),false);content.addView(error);Ui.gap(content,10);
        content.addView(Ui.button(this,"이 위젯에 적용",true,v->save()));Ui.gap(content,12);
        content.addView(Ui.text(this,"1 × 1은 첫 번째 기기만 표시합니다.\n위젯의 아래 시각을 누르면 센서값을 새로 받아옵니다.",11,Ui.MUTED,false));
        TextWatcher watcher=new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){if(updating)return;try{style=new WidgetStyle(WidgetStyle.parseColor(background.getText().toString()),WidgetStyle.parseColor(accent.getText().toString()),style.opacity());error.setText("");preview();}catch(IllegalArgumentException ignored){/* Incomplete colors are validated on save. */}}
            @Override public void afterTextChanged(Editable value){}
        };
        background.addTextChangedListener(watcher);accent.addTextChangedListener(watcher);syncFields();preview();
    }
    private List<String> selection() {
        List<String> ids=new ArrayList<>();if(first==null||devices.isEmpty())return ids;
        ids.add(devices.get(first.getSelectedItemPosition()).id());int b=second.getSelectedItemPosition()-1;if(b>=0)ids.add(devices.get(b).id());return ids;
    }
    private void syncFields(){updating=true;background.setText(WidgetStyle.hex(style.background()));accent.setText(WidgetStyle.hex(style.accent()));opacity.setProgress(style.opacity()-40);updating=false;}
    private void preview() {
        if(previewHost==null||first==null)return;
        List<String> ids=selection();RemoteViews views=paired?ClimateWidget.pair(this,store,widgetId,ids,horizontal,false,style):ClimateWidget.single(this,store,widgetId,ids,0,style,true);
        previewHost.removeAllViews();previewHost.addView(views.apply(this,previewHost),new FrameLayout.LayoutParams(-1,-1));
        if(opacityLabel!=null)opacityLabel.setText("배경 불투명도  "+style.opacity()+"%");
        for(int i=0;i<swatches.size();i++) {
            WidgetStyle preset=WidgetStyle.PRESETS[i];boolean selected=style.background()==preset.background()&&style.accent()==preset.accent();
            Button b=swatches.get(i);b.setText(WidgetStyle.NAMES[i]+(selected?" ✓":""));b.setTextColor(preset.foreground());
            android.graphics.drawable.GradientDrawable shape=Ui.background(this,preset.background(),14);shape.setStroke(Ui.dp(this,selected?2:1),selected?Ui.GREEN:Color.rgb(214,223,217));b.setBackground(shape);
        }
    }
    private void save() {
        List<String> ids=selection();if(ids.size()>1&&ids.get(0).equals(ids.get(1))){error.setText("서로 다른 기기를 선택해 주세요");return;}
        try {
            style=new WidgetStyle(WidgetStyle.parseColor(background.getText().toString()),WidgetStyle.parseColor(accent.getText().toString()),style.opacity());
            if(WidgetStyle.contrast(style.background(),style.accent())<3.0){error.setText("습도 숫자가 잘 보이도록 배경과 다른 강조 색상을 골라 주세요");return;}
        }catch(IllegalArgumentException e){error.setText("색상은 #RRGGBB 형식으로 입력해 주세요");return;}
        store.widgetDevices(widgetId,ids);store.widgetStyle(widgetId,style);ClimateWidget.render(this,widgetId);RefreshWorker.schedule(this);RefreshWorker.refresh(this);
        setResult(RESULT_OK,new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,widgetId));finish();
    }
    private EditText colorField(LinearLayout parent,String label,String hint){
        parent.addView(Ui.text(this,label,12,Ui.MUTED,true));Ui.gap(parent,6);
        EditText field=new EditText(this);field.setSingleLine(true);field.setTextSize(14);field.setTextColor(Ui.INK);field.setHint(hint);
        field.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);field.setBackground(Ui.background(this,Color.WHITE,12));field.setPadding(Ui.dp(this,14),Ui.dp(this,12),Ui.dp(this,14),Ui.dp(this,12));
        parent.addView(field);Ui.gap(parent,12);return field;
    }
    private Spinner picker(LinearLayout parent,String title,boolean optional) {
        parent.addView(Ui.text(this,title,12,Ui.MUTED,true));Ui.gap(parent,6);
        List<String> labels=new ArrayList<>();if(optional)labels.add("선택 안 함");for(Climate.Device d:devices)labels.add(d.name());
        Spinner spinner=new Spinner(this);android.graphics.drawable.LayerDrawable pickerBackground=new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{Ui.touch(this,Color.WHITE,14,true),getDrawable(R.drawable.ic_chevron)});
        pickerBackground.setLayerGravity(1,Gravity.END|Gravity.CENTER_VERTICAL);pickerBackground.setLayerSize(1,Ui.dp(this,20),Ui.dp(this,20));pickerBackground.setLayerInset(1,0,0,Ui.dp(this,12),0);
        spinner.setBackground(pickerBackground);spinner.setPadding(Ui.dp(this,12),Ui.dp(this,10),Ui.dp(this,36),Ui.dp(this,10));
        spinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));parent.addView(spinner);Ui.gap(parent,14);return spinner;
    }
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);if(style!=null)out.putString("appearance",style.encode());if(first!=null){out.putInt("first",first.getSelectedItemPosition());out.putInt("second",second.getSelectedItemPosition());}}
}
