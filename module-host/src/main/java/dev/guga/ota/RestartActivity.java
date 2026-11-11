package dev.guga.ota;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Process;

/** Foreground bridge survives the business process, without alarms or background launches. */
public final class RestartActivity extends Activity {
 private final Handler handler=new Handler(android.os.Looper.getMainLooper());
 private int sourcePid;
 private boolean started;
 private int attempts;
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  sourcePid=getIntent().getIntExtra("sourcePid",-1);
 }
 private boolean sourceAlive(){
  ActivityManager manager=(ActivityManager)getSystemService(ACTIVITY_SERVICE);
  var processes=manager.getRunningAppProcesses();
  if(processes!=null)for(var p:processes)
   if(p.pid==sourcePid&&p.uid==Process.myUid()&&p.processName.equals(getPackageName()))return true;
  return false;
 }
 @Override protected void onResume(){
  super.onResume();
  if(started)return;
  started=true;
  if(sourceAlive())Process.killProcess(sourcePid);
  handler.postDelayed(this::launchWhenStopped,100);
 }
 private void launchWhenStopped(){
  if(isFinishing())return;
  if(sourceAlive()){
   if(++attempts<50){handler.postDelayed(this::launchWhenStopped,100);return;}
   android.widget.Toast.makeText(this,"暂时无法重启，请关闭应用后重新打开",android.widget.Toast.LENGTH_LONG).show();
   finish();return;
  }
  Intent launch=getPackageManager().getLaunchIntentForPackage(getPackageName());
  if(launch!=null){launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);startActivity(launch);}
  finish();
 }
 @Override protected void onDestroy(){super.onDestroy();handler.removeCallbacksAndMessages(null);}
}
