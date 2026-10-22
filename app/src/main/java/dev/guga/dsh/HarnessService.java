package dev.guga.dsh;

import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;
import java.nio.file.Files;
import java.util.UUID;

public final class HarnessService extends Service {

  static {
    System.loadLibrary("node");
    System.loadLibrary("harness");
  }

  private static native int startNode(String[] arguments, String logPath);

  static volatile HarnessService instance;
  static volatile String failure = "";
  static volatile boolean nodeStarted;
  static String token;
  static NativeBridge retainedBridge;
  static File retainedRuntimeDir;
  volatile boolean taskActive;
  NativeBridge bridge;
  File runtimeDir;
  PowerManager.WakeLock wake;
  final Handler main = new Handler(Looper.getMainLooper());

  @Override
  public void onCreate() {
    super.onCreate();
    instance = this;
    NotificationManager nm = getSystemService(NotificationManager.class);
    nm.createNotificationChannel(
      new NotificationChannel(
        "tasks",
        "任务运行",
        NotificationManager.IMPORTANCE_LOW
      )
    );
    try {
      failure = "";
      wake = ((PowerManager) getSystemService(POWER_SERVICE)).newWakeLock(
        PowerManager.PARTIAL_WAKE_LOCK, "DSHPocket:task");
      if (nodeStarted && retainedBridge != null) {
        bridge = retainedBridge;
        bridge.service = this;
        runtimeDir = retainedRuntimeDir;
        android.util.Log.i("DSH-Lifecycle", "Reattached existing core");
        main.postDelayed(() -> { if (!taskActive) taskStatus(false); }, 1200);
        return;
      }
      token = UUID.randomUUID().toString() + UUID.randomUUID();
      runtimeDir = new File(getFilesDir(), "runtime");
      runtimeDir.mkdirs();
      copyAsset("runtime/main.cjs", new File(runtimeDir, "main.cjs"));
      copyAsset("runtime/bootstrap.cjs", new File(runtimeDir, "bootstrap.cjs"));
      new File(runtimeDir, "runtime-port").delete();
      new File(runtimeDir, "runtime-error").delete();
      bridge = new NativeBridge(this, token);
      bridge.start(30000, false);
      wake = ((PowerManager) getSystemService(POWER_SERVICE)).newWakeLock(
        PowerManager.PARTIAL_WAKE_LOCK,
        "DSHPocket:task"
      );
      if (nodeStarted) throw new IOException(
        "运行服务已被系统回收，请彻底关闭应用后重新打开"
      );
      retainedBridge = bridge;
      retainedRuntimeDir = runtimeDir;
      nodeStarted = true;
      android.util.Log.i("DSH-Lifecycle", "Starting process core");
      new Thread(() -> {
        int result = startNode(
          new String[] {
            "node",
            new File(runtimeDir, "bootstrap.cjs").getAbsolutePath(),
            runtimeDir.getAbsolutePath(),
            String.valueOf(bridge.getListeningPort()),
            token,
          },
          new File(runtimeDir, "runtime.log").getAbsolutePath()
        );
        failure = "运行核心已停止（" + result + "），请重新打开应用";
        taskStatus(false);
      }, "DSH-Core").start();
      main.postDelayed(() -> {
        if (!taskActive) taskStatus(false);
      }, 1200);
    } catch (Throwable e) {
      failure = e.toString();
      stopForeground(STOP_FOREGROUND_REMOVE);
    }
  }

  private void copyAsset(String name, File dest) throws IOException {
    try (
      InputStream in = getAssets().open(name);
      FileOutputStream out = new FileOutputStream(dest)
    ) {
      FileTools.copy(in, out, 20 * 1024 * 1024);
    }
  }

  Notification notification(String text) {
    Intent open = new Intent(this, MainActivity.class);
    PendingIntent pi = PendingIntent.getActivity(
      this,
      0,
      open,
      PendingIntent.FLAG_IMMUTABLE
    );
    Intent cancel = new Intent(this, HarnessService.class).setAction("cancel");
    PendingIntent ci = PendingIntent.getService(
      this,
      1,
      cancel,
      PendingIntent.FLAG_IMMUTABLE
    );
    return new Notification.Builder(this, "tasks")
      .setSmallIcon(R.drawable.ic_notification)
      .setContentTitle("DSH Pocket")
      .setContentText(text)
      .setContentIntent(pi)
      .setOngoing(true)
      .addAction(new Notification.Action.Builder(null, "停止任务", ci).build())
      .build();
  }

  void taskStatus(boolean busy) {
    taskActive = busy;
    main.post(() -> {
      if (busy) {
        startService(new Intent(this, HarnessService.class));
        startForeground(1, notification("正在处理，可返回其他应用"));
        if (!wake.isHeld()) wake.acquire(30 * 60 * 1000L);
      } else {
        if (wake != null && wake.isHeld()) wake.release();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf(); // Foreground Activity binding keeps idle service available.
      }
    });
  }

  static int port() throws Exception {
    HarnessService s = instance;
    if (s == null || s.runtimeDir == null) throw new IOException(
      "核心尚未启动"
    );
    File err = new File(s.runtimeDir, "runtime-error");
    if (err.exists()) throw new IOException(
      new String(
        Files.readAllBytes(err.toPath()),
        java.nio.charset.StandardCharsets.UTF_8
      )
    );
    File p = new File(s.runtimeDir, "runtime-port");
    if (!p.exists()) throw new IOException("正在启动本地核心…");
    return Integer.parseInt(
      new String(
        Files.readAllBytes(p.toPath()),
        java.nio.charset.StandardCharsets.UTF_8
      ).trim()
    );
  }

  @Override
  public int onStartCommand(Intent i, int flags, int id) {
    if (i != null && "cancel".equals(i.getAction())) new Thread(() -> {
      try {
        Api.call("/cancel", new org.json.JSONObject());
      } catch (Exception e) {
        failure = e.getMessage();
      }
      taskStatus(false);
    }, "DSH-Cancel").start();
    return START_NOT_STICKY;
  }

  @Override
  public void onTimeout(int startId, int fgsType) {
    new Thread(() -> {
      try {
        Api.call("/cancel", new org.json.JSONObject());
      } catch (Exception e) {
        failure = e.getMessage();
      }
      taskStatus(false);
      stopSelf();
    }).start();
  }

  @Override
  public IBinder onBind(Intent i) {
    return new Binder();
  }

  @Override
  public void onDestroy() {
    if (wake != null && wake.isHeld()) wake.release();
    android.util.Log.i("DSH-Lifecycle", "Service destroyed; process core retained");
    main.removeCallbacksAndMessages(null);
    // Node can only be started once per process. Keep its authenticated bridge
    // and endpoint alive when Android destroys only the Service component.
    if (bridge != null && bridge.service == this) bridge.service = null;
    if (instance == this) instance = null;
    super.onDestroy();
  }
}
