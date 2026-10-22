package dev.guga.updates;
/** Test-only access; never packaged in the library or production APK. */
public final class UpdateTestDriver {
 public static java.util.concurrent.atomic.AtomicBoolean running() { return AppUpdates.running; }
 public static void status(String value) { AppUpdates.status=value; }
 public static String failure(Exception e) { return AppUpdates.failure(e); }
 public static String today() { return AppUpdates.today(); }
 public static android.content.SharedPreferences prefs(android.content.Context c) { return AppUpdates.prefs(c); }
 public static java.io.File apk(android.content.Context c) { return AppUpdates.apk(c); }
 public static android.app.AlertDialog errorDialog(UpdateInstallActivity a) { return a.errorDialog; }
}
