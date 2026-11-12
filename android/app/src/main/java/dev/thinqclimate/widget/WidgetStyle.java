package dev.thinqclimate.widget;

import android.graphics.Color;
import java.util.Locale;
import org.json.*;

/** Widget-local colors. Foreground/muted colors follow the background for legibility. */
record WidgetStyle(int background, int accent, int opacity) {
    static final String[] NAMES={"아이보리","포레스트","슬레이트","오션","라벤더","피치"};
    static final WidgetStyle[] PRESETS={
        new WidgetStyle(Color.rgb(250,248,242),Color.rgb(47,108,83),100),
        new WidgetStyle(Color.rgb(31,59,48),Color.rgb(183,224,170),100),
        new WidgetStyle(Color.rgb(35,43,54),Color.rgb(173,210,236),100),
        new WidgetStyle(Color.rgb(228,241,249),Color.rgb(43,101,145),100),
        new WidgetStyle(Color.rgb(239,233,248),Color.rgb(111,77,151),100),
        new WidgetStyle(Color.rgb(251,234,221),Color.rgb(158,80,54),100)
    };
    WidgetStyle { background=background|0xff000000;accent=accent|0xff000000;opacity=Math.max(0,Math.min(100,opacity)); }
    static WidgetStyle defaults(){return PRESETS[0];}
    int surface(){return (Math.round(255*opacity/100f)<<24)|(background&0xffffff);}
    int foreground(){
        int dark=Color.rgb(34,47,42),light=Color.rgb(247,250,248);
        int chosen=contrast(background,dark)>=contrast(background,light)?dark:light;
        if(contrast(background,chosen)>=4.5)return chosen;
        return contrast(background,Color.BLACK)>=contrast(background,Color.WHITE)?Color.BLACK:Color.WHITE;
    }
    int muted(){int muted=blend(foreground(),background,.68f);return contrast(background,muted)>=4.5?muted:foreground();}
    int warning(){return luminance(background)>.35?Color.rgb(151,80,28):Color.rgb(244,195,132);}
    static int blend(int a,int b,float amount){return Color.rgb(Math.round(Color.red(a)*amount+Color.red(b)*(1-amount)),Math.round(Color.green(a)*amount+Color.green(b)*(1-amount)),Math.round(Color.blue(a)*amount+Color.blue(b)*(1-amount)));}
    private static double linear(int c){double s=c/255.0;return s<=.04045?s/12.92:Math.pow((s+.055)/1.055,2.4);}
    static double luminance(int c){return .2126*linear(Color.red(c))+.7152*linear(Color.green(c))+.0722*linear(Color.blue(c));}
    static double contrast(int a,int b){double x=luminance(a),y=luminance(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
    static int parseColor(String value){String v=value.trim();if(!v.matches("#?[0-9a-fA-F]{6}"))throw new IllegalArgumentException("#RRGGBB 형식으로 입력해 주세요");return Color.parseColor(v.startsWith("#")?v:"#"+v);}
    static String hex(int color){return String.format(Locale.ROOT,"#%06X",color&0xffffff);}
    String encode(){try{return new JSONObject().put("background",background).put("accent",accent).put("opacity",opacity).toString();}catch(JSONException e){throw new IllegalStateException(e);}}
    static WidgetStyle decode(String value){try{JSONObject o=new JSONObject(value);return new WidgetStyle(o.getInt("background"),o.getInt("accent"),o.optInt("opacity",100));}catch(JSONException e){return defaults();}}
}
