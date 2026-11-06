package dev.guga.dsh;

import android.view.*;
import android.webkit.WebView;
import android.widget.FrameLayout;

/** Visible SVG renderers plus a bounded, short-lived offscreen cache. */
final class InlineSvgView extends FrameLayout {
  final MainActivity host;String html;WebView web;
  final View touch;
  boolean syncQueued;
  final Runnable syncLater=()->{syncQueued=false;sync();};
  final Runnable expire=this::release;
  boolean parked;
  final ViewTreeObserver.OnScrollChangedListener scrollListener=this::scheduleSync;
  void scheduleSync(){if(!syncQueued){syncQueued=true;postDelayed(syncLater,80);}}
  InlineSvgView(MainActivity host){super(host);this.host=host;setBackground(host.ui.shape(UiStyle.LOW,18));setClipToOutline(true);touch=new View(host);addView(touch,new LayoutParams(-1,-1));}
  static double aspectRatio(String source){
    try{var root=org.jsoup.Jsoup.parse(SvgPreview.extract(source),"",org.jsoup.parser.Parser.xmlParser()).children().first();
      String[] box=root.attr("viewBox").trim().split("[ ,\\s]+");
      double ratio=box.length==4?Double.parseDouble(box[2])/Double.parseDouble(box[3]):Double.parseDouble(root.attr("width").replace("px",""))/Double.parseDouble(root.attr("height").replace("px",""));
      if(Double.isFinite(ratio)&&ratio>0)return ratio;
    }catch(Exception ignored){}return 1.5;
  }
  void content(String source){
    double ratio=aspectRatio(source);ViewGroup.LayoutParams p=getLayoutParams();
    if(p!=null&&p.width>0){int width=p.width;double height=width/ratio;int limit=host.dp(2000);if(height>limit){height=limit;width=Math.max(1,(int)Math.round(height*ratio));}p.width=width;p.height=Math.max(1,(int)Math.round(height));setLayoutParams(p);}
    String nextHtml=SvgPreview.html(source).replace("background:#fdfbff","background:transparent").replace("min-height:100%","height:100%").replace("min-height:100vh","height:100vh").replace("max-height:90vh;width:100%;height:auto","max-height:100vh;width:100%;height:100%;display:block");
    boolean changed=!nextHtml.equals(html);html=nextHtml;
    if(web!=null&&changed)web.loadDataWithBaseURL("https://svg.invalid/",html,"text/html","UTF-8",null);
    sync();
  }
  void sync(){
    android.graphics.Rect rect=new android.graphics.Rect();boolean visible=isAttachedToWindow()&&getWindowVisibility()==VISIBLE&&getGlobalVisibleRect(rect);
    // Keep an existing renderer just outside the viewport while the user scrolls
    // back and forth, but never allocate offscreen renderers speculatively.
    if(!visible&&web!=null&&isAttachedToWindow()&&getWindowVisibility()==VISIBLE){
      android.graphics.Rect viewport=new android.graphics.Rect();int[] at=new int[2];getLocationOnScreen(at);
      if(host.scroll.getGlobalVisibleRect(viewport)){int margin=viewport.height();visible=at[1]+getHeight()>viewport.top-margin&&at[1]<viewport.bottom+margin;}
    }
    if(!visible){
      if(web!=null&&isAttachedToWindow()&&getWindowVisibility()==VISIBLE){
        if(!parked){parked=true;web.onPause();host.warmSvgViews.addLast(this);postDelayed(expire,20000);}
        while(host.warmSvgViews.size()>3)host.warmSvgViews.peekFirst().release();
      }else release();
      return;
    }
    if(parked){parked=false;removeCallbacks(expire);host.warmSvgViews.remove(this);if(web!=null)web.onResume();}
    if(web==null&&html!=null){web=new WebView(host);web.setBackgroundColor(android.graphics.Color.TRANSPARENT);web.getSettings().setJavaScriptEnabled(false);web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);web.getSettings().setBlockNetworkLoads(true);web.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);addView(web,0,new LayoutParams(-1,-1));web.loadDataWithBaseURL("https://svg.invalid/",html,"text/html","UTF-8",null);}
  }
  void release(){removeCallbacks(expire);host.warmSvgViews.remove(this);parked=false;if(web!=null){removeView(web);web.stopLoading();web.destroy();web=null;}}
  @Override protected void onAttachedToWindow(){super.onAttachedToWindow();getViewTreeObserver().addOnScrollChangedListener(scrollListener);scheduleSync();}
  @Override protected void onDetachedFromWindow(){getViewTreeObserver().removeOnScrollChangedListener(scrollListener);removeCallbacks(syncLater);syncQueued=false;release();super.onDetachedFromWindow();}
  @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);scheduleSync();}
}
