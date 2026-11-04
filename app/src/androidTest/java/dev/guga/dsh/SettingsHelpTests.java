package dev.guga.dsh;
import static org.junit.Assert.*;
import android.content.Intent;
import android.view.View;
import org.junit.Test;
import androidx.test.platform.app.InstrumentationRegistry;

public class SettingsHelpTests {
 static int x(MainActivity a,String name){View v=PersonalizationTests.named(a.currentPage,name+"说明");assertNotNull(name,v);int[] pos=new int[2];v.getLocationOnScreen(pos);return pos[0];}
 @Test public void columnsAcrossPages()throws Exception{
  var i=InstrumentationRegistry.getInstrumentation();var c=i.getTargetContext();
  MainActivity a=(MainActivity)i.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  try{
   int[] ref={0};i.runOnMainSync(a::settings);i.waitForIdleSync();i.runOnMainSync(()->ref[0]=x(a,"个性化"));
   String[] names={"自动更新","删除对话时清理关联文件","自定义指令","图片自动切换模型"};
   Runnable[] pages={a::updateSettings,a::files,a::personalization,()->a.modelSettings(false)};
   for(int n=0;n<pages.length;n++){String title=names[n];i.runOnMainSync(pages[n]);i.waitForIdleSync();i.runOnMainSync(()->{assertEquals(title,ref[0],x(a,title));if(title.equals("图片自动切换模型")){assertEquals(ref[0],x(a,"API Key"));assertEquals(ref[0],x(a,"网页读取"));}a.onBackPressed();});i.waitForIdleSync();}
  }finally{i.runOnMainSync(a::finish);}
 }
 @Test public void sharedColumnAndIndependentHelp() throws Exception {
  var i=InstrumentationRegistry.getInstrumentation();var c=i.getTargetContext();
  MainActivity a=(MainActivity)i.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  try{
   i.runOnMainSync(a::settings);i.waitForIdleSync();
   i.runOnMainSync(()->{
    int x=-1;
    for(String title:new String[]{"DeepSeek","个性化","已归档对话","文件库","应用更新"}){
     View q=PersonalizationTests.named(a.currentPage,title+"说明");int[] pos=new int[2];q.getLocationOnScreen(pos);
     assertTrue(q.getWidth()>=a.dp(48));if(x!=-1)assertEquals(x,pos[0]);x=pos[0];
    }
    try{ModelSettingsPanel p=new ModelSettingsPanel(a,false);p.open();boolean before=p.autoImageModel.isChecked();
     View q=PersonalizationTests.named(a.currentPage,"图片自动切换模型说明");assertTrue(q.performClick());assertEquals(before,p.autoImageModel.isChecked());
    }catch(Exception e){throw new RuntimeException(e);}
   });
  }finally{i.runOnMainSync(a::finish);}
 }
}
