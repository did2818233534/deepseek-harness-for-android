package dev.guga.dsh;
import static org.junit.Assert.*;
import android.content.Intent;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import dev.guga.dsh.settings.Settings;

/** Screenshots from the same dedicated keyless emulator used by the touch tests. */
public class ProviderVisualTests {
  @Test public void captureSupplierAndMorePages() throws Exception {
    var i=InstrumentationRegistry.getInstrumentation();var c=i.getTargetContext();
    assertFalse(c.getSharedPreferences("settings",0).contains("key"));assertFalse(c.getSharedPreferences("settings",0).contains("anthropic.key"));
    boolean previous=HarnessService.nodeStarted;HarnessService.nodeStarted=true;
    MainActivity a=(MainActivity)i.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    ModelSettingsPanel[] panel={null};String original=new Settings(a).editingProvider();
    try {
      i.runOnMainSync(()->{a.visible=false;a.busy=false;new Settings(a).saveEditingProvider("anthropic");try{panel[0]=new ModelSettingsPanel(a,false);panel[0].open();}catch(Exception e){throw new AssertionError(e);}});
      i.waitForIdleSync();Thread.sleep(300);capture(a,"provider-basic.png");
      i.runOnMainSync(()->ClaudeSettingsTests.touch(panel[0].providerButton,panel[0].providerButton.getWidth()/2f,panel[0].providerButton.getHeight()/2f));
      i.waitForIdleSync();Thread.sleep(200);capture(a,"provider-dropdown.png");
      i.runOnMainSync(()->{panel[0].popup.dismiss();panel[0].close();try{panel[0]=new ModelSettingsPanel(a,false,true);panel[0].open();}catch(Exception e){throw new AssertionError(e);}});
      i.waitForIdleSync();Thread.sleep(300);capture(a,"provider-more.png");
    }finally{i.runOnMainSync(()->{if(panel[0]!=null)panel[0].close();new Settings(a).saveEditingProvider(original);a.finish();});HarnessService.nodeStarted=previous;}
  }
  static void capture(MainActivity a,String name) throws Exception {
    // TCG-only emulator can retain system ANR dialogs from boot; use actual screen touches to wait.
    var automation=InstrumentationRegistry.getInstrumentation().getUiAutomation();
    for(int attempt=0;attempt<4;attempt++) {
      var root=automation.getRootInActiveWindow();if(root==null || root.findAccessibilityNodeInfosByText("responding").isEmpty())break;
      var buttons=root.findAccessibilityNodeInfosByText("Wait");if(buttons.isEmpty())break;
      android.graphics.Rect bounds=new android.graphics.Rect();buttons.get(0).getBoundsInScreen(bounds);
      long now=android.os.SystemClock.uptimeMillis();
      for(int action:new int[]{android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP}) {
        var event=android.view.MotionEvent.obtain(now,now+50,action,bounds.centerX(),bounds.centerY(),0);event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);automation.injectInputEvent(event,true);event.recycle();
      }
      Thread.sleep(500);
    }
    var bitmap=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(bitmap);
    try(var out=new java.io.FileOutputStream(new java.io.File(a.getFilesDir(),name))){assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));}finally{bitmap.recycle();}
  }
}
