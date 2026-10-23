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
    return "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'><meta http-equiv='Content-Security-Policy' content=\"default-src 'none'; style-src 'unsafe-inline'\"><style>"
      +"body{margin:0;padding:24px 20px 40px;color:#292532;background:#fdfbff;font:16px/1.8 sans-serif;overflow-wrap:anywhere}main{max-width:780px;margin:auto}h1,h2,h3{line-height:1.4;margin:1.5em 0 .65em;font-weight:650}h1{font-size:1.55em}h2{font-size:1.35em}h3{font-size:1.18em}p{margin:.8em 0}a{color:#6750a4}ul,ol{padding-left:1.5em}li{margin:.3em 0}blockquote{margin:1em 0;padding:8px 16px;border-left:3px solid #b6a4d4;background:#f2eff5;border-radius:0 12px 12px 0;color:#655d71}pre{padding:16px;background:#f0eaf5;border-radius:14px;overflow:auto;line-height:1.6;white-space:pre}code{font-family:monospace;font-size:.88em;background:#f0eaf5;border-radius:4px;padding:2px 4px}pre code{padding:0}table{border-collapse:collapse;width:100%;margin:1em 0;font-size:.94em}th,td{padding:12px;text-align:left;vertical-align:top;border:1px solid #e2dce9;min-width:70px}th{background:#ece6f0}tr:nth-child(even){background:#f6f3f8}hr{border:0;border-top:1px solid #e2dce9;margin:1.6em 0}img{display:none}"
      +"</style></head><body><main>"+org.jsoup.Jsoup.clean(renderer.render(parser.parse(source)),org.jsoup.safety.Safelist.relaxed())+"</main></body></html>";
  }
  static void open(MainActivity host,String source) {
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
        Uri uri=request.getUrl();if("https".equals(uri.getScheme())||"http".equals(uri.getScheme()))try{host.startActivity(new Intent(Intent.ACTION_VIEW,uri));}catch(ActivityNotFoundException e){host.toast("没有可打开链接的应用");}return true;
      }
    });
    LinearLayout actions=host.row();actions.setGravity(Gravity.END);actions.setPadding(0,host.dp(2),0,host.dp(2));
    Button select=new Button(host);select.setText("全选");host.ui.action(select,false);
    Button copy=new Button(host);copy.setText("复制");host.ui.action(copy,false);
    for(Button button:new Button[]{select,copy}){
      button.setTextSize(13);button.setMinWidth(0);button.setMinimumWidth(0);button.setPadding(host.dp(14),0,host.dp(14),0);button.setElevation(0);button.setStateListAnimator(null);
      button.setBackground(new android.graphics.drawable.InsetDrawable(host.ui.surface(UiStyle.SURFACE,18),0,host.dp(7),0,host.dp(7)));
      LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,host.dp(48));p.setMargins(host.dp(8),0,0,0);actions.addView(button,p);
    }
    select.setOnClickListener(v->{web.requestFocus();web.evaluateJavascript("(function(){var r=document.createRange();r.selectNodeContents(document.querySelector('main'));var s=window.getSelection();var all=s.toString()===r.toString()&&s.toString().length>0;s.removeAllRanges();if(!all)s.addRange(r);return !all;})()",result->select.setText("true".equals(result)?"全不选":"全选"));});
    copy.setOnClickListener(v->web.evaluateJavascript("window.getSelection().toString()",result->{
      String selected="";try{Object value=new org.json.JSONTokener(result).nextValue();if(value instanceof String)selected=(String)value;}catch(Exception ignored){}
      ((android.content.ClipboardManager)host.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("消息",selected.isEmpty()?source:selected));Toast.makeText(host,selected.isEmpty()?"已复制完整 Markdown":"已复制选中文字",Toast.LENGTH_SHORT).show();
    }));
    header.addView(actions);page.addView(web,new LinearLayout.LayoutParams(-1,0,1));
    host.pageCleanup.put(page,()->{web.stopLoading();web.destroy();});
    web.loadDataWithBaseURL("https://preview.invalid/",html(source),"text/html","UTF-8",null);
  }
}
