package dev.guga.dsh;

import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.*;

/** A stable view per turn: streaming updates do not replace the composer or transcript. */
final class ChatTurnView extends LinearLayout {
  final MainActivity host;
  final TextView user, answer, reasoning, steps, waiting;
  final LinearLayout process, details, header;
  final TextView caption;
  final SvgMessageContent svgContent;
  final ImageView chevron;
  final ProgressBar progress;
  boolean expanded, initialized, wasActive;
  String selectionKey="";
  String turnId="", attachmentKey="";
  final LinearLayout attachmentRow, outputFiles, assistantBody;
  String outputKey="";
  final ChatMarkdown markdown;

  ChatTurnView(MainActivity host) {
    super(host); this.host=host; setOrientation(VERTICAL);
    markdown=new ChatMarkdown(host);
    user=bubble(true); addView(user, bubbleParams(true));
    attachmentRow=new LinearLayout(host);attachmentRow.setOrientation(VERTICAL);addView(attachmentRow,bubbleParams(true));
    process=new LinearLayout(host); process.setOrientation(VERTICAL);
    LayoutParams processParams=new LayoutParams(-1,-2); processParams.bottomMargin=host.dp(10);
    addView(process,processParams);
    header=new LinearLayout(host); header.setGravity(Gravity.CENTER_VERTICAL);
    header.setMinimumHeight(host.dp(48)); header.setPadding(host.dp(8),0,host.dp(8),0);
    header.setBackground(host.ui.surface(android.graphics.Color.TRANSPARENT,16));
    progress=new ProgressBar(host);progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(UiStyle.ACCENT));header.addView(progress,new LayoutParams(host.dp(18),host.dp(18)));progress.setVisibility(GONE);
    caption=host.label("",13,host.muted);
    header.addView(caption,new LayoutParams(0,-2,1));
    chevron=new ImageView(host); chevron.setImageResource(R.drawable.ic_chevron_down);
    chevron.setColorFilter(host.muted); header.addView(chevron,new LayoutParams(host.dp(18),host.dp(18)));
    header.setFocusable(true); header.setOnClickListener(v -> {expanded=!expanded; showExpansion();});
    process.addView(header,new LayoutParams(-1,-2));
    details=new LinearLayout(host); details.setOrientation(VERTICAL);
    details.setPadding(host.dp(14),host.dp(8),host.dp(14),host.dp(12));
    details.setBackground(host.ui.surface(UiStyle.LOW,16));
    reasoning=host.label("",14,host.muted); reasoning.setTextIsSelectable(true); reasoning.setLineSpacing(host.dp(3),1);
    details.addView(reasoning,new LayoutParams(-1,-2));
    steps=host.label("",13,host.muted); steps.setTextIsSelectable(true); steps.setLineSpacing(host.dp(5),1);
    details.addView(steps,new LayoutParams(-1,-2)); process.addView(details,new LayoutParams(-1,-2));
    assistantBody=new LinearLayout(host);assistantBody.setOrientation(VERTICAL);assistantBody.setBackground(host.ui.bubble(UiStyle.LOW,false));assistantBody.setClipToOutline(true);addView(assistantBody,bubbleParams(false));
    answer=bubble(false);answer.setBackground(null);assistantBody.addView(answer,new LayoutParams(-2,-2));
    svgContent=new SvgMessageContent(this);svgContent.setBackground(null);assistantBody.addView(svgContent,new LayoutParams(-2,-2));
    outputFiles=new LinearLayout(host);outputFiles.setOrientation(VERTICAL);outputFiles.setVisibility(GONE);outputFiles.setPadding(host.dp(14),host.dp(2),host.dp(14),host.dp(8));assistantBody.addView(outputFiles,new LayoutParams(-2,-2));
    ChatMarkdown.links(answer,host);
    user.setOnLongClickListener(v -> {host.messageActions.toggle(turnId+"/u");return true;});
    answer.setOnLongClickListener(v -> {host.messageActions.toggle(turnId+"/a");return true;});
    user.setOnClickListener(v -> {if(host.messageActions.active())host.messageActions.toggle(turnId+"/u");else MessagePreview.open(host,user.getText().toString());});
    answer.setOnClickListener(v -> {if(host.messageActions.active())host.messageActions.toggle(turnId+"/a");else MessagePreview.open(host,markdown.source==null?"":markdown.source);});
    waiting=host.label("正在请求模型…",13,host.muted); waiting.setPadding(host.dp(14),host.dp(4),0,host.dp(14));
    addView(waiting,new LayoutParams(-2,-2));
  }
  TextView bubble(boolean mine) {
    TextView view=host.label("",16,host.ink); view.setTextIsSelectable(false);
    view.setLineSpacing(host.dp(3),1); view.setPadding(host.dp(14),host.dp(12),host.dp(14),host.dp(12));
    view.setMaxWidth(Math.max(host.dp(96),(int)(host.getResources().getDisplayMetrics().widthPixels*.82f)-host.dp(32)));
    view.setBackground(host.ui.bubble(mine ? 0xFFE8DEF8 : UiStyle.LOW,mine)); return view;
  }
  LayoutParams bubbleParams(boolean mine) {
    LayoutParams p=new LayoutParams(-2,-2); p.gravity=mine?Gravity.END:Gravity.START; p.bottomMargin=host.dp(14); return p;
  }
  static void text(TextView view,String value) {
    if(!value.contentEquals(view.getText())) view.setText(value);
    view.setVisibility(value.isEmpty()?GONE:VISIBLE);
  }
  void update(JSONObject turn) {
    turnId=turn.optString("id");selection();
    JSONArray attached=turn.optJSONArray("attachments");String key=attached==null?"":attached.toString();
    if(!key.equals(attachmentKey)){
      attachmentKey=key;attachmentRow.removeAllViews();
      if(attached!=null)for(int n=0;n<attached.length();n++){
        JSONObject item=attached.optJSONObject(n);if(item==null)continue;
        attachmentRow.addView(Attachments.card(host,item,null,()->{if(host.messageActions.active())host.messageActions.toggle(turnId+"/u");else if(item.optString("mime").startsWith("image/"))WorkspaceFiles.preview(host,item.optString("path"));else WorkspaceFiles.open(host,item.optString("path"));},()->host.messageActions.toggle(turnId+"/u")));
      }
    }
    attachmentRow.setVisibility(attachmentRow.getChildCount()>0?VISIBLE:GONE);
    text(user,turn.optString("userText"));
    String reply=turn.optString("text");
    boolean inline=svgContent.update(reply);
    if(inline){markdown.source=reply;answer.setVisibility(GONE);}else markdown.update(answer,reply);
    JSONArray generated=ArtifactViews.collect(turn);String generatedKey=generated.toString();
    if(!generatedKey.equals(outputKey)){outputKey=generatedKey;ArtifactViews.fill(this,generated);}
    String thought=turn.optString("reasoning"); JSONArray tools=turn.optJSONArray("tools");
    boolean hasTools=tools!=null&&tools.length()>0, hasProcess=!thought.isEmpty()||hasTools;
    boolean active=turn.optBoolean("processActive")&&turn.optBoolean("busy");
    process.setVisibility(hasProcess?VISIBLE:GONE);progress.setVisibility(active?VISIBLE:GONE);
    if(!initialized || active!=wasActive) expanded=active;
    initialized=true; wasActive=active;
    text(reasoning,thought);
    StringBuilder summary=new StringBuilder(); String running="";
    if(tools!=null) for(int n=0;n<tools.length();n++) {
      JSONObject tool=tools.optJSONObject(n); if(tool==null) continue;
      String verb=toolLabel(tool.optString("name")); String state=tool.optString("status");
      boolean pending=state.equals("running")||state.equals("pending");
      if(pending) running="正在"+verb+"…";
      if(summary.length()>0) summary.append('\n');
      summary.append(pending?"正在":state.equals("error")?"未完成：":state.equals("interrupted")?"已停止：":"已完成：").append(verb);
      String detail=tool.optString("detail"); if(!detail.isEmpty()) summary.append(" · ").append(detail);
    }
    text(steps,summary.toString()); steps.setPadding(0,!thought.isEmpty()&&hasTools?host.dp(12):0,0,0);
    String label=active?(!running.isEmpty()?running:!thought.isEmpty()?"正在思考…":"正在执行…"):
      (!thought.isEmpty()?"思考过程":"执行过程");
    long elapsed=turn.optLong("processElapsedMs");
    if(elapsed>0) label+=" · "+duration(elapsed);
    if(!label.contentEquals(caption.getText()))caption.setText(label); showExpansion();
    waiting.setText("正在请求模型…");
    waiting.setVisibility(turn.optBoolean("busy")&&!hasProcess&&turn.optString("text").isEmpty()?VISIBLE:GONE);
  }
  String attachmentSummary(){
    StringBuilder out=new StringBuilder();try{JSONArray a=new JSONArray(attachmentKey.isEmpty()?"[]":attachmentKey);for(int n=0;n<a.length();n++)out.append("\n附件：").append(a.getJSONObject(n).optString("name"));}catch(Exception ignored){}return out.toString();
  }
  void selection() {
    if(host.messageActions==null)return;
    String nextSelection=turnId+":"+host.messageActions.selected.contains(turnId+"/u")+":"+host.messageActions.selected.contains(turnId+"/a");
    if(nextSelection.equals(selectionKey))return;
    selectionKey=nextSelection;
    boolean attachmentSelected=host.messageActions.selected.contains(turnId+"/u");
    attachmentRow.setBackground(attachmentSelected?host.ui.surface(0xFFDDD0F4,16):null);
    attachmentRow.setContentDescription((attachmentSelected?"已选择，":"")+"用户附件，长按选择");
    assistantBody.setBackground(host.ui.bubble(host.messageActions.selected.contains(turnId+"/a")?0xFFDDD0F4:UiStyle.LOW,false));
    for(boolean mine:new boolean[]{true,false}){
      TextView view=mine?user:answer;boolean selected=host.messageActions.selected.contains(turnId+(mine?"/u":"/a"));
      android.graphics.drawable.GradientDrawable background=host.ui.bubble(selected?0xFFDDD0F4:mine?0xFFE8DEF8:UiStyle.LOW,mine);
      if(selected)background.setStroke(host.dp(2),UiStyle.ACCENT);if(mine)view.setBackground(background);
      view.setContentDescription((selected?"已选择，":"")+(mine?"用户消息":"助手消息")+"，长按选择");
    }
  }
  void showExpansion() {
    details.setVisibility(expanded?VISIBLE:GONE); chevron.setRotation(expanded?180:0);
    header.setContentDescription(caption.getText()+ (expanded?"，收起":"，展开"));
  }
  static String duration(long millis) {
    long seconds=Math.max(1,(millis+999)/1000);
    return seconds<60?seconds+" 秒":seconds/60+" 分 "+seconds%60+" 秒";
  }
  String outputSummary() {
    StringBuilder text=new StringBuilder();
    try {JSONArray files=new JSONArray(outputKey.isEmpty()?"[]":outputKey);
      for(int n=0;n<files.length();n++){JSONObject f=files.getJSONObject(n);String path=f.optString("path");
        if(WorkspaceFiles.linkPath(path)!=null)text.append("\n\n[").append(f.optString("name",path).replace("[","\\[").replace("]","\\]")).append("](").append(android.net.Uri.encode(path,"/")).append(")");
      }
    }catch(Exception ignored){}return text.toString();
  }
  static String toolLabel(String name) {
    switch(name) {
      case "compact_context": return "压缩上下文";
      case "web_search": return "搜索网页";
      case "web_fetch": return "读取网页";
      case "list_files": return "查看文件列表";
      case "read_file": return "读取文件";
      case "write_file": return "写入文件";
      case "create_document": return "生成文档";
      default: return name.isEmpty()?"执行工具":name;
    }
  }
}
