package dev.guga.dsh;

import static org.junit.Assert.*;
import android.content.*;
import android.view.View;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import okhttp3.*;
import org.json.*;
import org.junit.Test;

public class StreamingTests {
  @Test public void realBridgeStreamsBeforeCompletion() throws Exception {
    var i=InstrumentationRegistry.getInstrumentation(); Context c=i.getTargetContext();
    dev.guga.updates.UpdateTestDriver.prefs(c).edit().putString("checkedDay",dev.guga.updates.UpdateTestDriver.today()).commit();
    MainActivity a=(MainActivity)i.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    Api.call("/state",new JSONObject()); NativeBridge bridge=HarnessService.instance.bridge; OkHttpClient prior=bridge.api;
    CountDownLatch releaseText=new CountDownLatch(1), releaseEnd=new CountDownLatch(1);
    PipedInputStream in=new PipedInputStream(8192); PipedOutputStream out=new PipedOutputStream(in);
    Thread writer=new Thread(() -> {
      try {
        out.write(event("reasoning_content","正在核对真实返回的内容。").getBytes(StandardCharsets.UTF_8));out.flush();
        releaseText.await(10,TimeUnit.SECONDS);
        out.write(event("content","正文第一段：你好👋").getBytes(StandardCharsets.UTF_8));out.flush();
        releaseEnd.await(10,TimeUnit.SECONDS);
        out.write((event("content","，这是后续内容。")+"data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n").getBytes(StandardCharsets.UTF_8));out.close();
      } catch(Exception ignored) {}
    },"test-delayed-sse");
    bridge.api=new OkHttpClient.Builder().addInterceptor(chain -> new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(new ResponseBody() {
      final okio.BufferedSource source=okio.Okio.buffer(okio.Okio.source(in));
      public MediaType contentType(){return MediaType.get("text/event-stream");}
      public long contentLength(){return -1;}
      public okio.BufferedSource source(){return source;}
    }).build()).build();
    try {
      new Settings(c).save("https://api.deepseek.com","test-model","local-test-key",true,32);
      Api.call("/select",new JSONObject()); writer.start(); Api.call("/send",new JSONObject().put("text","流式显示测试"));
      JSONObject thought=awaitState("正在核对",true);
      i.runOnMainSync(() -> {a.render(thought);assertEquals(1,a.turnViews.size());ChatTurnView v=a.turnViews.values().iterator().next();assertEquals(View.VISIBLE,v.details.getVisibility());});
      releaseText.countDown(); JSONObject partial=awaitState("正文第一段",true);
      i.runOnMainSync(() -> {
        ChatTurnView original=a.turnViews.values().iterator().next();a.input.requestFocus();a.render(partial);
        assertSame(original,a.turnViews.values().iterator().next());assertEquals(View.GONE,original.details.getVisibility());
        assertTrue(original.answer.getText().toString().contains("正文第一段"));assertFalse(original.answer.getText().toString().contains("后续内容"));
        assertTrue(a.input.hasFocus());
      });
      releaseEnd.countDown();JSONObject done=awaitState("后续内容",false);
      i.runOnMainSync(() -> {
        a.render(done);ChatTurnView v=a.turnViews.values().iterator().next();assertEquals(View.GONE,v.details.getVisibility());
        assertTrue(v.caption.getText().toString().contains("秒"));v.header.performClick();assertEquals(View.VISIBLE,v.details.getVisibility());
        a.render(done);assertEquals(View.VISIBLE,v.details.getVisibility());v.header.performClick();assertEquals(View.GONE,v.details.getVisibility());
      });
      screenshot(c,"process-collapsed.png");
      i.runOnMainSync(() -> a.turnViews.values().iterator().next().header.performClick());
      screenshot(c,"process-expanded.png");
    } finally {
      releaseText.countDown();releaseEnd.countDown();writer.join(3000);in.close();out.close();
      bridge.api=prior;c.getSharedPreferences("settings",0).edit().clear().commit();i.runOnMainSync(a::finish);
    }
  }
  static void screenshot(Context c,String name) throws Exception {
    var i=InstrumentationRegistry.getInstrumentation();i.waitForIdleSync();
    // Let the compositor present the just-updated expansion before capturing.
    Thread.sleep(200);
    try(FileOutputStream file=new FileOutputStream(new File(c.getExternalFilesDir(null),name))) {
      android.graphics.Bitmap image=i.getUiAutomation().takeScreenshot();image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,file);image.recycle();
    }
  }
  static String event(String field,String text) throws Exception {
    return "data: "+new JSONObject().put("choices",new JSONArray().put(new JSONObject().put("delta",new JSONObject().put(field,text))))+"\n\n";
  }
  static JSONObject awaitState(String text,boolean busy) throws Exception {
    long end=System.currentTimeMillis()+6000;JSONObject state=null;
    do {state=Api.call("/state",new JSONObject());if(state.optBoolean("busy")==busy&&state.getJSONArray("turns").toString().contains(text))return state;Thread.sleep(40);}while(System.currentTimeMillis()<end);
    throw new AssertionError("Expected streamed phase before server completion: "+text+"; "+state);
  }
  @Test public void processOnlyAppearsForActualReasoningOrTools() throws Exception {
    var i=InstrumentationRegistry.getInstrumentation();Context c=i.getTargetContext();
    dev.guga.updates.UpdateTestDriver.prefs(c).edit().putString("checkedDay",dev.guga.updates.UpdateTestDriver.today()).commit();
    MainActivity a=(MainActivity)i.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    JSONObject plain=new JSONObject().put("text","普通回复").put("busy",false).put("tools",new JSONArray());
    JSONObject tool=new JSONObject().put("text","搜索结果").put("busy",false).put("processElapsedMs",12345)
      .put("tools",new JSONArray().put(new JSONObject().put("name","web_search").put("status","completed").put("detail","北京天气")));
    i.runOnMainSync(() -> {ChatTurnView view=new ChatTurnView(a);view.update(plain);assertEquals(View.GONE,view.process.getVisibility());
      view.update(tool);assertEquals(View.VISIBLE,view.process.getVisibility());assertEquals(View.GONE,view.details.getVisibility());
      assertTrue(view.caption.getText().toString().contains("13 秒"));view.header.performClick();assertTrue(view.steps.getText().toString().contains("搜索网页"));
      assertEquals(View.GONE,view.reasoning.getVisibility());a.finish();});
  }
}
