package dev.guga.dsh;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.os.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;

final class AppUpdates {
  static final String REPO = "https://github.com/did2818233534/deepseek-harness-for-android";
  static final String MANIFEST = REPO + "/releases/latest/download/latest.json";
  static final int NOTICE = 50202;
  static final long MAX_APK = 200 * 1024 * 1024L;
  static final AtomicBoolean running = new AtomicBoolean();
  static volatile okhttp3.OkHttpClient activeClient;
  static android.content.SharedPreferences prefs(Context c) { return c.getSharedPreferences("updates", 0); }
  static boolean enabled(Context c) { return prefs(c).getBoolean("enabled", true); }
  static void setEnabled(Context c, boolean value) {
    prefs(c).edit().putBoolean("enabled", value).apply();
    if (!value) {
      new Thread(() -> {
        int session=prefs(c).getInt("session",-1);
        if (session>=0) try { c.getPackageManager().getPackageInstaller().abandonSession(session); } catch (Exception ignored) {}
        prefs(c).edit().remove("session").apply();
      }, "DSH-update-abandon").start();
      okhttp3.OkHttpClient client = activeClient;
      if (client != null) new Thread(() -> client.dispatcher().cancelAll(), "DSH-update-cancel").start();
      c.getSystemService(NotificationManager.class).cancel(NOTICE);
    } else {
      checkAsync(c, false, null);
    }
  }
  static String today() {
    return new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(new java.util.Date());
  }
  static boolean due(String checkedDay, String today, boolean manual, boolean enabled) {
    return manual || (enabled && !today.equals(checkedDay));
  }
  static void checkAsync(Context context, boolean manual, Runnable finished) {
    Context c = context.getApplicationContext();
    new Thread(() -> {
      boolean locked = false;
      try {
        if (!running.compareAndSet(false, true)) return;
        locked = true;
        if (!due(prefs(c).getString("checkedDay", ""), today(), manual, enabled(c))) return;
        prefs(c).edit().putString("checkedDay", today()).apply();
        check(c, manual);
      } catch (Exception ignored) {
        // Offline, GitHub outages, invalid updates and rate limiting never interrupt chat.
      } finally {
        if (locked) {
          okhttp3.OkHttpClient client = activeClient; activeClient = null;
          if (client != null) {
            client.dispatcher().cancelAll(); client.connectionPool().evictAll();
            client.dispatcher().executorService().shutdown();
          }
          running.set(false);
        }
        if (finished != null) finished.run();
      }
    }, "DSH-update-check").start();
  }
  static final class Release {
    final long version, size;
    final String url, sha, name;
    Release(long version, long size, String url, String sha, String name) {
      this.version=version; this.size=size; this.url=url; this.sha=sha; this.name=name;
    }
  }
  static Release parse(String text, long installed, String[] abis) throws Exception {
    JSONObject root = new JSONObject(text);
    if (root.optInt("schema") != 1 || root.optBoolean("prerelease")) throw new IOException("Unsupported release");
    long version = root.getLong("versionCode");
    if (version <= installed) return null;
    JSONObject assets = root.getJSONObject("assets");
    JSONObject asset = null;
    for (String abi : abis) if (assets.optJSONObject(abi) != null) { asset = assets.getJSONObject(abi); break; }
    if (asset == null) throw new IOException("Unsupported ABI");
    String url=asset.getString("url"), sha=asset.getString("sha256");
    long size=asset.getLong("size");
    if (!url.startsWith(REPO + "/releases/download/") || !url.endsWith(".apk") ||
        !sha.matches("[a-fA-F0-9]{64}") || size <= 0 || size > MAX_APK) throw new IOException("Invalid release");
    return new Release(version, size, url, sha, root.getString("versionName"));
  }
  static File apk(Context c) { return new File(c.getFilesDir(), "updates/update.apk"); }
  static void check(Context c, boolean manual) throws Exception {
    PackageInfo installed = c.getPackageManager().getPackageInfo(c.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
    activeClient = new okhttp3.OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(30, TimeUnit.SECONDS).callTimeout(8, TimeUnit.MINUTES).build();
    check(c, manual, activeClient, installed);
  }
  static void check(Context c, boolean manual, okhttp3.OkHttpClient client, PackageInfo installed) throws Exception {
    Release release;
    try (okhttp3.Response r = client.newCall(new okhttp3.Request.Builder().url(MANIFEST).build()).execute()) {
      if (!r.isSuccessful() || r.body() == null) return;
      ByteArrayOutputStream manifest = new ByteArrayOutputStream();
      InputStream stream = r.body().byteStream(); byte[] block = new byte[8192]; int n;
      while ((n=stream.read(block))!=-1) {
        manifest.write(block,0,n);
        if (manifest.size()>1024*1024) return;
      }
      byte[] bytes = manifest.toByteArray();
      if (bytes.length > 1024 * 1024) return;
      release = parse(new String(bytes, java.nio.charset.StandardCharsets.UTF_8), installed.getLongVersionCode(), Build.SUPPORTED_ABIS);
    }
    if (release == null) {
      prefs(c).edit().putLong("success",System.currentTimeMillis()).apply();
      if (prefs(c).getLong("ready", 0) <= installed.getLongVersionCode()) {
        apk(c).delete(); prefs(c).edit().remove("ready").remove("session").apply();
        c.getSystemService(NotificationManager.class).cancel(NOTICE);
      }
      return;
    }
    if (!manual && !enabled(c)) return;
    File target = apk(c); target.getParentFile().mkdirs();
    if (prefs(c).getLong("ready", 0) == release.version && valid(c, target, release.version)) {
      prefs(c).edit().putLong("success",System.currentTimeMillis()).apply();
      notifyReady(c, release.name); return;
    }
    File partial = new File(target.getParentFile(), "download.part");
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      long total = 0;
      try (okhttp3.Response r = client.newCall(new okhttp3.Request.Builder().url(release.url).build()).execute()) {
        if (!r.isSuccessful() || r.body() == null) return;
        try (InputStream in = r.body().byteStream(); FileOutputStream out = new FileOutputStream(partial)) {
          byte[] buffer = new byte[32768]; int count;
          while ((count = in.read(buffer)) != -1) {
            if ((!manual && !enabled(c)) || Thread.currentThread().isInterrupted()) throw new IOException("Cancelled");
            total += count; if (total > release.size || total > MAX_APK) throw new IOException("Size mismatch");
            out.write(buffer, 0, count); digest.update(buffer, 0, count);
          }
          out.getFD().sync();
        }
      }
      if (total != release.size || !hex(digest.digest()).equalsIgnoreCase(release.sha) ||
          !valid(c, partial, release.version)) return;
      if (!manual && !enabled(c)) return;
      if (target.exists() && !target.delete()) return;
      if (!partial.renameTo(target)) return;
      prefs(c).edit().putLong("success",System.currentTimeMillis()).putLong("ready", release.version).putString("name", release.name).remove("session").apply();
      if (c.getPackageManager().canRequestPackageInstalls()) install(c);
      else notifyReady(c, release.name);
    } finally { partial.delete(); }
  }
  static String hex(byte[] bytes) {
    StringBuilder s = new StringBuilder(); for (byte b : bytes) s.append(String.format(java.util.Locale.ROOT, "%02x", b & 255)); return s.toString();
  }
  static boolean valid(Context c, File file, long version) {
    try {
      PackageManager pm = c.getPackageManager();
      PackageInfo next = pm.getPackageArchiveInfo(file.getPath(), PackageManager.GET_SIGNING_CERTIFICATES);
      PackageInfo own = pm.getPackageInfo(c.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
      if (next == null || !c.getPackageName().equals(next.packageName) ||
          next.getLongVersionCode() != version || version <= own.getLongVersionCode() ||
          next.applicationInfo.minSdkVersion > Build.VERSION.SDK_INT || next.signingInfo == null) return false;
      Set<String> a=new HashSet<>(), b=new HashSet<>();
      for (Signature signature : own.signingInfo.getApkContentsSigners()) a.add(hex(MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())));
      for (Signature signature : next.signingInfo.getApkContentsSigners()) b.add(hex(MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())));
      return !a.isEmpty() && a.equals(b);
    } catch (Exception ignored) { return false; }
  }
  static void install(Context c) throws Exception {
    if (!c.getPackageManager().canRequestPackageInstalls()) return;
    long version=prefs(c).getLong("ready", 0);
    if (!valid(c, apk(c), version)) return;
    PackageInstaller installer=c.getPackageManager().getPackageInstaller();
    int previous=prefs(c).getInt("session", -1);
    if (previous >= 0 && installer.getSessionInfo(previous) != null) installer.abandonSession(previous);
    PackageInstaller.SessionParams params=new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
    params.setAppPackageName(c.getPackageName()); params.setSize(apk(c).length());
    if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
    int id=installer.createSession(params);
    try (PackageInstaller.Session session=installer.openSession(id)) {
      try (InputStream in=new FileInputStream(apk(c)); OutputStream out=session.openWrite("base.apk",0,apk(c).length())) {
        byte[] buffer=new byte[32768]; int n;
        while ((n=in.read(buffer))!=-1) { out.write(buffer,0,n); }
        session.fsync(out);
      }
      Intent result=new Intent(c, UpdateResultReceiver.class).setAction("dev.guga.dsh.UPDATE_RESULT");
      PendingIntent callback=PendingIntent.getBroadcast(c,id,result,PendingIntent.FLAG_UPDATE_CURRENT |
        (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0));
      prefs(c).edit().putInt("session",id).apply();
      session.commit(callback.getIntentSender());
    } catch (Exception e) { installer.abandonSession(id); prefs(c).edit().remove("session").apply(); throw e; }
  }
  static void notifyReady(Context c, String version) { notifyReady(c, version, null); }
  static void notifyReady(Context c, String version, Intent confirmation) {
    NotificationManager manager=c.getSystemService(NotificationManager.class);
    manager.createNotificationChannel(new NotificationChannel("updates","应用更新",NotificationManager.IMPORTANCE_LOW));
    Intent open=new Intent(c,UpdateInstallActivity.class);
    if (confirmation != null) open.putExtra("confirmation",confirmation);
    PendingIntent pending=PendingIntent.getActivity(c,NOTICE,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    try {
      manager.notify(NOTICE,new Notification.Builder(c,"updates").setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("DSH Pocket " + version + " 已下载")
        .setContentText("点此完成系统安装确认").setContentIntent(pending).setAutoCancel(true).build());
    } catch (SecurityException ignored) {}
  }
}
