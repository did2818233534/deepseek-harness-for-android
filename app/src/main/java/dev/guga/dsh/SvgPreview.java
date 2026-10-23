package dev.guga.dsh;

import android.content.*;
import android.webkit.*;
import android.widget.*;
import java.util.*;
import java.util.regex.*;
import org.jsoup.nodes.*;
import org.jsoup.parser.Parser;

/** Untrusted model SVG is reduced to geometry and declarative animation before display. */
final class SvgPreview {
  private static final Pattern SVG=Pattern.compile("(?is)<svg\\b[^>]*>.*?</svg\\s*>");
  private static final Set<String> TAGS=new HashSet<>(Arrays.asList("svg","g","defs","path","rect","circle","ellipse","line","polyline","polygon","text","tspan","title","desc","linearGradient","radialGradient","stop","clipPath","mask","pattern","animate","animateTransform","animateMotion","mpath","use"));
  private static final Set<String> ATTRS=new HashSet<>(Arrays.asList("id","viewBox","width","height","x","y","x1","y1","x2","y2","cx","cy","r","rx","ry","d","points","fill","fill-opacity","fill-rule","stroke","stroke-width","stroke-opacity","stroke-linecap","stroke-linejoin","stroke-dasharray","stroke-dashoffset","opacity","transform","preserveAspectRatio","font-size","font-family","font-weight","text-anchor","dominant-baseline","dx","dy","offset","stop-color","stop-opacity","gradientUnits","gradientTransform","spreadMethod","fx","fy","clip-path","clipPathUnits","mask","maskUnits","maskContentUnits","patternUnits","patternContentUnits","patternTransform","attributeName","attributeType","type","from","to","by","values","keyTimes","keySplines","calcMode","dur","begin","end","repeatCount","repeatDur","restart","additive","accumulate","path","rotate","href","xlink:href"));
  static String extract(String source) {
    if(source==null||source.length()>512*1024)return "";
    Matcher m=SVG.matcher(source);return m.find()?m.group():"";
  }
  static boolean standalone(String source) {
    String s=source.trim().replaceFirst("(?is)^```(?:svg|xml|html)?\\s*","").replaceFirst("\\s*```$","").trim();
    return !extract(s).isEmpty()&&s.equals(extract(s));
  }
  static String sanitize(String source) {
    String raw=extract(source);if(raw.isEmpty())return "";
    Document doc=org.jsoup.Jsoup.parse(raw,"",Parser.xmlParser());
    Element root=doc.children().first();if(root==null||!root.tagName().equals("svg"))return "";
    for(Element el:new ArrayList<>(root.getAllElements())) {
      if(!TAGS.contains(el.tagName())){el.remove();continue;}
      for(Attribute attr:new ArrayList<>(el.attributes().asList())){
        String key=attr.getKey(),value=attr.getValue().trim();
        boolean safe=ATTRS.contains(key)&&!value.contains("\\")&&!value.contains("<")&&!value.contains(">");
        if(key.equals("href")||key.equals("xlink:href"))safe=value.matches("#[A-Za-z_][A-Za-z0-9_.-]*");
        if(value.toLowerCase(Locale.ROOT).contains("url("))safe=safe&&value.matches("url\\(#[A-Za-z_][A-Za-z0-9_.-]*\\)");
        if(key.equals("attributeName"))safe=ATTRS.contains(value)&&!value.equals("href")&&!value.equals("xlink:href")&&!value.equals("id")&&!value.equals("attributeName");
        if(key.equals("begin")||key.equals("end"))safe=value.matches("[0-9.ms; +\\-]+|indefinite");
        if(!safe)el.removeAttr(key);
      }
      // An animation without a safe target cannot be allowed to mutate another attribute.
      if(el.tagName().equals("animate")&&!el.hasAttr("attributeName"))el.remove();
    }
    root.attr("xmlns","http://www.w3.org/2000/svg");
    doc.outputSettings().prettyPrint(false);return root.outerHtml();
  }
  static String html(String source) {
    return "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'><meta http-equiv='Content-Security-Policy' content=\"default-src 'none'; style-src 'unsafe-inline'; script-src 'none'\"><style>html,body{margin:0;min-height:100%;background:#fdfbff}body{display:grid;place-items:center;min-height:100vh}svg{max-width:100%;max-height:90vh;width:100%;height:auto}</style></head><body>"+sanitize(source)+"</body></html>";
  }
  static void open(MainActivity host,String source) {
    LinearLayout page=host.showPage("SVG 预览",new LinearLayout(host));page.removeViewAt(1);
    LinearLayout header=(LinearLayout)page.getChildAt(0);
    TextView title=host.label("SVG 预览",18,UiStyle.INK);title.setPadding(host.dp(12),0,0,0);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
    ImageButton copy=host.icon(R.drawable.ic_copy,"复制 SVG",v->{
      ((ClipboardManager)host.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("SVG",extract(source)));
      Toast.makeText(host,"已复制 SVG",Toast.LENGTH_SHORT).show();
    });copy.setColorFilter(UiStyle.ACCENT);copy.setBackground(host.ui.surface(UiStyle.SURFACE,24));header.addView(copy,new LinearLayout.LayoutParams(host.dp(48),host.dp(48)));
    WebView web=new WebView(host);web.setBackgroundColor(UiStyle.BACKGROUND);
    web.getSettings().setJavaScriptEnabled(false);web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);web.getSettings().setBlockNetworkLoads(true);
    web.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return true;}});
    page.addView(web,new LinearLayout.LayoutParams(-1,0,1));host.pageCleanup.put(page,()->{web.stopLoading();web.destroy();});
    web.loadDataWithBaseURL("https://svg.invalid/",html(source),"text/html","UTF-8",null);
  }
}
