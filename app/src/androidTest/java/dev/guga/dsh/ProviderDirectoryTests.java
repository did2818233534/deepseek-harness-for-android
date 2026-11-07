package dev.guga.dsh;
import static org.junit.Assert.*;
import android.content.*;
import android.widget.*;
import androidx.test.platform.app.InstrumentationRegistry;
import dev.guga.dsh.settings.*;
import org.junit.Test;

public class ProviderDirectoryTests {
  @Test public void defaultsEnableOnlyDeepSeekAndDisabledProvidersKeepSelections() throws Exception {
    var base=InstrumentationRegistry.getInstrumentation().getTargetContext();String name="supplier-toggle-"+java.util.UUID.randomUUID();
    Context c=new ContextWrapper(base){@Override public SharedPreferences getSharedPreferences(String ignored,int mode){return base.getSharedPreferences(name,mode);}};
    try {
      Settings s=new Settings(c);assertTrue(s.providerEnabled("deepseek"));assertFalse(s.providerEnabled("anthropic"));
      s.setModelEnabled("anthropic","claude-opus-5-5",true);
      try{s.selectModel("anthropic","claude-opus-5-5");fail();}catch(Exception expected){}
      for(int n=0;n<3;n++) {
        s.setProviderEnabled("anthropic",true);s.setProviderEnabled("deepseek",false);
        assertEquals("anthropic",s.providersByEnabled().get(0).id);
        s.selectModel("anthropic","claude-opus-5-5");
        s.setProviderEnabled("anthropic",false);assertTrue(s.enabledModels("anthropic").contains("claude-opus-5-5"));
        s.setProviderEnabled("deepseek",true);assertEquals("deepseek",s.providersByEnabled().get(0).id);
      }
    }finally{base.deleteSharedPreferences(name);}
  }
  @Test public void directorySwitchesReorderAndCardsOpenSettings() throws Exception {
    var i=InstrumentationRegistry.getInstrumentation();var c=i.getTargetContext();var prefs=c.getSharedPreferences("settings",0);
    assertFalse(prefs.contains("key"));assertFalse(prefs.contains("anthropic.key"));
    prefs.edit().remove("enabled").remove("anthropic.enabled").commit();
    boolean previous=HarnessService.nodeStarted;HarnessService.nodeStarted=true;
    MainActivity a=(MainActivity)i.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    try {
      i.runOnMainSync(()->{a.visible=false;a.busy=false;a.settingsPages.providers();});i.waitForIdleSync();Thread.sleep(200);
      for(int turn=0;turn<2;turn++) {
        for(String label:new String[]{"启用 Claude / Anthropic","启用 DeepSeek"}) {
          i.runOnMainSync(()->{
            var rows=a.settingsPages.providerRows;Switch control=null;
            for(int n=0;n<rows.getChildCount();n++) {var card=(LinearLayout)rows.getChildAt(n);var toggle=(Switch)card.getChildAt(1);if(label.contentEquals(toggle.getContentDescription()))control=toggle;}
            assertNotNull(control);ClaudeSettingsTests.touch(control,control.getWidth()/2f,control.getHeight()/2f);
          });i.waitForIdleSync();Thread.sleep(150);
        }
        assertEquals(turn==0?"anthropic":"deepseek",new Settings(a).providersByEnabled().get(0).id);
      }
      android.view.View directory=a.currentPage;
      i.runOnMainSync(()->{var card=a.settingsPages.providerRows.getChildAt(0);ClaudeSettingsTests.touch(card,card.getWidth()/3f,card.getHeight()/2f);});i.waitForIdleSync();
      assertNotSame(directory,a.currentPage);assertEquals("deepseek",new Settings(a).editingProvider());
      i.runOnMainSync(a::onBackPressed);i.waitForIdleSync();assertSame(directory,a.currentPage);
      ProviderVisualTests.capture(a,"provider-directory.png");
    }finally{i.runOnMainSync(a::finish);prefs.edit().remove("enabled").remove("anthropic.enabled").commit();HarnessService.nodeStarted=previous;}
  }
}
