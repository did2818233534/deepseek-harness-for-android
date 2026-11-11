package dev.guga.ota;
import static org.junit.Assert.*;
import android.content.*;
import android.net.Uri;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.io.*;
import java.util.zip.*;

public class ModuleRuntimeTests {
 @org.junit.Before public void explicitEmulatorScenarioOnly() throws Exception {
  org.junit.Assume.assumeTrue("Run each OTA phase in a fresh process with -e moduleScenario true",
   "true".equals(androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("moduleScenario")));
  org.junit.Assume.assumeTrue(c().getPackageManager().getPackageInfo(c().getPackageName(),0).getLongVersionCode()==4);
  assertFalse("Use a keyless test emulator",c().getSharedPreferences("settings",0).contains("key"));
 }
 @Test public void stageOnly() throws Exception {
  assertFalse(ModuleRuntime.isDynamic());File patch=fixture("module4-to5.dshota");
  try{ModuleRuntime.prepareNow(c(),Uri.fromFile(patch));assertTrue(ModuleRuntime.hasPending(c()));}finally{patch.delete();}
 }
 private android.app.Instrumentation i(){return InstrumentationRegistry.getInstrumentation();}
 private Context c(){return i().getTargetContext();}
 private File fixture(String name) throws Exception {
  File f=new File(c().getCacheDir(),name);
  try(InputStream in=i().getContext().getAssets().open(name);FileOutputStream out=new FileOutputStream(f)){
   byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
  }return f;
 }
 @Test public void prepareSignedModuleAndRejectDamage() throws Exception {
  assertFalse(ModuleRuntime.isDynamic());assertEquals(4,ModuleRuntime.currentVersion(c()));
  c().getSharedPreferences("ota-preservation-test",0).edit().putString("chat","keep me").commit();
  File patch=fixture("module4-to5.dshota");
  var completed=new java.util.concurrent.CountDownLatch(1);String[] error={null};
  ModuleRuntime.prepare(c(),Uri.fromFile(patch),value->{error[0]=value;completed.countDown();});
  long before=android.os.SystemClock.elapsedRealtime();ModuleRuntime.hasPending(c());
  assertTrue("state reads must not wait for signature/merge work",android.os.SystemClock.elapsedRealtime()-before<2000);
  assertTrue(completed.await(600,java.util.concurrent.TimeUnit.SECONDS));assertNull(error[0]);
  assertTrue(ModuleRuntime.hasPending(c()));
  ModuleRuntime.prepareNow(c(),Uri.fromFile(patch));assertTrue(ModuleRuntime.hasPending(c()));
  File bad=new File(c().getCacheDir(),"broken-module");try(FileOutputStream out=new FileOutputStream(bad)){out.write(new byte[100]);}
  try{ModuleRuntime.prepareNow(c(),Uri.fromFile(bad));fail("must reject invalid signature");}catch(Exception expected){}
  assertTrue(ModuleRuntime.hasPending(c()));
  File wrong=fixture("module4-to5.dshota");try(RandomAccessFile f=new RandomAccessFile(wrong,"rw")){f.seek(8);f.write(new byte[32]);}
  try{ModuleRuntime.prepareNow(c(),Uri.fromFile(wrong));fail("must reject wrong base");}catch(Exception expected){}
  assertTrue(ModuleRuntime.hasPending(c()));
  File wrongSigner=fixture("wrong-signer.dshota");
  try{ModuleRuntime.prepareNow(c(),Uri.fromFile(wrongSigner));fail("must reject another signer");}catch(Exception expected){assertTrue(expected.getMessage().contains("签名"));}
  File incompatible=fixture("incompatible-host.dshota");
  try{ModuleRuntime.prepareNow(c(),Uri.fromFile(incompatible));fail("must reject another host contract");}catch(Exception expected){assertTrue(expected.getMessage().contains("宿主"));}
  File corrupt=fixture("corrupt-signature.dshota");
  try{ModuleRuntime.prepareNow(c(),Uri.fromFile(corrupt));fail("must reject damaged signed content");}catch(Exception expected){assertTrue(expected.getMessage().contains("签名"));}corrupt.delete();
  File resource=fixture("resource-change.dshota");
  try{ModuleRuntime.prepareNow(c(),Uri.fromFile(resource));fail("must reject host resources");}catch(Exception expected){assertTrue(expected.getMessage().contains("宿主资源"));}
  assertTrue(ModuleRuntime.hasPending(c()));patch.delete();wrong.delete();bad.delete();wrongSigner.delete();incompatible.delete();resource.delete();
 }
 @Test public void dynamicBusinessReallyLoadsAndRenders() throws Exception {
  assertTrue(ModuleRuntime.isDynamic());assertEquals(5,ModuleRuntime.currentVersion(c()));
  assertEquals("keep me",c().getSharedPreferences("ota-preservation-test",0).getString("chat",""));
  ClassLoader cl=ModuleRuntime.loader(getClass().getClassLoader());assertTrue(cl instanceof BusinessLoader);
  assertEquals(5,cl.loadClass("dev.guga.dsh.BuildConfig").getField("MODULE_VERSION").getLong(null));
  assertSame(ModuleRuntime.class,cl.loadClass("dev.guga.ota.ModuleRuntime"));
  Class<?> service=cl.loadClass("dev.guga.dsh.HarnessService");var started=service.getDeclaredField("nodeStarted");started.setAccessible(true);started.setBoolean(null,true);
  android.app.Activity a=i().startActivitySync(new Intent().setClassName(c(),"dev.guga.dsh.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  try {
   assertSame(cl,a.getClass().getClassLoader());
   var visible=a.getClass().getDeclaredField("visible");visible.setAccessible(true);
   var settings=a.getClass().getDeclaredMethod("updateSettings");settings.setAccessible(true);
   i().runOnMainSync(()->{try{visible.setBoolean(a,false);settings.invoke(a);}catch(Exception e){throw new RuntimeException(e);}});i().waitForIdleSync();
   ModuleRuntime.healthy(c());assertFalse(ModuleRuntime.hasPending(c()));
   assertEquals(4,c().getPackageManager().getPackageInfo(c().getPackageName(),0).getLongVersionCode());
   var bitmap=android.graphics.Bitmap.createBitmap(a.getWindow().getDecorView().getWidth(),a.getWindow().getDecorView().getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
   var latch=new java.util.concurrent.CountDownLatch(1);int[] result={-1};
   android.view.PixelCopy.request(a.getWindow(),bitmap,r->{result[0]=r;latch.countDown();},new android.os.Handler(a.getMainLooper()));
   assertTrue(latch.await(10,java.util.concurrent.TimeUnit.SECONDS));assertEquals(android.view.PixelCopy.SUCCESS,result[0]);
   try(var out=new FileOutputStream(new File(c().getFilesDir(),"module-update-settings.png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
  }finally{i().runOnMainSync(a::finish);}
 }
 @Test public void confirmedModuleSurvivesQuickRestart() throws Exception {
  assertTrue(ModuleRuntime.isDynamic());assertEquals(5,ModuleRuntime.currentVersion(c()));
  File file=new File(c().getFilesDir(),"modules/state.json");
  var s=new org.json.JSONObject(new String(java.nio.file.Files.readAllBytes(file.toPath()),java.nio.charset.StandardCharsets.UTF_8));
  assertTrue(s.getBoolean("confirmed"));assertFalse(s.optBoolean("booting"));
 }
 @Test public void simulateUnfinishedStartup() throws Exception {
  assertTrue(ModuleRuntime.isDynamic());
  File state=new File(c().getFilesDir(),"modules/state.json");
  String json=new String(java.nio.file.Files.readAllBytes(state.toPath()),java.nio.charset.StandardCharsets.UTF_8);
  var s=new org.json.JSONObject(json);s.put("booting",true);
  java.nio.file.Files.write(state.toPath(),s.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
 }
 @Test public void startupFailureRestored() throws Exception {
  assertFalse(ModuleRuntime.isDynamic());assertEquals(4,ModuleRuntime.currentVersion(c()));
  assertTrue(ModuleRuntime.notice().contains("启动未完成"));
  assertEquals("keep me",c().getSharedPreferences("ota-preservation-test",0).getString("chat",""));
 }
 @Test public void queueRestoreWithoutClearingData() throws Exception {
  assertTrue(ModuleRuntime.isDynamic());ModuleRuntime.restoreBuiltin(c());assertTrue(ModuleRuntime.hasPending(c()));
 }
 @Test public void builtinRestored() throws Exception {
  assertFalse(ModuleRuntime.isDynamic());assertEquals(4,ModuleRuntime.currentVersion(c()));
  assertEquals("keep me",c().getSharedPreferences("ota-preservation-test",0).getString("chat",""));
 }
}
