package dev.guga.updates;

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
    JSONObject asset=new JSONObject().put("url",UpdateConfig.read(context()).repository+"/releases/download/v9/test.apk")
      .put("sha256",sha).put("size",size);
    return new JSONObject().put("schema",1).put("versionCode",version).put("versionName","test")
      .put("assets",new JSONObject().put("arm64-v8a",asset).put("x86_64",asset)).toString();
  }
  @Test public void reusableRepositoryAndUniversalApk() throws Exception {
    UpdateConfig config=new UpdateConfig("https://github.com/example/other-app/",null,"Other App",1);
    assertEquals("https://github.com/example/other-app/releases/latest/download/latest.json",config.manifestUrl);
    JSONObject asset=new JSONObject().put("url",config.repository+"/releases/download/v2/app.apk")
      .put("sha256","a".repeat(64)).put("size",100);
    String json=new JSONObject().put("schema",1).put("versionCode",2).put("versionName","2.0")
      .put("assets",new JSONObject().put("universal",asset)).toString();
    assertEquals(2,AppUpdates.parse(json,1,new String[]{"arm64-v8a"},config.repository).version);
    try { AppUpdates.parse(json,1,new String[]{"arm64-v8a"},"https://github.com/example/wrong-app"); fail(); }
    catch(java.io.IOException expected) {}
    for(String invalid : new String[]{"http://github.com/example/app","https://user@github.com/example/app","https://github.com/example/app?token=secret"}) {
      try { new UpdateConfig(invalid,null,"Other",1); fail(); } catch(IllegalArgumentException expected) {}
    }
  }
  @Test public void joinedManualCallbackWaitsAndUsesMainThread() throws Exception {
    AppUpdates.running.set(true);
    java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.atomic.AtomicBoolean main=new java.util.concurrent.atomic.AtomicBoolean();
    try {
      ReleaseUpdater.checkManually(context(),() -> {
        main.set(android.os.Looper.myLooper()==android.os.Looper.getMainLooper()); done.countDown();
      });
      assertFalse(done.await(300,java.util.concurrent.TimeUnit.MILLISECONDS));
      AppUpdates.running.set(false);
      assertTrue(done.await(2,java.util.concurrent.TimeUnit.SECONDS)); assertTrue(main.get());
    } finally { AppUpdates.running.set(false); }
  }
  @Test public void installedDownloadIsCleanedWithoutNetwork() throws Exception {
    Context c=context();
    AppUpdates.apk(c).getParentFile().mkdirs();
    try(var output=new java.io.FileOutputStream(AppUpdates.apk(c))) { output.write(1); }
    long version=c.getPackageManager().getPackageInfo(c.getPackageName(),0).getLongVersionCode();
    AppUpdates.prefs(c).edit().putLong("ready",version).putString("checkedDay",AppUpdates.today()).commit();
    AppUpdates.cleanupInstalled(c);
    assertFalse(AppUpdates.apk(c).exists()); assertEquals(0,AppUpdates.prefs(c).getLong("ready",0));
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
    assertEquals(19,AppUpdates.parse(json,18,new String[]{"x86_64"},UpdateConfig.read(context()).repository).version);
    assertNull(AppUpdates.parse(json,19,new String[]{"x86_64"},UpdateConfig.read(context()).repository));
    assertNull(AppUpdates.parse(json,20,new String[]{"x86_64"},UpdateConfig.read(context()).repository));
    JSONObject foreign=new JSONObject(json);
    foreign.getJSONObject("assets").getJSONObject("x86_64").put("url","https://evil.example/update.apk");
    for(String invalid : new String[]{foreign.toString(),
        json.replace("a".repeat(64),"bad"),json.replace("\"size\":100","\"size\":0"),
        json.replace("\"schema\":1","\"schema\":2"),json.replace("\"schema\":1","\"schema\":1,\"prerelease\":true")}) {
      try { AppUpdates.parse(invalid,18,new String[]{"x86_64"},UpdateConfig.read(context()).repository); fail("accepted invalid release"); }
      catch(java.io.IOException expected) {}
    }
    try { AppUpdates.parse(json,18,new String[]{"unsupported"},UpdateConfig.read(context()).repository); fail(); }
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
      byte[] body=chain.request().url().toString().equals(UpdateConfig.read(context()).manifestUrl)?manifest.getBytes(StandardCharsets.UTF_8):invalid;
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
