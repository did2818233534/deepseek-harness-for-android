package dev.guga.dsh;
import static org.junit.Assert.*;
import android.app.*;
import android.content.*;
import android.net.Uri;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.io.*;

public class ModuleSettingsTests {
 @Test public void pendingPromptCanBeDeferredAndReopened() throws Exception {
  var i=InstrumentationRegistry.getInstrumentation();var c=i.getTargetContext();
  HarnessService.nodeStarted=true;
  MainActivity a=(MainActivity)i.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  File state=new File(c.getFilesDir(),"modules/state.json");byte[] original=java.nio.file.Files.readAllBytes(state.toPath());
  try {
   dev.guga.ota.ModuleRuntime.restoreBuiltin(c);
   i.runOnMainSync(a::updateSettings);i.waitForIdleSync();
   var panel=a.settingsPages.modulePanel;
   assertEquals("应用更新",panel.choose.getText().toString());
   for(int n=0;n<2;n++) {
    i.runOnMainSync(()->ClaudeSettingsTests.touch(panel.choose,20,20));i.waitForIdleSync();
    assertTrue(panel.confirmation.isShowing());
    assertEquals("立即重启",panel.confirmation.getButton(AlertDialog.BUTTON_POSITIVE).getText().toString());
    i.runOnMainSync(()->ClaudeSettingsTests.touch(panel.confirmation.getButton(AlertDialog.BUTTON_NEGATIVE),20,20));i.waitForIdleSync();
    assertFalse(panel.confirmation.isShowing());assertTrue(dev.guga.ota.ModuleRuntime.hasPending(c));
   }
   i.runOnMainSync(()->ClaudeSettingsTests.touch(panel.more,20,20));i.waitForIdleSync();
   assertTrue(panel.options.isShowing());i.runOnMainSync(()->panel.options.onBackPressed());i.waitForIdleSync();assertFalse(panel.options.isShowing());
  }finally{java.nio.file.Files.write(state.toPath(),original);i.runOnMainSync(a::finish);}
 }
 @Test public void importSuccessPromptsAutomatically() throws Exception {
  var i=InstrumentationRegistry.getInstrumentation();var c=i.getTargetContext();
  org.junit.Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("restartScenario")));
  HarnessService.nodeStarted=true;
  MainActivity a=(MainActivity)i.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  File patch=new File(c.getCacheDir(),"restart-test.dshota");
  try(var in=i.getContext().getAssets().open("module5-to6.dshota");var out=new FileOutputStream(patch)){byte[] buffer=new byte[65536];int count;while((count=in.read(buffer))!=-1)out.write(buffer,0,count);}
  i.runOnMainSync(a::updateSettings);i.waitForIdleSync();var panel=a.settingsPages.modulePanel;
  i.runOnMainSync(()->panel.importResult(Uri.fromFile(patch)));
  for(int n=0;n<12000&&dev.guga.ota.ModuleRuntime.isRunning();n++)Thread.sleep(50);
  i.waitForIdleSync();assertFalse(dev.guga.ota.ModuleRuntime.isRunning());
  assertNotNull(panel.confirmation);assertTrue(panel.confirmation.isShowing());
  screenshot(a,panel.confirmation.getWindow(),"update-ready.png");
  i.runOnMainSync(()->ClaudeSettingsTests.touch(panel.confirmation.getButton(AlertDialog.BUTTON_NEGATIVE),20,20));i.waitForIdleSync();
  assertTrue(dev.guga.ota.ModuleRuntime.hasPending(c));patch.delete();
 }
 private static void screenshot(Activity a,android.view.Window window,String name) throws Exception {
  var bitmap=android.graphics.Bitmap.createBitmap(window.getDecorView().getWidth(),window.getDecorView().getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
  var latch=new java.util.concurrent.CountDownLatch(1);int[] result={-1};
  android.view.PixelCopy.request(window,bitmap,r->{result[0]=r;latch.countDown();},new android.os.Handler(a.getMainLooper()));
  assertTrue(latch.await(10,java.util.concurrent.TimeUnit.SECONDS));assertEquals(android.view.PixelCopy.SUCCESS,result[0]);
  try(var out=new FileOutputStream(new File(a.getFilesDir(),name))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
 }
 @Test public void cancelInvalidRepeatAndReturn() throws Exception {
  var i=InstrumentationRegistry.getInstrumentation();var c=i.getTargetContext();
  assertFalse(c.getSharedPreferences("settings",0).contains("key"));
  HarnessService.nodeStarted=true;
  MainActivity a=(MainActivity)i.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  File wrong=new File(c.getCacheDir(),"invalid.dshota");try(var out=new FileOutputStream(wrong)){out.write(new byte[100]);}
  try {
   for(int repeat=0;repeat<2;repeat++){
    i.runOnMainSync(()->{a.visible=false;a.updateSettings();});i.waitForIdleSync();
    var panel=a.settingsPages.modulePanel;
    var filter=new IntentFilter(Intent.ACTION_OPEN_DOCUMENT,"*/*");filter.addCategory(Intent.CATEGORY_OPENABLE);
    var monitor=i.addMonitor(filter,new Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null),true);
    i.runOnMainSync(()->ClaudeSettingsTests.touch(panel.choose,20,20));i.waitForIdleSync();Thread.sleep(250);
    assertTrue(i.checkMonitorHit(monitor,1));i.removeMonitor(monitor);
    i.runOnMainSync(()->a.onActivityResult(ModuleUpdatePanel.IMPORT_MODULE,Activity.RESULT_OK,new Intent().setData(Uri.fromFile(wrong))));
    for(int n=0;n<200&&dev.guga.ota.ModuleRuntime.isRunning();n++)Thread.sleep(50);
    i.waitForIdleSync();assertFalse(dev.guga.ota.ModuleRuntime.isRunning());
    assertTrue(panel.choose.isEnabled());assertEquals("导入更新",panel.choose.getText().toString());
    assertEquals(android.view.View.GONE,panel.more.getVisibility());
    assertFalse(panel.status.getText().toString().isEmpty());
    i.runOnMainSync(a::onBackPressed);i.waitForIdleSync();assertSame(a.chatRoot,a.currentPage);
   }
   i.runOnMainSync(a::updateSettings);i.waitForIdleSync();
   var bitmap=android.graphics.Bitmap.createBitmap(a.getWindow().getDecorView().getWidth(),a.getWindow().getDecorView().getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
   var latch=new java.util.concurrent.CountDownLatch(1);int[] result={-1};
   android.view.PixelCopy.request(a.getWindow(),bitmap,r->{result[0]=r;latch.countDown();},new android.os.Handler(a.getMainLooper()));
   assertTrue(latch.await(10,java.util.concurrent.TimeUnit.SECONDS));assertEquals(android.view.PixelCopy.SUCCESS,result[0]);
   try(var out=new FileOutputStream(new File(a.getFilesDir(),"host-module-settings.png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
  }finally{i.runOnMainSync(a::finish);wrong.delete();}
 }
}
