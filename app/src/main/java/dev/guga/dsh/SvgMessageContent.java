package dev.guga.dsh;

import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.regex.*;
import java.nio.charset.StandardCharsets;

/** Closed SVG fences become stable inline previews; unfinished fences remain Markdown. */
final class SvgMessageContent extends LinearLayout {
  static final Pattern FENCE=Pattern.compile("(?m)^ {0,3}(`{3,}|~{3,})([^\\r\\n]*)\\r?\\n");
  static final class Part {final String text;final boolean svg,code;final String raw;Part(String t,boolean s){this(t,s,false,"");}Part(String t,boolean s,boolean c,String r){text=t;svg=s;code=c;raw=r;}}
  static List<Part> parts(String source){
    List<Part> out=new ArrayList<>();Matcher open=FENCE.matcher(source);int from=0,search=0;
    while(open.find(search)){
      String fence=open.group(1),lang=open.group(2).trim();
      Pattern end=Pattern.compile("(?m)^ {0,3}"+Pattern.quote(fence.substring(0,1))+"{"+fence.length()+",}[ \\t]*(?:\\r?$)");
      Matcher close=end.matcher(source);boolean closed=close.find(open.end());int finish=closed?close.start():source.length();
      String body=source.substring(open.end(),finish).trim();
      if(closed&&(lang.equalsIgnoreCase("svg")||lang.equalsIgnoreCase("xml")||lang.equalsIgnoreCase("html")||lang.isEmpty())&&SvgPreview.standalone(body)){
        if(open.start()>from)out.add(new Part(source.substring(from,open.start()),false));
        out.add(new Part(SvgPreview.extract(body),true));from=close.end();
      }else{
        if(open.start()>from)out.add(new Part(source.substring(from,open.start()),false));
        int endIndex=closed?close.end():source.length();
        out.add(new Part(source.substring(open.start(),endIndex),false,true,source.substring(open.end(),finish)));from=endIndex;
      }
      if(!closed)break;
      search=close.end();
    }
    if(from<source.length())out.add(new Part(source.substring(from),false));return out;
  }
  final ChatTurnView owner;final MainActivity host;final List<Part> rendered=new ArrayList<>();String previousSource;
  SvgMessageContent(ChatTurnView owner){super(owner.host);this.owner=owner;host=owner.host;setOrientation(VERTICAL);setBackground(host.ui.bubble(UiStyle.LOW,false));setClipToOutline(true);setVisibility(GONE);}
  boolean update(String source){
    if(source.equals(previousSource))return getVisibility()==VISIBLE;
    previousSource=source;
    List<Part> next=parts(source);boolean has=false;for(Part p:next)has|=p.svg||p.code;
    setVisibility(has?VISIBLE:GONE);if(!has){removeAllViews();rendered.clear();return false;}
    for(int n=0;n<next.size();n++){
      Part p=next.get(n);boolean reuse=n<rendered.size()&&rendered.get(n).svg==p.svg&&rendered.get(n).code==p.code&&(!p.svg||rendered.get(n).text.equals(p.text));
      if(!reuse){while(getChildCount()>n)removeViewAt(n);while(rendered.size()>n)rendered.remove(n);
        View child=p.svg?new Card(p.text):p.code?new CodeCard():owner.bubble(false);
        if(!p.svg&&!p.code){TextView text=(TextView)child;text.setBackground(null);ChatMarkdown.links(text,host);text.setOnClickListener(v->{if(host.messageActions.active())select();else MessagePreview.open(host,owner.markdown.source);});text.setOnLongClickListener(v->{select();return true;});}
        LayoutParams lp=new LayoutParams(-2,-2);lp.bottomMargin=0;addView(child,lp);rendered.add(p);
      }
      if(p.code)((CodeCard)getChildAt(n)).update(p.raw);
      if(!p.svg&&!p.code&&!p.text.equals(getChildAt(n).getTag())){TextView text=(TextView)getChildAt(n);new ChatMarkdown(host).update(text,p.text);text.setTag(p.text);}
      rendered.set(n,p);
    }
    while(getChildCount()>next.size())removeViewAt(next.size());while(rendered.size()>next.size())rendered.remove(next.size());return true;
  }
  void select(){host.messageActions.toggle(owner.turnId+"/a");}
  final class CodeCard extends LinearLayout {
    final TextView text;final ImageButton copy;final Runnable resetCopy;String raw="";
    CodeCard(){super(host);setOrientation(VERTICAL);setBackground(host.ui.shape(0xFFE8E1F0,12));
      LinearLayout bar=new LinearLayout(host);bar.setGravity(Gravity.END);
      copy=host.icon(R.drawable.ic_copy,"复制代码",v->{});
      resetCopy=()->{copy.setImageResource(R.drawable.ic_copy);copy.setContentDescription("复制代码");};
      copy.setOnClickListener(v->{((android.content.ClipboardManager)host.getSystemService(android.content.Context.CLIPBOARD_SERVICE)).setPrimaryClip(android.content.ClipData.newPlainText("代码",raw));copy.removeCallbacks(resetCopy);copy.setImageResource(R.drawable.ic_check);copy.setContentDescription("已复制代码");copy.postDelayed(resetCopy,1600);});
      bar.addView(copy,new LayoutParams(host.dp(48),host.dp(48)));addView(bar,new LayoutParams(-1,-2));
      text=host.label("",14,UiStyle.INK);text.setTypeface(android.graphics.Typeface.MONOSPACE);text.setHorizontallyScrolling(false);text.setPadding(host.dp(14),0,host.dp(14),host.dp(14));text.setBreakStrategy(android.graphics.text.LineBreaker.BREAK_STRATEGY_SIMPLE);text.setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE);
      addView(text,new LayoutParams(-1,-2));int width=Math.max(host.dp(96),(int)(host.getResources().getDisplayMetrics().widthPixels*.82f)-host.dp(32));setMinimumWidth(width);text.setMaxWidth(width);
      text.setOnClickListener(v->{if(host.messageActions.active())select();else MessagePreview.open(host,owner.markdown.source);});text.setOnLongClickListener(v->{select();return true;});
    }
    @Override protected void onDetachedFromWindow(){copy.removeCallbacks(resetCopy);resetCopy.run();super.onDetachedFromWindow();}
    void update(String value){if(!raw.equals(value)){raw=value;text.setText(value);}}
  }
  final class Card extends FrameLayout {
    final String source;final InlineSvgView preview;
    Card(String source){super(host);this.source=source;
      int width=Math.max(host.dp(96),(int)(host.getResources().getDisplayMetrics().widthPixels*.82f)-host.dp(32));
      int height=(int)Math.max(1,width/InlineSvgView.aspectRatio(source));
      preview=new InlineSvgView(host);preview.setBackground(null);addView(preview,new FrameLayout.LayoutParams(width,height));preview.content(source);preview.html=preview.html.replace("background:#fdfbff","background:transparent");
      preview.touch.setContentDescription("SVG 图像，点击放大，长按保存或分享");
      preview.touch.setOnClickListener(v->{if(host.messageActions.active())select();else withFile(path->SvgPreview.open(host,source,path));});
      preview.touch.setOnLongClickListener(v->{if(host.messageActions.active())select();else withFile(path->WorkspaceFiles.actions(host,path,false));return true;});
    }
    void withFile(java.util.function.Consumer<String> ready){host.io.execute(()->{try{
      byte[] bytes=source.getBytes(StandardCharsets.UTF_8);byte[] hash=java.security.MessageDigest.getInstance("SHA-256").digest(bytes);StringBuilder id=new StringBuilder();for(byte b:hash)id.append(String.format(java.util.Locale.ROOT,"%02x",b));
      String path="svg-previews/"+id+".svg";java.io.File file=new FileTools(host).resolve(path);file.getParentFile().mkdirs();java.nio.file.Files.write(file.toPath(),bytes);
      host.runOnUiThread(()->{if(!host.isDestroyed())ready.accept(path);});
    }catch(Exception e){host.toast("无法保存 SVG："+e.getMessage());}});}
  }
}
