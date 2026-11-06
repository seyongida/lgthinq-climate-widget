package dev.thinqclimate.widget;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.content.res.ColorStateList;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.*;
import android.widget.*;

final class Ui {
    static final int BG=Color.rgb(245,247,243), INK=Color.rgb(26,46,40), MUTED=Color.rgb(111,128,119), GREEN=Color.rgb(36,102,77);
    private Ui() {}
    static int dp(Context c,float n) { return Math.round(n*c.getResources().getDisplayMetrics().density); }
    static GradientDrawable background(Context c,int color,int radius) {
        GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(c,radius)); return d;
    }
    static TextView text(Context c,String value,int size,int color,boolean bold) {
        TextView t=new TextView(c); t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setIncludeFontPadding(false);
        t.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL)); return t;
    }
    static LinearLayout column(Context c) { LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.VERTICAL);return l; }
    static LinearLayout row(Context c) { LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l; }
    static LinearLayout card(Context c,int color) {
        LinearLayout l=column(c); l.setPadding(dp(c,22),dp(c,22),dp(c,22),dp(c,22));l.setBackground(background(c,color,26));return l;
    }
    static void gap(LinearLayout parent,int height) {
        View v=new View(parent.getContext()); parent.addView(v,new LinearLayout.LayoutParams(1,dp(parent.getContext(),height)));
    }
    static void weighted(LinearLayout parent,View child) { parent.addView(child,new LinearLayout.LayoutParams(0,-2,1)); }
    static android.graphics.drawable.Drawable touch(Context c,int color,int radius,boolean border) {
        GradientDrawable shape=background(c,color,radius);
        if(border)shape.setStroke(dp(c,1),Color.rgb(216,226,218));
        return new RippleDrawable(ColorStateList.valueOf(0x1824664d),shape,background(c,Color.WHITE,radius));
    }
    static Button button(Context c,String title,boolean primary,View.OnClickListener listener) {
        Button b=new Button(c);b.setText(title);b.setTextSize(14);b.setAllCaps(false);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        b.setTextColor(primary?Color.WHITE:GREEN);b.setBackgroundTintList(null);
        b.setStateListAnimator(null);b.setElevation(0);b.setTranslationZ(0);
        b.setBackground(touch(c,primary?GREEN:Color.WHITE,16,!primary));
        b.setGravity(Gravity.CENTER);b.setIncludeFontPadding(false);
        b.setMinimumHeight(dp(c,50));b.setMinHeight(dp(c,50));b.setMinWidth(0);b.setMinimumWidth(0);
        b.setPadding(dp(c,14),dp(c,12),dp(c,14),dp(c,12));
        b.setOnClickListener(listener);return b;
    }
    static LinearLayout sizeTile(Context c,String size,String caption,int columns,int rows,View.OnClickListener listener) {
        LinearLayout tile=column(c);tile.setGravity(Gravity.CENTER);tile.setPadding(dp(c,8),dp(c,16),dp(c,8),dp(c,16));
        tile.setBackground(touch(c,Color.WHITE,20,true));tile.setClickable(true);tile.setFocusable(true);tile.setOnClickListener(listener);
        View graphic=new View(c) {
            final android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(android.graphics.Canvas canvas) {
                float cell=dp(c,13),gap=dp(c,3),left=(getWidth()-(cell*columns+gap*(columns-1)))/2,top=(getHeight()-(cell*rows+gap*(rows-1)))/2;
                paint.setColor(GREEN);
                for(int row=0;row<rows;row++)for(int col=0;col<columns;col++) {
                    float x=left+col*(cell+gap),y=top+row*(cell+gap);
                    canvas.drawRoundRect(x,y,x+cell,y+cell,dp(c,3),dp(c,3),paint);
                }
            }
        };
        graphic.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);tile.addView(graphic,new LinearLayout.LayoutParams(-1,dp(c,32)));
        gap(tile,10);TextView sizeLabel=text(c,size,18,INK,true);sizeLabel.setGravity(Gravity.CENTER);tile.addView(sizeLabel);gap(tile,5);TextView description=text(c,caption,11,MUTED,false);description.setGravity(Gravity.CENTER);tile.addView(description);
        tile.setContentDescription(size+" 위젯 추가, "+caption);return tile;
    }
    static void window(Activity a,View root) {
        a.getWindow().setStatusBarColor(BG);a.getWindow().setNavigationBarColor(BG);
        a.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v,insets)-> {
            if(Build.VERSION.SDK_INT>=30) {
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());
                v.setPadding(bars.left,bars.top,bars.right,bars.bottom);
            } else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        a.setContentView(root);root.requestApplyInsets();
    }
}
