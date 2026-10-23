package dev.guga.dsh;

import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.util.Arrays;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;

/** Local read-only document preview, isolated from remote content and app credentials. */
final class MessagePreview {
  static String html(String source) {
    var renderer=HtmlRenderer.builder().escapeHtml(true)
      .extensions(Arrays.asList(TablesExtension.create(),StrikethroughExtension.create())).build();
    var parser=org.commonmark.parser.Parser.builder().extensions(Arrays.asList(TablesExtension.create(),StrikethroughExtension.create())).build();
    org.commonmark.node.Node document=parser.parse(source);
    document.accept(new org.commonmark.node.AbstractVisitor(){@Override public void visit(org.commonmark.node.Link link){String path=WorkspaceFiles.linkPath(link.getDestination());if(path!=null)link.setDestination("https://workspace.invalid/"+Uri.encode(path));visitChildren(link);}});
    org.jsoup.nodes.Document clean=org.jsoup.Jsoup.parseBodyFragment(org.jsoup.Jsoup.clean(renderer.render(document),org.jsoup.safety.Safelist.relaxed()));
    int block=0;for(org.jsoup.nodes.Element pre:clean.select("pre")){pre.prependElement("a").attr("href","https://preview.invalid/code-copy/"+block++).attr("class","code-copy").attr("aria-label","复制代码").text("⧉");}
    return "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'><meta http-equiv='Content-Security-Policy' content=\"default-src 'none'; style-src 'unsafe-inline'\"><style>*{-webkit-tap-highlight-color:transparent;}"
      +"body{margin:0;padding:24px 20px 40px;color:#292532;background:#fdfbff;font:16px/1.8 sans-serif;overflow-wrap:anywhere}main{max-width:780px;margin:auto}h1,h2,h3{line-height:1.4;margin:1.5em 0 .65em;font-weight:650}h1{font-size:1.55em}h2{font-size:1.35em}h3{font-size:1.18em}p{margin:.8em 0}a{color:#6750a4}ul,ol{padding-left:1.5em}li{margin:.3em 0}blockquote{margin:1em 0;padding:8px 16px;border-left:3px solid #b6a4d4;background:#f2eff5;border-radius:0 12px 12px 0;color:#655d71}pre{padding:16px;background:#f0eaf5;border-radius:14px;overflow:auto;line-height:1.6;white-space:pre-wrap;overflow-wrap:anywhere;position:relative;padding-top:48px}a.code-copy{position:absolute;right:8px;top:4px;display:grid;place-items:center;width:40px;height:40px;text-decoration:none;font-size:24px}code{font-family:monospace;font-size:.88em;background:#f0eaf5;border-radius:4px;padding:2px 4px}pre code{padding:0}table{border-collapse:collapse;width:100%;margin:1em 0;font-size:.94em}th,td{padding:12px;text-align:left;vertical-align:top;border:1px solid #e2dce9;min-width:70px}th{background:#ece6f0}tr:nth-child(even){background:#f6f3f8}hr{border:0;border-top:1px solid #e2dce9;margin:1.6em 0}img{display:none}"
      +"</style></head><body><main>"+clean.body().html()+"</main></body></html>";
  }
  static void open(MainActivity host,String source) {
    if(SvgPreview.standalone(source)){SvgPreview.open(host,source);return;}
    LinearLayout body=new LinearLayout(host);body.setOrientation(LinearLayout.VERTICAL);
    LinearLayout page=host.showPage("消息预览",body);
    // Reuse navigation/insets but give the document its own full-height scrolling surface.
    page.removeViewAt(1);
    LinearLayout header=(LinearLayout)page.getChildAt(0);
    TextView title=host.label("消息预览",18,UiStyle.INK);title.setPadding(host.dp(12),0,0,0);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
    WebView web=new WebView(host);web.setBackgroundColor(UiStyle.BACKGROUND);
    // Only app-owned selection commands run; sanitized HTML and CSP forbid document scripts.
    web.getSettings().setJavaScriptEnabled(true);web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);web.getSettings().setBlockNetworkLoads(true);
    web.setWebViewClient(new WebViewClient(){
      @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest request){
        Uri uri=request.getUrl();if("https".equals(uri.getScheme())&&"preview.invalid".equals(uri.getHost())&&uri.getPath()!=null&&uri.getPath().matches("/code-copy/[0-9]+")){
          int index=Integer.parseInt(uri.getLastPathSegment());v.evaluateJavascript("document.querySelectorAll('pre code')["+index+"].textContent",result->{try{Object value=new org.json.JSONTokener(result).nextValue();if(value instanceof String){((ClipboardManager)host.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("代码",(String)value));v.evaluateJavascript("(function(){var b=document.querySelectorAll('a.code-copy')["+index+"];if(!b)return;clearTimeout(b.copyTimer);b.textContent='✓';b.setAttribute('aria-label','已复制代码');b.copyTimer=setTimeout(function(){b.textContent='⧉';b.setAttribute('aria-label','复制代码');},1600);})()",null);}}catch(Exception ignored){}});return true;
        }if(WorkspaceFiles.openLink(host,uri.toString()))return true;if("https".equals(uri.getScheme())||"http".equals(uri.getScheme()))try{host.startActivity(new Intent(Intent.ACTION_VIEW,uri));}catch(ActivityNotFoundException e){host.toast("没有可打开链接的应用");}return true;
      }
    });
    LinearLayout actions=host.row();actions.setGravity(Gravity.END);actions.setPadding(0,host.dp(2),0,host.dp(2));
    ImageButton select=host.icon(R.drawable.ic_select_all,"全选",v->{});
    ImageButton copy=host.icon(R.drawable.ic_copy,"复制",v->{});
    for(ImageButton button:new ImageButton[]{select,copy}){
      button.setColorFilter(UiStyle.ACCENT);button.setPadding(host.dp(12),host.dp(12),host.dp(12),host.dp(12));
      button.setScaleType(ImageView.ScaleType.FIT_CENTER);
      button.setBackground(host.ui.surface(UiStyle.SURFACE,24));
      button.setTooltipText(button.getContentDescription());
      LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(host.dp(48),host.dp(48));p.setMargins(host.dp(8),0,0,0);actions.addView(button,p);
    }
    select.setOnClickListener(v->{
      // Selection.toString and Range.toString differ for rendered tables/line breaks.
      // The displayed action defines intent; never infer it by comparing text.
      boolean chooseAll=!select.isSelected();select.setEnabled(false);
      if(chooseAll)web.requestFocus();
      String script=chooseAll
        ? "(function(){var s=window.getSelection(),r=document.createRange();r.selectNodeContents(document.querySelector('main'));s.removeAllRanges();s.addRange(r);return s.rangeCount>0&&!s.isCollapsed;})()"
        : "(function(){window.getSelection().removeAllRanges();return false;})()";
      web.evaluateJavascript(script,result->{boolean selected="true".equals(result);select.setSelected(selected);select.setEnabled(true);select.setContentDescription(selected?"全不选":"全选");select.setTooltipText(select.getContentDescription());select.setImageResource(selected?R.drawable.ic_deselect_all:R.drawable.ic_select_all);select.setColorFilter(selected?Color.WHITE:UiStyle.ACCENT);select.setBackground(host.ui.surface(selected?UiStyle.ACCENT:UiStyle.SURFACE,24));});
    });
    copy.setOnClickListener(v->web.evaluateJavascript("(function(){var selected=window.getSelection().toString();if(selected)return selected;var root=document.querySelector('main');var buttons=root.querySelectorAll('a.code-copy');buttons.forEach(function(b){b.style.display='none';});var text=root.innerText;buttons.forEach(function(b){b.style.display='';});return text;})()",result->{
      String selected="";try{Object value=new org.json.JSONTokener(result).nextValue();if(value instanceof String)selected=(String)value;}catch(Exception ignored){}
      ((android.content.ClipboardManager)host.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("消息",selected));Toast.makeText(host,"已复制文字",Toast.LENGTH_SHORT).show();
    }));
    header.addView(actions);page.addView(web,new LinearLayout.LayoutParams(-1,0,1));
    host.pageCleanup.put(page,()->{web.stopLoading();web.destroy();});
    web.loadDataWithBaseURL("https://preview.invalid/",html(source),"text/html","UTF-8",null);
  }
}
