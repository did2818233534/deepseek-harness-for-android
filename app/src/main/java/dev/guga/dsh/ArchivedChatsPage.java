package dev.guga.dsh;
import android.view.*;
import android.widget.*;
import org.json.*;

final class ArchivedChatsPage {
 final MainActivity host;View page;LinearLayout list;boolean fetching;String previous="";
 final Runnable refresh=new Runnable(){public void run(){if(!host.isDestroyed()){if(host.currentPage==page)load();host.handler.postDelayed(this,1500);}}};
 ArchivedChatsPage(MainActivity host){this.host=host;}
 void open(){LinearLayout body=new LinearLayout(host);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(host.dp(8),host.dp(16),host.dp(8),host.dp(20));list=new LinearLayout(host);list.setOrientation(LinearLayout.VERTICAL);body.addView(list);page=host.showPage("已归档对话",body);host.pageCleanup.put(page,()->host.handler.removeCallbacks(refresh));refresh.run();}
 void load(){if(fetching)return;fetching=true;host.coreIo.execute(()->{try{JSONArray sessions=Api.call("/sessions",new JSONObject().put("archived",true)).getJSONArray("sessions");host.runOnUiThread(()->{fetching=false;if(host.currentPage!=page)return;String content=sessions.toString();if(content.equals(previous))return;previous=content;render(sessions);});}catch(Exception e){host.runOnUiThread(()->{fetching=false;if(host.currentPage==page){host.handler.removeCallbacks(refresh);host.toast(e.getMessage());}});}});}
 void render(JSONArray sessions){list.removeAllViews();if(sessions.length()==0)list.addView(host.label("暂无归档对话",16,host.muted));
  for(int n=0;n<sessions.length();n++){
   JSONObject session=sessions.optJSONObject(n),storage=session.optJSONObject("archiveStorage");String state=storage==null?"":storage.optString("state");boolean working=state.equals("packing")||state.equals("restoring");
   LinearLayout row=host.row();row.setBaselineAligned(false);row.setMinimumHeight(host.dp(56));row.setBackground(host.shape(UiStyle.SURFACE,20));
   LinearLayout words=new LinearLayout(host);words.setOrientation(LinearLayout.VERTICAL);words.setPadding(host.dp(16),host.dp(8),host.dp(8),host.dp(8));TextView title=host.label(session.optString("title","新对话"),17,host.ink);title.setSingleLine();title.setEllipsize(android.text.TextUtils.TruncateAt.END);words.addView(title);
   if(working||state.equals("error")){TextView detail=host.label(working?(state.equals("packing")?"正在归档…":"正在恢复…"):"处理失败，可恢复后重试",12,host.muted);words.addView(detail);if(state.equals("error"))detail.setOnClickListener(v->host.toast(storage.optString("error","归档失败，原文件或归档已保留")));}
   row.addView(words,new LinearLayout.LayoutParams(0,-2,1));
   if(working){ProgressBar spinner=new ProgressBar(host);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(host.dp(24),host.dp(24));sp.setMargins(host.dp(12),0,host.dp(12),0);row.addView(spinner,sp);}
   ImageButton restore=host.icon(R.drawable.ic_restore,"恢复对话",v->host.archiveSession(session,true,()->{previous="";load();}));restore.setPadding(host.dp(15),host.dp(15),host.dp(15),host.dp(15));restore.setEnabled(!working);row.addView(restore,new LinearLayout.LayoutParams(host.dp(48),host.dp(48)));
   ImageButton delete=host.icon(R.drawable.ic_delete,"删除对话",v->host.deleteSession(session,true,()->{previous="";load();}));delete.setImageTintList(android.content.res.ColorStateList.valueOf(0xFFC64040));delete.setPadding(host.dp(15),host.dp(15),host.dp(15),host.dp(15));delete.setEnabled(!working);row.addView(delete,new LinearLayout.LayoutParams(host.dp(48),host.dp(48)));
   LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=host.dp(12);list.addView(row,lp);
  }
 }
}
