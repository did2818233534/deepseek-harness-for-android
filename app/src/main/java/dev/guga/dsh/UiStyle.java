package dev.guga.dsh;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

final class UiStyle {
  static int INK = 0xFF1E222B, MUTED = 0xFF7F8490, ACCENT = 0xFF6750A4;
  static int BACKGROUND = 0xFFFDFBFF, SURFACE = 0xFFECE6F0, LOW = 0xFFF2EFF5;
  static int DANGER = 0xFFC64040, GAP = 12, CONTROL = 48, CARD = 24;
  static boolean dark;
  static void configure(Context context){
    String mode=context.getSharedPreferences("appearance",0).getString("theme","system");
    dark=mode.equals("dark") || (mode.equals("system") && (context.getResources().getConfiguration().uiMode & 48)==32);
    INK=dark?0xFFE9E4EE:0xFF1E222B; MUTED=dark?0xFFB0A9BA:0xFF7F8490; ACCENT=dark?0xFFCBB6FF:0xFF6750A4;
    BACKGROUND=dark?0xFF151218:0xFFFDFBFF;SURFACE=dark?0xFF302A38:0xFFECE6F0;LOW=dark?0xFF25212C:0xFFF2EFF5;DANGER=dark?0xFFFFB4AB:0xFFC64040;
  }
  static int color(int value){
    if(!dark)return value;
    switch(value){
      case 0xFFFDFBFF:return BACKGROUND;
      case 0xFFF1EDF7:case 0xFFF7F8FC:return LOW;
      case 0xFFE6E8EF:return 0xFF514959;
      case 0xFFF2EFF5:return LOW;
      case 0xFFECE6F0:case 0xFFE8E1F0:case 0xFFE8DEF8:return SURFACE;
      case 0xFFDDD0F4:return 0xFF49405E;
      case 0xFF1E222B:case 0xFF51416D:return INK;
      case 0xFF7F8490:return MUTED;
      case 0xFF6750A4:return ACCENT;
      case 0xFFE1DBE8:case 0xFFDCD5E5:return 0xFF514959;
      default:return value;
    }
  }
  static String webColors(String html){
    if(!dark)return html;
    int end=html.indexOf("</style>");if(end<0)return html;
    return html.substring(0,end).replace("#fdfbff","#151218").replace("#292532","#e9e4ee").replace("#6750a4","#cbb6ff").replace("#f2eff5","#25212c").replace("#f0eaf5","#302a38").replace("#ece6f0","#302a38").replace("#f6f3f8","#25212c").replace("#e2dce9","#514959").replace("#655d71","#b0a9ba")+html.substring(end);
  }
  final Context context;
  UiStyle(Context context) { this.context = context; }
  int dp(int n) { return Math.round(n * context.getResources().getDisplayMetrics().density); }
  GradientDrawable shape(int color, int radius) {
    GradientDrawable d = new GradientDrawable(); d.setColor(color(color)); d.setCornerRadius(dp(radius)); return d;
  }
  RippleDrawable surface(int color, int radius) {
    return new RippleDrawable(ColorStateList.valueOf(0x226750A4), shape(color, radius), shape(Color.WHITE, radius));
  }
  void action(Button button, boolean primary) {
    button.setAllCaps(false); button.setTextSize(15);
    button.setTextColor(primary ? (dark?BACKGROUND:Color.WHITE) : INK);
    button.setMinimumHeight(dp(CONTROL)); button.setMinHeight(dp(CONTROL));
    button.setPadding(dp(20), dp(8), dp(20), dp(8));
    button.setBackground(surface(primary ? ACCENT : SURFACE, CARD));
  }
  void dialogAction(Button button, boolean destructive) {
    button.setAllCaps(false); button.setTextSize(15);
    button.setTextColor(destructive ? DANGER : ACCENT);
    button.setMinimumHeight(dp(CONTROL)); button.setMinHeight(dp(CONTROL));
    button.setPadding(dp(16), dp(8), dp(16), dp(8));
    button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x226750A4),
      null, shape(Color.WHITE, 24)));
  }
  GradientDrawable bubble(int color, boolean user) {
    GradientDrawable d = shape(color, 20);
    float r = dp(20), tail = dp(5);
    d.setCornerRadii(new float[]{user?r:tail,user?r:tail,user?tail:r,user?tail:r,r,r,r,r});
    return d;
  }
}
