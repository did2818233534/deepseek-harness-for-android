package dev.guga.dsh;

import android.content.Context;
import android.graphics.*;
import android.widget.SeekBar;

/** Discrete native seek interaction with a compact pill track and visible stops. */
final class EffortSlider extends SeekBar {
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final float density;
  EffortSlider(Context context) {
    super(context);density=getResources().getDisplayMetrics().density;
    setPadding(dp(18),0,dp(18),0);setSplitTrack(false);setBackground(null);
  }
  private int dp(int value){return Math.round(value*density);}
  @Override protected synchronized void onDraw(Canvas canvas) {
    float left=getPaddingLeft(),right=getWidth()-getPaddingRight(),cy=getHeight()/2f;
    boolean rtl=getLayoutDirection()==LAYOUT_DIRECTION_RTL;
    float fraction=getMax()==0?0:getProgress()/(float)getMax();
    float x=rtl?right-fraction*(right-left):left+fraction*(right-left);
    float half=dp(12);
    paint.setStyle(Paint.Style.FILL);paint.setColor(0xFFDCD5E5);
    canvas.drawRoundRect(left-half,cy-half,right+half,cy+half,half,half,paint);
    paint.setColor(UiStyle.ACCENT);
    canvas.drawRoundRect(rtl?x-half:left-half,cy-half,rtl?right+half:x+half,cy+half,half,half,paint);
    for(int n=0;n<=getMax();n++) {
      float tick=rtl?right-n*(right-left)/getMax():left+n*(right-left)/getMax();
      paint.setColor(n<=getProgress()?0xFFB9A6DF:0xFFA49AAC);canvas.drawCircle(tick,cy,dp(2),paint);
    }
    paint.setColor(0xFFD1C5E1);canvas.drawCircle(x,cy,dp(15),paint);
    paint.setColor(Color.WHITE);canvas.drawCircle(x,cy,dp(14),paint);
  }
}
