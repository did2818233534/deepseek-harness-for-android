package dev.guga.dsh;
import static org.junit.Assert.*;
import org.junit.Test;
import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import dev.guga.updates.*;
public class UpdateUiTests {
  Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }
  @Test public void manualPageShowsExistingCheckAndFailure() throws Exception {
    Context c=context();
    UpdateTestDriver.prefs(c).edit().putString("checkedDay",UpdateTestDriver.today()).commit();
    var i=InstrumentationRegistry.getInstrumentation();
    MainActivity activity=(MainActivity)i.startActivitySync(new android.content.Intent(c,MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
    try {
      UpdateTestDriver.running().set(true); UpdateTestDriver.status("正在下载 0.5.2：42%");
      i.runOnMainSync(() -> { activity.settings(); activity.updateSettings(); clickCheck(activity.currentPage); });
      Thread.sleep(700);
      i.runOnMainSync(() -> assertTrue(DeviceTests.containsText(activity.currentPage,"42%")));
      UpdateTestDriver.status(UpdateTestDriver.failure(new java.net.UnknownHostException())); UpdateTestDriver.running().set(false);
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
    } finally { UpdateTestDriver.running().set(false); i.runOnMainSync(activity::finish); }
    assertTrue(UpdateTestDriver.failure(new java.net.SocketTimeoutException()).contains("超时"));
    assertTrue(UpdateTestDriver.failure(new javax.net.ssl.SSLException("private diagnostic")).contains("安全连接"));
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
    UpdateTestDriver.prefs(c).edit().remove("ready").remove("session").commit(); UpdateTestDriver.apk(c).delete();
    UpdateInstallActivity activity=(UpdateInstallActivity)i.startActivitySync(new android.content.Intent(c,UpdateInstallActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
    try {
      // Installation validation runs on an executor; wait for its observable UI result.
      for(int n=0;n<50;n++) {
        boolean[] ready={false};
        i.runOnMainSync(()->ready[0]=UpdateTestDriver.errorDialog(activity)!=null);
        if(ready[0])break;
        Thread.sleep(100);
      }
      i.waitForIdleSync();
      i.runOnMainSync(() -> {
        assertNotNull(UpdateTestDriver.errorDialog(activity)); assertTrue(UpdateTestDriver.errorDialog(activity).isShowing());
        assertTrue(DeviceTests.containsText(UpdateTestDriver.errorDialog(activity).getWindow().getDecorView(),"安装包无效"));
        UpdateTestDriver.errorDialog(activity).dismiss();
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
        i.runOnMainSync(() -> assertTrue(DeviceTests.containsText(UpdateTestDriver.errorDialog(activity).getWindow().getDecorView(),"系统阻止")));
      } finally { i.removeMonitor(monitor); }
    } finally { i.runOnMainSync(activity::finish); }
  }
}
