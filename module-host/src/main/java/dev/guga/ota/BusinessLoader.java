package dev.guga.ota;

import dalvik.system.DexClassLoader;

/** Only our business namespace is replaceable. Android, the host and libraries stay stable. */
public final class BusinessLoader extends DexClassLoader {
 public BusinessLoader(String apk,String cache,String nativePath,ClassLoader host) {
  super(apk,cache,nativePath,host);
 }
 @Override protected Class<?> loadClass(String name,boolean resolve) throws ClassNotFoundException {
  synchronized(this) {
   if(!name.startsWith("dev.guga.dsh.") || name.equals("dev.guga.dsh.R") || name.startsWith("dev.guga.dsh.R$"))
    return super.loadClass(name,resolve);
   Class<?> found=findLoadedClass(name);
   // Missing business classes are an incompatible update, never mix two business versions.
   if(found==null)found=findClass(name);
   if(resolve)resolveClass(found);
   return found;
  }
 }
}
