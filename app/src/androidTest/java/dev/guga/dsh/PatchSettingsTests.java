package dev.guga.dsh;
import static org.junit.Assert.*;
import android.content.*;
import android.app.*;
import android.net.Uri;
import android.view.View;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.io.*;

public class PatchSettingsTests {
 @Test public void pickerCancelMalformedImportAndRepeatReturn() throws Exception {
  var i=InstrumentationRegistry.getInstrumentation();var c=i.getTargetContext();
  assertFalse(c.getSharedPreferences("settings",0).contains("key"));
  boolean started=HarnessService.nodeStarted;HarnessService.nodeStarted=true;
  MainActivity a=(MainActivity)i.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  File wrong=new File(c.getCacheDir(),"invalid.dshpatch");try(var out=new FileOutputStream(wrong)){out.write(new byte[100]);}
  try {
   for(int repeat=0;repeat<2;repeat++) {
    i.runOnMainSync(()->{a.visible=false;a.updateSettings();});i.waitForIdleSync();
    var filter=new IntentFilter(Intent.ACTION_OPEN_DOCUMENT,"*/*");filter.addCategory(Intent.CATEGORY_OPENABLE);
    var monitor=i.addMonitor(filter,new android.app.Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null),true);
    i.runOnMainSync(()->ClaudeSettingsTests.touch(a.settingsPages.importPatch,20,20));i.waitForIdleSync();Thread.sleep(250);
    assertTrue(i.checkMonitorHit(monitor,1));i.removeMonitor(monitor);
    assertFalse(dev.guga.updates.ReleaseUpdater.isRunning());
    i.runOnMainSync(()->a.onActivityResult(SettingsPages.IMPORT_PATCH,Activity.RESULT_OK,new Intent().setData(Uri.fromFile(wrong))));
    for(int n=0;n<100 && dev.guga.updates.ReleaseUpdater.isRunning();n++)Thread.sleep(50);
    i.waitForIdleSync();assertFalse(dev.guga.updates.ReleaseUpdater.isRunning());
    assertTrue(a.settingsPages.importPatch.isEnabled());assertFalse(a.settingsPages.installPatch.isEnabled());
    assertTrue(a.settingsPages.patchStatus.getText().toString().contains("补丁"));
    i.runOnMainSync(a::onBackPressed);i.waitForIdleSync();assertSame(a.chatRoot,a.currentPage);
   }
   i.runOnMainSync(a::updateSettings);i.waitForIdleSync();android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(a.getWindow().getDecorView().getWidth(),a.getWindow().getDecorView().getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
   var latch=new java.util.concurrent.CountDownLatch(1);int[] copied={-1};
   android.view.PixelCopy.request(a.getWindow(),bitmap,result->{copied[0]=result;latch.countDown();},new android.os.Handler(a.getMainLooper()));
   assertTrue(latch.await(10,java.util.concurrent.TimeUnit.SECONDS));assertEquals(android.view.PixelCopy.SUCCESS,copied[0]);
   try(var out=new FileOutputStream(new File(a.getFilesDir(),"patch-update-settings.png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}
  }finally{i.runOnMainSync(a::finish);wrong.delete();HarnessService.nodeStarted=started;}
 }
}
