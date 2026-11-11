package dev.guga.updates;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

/** Public update API. Storage, sessions, downloads and certificates remain internal. */
public final class ReleaseUpdater {
  private ReleaseUpdater() {}
  public static boolean isAutomaticEnabled(Context context) { return AppUpdates.enabled(context); }
  public static void setAutomaticEnabled(Context context,boolean enabled) { AppUpdates.setEnabled(context.getApplicationContext(),enabled); }
  /** Call on foreground entry. At most one automatic attempt per local calendar day. */
  public static void checkAutomatically(Context context) { AppUpdates.checkAsync(context,false,null); }
  /** Manual checks ignore the daily gate; an existing request is reused. Callback runs on main. */
  public static void checkManually(Context context,Runnable finished) {
    AppUpdates.checkAsync(context,true,() -> {
      if(finished==null) return;
      Handler main=new Handler(Looper.getMainLooper());
      main.post(new Runnable() { public void run() {
        if(isRunning()) main.postDelayed(this,100); else finished.run();
      }});
    });
  }
  public static boolean isRunning() { return AppUpdates.running.get(); }
  public static String statusMessage() { return AppUpdates.status; }
  public static boolean latestDownloadReady(Context context) { return AppUpdates.readyResult && hasDownloadedUpdate(context); }
  public static boolean hasDownloadedUpdate(Context context) {
    try { return AppUpdates.prefs(context).getLong("ready",0)>context.getPackageManager().getPackageInfo(context.getPackageName(),0).getLongVersionCode()
      && AppUpdates.apk(context).exists(); } catch(Exception ignored) { return false; }
  }
  public static void installDownloaded(Activity activity) { activity.startActivity(new Intent(activity,UpdateInstallActivity.class)); }
  /** Import a local .dshpatch; callback is delivered on main with null on success. */
  public static void preparePatch(Context context,android.net.Uri uri,java.util.function.Consumer<String> finished) {
    preparePatch(context,uri,new java.io.File(context.getApplicationInfo().sourceDir),finished);
  }
  /** A dynamic host may use its currently running signed module as the exact patch base. */
  public static void preparePatch(Context context,android.net.Uri uri,java.io.File base,java.util.function.Consumer<String> finished) {
    Context c=context.getApplicationContext();Handler main=new Handler(Looper.getMainLooper());
    if(!AppUpdates.running.compareAndSet(false,true)){main.post(()->finished.accept("已有更新任务正在进行，请稍后重试"));return;}
    AppUpdates.status="正在校验并合成更新…";
    new Thread(()->{
      String error=null;
      try{PatchUpdates.prepare(c,uri,base);AppUpdates.status="补丁已准备好，可以安装";}
      catch(Exception e){error=e.getMessage()==null?"无法处理补丁，请重新下载或使用完整 APK":e.getMessage();AppUpdates.status=error;}
      finally{AppUpdates.running.set(false);}
      String result=error;main.post(()->finished.accept(result));
    },"apk-patch-import").start();
  }
}
