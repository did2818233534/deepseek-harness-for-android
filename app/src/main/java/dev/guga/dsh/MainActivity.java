package dev.guga.dsh;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

public final class MainActivity extends Activity {

  static final int IMPORT = 10,
    EXPORT = 11;
  final int ink = Color.rgb(30, 34, 43),
    muted = Color.rgb(127, 132, 144),
    accent = Color.rgb(103, 80, 164),
    bg = Color.rgb(253, 251, 255);
  final UiStyle ui = new UiStyle(this);
  final Map<Dialog, Runnable> dialogCleanup = new HashMap<>();
  final ExecutorService io = Executors.newSingleThreadExecutor();
  final Handler handler = new Handler(Looper.getMainLooper());
  LinearLayout chatRoot;
  boolean settingsPage;
  final ArrayDeque<View> pageHistory = new ArrayDeque<>();
  final Map<View, Runnable> pageCleanup = new HashMap<>();
  View currentPage;
  PopupWindow modelPopup;
  final ArrayDeque<Dialog> dialogs = new ArrayDeque<>();
  android.window.OnBackInvokedCallback backCallback;
  LinearLayout messages;
  ScrollView scroll;
  EditText input;
  TextView status;
  ImageButton send;
  TextView chatTitle;
  boolean visible, busy, polling;
  long revision = -1;
  String currentId = "",
    exportPath = "";
  boolean serviceBound;
  final ServiceConnection coreConnection = new ServiceConnection() {
    public void onServiceConnected(ComponentName name, IBinder binder) {}
    public void onServiceDisconnected(ComponentName name) {}
  };

  @Override
  public void onStart() {
    super.onStart();
    serviceBound = bindService(new Intent(this, HarnessService.class), coreConnection, Context.BIND_AUTO_CREATE);
  }

  @Override
  public void onStop() {
    if (serviceBound) { unbindService(coreConnection); serviceBound = false; }
    super.onStop();
  }

  final Runnable poll = new Runnable() {
    public void run() {
      if (!visible) return;
      if (!polling) {
        polling = true;
        io.execute(() -> {
          try {
            JSONObject state = Api.call("/state", new JSONObject());
            runOnUiThread(() -> { if (visible) render(state); });
          } catch (Exception e) {
            // Startup/reconnection is internal; keep the model label stable.
          } finally {
            polling = false;
          }
        });
      }
      handler.postDelayed(this, 700);
    }
  };

  int dp(int n) {
    return Math.round(n * getResources().getDisplayMetrics().density);
  }

  @Override
  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    getWindow().setStatusBarColor(bg);
    getWindow().setNavigationBarColor(bg);
    getWindow()
      .getDecorView()
      .setSystemUiVisibility(
        View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR |
          View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
      );
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(bg);
    root.setPadding(dp(16), dp(12), dp(16), dp(12));
    chatRoot = root;
    currentPage = root;
    if (Build.VERSION.SDK_INT >= 33) {
      backCallback = () -> onBackPressed();
      getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
        android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback);
    }
    setContentView(root);
    if (Build.VERSION.SDK_INT >= 30) root.setOnApplyWindowInsetsListener(
      (v, insets) -> {
        android.graphics.Insets bars = insets.getInsets(
          WindowInsets.Type.systemBars()
        );
        root.setPadding(
          dp(16),
          bars.top + dp(8),
          dp(16),
          Math.max(bars.bottom, insets.getInsets(WindowInsets.Type.ime()).bottom) + dp(8)
        );
        return insets;
      }
    );
    LinearLayout header = row();
    header.addView(icon(R.drawable.ic_menu, "对话与设置", v -> navigation()), new LinearLayout.LayoutParams(dp(48), dp(48)));
    LinearLayout heading = new LinearLayout(this);
    heading.setOrientation(LinearLayout.VERTICAL);
    heading.setGravity(Gravity.CENTER_VERTICAL);
    heading.setMinimumHeight(dp(48));
    heading.setPadding(dp(8), 0, dp(8), 0);
    chatTitle = label("新对话", 17, ink);
    chatTitle.setSingleLine(true);
    chatTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
    heading.addView(chatTitle);
    status = label(new Settings(this).model(), 11, muted);
    status.setSingleLine(true);
    status.setEllipsize(android.text.TextUtils.TruncateAt.END);
    heading.addView(status);
    heading.setBackground(ui.surface(Color.TRANSPARENT, 16));
    heading.setOnClickListener(v -> modelSettings(true));
    heading.setContentDescription("DeepSeek 模型设置");
    header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
    header.addView(icon(R.drawable.ic_new_chat, "新对话", v -> newChat()), new LinearLayout.LayoutParams(dp(48), dp(48)));
    root.addView(header);
    scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    scroll.setVerticalScrollBarEnabled(false);
    messages = new LinearLayout(this);
    messages.setOrientation(LinearLayout.VERTICAL);
    messages.setPadding(dp(4), dp(24), dp(4), dp(16));
    scroll.addView(messages);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout composer = new LinearLayout(this);
    composer.setOrientation(LinearLayout.VERTICAL);
    composer.setPadding(dp(12), dp(8), dp(12), dp(8));
    composer.setBackground(outline(0xFFF1EDF7, 24));
    input = new EditText(this);
    input.setHint("发送消息");
    input.setHintTextColor(muted);
    input.setTextColor(ink);
    input.setTextSize(16);
    input.setMinLines(1);
    input.setMaxLines(5);
    input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    input.setPadding(dp(4), dp(12), dp(4), dp(14));
    input.setBackgroundColor(Color.TRANSPARENT);
    composer.addView(input, new LinearLayout.LayoutParams(-1, -2));
    LinearLayout controls = row();
    controls.addView(icon(R.drawable.ic_add, "添加文件", v -> importFile()), new LinearLayout.LayoutParams(dp(48), dp(48)));
    controls.addView(icon(R.drawable.ic_model, "模型设置", v -> modelSettings(true)), new LinearLayout.LayoutParams(dp(48), dp(48)));
    controls.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
    send = icon(R.drawable.ic_send, "发送消息", v -> send());
    send.setImageTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
    send.setBackground(ui.surface(accent, 24));
    controls.addView(send, new LinearLayout.LayoutParams(dp(48), dp(48)));
    composer.addView(controls);
    root.addView(composer);
    if (saved != null) {
      input.setText(saved.getString("draft", ""));
      exportPath = saved.getString("exportPath", "");
    }
    if (
      Build.VERSION.SDK_INT >= 33 &&
      checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
        android.content.pm.PackageManager.PERMISSION_GRANTED
    ) requestPermissions(
      new String[] { android.Manifest.permission.POST_NOTIFICATIONS },
      1
    );
  }

  @Override
  public void onSaveInstanceState(Bundle out) {
    out.putString("draft", input.getText().toString());
    out.putString("exportPath", exportPath);
    super.onSaveInstanceState(out);
  }

  @Override
  public void onResume() {
    super.onResume();
    AppUpdates.checkAsync(this, false, null);
    visible = true;
    handler.post(poll);
  }

  @Override
  public void onPause() {
    visible = false;
    handler.removeCallbacks(poll);
    super.onPause();
  }

  @Override
  public void onDestroy() {
    io.shutdown();
    for (Runnable cleanup : pageCleanup.values()) cleanup.run();
    pageCleanup.clear();
    for (Runnable cleanup : new ArrayList<>(dialogCleanup.values())) cleanup.run();
    dialogCleanup.clear();
    if (modelPopup != null) modelPopup.dismiss();
    for (Dialog dialog : new ArrayList<>(dialogs)) dialog.dismiss();
    if (Build.VERSION.SDK_INT >= 33 && backCallback != null)
      getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
    super.onDestroy();
  }

  LinearLayout row() {
    LinearLayout l = new LinearLayout(this);
    l.setOrientation(LinearLayout.HORIZONTAL);
    l.setGravity(Gravity.CENTER_VERTICAL);
    return l;
  }

  TextView label(String s, int size, int color) {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextSize(size);
    t.setTextColor(color);
    return t;
  }

  Button button(String text, View.OnClickListener click) {
    Button b = new Button(this);
    b.setText(text);
    b.setTextSize(15);
    b.setAllCaps(false);
    b.setSingleLine(true);
    b.setGravity(Gravity.CENTER);
    b.setIncludeFontPadding(false);
    b.setMinWidth(0);
    b.setMinimumWidth(0);
    b.setPadding(dp(12), 0, dp(12), 0);
    b.setTextColor(ink);
    b.setStateListAnimator(null);
    b.setMinimumHeight(dp(44));
    b.setMinHeight(dp(44));
    b.setBackground(new android.graphics.drawable.RippleDrawable(
      android.content.res.ColorStateList.valueOf(0x184B5BDE), shape(UiStyle.SURFACE, UiStyle.CARD), null));
    b.setOnClickListener(click);
    return b;
  }

  ImageButton icon(int resource, String description, View.OnClickListener click) {
    ImageButton b = new ImageButton(this);
    b.setImageResource(resource);
    b.setImageTintList(android.content.res.ColorStateList.valueOf(ink));
    b.setPadding(dp(12), dp(12), dp(12), dp(12));
    b.setScaleType(ImageView.ScaleType.FIT_CENTER);
    b.setContentDescription(description);
    b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x206750A4), shape(Color.TRANSPARENT, 24), null));
    b.setOnClickListener(click);
    return b;
  }

  void newChat() {
    request("/select", new JSONObject(), () -> { revision = -1; currentId = ""; });
  }

  void trackDialog(Dialog dialog) {
    dialogs.push(dialog);
    android.window.OnBackInvokedCallback callback = dialog::dismiss;
    if (Build.VERSION.SDK_INT >= 33)
      dialog.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
        android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback);
    dialog.setOnKeyListener((d, keyCode, event) -> {
      if (keyCode != KeyEvent.KEYCODE_BACK) return false;
      if (event.getAction() == KeyEvent.ACTION_UP) dialog.dismiss();
      return true;
    });
    dialog.setOnDismissListener(d -> {
      dialogs.remove(dialog);
      Runnable cleanup = dialogCleanup.remove(dialog);
      if (cleanup != null) cleanup.run();
      if (Build.VERSION.SDK_INT >= 33)
        dialog.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(callback);
    });
  }

  void navigation() { navigation(false); }

  void navigation(boolean archived) {
    if (archived) { archivedPage(); return; }
    Dialog drawer = new Dialog(this);
    LinearLayout panel = new LinearLayout(this);
    panel.setOrientation(LinearLayout.VERTICAL);
    panel.setPadding(dp(16), dp(24), dp(16), dp(16));
    panel.setBackground(shape(bg, 24));
    TextView title = label(archived ? "已归档" : "对话", 24, ink);
    title.setPadding(dp(8), 0, 0, dp(24));
    panel.addView(title);
    ScrollView scrollList = new ScrollView(this);
    LinearLayout list = new LinearLayout(this);
    list.setOrientation(LinearLayout.VERTICAL);
    list.setPadding(0, dp(16), 0, dp(16));
    scrollList.addView(list);
    panel.addView(scrollList, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout footer = row();
    footer.setPadding(0, dp(12), 0, 0);
    Button create = button(archived ? "返回对话" : "＋  新对话", v -> {
      drawer.dismiss(); if (archived) navigation(false); else newChat();
    });
    create.setTextSize(16);
    create.setTextColor(Color.WHITE);
    create.setBackground(shape(accent, 26));
    footer.addView(create, new LinearLayout.LayoutParams(-2, dp(52)));
    footer.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
    ImageButton preferences = icon(R.drawable.ic_settings, "设置", v -> { drawer.dismiss(); settings(); });
    preferences.setBackground(shape(0xFFECE6F0, 26));
    footer.addView(preferences, new LinearLayout.LayoutParams(dp(52), dp(52)));
    panel.addView(footer);
    drawer.setContentView(panel);
    drawer.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    drawer.getWindow().setGravity(Gravity.START);
    drawer.show();
    trackDialog(drawer);
    drawer.getWindow().setLayout(Math.min(dp(340), getResources().getDisplayMetrics().widthPixels - dp(48)), -1);
    io.execute(() -> {
      try {
        JSONArray sessions = Api.call("/sessions", new JSONObject().put("archived", archived)).getJSONArray("sessions");
        runOnUiThread(() -> {
          if (!drawer.isShowing()) return;
          for (int i = 0; i < sessions.length(); i++) {
            JSONObject session = sessions.optJSONObject(i);
            Button item = button(session.optString("title", "新对话"), v -> {
              drawer.dismiss();
              try { request("/select", new JSONObject().put("id", session.getString("id")), () -> revision = -1); }
              catch (Exception e) { toast(e.getMessage()); }
            });
            item.setTextSize(17);
            item.setPadding(dp(8), 0, dp(4), 0);
            item.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            item.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout entry = row();
            item.setBackground(new android.graphics.drawable.RippleDrawable(
              android.content.res.ColorStateList.valueOf(0x226750A4), shape(Color.TRANSPARENT, 16), null));
            if (currentId.equals(session.optString("id"))) {
              item.setTextColor(accent);
              item.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            }
            entry.addView(item, new LinearLayout.LayoutParams(0, dp(52), 1));
            ImageButton archive = icon(archived ? R.drawable.ic_restore : R.drawable.ic_archive, archived ? "恢复对话" : "归档对话", v -> {
              archiveSession(session, archived, () -> list.removeView(entry));
            });
            archive.setPadding(dp(15), dp(15), dp(15), dp(15));
            archive.setTooltipText(archived ? "恢复对话" : "归档对话");
            entry.addView(archive, new LinearLayout.LayoutParams(dp(48), dp(48)));
            ImageButton delete = icon(R.drawable.ic_delete, "删除对话", v -> { deleteSession(session, archived, () -> list.removeView(entry)); });
            delete.setPadding(dp(15), dp(15), dp(15), dp(15));
            delete.setImageTintList(android.content.res.ColorStateList.valueOf(0xFFC64040));
            delete.setTooltipText("删除对话");
            entry.addView(delete, new LinearLayout.LayoutParams(dp(48), dp(48)));
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, dp(56));
            rowParams.bottomMargin = dp(12);
            list.addView(entry, rowParams);
          }
        });
      } catch (Exception e) { toast(e.getMessage()); }
    });
  }

  void archiveSession(JSONObject session, boolean archived, Runnable updated) {
    try {
      request("/session/archive", new JSONObject().put("id", session.getString("id")).put("archived", !archived), () -> {
        revision = -1;
        Toast.makeText(this, archived ? "已恢复对话" : "已归档", Toast.LENGTH_SHORT).show();
        updated.run();
      });
    } catch (Exception e) { toast(e.getMessage()); }
  }

  void deleteSession(JSONObject session, boolean archived, Runnable updated) {
    AlertDialog confirmation = new AlertDialog.Builder(this).setTitle("删除对话？")
      .setMessage("将永久删除这段聊天记录，无法恢复。文件库中的文件会保留。")
      .setNegativeButton("取消", null)
      .setPositiveButton("删除", (dialog, which) -> {
        try {
          request("/session/delete", new JSONObject().put("id", session.getString("id")), () -> {
            revision = -1;
            Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
            updated.run();
          });
        } catch (Exception e) { toast(e.getMessage()); }
      }).create();
    confirmation.show();
    styleDialog(confirmation, true);
  }

  GradientDrawable shape(int color, int radius) {
    return ui.shape(color, radius);
  }

  GradientDrawable outline(int color, int radius) {
    GradientDrawable d = shape(color, radius);
    d.setStroke(dp(1), Color.rgb(230, 232, 239));
    return d;
  }

  void toast(String text) {
    runOnUiThread(() -> {
      if (isFinishing() || isDestroyed()) return;
      AlertDialog dialog = new AlertDialog.Builder(this).setTitle("提示")
        .setMessage(text).setPositiveButton("知道了", null).create();
      dialog.show(); styleDialog(dialog, false);
    });
  }

  interface Success {
    void run() throws Exception;
  }

  void request(String route, JSONObject body, Success done) {
    io.execute(() -> {
      try {
        Api.call(route, body);
        runOnUiThread(() -> {
          try {
            done.run();
          } catch (Exception e) {
            toast(e.getMessage());
          }
        });
      } catch (Exception e) {
        toast(e.getMessage());
      }
    });
  }

  void send() {
    if (busy) {
      request("/cancel", new JSONObject(), () -> {});
      return;
    }
    String text = input.getText().toString().trim();
    if (text.isEmpty()) return;
    try {
      startService(new Intent(this, HarnessService.class));
      request("/send", new JSONObject().put("text", text), () -> {
        input.setText("");
        input.requestFocus();
        busy = true;
        send.setImageResource(R.drawable.ic_stop);
      });
    } catch (Exception e) {
      toast(e.getMessage());
    }
  }

  String content(JSONArray blocks) {
    StringBuilder b = new StringBuilder();
    if (blocks == null) return "";
    for (int i = 0; i < blocks.length(); i++) {
      JSONObject v = blocks.optJSONObject(i);
      if (v != null && "text".equals(v.optString("type"))) b.append(
        v.optString("text")
      ).append('\n');
    }
    return b.toString().trim();
  }

  void card(String who, String text, boolean user, boolean small) {
    if (text.trim().isEmpty()) return;
    LinearLayout card = new LinearLayout(this);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setPadding(dp(14), dp(12), dp(14), dp(12));
    card.setBackground(ui.bubble(user ? 0xFFE8DEF8 : UiStyle.LOW, user));
    TextView name = label(who, 11, muted);
    name.setTypeface(null, Typeface.BOLD);
    if (small) card.addView(name);
    TextView body = label(text, small ? 12 : 16, ink);
    body.setPadding(0, small ? dp(6) : 0, 0, 0);
    body.setTextIsSelectable(true);
    body.setLineSpacing(dp(3), 1);
    body.setMaxWidth(Math.max(dp(96), (int)(getResources().getDisplayMetrics().widthPixels * 0.82f) - dp(60)));
    card.addView(body, new LinearLayout.LayoutParams(-2, -2));
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
    params.gravity = user ? Gravity.END : Gravity.START;
    params.bottomMargin = dp(14);
    messages.addView(card, params);
  }

  void render(JSONObject state) {
    if (revision == -1) android.util.Log.i("DSH-Lifecycle", "Core state reachable");
    boolean changed = !currentId.equals(state.optString("id"));
    long next = state.optLong("revision");
    busy = state.optBoolean("busy");
    send.setImageResource(busy ? R.drawable.ic_stop : R.drawable.ic_send);
    send.setContentDescription(busy ? "停止任务" : "发送消息");
    status.setText((busy ? "正在处理 · " : "") + state.optString("model"));
    if (!changed && next == revision) return;
    boolean atBottom =
      scroll.getChildAt(0).getHeight() -
        scroll.getHeight() -
        scroll.getScrollY() <
      dp(100);
    currentId = state.optString("id");
    revision = next;
    messages.removeAllViews();
    JSONArray events = state.optJSONArray("events");
    boolean any = false;
    chatTitle.setText("新对话");
    if (events != null) for (int i = 0; i < events.length(); i++) {
      JSONObject event = events.optJSONObject(i);
      if (event == null) continue;
      JSONObject d = event.optJSONObject("data");
      if (d == null) continue;
      String type = event.optString("type");
      if (type.equals("user/message")) {
        card("你", content(d.optJSONArray("content")), true, false);
        if (!any) chatTitle.setText(content(d.optJSONArray("content")));
        any = true;
      } else if (type.equals("assistant/message")) {
        JSONObject m = d.optJSONObject("message");
        if (m != null) card(
          "DSH",
          content(m.optJSONArray("content")),
          false,
          false
        );
      } else if (type.equals("tool/call")) {
        String name = d.optString("name");
        if (name.isEmpty() && d.optJSONObject("call") != null) name = d
          .optJSONObject("call")
          .optString("name");
        card("工具", name.isEmpty() ? "执行工具…" : name, false, true);
      } else if (type.equals("tool/result")) {
        JSONObject m = d.optJSONObject("message");
        if (m != null && m.optBoolean("isError")) card(
          "工具反馈",
          content(m.optJSONArray("content")),
          false,
          true
        );
      }
    }
    String live = state.optString("live");
    if (!live.isEmpty()) card("DSH · 正在回答", live, false, false);
    else if (busy) card(
      "处理中",
      state.optString("liveReasoning").isEmpty()
        ? "正在请求模型或执行工具…"
        : "正在思考…",
      false,
      true
    );
    if (!state.optString("lastError").isEmpty()) card(
      "任务状态",
      state.optString("lastError"),
      false,
      true
    );
    if (!any && !busy) scroll.post(() -> scroll.scrollTo(0, 0));
    else if (atBottom || changed) scroll.post(() ->
      scroll.scrollTo(0, Math.max(0, messages.getHeight() - scroll.getHeight()))
    );
  }

  @Override
  public void onBackPressed() {
    if (modelPopup != null && modelPopup.isShowing()) { modelPopup.dismiss(); return; }
    if (!dialogs.isEmpty()) {
      dialogs.peek().dismiss();
      return;
    }
    if (!pageHistory.isEmpty()) {
      Runnable cleanup = pageCleanup.remove(currentPage);
      if (cleanup != null) cleanup.run();
      currentPage = pageHistory.pop();
      settingsPage = currentPage != chatRoot;
      setContentView(currentPage);
      currentPage.requestApplyInsets();
    } else moveTaskToBack(true);
  }

  LinearLayout showPage(String title, LinearLayout body) {
    ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
      .hideSoftInputFromWindow(input.getWindowToken(), 0);
    LinearLayout page = new LinearLayout(this);
    page.setOrientation(LinearLayout.VERTICAL);
    page.setBackgroundColor(bg);
    page.setPadding(dp(16), dp(12), dp(16), dp(12));
    LinearLayout header = row();
    ImageButton back = icon(R.drawable.ic_back, "返回", v -> onBackPressed());
    back.setBackground(new android.graphics.drawable.RippleDrawable(
      android.content.res.ColorStateList.valueOf(0x226750A4), shape(0xFFECE6F0, 24), null));
    header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
    page.addView(header);
    TextView heading = label(title, 32, ink);
    heading.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
    heading.setPadding(0, dp(12), 0, dp(28));
    body.addView(heading, 0);
    ScrollView content = new ScrollView(this);
    content.setFillViewport(true);
    content.addView(body);
    page.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
    pageHistory.push(currentPage);
    currentPage = page;
    settingsPage = true;
    setContentView(page);
    if (Build.VERSION.SDK_INT >= 30) page.setOnApplyWindowInsetsListener((v, insets) -> {
      android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
      page.setPadding(dp(16), bars.top + dp(8), dp(16),
        Math.max(bars.bottom, insets.getInsets(WindowInsets.Type.ime()).bottom) + dp(8));
      return insets;
    });
    page.requestApplyInsets();
    return page;
  }

  static Thread closeModelClient(okhttp3.OkHttpClient client) {
    Thread cleanup = new Thread(() -> {
      client.dispatcher().cancelAll();
      client.connectionPool().evictAll();
      client.dispatcher().executorService().shutdown();
    }, "DSH-model-cleanup");
    cleanup.start();
    return cleanup;
  }

  LinearLayout.LayoutParams spacedControl() {
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(56));
    lp.topMargin = dp(8);
    lp.bottomMargin = dp(12);
    return lp;
  }

  void addAction(LinearLayout parent, Button action, boolean primary) {
    action.setTextSize(16);
    action.setTextColor(primary ? Color.WHITE : ink);
    action.setBackground(new android.graphics.drawable.RippleDrawable(
      android.content.res.ColorStateList.valueOf(0x226750A4),
      shape(primary ? accent : 0xFFECE6F0, 16), null));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52));
    lp.topMargin = dp(12);
    lp.bottomMargin = dp(12);
    parent.addView(action, lp);
  }

  void settingSection(LinearLayout body, String title) {
    TextView label = label(title, 14, accent);
    label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    label.setPadding(dp(4), dp(20), dp(4), dp(12));
    body.addView(label);
  }

  void settingEntry(LinearLayout body, int glyph, String title, String detail,
      boolean first, boolean last, View.OnClickListener click) {
    LinearLayout entry = row();
    entry.setPadding(dp(16), dp(16), dp(16), dp(16));
    entry.setMinimumHeight(dp(80));
    GradientDrawable surface = shape(0xFFECE6F0, 4);
    float top = dp(first ? 24 : 4), bottom = dp(last ? 24 : 4);
    surface.setCornerRadii(new float[] {top, top, top, top, bottom, bottom, bottom, bottom});
    entry.setBackground(new android.graphics.drawable.RippleDrawable(
      android.content.res.ColorStateList.valueOf(0x226750A4), surface, null));
    entry.setOnClickListener(click);
    entry.setFocusable(true);
    entry.setContentDescription(title);
    ImageView symbol = new ImageView(this);
    symbol.setImageResource(glyph);
    symbol.setImageTintList(android.content.res.ColorStateList.valueOf(ink));
    LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(26), dp(26));
    iconParams.rightMargin = dp(20);
    entry.addView(symbol, iconParams);
    LinearLayout text = new LinearLayout(this);
    text.setOrientation(LinearLayout.VERTICAL);
    text.addView(label(title, 18, ink));
    TextView subtitle = label(detail, 14, muted);
    subtitle.setPadding(0, dp(4), 0, 0);
    if (!detail.isEmpty()) text.addView(subtitle);
    entry.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
    TextView arrow = label("›", 24, muted);
    arrow.setPadding(dp(12), 0, 0, 0);
    entry.addView(arrow);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.bottomMargin = dp(4);
    body.addView(entry, lp);
  }

  void settings() {
    LinearLayout entries = new LinearLayout(this);
    entries.setOrientation(LinearLayout.VERTICAL);
    entries.setPadding(dp(4), dp(12), dp(4), dp(20));
    settingSection(entries, "模型与服务");
    settingEntry(entries, R.drawable.ic_model, "DeepSeek", "模型、API Key 与工具设置",
      true, true, v -> modelSettings(false));
    settingSection(entries, "数据管理");
    settingEntry(entries, R.drawable.ic_archive, "已归档对话", "查看和恢复归档的聊天",
      true, false, v -> navigation(true));
    settingEntry(entries, R.drawable.ic_folder, "文件库", "管理导入资料和生成的文件",
      false, true, v -> files());
    settingSection(entries, "应用");
    settingEntry(entries, R.drawable.ic_refresh, "应用更新", "自动更新与版本检查",
      true, true, v -> updateSettings());
    showPage("设置", entries);
  }

  void updateSettings() {
    LinearLayout entries = new LinearLayout(this);
    entries.setOrientation(LinearLayout.VERTICAL);
    entries.setPadding(dp(4), dp(12), dp(4), dp(20));
    String version="";
    try { version=getPackageManager().getPackageInfo(getPackageName(),0).versionName; } catch (Exception ignored) {}
    settingSection(entries, "当前版本 " + version);
    LinearLayout update = row();
    update.setPadding(dp(16), dp(16), dp(16), dp(16));
    update.setBackground(shape(UiStyle.SURFACE, 24));
    LinearLayout wording = new LinearLayout(this); wording.setOrientation(LinearLayout.VERTICAL);
    wording.addView(label("自动更新", 18, ink));
    TextView detail=label("每天首次打开时检查并下载，安装需系统授权", 13, muted);
    detail.setPadding(0,dp(4),dp(8),0); wording.addView(detail);
    update.addView(wording,new LinearLayout.LayoutParams(0,-2,1));
    Switch autoUpdate=new Switch(this); autoUpdate.setContentDescription("自动更新");
    autoUpdate.setChecked(AppUpdates.enabled(this));
    autoUpdate.setOnCheckedChangeListener((v, checked) -> AppUpdates.setEnabled(this, checked));
    update.addView(autoUpdate,new LinearLayout.LayoutParams(dp(56),dp(48)));
    entries.addView(update);
    Button installUpdate=button("安装已下载的更新",v -> startActivity(new Intent(this,UpdateInstallActivity.class)));
    installUpdate.setVisibility(AppUpdates.prefs(this).getLong("ready",0)>0 && AppUpdates.apk(this).exists() ? View.VISIBLE : View.GONE);
    Button checkUpdate=button("检查更新",null);
    checkUpdate.setOnClickListener(v -> {
      long started=System.currentTimeMillis();
      checkUpdate.setEnabled(false); checkUpdate.setText("正在检查…");
      AppUpdates.checkAsync(this,true,() -> runOnUiThread(() -> {
        if (isFinishing() || isDestroyed()) return;
        checkUpdate.setEnabled(true); checkUpdate.setText("检查更新");
        installUpdate.setVisibility(AppUpdates.prefs(this).getLong("ready",0)>0 && AppUpdates.apk(this).exists() ? View.VISIBLE : View.GONE);
        if (AppUpdates.prefs(this).getLong("success",0)>=started) {
          if (AppUpdates.prefs(this).getLong("ready",0)>0 && AppUpdates.apk(this).exists())
            checkUpdate.setText("新版本已下载");
          else checkUpdate.setText("当前已是最新版本");
        }
      }));
    });
    addAction(entries,checkUpdate,false);
    addAction(entries,installUpdate,false);
    showPage("应用更新", entries);
  }

  void modelSettings() { modelSettings(false); }

  void modelSettings(boolean quick) {
    try { new ModelSettingsPanel(this, quick).open(); }
    catch (Exception e) { toast(e.getMessage()); }
  }

  Dialog bottomPanel(String title, LinearLayout content) {
    Dialog dialog = new Dialog(this);
    LinearLayout panel = new LinearLayout(this);
    panel.setOrientation(LinearLayout.VERTICAL);
    panel.setPadding(dp(20), dp(12), dp(20), dp(20));
    panel.setBackground(shape(bg, 28));
    View grip = new View(this);
    grip.setBackground(shape(0xFFCAC4D0, 2));
    LinearLayout.LayoutParams handle = new LinearLayout.LayoutParams(dp(32), dp(4));
    handle.gravity = Gravity.CENTER_HORIZONTAL; handle.bottomMargin = dp(20);
    panel.addView(grip, handle);
    TextView heading = label(title, 22, ink);
    heading.setPadding(dp(4), 0, 0, dp(12));
    panel.addView(heading);
    ScrollView scroll = new ScrollView(this); scroll.setFillViewport(false);
    scroll.addView(content); panel.addView(scroll);
    dialog.setContentView(panel);
    Window window = dialog.getWindow();
    window.setBackgroundDrawableResource(android.R.color.transparent);
    window.setGravity(Gravity.BOTTOM);
    window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    dialog.show();
    window.setLayout(-1, -2);
    trackDialog(dialog);
    panel.post(() -> {
      int max = (int)(getResources().getDisplayMetrics().heightPixels * 0.8f);
      if (panel.getHeight() > max) scroll.getLayoutParams().height =
        Math.max(dp(100), max - (panel.getHeight() - scroll.getHeight()));
      panel.requestLayout();
    });
    return dialog;
  }

  void styleDialog(AlertDialog dialog, boolean destructive) {
    dialog.getWindow().setBackgroundDrawable(shape(bg, 28));
    for (int which : new int[]{AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_POSITIVE}) {
      Button action = dialog.getButton(which);
      if (action == null || action.getVisibility() != View.VISIBLE) continue;
      ui.dialogAction(action, destructive && which == AlertDialog.BUTTON_POSITIVE);
      if (action.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) action.getLayoutParams();
        lp.setMarginStart(dp(8)); action.setLayoutParams(lp);
      }
    }
    trackDialog(dialog);
  }

  EditText secretField(LinearLayout box, String title, String value) {
    EditText edit = field(box, title, value, true);
    int index = box.indexOfChild(edit);
    ViewGroup.LayoutParams spacing = edit.getLayoutParams();
    box.removeView(edit);
    LinearLayout container = row();
    container.setBackground(outline(0xFFF7F8FC, 12));
    edit.setBackgroundColor(Color.TRANSPARENT);
    container.addView(edit, new LinearLayout.LayoutParams(0, -2, 1));
    ImageButton visibility = icon(R.drawable.ic_eye, "显示 API Key", null);
    visibility.setOnClickListener(v -> {
      int start = edit.getSelectionStart(), end = edit.getSelectionEnd();
      boolean hidden = edit.getTransformationMethod() != null;
      edit.setTransformationMethod(hidden ? null :
        android.text.method.PasswordTransformationMethod.getInstance());
      if (start >= 0 && end >= 0) edit.setSelection(
        Math.min(start, edit.length()), Math.min(end, edit.length()));
      visibility.setImageResource(hidden ? R.drawable.ic_eye_off : R.drawable.ic_eye);
      visibility.setContentDescription(hidden ? "隐藏 API Key" : "显示 API Key");
    });
    container.addView(visibility, new LinearLayout.LayoutParams(dp(48), dp(48)));
    box.addView(container, index, spacing);
    return edit;
  }

  EditText field(LinearLayout box, String title, String value, boolean secret) {
    box.addView(label(title, 12, muted));
    EditText edit = new EditText(this);
    edit.setTextSize(14);
    edit.setSingleLine(true);
    edit.setText(value);
    if (secret) edit.setInputType(
      InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
    );
    edit.setTextColor(ink);
    edit.setPadding(dp(14), dp(12), dp(14), dp(12));
    edit.setBackground(outline(0xFFF7F8FC, 12));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.topMargin = dp(8); lp.bottomMargin = dp(18);
    box.addView(edit, lp);
    return edit;
  }

  interface Pick { void select(int index); }

  void sheet(String title, String subtitle, String[] items, Pick pick) {
    LinearLayout body = new LinearLayout(this);
    body.setOrientation(LinearLayout.VERTICAL);
    body.setPadding(dp(8), dp(16), dp(8), dp(20));
    if (!subtitle.isEmpty()) {
      TextView description = label(subtitle, 13, muted);
      description.setPadding(dp(4), 0, dp(4), dp(12));
      body.addView(description);
    }
    for (int i = 0; i < items.length; i++) {
      final int index = i;
      String[] text = items[i].split("\n", 2);
      settingEntry(body, title.equals("文件库") ? R.drawable.ic_folder : R.drawable.ic_add,
        text[0], text.length > 1 ? text[1] : "", i == 0, i == items.length - 1,
        v -> pick.select(index));
    }
    if (items.length == 0) body.addView(label("这里还是空的", 16, muted));
    showPage(title, body);
  }

  void returnToChat() {
    while (!pageHistory.isEmpty()) onBackPressed();
  }

  void archivedPage() {
    LinearLayout body = new LinearLayout(this);
    body.setOrientation(LinearLayout.VERTICAL);
    body.setPadding(dp(8), dp(16), dp(8), dp(20));
    View page = showPage("已归档对话", body);
    io.execute(() -> {
      try {
        JSONArray sessions = Api.call("/sessions", new JSONObject().put("archived", true)).getJSONArray("sessions");
        runOnUiThread(() -> {
          if (currentPage != page) return;
          if (sessions.length() == 0) body.addView(label("暂无归档对话", 16, muted));
          for (int i = 0; i < sessions.length(); i++) {
            JSONObject session = sessions.optJSONObject(i);
            LinearLayout entry = row();

            TextView title = label(session.optString("title", "新对话"), 17, ink);
            title.setPadding(dp(16), 0, dp(8), 0);
            title.setSingleLine(true);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            entry.addView(title, new LinearLayout.LayoutParams(0, dp(56), 1));
            ImageButton restore = icon(R.drawable.ic_restore, "恢复对话", v -> {
              archiveSession(session, true, () -> body.removeView(entry));
            });
            restore.setPadding(dp(15), dp(15), dp(15), dp(15));
            entry.addView(restore, new LinearLayout.LayoutParams(dp(48), dp(48)));
            ImageButton delete = icon(R.drawable.ic_delete, "删除对话", v -> {
              deleteSession(session, true, () -> body.removeView(entry));
            });
            delete.setImageTintList(android.content.res.ColorStateList.valueOf(0xFFC64040));
            delete.setPadding(dp(15), dp(15), dp(15), dp(15));
            entry.addView(delete, new LinearLayout.LayoutParams(dp(48), dp(48)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(56));
            lp.bottomMargin = dp(12);
            body.addView(entry, lp);
          }
        });
      } catch (Exception e) { toast(e.getMessage()); }
    });
  }

  void history() {
    io.execute(() -> {
      try {
        JSONArray rows = Api.call("/sessions", new JSONObject()).getJSONArray("sessions");
        String[] labels = new String[rows.length()];
        for (int i = 0; i < labels.length; i++) labels[i] = rows.getJSONObject(i).getString("title");
        runOnUiThread(() -> sheet("对话记录", "继续之前的想法", labels, which -> {
          try { request("/select", new JSONObject().put("id", rows.getJSONObject(which).getString("id")), () -> revision = -1); }
          catch (Exception e) { toast(e.getMessage()); }
        }));
      } catch (Exception e) { toast(e.getMessage()); }
    });
  }

  void importFile() {
    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
      .addCategory(Intent.CATEGORY_OPENABLE)
      .setType("*/*");
    startActivityForResult(intent, IMPORT);
  }

  void files() {
    View origin = currentPage;
    io.execute(() -> {
      try {
        JSONArray rows = new FileTools(this).list();
        String[] labels = new String[rows.length()];
        for (int i = 0; i < labels.length; i++) {
          JSONObject f = rows.getJSONObject(i);
          labels[i] = f.getString("path") + "\n" + Math.max(1, f.getLong("bytes") / 1024) + " KB";
        }
        runOnUiThread(() -> { if (currentPage != origin || isFinishing()) return; sheet("文件库", "导入的资料和生成的文件都在这里", labels, which -> {
          try {
            String path = rows.getJSONObject(which).getString("path");
            sheet(new File(path).getName(), "选择如何使用这份文件", new String[] { "添加到消息", "导出到手机" }, action -> {
              if (action == 0) { returnToChat(); input.append("\n请处理工作区文件：" + path + "\n"); }
              else {
                exportPath = path;
                startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                  .setType("application/octet-stream").putExtra(Intent.EXTRA_TITLE, new File(path).getName()), EXPORT);
              }
            });
          } catch (Exception e) { toast(e.getMessage()); }
        }); });
      } catch (Exception e) { toast(e.getMessage()); }
    });
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (result != RESULT_OK || data == null || data.getData() == null) return;
    Uri uri = data.getData();
    io.execute(() -> {
      try {
        FileTools tools = new FileTools(this);
        if (request == IMPORT) {
          String name = "导入文件.txt";
          try (
            Cursor c = getContentResolver().query(
              uri,
              new String[] { OpenableColumns.DISPLAY_NAME },
              null,
              null,
              null
            )
          ) {
            if (c != null && c.moveToFirst()) name = c.getString(0);
          }
          String saved;
          try (InputStream in = getContentResolver().openInputStream(uri)) {
            saved = tools.importFile(in, name);
          }
          runOnUiThread(() -> {
            input.append("\n请处理工作区文件：" + saved + "\n");
            Toast.makeText(this, "已导入 " + saved, Toast.LENGTH_SHORT).show();
          });
        } else if (request == EXPORT) {
          try (
            InputStream in = new FileInputStream(tools.resolve(exportPath));
            OutputStream out = getContentResolver().openOutputStream(uri, "wt")
          ) {
            if (out == null) throw new IOException("无法打开导出位置");
            FileTools.copy(in, out, FileTools.MAX_BYTES);
          }
          runOnUiThread(() ->
            Toast.makeText(this, "文件已导出", Toast.LENGTH_SHORT).show()
          );
        }
      } catch (Exception e) {
        toast(e.getMessage());
      }
    });
  }
}
