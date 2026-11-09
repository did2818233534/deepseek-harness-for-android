package dev.guga.ota;

import android.app.*;
import android.content.*;

/** Framework-owned components are always instantiated from one process-wide module. API 28+. */
public final class ModuleFactory extends AppComponentFactory {
 private ClassLoader selected(ClassLoader host,String name) {
  return name.startsWith("dev.guga.dsh.") ? ModuleRuntime.loader(host) : host;
 }
 @Override public Activity instantiateActivity(ClassLoader cl,String name,Intent intent)
   throws InstantiationException,IllegalAccessException,ClassNotFoundException {
  return super.instantiateActivity(selected(cl,name),name,intent);
 }
 @Override public Service instantiateService(ClassLoader cl,String name,Intent intent)
   throws InstantiationException,IllegalAccessException,ClassNotFoundException {
  return super.instantiateService(selected(cl,name),name,intent);
 }
 @Override public BroadcastReceiver instantiateReceiver(ClassLoader cl,String name,Intent intent)
   throws InstantiationException,IllegalAccessException,ClassNotFoundException {
  return super.instantiateReceiver(selected(cl,name),name,intent);
 }
 @Override public ContentProvider instantiateProvider(ClassLoader cl,String name)
   throws InstantiationException,IllegalAccessException,ClassNotFoundException {
  return super.instantiateProvider(selected(cl,name),name);
 }
}
