package dev.guga.dsh;
import static org.junit.Assert.*;
import org.junit.Test;
import org.json.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import dev.guga.dsh.settings.ModelList;

public class WebSearchTests {
  @Test public void cloudFirstFallsBackOnlyOnFailure() throws Exception {
    int[] calls={0};JSONObject cloud=new JSONObject().put("results",new JSONArray());
    assertSame(cloud,WebSearchTools.cloudFirst(()->cloud,()->{calls[0]++;return new JSONObject();}));
    assertEquals(0,calls[0]);
    for(int n=0;n<3;n++) {
      JSONObject local=WebSearchTools.cloudFirst(()->{throw new java.net.SocketTimeoutException();},()->{calls[0]++;return new JSONObject().put("local",true);});
      assertTrue(local.getBoolean("local"));
    }
    assertEquals(3,calls[0]);
    try {WebSearchTools.cloudFirst(()->{throw new CancellationException();},()->{calls[0]++;return cloud;});fail();}catch(CancellationException expected){}
    assertEquals(3,calls[0]);
    try {WebSearchTools.cloudFirst(()->{throw new java.io.IOException();},()->{throw new java.io.IOException();});fail();}catch(java.io.IOException expected){assertTrue(expected.getMessage().contains("本地"));}
  }
  @Test public void officialCloudResultsErrorsAndCitations() throws Exception {
    assertEquals("https://relay.example/v1/messages",dev.guga.dsh.settings.ModelEndpoint.url("anthropic","https://relay.example",false));
    JSONObject request=WebSearchTools.claudeRequest("claude-opus-5-5","test");
    assertEquals("web_search_20250305",request.getJSONArray("tools").getJSONObject(0).getString("type"));
    JSONArray content=new JSONArray().put(new JSONObject().put("type","web_search_tool_result").put("content",new JSONArray().put(new JSONObject().put("type","web_search_result").put("url","https://example.com/a").put("title","Source").put("encrypted_content","opaque"))))
      .put(new JSONObject().put("type","text").put("text","Answer").put("citations",new JSONArray().put(new JSONObject().put("url","https://example.com/a").put("cited_text","Evidence"))));
    JSONObject response=new JSONObject().put("content",content);
    JSONObject result=WebSearchTools.cloudResults(response,10).getJSONArray("results").getJSONObject(0);
    assertEquals("Evidence\n",result.getString("snippet"));assertFalse(result.has("encrypted_content"));
    content.getJSONObject(0).put("content",new JSONObject().put("type","web_search_tool_result_error").put("error_code","unavailable"));
    try{WebSearchTools.cloudResults(response,10);fail();}catch(java.io.IOException expected){}
    content.getJSONObject(0).put("content",new JSONArray());
    assertEquals(0,WebSearchTools.cloudResults(response,10).getJSONArray("results").length());
    try{WebSearchTools.cloudResults(new JSONObject().put("content",new JSONArray()),10);fail();}catch(java.io.IOException expected){}
  }
  @Test public void cloudSearchUsesConfiguredRelayAndFallsBackOnHttpFailure() throws Exception {
    var base=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getTargetContext();
    String name="web-relay-test-"+UUID.randomUUID();
    android.content.Context c=new android.content.ContextWrapper(base){@Override public android.content.SharedPreferences getSharedPreferences(String ignored,int mode){return base.getSharedPreferences(name,mode);}};
    try {
      var settings=new dev.guga.dsh.settings.Settings(c);
      settings.save("anthropic","https://relay.example/gateway/v1","claude-opus-5-5","fake-test-key",false,32);
      String[] expectedPath={"https://relay.example/gateway/v1/messages"};
      int[] status={200}, requests={0}, fallbacks={0};
      var client=new okhttp3.OkHttpClient.Builder().addInterceptor(chain->{
        requests[0]++;
        assertEquals(expectedPath[0],chain.request().url().toString());
        assertEquals("fake-test-key",chain.request().header("x-api-key"));
        okio.Buffer bytes=new okio.Buffer();chain.request().body().writeTo(bytes);
        assertTrue(bytes.readUtf8().contains("web_search_20250305"));
        return new okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(status[0]).message("test")
          .body(okhttp3.ResponseBody.create("{\"stop_reason\":\"end_turn\",\"content\":[{\"type\":\"web_search_tool_result\",\"content\":[]}]}",okhttp3.MediaType.get("application/json"))).build();
      }).build();
      WebSearchTools.Attempt cloud=()->WebSearchTools.claude(client,settings,"anthropic","claude-opus-5-5","query",10,"test",()->{});
      WebSearchTools.Attempt local=()->{fallbacks[0]++;return new JSONObject().put("results",new JSONArray());};
      WebSearchTools.cloudFirst(cloud,local);assertEquals(0,fallbacks[0]);
      status[0]=400;WebSearchTools.cloudFirst(cloud,local);assertEquals(1,fallbacks[0]);
      status[0]=502;WebSearchTools.cloudFirst(cloud,local);assertEquals(2,fallbacks[0]);
      status[0]=200;WebSearchTools.cloudFirst(cloud,local);assertEquals(2,fallbacks[0]);assertEquals(4,requests[0]);
      Map<String,String> paths=new HashMap<>();for(String kind:dev.guga.dsh.settings.ProviderRegistry.ENDPOINTS)paths.put(kind,settings.endpointPath("anthropic",kind));
      paths.put("search","/custom/search");settings.saveEndpoints("anthropic",paths);expectedPath[0]="https://relay.example/gateway/v1/custom/search";
      WebSearchTools.cloudFirst(cloud,local);assertEquals(5,requests[0]);assertEquals(2,fallbacks[0]);
    }finally{base.deleteSharedPreferences(name);}
  }
  @Test public void pausedCloudSearchReplaysEncryptedBlocksAndKeepsEarlierSources() throws Exception {
    var base=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getTargetContext();String name="web-pause-"+UUID.randomUUID();
    android.content.Context c=new android.content.ContextWrapper(base){@Override public android.content.SharedPreferences getSharedPreferences(String ignored,int mode){return base.getSharedPreferences(name,mode);}};
    try {
      var settings=new dev.guga.dsh.settings.Settings(c);settings.save("anthropic","https://relay.example","claude-opus-5-5","fake",false,32);
      int[] calls={0};var client=new okhttp3.OkHttpClient.Builder().addInterceptor(chain->{
        String payload;
        if(calls[0]++==0)payload="{\"stop_reason\":\"pause_turn\",\"content\":[{\"type\":\"web_search_tool_result\",\"content\":[{\"type\":\"web_search_result\",\"url\":\"https://example.com\",\"title\":\"Source\",\"encrypted_content\":\"opaque-original\"}]}]}";
        else {
          okio.Buffer buffer=new okio.Buffer();chain.request().body().writeTo(buffer);assertTrue(buffer.readUtf8().contains("opaque-original"));
          payload="{\"stop_reason\":\"end_turn\",\"content\":[{\"type\":\"text\",\"text\":\"done\"}]}";
        }
        return new okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("test").body(okhttp3.ResponseBody.create(payload,okhttp3.MediaType.get("application/json"))).build();
      }).build();
      assertEquals(1,WebSearchTools.claude(client,settings,"anthropic","claude-opus-5-5","query",10,"test",()->{}).getJSONArray("results").length());assertEquals(2,calls[0]);
    }finally{base.deleteSharedPreferences(name);}
  }
  @Test public void localSearchParsesLinksAndRejectsChallenge() throws Exception {
    String html="<div class='result'><a class='result__a' href='//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Fa'>Title</a><div class='result__snippet'>Snippet</div></div>";
    JSONObject result=WebSearchTools.parseLocalSearch(html,10).getJSONArray("results").getJSONObject(0);
    assertEquals("https://example.com/a",result.getString("url"));assertEquals("Snippet",result.getString("snippet"));
    try{WebSearchTools.parseLocalSearch("<p>captcha</p>",10);fail();}catch(java.io.IOException expected){}
    assertFalse(WebSearchTools.publicUrl("javascript:alert(1)"));assertFalse(WebSearchTools.publicUrl("https://key@example.com"));
  }
  @Test public void claudeVersionsSortNumericallyNewestFirst() {
    List<String> ids=new ArrayList<>(Arrays.asList("custom", "claude-3-7-sonnet-20250219", "claude-opus-5-5", "claude-sonnet-4-6", "claude-opus-10-2", "claude-3-5-sonnet-20241022"));
    ModelList.newestClaudeFirst(ids,"");
    assertEquals(Arrays.asList("claude-opus-10-2","claude-opus-5-5","claude-sonnet-4-6","claude-3-7-sonnet-20250219","claude-3-5-sonnet-20241022","custom"),ids);
    ModelList.newestClaudeFirst(ids,"");assertEquals("claude-opus-10-2",ids.get(0));
  }
}
