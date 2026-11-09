package dev.guga.dsh;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.*;
import dev.guga.ota.ModuleRuntime;

/** One primary update action; recovery stays in the secondary options sheet. */
final class ModuleUpdatePanel {
 static final int IMPORT_MODULE=42;
 final MainActivity activity;
 TextView status;
 Button choose;
 ImageButton more;
 Dialog options;
 AlertDialog confirmation;
 final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
 final Runnable poll;
 ModuleUpdatePanel(MainActivity a){activity=a;poll=()->{if(!activity.isDestroyed()&&activity.settingsPages.modulePanel==this)refresh(null);};}
 void addTo(LinearLayout entries) {
  activity.settingSection(entries,"动态模块更新");
  LinearLayout heading=activity.row();
  heading.addView(activity.label("业务模块",16,activity.ink),new LinearLayout.LayoutParams(0,-2,1));
  more=activity.icon(R.drawable.ic_file_more,"更新选项",v->showOptions());
  heading.addView(more,new LinearLayout.LayoutParams(activity.dp(48),activity.dp(48)));
  activity.addHelp(heading,"业务模块","选择更新文件后，应用会自动校验并准备。准备完成后可立即重启应用，也可稍后在下次冷启动时自动启用；聊天和设置保留。右侧更多菜单可重新选择文件或恢复内置版本。系统权限、原生库或宿主资源变更仍需安装完整 APK。");
  entries.addView(heading);
  status=activity.label(ModuleRuntime.description(activity),14,activity.muted);entries.addView(status);
  choose=activity.button("导入更新",v->{
   if(ModuleRuntime.isRunning())return;
   if(ModuleRuntime.hasPending(activity))applyUpdate();else pickFile();
  });activity.addAction(entries,choose,false);
  refresh(null);
 }
 void pickFile() {
  try{activity.startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),IMPORT_MODULE);}
  catch(Exception e){activity.toast("无法打开文件选择器");}
 }
 void applyUpdate() {
  if(confirmation!=null&&confirmation.isShowing())return;
  confirmation=new AlertDialog.Builder(activity).setTitle("更新已准备好")
   .setMessage("更新需要重启应用。是否现在重启？聊天和设置会保留。选择稍后，将在下次冷启动时自动应用。")
   .setNegativeButton("稍后",null).setPositiveButton("立即重启",(d,w)->{
    if(HarnessService.instance!=null&&HarnessService.instance.taskActive){activity.toast("请等待当前任务完成后再更新");return;}
    activity.stopService(new Intent(activity,HarnessService.class));
    activity.startActivity(new Intent(activity,dev.guga.ota.RestartActivity.class)
      .putExtra("sourcePid",android.os.Process.myPid()));
   }).create();confirmation.show();activity.styleDialog(confirmation,false);
 }
 void showOptions() {
  if(ModuleRuntime.isRunning())return;
  LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);
  if(ModuleRuntime.hasPending(activity)) {
   Button replace=activity.button("重新选择更新",v->{options.dismiss();pickFile();});
   body.addView(replace,activity.spacedControl());
  }
  Button restore=activity.button("恢复内置版本",v->{options.dismiss();confirmRestore();});
  body.addView(restore,activity.spacedControl());
  options=activity.bottomPanel("更新选项",body);
 }
 void confirmRestore() {
  confirmation=new AlertDialog.Builder(activity).setTitle("恢复内置版本")
   .setMessage("下次启动使用随安装包提供的版本，并取消已准备的更新。不会删除聊天和设置。")
   .setNegativeButton("取消",null).setPositiveButton("恢复",(d,w)->{
    try{ModuleRuntime.restoreBuiltin(activity);refresh(null);}catch(Exception e){refresh(e.getMessage());}
   }).create();confirmation.show();activity.styleDialog(confirmation,false);
 }
 void refresh(String error) {
  if(status==null)return;
  boolean running=ModuleRuntime.isRunning();
  handler.removeCallbacks(poll);if(running)handler.postDelayed(poll,500);
  boolean pending=!running&&ModuleRuntime.hasPending(activity);
  status.setText(error!=null?error:running?"正在校验并准备更新…":ModuleRuntime.description(activity));
  choose.setText(running?"正在准备…":pending?"应用更新":"导入更新");
  choose.setEnabled(!running);choose.setAlpha(running?0.5f:1f);
  more.setVisibility(!running&&(ModuleRuntime.isDynamic()||pending)?View.VISIBLE:View.GONE);
 }
 void importResult(Uri uri) {
  ModuleRuntime.prepare(activity,uri,error->{if(!activity.isFinishing()&&!activity.isDestroyed()){refresh(error);if(error==null)applyUpdate();}});refresh(null);
 }
}
