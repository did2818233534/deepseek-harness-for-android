package dev.guga.dsh;
import static org.junit.Assert.*;
import android.app.*;
import android.content.*;
import android.net.Uri;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.io.*;

public class ModuleSettingsTests {
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
    assertTrue(panel.choose.isEnabled());assertFalse(panel.exit.isEnabled());
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
