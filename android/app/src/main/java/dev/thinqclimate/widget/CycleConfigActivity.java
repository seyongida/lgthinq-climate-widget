package dev.thinqclimate.widget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import dev.thinqclimate.core.Climate;
import java.util.*;

/** Options for one appliance timer widget, with a live preview of the real RemoteViews. */
public final class CycleConfigActivity extends Activity {
    private int widgetId;
    private Store store;
    private List<Climate.Device> appliances;
    private RadioButton auto, selected;
    private final List<CheckBox> checks = new ArrayList<>();
    private Spinner max, pinned;
    private CheckBox alerts;
    private SeekBar opacity;
    private TextView opacityLabel;
    private FrameLayout previewHost;
    private WidgetStyle style;
    private final List<Button> swatches = new ArrayList<>();

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); setResult(RESULT_CANCELED);
        widgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return; }
        store = new Store(this); appliances = store.appliances();
        Store.TimerOptions o = store.timerOptions(widgetId); style = o.style();
        LinearLayout outer = Ui.column(this); ScrollView scroll = new ScrollView(this); outer.addView(scroll); Ui.window(this, outer);
        LinearLayout content = Ui.column(this); content.setPadding(Ui.dp(this,20), Ui.dp(this,22), Ui.dp(this,20), Ui.dp(this,28)); scroll.addView(content);
        LinearLayout heading = Ui.row(this); Ui.weighted(heading, Ui.text(this, "가전 타이머 설정", 25, Ui.INK, true));
        heading.addView(Ui.pill(this, "닫기", v -> finish())); content.addView(heading); Ui.gap(content, 8);
        content.addView(Ui.text(this, "어떤 가전을 몇 대까지 보여 줄지 고르면 미리보기에 바로 반영돼요.", 13, Ui.MUTED, false)); Ui.gap(content, 18);
        FrameLayout stage = new FrameLayout(this); stage.setBackground(Ui.background(this, Color.rgb(90, 122, 114), 24));
        previewHost = new FrameLayout(this);
        stage.addView(previewHost, new FrameLayout.LayoutParams(Ui.dp(this, 280), Ui.dp(this, 150), Gravity.CENTER));
        content.addView(stage, new LinearLayout.LayoutParams(-1, Ui.dp(this, 182))); Ui.gap(content, 22);

        content.addView(Ui.text(this, "표시 방식", 17, Ui.INK, true)); Ui.gap(content, 6);
        RadioGroup mode = new RadioGroup(this);
        auto = new RadioButton(this); auto.setText("자동 · 작동 중인 가전 모두"); auto.setId(View.generateViewId());
        selected = new RadioButton(this); selected.setText("선택한 가전만"); selected.setId(View.generateViewId());
        for (RadioButton b : List.of(auto, selected)) { b.setTextColor(Ui.INK); b.setTextSize(15); b.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.GREEN)); mode.addView(b); }
        (o.selectedOnly() ? selected : auto).setChecked(true);
        mode.setOnCheckedChangeListener((g, id) -> { syncChecks(); preview(); });
        content.addView(mode); Ui.gap(content, 4);
        LinearLayout list = Ui.column(this); list.setPadding(Ui.dp(this, 24), 0, 0, 0);
        if (appliances.isEmpty()) list.addView(Ui.text(this, "앱에서 고른 기기 중 남은 시간을 알려 주는 가전이 없어요.", 12, Ui.MUTED, false));
        for (Climate.Device d : appliances) {
            CheckBox box = new CheckBox(this); box.setText(d.name().equals(d.typeName()) ? d.name() : d.name() + " · " + d.typeName()); box.setTextColor(Ui.INK); box.setTextSize(14);
            box.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.GREEN)); box.setChecked(o.devices().contains(d.id()));
            box.setOnCheckedChangeListener((b, on) -> preview()); checks.add(box); list.addView(box);
        }
        content.addView(list); Ui.gap(content, 14);

        content.addView(Ui.text(this, "최대 표시 수", 17, Ui.INK, true)); Ui.gap(content, 6);
        max = spinner(List.of("1대", "2대", "3대")); max.setSelection(o.max() - 1); content.addView(max);
        content.addView(Ui.text(this, "위젯 높이가 허용하는 만큼만 보여요. 가려진 가전은 ‘외 N대’로 알려 드려요.", 11, Ui.MUTED, false)); Ui.gap(content, 14);

        content.addView(Ui.text(this, "쉬고 있을 때 보여 줄 가전", 17, Ui.INK, true)); Ui.gap(content, 6);
        List<String> names = new ArrayList<>(); names.add("없음 · ‘진행 중인 가전이 없어요’ 표시");
        int pinnedIndex = 0;
        for (int i = 0; i < appliances.size(); i++) { names.add(appliances.get(i).name()); if (appliances.get(i).id().equals(o.pinned())) pinnedIndex = i + 1; }
        pinned = spinner(names); pinned.setSelection(pinnedIndex); content.addView(pinned); Ui.gap(content, 10);

        alerts = new CheckBox(this); alerts.setText("확인할 일 표시 (끝난 세탁, 냉장고 문 열림 등)"); alerts.setTextColor(Ui.INK); alerts.setTextSize(14);
        alerts.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.GREEN)); alerts.setChecked(o.alerts());
        alerts.setOnCheckedChangeListener((b, on) -> preview()); content.addView(alerts); Ui.gap(content, 16);

        content.addView(Ui.text(this, "색상", 17, Ui.INK, true)); Ui.gap(content, 10);
        for (int row = 0; row < 2; row++) {
            LinearLayout colors = Ui.row(this);
            for (int col = 0; col < 3; col++) {
                final int index = row * 3 + col;
                if (col > 0) colors.addView(new View(this), new LinearLayout.LayoutParams(Ui.dp(this, 8), 1));
                Button choice = Ui.button(this, WidgetStyle.NAMES[index], false, v -> {
                    style = new WidgetStyle(WidgetStyle.PRESETS[index].background(), WidgetStyle.PRESETS[index].accent(), style.opacity()); preview();
                });
                choice.setTextSize(12); swatches.add(choice); Ui.weighted(colors, choice);
            }
            content.addView(colors); Ui.gap(content, 8);
        }
        opacityLabel = Ui.text(this, "", 12, Ui.MUTED, false); content.addView(opacityLabel);
        opacity = new SeekBar(this); opacity.setMax(100); opacity.setProgress(style.opacity());
        opacity.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.GREEN)); content.addView(opacity);
        opacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) { if (fromUser) { style = new WidgetStyle(style.background(), style.accent(), value); preview(); } }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        Ui.gap(content, 18);
        content.addView(Ui.button(this, "이 위젯에 적용", true, v -> save()));
        syncChecks(); preview();
    }
    private Spinner spinner(List<String> labels) {
        Spinner s = new Spinner(this); s.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        s.setBackground(Ui.touch(this, Color.WHITE, 14, true)); s.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        s.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int i, long id) { preview(); }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        return s;
    }
    private void syncChecks() { for (CheckBox b : checks) { b.setEnabled(selected.isChecked()); b.setAlpha(selected.isChecked() ? 1f : .45f); } }
    private Store.TimerOptions options() {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < checks.size(); i++) if (checks.get(i).isChecked()) ids.add(appliances.get(i).id());
        int p = pinned == null ? 0 : pinned.getSelectedItemPosition();
        return new Store.TimerOptions(selected.isChecked(), List.copyOf(ids), max == null ? 3 : max.getSelectedItemPosition() + 1,
            p <= 0 ? "" : appliances.get(p - 1).id(), alerts == null || alerts.isChecked(), style);
    }
    private void preview() {
        if (previewHost == null || alerts == null || opacityLabel == null) return;
        RemoteViews views = CycleWidget.forWidget(this, store, options(), 3);
        previewHost.removeAllViews(); previewHost.addView(views.apply(this, previewHost), new FrameLayout.LayoutParams(-1, -1));
        // Text colors follow the chosen background, but a see-through widget shows the wallpaper instead.
        opacityLabel.setText("배경 불투명도  " + style.opacity() + "%" + (style.opacity() < 40 ? "\n배경화면에 따라 글자가 잘 안 보일 수 있어요" : ""));
        for (int i = 0; i < swatches.size(); i++) {
            WidgetStyle preset = WidgetStyle.PRESETS[i]; boolean on = style.background() == preset.background() && style.accent() == preset.accent();
            Button b = swatches.get(i); b.setText(WidgetStyle.NAMES[i] + (on ? " ✓" : "")); b.setTextColor(preset.foreground());
            android.graphics.drawable.GradientDrawable shape = Ui.background(this, preset.background(), 14);
            shape.setStroke(Ui.dp(this, on ? 2 : 1), on ? Ui.GREEN : Color.rgb(214, 223, 217)); b.setBackground(shape);
        }
    }
    private void save() {
        Store.TimerOptions o = options();
        if (o.selectedOnly() && o.devices().isEmpty()) { Toast.makeText(this, "보여 줄 가전을 하나 이상 골라 주세요", Toast.LENGTH_SHORT).show(); return; }
        try { store.timerOptions(widgetId, o); } catch (Exception e) { Toast.makeText(this, "저장하지 못했어요", Toast.LENGTH_SHORT).show(); return; }
        CycleWidget.render(this, widgetId);
        setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)); finish();
    }
}
