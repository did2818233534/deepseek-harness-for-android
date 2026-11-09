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
      // Web settings stay available without a DeepSeek key: on-device fallback is independent.
      base.getSharedPreferences(name,0).edit().remove("key").commit();
      assertEquals(s.webSearch(),s.publicData().getBoolean("webSearch"));assertEquals(s.webFetch(),s.publicData().getBoolean("webFetch"));
    } finally {base.deleteSharedPreferences(name);}
  }
  @Test public void enabledModelsAndDailyRefreshAreIndependent() throws Exception {
    var base=InstrumentationRegistry.getInstrumentation().getTargetContext();
    String name="provider-selection-test-"+java.util.UUID.randomUUID();
    Context c=new ContextWrapper(base){@Override public SharedPreferences getSharedPreferences(String ignored,int mode){return base.getSharedPreferences(name,mode);}};
    try {
      Settings s=new Settings(c);
      s.save("deepseek","https://api.deepseek.com","same-id","test-ds",false,32);
      s.saveProfile("anthropic","https://relay.example","same-id","test-cl",false,32,false);
      s.setProviderEnabled("anthropic",true);
      assertEquals("deepseek",s.provider());
      for(int n=0;n<3;n++) {
        for(String provider:new String[]{"deepseek","anthropic"}) {
          s.setModelEnabled(provider,"same-id",true);s.setModelEnabled(provider,"extra",true);
          assertEquals(2,s.enabledModels(provider).size());
          s.setModelEnabled(provider,"same-id",false);s.setModelEnabled(provider,"extra",false);
          assertTrue(s.enabledModels(provider).isEmpty());
          s.setModelEnabled(provider,"same-id",true);
          s.selectModel(provider,"same-id");assertEquals(provider,s.provider());
          assertEquals(provider.equals("deepseek")?"test-ds":"test-cl",s.apiKey());
        }
      }
      s.saveModelCatalog("anthropic","{\"data\":[{\"id\":\"new-unselected\"}]}");
      assertFalse(s.enabledModels("anthropic").contains("new-unselected"));
      assertTrue(s.claimCatalogRefresh("deepseek","2026-11-07"));
      assertFalse(new Settings(c).claimCatalogRefresh("deepseek","2026-11-07"));
      assertTrue(s.claimCatalogRefresh("anthropic","2026-11-07"));
      assertTrue(s.claimCatalogRefresh("deepseek","2026-11-08"));
      s.markCatalogRefresh("anthropic","2026-11-08");
      assertFalse(s.claimCatalogRefresh("anthropic","2026-11-08"));
    }finally{base.deleteSharedPreferences(name);}
  }
  @Test public void customEndpointsAndResetRemainProviderScoped() throws Exception {
    var base=InstrumentationRegistry.getInstrumentation().getTargetContext();String name="endpoint-test-"+java.util.UUID.randomUUID();
    Context c=new ContextWrapper(base){@Override public SharedPreferences getSharedPreferences(String ignored,int mode){return base.getSharedPreferences(name,mode);}};
    try {
      Settings s=new Settings(c);s.save("anthropic","https://relay.example/prefix/v1","claude-opus-5-5","fake-secret",false,32);
      assertEquals("https://relay.example/prefix/v1/messages",s.endpoint("anthropic","model"));
      java.util.Map<String,String> paths=new java.util.HashMap<>();
      for(String kind:dev.guga.dsh.settings.ProviderRegistry.ENDPOINTS)paths.put(kind,dev.guga.dsh.settings.ProviderRegistry.defaultPath("anthropic",kind));
      paths.put("model","/custom/chat");paths.put("search","/custom/search");paths.put("usage","/custom/usage");
      s.saveEndpoints("anthropic",paths);s.saveUsageKey("anthropic","fake-admin-key");
      assertEquals("https://relay.example/prefix/v1/custom/search",s.endpoint("anthropic","search"));
      assertFalse(s.publicData().toString().contains("fake-admin-key"));
      assertEquals("/chat/completions",s.endpointPath("deepseek","model"));
      for(String invalid:new String[]{"https://other.example/x","//other.example/x","/../x","/x?token=key","/x#fragment"}){
        paths.put("model",invalid);try{s.saveEndpoints("anthropic",paths);fail();}catch(IllegalArgumentException expected){}
        assertEquals("/custom/chat",s.endpointPath("anthropic","model"));
      }
      s.setModelEnabled("anthropic","claude-opus-5-5",true);
      s.resetProviderDefaults("anthropic");s.resetProviderDefaults("anthropic");
      assertEquals("https://api.anthropic.com",s.base("anthropic"));assertEquals("/v1/messages",s.endpointPath("anthropic","search"));
      assertEquals("fake-secret",s.apiKey("anthropic"));assertEquals("fake-admin-key",s.usageKey("anthropic"));assertTrue(s.enabledModels("anthropic").contains("claude-opus-5-5"));
      assertEquals("/chat/completions",s.endpointPath("deepseek","model"));
    }finally{base.deleteSharedPreferences(name);}
  }
  @Test public void manualModelAndInvalidAddressPersistOnlyValidEdits() throws Exception {
    var i=InstrumentationRegistry.getInstrumentation();var c=i.getTargetContext();
    var prefs=c.getSharedPreferences("settings",0);
    assertFalse("Use a dedicated keyless emulator",prefs.contains("key"));assertFalse(prefs.contains("anthropic.key"));
    String originalBase=prefs.getString("base",null), originalModel=prefs.getString("model",null), originalProvider=prefs.getString("provider",null);
    prefs.edit().putString("provider","deepseek").putString("editingProvider","deepseek").commit();
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
      assertEquals(View.VISIBLE,panel[0].basicPage.getVisibility());assertEquals(View.GONE,panel[0].morePage.getVisibility());
      i.runOnMainSync(()->{try{panel[0].catalog("{\"data\":[{\"id\":\"model-a\"},{\"id\":\"model-b\"}]}");}catch(Exception e){throw new AssertionError(e);}});
      i.waitForIdleSync();
      for(int pass=0;pass<3;pass++) {
        for(String id:new String[]{"model-a","model-b"}) {
          i.runOnMainSync(()->{
            View toggle=GeneralSettingsTests.findDescription(panel[0].modelRows,"启用模型 "+id);
            assertNotNull(toggle);touch(toggle,toggle.getWidth()/2f,toggle.getHeight()/2f);
          });i.waitForIdleSync();
        }
        assertEquals(pass!=1,new Settings(a).enabledModels("deepseek").contains("model-a"));
        assertEquals(pass!=1,new Settings(a).enabledModels("deepseek").contains("model-b"));
      }
      for(int repeat=0;repeat<2;repeat++) {
        i.runOnMainSync(()->touch(panel[0].moreTab,20,20));i.waitForIdleSync();
        assertSame(panel[0].page,a.currentPage);assertEquals(View.VISIBLE,panel[0].morePage.getVisibility());
        final String next="https://edited-"+repeat+".example";
        i.runOnMainSync(()->{panel[0].base.setText(next);touch(panel[0].modelsTab,20,20);});i.waitForIdleSync();
        assertSame(panel[0].page,a.currentPage);assertEquals(View.VISIBLE,panel[0].basicPage.getVisibility());
        assertEquals(next,new Settings(a).base(panel[0].protocol));
      }
      i.runOnMainSync(()->touch(panel[0].moreTab,20,20));i.waitForIdleSync();
      i.runOnMainSync(()->{panel[0].manualModel.setText("manual-disabled");touch(panel[0].addModel,20,20);});i.waitForIdleSync();
      assertTrue(new Settings(a).customModels("deepseek").contains("manual-disabled"));
      assertFalse(new Settings(a).enabledModels("deepseek").contains("manual-disabled"));
      for(String model:new String[]{"custom-model-a","custom-model-b","custom-model-a"}) {
        i.runOnMainSync(()->{panel[0].manualModel.setText(model);assertTrue(panel[0].persist());assertEquals(model,new Settings(a).model(panel[0].protocol));});
      }
      i.runOnMainSync(()->{
        panel[0].base.setText("https://relay.example/v1");assertTrue(panel[0].persist());
        panel[0].base.setText("http://bad.example");assertFalse(panel[0].persist());assertEquals("https://relay.example/v1",new Settings(a).base());
        panel[0].base.setText("https://relay.example/v1");
        panel[0].close();
        try {panel[0]=new ModelSettingsPanel(a,false);panel[0].open();}catch(Exception e){throw new AssertionError(e);}
        assertEquals("custom-model-a",panel[0].manualModel.getText().toString());
        assertEquals("https://relay.example/v1",panel[0].base.getText().toString());
        a.busy=true;panel[0].manualModel.setText("blocked");assertFalse(panel[0].persist());assertEquals("custom-model-a",new Settings(a).model(panel[0].protocol));a.busy=false;
      });
      i.runOnMainSync(()->{
        panel[0].manualModel.setText("custom-model-a");panel[0].persist();panel[0].close();
        try {
          new Settings(a).setProviderEnabled("anthropic",true);
          new Settings(a).setModelEnabled("anthropic","claude-opus-5-5",true);
          panel[0]=new ModelSettingsPanel(a,true);panel[0].open();
        }catch(Exception e){throw new AssertionError(e);}
      });i.waitForIdleSync();Thread.sleep(200);
      for(int at:new int[]{2,0,2,1}) {
        i.runOnMainSync(()->touch(panel[0].modelButton,panel[0].modelButton.getWidth()/2f,panel[0].modelButton.getHeight()/2f));
        i.waitForIdleSync();Thread.sleep(200);
        i.runOnMainSync(()->{
          var rows=(android.widget.LinearLayout)((android.widget.ScrollView)panel[0].popup.getContentView()).getChildAt(0);
          assertEquals(3,rows.getChildCount());
          var row=rows.getChildAt(at);touch(row,row.getWidth()/2f,row.getHeight()/2f);
        });i.waitForIdleSync();Thread.sleep(100);
        assertEquals(at==2?"anthropic":"deepseek",new Settings(a).provider());
        assertEquals(at==2?"claude-opus-5-5":at==0?"model-a":"model-b",new Settings(a).model());
      }
      i.runOnMainSync(()->{
        panel[0].dialog.dismiss();panel[0].close();
        try{panel[0]=new ModelSettingsPanel(a,false);panel[0].open();}catch(Exception e){throw new AssertionError(e);}
        assertFalse(panel[0].base.isShown());assertTrue(panel[0].key.isShown());
        panel[0].close();
        try{panel[0]=new ModelSettingsPanel(a,false,true);panel[0].open();}catch(Exception e){throw new AssertionError(e);}
        assertTrue(panel[0].base.isShown());assertFalse(panel[0].key.isShown());
        panel[0].endpointFields.get("search").setText("/custom/search");assertTrue(panel[0].persist());
        panel[0].resetButton.requestRectangleOnScreen(new android.graphics.Rect(0,0,panel[0].resetButton.getWidth(),panel[0].resetButton.getHeight()),true);
      });i.waitForIdleSync();Thread.sleep(200);
      i.runOnMainSync(()->{touch(panel[0].resetButton,panel[0].resetButton.getWidth()/2f,panel[0].resetButton.getHeight()/2f);});
      i.waitForIdleSync();Thread.sleep(100);
      assertEquals(dev.guga.dsh.settings.ProviderRegistry.defaultPath(panel[0].protocol,"search"),new Settings(a).endpointPath(panel[0].protocol,"search"));
    } finally {
      i.runOnMainSync(()->{if(panel[0]!=null)panel[0].close();a.finish();});
      var edit=prefs.edit().remove("customModels").remove("enabledModels").remove("anthropic.enabledModels").remove("activeModel").remove("anthropic.enabled");if(originalBase==null)edit.remove("base");else edit.putString("base",originalBase);
      if(originalModel==null)edit.remove("model");else edit.putString("model",originalModel);
      if(originalProvider==null)edit.remove("provider");else edit.putString("provider",originalProvider);edit.commit();
      if(isolate) HarnessService.nodeStarted=started;
    }
  }
  @Test public void emptyModelHintOpensSettingsAndReturnsRepeatedly() throws Exception {
    var i=InstrumentationRegistry.getInstrumentation();var c=i.getTargetContext();
    var prefs=c.getSharedPreferences("settings",0);
    assertFalse(prefs.contains("key"));assertFalse(prefs.contains("anthropic.key"));
    var settings=new Settings(c);
    boolean ds=settings.providerEnabled("deepseek"),cl=settings.providerEnabled("anthropic"),started=HarnessService.nodeStarted;
    HarnessService.nodeStarted=true;
    settings.setProviderEnabled("deepseek",false);settings.setProviderEnabled("anthropic",false);
    MainActivity a=(MainActivity)i.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    ModelSettingsPanel[] panel={null};
    try {
      i.runOnMainSync(()->{a.visible=false;a.busy=false;a.settings();});i.waitForIdleSync();
      ProviderVisualTests.capture(a,"settings-help-alignment.png");
      for(int repeat=0;repeat<2;repeat++) {
        i.runOnMainSync(()->{
          View help=GeneralSettingsTests.findDescription(a.currentPage,"模型与服务说明");
          View other=GeneralSettingsTests.findDescription(a.currentPage,"通用设置说明");
          int[] first=new int[2],second=new int[2];help.getLocationOnScreen(first);other.getLocationOnScreen(second);
          assertEquals(first[0],second[0]);
          var row=(android.view.ViewGroup)help.getParent();assertSame(help,row.getChildAt(row.getChildCount()-1));
          assertTrue(help.getLeft()>row.getChildAt(row.getChildCount()-2).getLeft());
          assertEquals(a.dp(48),help.getWidth());assertEquals(a.dp(48),help.getHeight());
          assertEquals(a.dp(96),row.getWidth());assertTrue(row.getHeight()>=a.dp(64));
          touch(help,20,20);
        });i.waitForIdleSync();
        i.runOnMainSync(()->{
          var dialog=(android.app.AlertDialog)a.dialogs.peek();assertTrue(dialog.isShowing());
          touch(dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE),20,20);
        });i.waitForIdleSync();assertTrue(a.dialogs.isEmpty());assertNotNull(a.currentPage);
        View settingsPage=a.currentPage;
        i.runOnMainSync(()->touch(GeneralSettingsTests.findDescription(a.currentPage,"打开模型与服务"),20,20));
        i.waitForIdleSync();assertNotSame(settingsPage,a.currentPage);
        i.runOnMainSync(a::onBackPressed);i.waitForIdleSync();assertSame(settingsPage,a.currentPage);
      }
      i.runOnMainSync(a::onBackPressed);i.waitForIdleSync();
      for(int repeat=0;repeat<2;repeat++) {
        i.runOnMainSync(()->{a.visible=false;a.busy=false;try{panel[0]=new ModelSettingsPanel(a,true);panel[0].open();}catch(Exception e){throw new AssertionError(e);}});
        i.waitForIdleSync();
        i.runOnMainSync(()->touch(panel[0].modelButton,20,20));i.waitForIdleSync();
        if(repeat==0)ProviderVisualTests.capture(a,"empty-model-hint.png");
        i.runOnMainSync(()->{
          var rows=(android.widget.LinearLayout)((android.widget.ScrollView)panel[0].popup.getContentView()).getChildAt(0);
          assertEquals("还没有可选模型",((android.widget.TextView)rows.getChildAt(0)).getText().toString());
          assertEquals(3,rows.getChildCount());assertTrue(panel[0].popup.getHeight()>0);assertTrue(panel[0].popup.getHeight()<=a.dp(320));
          touch(rows.getChildAt(2),20,20);
        });i.waitForIdleSync();
        assertFalse(panel[0].popup.isShowing());assertFalse(panel[0].dialog.isShowing());assertNotNull(a.currentPage);
        i.runOnMainSync(a::onBackPressed);i.waitForIdleSync();
        assertSame(a.chatRoot,a.currentPage);
      }
    }finally{
      i.runOnMainSync(()->{if(panel[0]!=null)panel[0].close();a.finish();});
      settings.setProviderEnabled("deepseek",ds);settings.setProviderEnabled("anthropic",cl);HarnessService.nodeStarted=started;
    }
  }
  static android.widget.EditText findEdit(View view,String text) {
    if(view instanceof android.widget.EditText && text.contentEquals(((android.widget.EditText)view).getText()))return (android.widget.EditText)view;
    if(view instanceof android.view.ViewGroup){var group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++){var found=findEdit(group.getChildAt(i),text);if(found!=null)return found;}}
    return null;
  }
  static void touch(View view,float x,float y) {
    long time=android.os.SystemClock.uptimeMillis();
    MotionEvent down=MotionEvent.obtain(time,time,MotionEvent.ACTION_DOWN,x,y,0);
    MotionEvent up=MotionEvent.obtain(time,time+50,MotionEvent.ACTION_UP,x,y,0);
    down.setSource(InputDevice.SOURCE_TOUCHSCREEN);up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
    view.dispatchTouchEvent(down);view.dispatchTouchEvent(up);down.recycle();up.recycle();
  }
}
