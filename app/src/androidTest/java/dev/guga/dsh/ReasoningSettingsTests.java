package dev.guga.dsh;

import static org.junit.Assert.*;
import android.content.*;
import android.view.*;
import android.widget.*;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;

public class ReasoningSettingsTests {
  @Test public void onlyModelBridgeAllowsLargeImagePayloads() {
    assertEquals(48L*1024*1024, NativeBridge.requestLimit("/model"));
    for(String route:new String[]{"/tool","/settings","/web-api","/model/"}) assertEquals(3L*1024*1024, NativeBridge.requestLimit(route));
  }
  @Test public void defaultHighAndEveryEffortSurviveSettingsRecreation() throws Exception {
    Context base = InstrumentationRegistry.getInstrumentation().getTargetContext();
    String name = "reasoning-test-" + java.util.UUID.randomUUID();
    Context isolated = new ContextWrapper(base) {
      @Override public SharedPreferences getSharedPreferences(String ignored, int mode) {
        return base.getSharedPreferences(name, mode);
      }
    };
    try {
      Settings settings = new Settings(isolated);
      assertEquals("high", settings.reasoningEffort());
      for (String effort : new String[]{"low", "max", "none", "high", "none", "high"}) {
        settings.saveReasoningEffort(effort);
        Settings reopened = new Settings(isolated);
        assertEquals(effort, reopened.reasoningEffort());
        assertEquals(effort, reopened.publicData().getString("reasoningEffort"));
        assertEquals(!effort.equals("none"), reopened.publicData().getBoolean("thinking"));
      }
      try { settings.saveReasoningEffort("medium"); fail("invalid effort accepted"); }
      catch (Exception expected) { assertEquals("high", settings.reasoningEffort()); }
    } finally { base.deleteSharedPreferences(name); }
  }

  static SeekBar slider(View view) {
    if (view instanceof SeekBar) return (SeekBar)view;
    if (view instanceof ViewGroup) for (int n=0;n<((ViewGroup)view).getChildCount();n++) {
      SeekBar found = slider(((ViewGroup)view).getChildAt(n)); if (found != null) return found;
    }
    return null;
  }
  static void touch(SeekBar bar, int stop) {
    float width = bar.getWidth()-bar.getPaddingLeft()-bar.getPaddingRight();
    float x = bar.getPaddingLeft()+width*stop/3f;
    long t = android.os.SystemClock.uptimeMillis();
    MotionEvent down=MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,x,bar.getHeight()/2f,0);
    MotionEvent up=MotionEvent.obtain(t,t+30,MotionEvent.ACTION_UP,x,bar.getHeight()/2f,0);
    bar.dispatchTouchEvent(down);bar.dispatchTouchEvent(up);down.recycle();up.recycle();
  }

  @Test public void sliderTouchesPersistReopenAndBusyRevertsWithoutCatalogCount() throws Exception {
    var i=InstrumentationRegistry.getInstrumentation();var c=i.getTargetContext();
    SharedPreferences prefs=c.getSharedPreferences("settings",0);
    // No key is read/decrypted and no model/network request is allowed in this test.
    assertFalse("Run this test only in a keyless test emulator",prefs.contains("key"));
    String before=prefs.getString("reasoningEffort",null);
    dev.guga.updates.UpdateTestDriver.prefs(c).edit().putString("checkedDay",dev.guga.updates.UpdateTestDriver.today()).commit();
    MainActivity a=(MainActivity)i.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    ModelSettingsPanel[] panel=new ModelSettingsPanel[1];
    try {
      i.runOnMainSync(()->{a.visible=false;a.busy=false;try {panel[0]=new ModelSettingsPanel(a,true);panel[0].open();}catch(Exception e){throw new AssertionError(e);}});
      i.waitForIdleSync();Thread.sleep(150);
      for(int stop:new int[]{1,2,3,0,3,1,0,2}) {
        i.runOnMainSync(()->{
          SeekBar bar=slider(panel[0].box);assertNotNull(bar);assertTrue(bar.getWidth()>0);
          android.graphics.Rect visible=new android.graphics.Rect();
          assertTrue("Slider must be visible, not clipped by fixed-height container",bar.getGlobalVisibleRect(visible));
          assertEquals(bar.getHeight(),visible.height());
          touch(bar,stop);
          assertEquals(stop,bar.getProgress());
          assertEquals(ModelSettingsPanel.EFFORTS[stop],new Settings(a).reasoningEffort());
          assertTrue(bar.getContentDescription().toString().contains(ModelSettingsPanel.EFFORT_LABELS[stop]));
        });
      }
      i.runOnMainSync(()->{
        panel[0].close();panel[0].dialog.dismiss();
        try {panel[0]=new ModelSettingsPanel(a,true);panel[0].open();}catch(Exception e){throw new AssertionError(e);}
        assertEquals(2,slider(panel[0].box).getProgress());
      });
      i.waitForIdleSync();Thread.sleep(150);
      i.runOnMainSync(()->{
        a.busy=true;SeekBar bar=slider(panel[0].box);touch(bar,0);
        assertEquals(2,bar.getProgress());assertEquals("high",panel[0].effort);
        assertEquals("high",new Settings(a).reasoningEffort());
        assertTrue(bar.getContentDescription().toString().contains("高"));
        a.busy=false;
        try {panel[0].catalog("{\"data\":[{\"id\":\"deepseek-flash\"},{\"id\":\"deepseek-v4-pro\"}]}");}catch(Exception e){throw new AssertionError(e);}
        assertEquals(View.GONE,panel[0].state.getVisibility());
      });
      Thread.sleep(4500);
      StreamingTests.screenshot(c,"reasoning-control.png");
    } finally {
      i.runOnMainSync(()->{a.busy=false;if(panel[0]!=null){panel[0].close();if(panel[0].dialog!=null)panel[0].dialog.dismiss();}a.finish();});
      if(before==null)prefs.edit().remove("reasoningEffort").commit();else prefs.edit().putString("reasoningEffort",before).commit();
    }
  }
}
