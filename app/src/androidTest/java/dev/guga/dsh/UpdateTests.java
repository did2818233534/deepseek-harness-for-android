package dev.guga.dsh;

import static org.junit.Assert.*;
import android.content.Context;
import android.content.pm.PackageManager;
import androidx.test.platform.app.InstrumentationRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.*;
import org.json.*;
import org.junit.Test;

public class UpdateTests {
  Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }
  String manifest(long version, String sha, long size) throws Exception {
    JSONObject asset=new JSONObject().put("url",AppUpdates.REPO+"/releases/download/v9/test.apk")
      .put("sha256",sha).put("size",size);
    return new JSONObject().put("schema",1).put("versionCode",version).put("versionName","test")
      .put("assets",new JSONObject().put("arm64-v8a",asset).put("x86_64",asset)).toString();
  }
  @Test public void manualPageShowsExistingCheckAndFailure() throws Exception {
    Context c=context();
    AppUpdates.prefs(c).edit().putString("checkedDay",AppUpdates.today()).commit();
    var i=InstrumentationRegistry.getInstrumentation();
    MainActivity activity=(MainActivity)i.startActivitySync(new android.content.Intent(c,MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
    try {
      AppUpdates.running.set(true); AppUpdates.status="正在下载 0.5.2：42%";
      i.runOnMainSync(() -> { activity.settings(); activity.updateSettings(); clickCheck(activity.currentPage); });
      Thread.sleep(700);
      i.runOnMainSync(() -> assertTrue(DeviceTests.containsText(activity.currentPage,"42%")));
      AppUpdates.status=AppUpdates.failure(new java.net.UnknownHostException()); AppUpdates.running.set(false);
      Thread.sleep(700);
      i.runOnMainSync(() -> assertTrue(DeviceTests.containsText(activity.currentPage,"无法解析 GitHub")));
    } finally { AppUpdates.running.set(false); i.runOnMainSync(activity::finish); }
    assertTrue(AppUpdates.failure(new java.net.SocketTimeoutException()).contains("超时"));
    assertTrue(AppUpdates.failure(new javax.net.ssl.SSLException("private diagnostic")).contains("安全连接"));
  }
  void clickCheck(android.view.View v) {
    if(v instanceof android.widget.Button && "检查更新".contentEquals(((android.widget.Button)v).getText())) { v.performClick(); return; }
    if(v instanceof android.view.ViewGroup) { var group=(android.view.ViewGroup)v; for(int n=0;n<group.getChildCount();n++) clickCheck(group.getChildAt(n)); }
  }
  @Test public void dailyLimitAndManualOverride() {
    assertTrue(AppUpdates.due("", "2026-10-22",false,true));
    assertFalse(AppUpdates.due("2026-10-22", "2026-10-22",false,true));
    assertTrue(AppUpdates.due("2026-10-22", "2026-10-23",false,true));
    assertFalse(AppUpdates.due("", "2026-10-22",false,false));
    assertTrue(AppUpdates.due("2026-10-22", "2026-10-22",true,false));
  }
  @Test public void validateReleaseManifest() throws Exception {
    String json=manifest(19,"a".repeat(64),100);
    assertEquals(19,AppUpdates.parse(json,18,new String[]{"x86_64"}).version);
    assertNull(AppUpdates.parse(json,19,new String[]{"x86_64"}));
    assertNull(AppUpdates.parse(json,20,new String[]{"x86_64"}));
    JSONObject foreign=new JSONObject(json);
    foreign.getJSONObject("assets").getJSONObject("x86_64").put("url","https://evil.example/update.apk");
    for(String invalid : new String[]{foreign.toString(),
        json.replace("a".repeat(64),"bad"),json.replace("\"size\":100","\"size\":0"),
        json.replace("\"schema\":1","\"schema\":2"),json.replace("\"schema\":1","\"schema\":1,\"prerelease\":true")}) {
      try { AppUpdates.parse(invalid,18,new String[]{"x86_64"}); fail("accepted invalid release"); }
      catch(java.io.IOException expected) {}
    }
    try { AppUpdates.parse(json,18,new String[]{"unsupported"}); fail(); }
    catch(java.io.IOException expected) {}
  }
  @Test public void downloadRejectsUninstallableApkAndOfflineIsQuiet() throws Exception {
    Context c=context();
    var installed=c.getPackageManager().getPackageInfo(c.getPackageName(),PackageManager.GET_SIGNING_CERTIFICATES);
    AppUpdates.prefs(c).edit().clear().putString("checkedDay",AppUpdates.today()).commit();
    AppUpdates.apk(c).delete();
    byte[] invalid="not an APK".getBytes(StandardCharsets.UTF_8);
    String manifest=manifest(installed.getLongVersionCode()+1,AppUpdates.hex(MessageDigest.getInstance("SHA-256").digest(invalid)),invalid.length);
    AtomicInteger requests=new AtomicInteger();
    OkHttpClient client=new OkHttpClient.Builder().addInterceptor(chain -> {
      requests.incrementAndGet();
      byte[] body=chain.request().url().toString().equals(AppUpdates.MANIFEST)?manifest.getBytes(StandardCharsets.UTF_8):invalid;
      return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
        .body(ResponseBody.create(body,MediaType.get("application/octet-stream"))).build();
    }).build();
    try {
      try { AppUpdates.check(c,true,client,installed); fail("Invalid APK accepted"); }
      catch (AppUpdates.UpdateFailure expected) { assertTrue(AppUpdates.failure(expected).contains("校验失败")); }
      assertEquals(2,requests.get()); assertFalse(AppUpdates.apk(c).exists());
      assertEquals(0,AppUpdates.prefs(c).getLong("success",0));
      assertEquals(0,AppUpdates.prefs(c).getLong("ready",0));
      assertFalse(new java.io.File(AppUpdates.apk(c).getParentFile(),"download.part").exists());
      assertFalse(AppUpdates.valid(c,new java.io.File(c.getApplicationInfo().sourceDir),installed.getLongVersionCode()));
    } finally { client.connectionPool().evictAll(); client.dispatcher().executorService().shutdown(); }
    OkHttpClient offline=new OkHttpClient.Builder().addInterceptor(chain -> new Response.Builder()
      .request(chain.request()).protocol(Protocol.HTTP_1_1).code(503).message("Unavailable")
      .body(ResponseBody.create("",MediaType.get("text/plain"))).build()).build();
    try {
      try { AppUpdates.check(c,true,offline,installed); fail("HTTP failure hidden"); }
      catch (AppUpdates.UpdateFailure expected) { assertTrue(AppUpdates.failure(expected).contains("HTTP 503")); }
      assertEquals(0,AppUpdates.prefs(c).getLong("success",0));
    }
    finally { offline.connectionPool().evictAll(); offline.dispatcher().executorService().shutdown(); }
  }
}
