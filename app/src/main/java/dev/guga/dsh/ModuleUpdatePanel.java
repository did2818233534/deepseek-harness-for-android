package dev.guga.dsh;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.widget.*;
import dev.guga.ota.ModuleRuntime;

/** Presentation only; the immutable host owns verification, activation and recovery. */
final class ModuleUpdatePanel {
 static final int IMPORT_MODULE=42;
 final MainActivity activity;
 TextView status;
 Button choose,exit,restore;
 final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
 final Runnable poll;
 ModuleUpdatePanel(MainActivity a){activity=a;poll=()->{if(!activity.isDestroyed()&&activity.settingsPages.modulePanel==this)refresh(null);};}
 void addTo(LinearLayout entries) {
  activity.settingSection(entries,"动态模块更新");
  entries.addView(activity.standaloneHelp("业务模块",16,activity.ink,"导入 .dshmodule 完整模块或 .dshota 增量包。校验通过后，下次打开应用自动启用，不需要系统安装。聊天和设置保留。系统权限、原生库或宿主资源变更仍需安装完整 APK。模块启动失败会恢复内置版本。"));
  status=activity.label(ModuleRuntime.description(activity),14,activity.muted);entries.addView(status);
  choose=activity.button("导入模块更新",v->{
   try{activity.startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),IMPORT_MODULE);}
   catch(Exception e){activity.toast("无法打开文件选择器");}
  });activity.addAction(entries,choose,false);
  exit=activity.button("退出并应用更新",v->{
   if(HarnessService.instance!=null&&HarnessService.instance.taskActive){activity.toast("请等待当前任务完成后再切换模块");return;}
   AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("应用模块更新")
    .setMessage("应用将退出。再次打开时启用已准备的模块，聊天和设置会保留。")
    .setNegativeButton("取消",null).setPositiveButton("退出应用",(d,w)->{
     activity.stopService(new Intent(activity,HarnessService.class));activity.finishAndRemoveTask();
     new android.os.Handler(activity.getMainLooper()).postDelayed(()->android.os.Process.killProcess(android.os.Process.myPid()),300);
    }).create();dialog.show();activity.styleDialog(dialog,false);
  });activity.addAction(entries,exit,false);
  restore=activity.button("恢复宿主内置模块",v->{
   AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("恢复内置模块")
    .setMessage("下次启动使用随宿主安装的业务版本，并取消已准备的模块更新。不会删除聊天和设置。")
    .setNegativeButton("取消",null).setPositiveButton("恢复",(d,w)->{
     try{ModuleRuntime.restoreBuiltin(activity);refresh(null);}catch(Exception e){refresh(e.getMessage());}
    }).create();dialog.show();activity.styleDialog(dialog,false);
  });activity.addAction(entries,restore,false);refresh(null);
 }
 void refresh(String error) {
  if(status==null)return;
  handler.removeCallbacks(poll);if(ModuleRuntime.isRunning())handler.postDelayed(poll,500);
  status.setText(error!=null?error:ModuleRuntime.isRunning()?"正在校验并准备模块…":ModuleRuntime.description(activity));
  choose.setEnabled(!ModuleRuntime.isRunning());
  exit.setEnabled(!ModuleRuntime.isRunning()&&ModuleRuntime.hasPending(activity));
  restore.setEnabled(!ModuleRuntime.isRunning()&&(ModuleRuntime.isDynamic()||ModuleRuntime.hasPending(activity)));
 }
 void importResult(Uri uri) {
  ModuleRuntime.prepare(activity,uri,error->{if(!activity.isFinishing()&&!activity.isDestroyed())refresh(error);});refresh(null);
 }
}
