package dev.guga.dsh;
import static org.junit.Assert.*;
import org.junit.Test;
import android.content.*;
import android.widget.*;
import androidx.test.platform.app.InstrumentationRegistry;
public class FileLibrarySettingsTests {
 @Test public void cleanupDefaultAndTogglePersistAcrossReentry()throws Exception{
  var i=InstrumentationRegistry.getInstrumentation();var c=i.getTargetContext();var prefs=c.getSharedPreferences("settings",0);
  boolean had=prefs.contains("cleanupConversationFiles"),old=prefs.getBoolean("cleanupConversationFiles",true);
  prefs.edit().remove("cleanupConversationFiles").commit();
  MainActivity a=(MainActivity)i.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  try{
   assertTrue(new Settings(c).cleanupConversationFiles());
   for(boolean expected:new boolean[]{false,true,false}){
    i.runOnMainSync(()->{
     LinearLayout body=new LinearLayout(a);a.addFileCleanupSetting(body);
     Switch toggle=(Switch)((LinearLayout)body.getChildAt(0)).getChildAt(1);a.showPage("文件库",body);
     assertEquals(new Settings(c).cleanupConversationFiles(),toggle.isChecked());toggle.setChecked(expected);
     a.onBackPressed();
    });
    assertEquals(expected,new Settings(c).cleanupConversationFiles());assertEquals(expected,new Settings(c).publicData().getBoolean("cleanupConversationFiles"));
   }
  }finally{i.runOnMainSync(a::finish);if(had)prefs.edit().putBoolean("cleanupConversationFiles",old).commit();else prefs.edit().remove("cleanupConversationFiles").commit();}
 }
}
