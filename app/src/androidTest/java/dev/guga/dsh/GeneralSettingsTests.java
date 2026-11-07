package dev.guga.dsh;
import static org.junit.Assert.*;
import android.content.Intent;
import androidx.test.platform.app.InstrumentationRegistry;
import dev.guga.dsh.settings.Settings;
import org.junit.Test;

public class GeneralSettingsTests {
  static android.view.View findDescription(android.view.View view,String text) {
    if(text.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;
    if(view instanceof android.view.ViewGroup) {
      var group=(android.view.ViewGroup)view;
      for(int n=0;n<group.getChildCount();n++){var found=findDescription(group.getChildAt(n),text);if(found!=null)return found;}
    }
    return null;
  }
  @Test public void sharedSettingsSurviveProviderEditsAndRepeatedTouches() throws Exception {
    var i=InstrumentationRegistry.getInstrumentation();var c=i.getTargetContext();
    var prefs=c.getSharedPreferences("settings",0);
    assertFalse(prefs.contains("key"));assertFalse(prefs.contains("anthropic.key"));
    var saved=new java.util.HashMap<String,Object>(prefs.getAll());
    boolean previous=HarnessService.nodeStarted;HarnessService.nodeStarted=true;
    MainActivity a=(MainActivity)i.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    ModelSettingsPanel[] panel={null};
    try {
      i.runOnMainSync(()->{
        a.visible=false;a.busy=false;
        try {
          Settings s=new Settings(a);s.saveGeneral(true,45,true,false,false,17);
          panel[0]=new ModelSettingsPanel(a,false);panel[0].openGeneral();
          assertEquals("45",panel[0].seconds.getText().toString());
          assertEquals("17",panel[0].steps.getText().toString());
          assertNull(panel[0].effortBox);
        }catch(Exception e){throw new AssertionError(e);}
      });
      i.waitForIdleSync();
      boolean[] expected={false};
      for(int n=0;n<3;n++) {
        i.runOnMainSync(()->{
          var p=panel[0];expected[0]=!p.search.isChecked();
          ClaudeSettingsTests.touch(p.search,p.search.getWidth()/2f,p.search.getHeight()/2f);

        });
        i.waitForIdleSync();
        i.runOnMainSync(()->assertEquals(expected[0],new Settings(a).webSearch()));
      }
      i.runOnMainSync(()->{
        try {
          var p=panel[0];Settings s=new Settings(a);
          p.steps.setText("0");assertFalse(p.persist());assertEquals(17,s.publicData().getInt("maxSteps"));
          p.steps.setText("24");p.seconds.setText("0");assertFalse(p.persist());assertEquals(17,s.publicData().getInt("maxSteps"));
          p.seconds.setText("60");assertTrue(p.persist());assertEquals(24,s.publicData().getInt("maxSteps"));
          a.onBackPressed();
          panel[0]=new ModelSettingsPanel(a,false);panel[0].openGeneral();
          assertEquals("24",panel[0].steps.getText().toString());assertEquals("60",panel[0].seconds.getText().toString());
          a.onBackPressed();
          panel[0]=new ModelSettingsPanel(a,false,true);panel[0].open();
          assertNull(panel[0].steps);assertNull(panel[0].search);assertNull(panel[0].timeout);
          s.saveGeneral(true,75,true,true,true,31);
          assertTrue(panel[0].persist());
          assertEquals(31,s.publicData().getInt("maxSteps"));
          assertEquals(75,s.publicData().getInt("thinkingSeconds"));assertTrue(s.webSearch());assertTrue(s.webFetch());
          a.onBackPressed();
          panel[0]=new ModelSettingsPanel(a,true);panel[0].open();
          assertEquals(s.reasoningEffort(),panel[0].effort);assertTrue(panel[0].search.isChecked());
          panel[0].close();panel[0].dialog.dismiss();
          panel[0]=new ModelSettingsPanel(a,false);panel[0].openGeneral();
        }catch(Exception e){throw new AssertionError(e);}
      });
      i.waitForIdleSync();ProviderVisualTests.capture(a,"general-settings.png");
      i.runOnMainSync(()->{
        try {
          a.onBackPressed();
          Settings s=new Settings(a);
          s.setProviderEnabled("deepseek",true);s.setProviderEnabled("anthropic",true);
          s.setModelEnabled("deepseek","deepseek-v4-pro",true);s.setModelEnabled("anthropic","claude-opus-5-5",true);
          s.selectModel("deepseek","deepseek-v4-pro");
          panel[0]=new ModelSettingsPanel(a,true);panel[0].open();
        }catch(Exception e){throw new AssertionError(e);}
      });
      i.waitForIdleSync();
      for(String provider:new String[]{"anthropic","deepseek","anthropic"}) {
        String model=provider.equals("anthropic")?"claude-opus-5-5":"deepseek-v4-pro";
        i.runOnMainSync(()->ClaudeSettingsTests.touch(panel[0].modelButton,20,20));
        i.waitForIdleSync();
        i.runOnMainSync(()->{
          var row=findDescription(panel[0].popup.getContentView(),"聊天模型 "+provider+"/"+model);
          assertNotNull(row);ClaudeSettingsTests.touch(row,20,20);
        });
        i.waitForIdleSync();
        i.runOnMainSync(()->{
          var bar=ReasoningSettingsTests.slider(panel[0].box);assertNotNull(bar);
          assertEquals(provider.equals("anthropic")?4:3,bar.getMax());
          ReasoningSettingsTests.touch(bar,provider.equals("anthropic")?3:1);
          assertEquals(provider.equals("anthropic")?"xhigh":"low",new Settings(a).reasoningEffort());
        });
      }
      i.waitForIdleSync();ProviderVisualTests.capture(a,"claude-reasoning.png");
    }finally{
      i.runOnMainSync(()->{if(panel[0]!=null)panel[0].close();a.finish();});
      i.waitForIdleSync();
      var edit=prefs.edit().clear();
      for(var entry:saved.entrySet()) {
        Object v=entry.getValue();String k=entry.getKey();
        if(v instanceof String)edit.putString(k,(String)v);
        else if(v instanceof Boolean)edit.putBoolean(k,(Boolean)v);
        else if(v instanceof Integer)edit.putInt(k,(Integer)v);
        else if(v instanceof Long)edit.putLong(k,(Long)v);
        else if(v instanceof Float)edit.putFloat(k,(Float)v);
        else if(v instanceof java.util.Set)edit.putStringSet(k,(java.util.Set<String>)v);
      }
      edit.commit();HarnessService.nodeStarted=previous;
    }
  }
}
