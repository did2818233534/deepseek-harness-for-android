package dev.guga.dsh;

import static org.junit.Assert.*;
import android.content.Intent;
import android.view.View;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import dev.guga.dsh.settings.Settings;
import dev.guga.dsh.files.FileTools;

public class ThinkingLimitTests {
  @Test public void toggleReopensAndLargeAttachmentsImport() throws Exception {
    var i=InstrumentationRegistry.getInstrumentation();var c=i.getTargetContext();
    var prefs=c.getSharedPreferences("settings",0);
    assertFalse("Use keyless emulator",prefs.contains("key"));
    prefs.edit().remove("limitThinking").remove("thinkingSeconds").commit();
    dev.guga.updates.UpdateTestDriver.prefs(c).edit().putString("checkedDay",dev.guga.updates.UpdateTestDriver.today()).commit();
    MainActivity a=(MainActivity)i.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    ModelSettingsPanel[] panel=new ModelSettingsPanel[1];
    String[] imported=new String[1];
    try {
      i.runOnMainSync(()->{
        a.visible=false;a.busy=false;
        try {panel[0]=new ModelSettingsPanel(a,false);panel[0].open();}
        catch(Exception e){throw new AssertionError(e);}
        assertFalse(panel[0].timeout.isChecked());
        assertEquals(View.GONE,panel[0].durationBox.getVisibility());
        panel[0].timeout.toggle();
        panel[0].seconds.setText("45");
        assertTrue(panel[0].persist());
        assertEquals(View.VISIBLE,panel[0].durationBox.getVisibility());
        panel[0].close();a.onBackPressed();
        try {panel[0]=new ModelSettingsPanel(a,false);panel[0].open();}
        catch(Exception e){throw new AssertionError(e);}
        assertTrue(panel[0].timeout.isChecked());
        assertEquals("45",panel[0].seconds.getText().toString());
        panel[0].timeout.toggle();panel[0].timeout.toggle();
        assertTrue(panel[0].timeout.isChecked());
      });
      FileTools tools=new FileTools(c);
      try(var input=new java.io.InputStream(){
        int remaining=17*1024*1024;
        public int read(){return remaining-- > 0 ? 65 : -1;}
        public int read(byte[] bytes,int offset,int length){
          if(remaining<=0)return -1;
          int count=Math.min(length,remaining);
          java.util.Arrays.fill(bytes,offset,offset+count,(byte)65);remaining-=count;return count;
        }
      }){imported[0]=tools.importFile(input,"large-test.txt");}
      assertEquals(17L*1024*1024,tools.resolve(imported[0]).length());
      var attachment=Attachments.describe(tools,imported[0],"text/plain");
      i.runOnMainSync(()->{
        a.attachments.reset();
        for(int n=0;n<10;n++)try{
          a.attachments.add(new org.json.JSONObject(attachment.toString()).put("path","test-"+n));
        }catch(Exception e){throw new AssertionError(e);}
        assertEquals(10,a.attachments.pending.size());
        a.attachments.reset();
      });
    } finally {
      if(imported[0]!=null)new FileTools(c).deleteLibraryFile(imported[0]);
      i.runOnMainSync(()->{if(panel[0]!=null)panel[0].close();a.finish();});
      prefs.edit().remove("limitThinking").remove("thinkingSeconds").commit();
    }
  }
}
