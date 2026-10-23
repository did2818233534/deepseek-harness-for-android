package dev.guga.dsh;

import android.view.*;
import android.webkit.WebView;
import android.widget.FrameLayout;

/** Only visible SVG cards allocate a WebView; detach releases their renderer. */
final class InlineSvgView extends FrameLayout {
  final MainActivity host;String html;WebView web;
  final View touch;
  final ViewTreeObserver.OnScrollChangedListener scrollListener=this::sync;
  InlineSvgView(MainActivity host){super(host);this.host=host;setBackground(host.ui.shape(UiStyle.LOW,18));setClipToOutline(true);touch=new View(host);addView(touch,new LayoutParams(-1,-1));}
  void content(String source){html=SvgPreview.html(source).replace("max-height:90vh","max-height:100vh");sync();}
  void sync(){
    android.graphics.Rect rect=new android.graphics.Rect();boolean visible=isAttachedToWindow()&&getWindowVisibility()==VISIBLE&&getGlobalVisibleRect(rect);
    if(!visible){release();return;}
    if(web==null&&html!=null){web=new WebView(host);web.setBackgroundColor(UiStyle.LOW);web.getSettings().setJavaScriptEnabled(false);web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);web.getSettings().setBlockNetworkLoads(true);web.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);addView(web,0,new LayoutParams(-1,-1));web.loadDataWithBaseURL("https://svg.invalid/",html,"text/html","UTF-8",null);}
  }
  void release(){if(web!=null){removeView(web);web.stopLoading();web.destroy();web=null;}}
  @Override protected void onAttachedToWindow(){super.onAttachedToWindow();getViewTreeObserver().addOnScrollChangedListener(scrollListener);post(this::sync);}
  @Override protected void onDetachedFromWindow(){getViewTreeObserver().removeOnScrollChangedListener(scrollListener);release();super.onDetachedFromWindow();}
  @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);post(this::sync);}
}
