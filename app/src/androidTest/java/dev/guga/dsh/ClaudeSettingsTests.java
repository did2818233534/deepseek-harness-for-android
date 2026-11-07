package dev.guga.dsh;

import static org.junit.Assert.*;
import android.content.*;
import android.view.*;
import androidx.test.platform.app.InstrumentationRegistry;
import dev.guga.dsh.settings.ModelEndpoint;
import dev.guga.dsh.settings.Settings;
import org.junit.Test;

public class ClaudeSettingsTests {
  @Test public void endpointsHeadersAndValidation() {
    assertEquals("https://relay.example/v1/messages",ModelEndpoint.url("anthropic","https://relay.example/",false));
    assertEquals("https://relay.example/v1/models",ModelEndpoint.url("anthropic","https://relay.example/v1/",true));
    assertEquals("https://relay.example/proxy/v1/messages",ModelEndpoint.url("anthropic","https://relay.example/proxy",false));
    assertEquals("https://api.deepseek.com/chat/completions",ModelEndpoint.url("deepseek","https://api.deepseek.com",false));
    var r=ModelEndpoint.request("anthropic","https://relay.example",false,"test-only-key").build();
    assertEquals("test-only-key",r.header("x-api-key")); assertEquals("2023-06-01",r.header("anthropic-version"));assertNull(r.header("Authorization"));
    assertEquals("Bearer test-only-key",ModelEndpoint.request("anthropic","https://relay.example",true,"test-only-key").build().header("Authorization"));
    for(String invalid:new String[]{"http://relay.example","https://user:pass@relay.example","https://relay.example?key=no","https://relay.example#no","https://relay.example/v1/messages","not a URL"}) {
      try { ModelEndpoint.normalize(invalid); fail("Accepted invalid base"); } catch(IllegalArgumentException expected) {}
    }
  }
  @Test public void profilesMigrateAndRemainIsolatedAcrossRepeatedSwitches() throws Exception {
    var base=InstrumentationRegistry.getInstrumentation().getTargetContext();
    String name="claude-profile-test-"+java.util.UUID.randomUUID();
    Context c=new ContextWrapper(base) { @Override public SharedPreferences getSharedPreferences(String ignored,int mode) { return base.getSharedPreferences(name,mode); } };
    try {
      Settings s=new Settings(c);assertEquals("deepseek",s.provider());
      s.save("https://api.deepseek.com","deepseek-flash","test-deepseek",false,32);
      s.saveModelCatalog("{\"data\":[{\"id\":\"deepseek-flash\"}]}");
      s.save("anthropic","https://relay.example/v1/","claude-sonnet-4-6","test-claude",true,32);
      assertEquals("test-claude",s.apiKey());assertEquals("test-deepseek",s.apiKey("deepseek"));assertEquals("",s.modelCatalog());
      s.saveModelCatalog("{\"data\":[{\"id\":\"claude-sonnet-4-6\"}]}");
      for(int i=0;i<3;i++) {
        for(String protocol:new String[]{"deepseek","anthropic"}) {
          s.save(protocol,s.base(protocol),s.model(protocol),"",true,32);s=new Settings(c);
          assertEquals(protocol,s.provider());assertFalse(s.modelCatalog().isEmpty());
          assertEquals(protocol.equals("anthropic")?"test-claude":"test-deepseek",s.apiKey());
          assertFalse(s.publicData().toString().contains("test-claude"));assertFalse(s.publicData().toString().contains("test-deepseek"));
        }
      }
      s.save("anthropic","https://new.example","custom-model","",true,32);assertEquals("",s.modelCatalog());
      assertEquals("custom-model",s.model());
      try {s.save("anthropic","http://invalid.example","bad","",true,32);fail("invalid URL accepted");}catch(IllegalArgumentException expected) {}
      assertEquals("https://new.example",s.base());assertEquals("custom-model",s.model());
      s.save("deepseek","https://deepseek-relay.example","custom-model","test-relay",false,32);
      assertFalse(s.hasWebKey());
      s.save("anthropic",s.base("anthropic"),s.model("anthropic"),"",true,32);
      // Claude-only credentials must never enable the DeepSeek-only web tools.
      base.getSharedPreferences(name,0).edit().remove("key").commit();
      assertFalse(s.publicData().getBoolean("webSearch"));assertFalse(s.publicData().getBoolean("webFetch"));
    } finally {base.deleteSharedPreferences(name);}
  }
  @Test public void manualModelAndInvalidAddressPersistOnlyValidEdits() throws Exception {
    var i=InstrumentationRegistry.getInstrumentation();var c=i.getTargetContext();
    var prefs=c.getSharedPreferences("settings",0);
    assertFalse("Use a dedicated keyless emulator",prefs.contains("key"));assertFalse(prefs.contains("anthropic.key"));
    String originalBase=prefs.getString("base",null), originalModel=prefs.getString("model",null), originalProvider=prefs.getString("provider",null);
    prefs.edit().putString("provider","deepseek").commit();
    dev.guga.updates.UpdateTestDriver.prefs(c).edit().putString("checkedDay",dev.guga.updates.UpdateTestDriver.today()).commit();
    // Optional isolation for software-only emulators whose native Node engine cannot boot.
    // Protocol/agent integration is exercised separately by anthropic-runtime.mjs.
    boolean isolate=InstrumentationRegistry.getArguments().getString("skipNativeCore", "false").equals("true");
    boolean started=HarnessService.nodeStarted;
    if(isolate) HarnessService.nodeStarted=true;
    MainActivity a=(MainActivity)i.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    ModelSettingsPanel[] panel=new ModelSettingsPanel[1];
    try {
      i.runOnMainSync(()->{a.visible=false;a.busy=false;try {panel[0]=new ModelSettingsPanel(a,false);panel[0].open();}catch(Exception e){throw new AssertionError(e);}});
      i.waitForIdleSync();
      for (String target : new String[]{"Claude / Anthropic", "DeepSeek", "Claude / Anthropic", "DeepSeek"}) {
        i.runOnMainSync(()->touch(panel[0].providerButton,panel[0].providerButton.getWidth()/2f,panel[0].providerButton.getHeight()/2f));
        i.waitForIdleSync(); Thread.sleep(200);
        i.runOnMainSync(()->{
          var list=((android.app.AlertDialog)a.dialogs.peek()).getListView();
          var row=list.getChildAt(target.equals("DeepSeek")?0:1);
          assertNotNull("Visible protocol choice",row);
          touch(list,list.getWidth()/2f,row.getTop()+row.getHeight()/2f);
        });
        i.waitForIdleSync();
        String expected=target.equals("DeepSeek")?"deepseek":"anthropic";
        for(int n=0;n<20 && !expected.equals(new Settings(a).provider());n++)Thread.sleep(100);
        String[] detail={""};i.runOnMainSync(()->detail[0]=panel[0].state.getText().toString());
        assertEquals(detail[0],expected,new Settings(a).provider());
      }
      for(String model:new String[]{"custom-model-a","custom-model-b","custom-model-a"}) {
        i.runOnMainSync(()->{panel[0].manualModel.setText(model);assertTrue(panel[0].persist());assertEquals(model,new Settings(a).model());});
      }
      i.runOnMainSync(()->{
        panel[0].base.setText("https://relay.example/v1");assertTrue(panel[0].persist());
        panel[0].base.setText("http://bad.example");assertFalse(panel[0].persist());assertEquals("https://relay.example/v1",new Settings(a).base());
        panel[0].base.setText("https://relay.example/v1");
        panel[0].close();
        try {panel[0]=new ModelSettingsPanel(a,false);panel[0].open();}catch(Exception e){throw new AssertionError(e);}
        assertEquals("custom-model-a",panel[0].manualModel.getText().toString());
        assertEquals("https://relay.example/v1",panel[0].base.getText().toString());
        a.busy=true;panel[0].manualModel.setText("blocked");assertFalse(panel[0].persist());assertEquals("custom-model-a",new Settings(a).model());a.busy=false;
      });
    } finally {
      i.runOnMainSync(()->{if(panel[0]!=null)panel[0].close();a.finish();});
      var edit=prefs.edit();if(originalBase==null)edit.remove("base");else edit.putString("base",originalBase);
      if(originalModel==null)edit.remove("model");else edit.putString("model",originalModel);
      if(originalProvider==null)edit.remove("provider");else edit.putString("provider",originalProvider);edit.commit();
      if(isolate) HarnessService.nodeStarted=started;
    }
  }
  static void touch(View view,float x,float y) {
    long time=android.os.SystemClock.uptimeMillis();
    MotionEvent down=MotionEvent.obtain(time,time,MotionEvent.ACTION_DOWN,x,y,0);
    MotionEvent up=MotionEvent.obtain(time,time+50,MotionEvent.ACTION_UP,x,y,0);
    down.setSource(InputDevice.SOURCE_TOUCHSCREEN);up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
    view.dispatchTouchEvent(down);view.dispatchTouchEvent(up);down.recycle();up.recycle();
  }
}
