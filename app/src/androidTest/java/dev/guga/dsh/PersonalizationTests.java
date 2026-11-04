package dev.guga.dsh;
import dev.guga.dsh.settings.Settings;
import static org.junit.Assert.*;
import android.content.*;
import android.view.*;
import android.widget.EditText;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;

public class PersonalizationTests {
 static View named(View v,String name){
  if(name.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;
  if(v instanceof ViewGroup)for(int n=0;n<((ViewGroup)v).getChildCount();n++){
   View found=named(((ViewGroup)v).getChildAt(n),name);if(found!=null)return found;
  }return null;
 }
 @Test public void editReturnReopenAndClear(){
  var i=InstrumentationRegistry.getInstrumentation();var c=i.getTargetContext();
  var prefs=new Settings(c);String old=prefs.customInstructions();
  MainActivity a=(MainActivity)i.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  try {i.runOnMainSync(()->{
   a.settings();View settings=a.currentPage;assertTrue(named(settings,"个性化").performClick());
   EditText edit=(EditText)named(a.currentPage,"自定义指令");edit.setText("说中文\n先给结论");
   a.onBackPressed();assertSame(settings,a.currentPage);named(settings,"个性化").performClick();
   edit=(EditText)named(a.currentPage,"自定义指令");assertEquals("说中文\n先给结论",edit.getText().toString());
   edit.setText("");a.onBackPressed();assertEquals("",new Settings(c).customInstructions());
  });}finally{prefs.saveCustomInstructions(old);i.runOnMainSync(a::finish);}
 }
}
