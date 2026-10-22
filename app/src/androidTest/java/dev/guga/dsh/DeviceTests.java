package dev.guga.dsh;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.Intent;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.*;
import org.json.*;
import org.junit.Test;

public class DeviceTests {
  @org.junit.Before public void preventExternalUpdateChecks() {
    AppUpdates.prefs(getContext()).edit().putString("checkedDay",AppUpdates.today()).commit();
  }
  @Test
  public void testSecretVisibilityKeepsValueAndSelection() throws Exception {
    android.app.Instrumentation i = InstrumentationRegistry.getInstrumentation();
    MainActivity activity = (MainActivity) i.startActivitySync(
      new Intent(getContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    i.runOnMainSync(() -> {
      android.widget.LinearLayout box = new android.widget.LinearLayout(activity);
      String fake = "sk-demo-for-ui-test";
      android.widget.EditText field = activity.secretField(box, "API Key", fake);
      assertNotNull(field.getTransformationMethod());
      assertEquals(fake.length(), field.getTransformationMethod().getTransformation(field.getText(), field).length());
      field.setSelection(3);
      findDescription(box, "显示 API Key").performClick();
      assertNull(field.getTransformationMethod());
      assertEquals(fake, field.getText().toString());
      assertEquals(3, field.getSelectionStart());
      findDescription(box, "隐藏 API Key").performClick();
      assertNotNull(field.getTransformationMethod());
      assertEquals(fake, field.getText().toString());
      assertEquals(3, field.getSelectionStart());
      activity.finish();
    });
  }

  static android.view.View findDescription(android.view.View view, String description) {
    if (description.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) return view;
    if (view instanceof android.view.ViewGroup) {
      android.view.ViewGroup group = (android.view.ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        android.view.View result = findDescription(group.getChildAt(i), description);
        if (result != null) return result;
      }
    }
    return null;
  }
  @Test
  public void testQuickModelAutoSave() throws Exception {
    getContext().getSharedPreferences("settings", 0).edit().clear()
      .putString("modelCatalog", "{\"data\":[{\"id\":\"deepseek-v4-pro\"},{\"id\":\"deepseek-flash\",\"name\":\"DeepSeek V4.1 Flash\"}]}").commit();
    android.app.Instrumentation i = InstrumentationRegistry.getInstrumentation();
    MainActivity activity = (MainActivity) i.startActivitySync(
      new Intent(getContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    i.runOnMainSync(() -> activity.modelSettings(true));
    i.waitForIdleSync();
    i.runOnMainSync(() -> {
      android.view.View panel = activity.dialogs.peek().getWindow().getDecorView();
      findDescription(panel, "选择模型").performClick();
    });
    i.waitForIdleSync();
    i.runOnMainSync(() -> {
      assertNotNull(activity.modelPopup);
      android.view.View choice = findDescription(activity.modelPopup.getContentView(), "选择 deepseek-flash");
      assertNotNull(choice); choice.performClick();
      assertEquals("deepseek-flash", new Settings(activity).model());
      assertNull(activity.modelPopup);
      assertEquals(1, activity.dialogs.size());
      assertSame(activity.chatRoot, activity.currentPage);
      activity.onBackPressed();
    });
    i.waitForIdleSync();
    i.runOnMainSync(() -> {
      assertTrue(activity.dialogs.isEmpty());
      activity.finish();
    });
  }

  @Test
  public void testIdleNotificationAndTaskIcon() throws Exception {
    android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    MainActivity activity = (MainActivity) instrumentation.startActivitySync(
      new Intent(getContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    android.app.NotificationManager manager = getContext().getSystemService(android.app.NotificationManager.class);
    instrumentation.runOnMainSync(() -> {
      assertNotNull(HarnessService.instance);
      assertFalse(HarnessService.instance.taskActive);
      assertEquals("Opening app should not post notification", 0, manager.getActiveNotifications().length);
      assertEquals(R.drawable.ic_notification,
        HarnessService.instance.notification("test").getSmallIcon().getResId());
      activity.finish();
    });
  }

  @Test
  public void testModelConnectionCleanupOffMainThread() throws Exception {
    java.util.concurrent.atomic.AtomicReference<Thread> worker = new java.util.concurrent.atomic.AtomicReference<>();
    java.util.concurrent.atomic.AtomicReference<Thread> caller = new java.util.concurrent.atomic.AtomicReference<>();
    okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder().build();
    InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
      caller.set(Thread.currentThread());
      worker.set(MainActivity.closeModelClient(client));
    });
    assertNotSame(caller.get(), worker.get());
    worker.get().join(3000);
    assertFalse(worker.get().isAlive());
    assertTrue(client.dispatcher().executorService().isShutdown());
  }

  @Test
  public void testDialogBackKeepsListAndChat() throws Exception {
    android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    MainActivity activity = (MainActivity) instrumentation.startActivitySync(
      new Intent(getContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    instrumentation.runOnMainSync(() -> {
      activity.navigation();
      try { activity.deleteSession(new JSONObject().put("id", "not-deleted-test"), false,
        () -> fail("Cancel must never delete")); }
      catch (JSONException e) { throw new AssertionError(e); }
      assertEquals(2, activity.dialogs.size());
      android.app.AlertDialog confirmation=(android.app.AlertDialog)activity.dialogs.peek();
      for (int which : new int[]{android.app.AlertDialog.BUTTON_POSITIVE,android.app.AlertDialog.BUTTON_NEGATIVE}) {
        android.graphics.drawable.RippleDrawable background=(android.graphics.drawable.RippleDrawable)confirmation.getButton(which).getBackground();
        assertEquals("Only ripple mask, no filled button",1,background.getNumberOfLayers());
      }
    });
    instrumentation.waitForIdleSync();
    try (java.io.FileOutputStream image=new java.io.FileOutputStream(new java.io.File(getContext().getExternalFilesDir(null),"delete-dialog.png"))) {
      android.graphics.Bitmap shot=instrumentation.getUiAutomation().takeScreenshot();
      shot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,image); shot.recycle();
    }
    instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
    instrumentation.waitForIdleSync();
    instrumentation.runOnMainSync(() -> {
      assertEquals("Back dismisses confirmation only", 1, activity.dialogs.size());
      assertTrue(activity.dialogs.peek().isShowing());
      assertFalse(activity.isFinishing());
    });
    instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
    instrumentation.waitForIdleSync();
    instrumentation.runOnMainSync(() -> {
      assertTrue(activity.dialogs.isEmpty());
      assertSame(activity.chatRoot, activity.currentPage);
      assertFalse(activity.isFinishing());
      android.widget.LinearLayout.LayoutParams spacing = activity.spacedControl();
      assertTrue(spacing.topMargin > 0 && spacing.bottomMargin > 0);
      activity.finish();
    });
  }

  static boolean containsText(android.view.View view, String text) {
    if (view instanceof android.widget.TextView
        && ((android.widget.TextView) view).getText().toString().contains(text)) return true;
    if (view instanceof android.view.ViewGroup) {
      android.view.ViewGroup group = (android.view.ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++)
        if (containsText(group.getChildAt(i), text)) return true;
    }
    return false;
  }

  @Test
  public void testSettingsNavigationAndQuickControls() throws Exception {
    getContext().getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit();
    android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    MainActivity activity = (MainActivity) instrumentation.startActivitySync(
      new Intent(getContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    instrumentation.runOnMainSync(() -> {
      activity.settings();
      android.view.View settings = activity.currentPage;
      activity.modelSettings(false);
      assertTrue(containsText(activity.currentPage, "每轮最多步骤"));
      assertEquals(2, activity.pageHistory.size());
      activity.onBackPressed();
      assertSame(settings, activity.currentPage);
      activity.onBackPressed();
      assertSame(activity.chatRoot, activity.currentPage);
      assertFalse(activity.isFinishing());
      activity.modelSettings(true);
      assertSame(activity.chatRoot, activity.currentPage);
      android.view.View quick = activity.dialogs.peek().getWindow().getDecorView();
      assertTrue(containsText(quick, "联网搜索"));
      assertFalse(containsText(quick, "每轮最多步骤"));
      assertFalse(containsText(quick, "网页读取"));
      assertFalse(containsText(quick, "深度思考"));
      assertFalse(containsText(quick, "保存"));
      activity.onBackPressed();
      assertTrue(activity.pageCleanup.isEmpty());
      activity.finish();
    });
  }

  @Test
  public void testModelListParsing() throws Exception {
    assertEquals(java.util.Arrays.asList("model-a", "model-b"), ModelList.parse(
      "{\"data\":[{\"id\":\"model-a\"},{\"id\":\"model-b\"},{\"id\":\"model-a\"},{\"id\":\"\"},{\"id\":42},null]}"));
    for (String invalid : new String[] { "{}", "{\"data\":[]}", "invalid" }) {
      try { ModelList.parse(invalid); fail("Invalid model list accepted"); }
      catch (JSONException expected) {}
    }
  }


  Context getContext() {
    return InstrumentationRegistry.getInstrumentation().getTargetContext();
  }

  @Test
  public void testAdaptiveChatBubbles() throws Exception {
    android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    MainActivity activity = (MainActivity) instrumentation.startActivitySync(new Intent(getContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    Thread.sleep(1500);
    instrumentation.runOnMainSync(() -> {
      activity.visible = false;
      activity.handler.removeCallbacks(activity.poll);
      activity.messages.removeAllViews();
      activity.card("你", "你好", true, false);
      activity.card("你", "请帮我整理这份文件，提炼主要观点，保留重要数据，并生成一份便于阅读的摘要。", true, false);
      activity.card("DSH", "好的。", false, false);
      activity.card("DSH", "我会先读取文件，再整理重点并生成新文件。原文件会保留，完成后你可以从文件库导出结果。", false, false);
    });
    java.util.concurrent.CountDownLatch layoutReady = new java.util.concurrent.CountDownLatch(1);
    instrumentation.runOnMainSync(() -> activity.messages.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
      public void onGlobalLayout() {
        if (activity.messages.getChildCount() == 4 && activity.messages.getChildAt(0).getWidth() > 0) {
          activity.messages.getViewTreeObserver().removeOnGlobalLayoutListener(this);
          layoutReady.countDown();
        }
      }
    }));
    instrumentation.runOnMainSync(() -> activity.messages.requestLayout());
    assertTrue("layout completes", layoutReady.await(5, java.util.concurrent.TimeUnit.SECONDS));
    instrumentation.runOnMainSync(() -> {
      assertTrue("model label must not be clipped", activity.status.getBottom() <= ((android.view.View)activity.status.getParent()).getHeight());
      android.view.View shortUser = activity.messages.getChildAt(0), longUser = activity.messages.getChildAt(1);
      android.view.View shortAssistant = activity.messages.getChildAt(2), longAssistant = activity.messages.getChildAt(3);
      assertTrue("short user bubble must shrink", shortUser.getWidth() < longUser.getWidth());
      assertTrue("short assistant bubble must shrink", shortAssistant.getWidth() < longAssistant.getWidth());
      assertEquals("user bubbles align right", shortUser.getRight(), longUser.getRight());
      assertEquals("assistant bubbles align left", shortAssistant.getLeft(), longAssistant.getLeft());
      assertTrue("user bubble respects available width", longUser.getWidth() < activity.messages.getWidth());
      assertTrue("assistant bubble respects available width", longAssistant.getWidth() < activity.messages.getWidth());
      try {
        android.view.View view = activity.getWindow().getDecorView();
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(view.getWidth(), view.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
        view.draw(new android.graphics.Canvas(bitmap));
        try (FileOutputStream out = new FileOutputStream(new File(activity.getCacheDir(), "bubble-test.png"))) {
          bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
        }
        bitmap.recycle();
      } catch (Exception e) { throw new RuntimeException(e); }
    });
  }

  @Test
  public void testServiceRebindKeepsCore() throws Exception {
    android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    MainActivity activity = (MainActivity) instrumentation.startActivitySync(new Intent(getContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    long deadline = System.currentTimeMillis() + 15000;
    while (HarnessService.instance == null && System.currentTimeMillis() < deadline) Thread.sleep(100);
    Api.call("/state", new JSONObject());
    NativeBridge original = HarnessService.instance.bridge;
    instrumentation.runOnMainSync(activity::finishAffinity);
    getContext().stopService(new Intent(getContext(), HarnessService.class));
    while (HarnessService.instance != null && System.currentTimeMillis() < deadline) Thread.sleep(100);
    assertNull("idle Android service must be released", HarnessService.instance);
    instrumentation.startActivitySync(new Intent(getContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    while (HarnessService.instance == null && System.currentTimeMillis() < deadline) Thread.sleep(100);
    assertNotNull(HarnessService.instance);
    assertSame("reuse authenticated bridge and Node runtime", original, HarnessService.instance.bridge);
    assertNotNull(Api.call("/state", new JSONObject()));
  }

  @Test
  public void testNativeDocumentsAndWorkspaceIsolation() throws Exception {
    FileTools f = new FileTools(getContext());
    String prefix = "test-" + System.nanoTime();
    try {
      JSONObject text = new JSONObject()
        .put("path", prefix + ".csv")
        .put("content", "姓名,金额\n张三,12\n李四,18");
      f.write(text, false);
      assertTrue(
        f
          .read(new JSONObject().put("path", prefix + ".csv"))
          .getString("text")
          .contains("张三")
      );
      try {
        f.write(text, false);
        fail("overwrite accepted");
      } catch (IOException expected) {}
      try {
        f.resolve("../settings.xml");
        fail("escape accepted");
      } catch (IOException expected) {}
      String doc = "中文文档测试\n第二段内容。";
      f.write(
        new JSONObject().put("path", prefix + ".docx").put("content", doc),
        true
      );
      assertTrue(
        f
          .read(new JSONObject().put("path", prefix + ".docx"))
          .getString("text")
          .contains("第二段内容")
      );
      f.write(
        new JSONObject()
          .put("path", prefix + ".pdf")
          .put("content", "PDF export test\nSecond line"),
        true
      );
      assertTrue(f.resolve(prefix + ".pdf").length() > 100);
      String copy = f.importFile(
        new ByteArrayInputStream("copy".getBytes(StandardCharsets.UTF_8)),
        prefix + ".csv"
      );
      assertFalse(copy.equals(prefix + ".csv"));
      f.resolve(copy).delete();
    } finally {
      for (String ext : new String[] { ".csv", ".docx", ".pdf" })
        f.resolve(prefix + ext).delete();
    }
  }

  @Test
  public void testDeepSeekWebSettingsAndTransport() throws Exception {
    Settings config = new Settings(getContext());
    config.save("https://api.deepseek.com", "deepseek-v4-pro", "test-local-only", false, 32);
    config.saveWeb(true, true);
    assertTrue(config.webSearch()); assertTrue(config.webFetch());
    try {
      try { config.save("https://other.example", "model", "", false, 32); fail("foreign endpoint accepted"); }
      catch (Exception expected) {}
    } finally {
      getContext().getSharedPreferences("settings", 0).edit().clear().commit();
    }
  }

  @Test
  public void testNativeCoreRoundTrip() throws Exception {
    getContext().startActivity(
      new Intent(getContext(), MainActivity.class).addFlags(
        Intent.FLAG_ACTIVITY_NEW_TASK
      )
    );
    long deadline = System.currentTimeMillis() + 20000;
    while (
        HarnessService.instance == null && System.currentTimeMillis() < deadline
      )
      Thread.sleep(100);
    assertNotNull(
      "Launch the app before instrumentation",
      HarnessService.instance
    );
    while (true) {
      try {
        Api.call("/state", new JSONObject());
        break;
      } catch (Exception e) {
        if (System.currentTimeMillis() > deadline) throw e;
        Thread.sleep(100);
      }
    }
    HarnessService service = HarnessService.instance;
    NativeBridge bridge = service.bridge;
    // Intercept only in-process instrumentation; never sends a test key to a remote endpoint.
    OkHttpClient original = bridge.api;
    AtomicInteger requests = new AtomicInteger();
    String output = "device-test-" + System.nanoTime() + ".txt";
    bridge.api = new OkHttpClient.Builder()
      .addInterceptor(chain -> {
        try {
          int n = requests.getAndIncrement();
          okio.Buffer requestBody = new okio.Buffer();
          chain.request().body().writeTo(requestBody);
          JSONObject req = new JSONObject(requestBody.readUtf8());
          JSONObject delta = new JSONObject();
          if (n == 0) {
            JSONObject fn = new JSONObject()
              .put("name", "write_file")
              .put(
                "arguments",
                new JSONObject()
                  .put("path", output)
                  .put("content", "Android native file tool passed")
                  .toString()
              );
            delta.put(
              "tool_calls",
              new JSONArray().put(
                new JSONObject()
                  .put("index", 0)
                  .put("id", "native_call")
                  .put("function", fn)
              )
            );
          } else {
            boolean sawTool = false;
            JSONArray ms = req.getJSONArray("messages");
            for (int i = 0; i < ms.length(); i++) if (
              "tool".equals(ms.getJSONObject(i).optString("role"))
            ) sawTool = true;
            if (!sawTool) throw new IOException("missing tool result");
            delta.put("content", "已完成安卓文件处理：中文👋😀 café 𠮷");
          }
          String s =
            "data: " +
            new JSONObject().put(
              "choices",
              new JSONArray().put(new JSONObject().put("delta", delta))
            ) +
            "\n\n" +
            "data: " +
            new JSONObject().put(
              "choices",
              new JSONArray().put(
                new JSONObject()
                  .put("delta", new JSONObject())
                  .put("finish_reason", n == 0 ? "tool_calls" : "stop")
              )
            ) +
            "\n\ndata: [DONE]\n\n";
          return new okhttp3.Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(new ResponseBody() {
              final okio.BufferedSource bytes = okio.Okio.buffer(new okio.ForwardingSource(new okio.Buffer().writeUtf8(s)) {
                @Override public long read(okio.Buffer sink, long count) throws IOException { return super.read(sink, Math.min(count, 1)); }
              });
              @Override public MediaType contentType() { return MediaType.get("text/event-stream; charset=utf-8"); }
              @Override public long contentLength() { return -1; }
              @Override public okio.BufferedSource source() { return bytes; }
            })
            .build();
        } catch (JSONException e) {
          throw new IOException(e);
        }
      })
      .build();
    try {
      new Settings(getContext()).save(
        "https://api.deepseek.com",
        "test-model",
        "instrumentation-not-a-real-key",
        false,
        32
      );
      Api.call("/select", new JSONObject());
      Api.call("/send", new JSONObject().put("text", "请创建测试文件"));
      JSONObject state = null;
      deadline = System.currentTimeMillis() + 20000;
      do {
        Thread.sleep(100);
        state = Api.call("/state", new JSONObject());
      } while (
        state.optBoolean("busy") && System.currentTimeMillis() < deadline
      );
      assertFalse(state.toString(), state.optBoolean("busy"));
      assertEquals(state.toString(), 2, requests.get());
      assertTrue(
        "native output content",
        new FileTools(getContext())
          .read(new JSONObject().put("path", output))
          .getString("text")
          .contains("passed")
      );
      assertTrue(
        "assistant text: " + state,
        state.toString().contains("已完成安卓文件处理")
      );
      assertTrue("UTF-8 survives byte-split native bridge", state.toString().contains("中文👋😀 café 𠮷"));
      assertFalse("no replacement characters", state.toString().contains("\uFFFD"));
      final JSONObject renderedState = state;
      final MainActivity[] screen = new MainActivity[1];
      InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
        for (android.app.Activity a : androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED))
          if (a instanceof MainActivity) screen[0] = (MainActivity)a;
        assertNotNull(screen[0]);
        screen[0].input.requestFocus();
        screen[0].revision = -1;
        screen[0].render(renderedState);
      });
      InstrumentationRegistry.getInstrumentation().waitForIdleSync();
      InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> assertTrue("render/autoscroll must preserve composer focus", screen[0].input.hasFocus()));

      boolean found = false;
      JSONArray events = state.getJSONArray("events");
      for (int i = 0; i < events.length(); i++) if (
        "tool/result".equals(events.getJSONObject(i).getString("type"))
      ) found = true;
      assertTrue("tool result event", found);
      bridge.api = new OkHttpClient.Builder().addInterceptor(chain -> {
        String url = chain.request().url().toString();
        if (!url.equals("https://api.deepseek.com/v1/tools/search") && !url.equals("https://api.deepseek.com/v1/tools/fetch")) throw new IOException("unexpected endpoint");
        return new okhttp3.Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
          .body(ResponseBody.create("{\"content\":\"mock page\",\"results\":[]}", MediaType.get("application/json"))).build();
      }).build();
      JSONObject web = new JSONObject().put("endpoint", "https://api.deepseek.com/v1/tools/search").put("body", new JSONObject().put("query", "test"));
      assertNotNull(bridge.webApi(web).getJSONArray("results"));
      new Settings(getContext()).saveWeb(false, true);
      try { bridge.webApi(web); fail("disabled search dispatched"); } catch (IOException expected) {}
      web.put("endpoint", "https://api.deepseek.com/v1/tools/fetch");
      assertEquals("mock page", bridge.webApi(web).getString("content"));
      new Settings(getContext()).saveWeb(false, false);
      try { bridge.webApi(web); fail("disabled fetch dispatched"); } catch (IOException expected) {}
      web.put("endpoint", "https://other.example");
      try { bridge.webApi(web); fail("foreign endpoint dispatched"); } catch (IOException expected) {}
      assertEquals(
        "instrumentation-not-a-real-key",
        new Settings(getContext()).apiKey()
      );
    } finally {
      bridge.api = original;
      getContext().getSharedPreferences("settings", 0).edit().clear().commit();
      new FileTools(getContext()).resolve(output).delete();
    }
  }
}
