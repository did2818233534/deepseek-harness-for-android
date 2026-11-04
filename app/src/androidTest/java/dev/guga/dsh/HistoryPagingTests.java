package dev.guga.dsh;
import static org.junit.Assert.*;
import org.json.*;
import org.junit.Test;
import android.view.*;

public class HistoryPagingTests {
 static JSONObject state(String id,int count,int revision)throws Exception{
  JSONArray turns=new JSONArray();for(int n=0;n<count;n++)turns.put(new JSONObject().put("id","turn-"+n).put("userText","问题-"+n).put("text","**答案-"+n+"**\n\n这是一段历史内容。\n\n这是一段历史内容。"));
  return new JSONObject().put("id",id).put("revision",revision).put("turns",turns);
 }
 @Test public void pagingPreservesAnchorSelectionAndStreaming()throws Exception{
  var i=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation();var c=i.getTargetContext();
  dev.guga.updates.UpdateTestDriver.prefs(c).edit().putString("checkedDay",dev.guga.updates.UpdateTestDriver.today()).commit();
  MainActivity a=(MainActivity)i.startActivitySync(new android.content.Intent(c,MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
  JSONObject history=state("paged",100,50001);View[] anchor=new View[1];int[] offset=new int[1];
  try{
   i.runOnMainSync(()->{a.visible=false;a.handler.removeCallbacks(a.poll);a.render(history);assertEquals(6,a.turnViews.size());assertEquals(94,a.historyStart);assertFalse(a.turnViews.containsKey("turn-0"));
    a.messageActions.selectAll();assertEquals(200,a.messageActions.selected.size());assertTrue(a.messageActions.selectedSource().contains("**答案-0**"));assertTrue(a.messageActions.markdown().contains("**答案-99**"));assertEquals(6,a.turnViews.size());a.messageActions.selectAll();assertTrue(a.messageActions.selected.isEmpty());a.messageActions.clear();
   });i.waitForIdleSync();
   i.runOnMainSync(()->{a.scroll.scrollTo(0,0);anchor[0]=a.turnViews.get("turn-94");offset[0]=anchor[0].getTop()-a.scroll.getScrollY();assertTrue(a.loadOlderTurns());assertFalse(a.loadOlderTurns());});i.waitForIdleSync();Thread.sleep(100);
   i.runOnMainSync(()->{assertEquals(12,a.turnViews.size());assertEquals(offset[0],anchor[0].getTop()-a.scroll.getScrollY());assertFalse(a.historyLoading);});
   history.getJSONArray("turns").put(new JSONObject().put("id","turn-100").put("userText","新问题").put("text","新回复").put("busy",true));history.put("revision",50002).put("busy",true);
   i.runOnMainSync(()->{a.render(history);assertEquals(88,a.historyStart);assertEquals(13,a.turnViews.size());assertSame(anchor[0],a.turnViews.get("turn-94"));});i.waitForIdleSync();
   for(int n=0;n<15;n++){i.runOnMainSync(()->a.loadOlderTurns());i.waitForIdleSync();Thread.sleep(25);}
   i.runOnMainSync(()->{assertEquals(0,a.historyStart);assertEquals(101,a.turnViews.size());assertFalse(a.loadOlderTurns());assertEquals("turn-0",a.turnViews.keySet().iterator().next());});
   JSONObject other=state("other-page",2,50003);
   i.runOnMainSync(()->{a.render(other);a.render(history);assertEquals(6,a.turnViews.size());assertEquals(95,a.historyStart);assertFalse(a.messageActions.active());});
  }finally{i.runOnMainSync(a::finish);}
 }
 @Test public void upwardHistoryGestureLoadsOneBatch()throws Exception{
  var i=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation();var c=i.getTargetContext();
  dev.guga.updates.UpdateTestDriver.prefs(c).edit().putString("checkedDay",dev.guga.updates.UpdateTestDriver.today()).commit();
  MainActivity a=(MainActivity)i.startActivitySync(new android.content.Intent(c,MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
  JSONObject history=state("gesture-page",25,51001);
  try{
   i.runOnMainSync(()->{a.visible=false;a.handler.removeCallbacks(a.poll);a.render(history);});i.waitForIdleSync();
   i.runOnMainSync(()->a.scroll.scrollTo(0,0));i.waitForIdleSync();
   long down=android.os.SystemClock.uptimeMillis();
   for(int n=0;n<=8;n++){final int step=n;i.runOnMainSync(()->{MotionEvent event=MotionEvent.obtain(down,down+step*25,step==0?MotionEvent.ACTION_DOWN:step==8?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE,80,60+step*18,0);a.scroll.dispatchTouchEvent(event);event.recycle();});Thread.sleep(25);}
   i.waitForIdleSync();Thread.sleep(200);
   i.runOnMainSync(()->{assertEquals(13,a.historyStart);assertEquals(12,a.turnViews.size());assertFalse(a.historyLoading);});
  }finally{i.runOnMainSync(a::finish);}
 }
}
