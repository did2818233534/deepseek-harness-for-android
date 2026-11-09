package dev.guga.dsh;
import static org.junit.Assert.*;
import android.content.Intent;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import dev.guga.dsh.settings.Settings;

/** Screenshots from the same dedicated keyless emulator used by the touch tests. */
public class ProviderVisualTests {
  @Test public void captureDarkProviderPages() throws Exception {
    var c=InstrumentationRegistry.getInstrumentation().getTargetContext();
    var prefs=c.getSharedPreferences("appearance",0);String before=prefs.getString("theme","system");
    try{prefs.edit().putString("theme","dark").commit();captureSupplierAndMorePages();}
    finally{prefs.edit().putString("theme",before).commit();}
  }
  @Test public void captureSupplierAndMorePages() throws Exception {
    var i=InstrumentationRegistry.getInstrumentation();var c=i.getTargetContext();
    assertFalse(c.getSharedPreferences("settings",0).contains("key"));assertFalse(c.getSharedPreferences("settings",0).contains("anthropic.key"));
    boolean previous=HarnessService.nodeStarted;HarnessService.nodeStarted=true;
    MainActivity a=(MainActivity)i.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    ModelSettingsPanel[] panel={null};String original=new Settings(a).editingProvider();
    try {
      i.runOnMainSync(()->{a.visible=false;a.busy=false;new Settings(a).saveEditingProvider("anthropic");try{panel[0]=new ModelSettingsPanel(a,false);panel[0].open();panel[0].catalog("{\"data\":[{\"id\":\"claude-opus-5-5\"},{\"id\":\"claude-sonnet-5-5\"},{\"id\":\"claude-haiku-5-5\"},{\"id\":\"claude-sonnet-4-6\"}]}");}catch(Exception e){throw new AssertionError(e);}});
      i.waitForIdleSync();Thread.sleep(300);capture(a,UiStyle.dark?"provider-basic-dark.png":"provider-basic.png");
      i.runOnMainSync(()->ClaudeSettingsTests.touch(panel[0].moreTab,20,20));
      i.waitForIdleSync();Thread.sleep(300);capture(a,UiStyle.dark?"provider-more-dark.png":"provider-more.png");
    }finally{i.runOnMainSync(()->{if(panel[0]!=null)panel[0].close();new Settings(a).saveEditingProvider(original);a.finish();});HarnessService.nodeStarted=previous;}
  }
  @Test public void captureFullHeightMenuDividers() throws Exception {
    var i=InstrumentationRegistry.getInstrumentation();var c=i.getTargetContext();
    var appearance=c.getSharedPreferences("appearance",0);String original=appearance.getString("theme","system");
    boolean started=HarnessService.nodeStarted;HarnessService.nodeStarted=true;
    try {
      for(String theme:new String[]{"dark","light"}) {
        appearance.edit().putString("theme",theme).commit();
        MainActivity a=(MainActivity)i.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
          i.runOnMainSync(()->{a.visible=false;a.settings();});i.waitForIdleSync();
          i.runOnMainSync(()->{
            var help=GeneralSettingsTests.findDescription(a.currentPage,"模型与服务说明");
            var cells=(android.view.ViewGroup)help.getParent();
            var actions=(android.view.ViewGroup)cells.getParent();
            var entry=(android.view.ViewGroup)actions.getParent();
            assertEquals(a.dp(48),help.getWidth());assertEquals(help.getWidth(),cells.getChildAt(0).getWidth());
            assertEquals(entry.getHeight(),actions.getHeight());assertEquals(0,actions.getTop());
            assertEquals(entry.getWidth(),actions.getRight());
            for(int n=1;n<actions.getChildCount();n++) {
              assertEquals(0,actions.getChildAt(n).getTop());assertEquals(entry.getHeight(),actions.getChildAt(n).getHeight());
            }
          });
          capture(a,"menu-edges-"+theme+".png");
        }finally{i.runOnMainSync(a::finish);}
      }
    }finally{appearance.edit().putString("theme",original).commit();HarnessService.nodeStarted=started;}
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
