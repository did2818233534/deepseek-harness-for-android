package dev.guga.dsh;

import static org.junit.Assert.*;
import android.content.*;
import android.view.View;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.*;
import org.json.*;
import org.junit.Test;

/** Real Android HTTP bridge + bundled Node 18 + desktop compaction, no paid calls. */
public class CompactionTests {
  @Test public void overflowCompactsAndContinuesOnAndroid() throws Exception {
    var i=InstrumentationRegistry.getInstrumentation();Context c=i.getTargetContext();
    dev.guga.updates.UpdateTestDriver.prefs(c).edit().putString("checkedDay",dev.guga.updates.UpdateTestDriver.today()).commit();
    MainActivity a=(MainActivity)i.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    Api.call("/state",new JSONObject());NativeBridge bridge=HarnessService.instance.bridge;OkHttpClient prior=bridge.api;
    AtomicInteger calls=new AtomicInteger(),summaries=new AtomicInteger();
    bridge.api=new OkHttpClient.Builder().addInterceptor(chain->{
      Response titleReply=TitleTestSupport.reply(chain);if(titleReply!=null)return titleReply;
      try {
        okio.Buffer data=new okio.Buffer();chain.request().body().writeTo(data);JSONObject request=new JSONObject(data.readUtf8());
        assertFalse(request.has("max_tokens"));
        JSONArray messages=request.getJSONArray("messages");String last=messages.getJSONObject(messages.length()-1).optString("content");
        int index=calls.incrementAndGet();int status=200;String body;
        if(index==2){status=400;body="{\"error\":{\"code\":\"context_length_exceeded\",\"message\":\"maximum context length exceeded\"}}";}
        else {
          boolean summary=last.startsWith("You are now acting as a compaction engine");
          if(summary)summaries.incrementAndGet();
          if(index==4)assertTrue(messages.toString().contains("<compacted-summary>"));
          body=StreamingTests.event("content",summary?"## Primary Request and Intent\n- Continue the user's document task.":"任务已完成")+"data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n";
        }
        return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status).message("Mock").body(ResponseBody.create(body,MediaType.get("text/event-stream"))).build();
      }catch(Exception e){throw new java.io.IOException(e);}
    }).build();
    try {
      new Settings(c).save("https://api.deepseek.com","deepseek-flash","local-test-key",false,32);
      Api.call("/select",new JSONObject());
      Api.call("/send",new JSONObject().put("text","原始文档要求。".repeat(2000)));StreamingTests.awaitState("任务已完成",false);
      Api.call("/send",new JSONObject().put("text","继续完成"));JSONObject done=StreamingTests.awaitState("继续完成",false);
      assertEquals("文档处理任务",done.optString("title"));assertEquals("",done.optString("lastError"));assertEquals(4,calls.get());assertEquals(1,summaries.get());assertEquals(2,done.getJSONArray("turns").length());
      i.runOnMainSync(()->{a.render(done);assertEquals("文档处理任务",a.chatTitle.getText().toString());ChatTurnView v=a.turnViews.values().stream().reduce((first,second)->second).get();assertEquals(View.GONE,v.details.getVisibility());v.header.performClick();assertTrue(v.steps.getText().toString().contains("整理上下文"));});
      StreamingTests.screenshot(c,"compaction-completed.png");
      // Changing tool availability replaces the system prompt: token-meter used Array.with here.
      new Settings(c).saveWeb(true,true);
      Api.call("/send",new JSONObject().put("text","启用联网工具后继续"));
      JSONObject refreshed=StreamingTests.awaitState("启用联网工具后继续",false);
      assertEquals("",refreshed.optString("lastError"));assertEquals(5,calls.get());
      new Settings(c).saveWeb(false,false);
      Api.call("/send",new JSONObject().put("text","关闭联网工具后再次继续"));
      JSONObject refreshedAgain=StreamingTests.awaitState("关闭联网工具后再次继续",false);
      assertEquals("",refreshedAgain.optString("lastError"));assertEquals(6,calls.get());
      assertTrue(MainActivity.readableError("{\"kind\":\"max-tokens\"}").contains("输出上限"));
    } finally {
      bridge.api=prior;c.getSharedPreferences("settings",0).edit().clear().commit();i.runOnMainSync(a::finish);
    }
  }
}
