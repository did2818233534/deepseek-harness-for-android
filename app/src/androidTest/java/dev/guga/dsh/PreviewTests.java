package dev.guga.dsh;
import static org.junit.Assert.*;
import android.content.*;
import android.view.*;
import android.webkit.WebView;
import android.widget.*;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.Test;
import java.util.concurrent.*;

public class PreviewTests {
 @Test public void tapPreviewSelectCopyBackAndLongPressRemainIndependent() throws Exception {
  var i=InstrumentationRegistry.getInstrumentation();var c=i.getTargetContext();
  dev.guga.updates.UpdateTestDriver.prefs(c).edit().putString("checkedDay",dev.guga.updates.UpdateTestDriver.today()).commit();
  MainActivity a=(MainActivity)i.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  String raw="## 阅读预览\n\n**中文内容**与列表。\n\n|需求|推荐渠道|\n|---|---|\n|少量样片|支持个人开发者的样片服务和授权渠道|\n\n```java\nString message = \"你好\";\n```";
  JSONObject state=new JSONObject().put("id","preview-test").put("revision",991).put("turns",new JSONArray().put(new JSONObject().put("id","1").put("userText","预览测试").put("text",raw)));
  try {
   i.runOnMainSync(()->{a.visible=false;a.render(state);ChatTurnView v=a.turnViews.get("1");assertTrue(v.answer.isLongClickable());assertTrue(v.answer.isClickable());v.answer.performClick();assertNotSame(a.chatRoot,a.currentPage);assertFalse(a.messageActions.active());});
   Thread.sleep(700);
   WebView[] web=new WebView[1];
   i.runOnMainSync(()->{LinearLayout page=(LinearLayout)a.currentPage;web[0]=(WebView)page.getChildAt(2);((LinearLayout)page.getChildAt(1)).getChildAt(0).performClick();});
   Thread.sleep(200);
   CountDownLatch done=new CountDownLatch(1);String[] selected=new String[1];
   i.runOnMainSync(()->web[0].evaluateJavascript("window.getSelection().toString()",value->{selected[0]=value;done.countDown();}));
   assertTrue(done.await(5,TimeUnit.SECONDS));assertTrue("Full preview selection: "+selected[0],selected[0].contains("推荐渠道"));
   StreamingTests.screenshot(c,"message-preview.png");
   i.runOnMainSync(()->{((LinearLayout)((LinearLayout)a.currentPage).getChildAt(1)).getChildAt(1).performClick();});Thread.sleep(200);
   i.runOnMainSync(()->{assertTrue(((ClipboardManager)c.getSystemService(Context.CLIPBOARD_SERVICE)).getPrimaryClip().getItemAt(0).getText().toString().contains("中文内容"));a.onBackPressed();assertSame(a.chatRoot,a.currentPage);});
   // Deliver real touch events, not performLongClick (which bypasses longClickable).
   long time=android.os.SystemClock.uptimeMillis();
   i.runOnMainSync(()->{MotionEvent e=MotionEvent.obtain(time,time,MotionEvent.ACTION_DOWN,30,30,0);a.turnViews.get("1").answer.dispatchTouchEvent(e);e.recycle();});
   Thread.sleep(ViewConfiguration.getLongPressTimeout()+150);
   i.runOnMainSync(()->{MotionEvent e=MotionEvent.obtain(time,android.os.SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,30,30,0);a.turnViews.get("1").answer.dispatchTouchEvent(e);e.recycle();assertTrue(a.messageActions.active());assertSame(a.chatRoot,a.currentPage);a.turnViews.get("1").user.performClick();assertEquals(2,a.messageActions.selected.size());});
  } finally {i.runOnMainSync(a::finish);}
 }
}
