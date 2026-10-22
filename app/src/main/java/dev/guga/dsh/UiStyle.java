package dev.guga.dsh;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

final class UiStyle {
  static final int INK = 0xFF1E222B, MUTED = 0xFF7F8490, ACCENT = 0xFF6750A4;
  static final int BACKGROUND = 0xFFFDFBFF, SURFACE = 0xFFECE6F0, LOW = 0xFFF2EFF5;
  static final int DANGER = 0xFFC64040, GAP = 12, CONTROL = 48, CARD = 24;
  final Context context;
  UiStyle(Context context) { this.context = context; }
  int dp(int n) { return Math.round(n * context.getResources().getDisplayMetrics().density); }
  GradientDrawable shape(int color, int radius) {
    GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
  }
  RippleDrawable surface(int color, int radius) {
    return new RippleDrawable(ColorStateList.valueOf(0x226750A4), shape(color, radius), null);
  }
  void action(Button button, boolean primary) {
    button.setAllCaps(false); button.setTextSize(15);
    button.setTextColor(primary ? Color.WHITE : INK);
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
