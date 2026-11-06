package dev.guga.dsh;
import static org.junit.Assert.*;
import android.content.*;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.Test;
import java.util.concurrent.*;
import okhttp3.*;
import java.io.*;

public class AuditProbeTests {
 MainActivity open() throws Exception {
  var i=InstrumentationRegistry.getInstrumentation();var c=i.getTargetContext();
  dev.guga.updates.UpdateTestDriver.prefs(c).edit().putString("checkedDay",dev.guga.updates.UpdateTestDriver.today()).commit();
  MainActivity a=(MainActivity)i.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  Api.call("/state",new JSONObject());
  i.runOnMainSync(()->{a.visible=false;a.handler.removeCallbacks(a.poll);});return a;
 }
 @Test public void fileDeleteColorRemainsOriginalInBothThemes()throws Exception {
  var i=InstrumentationRegistry.getInstrumentation();var c=i.getTargetContext();var prefs=c.getSharedPreferences("appearance",0);String prior=prefs.getString("theme","system");
  try{for(String mode:new String[]{"light","dark"}){
   prefs.edit().putString("theme",mode).commit();MainActivity a=open();
   try{i.runOnMainSync(()->{a.files();a.fileLibrary.setSelecting(true);assertEquals(0xFFB3261E,a.fileLibrary.remove.getCurrentTextColor());assertFalse(a.fileLibrary.remove.isEnabled());a.fileLibrary.selected.add("color-test.txt");a.fileLibrary.render();assertTrue(a.fileLibrary.remove.isEnabled());assertEquals(0xFFB3261E,a.fileLibrary.remove.getCurrentTextColor());});}
   finally{i.runOnMainSync(a::finish);}
  }}finally{prefs.edit().putString("theme",prior).commit();i.runOnMainSync(()->UiStyle.configure(c));}
 }
 @Test public void fileSelectionSurvivesStreamingRefresh() throws Exception {
  var i=InstrumentationRegistry.getInstrumentation();MainActivity a=open();
  JSONObject state=new JSONObject().put("id","audit-stream").put("revision",123).put("busy",true).put("turns",new JSONArray());
  try{i.runOnMainSync(()->{a.render(state);a.files();a.fileLibrary.setSelecting(true);a.fileLibrary.selected.add("audit.txt");a.render(state);assertTrue("Streaming refresh discarded file multi-selection",a.fileLibrary.selecting);});}
  finally{i.runOnMainSync(a::finish);}
 }
 @Test public void typingDuringConversationSwitchIsPreserved() throws Exception {
  var i=InstrumentationRegistry.getInstrumentation();MainActivity a=open();CountDownLatch gate=new CountDownLatch(1),done=new CountDownLatch(1);
  try{
   a.coreIo.execute(()->{try{gate.await(5,TimeUnit.SECONDS);}catch(Exception e){}});
   i.runOnMainSync(()->{a.newChat();a.input.setText("new draft typed after switching");try{a.attachments.add(new JSONObject().put("path","new-draft.txt").put("name","new-draft.txt"));}catch(Exception e){throw new RuntimeException(e);}});
   a.coreIo.execute(()->a.runOnUiThread(done::countDown));gate.countDown();assertTrue(done.await(10,TimeUnit.SECONDS));
   i.runOnMainSync(()->{assertEquals("new draft typed after switching",a.input.getText().toString());assertEquals(1,a.attachments.pending.size());});
  }finally{gate.countDown();i.runOnMainSync(a::finish);}
 }
 @Test public void notificationCancelDoesNotHideRunningBackgroundTask() throws Exception {
  var i=InstrumentationRegistry.getInstrumentation();var c=i.getTargetContext();MainActivity a=open();
  NativeBridge bridge=HarnessService.instance.bridge;OkHttpClient prior=bridge.api;
  PipedInputStream in=new PipedInputStream(8192);PipedOutputStream out=new PipedOutputStream(in);
  bridge.api=new OkHttpClient.Builder().addInterceptor(chain->{Response title=TitleTestSupport.reply(chain);if(title!=null)return title;return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(new ResponseBody(){final okio.BufferedSource source=okio.Okio.buffer(okio.Okio.source(in));public MediaType contentType(){return MediaType.get("text/event-stream");}public long contentLength(){return -1;}public okio.BufferedSource source(){return source;}}).build();}).build();
  String running="";
  try{
   new dev.guga.dsh.settings.Settings(c).save("https://api.deepseek.com","test-model","audit-fake-key",true,32);
   Api.call("/select",new JSONObject());running=Api.call("/state",new JSONObject()).getString("id");
   out.write(StreamingTests.event("content","audit held response").getBytes(java.nio.charset.StandardCharsets.UTF_8));out.flush();
   Api.call("/send",new JSONObject().put("text","audit task"));
   long end=System.currentTimeMillis()+5000;while(!HarnessService.instance.taskActive&&System.currentTimeMillis()<end)Thread.sleep(20);
   Api.call("/select",new JSONObject());assertEquals(1,Api.call("/state",new JSONObject()).getInt("runningCount"));
   i.runOnMainSync(()->c.startService(new Intent(c,HarnessService.class).setAction("cancel")));
   long stopDeadline=System.currentTimeMillis()+5000;
   while(Api.call("/state",new JSONObject()).getInt("runningCount")>0&&System.currentTimeMillis()<stopDeadline){assertTrue("Running task lost foreground protection",HarnessService.instance.taskActive);Thread.sleep(20);}
   assertEquals("Notification must cancel the background conversation",0,Api.call("/state",new JSONObject()).getInt("runningCount"));
  }finally{
   if(!running.isEmpty()){Api.call("/select",new JSONObject().put("id",running));Api.call("/cancel",new JSONObject());}
   out.close();in.close();MainActivity.closeModelClient(bridge.api);bridge.api=prior;c.getSharedPreferences("settings",0).edit().clear().commit();i.runOnMainSync(a::finish);
  }
 }
}
