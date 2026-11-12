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
    // Same palette as the iOS app.
    static final int BG=Color.rgb(246,245,241), INK=Color.rgb(18,33,30), MUTED=Color.rgb(98,113,107), GREEN=Color.rgb(24,110,90), TINT=Color.rgb(226,240,233), LINE=Color.rgb(228,230,224), WARNING=Color.rgb(158,88,45);
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
        LinearLayout l=column(c); l.setPadding(dp(c,20),dp(c,20),dp(c,20),dp(c,20));l.setBackground(background(c,color,26));l.setElevation(dp(c,3));l.setClipToOutline(false);return l;
    }
    static void gap(LinearLayout parent,int height) {
        View v=new View(parent.getContext()); parent.addView(v,new LinearLayout.LayoutParams(1,dp(parent.getContext(),height)));
    }
    static void weighted(LinearLayout parent,View child) { parent.addView(child,new LinearLayout.LayoutParams(0,-2,1)); }
    static android.graphics.drawable.Drawable touch(Context c,int color,int radius,boolean border) {
        GradientDrawable shape=background(c,color,radius);
        if(border)shape.setStroke(dp(c,1),LINE);
        return new RippleDrawable(ColorStateList.valueOf(0x1824664d),shape,background(c,Color.WHITE,radius));
    }
    static Button button(Context c,String title,boolean primary,View.OnClickListener listener) {
        Button b=new Button(c);b.setText(title);b.setTextSize(14);b.setAllCaps(false);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        b.setTextColor(primary?Color.WHITE:INK);b.setBackgroundTintList(null);
        b.setStateListAnimator(null);b.setElevation(0);b.setTranslationZ(0);
        b.setBackground(touch(c,primary?INK:Color.WHITE,18,!primary));
        b.setGravity(Gravity.CENTER);b.setIncludeFontPadding(false);
        b.setMinimumHeight(dp(c,54));b.setMinHeight(dp(c,54));b.setMinWidth(0);b.setMinimumWidth(0);
        b.setPadding(dp(c,14),dp(c,12),dp(c,14),dp(c,12));
        b.setOnClickListener(listener);return b;
    }
    static LinearLayout sizeTile(Context c,String size,String caption,int columns,int rows,View.OnClickListener listener) {
        LinearLayout tile=column(c);tile.setGravity(Gravity.CENTER);tile.setPadding(dp(c,8),dp(c,16),dp(c,8),dp(c,16));
        tile.setBackground(touch(c,Color.WHITE,22,true));tile.setClickable(true);tile.setFocusable(true);tile.setOnClickListener(listener);
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
    static Button pill(Context c,String title,View.OnClickListener listener) {
        Button b=button(c,title,false,listener);b.setTextSize(13);b.setTextColor(GREEN);b.setBackground(touch(c,TINT,18,false));
        b.setMinimumHeight(dp(c,36));b.setMinHeight(dp(c,36));b.setPadding(dp(c,14),dp(c,6),dp(c,14),dp(c,6));return b;
    }
    /** An icon on a filled circle or rounded square. */
    static ImageView icon(Context c,int drawable,int tint,int fill,int size,float scale,boolean square) {
        ImageView v=new ImageView(c);v.setImageResource(drawable);v.setColorFilter(tint);v.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int inset=Math.round(dp(c,size)*(1-scale)/2);v.setPadding(inset,inset,inset,inset);
        GradientDrawable d=new GradientDrawable();d.setColor(fill);
        if(square)d.setCornerRadius(dp(c,size)*.3f);else d.setShape(GradientDrawable.OVAL);
        v.setBackground(d);v.setLayoutParams(new LinearLayout.LayoutParams(dp(c,size),dp(c,size)));return v;
    }
    static View logo(Context c,int size) {
        ImageView v=icon(c,R.drawable.ic_drop,Color.WHITE,GREEN,size,.56f,true);
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.rgb(11,62,56),Color.rgb(24,118,96)});
        d.setCornerRadius(dp(c,size)*.3f);v.setBackground(d);return v;
    }
    /** Hero card background: comfort gradient with two soft circles, clipped to the rounded card. */
    static android.graphics.drawable.Drawable hero(Context c,dev.thinqclimate.core.Climate.Comfort comfort) {
        int[] colors=switch(comfort) {
            case GOOD->new int[]{Color.rgb(11,62,56),Color.rgb(24,118,96)};
            case DRY->new int[]{Color.rgb(112,58,20),Color.rgb(170,98,32)};
            case HUMID->new int[]{Color.rgb(16,56,104),Color.rgb(44,102,164)};
            default->new int[]{Color.rgb(36,52,58),Color.rgb(72,96,106)};
        };
        float radius=dp(c,30);
        return new android.graphics.drawable.Drawable() {
            final android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            final android.graphics.Path clip=new android.graphics.Path();
            @Override public void draw(android.graphics.Canvas canvas) {
                android.graphics.Rect b=getBounds();
                clip.reset();clip.addRoundRect(b.left,b.top,b.right,b.bottom,radius,radius,android.graphics.Path.Direction.CW);
                canvas.save();canvas.clipPath(clip);
                // The circles below lower the alpha; reset it so later redraws do not fade the gradient.
                paint.setAlpha(255);
                paint.setShader(new android.graphics.LinearGradient(b.left,b.top,b.right,b.bottom,colors[0],colors[1],android.graphics.Shader.TileMode.CLAMP));
                canvas.drawRect(b,paint);paint.setShader(null);
                paint.setColor(Color.WHITE);paint.setAlpha(18);canvas.drawCircle(b.right-dp(c,40),b.top+dp(c,60),dp(c,130),paint);
                paint.setAlpha(13);canvas.drawCircle(b.right,b.bottom-dp(c,70),dp(c,90),paint);
                canvas.restore();
            }
            @Override public void getOutline(android.graphics.Outline outline) { outline.setRoundRect(getBounds(),radius); }
            @Override public void setAlpha(int alpha) {}
            @Override public void setColorFilter(android.graphics.ColorFilter filter) {}
            @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
        };
    }
    /** Humidity on a 0–100 track, with the comfortable range highlighted. Decorative: values are in adjacent text. */
    static final class Gauge extends View {
        private final android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final int track,zone,marker;
        private Double humidity;
        Gauge(Context c,int track,int zone,int marker) {
            super(c);this.track=track;this.zone=zone;this.marker=marker;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            setLayoutParams(new LinearLayout.LayoutParams(-1,dp(c,14)));
        }
        void humidity(Double value){humidity=value;invalidate();}
        @Override protected void onDraw(android.graphics.Canvas canvas) {
            float w=getWidth(),mid=getHeight()/2f,half=dp(getContext(),4);
            paint.setColor(track);canvas.drawRoundRect(0,mid-half,w,mid+half,half,half,paint);
            paint.setColor(zone);canvas.drawRoundRect(w*.4f,mid-half,w*.6f,mid+half,half,half,paint);
            if(humidity!=null) {
                float r=dp(getContext(),7),x=Math.max(r,Math.min(w-r,(float)(w*Math.max(0,Math.min(100,humidity))/100)));
                paint.setColor(marker);canvas.drawCircle(x,mid,r,paint);
            }
        }
    }
    static int alpha(int color,int alpha){return (color&0xffffff)|(alpha<<24);}
    static final int WARM_TINT=Color.rgb(250,236,222);
    static int typeIcon(String type) {
        return switch (dev.thinqclimate.core.Home.icon(type)) {
            case "drop", "water" -> R.drawable.ic_drop;
            case "air", "vacuum" -> R.drawable.ic_air;
            case "snow" -> R.drawable.ic_snow;
            case "washer", "dryer" -> R.drawable.ic_washer;
            case "hanger" -> R.drawable.ic_hanger;
            case "dishes" -> R.drawable.ic_dishes;
            case "oven" -> R.drawable.ic_oven;
            case "fridge" -> R.drawable.ic_fridge;
            default -> R.drawable.ic_plug;
        };
    }
    static String time(long millis){return new java.text.SimpleDateFormat("HH:mm",java.util.Locale.KOREA).format(new java.util.Date(millis));}
    /** "11:04 완료 예정", "12:59 시작 예정", or the state label. */
    static String cycleDetail(dev.thinqclimate.core.Home.Status s,dev.thinqclimate.core.Home.Cycle c){
        Long end=s.endsAt(c); if(end!=null)return time(end)+" 완료 예정";
        if(c.phase()==dev.thinqclimate.core.Home.Phase.RESERVED&&c.startsIn()!=null)return time(s.fetchedAt()+c.startsIn()*1000L)+" 시작 예정";
        return c.label();
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
