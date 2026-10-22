package dev.guga.dsh;
import android.content.*;
import android.content.pm.PackageInstaller;
public final class UpdateResultReceiver extends BroadcastReceiver {
  public void onReceive(Context c, Intent intent) {
    int status=intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
    if (status==PackageInstaller.STATUS_PENDING_USER_ACTION) {
      Intent confirmation=intent.getParcelableExtra(Intent.EXTRA_INTENT);
      AppUpdates.notifyReady(c, AppUpdates.prefs(c).getString("name",""),confirmation);
    } else {
      AppUpdates.prefs(c).edit().remove("session").apply();
      if (status==PackageInstaller.STATUS_SUCCESS) {
        AppUpdates.apk(c).delete();
        AppUpdates.prefs(c).edit().remove("ready").apply();
        c.getSystemService(android.app.NotificationManager.class).cancel(AppUpdates.NOTICE);
      }
    }
  }
}
