package dev.guga.dsh;
import android.app.AlertDialog;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;

final class FileLibraryPage {
 final MainActivity host;View page;LinearLayout list,actions;Button mode,all,remove;boolean selecting,deleting;
 JSONArray rows=new JSONArray();final Set<String> selected=new LinkedHashSet<>();
 FileLibraryPage(MainActivity host){this.host=host;}
 void open(){
  LinearLayout body=new LinearLayout(host);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(host.dp(4),host.dp(12),host.dp(4),host.dp(16));host.addFileCleanupSetting(body);
  actions=host.row();all=host.button("全选",v->{if(selected.size()==rows.length())selected.clear();else for(int n=0;n<rows.length();n++)selected.add(rows.optJSONObject(n).optString("path"));render();});
  remove=host.button("删除",v->confirmDelete());remove.setTextColor(0xFFB3261E);actions.addView(all,new LinearLayout.LayoutParams(0,host.dp(48),1));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,host.dp(48),1);rp.leftMargin=host.dp(12);actions.addView(remove,rp);body.addView(actions);
  list=new LinearLayout(host);list.setOrientation(LinearLayout.VERTICAL);body.addView(list);page=host.showPage("文件库",body);
  LinearLayout header=(LinearLayout)((LinearLayout)page).getChildAt(0);header.addView(new View(host),new LinearLayout.LayoutParams(0,1,1));mode=host.button("多选",v->setSelecting(!selecting));header.addView(mode,new LinearLayout.LayoutParams(-2,host.dp(48)));render();load();
 }
 void setSelecting(boolean enabled){if(deleting)return;selecting=enabled;selected.clear();render();}
 void load(){host.io.execute(()->{try{JSONArray result=new FileTools(host).list();host.runOnUiThread(()->{if(host.isDestroyed())return;rows=result;render();});}catch(Exception e){host.toast(e.getMessage());}});}
 void render(){
  mode.setText(selecting?"取消":"多选");mode.setEnabled(!deleting);actions.setVisibility(selecting?View.VISIBLE:View.GONE);all.setText(rows.length()>0&&selected.size()==rows.length()?"取消全选":"全选");all.setEnabled(!deleting&&rows.length()>0);remove.setText(deleting?"正在删除…":"删除（"+selected.size()+"）");remove.setEnabled(!deleting&&!selected.isEmpty());list.removeAllViews();
  for(int n=0;n<rows.length();n++){
   JSONObject item=rows.optJSONObject(n);String path=item.optString("path");
   LinearLayout row=host.row();row.setPadding(host.dp(8),host.dp(8),host.dp(8),host.dp(8));row.setMinimumHeight(host.dp(64));row.setContentDescription(path);
   if(selecting){CheckBox check=new CheckBox(host);check.setChecked(selected.contains(path));check.setClickable(false);check.setFocusable(false);row.addView(check,new LinearLayout.LayoutParams(host.dp(48),host.dp(48)));}
   else {ImageView icon=new ImageView(host);icon.setImageResource(R.drawable.ic_folder);icon.setImageTintList(android.content.res.ColorStateList.valueOf(host.muted));row.addView(icon,new LinearLayout.LayoutParams(host.dp(32),host.dp(32)));}
   LinearLayout words=new LinearLayout(host);words.setOrientation(LinearLayout.VERTICAL);words.setPadding(host.dp(12),0,0,0);TextView name=host.label(path,16,host.ink);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);words.addView(name);words.addView(host.label(Math.max(1,item.optLong("bytes")/1024)+" KB",12,host.muted));row.addView(words,new LinearLayout.LayoutParams(0,-2,1));
   row.setEnabled(!deleting);row.setOnClickListener(v->{if(deleting)return;if(selecting){if(!selected.remove(path))selected.add(path);render();}else host.fileActions(path);});list.addView(row,new LinearLayout.LayoutParams(-1,-2));
  }
  if(rows.length()==0){TextView empty=host.label("这里还是空的",16,host.muted);empty.setPadding(host.dp(8),host.dp(20),0,0);list.addView(empty);}
 }
 void confirmDelete(){
  if(selected.isEmpty()||deleting)return;
  AlertDialog dialog=new AlertDialog.Builder(host).setTitle("删除 "+selected.size()+" 个文件？").setMessage("将删除文件库中的所选文件，无法恢复。聊天中对应的文件链接可能失效；不会删除相册原图或已导出的文件。")
   .setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->deleteSelected()).create();dialog.show();host.styleDialog(dialog,true);
 }
 void deleteSelected(){
  if(deleting)return;deleting=true;List<String> targets=new ArrayList<>(selected);render();
  host.io.execute(()->{
   Set<String> deleted=new HashSet<>();List<String> failures=new ArrayList<>();FileTools tools=new FileTools(host);
   for(String path:targets)try{tools.deleteLibraryFile(path);deleted.add(path);}catch(Exception e){failures.add(path);}
   JSONArray refreshed=null;try{refreshed=tools.list();}catch(Exception ignored){}
   final JSONArray result=refreshed;
   host.runOnUiThread(()->{if(host.isDestroyed())return;deleting=false;selected.removeAll(deleted);
    if(result!=null)rows=result;else{JSONArray remaining=new JSONArray();for(int n=0;n<rows.length();n++)if(!deleted.contains(rows.optJSONObject(n).optString("path")))remaining.put(rows.optJSONObject(n));rows=remaining;}
    if(failures.isEmpty()){selecting=false;selected.clear();}render();
    if(!failures.isEmpty())host.toast("部分文件未删除，可重试：\n"+String.join("\n",failures));
   });
  });
 }
}
