package dev.guga.dsh;
import okhttp3.*;
import org.json.*;
final class TitleTestSupport {
  static Response reply(Interceptor.Chain chain) throws java.io.IOException {
    try {
      okio.Buffer buffer=new okio.Buffer();chain.request().body().writeTo(buffer);
      JSONObject body=new JSONObject(buffer.readUtf8());JSONArray messages=body.optJSONArray("messages");
      if(messages==null||!messages.getJSONObject(0).optString("content").startsWith("为对话生成简短标题"))return null;
      if(!body.getJSONObject("thinking").optString("type").equals("disabled"))throw new AssertionError("Titles should not reason");
      String stream=StreamingTests.event("content","文档处理任务")+"data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n";
      return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(ResponseBody.create(stream,MediaType.get("text/event-stream"))).build();
    }catch(Exception e){throw new java.io.IOException(e);}
  }
}
