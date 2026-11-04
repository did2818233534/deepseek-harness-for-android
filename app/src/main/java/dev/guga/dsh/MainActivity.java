package dev.guga.dsh;
import dev.guga.dsh.settings.Settings;
import dev.guga.dsh.files.GalleryPicker;
import dev.guga.dsh.files.FileTools;
import dev.guga.updates.ReleaseUpdater;
import dev.guga.updates.UpdateButton;

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
    EXPORT = 11, IMPORT_IMAGE = 13;
  final int ink = Color.rgb(30, 34, 43),
    muted = Color.rgb(127, 132, 144),
    accent = Color.rgb(103, 80, 164),
    bg = Color.rgb(253, 251, 255);
  final SettingsPages settingsPages = new SettingsPages(this);
  final ConversationDrawer conversationDrawer = new ConversationDrawer(this);
  final UiStyle ui = new UiStyle(this);
  final Map<Dialog, Runnable> dialogCleanup = new HashMap<>();
  volatile String stateStamp="";
  io.noties.markwon.Markwon markdownRenderer;
  final ExecutorService io = Executors.newSingleThreadExecutor();
  final ExecutorService coreIo = Executors.newSingleThreadExecutor();
  final Handler handler = new Handler(Looper.getMainLooper());
  LinearLayout chatRoot, composer;
  MessageActions messageActions;
  Attachments attachments;
  boolean sending;
  int attachmentPickerGeneration;
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
  final Map<String, ChatTurnView> turnViews = new LinkedHashMap<>();
  TextView turnError;
  static final int HISTORY_BATCH=6;
  JSONArray transcriptTurns=new JSONArray();
  int historyStart,historyGeneration;
  boolean historyLoading;
  final TranscriptViews transcriptViews=new TranscriptViews(this);
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
        coreIo.execute(() -> {
          try {
            JSONObject state = Api.call("/state", new JSONObject().put("compact",true).put("stamp",stateStamp));
            runOnUiThread(() -> { if (visible&&!state.optBoolean("unchanged")) render(state); });
          } catch (Exception e) {
            // Startup/reconnection is internal; keep the model label stable.
          } finally {
            polling = false;
          }
        });
      }
      handler.postDelayed(this, busy ? 100 : 700);
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
    scroll = new HistoryScrollView(this);
    scroll.setFillViewport(true);
    scroll.setVerticalScrollBarEnabled(false);
    messages = new LinearLayout(this);
    messages.setOrientation(LinearLayout.VERTICAL);
    messages.setPadding(dp(4), dp(24), dp(4), dp(16));
    scroll.addView(messages);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    composer = new LinearLayout(this);
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
    input.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){if(send!=null)updateSendIcon();}public void afterTextChanged(android.text.Editable e){}});
    attachments=new Attachments(this);
    composer.addView(attachments.tray,new LinearLayout.LayoutParams(-1,-2));
    composer.addView(input, new LinearLayout.LayoutParams(-1, -2));
    LinearLayout controls = row();
    controls.addView(icon(R.drawable.ic_add, "添加附件", v -> attachmentMenu()), new LinearLayout.LayoutParams(dp(48), dp(48)));
    controls.addView(icon(R.drawable.ic_model, "模型设置", v -> modelSettings(true)), new LinearLayout.LayoutParams(dp(48), dp(48)));
    controls.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
    send = icon(R.drawable.ic_send, "发送消息", v -> send());
    send.setImageTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
    send.setBackground(ui.surface(accent, 24));
    controls.addView(send, new LinearLayout.LayoutParams(dp(48), dp(48)));
    composer.addView(controls);
    root.addView(composer);
    messageActions=new MessageActions(this,root,composer);
    if (saved != null) {
      input.setText(saved.getString("draft", ""));
      attachments.restore(saved.getString("attachments","[]"));
      attachments.generation=saved.getInt("attachmentGeneration",0);
      attachmentPickerGeneration=saved.getInt("attachmentPickerGeneration",0);
      exportPath = saved.getString("exportPath", "");
      messageActions.pendingExport=saved.getString("pendingMarkdown", "");
    }
    if(saved==null) IncomingFiles.receive(this,getIntent());
    if (
      Build.VERSION.SDK_INT >= 33 &&
      checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
        android.content.pm.PackageManager.PERMISSION_GRANTED
    ) requestPermissions(
      new String[] { android.Manifest.permission.POST_NOTIFICATIONS },
      1
    );
  }

  @Override protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);setIntent(intent);IncomingFiles.receive(this,intent);
  }

  @Override
  public void onSaveInstanceState(Bundle out) {
    out.putString("draft", input.getText().toString());
    out.putString("attachments",attachments.snapshot().toString());
    out.putInt("attachmentGeneration",attachments.generation);
    out.putInt("attachmentPickerGeneration",attachmentPickerGeneration);
    out.putString("exportPath", exportPath);
    out.putString("pendingMarkdown",messageActions.pendingExport);
    super.onSaveInstanceState(out);
  }

  @Override
  public void onResume() {
    super.onResume();
    ReleaseUpdater.checkAutomatically(this);
    visible = true;
    handler.post(poll);
  }

  @Override
  public void onPause() {
    visible = false;
    handler.removeCallbacks(poll);
    super.onPause();
  }

  @Override public void onTrimMemory(int level){super.onTrimMemory(level);if(level>=TRIM_MEMORY_RUNNING_LOW)transcriptViews.clear();}

  @Override
  public void onDestroy() {
    transcriptViews.clear();
    io.shutdown();coreIo.shutdown();
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
      android.content.res.ColorStateList.valueOf(0x226750A4), shape(UiStyle.SURFACE, UiStyle.CARD), shape(Color.WHITE, 24)));
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
    b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x206750A4), shape(Color.TRANSPARENT, 24), shape(Color.WHITE, 24)));
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

  void navigation(boolean archived) { conversationDrawer.navigation(archived); }
  void archiveSession(JSONObject session, boolean archived, Runnable updated) {
    conversationDrawer.archiveSession(session, archived, updated);
  }
  void deleteSession(JSONObject session, boolean archived, Runnable updated) {
    conversationDrawer.deleteSession(session, archived, updated);
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
    coreIo.execute(() -> {
      try {
        Api.call(route, body);
        JSONObject selectedState=route.equals("/select")?Api.call("/state",new JSONObject().put("compact",true)):null;
        runOnUiThread(() -> {
          try {
            if(route.equals("/select")){attachments.reset();input.setText("");}
            if(route.equals("/send")||route.equals("/steer")){sending=false;send.setEnabled(attachments.importing==0);}
            done.run();
            if(selectedState!=null&&visible)render(selectedState);
          } catch (Exception e) {
            toast(e.getMessage());
          }
        });
      } catch (Exception e) {
        if(route.equals("/send")||route.equals("/steer"))runOnUiThread(()->{sending=false;send.setEnabled(attachments.importing==0);});
        toast(e.getMessage());
      }
    });
  }

  void updateSendIcon(){boolean steer=busy&&!input.getText().toString().trim().isEmpty();send.setImageResource(busy&&!steer?R.drawable.ic_stop:R.drawable.ic_send);send.setContentDescription(steer?"引导":busy?"停止任务":"发送消息");send.setTooltipText(send.getContentDescription());}
  void send() {
    if(sending||attachments.importing>0)return;
    if (busy && input.getText().toString().trim().isEmpty() && attachments.snapshot().length()==0) {
      request("/cancel", new JSONObject(), () -> {});
      return;
    }
    String text = input.getText().toString().trim();
    JSONArray attached=attachments.snapshot();
    if (text.isEmpty() && attached.length()==0) return;
    try {
      startService(new Intent(this, HarnessService.class));
      sending=true;send.setEnabled(false);
      request(busy?"/steer":"/send", new JSONObject().put("text", text).put("attachments",attached), () -> {
        if(input.getText().toString().trim().equals(text))input.setText("");
        attachments.clearSent(attached);
        input.requestFocus();
        busy = true;
        send.setImageResource(R.drawable.ic_stop);
      });
    } catch (Exception e) {
      sending=false;send.setEnabled(attachments.importing==0);
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
    stateStamp=state.optString("stamp");
    if (revision == -1) android.util.Log.i("DSH-Lifecycle", "Core state reachable");
    boolean changed = !currentId.equals(state.optString("id"));
    long next = state.optLong("revision");
    busy = state.optBoolean("busy");
    updateSendIcon();
    status.setText((busy ? "正在处理 · " : "") + state.optString("model")+(state.optJSONArray("pendingGuidance")!=null&&state.optJSONArray("pendingGuidance").length()>0?" · 引导已排队":""));
    if (!changed && next == revision && !busy) return;
    boolean atBottom =
      scroll.getChildAt(0).getHeight() -
        scroll.getHeight() -
        scroll.getScrollY() <
      dp(100);
    currentId = state.optString("id");
    revision = next;
    if (state.optJSONArray("turns") != null) {
      renderTurns(state, changed, atBottom);
      return;
    }
    turnViews.clear(); turnError=null;
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

  void renderTurns(JSONObject state, boolean changed, boolean atBottom) {
    JSONArray turns=state.optJSONArray("turns");
    if(changed){
      historyGeneration++;historyLoading=false;
      ((HistoryScrollView)scroll).resetHistoryGesture();
      transcriptViews.activate(state.optString("id"));
      historyStart=Math.max(0,turns.length()-HISTORY_BATCH);
    }else if(turns.length()<transcriptTurns.length())historyStart=Math.max(0,turns.length()-HISTORY_BATCH);
    transcriptTurns=turns;
    chatTitle.setText((!state.optString("title").isEmpty()?state.optString("title"):turns.length()==0?"新对话":turns.optJSONObject(0).optString("userText","新对话"))+(state.optBoolean("isBranch")?" · 分支":""));
    renderVisibleTurns();
    String error=state.optString("lastError");
    if(turnError==null) {turnError=label("",14,UiStyle.DANGER); turnError.setPadding(dp(14),dp(8),dp(14),dp(14));messages.addView(turnError);}
    ChatTurnView.text(turnError,readableError(error));
    if(fileLibrary!=null && currentPage==fileLibrary.page && fileLibrary.selecting && !fileLibrary.deleting){fileLibrary.setSelecting(false);return;}
    if(messageActions.active())messageActions.updateForkEnabled();
    if(turns.length()==0&&!busy) scroll.post(() -> scroll.scrollTo(0,0));
    else if((atBottom||changed)&&!historyLoading){int generation=historyGeneration;scroll.post(() -> {if(generation==historyGeneration&&!historyLoading)scroll.scrollTo(0,Math.max(0,messages.getHeight()-scroll.getHeight()));});}
  }

  void renderVisibleTurns(){
    LinkedHashMap<String,ChatTurnView> ordered=new LinkedHashMap<>();
    for(int n=historyStart;n<transcriptTurns.length();n++){
      JSONObject turn=transcriptTurns.optJSONObject(n);if(turn==null)continue;
      String id=turn.optString("id",String.valueOf(n));ChatTurnView view=turnViews.get(id);
      if(view==null)view=new ChatTurnView(this);
      ordered.put(id,view);view.update(turn);
    }
    for(String id:new ArrayList<>(turnViews.keySet()))if(!ordered.containsKey(id))messages.removeView(turnViews.get(id));
    int index=0;for(ChatTurnView view:ordered.values()){
      if(view.getParent()==null)messages.addView(view,index,new LinearLayout.LayoutParams(-1,-2));
      else if(messages.indexOfChild(view)!=index){messages.removeView(view);messages.addView(view,index);}
      index++;
    }
    turnViews.clear();turnViews.putAll(ordered);
  }

  boolean loadOlderTurns(){
    if(historyLoading||historyStart<=0||turnViews.isEmpty()||currentPage!=chatRoot)return false;
    historyLoading=true;int generation=historyGeneration;
    View anchor=turnViews.values().iterator().next();int offset=anchor.getTop()-scroll.getScrollY();
    LinearLayout body=messages;
    body.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){
      public boolean onPreDraw(){
        if(body.getViewTreeObserver().isAlive())body.getViewTreeObserver().removeOnPreDrawListener(this);
        if(generation==historyGeneration&&messages==body){scroll.scrollTo(0,Math.max(0,anchor.getTop()-offset));historyLoading=false;}
        return true;
      }
    });
    historyStart=Math.max(0,historyStart-HISTORY_BATCH);renderVisibleTurns();body.requestLayout();
    return true;
  }

  static String readableError(String raw) {
    if(raw.isEmpty()) return "";
    try {
      JSONObject reason=new JSONObject(raw);
      if("max-tokens".equals(reason.optString("kind")))return "本轮达到模型输出上限，已保留收到的内容。可发送“继续”或降低思考强度后重试。";
      JSONObject error=reason.optJSONObject("error");
      String message=error==null?reason.optString("reason",reason.optString("message","")):error.optString("message");
      if(message.isEmpty()) return "本轮未完成，请重试。";
      if(message.contains("tool-call-delta")) return "工具调用数据不完整，请重新发送消息。";
      return "本轮未完成："+message;
    } catch(JSONException ignored) {return raw;}
  }

  @Override
  public void onBackPressed() {
    if (modelPopup != null && modelPopup.isShowing()) { modelPopup.dismiss(); return; }
    if (!dialogs.isEmpty()) {
      dialogs.peek().dismiss();
      return;
    }
    if(fileLibrary!=null && currentPage==fileLibrary.page && fileLibrary.selecting && !fileLibrary.deleting){fileLibrary.setSelecting(false);return;}
    if(messageActions.active() && currentPage==chatRoot) {messageActions.clear();return;}
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
      android.content.res.ColorStateList.valueOf(0x226750A4), shape(0xFFECE6F0, 24), shape(Color.WHITE, 24)));
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
      shape(primary ? accent : 0xFFECE6F0, 16), shape(Color.WHITE, 24)));
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

  // Settings screens use 4dp outer gutters, 16dp card insets and one trailing column.
  static final int SETTINGS_ACTION_DP=56;
  LinearLayout standaloneHelp(String title,int size,int color,String detail){
    LinearLayout heading=helpTitle(title,size,color,detail);
    heading.setPaddingRelative(0,0,dp(16)+dp(SETTINGS_ACTION_DP),0);
    return heading;
  }
  LinearLayout helpTitle(String title, int size, int color, String detail) {
    LinearLayout heading=row();
    TextView text=label(title,size,color);
    heading.addView(text,new LinearLayout.LayoutParams(0,-2,1));
    if(!detail.isEmpty()) {
      ImageButton help=icon(R.drawable.ic_help,title+"说明",v -> {
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(title).setMessage(detail)
          .setPositiveButton("知道了",null).create();
        dialog.show(); styleDialog(dialog,false);
      });
      help.setPadding(dp(15),dp(15),dp(15),dp(15));
      help.setImageTintList(android.content.res.ColorStateList.valueOf(muted));
      heading.addView(help,new LinearLayout.LayoutParams(dp(48),dp(48)));
    }
    return heading;
  }

  void settingEntry(LinearLayout body, int glyph, String title, String detail,
      boolean first, boolean last, View.OnClickListener click) {
    LinearLayout entry = row();
    entry.setPadding(dp(16), dp(8), dp(16), dp(8));
    entry.setMinimumHeight(dp(64));
    GradientDrawable surface = shape(0xFFECE6F0, 4);
    float top = dp(first ? 24 : 4), bottom = dp(last ? 24 : 4);
    surface.setCornerRadii(new float[] {top, top, top, top, bottom, bottom, bottom, bottom});
    entry.setBackground(new android.graphics.drawable.RippleDrawable(
      android.content.res.ColorStateList.valueOf(0x226750A4), surface, shape(Color.WHITE, 24)));
    entry.setOnClickListener(click);
    entry.setFocusable(true);
    entry.setContentDescription(title);
    ImageView symbol = new ImageView(this);
    symbol.setImageResource(glyph);
    symbol.setImageTintList(android.content.res.ColorStateList.valueOf(ink));
    LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(26), dp(26));
    iconParams.rightMargin = dp(20);
    entry.addView(symbol, iconParams);
    entry.addView(helpTitle(title,18,ink,detail), new LinearLayout.LayoutParams(0, -2, 1));
    TextView arrow = label("›", 24, muted);
    arrow.setGravity(Gravity.CENTER);
    entry.addView(arrow,new LinearLayout.LayoutParams(dp(SETTINGS_ACTION_DP),-2));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.bottomMargin = dp(4);
    body.addView(entry, lp);
  }

  void settings() { settingsPages.settings(); }
  void personalization() { settingsPages.personalization(); }
  void updateSettings() { settingsPages.updateSettings(); }

  void updateButtonStatus(Button button,String message) {
    ((UpdateButton)button).setStatusText(message);
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
    if (title.equals("API Key")) box.addView(standaloneHelp(title,12,muted,"更改自动保存。密钥通过 Android Keystore 加密保存在此设备，默认隐藏；点击眼睛图标可切换显示。"),new LinearLayout.LayoutParams(-1,-2));
    else box.addView(label(title, 12, muted));
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
    if (title.equals("文件库")) addFileCleanupSetting(body);
    if (!subtitle.isEmpty() && !title.equals("文件库")) {
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
    // Dismiss listeners are queued: remove dialogs now so navigation cannot spin.
    while (!dialogs.isEmpty()) dialogs.pop().dismiss();
    if(modelPopup!=null && modelPopup.isShowing()) modelPopup.dismiss();
    while (!pageHistory.isEmpty()) onBackPressed();
  }

  void archivedPage() {new ArchivedChatsPage(this).open();}

  void history() {
    coreIo.execute(() -> {
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

  void attachmentMenu() {
    LinearLayout choices = new LinearLayout(this); choices.setOrientation(LinearLayout.VERTICAL);
    Dialog[] panel = new Dialog[1];
    Button photo = button("图片", v -> {panel[0].dismiss(); importImage();});
    Button file = button("文件", v -> {panel[0].dismiss(); importFile();});
    Button[] buttons = {photo,file}; int[] icons = {R.drawable.ic_image,R.drawable.ic_folder};
    for(int n=0;n<buttons.length;n++) {
      android.graphics.drawable.Drawable icon=getDrawable(icons[n]);icon.setTint(accent);icon.setBounds(0,0,dp(24),dp(24));
      buttons[n].setCompoundDrawablesRelative(icon,null,null,null);buttons[n].setCompoundDrawablePadding(dp(12));buttons[n].setGravity(Gravity.CENTER_VERTICAL|Gravity.START);
      choices.addView(buttons[n],spacedControl());
    }
    panel[0]=bottomPanel("添加附件",choices);
  }
  Intent imagePickerIntent() { return GalleryPicker.preferred(this); }
  void importImage() {
    attachmentPickerGeneration=attachments.generation;
    if(attachments.pending.size()>=8){toast("每条消息最多添加 8 个附件");return;}
    Intent[] candidates={imagePickerIntent(),GalleryPicker.fallback(8-attachments.pending.size()),GalleryPicker.documentRequest()};
    for(Intent candidate:candidates){
      try{startActivityForResult(candidate,IMPORT_IMAGE);return;}
      catch(android.content.ActivityNotFoundException|SecurityException unavailable){/* Try another system-supported entry. */}
    }
    toast("没有可用的相册或图片选择器");
  }
  void importFile() {
    attachmentPickerGeneration=attachments.generation;
    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
      .addCategory(Intent.CATEGORY_OPENABLE)
      .setType("*/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
    startActivityForResult(intent, IMPORT);
  }

  void addFileCleanupSetting(LinearLayout body) {
    LinearLayout setting = row();
    setting.setPadding(dp(16),dp(12),dp(16),dp(12));
    setting.setBackground(shape(UiStyle.SURFACE,24));
    setting.addView(helpTitle("删除对话时清理关联文件",16,ink,"默认开启。删除对话时，同时清理它导入或生成的文件；其他对话仍引用的文件会保留。关闭后仅删除聊天记录。已导出到手机其他位置的文件不受影响。"),new LinearLayout.LayoutParams(0,-2,1));
    Settings prefs=new Settings(this);
    Switch toggle=new Switch(this);
    toggle.setContentDescription("删除对话时清理关联文件");
    toggle.setChecked(prefs.cleanupConversationFiles());
    toggle.setOnCheckedChangeListener((v,checked)->{
      try {prefs.saveCleanupConversationFiles(checked);}
      catch(Exception e){toggle.setChecked(prefs.cleanupConversationFiles());toast(e.getMessage());}
    });
    setting.addView(toggle,new LinearLayout.LayoutParams(dp(SETTINGS_ACTION_DP),dp(48)));
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(16);
    body.addView(setting,lp);
  }

  FileLibraryPage fileLibrary;
  void files() { fileLibrary=new FileLibraryPage(this);fileLibrary.open(); }
  void fileActions(String path) {
    sheet(new File(path).getName(), "选择如何使用这份文件", new String[] { "添加到消息", "导出到手机" }, action -> {
      if(action==0){returnToChat();attachments.addPath(path);}
      else {exportPath=path;startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/octet-stream").putExtra(Intent.EXTRA_TITLE,new File(path).getName()),EXPORT);}
    });
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (result != RESULT_OK || data == null) return;
    if(request==IMPORT||request==IMPORT_IMAGE){
      if(attachmentPickerGeneration!=attachments.generation)return;
      ArrayList<Uri> uris=new ArrayList<>();
      if(data.getClipData()!=null)for(int n=0;n<data.getClipData().getItemCount();n++)uris.add(data.getClipData().getItemAt(n).getUri());
      else if(data.getData()!=null)uris.add(data.getData());
      if(uris.size()+attachments.pending.size()>8){toast("每条消息最多添加 8 个附件");return;}
      attachments.importUris(uris);return;
    }
    if(data.getData()==null)return;
    Uri uri = data.getData();
    io.execute(() -> {
      try {
        FileTools tools = new FileTools(this);
        if(request==MessageActions.SAVE_MARKDOWN) {
          messageActions.saveTo(uri);
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
