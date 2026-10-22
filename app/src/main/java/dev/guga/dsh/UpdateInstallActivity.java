package dev.guga.dsh;
import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
public final class UpdateInstallActivity extends Activity {
  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    Intent confirmation=getIntent().getParcelableExtra("confirmation");
    if (confirmation != null) {
      try { startActivity(confirmation); } catch (Exception ignored) {}
      finish(); return;
    }
    if (!getPackageManager().canRequestPackageInstalls()) {
      startActivityForResult(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:"+getPackageName())),1);
    } else install();
  }
  void install() {
    new Thread(() -> {
      try { AppUpdates.install(this); } catch (Exception ignored) {}
      runOnUiThread(this::finish);
    },"DSH-install").start();
  }
  protected void onActivityResult(int request,int result,Intent data) {
    super.onActivityResult(request,result,data);
    if (getPackageManager().canRequestPackageInstalls()) install(); else finish();
  }
}
