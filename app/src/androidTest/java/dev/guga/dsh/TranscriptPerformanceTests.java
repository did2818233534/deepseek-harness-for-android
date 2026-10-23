package dev.guga.dsh;
import static org.junit.Assert.*;
import org.junit.Test;
import org.json.*;

public class TranscriptPerformanceTests {
 static JSONObject state(String id,int revision,int count)throws Exception{
  JSONArray turns=new JSONArray();String text="## 内容\n\n这是长对话性能测试，用于检查切换时是否反复创建消息视图。**重点内容**以及列表。\n\n- 第一项\n- 第二项\n\n".repeat(5);
  for(int n=0;n<count;n++)turns.put(new JSONObject().put("id",id+n).put("userText","问题 "+n).put("text",text));
  return new JSONObject().put("id",id).put("revision",revision).put("turns",turns);
 }
 @Test public void measureRepeatedSwitch()throws Exception{
  var i=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation();var c=i.getTargetContext();
  dev.guga.updates.UpdateTestDriver.prefs(c).edit().putString("checkedDay",dev.guga.updates.UpdateTestDriver.today()).commit();
  MainActivity a=(MainActivity)i.startActivitySync(new android.content.Intent(c,MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
  JSONObject first=state("perf-a",10001,35),second=state("perf-b",10002,35);long[] time=new long[2];
  try{
   i.runOnMainSync(()->{a.visible=false;a.handler.removeCallbacks(a.poll);long start=System.nanoTime();a.render(first);time[0]=System.nanoTime()-start;});i.waitForIdleSync();
   i.runOnMainSync(()->a.render(second));i.waitForIdleSync();
   i.runOnMainSync(()->{long start=System.nanoTime();a.render(first);time[1]=System.nanoTime()-start;assertEquals(35,a.turnViews.size());});i.waitForIdleSync();
   android.util.Log.i("DSH-Perf","coldMs="+time[0]/1000000.0+" repeatMs="+time[1]/1000000.0);
  }finally{i.runOnMainSync(a::finish);}
 }
 @Test public void cacheRefreshesContentClearsSelectionAndEvicts()throws Exception{
  var i=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation();var c=i.getTargetContext();
  dev.guga.updates.UpdateTestDriver.prefs(c).edit().putString("checkedDay",dev.guga.updates.UpdateTestDriver.today()).commit();
  MainActivity a=(MainActivity)i.startActivitySync(new android.content.Intent(c,MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
  JSONObject first=state("cache-a",20001,2),second=state("cache-b",20002,2);
  try{i.runOnMainSync(()->{try{
   a.visible=false;a.handler.removeCallbacks(a.poll);a.render(first);ChatTurnView original=a.turnViews.get("cache-a0");int updates=original.contentUpdates;
   a.messageActions.toggle("cache-a0/a");a.render(second);assertFalse(a.messageActions.active());a.render(first);assertSame(original,a.turnViews.get("cache-a0"));assertEquals(updates,original.contentUpdates);
   first.put("revision",20003);first.getJSONArray("turns").getJSONObject(1).put("text","后台任务的新回复");a.render(first);assertEquals(updates,original.contentUpdates);assertEquals("后台任务的新回复",a.turnViews.get("cache-a1").markdown.source);
   a.messageActions.selectAll();assertEquals(4,a.messageActions.selected.size());assertTrue(a.messageActions.selectedSource().contains("后台任务的新回复"));a.messageActions.selectAll();assertTrue(a.messageActions.selected.isEmpty());
   a.render(state("cache-c",20004,2));a.render(state("cache-d",20005,2));a.render(state("cache-e",20006,2));assertTrue(a.transcriptViews.cached.size()<=2);assertFalse(a.transcriptViews.cached.containsKey("cache-a"));
   a.render(first);assertNotSame(original,a.turnViews.get("cache-a0"));a.transcriptViews.forget("cache-a");a.render(second);assertFalse(a.transcriptViews.cached.containsKey("cache-a"));
   a.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW);assertTrue(a.transcriptViews.cached.isEmpty());
  }catch(Exception e){throw new RuntimeException(e);}});}finally{i.runOnMainSync(a::finish);}
 }

}
