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
    html=SvgPreview.html(source).replace("background:#fdfbff","background:transparent").replace("min-height:100%","height:100%").replace("min-height:100vh","height:100vh").replace("max-height:90vh;width:100%;height:auto","max-height:100vh;width:100%;height:100%;display:block");
    if(web!=null)web.loadDataWithBaseURL("https://svg.invalid/",html,"text/html","UTF-8",null);
    sync();
  }
  void sync(){
    android.graphics.Rect rect=new android.graphics.Rect();boolean visible=isAttachedToWindow()&&getWindowVisibility()==VISIBLE&&getGlobalVisibleRect(rect);
    if(!visible){release();return;}
    if(web==null&&html!=null){web=new WebView(host);web.setBackgroundColor(android.graphics.Color.TRANSPARENT);web.getSettings().setJavaScriptEnabled(false);web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);web.getSettings().setBlockNetworkLoads(true);web.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);addView(web,0,new LayoutParams(-1,-1));web.loadDataWithBaseURL("https://svg.invalid/",html,"text/html","UTF-8",null);}
  }
  void release(){if(web!=null){removeView(web);web.stopLoading();web.destroy();web=null;}}
  @Override protected void onAttachedToWindow(){super.onAttachedToWindow();getViewTreeObserver().addOnScrollChangedListener(scrollListener);post(this::sync);}
  @Override protected void onDetachedFromWindow(){getViewTreeObserver().removeOnScrollChangedListener(scrollListener);release();super.onDetachedFromWindow();}
  @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);post(this::sync);}
}
