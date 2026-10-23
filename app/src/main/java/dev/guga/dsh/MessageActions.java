package dev.guga.dsh;

import android.content.*;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

final class MessageActions {
  static final int SAVE_MARKDOWN=12;
  final MainActivity host;
  final LinkedHashSet<String> selected=new LinkedHashSet<>();
  final LinearLayout bar;
  final TextView count;
  final Button share,save,fork,all;
  Button copy;
  String pendingExport="";
  boolean selecting;
  MessageActions(MainActivity host,LinearLayout root,View composer) {
    this.host=host;bar=new LinearLayout(host);bar.setOrientation(LinearLayout.VERTICAL);
    bar.setPadding(host.dp(8),host.dp(8),host.dp(8),host.dp(8));bar.setBackground(host.ui.surface(UiStyle.LOW,24));
    LinearLayout heading=host.row();heading.setGravity(Gravity.CENTER_VERTICAL);bar.addView(heading);
    count=host.label("",14,host.muted);count.setPadding(host.dp(8),0,0,0);heading.addView(count,new LinearLayout.LayoutParams(0,-2,1));
    ImageButton close=new ImageButton(host);close.setImageResource(R.drawable.ic_close);close.setColorFilter(UiStyle.MUTED);close.setBackground(host.ui.surface(android.graphics.Color.TRANSPARENT,24));close.setContentDescription("取消选择");close.setOnClickListener(v->clear());heading.addView(close,new LinearLayout.LayoutParams(host.dp(48),host.dp(48)));
    LinearLayout row=host.row();bar.addView(row);
    all=action(row,"全选",R.drawable.ic_select_all,v->selectAll());copy=action(row,"复制",R.drawable.ic_copy,v->copySelected());share=action(row,"分享",R.drawable.ic_share,v->export(false));save=action(row,"保存",R.drawable.ic_download,v->export(true));fork=action(row,"分支",R.drawable.ic_branch,v->fork());
    root.addView(bar);bar.setVisibility(View.GONE);
  }
  Button action(LinearLayout row,String title,int icon,View.OnClickListener listener) {
    Button button=new Button(host);button.setText(title);host.ui.action(button,false);button.setTextSize(12);button.setPadding(host.dp(4),host.dp(8),host.dp(4),host.dp(6));button.setMinWidth(0);button.setMinimumWidth(0);
    button.setBackground(host.ui.surface(android.graphics.Color.TRANSPARENT,16));button.setTextColor(UiStyle.ACCENT);
    android.graphics.drawable.Drawable image=host.getDrawable(icon).mutate();image.setTint(UiStyle.ACCENT);image.setBounds(0,0,host.dp(22),host.dp(22));button.setCompoundDrawables(null,image,null,null);button.setCompoundDrawablePadding(host.dp(4));
    button.setOnClickListener(listener);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,host.dp(72),1);p.setMargins(host.dp(3),0,host.dp(3),0);row.addView(button,p);return button;
  }
  void selectAll(){
    LinkedHashSet<String> ids=selectableIds();
    boolean cancel=!ids.isEmpty()&&selected.containsAll(ids);
    selected.clear();if(!cancel)selected.addAll(ids);selecting=true;refresh();
  }
  LinkedHashSet<String> selectableIds(){
    LinkedHashSet<String> ids=new LinkedHashSet<>();
    for(int n=0;n<host.transcriptTurns.length();n++){
      JSONObject turn=host.transcriptTurns.optJSONObject(n);if(turn==null)continue;String id=turn.optString("id",String.valueOf(n));
      if(!turn.optString("userText").isEmpty()||(turn.optJSONArray("attachments")!=null&&turn.optJSONArray("attachments").length()>0))ids.add(id+"/u");
      if(!turn.optString("text").isEmpty()||ArtifactViews.collect(turn).length()>0)ids.add(id+"/a");
    }
    return ids;
  }
  boolean active(){return selecting;}
  void toggle(String id){selecting=true;if(!selected.remove(id))selected.add(id);refresh();}
  void clear(){selecting=false;selected.clear();refresh();}
  void refresh(){bar.setVisibility(active()?View.VISIBLE:View.GONE);host.composer.setVisibility(active()?View.GONE:View.VISIBLE);
    if(!active()){for(ChatTurnView view:host.turnViews.values())view.selection();return;}
    count.setText("已选择 "+selected.size()+" 条消息");updateForkEnabled();
    LinkedHashSet<String> ids=selectableIds();all.setText(!ids.isEmpty()&&selected.containsAll(ids)?"取消全选":"全选");
    for(Button b:new Button[]{share,save,copy}){b.setEnabled(!selected.isEmpty());b.setAlpha(selected.isEmpty()?.4f:1f);}
    for(ChatTurnView view:host.turnViews.values())view.selection();
    if(active())((android.view.inputmethod.InputMethodManager)host.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(host.input.getWindowToken(),0);
  }
  void updateForkEnabled(){boolean enabled=selected.size()==1&&!host.busy;fork.setEnabled(enabled);fork.setAlpha(enabled?1f:.4f);}
  void fork(){
    if(host.busy){host.toast("请先停止当前任务");return;}
    if(selected.size()!=1){host.toast("请选择一条消息作为分支起点");return;}
    String key=selected.iterator().next();int slash=key.lastIndexOf('/');
    try {host.request("/session/fork",new JSONObject().put("turnId",key.substring(0,slash)).put("includeAssistant",key.endsWith("/a")),()->{clear();host.revision=-1;host.currentId="";host.toast("已创建分支，原对话已保留");});}
    catch(Exception e){host.toast(e.getMessage());}
  }
  static String userSource(JSONObject turn){
    StringBuilder out=new StringBuilder(turn.optString("userText"));JSONArray files=turn.optJSONArray("attachments");
    if(files!=null)for(int n=0;n<files.length();n++){JSONObject file=files.optJSONObject(n);if(file!=null)out.append("\n附件：").append(file.optString("name"));}return out.toString();
  }
  static String assistantSource(JSONObject turn){
    StringBuilder out=new StringBuilder(turn.optString("text"));JSONArray files=ArtifactViews.collect(turn);
    for(int n=0;n<files.length();n++){JSONObject f=files.optJSONObject(n);String path=f.optString("path");
      if(WorkspaceFiles.linkPath(path)!=null)out.append("\n\n[").append(f.optString("name",path).replace("[","\\[").replace("]","\\]")).append("](").append(Uri.encode(path,"/")).append(")");
    }return out.toString();
  }
  String selectedSource(){return selectionText(false);}
  String selectionText(boolean headings){
    StringBuilder out=new StringBuilder();
    for(int n=0;n<host.transcriptTurns.length();n++){
      JSONObject turn=host.transcriptTurns.optJSONObject(n);if(turn==null)continue;String id=turn.optString("id",String.valueOf(n));
      for(boolean mine:new boolean[]{true,false})if(selected.contains(id+(mine?"/u":"/a"))){
        if(out.length()>0)out.append("\n\n");if(headings)out.append(mine?"## 用户\n\n":"## 助手\n\n");out.append(mine?userSource(turn):assistantSource(turn));
      }
    }return out.toString();
  }
  void copySelected(){
    ((ClipboardManager)host.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Markdown",selectedSource()));
    copy.setText("已复制");copy.removeCallbacks(resetCopy);copy.postDelayed(resetCopy,1600);
  }
  final Runnable resetCopy=()->{if(copy!=null)copy.setText("复制");};
  String markdown(){return "# "+host.chatTitle.getText().toString().replace('\n',' ')+"\n\n"+selectionText(true)+"\n\n";}
  static File write(Context c,String markdown) throws IOException {
    File dir=ChatShareProvider.directory(c);if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("无法创建分享文件");
    File[] old=dir.listFiles();if(old!=null)for(File file:old)if(file.lastModified()<System.currentTimeMillis()-7*86400000L)file.delete();
    File file=new File(dir,"chat-"+UUID.randomUUID()+".md");try(FileOutputStream out=new FileOutputStream(file)){out.write(markdown.getBytes(StandardCharsets.UTF_8));}return file;
  }
  static Intent shareIntent(Context c,File file){
    Uri uri=new Uri.Builder().scheme("content").authority(c.getPackageName()+".chat-share").appendPath(file.getName()).build();
    Intent intent=new Intent(Intent.ACTION_SEND).setType("text/markdown").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    intent.setClipData(ClipData.newRawUri("Markdown 对话",uri));return intent;
  }
  void export(boolean local){
    String markdown=markdown();host.io.execute(()->{
      try{File file=write(host,markdown);host.runOnUiThread(()->{
        try{if(local){pendingExport=file.getName();host.startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/markdown").putExtra(Intent.EXTRA_TITLE,"对话记录.md"),SAVE_MARKDOWN);}
          else host.startActivity(Intent.createChooser(shareIntent(host,file),"分享 Markdown 文件"));
        }catch(Exception e){host.toast("无法打开分享或保存界面："+e.getMessage());}
      });}catch(Exception e){host.toast(e.getMessage());}
    });
  }
  void saveTo(Uri uri) throws IOException {
    if(!pendingExport.matches("chat-[a-f0-9-]+\\.md"))throw new IOException("请重新选择消息后保存");
    File file=new File(ChatShareProvider.directory(host),pendingExport);
    try(InputStream in=new FileInputStream(file);OutputStream out=host.getContentResolver().openOutputStream(uri,"wt")){
      if(out==null)throw new IOException("无法打开保存位置");FileTools.copy(in,out,FileTools.MAX_BYTES);
    }
    host.toast("对话已保存为 Markdown");
  }
}
