package dev.guga.ota;

import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import android.util.AtomicFile;
import com.android.apksig.ApkVerifier;
import dev.guga.updates.ApkPatch;
import org.json.JSONObject;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.zip.*;

/** Stable OTA API. Persistent state is atomic; activation only occurs at a cold process start. */
public final class ModuleRuntime {
 private static final long MAX=512L*1024*1024;
 private static final String META="assets/dsh-module.properties";
 private static final AtomicBoolean busy=new AtomicBoolean();
 private static volatile ClassLoader selected;
 private static volatile File runningApk;
 private static volatile long runningVersion;
 private static volatile String startupNotice="";
 private ModuleRuntime() {}
 private static File dir(Context c){File d=new File(c.getFilesDir(),"modules");d.mkdirs();return d;}
 private static AtomicFile stateFile(Context c){return new AtomicFile(new File(dir(c),"state.json"));}
 private static synchronized JSONObject state(Context c) throws Exception {
  AtomicFile f=stateFile(c);
  if(!f.getBaseFile().exists())return new JSONObject();
  try(InputStream in=f.openRead()){return new JSONObject(new String(readSmall(in),java.nio.charset.StandardCharsets.UTF_8));}
 }
 private static synchronized void save(Context c,JSONObject value) throws Exception {
  AtomicFile f=stateFile(c);FileOutputStream out=f.startWrite();
  try{out.write(value.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));f.finishWrite(out);}
  catch(Exception e){f.failWrite(out);throw e;}
 }
 private static File named(Context c,String name) throws IOException {
  if(!name.matches("[a-f0-9]{64}\\.apk"))throw new IOException("模块记录无效");
  return new File(dir(c),name);
 }
 private static Properties metadata(File apk) throws Exception {
  try(ZipFile zip=new ZipFile(apk)) {
   ZipEntry entry=zip.getEntry(META);
   if(entry==null||entry.getSize()>4096)throw new IOException("此安装包不包含动态模块，请使用完整 APK 安装");
   Properties p=new Properties();try(InputStream in=zip.getInputStream(entry)){p.load(in);}return p;
  }
 }
 private static long version(File apk) throws Exception {
  long v=Long.parseLong(metadata(apk).getProperty("moduleVersion","0"));
  if(v<=0)throw new IOException("模块版本无效");return v;
 }
 private static File host(Context c){return new File(c.getApplicationInfo().sourceDir);}
 public static ClassLoader loader(ClassLoader fallback){return selected==null?fallback:selected;}
 public static boolean isRunning(){return busy.get();}
 public static long currentVersion(Context c){return runningVersion;}
 public static File currentApk(Context c){return runningApk==null?host(c):runningApk;}
 public static boolean isDynamic(){return runningApk!=null;}
 public static String notice(){return startupNotice;}
 public static boolean hasPending(Context c){try{return state(c).has("pending");}catch(Exception e){return false;}}
 public static String description(Context c) {
  String text="业务模块 "+currentVersion(c)+(isDynamic()?" · 动态模块":" · 宿主内置");
  try{JSONObject s=state(c);if(s.has("pending"))text+="\n"+(s.optString("pending").isEmpty()?"恢复内置模块":"模块 "+s.optLong("pendingVersion"))+"已准备，下次启动生效";}catch(Exception ignored){}
  if(!startupNotice.isEmpty())text+="\n"+startupNotice;
  return text;
 }
 static synchronized void start(Context c) {
  try {
   runningVersion=version(host(c));
   JSONObject s=state(c);
   // A system-installed host replaces all old dynamic selections, while keeping app data.
   if(!host(c).getPath().equals(s.optString("host")))s=new JSONObject().put("host",host(c).getPath());
   if(s.optBoolean("booting")&&!s.has("pending")) {
    s.remove("active");s.remove("pending");s.put("booting",false);
    s.put("notice","上次模块启动未完成，已恢复宿主内置版本");
   }
   if(s.has("pending")) {
    String next=s.getString("pending");s.remove("pending");s.remove("pendingVersion");
    if(next.isEmpty())s.remove("active");else s.put("active",next);
    s.put("confirmed",false).put("booting",false);
   }
   startupNotice=s.optString("notice");
   if(s.has("active")) {
    File apk=named(c,s.getString("active"));
    if(!apk.isFile()||!hex(ApkPatch.digest(apk)).concat(".apk").equals(apk.getName()))throw new IOException("模块完整性校验失败");
    if(!metadata(host(c)).getProperty("hostContract","").equals(metadata(apk).getProperty("hostContract")))throw new IOException("宿主版本已变化");
    if(!s.optBoolean("confirmed"))s.put("booting",true);
    save(c,s); // Durable before executing any unconfirmed module class.
    runningApk=apk;runningVersion=version(apk);
    selected=new BusinessLoader(apk.getPath(),c.getCodeCacheDir().getPath(),c.getApplicationInfo().nativeLibraryDir,ModuleRuntime.class.getClassLoader());
    Thread.UncaughtExceptionHandler prior=Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler((thread,error)->{
     rollbackAfterFailure(c,"动态模块运行异常，已恢复宿主内置版本");
     if(prior!=null)prior.uncaughtException(thread,error);else android.os.Process.killProcess(android.os.Process.myPid());
    });
   } else save(c,s);
  } catch(Throwable error) {
   selected=null;runningApk=null;
   try{runningVersion=version(host(c));}catch(Exception ignored){}
   rollbackAfterFailure(c,"动态模块无法启动，已恢复宿主内置版本");
  }
 }
 private static synchronized void rollbackAfterFailure(Context c,String reason) {
  startupNotice=reason;
  try{JSONObject s=state(c);s.remove("active");s.remove("pending");s.remove("pendingVersion");s.put("booting",false).put("confirmed",false).put("notice",reason);save(c,s);}catch(Exception ignored){}
 }
 /** Called by the stable host after the first activity remains resumed for 30 seconds. */
 static synchronized void healthy(Context c) {
  if(selected==null)return;
  try{JSONObject s=state(c);s.put("booting",false).put("confirmed",true);s.remove("notice");save(c,s);startupNotice="";}catch(Exception ignored){}
 }
 public static synchronized void restoreBuiltin(Context c) throws Exception {
  if(busy.get())throw new IOException("请等待模块导入完成");
  JSONObject s=state(c);s.put("pending","");s.remove("pendingVersion");save(c,s);
 }
 /** Overlay only explicit business assets; native runtimes, resources and libraries remain host-owned. */
 public static InputStream openAsset(Context c,String path) throws IOException {
  if(runningApk==null||!(path.equals("runtime/main.cjs")||path.equals("runtime/bootstrap.cjs")||path.equals("reasoning-models.json")))return c.getAssets().open(path);
  ZipFile zip=new ZipFile(runningApk);ZipEntry entry=zip.getEntry("assets/"+path);
  if(entry==null){zip.close();throw new FileNotFoundException(path);}
  return new FilterInputStream(zip.getInputStream(entry)){@Override public void close() throws IOException{try{super.close();}finally{zip.close();}}};
 }
 public static void prepare(Context c,Uri uri,Consumer<String> callback) {
  Context app=c.getApplicationContext();Handler main=new Handler(Looper.getMainLooper());
  if(!busy.compareAndSet(false,true)){main.post(()->callback.accept("正在导入模块，请稍后重试"));return;}
  new Thread(()->{
   String error=null;
   try{prepareNow(app,uri);}catch(Exception e){error=e.getMessage()==null?"模块导入失败，当前版本未改变":e.getMessage();}
   finally{busy.set(false);}
   String result=error;main.post(()->callback.accept(result));
  },"module-import").start();
 }
 static void prepareNow(Context c,Uri uri) throws Exception {
  if(c.getApplicationInfo().splitSourceDirs!=null&&c.getApplicationInfo().splitSourceDirs.length>0)throw new IOException("请先安装独立完整宿主 APK");
  File temp=new File(dir(c),"incoming.apk");temp.delete();
  try {
   try(InputStream raw=c.getContentResolver().openInputStream(uri)) {
    if(raw==null)throw new IOException("无法打开更新文件");
    BufferedInputStream in=new BufferedInputStream(raw);in.mark(8);byte[] prefix=new byte[8];int prefixSize=0;while(prefixSize<8){int n=in.read(prefix,prefixSize,8-prefixSize);if(n<0)break;prefixSize+=n;}in.reset();
    if(Arrays.equals(prefix,"DSHPAT01".getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
     ApkPatch.apply(runningApk==null?host(c):runningApk,in,temp);
    else try(FileOutputStream out=new FileOutputStream(temp)) {
     byte[] buffer=new byte[65536];long count=0;int n;
     while((n=in.read(buffer))!=-1){count+=n;if(count>MAX)throw new IOException("模块文件过大");out.write(buffer,0,n);}out.getFD().sync();
    }
   }
   validate(c,temp);
   long next=version(temp);String name=hex(ApkPatch.digest(temp))+".apk";
   synchronized(ModuleRuntime.class) {
   JSONObject s=state(c);
   if(name.equals(s.optString("pending")))return; // Re-importing the same pending module is harmless.
   if(next<=Math.max(runningVersion,s.optLong("highest")))throw new IOException("请选择更新版本的模块；降级请使用恢复内置模块");
   File dest=named(c,name);
   if(!temp.setReadOnly())throw new IOException("无法保护模块文件"); // Required before Android 14+ loads code.
   Files.move(temp.toPath(),dest.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
   s.put("host",host(c).getPath()).put("pending",name).put("pendingVersion",next).put("highest",next);s.remove("notice");
   save(c,s);
   // Keep only the running and pending module; never remove either patch base.
   File[] old=dir(c).listFiles();if(old!=null)for(File f:old)
    if(f.getName().endsWith(".apk")&&!f.equals(dest)&&!f.equals(runningApk))f.delete();
   }
  } finally {temp.delete();}
 }
 private static void validate(Context c,File apk) throws Exception {
  ApkVerifier.Result verified=new ApkVerifier.Builder(apk).setMinCheckedPlatformVersion(28).setMaxCheckedPlatformVersion(Build.VERSION.SDK_INT).build().verify();
  if(!verified.isVerified())throw new IOException("模块签名校验失败，文件可能损坏");
  PackageManager pm=c.getPackageManager();
  PackageInfo own=pm.getPackageInfo(c.getPackageName(),PackageManager.GET_SIGNING_CERTIFICATES);
  PackageInfo next=pm.getPackageArchiveInfo(apk.getPath(),0);
  Set<String> trusted=new HashSet<>(),actual=new HashSet<>();
  for(Signature cert:own.signingInfo.getApkContentsSigners())trusted.add(hex(cert.toByteArray()));
  for(java.security.cert.X509Certificate cert:verified.getSignerCertificates())actual.add(hex(cert.getEncoded()));
  if(!trusted.equals(actual)||actual.isEmpty()||next==null||!own.packageName.equals(next.packageName))throw new IOException("模块签名或应用标识不匹配");
  Properties base=metadata(host(c)),target=metadata(apk);
  if(base.getProperty("hostContract","").isEmpty()||!base.getProperty("hostContract").equals(target.getProperty("hostContract")))throw new IOException("此更新需要新版宿主，请安装完整 APK");
  if(!immutableEntries(host(c)).equals(immutableEntries(apk)))throw new IOException("此更新包含宿主资源或原生库变更，请安装完整 APK");
  try(ZipFile zip=new ZipFile(apk)){if(zip.getEntry("classes.dex")==null)throw new IOException("缺少业务代码");}
 }
 private static boolean mutable(String name) {
  return name.matches("classes[0-9]*\\.dex")||name.startsWith("META-INF/")||name.equals("AndroidManifest.xml")||name.equals(META)
   ||name.equals("assets/runtime/main.cjs")||name.equals("assets/runtime/bootstrap.cjs")||name.equals("assets/reasoning-models.json");
 }
 private static Map<String,String> immutableEntries(File apk) throws Exception {
  Map<String,String> entries=new TreeMap<>();Set<String> names=new HashSet<>();
  try(ZipFile zip=new ZipFile(apk)) {
   Enumeration<? extends ZipEntry> all=zip.entries();long size=0;
   while(all.hasMoreElements()) {
    ZipEntry e=all.nextElement();if(!names.add(e.getName()))throw new IOException("重复模块条目");
    if(e.isDirectory()||mutable(e.getName()))continue;
    MessageDigest digest=MessageDigest.getInstance("SHA-256");
    try(InputStream in=zip.getInputStream(e)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){size+=n;if(size>1024L*1024*1024)throw new IOException("模块解压大小异常");digest.update(b,0,n);}}
    entries.put(e.getName(),hex(digest.digest()));
   }
  }
  return entries;
 }
 private static byte[] readSmall(InputStream in) throws IOException {
  ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[1024];int n;
  while((n=in.read(b))!=-1){if(out.size()+n>16384)throw new IOException("模块状态过大");out.write(b,0,n);}return out.toByteArray();
 }
 private static String hex(byte[] b){StringBuilder s=new StringBuilder();for(byte n:b)s.append(String.format(java.util.Locale.ROOT,"%02x",n&255));return s.toString();}
}
