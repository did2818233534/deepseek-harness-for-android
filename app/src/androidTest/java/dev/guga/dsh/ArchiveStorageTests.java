package dev.guga.dsh;
import static org.junit.Assert.*;
import org.junit.Test;
import org.json.*;
import okhttp3.*;
import java.io.*;
import androidx.test.platform.app.InstrumentationRegistry;
public class ArchiveStorageTests {
 @Test public void archiveRestoreRunsInNativeRuntimeAndPreservesFiles()throws Exception{
  var i=InstrumentationRegistry.getInstrumentation();var c=i.getTargetContext();c.getSharedPreferences("settings",0).edit().clear().commit();dev.guga.updates.UpdateTestDriver.prefs(c).edit().putString("checkedDay",dev.guga.updates.UpdateTestDriver.today()).commit();
  MainActivity a=(MainActivity)i.startActivitySync(new android.content.Intent(c,MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));Api.call("/state",new JSONObject());NativeBridge bridge=HarnessService.instance.bridge;OkHttpClient prior=bridge.api;
  bridge.api=new OkHttpClient.Builder().addInterceptor(chain->{Response title=TitleTestSupport.reply(chain);if(title!=null)return title;try{return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(ResponseBody.create(StreamingTests.event("content","已读取资料")+"data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n",MediaType.get("text/event-stream"))).build();}catch(Exception e){throw new IOException(e);}}).build();
  FileTools files=new FileTools(c);String filename="archive-test-"+System.nanoTime()+".txt",id="";File original=files.resolve(filename);String text="无损压缩验证\n".repeat(10000);java.nio.file.Files.write(original.toPath(),text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  try{
   new Settings(c).save("https://api.deepseek.com","test-model","test-only-key",false,32);
   Api.call("/select",new JSONObject());id=Api.call("/state",new JSONObject()).getString("id");Api.call("/send",new JSONObject().put("text","归档测试").put("attachments",new JSONArray().put(Attachments.describe(files,filename,"text/plain"))));ImageModelSwitchTests.idle();
   Api.call("/session/archive",new JSONObject().put("id",id).put("archived",true));waitArchive(id,true);assertFalse(original.exists());
   i.runOnMainSync(()->{a.archivedPage();});Thread.sleep(400);StreamingTests.screenshot(c,"archived-storage.png");i.runOnMainSync(a::onBackPressed);
   Api.call("/session/archive",new JSONObject().put("id",id).put("archived",false));waitArchive(id,false);assertEquals(text,new String(java.nio.file.Files.readAllBytes(original.toPath()),java.nio.charset.StandardCharsets.UTF_8));
   Api.call("/select",new JSONObject().put("id",id));assertTrue(Api.call("/state",new JSONObject()).getJSONArray("turns").length()>0);
   Api.call("/session/archive",new JSONObject().put("id",id).put("archived",true));waitArchive(id,true);new Settings(c).saveCleanupConversationFiles(false);
   Api.call("/session/delete",new JSONObject().put("id",id));assertTrue(original.exists());
  }finally{bridge.api=prior;original.delete();c.getSharedPreferences("settings",0).edit().clear().commit();i.runOnMainSync(a::finish);}
 }
 void waitArchive(String id,boolean archived)throws Exception{for(int n=0;n<300;n++){JSONArray rows=Api.call("/sessions",new JSONObject().put("archived",archived)).getJSONArray("sessions");for(int j=0;j<rows.length();j++){JSONObject row=rows.getJSONObject(j);if(!id.equals(row.optString("id")))continue;String state=row.optJSONObject("archiveStorage").optString("state");assertNotEquals(row.toString(),"error",state);if(archived?state.equals("packed"):state.isEmpty())return;}Thread.sleep(50);}fail("Archive task timed out");}
}
