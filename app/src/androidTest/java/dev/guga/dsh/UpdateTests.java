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
      android.widget.Button button=findButton(activity.currentPage);
      int height=button.getHeight(),top=button.getTop();
      i.runOnMainSync(() -> activity.updateButtonStatus(button,"下载好最新版本，点击安装"));
      Thread.sleep(300);
      i.runOnMainSync(() -> {
        assertEquals(height,button.getHeight()); assertEquals(top,button.getTop());
        assertEquals(2,button.getMaxLines());
        activity.updateButtonStatus(button,"这里是一段无法在两行内完整显示的更新错误详情。".repeat(12));
      });
      Thread.sleep(300);
      i.runOnMainSync(() -> {
        assertEquals(height,button.getHeight()); assertEquals(top,button.getTop());
        assertTrue(DeviceTests.containsText(activity.dialogs.peek().getWindow().getDecorView(),"更新错误详情"));
        activity.onBackPressed(); activity.onBackPressed(); activity.updateSettings();
        assertEquals("检查更新",findButton(activity.currentPage).getText().toString());
      });
    } finally { AppUpdates.running.set(false); i.runOnMainSync(activity::finish); }
    assertTrue(AppUpdates.failure(new java.net.SocketTimeoutException()).contains("超时"));
    assertTrue(AppUpdates.failure(new javax.net.ssl.SSLException("private diagnostic")).contains("安全连接"));
  }
  android.widget.Button findButton(android.view.View v) {
    if(v instanceof android.widget.Button && !(v instanceof android.widget.CompoundButton)) return (android.widget.Button)v;
    if(v instanceof android.view.ViewGroup) { var g=(android.view.ViewGroup)v; for(int n=0;n<g.getChildCount();n++) { var b=findButton(g.getChildAt(n)); if(b!=null)return b; } }
    return null;
  }
  void clickCheck(android.view.View v) {
    if(v instanceof android.widget.Button && "检查更新".contentEquals(((android.widget.Button)v).getText())) { v.performClick(); return; }
    if(v instanceof android.view.ViewGroup) { var group=(android.view.ViewGroup)v; for(int n=0;n<group.getChildCount();n++) clickCheck(group.getChildAt(n)); }
  }
  @Test public void manualInstallFailureAndConfirmationStayForeground() throws Exception {
    var i=InstrumentationRegistry.getInstrumentation(); Context c=context();
    try(var command=i.getUiAutomation().executeShellCommand("appops set dev.guga.dsh REQUEST_INSTALL_PACKAGES allow")) {
      try(var input=new android.os.ParcelFileDescriptor.AutoCloseInputStream(command)) { while(input.read()!=-1) {} }
    }
    AppUpdates.prefs(c).edit().remove("ready").remove("session").commit(); AppUpdates.apk(c).delete();
    UpdateInstallActivity activity=(UpdateInstallActivity)i.startActivitySync(new android.content.Intent(c,UpdateInstallActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
    try {
      Thread.sleep(500); i.waitForIdleSync();
      i.runOnMainSync(() -> {
        assertNotNull(activity.errorDialog); assertTrue(activity.errorDialog.isShowing());
        assertTrue(DeviceTests.containsText(activity.errorDialog.getWindow().getDecorView(),"安装包无效"));
        activity.errorDialog.dismiss();
      });
      var monitor=new android.app.Instrumentation.ActivityMonitor(MainActivity.class.getName(),new android.app.Instrumentation.ActivityResult(0,null),true);
      i.addMonitor(monitor);
      try {
        i.runOnMainSync(() -> new UpdateResultReceiver().onReceive(c,new android.content.Intent()
          .putExtra(android.content.pm.PackageInstaller.EXTRA_STATUS,android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION)
          .putExtra(android.content.Intent.EXTRA_INTENT,new android.content.Intent(c,MainActivity.class))));
        assertEquals("System confirmation opens directly",1,monitor.getHits());
        i.runOnMainSync(() -> new UpdateResultReceiver().onReceive(c,new android.content.Intent()
          .putExtra(android.content.pm.PackageInstaller.EXTRA_STATUS,android.content.pm.PackageInstaller.STATUS_FAILURE_BLOCKED)));
        i.runOnMainSync(() -> assertTrue(DeviceTests.containsText(activity.errorDialog.getWindow().getDecorView(),"系统阻止")));
      } finally { i.removeMonitor(monitor); }
    } finally { i.runOnMainSync(activity::finish); }
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
