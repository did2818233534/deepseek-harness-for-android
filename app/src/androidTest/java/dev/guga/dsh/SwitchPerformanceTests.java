package dev.guga.dsh;
import static org.junit.Assert.*;
import org.junit.Test;
import org.json.JSONObject;
import java.util.concurrent.*;
public class SwitchPerformanceTests {
 @Test public void switchingBypassesMediaQueueAndRendersWithoutPolling()throws Exception{
  var i=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation();var c=i.getTargetContext();
  dev.guga.updates.UpdateTestDriver.prefs(c).edit().putString("checkedDay",dev.guga.updates.UpdateTestDriver.today()).commit();
  MainActivity a=(MainActivity)i.startActivitySync(new android.content.Intent(c,MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
  CountDownLatch release=new CountDownLatch(1),blocked=new CountDownLatch(1),switched=new CountDownLatch(1);
  try{
   String first=Api.call("/select",new JSONObject()).getString("id");Api.call("/select",new JSONObject());
   a.io.execute(()->{blocked.countDown();try{release.await(8,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});assertTrue(blocked.await(2,TimeUnit.SECONDS));
   i.runOnMainSync(()->{a.visible=true;a.handler.removeCallbacks(a.poll);assertSame(new ChatMarkdown(a).renderer,new ChatMarkdown(a).renderer);try{a.request("/select",new JSONObject().put("id",first),switched::countDown);}catch(Exception e){throw new RuntimeException(e);}});
   assertTrue("Switch must not wait for media decoding",switched.await(3,TimeUnit.SECONDS));i.waitForIdleSync();
   i.runOnMainSync(()->assertEquals("Selected history should render without a polling tick",first,a.currentId));
  }finally{release.countDown();i.runOnMainSync(a::finish);}
 }
}
