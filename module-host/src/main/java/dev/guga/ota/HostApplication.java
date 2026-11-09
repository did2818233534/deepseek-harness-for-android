package dev.guga.ota;

import android.app.Application;
import android.content.Context;

/** This class and its recovery path are never supplied by an OTA module. */
public final class HostApplication extends Application {
 @Override protected void attachBaseContext(Context base) {
  super.attachBaseContext(base);
  ModuleRuntime.start(base);
 }
 @Override public ClassLoader getClassLoader(){return ModuleRuntime.loader(super.getClassLoader());}
 @Override public void onCreate() {
  super.onCreate();
  android.os.Handler handler=new android.os.Handler(getMainLooper());
  Runnable healthy=()->ModuleRuntime.healthy(this);
  registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
   public void onActivityCreated(android.app.Activity a,android.os.Bundle b){}
   public void onActivityStarted(android.app.Activity a){}
   public void onActivityResumed(android.app.Activity a){if(a.getClass().getName().equals("dev.guga.dsh.MainActivity")){handler.removeCallbacks(healthy);handler.postDelayed(healthy,30000);}}
   public void onActivityPaused(android.app.Activity a){handler.removeCallbacks(healthy);}
   public void onActivityStopped(android.app.Activity a){}
   public void onActivitySaveInstanceState(android.app.Activity a,android.os.Bundle b){}
   public void onActivityDestroyed(android.app.Activity a){}
  });
 }
}
