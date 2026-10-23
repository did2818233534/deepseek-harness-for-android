package dev.guga.dsh;

import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.ScrollView;

/** At most one history batch per upward-history gesture (including its fling). */
final class HistoryScrollView extends ScrollView {
  final MainActivity host;boolean gesture,requested;float downY;
  HistoryScrollView(MainActivity host){super(host);this.host=host;}
  void resetHistoryGesture(){gesture=false;requested=false;}
  void maybeLoad(){if(gesture&&!requested&&getScrollY()<=host.dp(120))requested=host.loadOlderTurns();}
  @Override public boolean dispatchTouchEvent(MotionEvent e){
    if(e.getActionMasked()==MotionEvent.ACTION_DOWN){gesture=true;requested=false;downY=e.getY();}
    if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&e.getY()-downY>ViewConfiguration.get(host).getScaledTouchSlop())maybeLoad();
    if(e.getActionMasked()==MotionEvent.ACTION_CANCEL)gesture=false;
    return super.dispatchTouchEvent(e);
  }
  @Override protected void onScrollChanged(int x,int y,int oldX,int oldY){super.onScrollChanged(x,y,oldX,oldY);if(y<oldY)maybeLoad();}
}
