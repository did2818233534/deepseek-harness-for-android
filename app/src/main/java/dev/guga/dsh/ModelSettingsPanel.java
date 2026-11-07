package dev.guga.dsh;
import dev.guga.dsh.settings.ModelList;
import dev.guga.dsh.settings.ModelEndpoint;
import dev.guga.dsh.settings.Settings;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.json.*;

final class ModelSettingsPanel {
  final MainActivity a;
  final boolean quick;
  final Settings settings;
  final okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
    .callTimeout(20, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build();
  final List<String> models = new ArrayList<>();
  final Map<String, String> names = new HashMap<>();
  String selected, protocol;
  EditText base, manualModel;
  Button providerButton;
  int catalogGeneration;
  boolean closed, editing, loaded;
  LinearLayout box, selector;
  Button modelButton;
  ImageButton refresh;
  TextView state;
  Switch search, fetch, autoImageModel;
  Switch timeout;
  EditText seconds;
  LinearLayout durationBox;
  String effort;
  static final String[] EFFORTS = {"none", "low", "high", "max"};
  static final String[] EFFORT_LABELS = {"关闭", "低", "高", "最高"};
  EditText key, steps;
  Dialog dialog;
  View page;
  PopupWindow popup;
  Runnable pending;
  ModelSettingsPanel(MainActivity activity, boolean quick) {
    a = activity; this.quick = quick; settings = new Settings(a); selected = settings.model(); protocol = settings.provider();
    models.add(selected);
  }
  String display(String id) {
    String name = names.get(id);
    if (name != null && !name.isEmpty() && !name.equals(id)) return name;
    if (id.equals("deepseek-flash")) return "DeepSeek V4.1 Flash";
    if (id.equals("deepseek-v4-pro")) return "DeepSeek V4 Pro";
    return id;
  }
  void open() throws Exception {
    JSONObject values = settings.publicData();
    box = new LinearLayout(a); box.setOrientation(LinearLayout.VERTICAL);
    box.setPadding(a.dp(quick?8:4), a.dp(12), a.dp(quick?8:4), a.dp(20));
    if (!quick) {
      providerButton = a.button(protocol.equals(ModelEndpoint.ANTHROPIC) ? "Claude / Anthropic" : "DeepSeek", v -> chooseProvider());
      box.addView(a.helpTitle("服务协议",13,a.muted,"Claude 使用 Messages API，支持官方服务和兼容中转站。两个服务分别保存地址、密钥和模型。"));
      box.addView(providerButton,a.spacedControl());
      base = a.field(box,"API 基础地址",settings.base(),false);
      base.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
      manualModel = a.field(box,"模型 ID",selected,false);
      manualModel.setSingleLine(true);
    }
    box.addView(a.label("模型", 13, a.muted));
    selector = a.row(); selector.setBackground(a.ui.surface(UiStyle.SURFACE, 16));
    modelButton = a.button(display(selected), v -> choose());
    modelButton.setBackgroundColor(Color.TRANSPARENT);
    android.graphics.drawable.Drawable arrow = a.getDrawable(R.drawable.ic_chevron_down);
    arrow.setBounds(0, 0, a.dp(20), a.dp(20));
    arrow.setTint(a.muted);
    modelButton.setCompoundDrawablesRelative(null, null, arrow, null);
    modelButton.setCompoundDrawablePadding(a.dp(8));
    modelButton.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
    modelButton.setSingleLine(true);
    modelButton.setEllipsize(TextUtils.TruncateAt.END);
    modelButton.setContentDescription("选择模型");
    selector.addView(modelButton, new LinearLayout.LayoutParams(0, a.dp(56), 1));
    refresh = a.icon(R.drawable.ic_refresh, "刷新模型列表", v -> load());
    selector.addView(refresh, new LinearLayout.LayoutParams(a.dp(48), a.dp(48)));
    box.addView(selector, a.spacedControl());
    state = a.label("", 12, a.muted);
    state.setPadding(a.dp(4), 0, a.dp(4), a.dp(8)); box.addView(state);
    state.setVisibility(View.GONE);
    effort = settings.reasoningEffort();
    effortControl();
    if (!quick) {
      timeout = toggle("最长思考时间", values.optBoolean("limitThinking"), "从收到思考内容开始累计本轮思考时间。到时若仍在思考，会停止当前请求，用相同问题和已有工具结果关闭思考重新请求，每轮最多切换一次。正在输出的正文不会中断。部分推理可能丢失，两次请求都可能计费，最终回答仍需等待网络和模型响应。");
      durationBox = new LinearLayout(a); durationBox.setOrientation(LinearLayout.VERTICAL);
      box.addView(durationBox);
      seconds = a.field(durationBox, "思考时间（秒）", String.valueOf(values.optInt("thinkingSeconds",30)), false);
      seconds.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
      durationBox.setVisibility(timeout.isChecked() ? View.VISIBLE : View.GONE);
      watch(seconds);
      key = a.secretField(box, "API Key", settings.apiKey());
      key.setSelectAllOnFocus(true);
    }
    search = toggle("联网搜索", settings.webSearch(), "搜索和网页读取使用 DeepSeek 官方工具接口，需要在 DeepSeek 服务中另存官方 API Key。Claude 中转站密钥不会用于这些接口。没有官方密钥时，本轮不提供联网工具。");
    if (!quick) {
      fetch = toggle("网页读取", settings.webFetch(), "开启后，助手可以读取指定链接或搜索结果中的网页正文。关闭后不再调用网页读取工具；是否可以搜索由“联网搜索”单独控制。");
      autoImageModel = toggle("图片自动切换模型", settings.autoImageModel(), "DeepSeek 非视觉模型可临时切换到 Flash。Claude 服务只在当前服务的模型列表中寻找支持图片的模型，绝不跨服务切换；找不到时按文件处理，不猜测画面。");
      steps = a.field(box, "每轮最多步骤", String.valueOf(values.optInt("maxSteps", 32)), false);
      steps.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);

    }
    if (quick) {
      dialog = a.bottomPanel("聊天设置", box);
      a.dialogCleanup.put(dialog, this::close);
    } else {
      page = a.showPage("模型与服务", box);
      a.pageCleanup.put(page, this::close);
      watch(key); watch(steps); watch(base); watch(manualModel);
    }
    String cache = settings.modelCatalog();
    if (!cache.isEmpty()) {
      try { catalog(cache); } catch (Exception ignored) {}
    }
    load();
  }
  void chooseProvider() {
    android.app.AlertDialog chooser = new android.app.AlertDialog.Builder(a)
      .setTitle("服务协议")
      .setSingleChoiceItems(new String[]{"DeepSeek", "Claude / Anthropic"},
        protocol.equals(ModelEndpoint.ANTHROPIC) ? 1 : 0, (dialog, which) -> {
          dialog.dismiss();
          String next = which == 1 ? ModelEndpoint.ANTHROPIC : ModelEndpoint.DEEPSEEK;
          if (next.equals(protocol) || !persist()) return;
          try {
            String nextBase=settings.base(next), nextModel=settings.model(next), nextKey=settings.apiKey(next);
            settings.save(next,nextBase,nextModel,"",!effort.equals("none"),Integer.parseInt(steps.getText().toString()));
            if (pending != null) { a.handler.removeCallbacks(pending); pending=null; }
            catalogGeneration++; refresh.setEnabled(true);
            protocol=next; selected=nextModel;
            editing=true;
            base.setText(nextBase); manualModel.setText(nextModel); key.setText(nextKey);
            editing=false;
            providerButton.setText(which == 1 ? "Claude / Anthropic" : "DeepSeek");
            models.clear(); models.add(selected); names.clear();
            modelButton.setText(display(selected)); a.status.setText(selected); setState("");
            if (popup != null) popup.dismiss();
            String cache=settings.modelCatalog(); if (!cache.isEmpty()) catalog(cache);
          } catch (Exception error) { editing=false; setState(error.getMessage()); }
        }).create();
    chooser.show(); a.styleDialog(chooser,false);
  }
  void setState(String text) {
    state.setText(text);
    state.setVisibility(text == null || text.isEmpty() ? View.GONE : View.VISIBLE);
  }
  void effortControl() {
    LinearLayout panel = new LinearLayout(a); panel.setOrientation(LinearLayout.VERTICAL);
    panel.setPadding(a.dp(16), a.dp(12), a.dp(16), a.dp(8));
    panel.setBackground(a.ui.shape(UiStyle.LOW, 20));
    TextView title = a.label("思考强度", 16, a.ink); panel.addView(title);
    TextView value = a.label("", 16, a.accent); value.setGravity(Gravity.CENTER);
    panel.addView(value);
    SeekBar slider = new EffortSlider(a); slider.setMax(3);
    slider.setProgressTintList(android.content.res.ColorStateList.valueOf(a.accent));
    slider.setThumbTintList(android.content.res.ColorStateList.valueOf(a.accent));
    int initial = java.util.Arrays.asList(EFFORTS).indexOf(effort);
    slider.setProgress(Math.max(0, initial)); value.setText(EFFORT_LABELS[slider.getProgress()]);
    slider.setContentDescription("思考强度：" + EFFORT_LABELS[slider.getProgress()]);
    panel.addView(slider, new LinearLayout.LayoutParams(-1, a.dp(48)));
    slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      public void onStartTrackingTouch(SeekBar bar) {}
      public void onStopTrackingTouch(SeekBar bar) {}
      public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
        if (fromUser) {
          String previous = effort; effort = EFFORTS[progress];
          if (!persist()) { effort = previous; bar.setProgress(java.util.Arrays.asList(EFFORTS).indexOf(previous)); return; }
        }
        value.setText(EFFORT_LABELS[progress]);
        bar.setContentDescription("思考强度：" + EFFORT_LABELS[progress]);
      }
    });
    LinearLayout.LayoutParams panelParams = a.spacedControl();
    panelParams.height = LinearLayout.LayoutParams.WRAP_CONTENT;
    box.addView(panel, panelParams);
  }
  Switch toggle(String title, boolean checked) {
    return toggle(title, checked, null);
  }
  Switch toggle(String title, boolean checked, String detail) {
    Switch toggle = new Switch(a);
    toggle.setText(title); toggle.setTextSize(16); toggle.setTextColor(a.ink);
    toggle.setChecked(checked);
    toggle.setPadding(a.dp(16), 0, a.dp(16), 0);
    toggle.setBackground(a.ui.shape(UiStyle.LOW, 16));
    if(detail==null)box.addView(toggle, a.spacedControl());
    else {
      LinearLayout row=a.row();row.setPadding(a.dp(16),0,a.dp(16),0);
      row.setBackground(a.ui.shape(UiStyle.LOW,16));
      row.addView(a.helpTitle(title,16,a.ink,detail),new LinearLayout.LayoutParams(0,-2,1));
      toggle.setText("");toggle.setContentDescription(title);toggle.setPadding(0,0,0,0);toggle.setBackgroundColor(Color.TRANSPARENT);
      row.addView(toggle,new LinearLayout.LayoutParams(a.dp(MainActivity.SETTINGS_ACTION_DP),a.dp(48)));
      row.setOnClickListener(v->toggle.toggle());box.addView(row,a.spacedControl());
    }
    toggle.setOnCheckedChangeListener((button, value) -> {
      if (editing) return;
      if (!persist()) {
        editing = true; toggle.setChecked(!value); editing = false;
      }
      if (toggle == timeout) durationBox.setVisibility(toggle.isChecked() ? View.VISIBLE : View.GONE);
    });
    return toggle;
  }
  void watch(EditText field) {
    field.addTextChangedListener(new TextWatcher() {
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
      public void onTextChanged(CharSequence s, int start, int before, int count) {
        if (editing) return;
        if (field == base || field == key) { catalogGeneration++; refresh.setEnabled(true); models.clear(); models.add(selected); names.clear(); modelButton.setText(display(selected)); if (popup != null) popup.dismiss(); }
        if (pending != null) a.handler.removeCallbacks(pending);
        pending = () -> { if (!closed) persist(); };
        a.handler.postDelayed(pending, 800);
      }
      public void afterTextChanged(Editable e) {}
    });
    field.setOnFocusChangeListener((v, focused) -> { if (!focused && !closed) persist(); });
  }
  boolean persist() {
    try {
      if (a.busy) throw new Exception("任务进行中，停止任务后再修改设置");
      JSONObject current = settings.publicData();
      int count = quick ? current.optInt("maxSteps", 32) : Integer.parseInt(steps.getText().toString());
      if (count < 1 || count > 128) throw new Exception("步骤数应为 1–128");
      if (!quick) {
        int duration = current.optInt("thinkingSeconds",30);
        if (timeout.isChecked()) {
          try { duration = Integer.parseInt(seconds.getText().toString()); } catch (NumberFormatException ignored) { duration=0; }
          if (duration < 1 || duration > 2147483) throw new Exception("请输入 1 至 2147483 之间的秒数");
        }
        settings.saveThinkingLimit(timeout.isChecked(),duration);
      }
      String model = quick ? selected : manualModel.getText().toString().trim();
      settings.save(protocol, quick ? settings.base() : base.getText().toString(), model,
        quick ? "" : key.getText().toString(), !effort.equals("none"), count);
      selected = model;
      modelButton.setText(display(selected));
      settings.saveReasoningEffort(effort);
      settings.saveWeb(search.isChecked(), quick ? settings.webFetch() : fetch.isChecked());
      if(!quick)settings.saveAutoImageModel(autoImageModel.isChecked());
      a.status.setText(selected);
      return true;
    } catch (Exception e) { setState(e instanceof NumberFormatException ? "请输入有效的步骤数" : e.getMessage()); return false; }
  }
  void choose() {
    if (popup != null && popup.isShowing()) { popup.dismiss(); return; }
    LinearLayout rows = new LinearLayout(a); rows.setOrientation(LinearLayout.VERTICAL);
    rows.setPadding(a.dp(8), a.dp(8), a.dp(8), a.dp(8));
    for (String id : models) {
      LinearLayout row = a.row(); row.setPadding(a.dp(12), a.dp(12), a.dp(12), a.dp(12));
      row.setMinimumHeight(a.dp(64));
      row.setBackground(a.ui.surface(id.equals(selected) ? UiStyle.SURFACE : Color.TRANSPARENT, 12));
      LinearLayout text = new LinearLayout(a); text.setOrientation(LinearLayout.VERTICAL);
      TextView name = a.label(display(id), 16, a.ink);
      name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
      text.addView(name);
      TextView api = a.label(id, 12, a.muted); api.setPadding(0, a.dp(4), 0, 0); text.addView(api);
      row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
      ImageView check = new ImageView(a);
      check.setImageResource(R.drawable.ic_check);
      check.setImageTintList(android.content.res.ColorStateList.valueOf(a.accent));
      check.setVisibility(id.equals(selected) ? View.VISIBLE : View.INVISIBLE);
      LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(a.dp(24),a.dp(24));
      checkParams.setMarginStart(a.dp(12));row.addView(check,checkParams);
      row.setContentDescription("选择 " + id); row.setFocusable(true);
      row.setOnClickListener(v -> {
        String previous = selected; selected = id;
        if (manualModel != null) { editing=true; manualModel.setText(id); editing=false; }
        if (persist()) {
          modelButton.setText(display(selected)); popup.dismiss();
        } else { selected = previous; if (manualModel != null) { editing=true; manualModel.setText(previous); editing=false; } }
      });
      rows.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }
    ScrollView scroll = new ScrollView(a); scroll.addView(rows);
    int width = selector.getWidth();
    int height = Math.min(a.dp(280), a.dp(16 + models.size() * 72));
    popup = new PopupWindow(scroll, width, height, true);
    popup.setBackgroundDrawable(a.ui.shape(UiStyle.BACKGROUND, 16));
    popup.setElevation(a.dp(8)); popup.setOutsideTouchable(true);
    popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
    popup.setOnDismissListener(() -> { if (a.modelPopup == popup) a.modelPopup = null; });
    a.modelPopup = popup;
    popup.showAsDropDown(selector, 0, a.dp(8));
  }
  void catalog(String json) throws Exception {
    List<String> available = ModelList.parse(json);
    JSONArray data = new JSONObject(json).getJSONArray("data");
    names.clear();
    for (int i = 0; i < data.length(); i++) {
      JSONObject item = data.optJSONObject(i);
      if (item != null) names.put(item.optString("id"), item.optString("name", item.optString("display_name")));
    }
    models.clear(); models.addAll(available); loaded = true;
    // Refresh never changes a user's selected model or silently saves the first option.
    modelButton.setText(display(selected));
    setState("");
  }
  void load() {
    if (closed || !refresh.isEnabled()) return;
    try {
      if (!quick && !persist()) return;
      final int generation = ++catalogGeneration;
      String secret = key == null ? "" : key.getText().toString().trim();
      if (secret.isEmpty()) secret = settings.apiKey();
      if (secret.isEmpty()) { setState("请先在模型与服务中填写 API Key"); return; }
      refresh.setEnabled(false); setState(""); modelButton.setText("正在获取模型列表…");
      client.newCall(ModelEndpoint.request(protocol, settings.base(), true, secret).build()).enqueue(new okhttp3.Callback() {
        void fail(String text) { a.runOnUiThread(() -> {
          if (closed || generation != catalogGeneration) return; refresh.setEnabled(true); modelButton.setText(display(selected)); setState(""); a.toast(text);
        }); }
        public void onFailure(okhttp3.Call call, IOException e) { fail("获取失败，点击右侧刷新图标重试"); }
        public void onResponse(okhttp3.Call call, okhttp3.Response response) {
          try (okhttp3.Response r = response) {
            if (!r.isSuccessful()) { fail("获取失败（HTTP " + r.code() + "），请检查网络和密钥"); return; }
            String json = r.body().string(); ModelList.parse(json);
            a.runOnUiThread(() -> {
              if (closed || generation != catalogGeneration) return;
              refresh.setEnabled(true);
              try {
                catalog(json);
                settings.saveModelCatalog(json);
                if (popup != null && popup.isShowing()) { popup.dismiss(); choose(); }
              } catch (Exception e) { setState("模型列表格式异常，请重试"); }
            });
          } catch (Exception e) { fail("模型列表格式异常，请重试"); }
        }
      });
    } catch (Exception e) { refresh.setEnabled(true); modelButton.setText(display(selected)); setState("无法读取密钥，请重新填写"); }
  }
  void close() {
    if (closed) return;
    if (pending != null) { a.handler.removeCallbacks(pending); persist(); }
    closed = true;
    if (popup != null) popup.dismiss();
    MainActivity.closeModelClient(client);
  }
}
