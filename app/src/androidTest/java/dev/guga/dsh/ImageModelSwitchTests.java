package dev.guga.dsh;
import static org.junit.Assert.*;
import org.junit.Test;
import org.json.*;
import okhttp3.*;
import java.util.*;

public class ImageModelSwitchTests {
 @Test public void togglePersistsAndImageUsesTemporaryModel()throws Exception{
  var i=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation();var c=i.getTargetContext();
  c.getSharedPreferences("settings",0).edit().clear().commit();
  dev.guga.updates.UpdateTestDriver.prefs(c).edit().putString("checkedDay",dev.guga.updates.UpdateTestDriver.today()).commit();
  MainActivity a=(MainActivity)i.startActivitySync(new android.content.Intent(c,MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
  Api.call("/state",new JSONObject());NativeBridge bridge=HarnessService.instance.bridge;OkHttpClient prior=bridge.api;
  List<String> models=Collections.synchronizedList(new ArrayList<>());
  bridge.api=new OkHttpClient.Builder().addInterceptor(chain->{Response title=TitleTestSupport.reply(chain);if(title!=null)return title;
   try{okio.Buffer b=new okio.Buffer();chain.request().body().writeTo(b);models.add(new JSONObject(b.readUtf8()).getString("model"));
    return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(ResponseBody.create(StreamingTests.event("content","已查看图片")+"data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n",MediaType.get("text/event-stream"))).build();
   }catch(Exception e){throw new java.io.IOException(e);}}).build();
  try{
   i.runOnMainSync(()->{try{a.visible=false;a.busy=false;ModelSettingsPanel p=new ModelSettingsPanel(a,false);p.open();assertTrue(p.autoImageModel.isChecked());p.autoImageModel.setChecked(false);assertFalse(new Settings(c).autoImageModel());p.autoImageModel.setChecked(true);assertTrue(new Settings(c).autoImageModel());a.onBackPressed();ModelSettingsPanel reopened=new ModelSettingsPanel(a,false);reopened.open();assertTrue(reopened.autoImageModel.isChecked());a.onBackPressed();}catch(Exception e){throw new RuntimeException(e);}});
   Settings settings=new Settings(c);settings.save(settings.base(),"deepseek-v4-pro","test-only-key",false,32);settings.saveReasoningEffort("none");
   java.io.File picture=new FileTools(c).resolve("auto-vision.png");
   android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(2,2,android.graphics.Bitmap.Config.ARGB_8888);
   try(var out=new java.io.FileOutputStream(picture)){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
   Api.call("/select",new JSONObject());
   JSONObject request=new JSONObject().put("text","看图").put("attachments",new JSONArray().put(Attachments.describe(new FileTools(c),"auto-vision.png","image/png")));
   Api.call("/send",request);idle();assertEquals("deepseek-flash",models.get(models.size()-1));assertEquals("deepseek-v4-pro",settings.model());assertEquals("deepseek-v4-pro",Api.call("/state",new JSONObject()).getString("model"));
   Api.call("/send",new JSONObject().put("text","继续"));idle();assertEquals("deepseek-v4-pro",models.get(models.size()-1));
   settings.saveAutoImageModel(false);int before=models.size();try{Api.call("/send",request);fail("off must reject unsupported image");}catch(java.io.IOException e){assertTrue(e.getMessage().contains("图片自动切换模型"));}assertEquals(before,models.size());
   i.runOnMainSync(()->assertTrue("Model switches must not create modal dialogs",a.dialogs.isEmpty()));
  }finally{bridge.api=prior;c.getSharedPreferences("settings",0).edit().clear().commit();i.runOnMainSync(a::finish);}
 }
 static void idle()throws Exception{long until=System.currentTimeMillis()+8000;do{if(!Api.call("/state",new JSONObject()).optBoolean("busy"))return;Thread.sleep(30);}while(System.currentTimeMillis()<until);throw new AssertionError("Task did not finish");}
}
