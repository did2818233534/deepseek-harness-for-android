package dev.guga.dsh;

import android.app.*;
import android.net.Uri;
import android.view.View;
import android.widget.*;
import android.text.*;
import org.json.*;
import java.util.*;

/** Pick a destination before any external file is copied into the workspace. */
final class IncomingConversationPicker {
 final MainActivity host;final List<Uri> files;
 Dialog dialog;LinearLayout list;EditText search;Button confirm;TextView status;
 JSONArray sessions=new JSONArray();String selected=null;boolean loading=true,working=false;
 IncomingConversationPicker(MainActivity host,List<Uri> files){this.host=host;this.files=files;}
 void open(){
  dialog=new Dialog(host);
  LinearLayout body=new LinearLayout(host);body.setOrientation(LinearLayout.VERTICAL);body.setBackgroundColor(host.bg);body.setPadding(host.dp(16),host.dp(12),host.dp(16),host.dp(16));
  LinearLayout header=host.row();header.addView(host.icon(dev.guga.dsh.R.drawable.ic_close,"取消导入",v->dialog.dismiss()),new LinearLayout.LayoutParams(host.dp(48),host.dp(48)));
  TextView title=host.label("选择对话",18,host.ink);title.setGravity(android.view.Gravity.CENTER);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));header.addView(new View(host),new LinearLayout.LayoutParams(host.dp(48),1));body.addView(header);
  search=new EditText(host);search.setSingleLine();search.setHint("搜索对话");search.setTextSize(16);search.setPadding(host.dp(16),host.dp(10),host.dp(16),host.dp(10));search.setBackground(host.shape(UiStyle.SURFACE,14));
  LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=host.dp(12);sp.bottomMargin=host.dp(12);body.addView(search,sp);
  TextView filesLabel=host.label("待添加 "+files.size()+" 个文件",13,host.muted);body.addView(filesLabel);
  LinearLayout heading=host.row();heading.addView(host.label("最近对话",14,host.muted),new LinearLayout.LayoutParams(0,-2,1));
  Button create=host.button("新建对话",v->{selected="";render();});heading.addView(create,new LinearLayout.LayoutParams(-2,host.dp(48)));body.addView(heading);
  list=new LinearLayout(host);list.setOrientation(LinearLayout.VERTICAL);ScrollView history=new ScrollView(host);history.addView(list);body.addView(history,new LinearLayout.LayoutParams(-1,0,1));
  status=host.label("正在加载对话…",14,host.muted);status.setPadding(host.dp(8),host.dp(12),host.dp(8),host.dp(12));body.addView(status);
  confirm=host.button("添加到对话",v->choose());confirm.setEnabled(false);body.addView(confirm,new LinearLayout.LayoutParams(-1,host.dp(48)));
  dialog.setContentView(body);dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);dialog.show();host.trackDialog(dialog);dialog.getWindow().setLayout(-1,-1);
  search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){render();}public void afterTextChanged(Editable e){}});
  host.coreIo.execute(()->{try{JSONArray rows=Api.call("/sessions",new JSONObject()).getJSONArray("sessions");host.runOnUiThread(()->{if(!dialog.isShowing())return;sessions=rows;loading=false;render();});}catch(Exception e){host.runOnUiThread(()->{if(!dialog.isShowing())return;loading=false;status.setText("无法加载历史对话，可新建对话或返回后重试");confirm.setEnabled(selected!=null);});}});
 }
 void render(){
  if(working)return;list.removeAllViews();String query=search.getText().toString().trim().toLowerCase(Locale.ROOT);int count=0;
  for(int n=0;n<sessions.length();n++){
   JSONObject session=sessions.optJSONObject(n);String id=session.optString("id"),title=session.optString("title","新对话");
   if(!title.toLowerCase(Locale.ROOT).contains(query))continue;count++;
   LinearLayout row=host.row();row.setPadding(host.dp(4),host.dp(8),host.dp(4),host.dp(8));row.setContentDescription(title);row.setOnClickListener(v->{selected=id;render();});
   TextView avatar=host.label(title.isEmpty()?"聊":title.substring(0,1),18,host.accent);avatar.setGravity(android.view.Gravity.CENTER);avatar.setBackground(host.shape(UiStyle.SURFACE,10));row.addView(avatar,new LinearLayout.LayoutParams(host.dp(40),host.dp(40)));
   TextView name=host.label(title,16,host.ink);name.setSingleLine();name.setEllipsize(android.text.TextUtils.TruncateAt.END);name.setPadding(host.dp(12),0,host.dp(8),0);row.addView(name,new LinearLayout.LayoutParams(0,-2,1));
   TextView tick=host.label(id.equals(selected)?"✓":"",20,host.accent);row.addView(tick,new LinearLayout.LayoutParams(host.dp(24),-2));list.addView(row,new LinearLayout.LayoutParams(-1,-2));

  }
  status.setText(selected!=null?(selected.isEmpty()?"已选择：新对话":"已选择："+selectedTitle()):loading?"正在加载对话…":count==0?"没有找到对话，可新建对话":"请选择对话");confirm.setEnabled(selected!=null);
 }
 String selectedTitle(){for(int n=0;n<sessions.length();n++){JSONObject s=sessions.optJSONObject(n);if(s.optString("id").equals(selected))return s.optString("title","对话");}return "对话";}
 void choose(){
  if(selected==null||working)return;
  if(!selected.equals(host.currentId)&&(!host.input.getText().toString().isEmpty()||!host.attachments.pending.isEmpty())){
   AlertDialog warning=new AlertDialog.Builder(host).setTitle("切换对话？").setMessage("当前输入框有未发送的内容，切换会清空草稿。可以取消后先处理草稿，或选择当前对话。")
    .setNegativeButton("取消",null).setPositiveButton("继续",(d,w)->apply()).create();warning.show();host.styleDialog(warning,false);return;
  }
  apply();
 }
 void apply(){
  if(working)return;working=true;confirm.setEnabled(false);confirm.setText("正在准备…");dialog.setCancelable(false);
  final String target=selected;final boolean switching=!target.equals(host.currentId);
  host.coreIo.execute(()->{try{
   if(switching)Api.call("/select",target.isEmpty()?new JSONObject():new JSONObject().put("id",target));
   JSONObject state=Api.call("/state",new JSONObject().put("compact",true));
   host.runOnUiThread(()->{
    if(host.isDestroyed())return;
    if(switching){host.attachments.reset();host.input.setText("");}
    dialog.dismiss();host.returnToChat();host.revision=-1;host.render(state);
    int available=8-host.attachments.pending.size()-host.attachments.importing;
    if(available<=0){host.toast("请先处理输入框中已有的附件");return;}
    if(files.size()>available)host.toast("每条消息最多添加 8 个附件，其余文件请分批打开");
    host.attachments.importUris(files.subList(0,Math.min(available,files.size())));
   });
  }catch(Exception e){host.runOnUiThread(()->{working=false;dialog.setCancelable(true);confirm.setEnabled(true);confirm.setText("重试添加");status.setText("无法打开对话，请重试");});}});
 }
}
