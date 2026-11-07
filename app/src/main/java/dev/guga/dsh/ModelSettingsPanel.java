package dev.guga.dsh;
import dev.guga.dsh.settings.ModelList;
import dev.guga.dsh.settings.ModelEndpoint;
import dev.guga.dsh.settings.Settings;
import dev.guga.dsh.settings.ProviderRegistry;

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
  final boolean quick, advanced;
  final Map<String,EditText> endpointFields=new LinkedHashMap<>();
  EditText usageKey;
  LinearLayout fetchEndpointRow;
  Button usageButton, resetButton;
  final Settings settings;
  final okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
    .callTimeout(20, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build();
  final List<String> models = new ArrayList<>();
  final Map<String, String> names = new HashMap<>();
  String selected, protocol;
  EditText base, manualModel;
  Button providerButton;
  int catalogGeneration;
  boolean closed, editing, loaded, suspended;
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
  ModelSettingsPanel(MainActivity activity, boolean quick) { this(activity,quick,false); }
  ModelSettingsPanel(MainActivity activity, boolean quick, boolean advanced) {
    this.advanced=advanced;
    a = activity; this.quick = quick; settings = new Settings(a); selected = settings.model(); protocol = settings.provider();
    if(!quick){protocol=settings.editingProvider();selected=settings.model(protocol);}
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
    LinearLayout pageBox=box, basicBox=new LinearLayout(a), moreBox=new LinearLayout(a);
    basicBox.setOrientation(LinearLayout.VERTICAL);moreBox.setOrientation(LinearLayout.VERTICAL);
    if (!quick) {
      providerButton = a.button(ProviderRegistry.get(protocol).label, v -> chooseProvider());
      box.addView(a.helpTitle("供应商",13,a.muted,"每家供应商分别保存地址、密钥和已启用的模型。勾选的模型会同时出现在聊天页，切换此处只编辑配置。"));
      styleDropdown(providerButton);
      box.addView(providerButton,a.spacedControl());
      box=moreBox;
      base = a.field(box,"API 基础地址",settings.base(protocol),false);
      base.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
      manualModel = a.field(box,"模型 ID",selected,false);
      manualModel.setSingleLine(true);
      box.addView(a.button("添加到聊天模型", v -> {
        if(persist()) try { settings.setModelEnabled(protocol,manualModel.getText().toString(),true); if(!models.contains(selected)) models.add(selected); a.toast("已添加到聊天模型"); } catch(Exception e){setState(e.getMessage());}
      }),a.spacedControl());
    }
    if(!quick){box=basicBox;key=a.secretField(box,"API Key",settings.apiKey(protocol));key.setSelectAllOnFocus(true);}
    box.addView(a.label(quick ? "模型" : "聊天页可选模型", 13, a.muted));
    selector = a.row(); selector.setBackground(a.ui.surface(UiStyle.SURFACE, 16));
    modelButton = a.button(quick ? (settings.enabledModels(protocol).contains(selected) ? display(selected) : "请选择模型") : "选择启用的模型", v -> choose());
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
    if (!quick) selector.addView(refresh, new LinearLayout.LayoutParams(a.dp(48), a.dp(48)));
    box.addView(selector, a.spacedControl());
    state = a.label("", 12, a.muted);
    state.setPadding(a.dp(4), 0, a.dp(4), a.dp(8));
    state.setVisibility(View.GONE);
    if(!quick)box=moreBox;
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

    }
    search = toggle("联网搜索", settings.webSearch(), "Claude 使用所填基地址的云端 web_search；DeepSeek 默认使用官方接口；接口路径和基础地址可在“更多”修改。中转站不支持、超时或报错时，回退为手机直接访问公开搜索网页。云端搜索可能另行计费，本地搜索也需要网络。");
    if (!quick) {
      fetch = toggle("网页读取", settings.webFetch(), "开启后读取网页正文；DeepSeek 使用所填基地址的读取接口，失败时回退手机直接读取；Claude 直接在手机读取公开文本网页。");
      autoImageModel = toggle("图片自动切换模型", settings.autoImageModel(), "DeepSeek 非视觉模型可临时切换到 Flash。Claude 服务只在当前服务的模型列表中寻找支持图片的模型，绝不跨服务切换；找不到时按文件处理，不猜测画面。");
      steps = a.field(box, "每轮最多步骤", String.valueOf(values.optInt("maxSteps", 32)), false);
      steps.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);

    }
    if(!quick) {
      box.addView(a.helpTitle("接口路径",13,a.muted,"默认使用官方路径，拼接在所填基础地址之后；已有 /v1 时不会重复添加。只修改路径，不自动改域名。模型、搜索与用量可分别设置。"));
      String[] titles={"对话接口","模型列表接口","云端搜索接口","网页读取接口","用量 / 余额接口"};
      for(int n=0;n<ProviderRegistry.ENDPOINTS.length;n++) {
        String kind=ProviderRegistry.ENDPOINTS[n];LinearLayout fieldBox=new LinearLayout(a);fieldBox.setOrientation(LinearLayout.VERTICAL);box.addView(fieldBox);
        if(kind.equals("fetch")){fetchEndpointRow=fieldBox;fieldBox.setVisibility(ModelEndpoint.ANTHROPIC.equals(ProviderRegistry.get(protocol).protocol)?View.GONE:View.VISIBLE);}
        EditText field=a.field(fieldBox,titles[n],settings.endpointPath(protocol,kind),false);
        field.setSingleLine(true);endpointFields.put(kind,field);watch(field);
      }
      box.addView(a.helpTitle("用量查询认证",13,a.muted,"Claude 官方用量接口需要 Admin API Key，普通聊天密钥通常无权限。可另填查询密钥；留空使用当前供应商的 API Key。中转站以其兼容的接口和权限为准。默认查询最近 24 小时，用量响应原样展示。"));
      usageKey=a.secretField(box,"用量查询 API Key（可选）",settings.usageKey(protocol));watch(usageKey);
      usageButton=a.button("查询用量 / 余额",v->queryUsage());box.addView(usageButton,a.spacedControl());
      resetButton=a.button("恢复当前供应商初始设置",v->resetDefaults());box.addView(resetButton,a.spacedControl());
      box=pageBox;box.addView(advanced?moreBox:basicBox,new LinearLayout.LayoutParams(-1,-2));
      if(!advanced) a.settingEntry(box,R.drawable.ic_settings,"更多","基地址、接口与扩展参数",true,true,v->{
        if(!persist())return;
        settings.saveEditingProvider(protocol);
        try{if(pending!=null){a.handler.removeCallbacks(pending);pending=null;}suspended=true;new ModelSettingsPanel(a,false,true).open();}catch(Exception error){suspended=false;a.toast(error.getMessage());}
      });
    }
    box.addView(state);
    if (quick) {
      dialog = a.bottomPanel("聊天设置", box);
      a.dialogCleanup.put(dialog, this::close);
    } else {
      page = a.showPage(advanced?"更多":ProviderRegistry.get(protocol).label, box);
      a.pageCleanup.put(page, this::close);
      a.pageResume.put(page,this::resumeProfile);
      watch(key); watch(steps); watch(base); watch(manualModel);
    }
    String cache = settings.modelCatalog(protocol);
    if (!cache.isEmpty()) {
      try { catalog(cache); } catch (Exception ignored) {}
    }

  }
  void styleDropdown(Button button) {
    button.setBackground(a.ui.surface(UiStyle.SURFACE,16));
    button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
    android.graphics.drawable.Drawable arrow=a.getDrawable(R.drawable.ic_chevron_down);
    arrow.setBounds(0,0,a.dp(20),a.dp(20)); arrow.setTint(a.muted);
    button.setCompoundDrawablesRelative(null,null,arrow,null);
  }
  void showDropdown(View anchor, LinearLayout rows, int count) {
    ScrollView scroll=new ScrollView(a); scroll.addView(rows);
    popup=new PopupWindow(scroll,anchor.getWidth(),Math.min(a.dp(320),a.dp(16+count*72)),true);
    popup.setBackgroundDrawable(a.ui.shape(UiStyle.BACKGROUND,16));
    popup.setElevation(a.dp(8)); popup.setOutsideTouchable(true);
    popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
    PopupWindow current=popup;
    popup.setOnDismissListener(()->{if(a.modelPopup==current)a.modelPopup=null;});
    a.modelPopup=popup; popup.showAsDropDown(anchor,0,a.dp(8));
  }
  void chooseProvider() {
    if(popup!=null && popup.isShowing()){popup.dismiss();return;}
    LinearLayout rows=new LinearLayout(a); rows.setOrientation(LinearLayout.VERTICAL);
    rows.setPadding(a.dp(8),a.dp(8),a.dp(8),a.dp(8));
    for(ProviderRegistry.Provider provider:ProviderRegistry.ALL) {
      Button item=a.button(provider.label,v->{
        if(!persist())return;
        popup.dismiss();
        try {
          if(pending!=null){a.handler.removeCallbacks(pending);pending=null;}
          catalogGeneration++;refresh.setEnabled(true);
          protocol=provider.id;settings.saveEditingProvider(protocol);selected=settings.model(protocol);
          editing=true;base.setText(settings.base(protocol));manualModel.setText(selected);key.setText(settings.apiKey(protocol));
          for(String kind:ProviderRegistry.ENDPOINTS)endpointFields.get(kind).setText(settings.endpointPath(protocol,kind));
          usageKey.setText(settings.usageKey(protocol));fetchEndpointRow.setVisibility(ModelEndpoint.ANTHROPIC.equals(provider.protocol)?View.GONE:View.VISIBLE);editing=false;
          providerButton.setText(provider.label);models.clear();models.add(selected);names.clear();
          modelButton.setText("选择启用的模型");setState("");
          String cache=settings.modelCatalog(protocol);if(!cache.isEmpty())catalog(cache);
        }catch(Exception error){editing=false;setState(error.getMessage());}
      });
      item.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
      item.setBackground(a.ui.surface(provider.id.equals(protocol)?UiStyle.SURFACE:Color.TRANSPARENT,12));
      android.graphics.drawable.Drawable mark=a.getDrawable(R.drawable.ic_check);
      mark.setBounds(0,0,a.dp(24),a.dp(24));mark.setTint(provider.id.equals(protocol)?a.accent:Color.TRANSPARENT);
      item.setCompoundDrawablesRelative(null,null,mark,null);
      rows.addView(item,new LinearLayout.LayoutParams(-1,a.dp(64)));
    }
    showDropdown(providerButton,rows,ProviderRegistry.ALL.size());
  }
  void resumeProfile() {
    if(closed || quick)return;
    try {
      editing=true;suspended=false;catalogGeneration++;refresh.setEnabled(true);
      base.setText(settings.base(protocol));manualModel.setText(settings.model(protocol));selected=settings.model(protocol);key.setText(settings.apiKey(protocol));
      for(String kind:ProviderRegistry.ENDPOINTS)endpointFields.get(kind).setText(settings.endpointPath(protocol,kind));
      usageKey.setText(settings.usageKey(protocol));effort=settings.reasoningEffort();
      JSONObject values=settings.publicData();timeout.setChecked(values.optBoolean("limitThinking"));seconds.setText(String.valueOf(values.optInt("thinkingSeconds",30)));steps.setText(String.valueOf(values.optInt("maxSteps",32)));
      search.setChecked(settings.webSearch());fetch.setChecked(settings.webFetch());autoImageModel.setChecked(settings.autoImageModel());
      models.clear();models.addAll(settings.enabledModels(protocol));names.clear();
      String cache=settings.modelCatalog(protocol);if(!cache.isEmpty())catalog(cache);
      editing=false;setState("");
    }catch(Exception error){editing=false;setState(error.getMessage());}
  }
  void resetDefaults() {
    if(a.busy){setState("任务进行中，停止任务后再恢复设置");return;}
    try {
      if(pending!=null){a.handler.removeCallbacks(pending);pending=null;}
      settings.resetProviderDefaults(protocol);catalogGeneration++;refresh.setEnabled(true);
      editing=true;base.setText(settings.base(protocol));selected=settings.model(protocol);manualModel.setText(selected);
      for(String kind:ProviderRegistry.ENDPOINTS)endpointFields.get(kind).setText(settings.endpointPath(protocol,kind));
      editing=false;models.clear();models.addAll(settings.enabledModels(protocol));names.clear();setState("已恢复基地址、接口路径和默认模型");
    }catch(Exception error){editing=false;setState(error.getMessage());}
  }
  void queryUsage() {
    if(!persist() || !usageButton.isEnabled())return;
    final String requested=protocol;
    try {
      String secret=settings.usageKey(protocol);if(secret.isEmpty())secret=settings.apiKey(protocol);
      okhttp3.Request.Builder request=ModelEndpoint.configured(settings,protocol,"usage",secret);
      okhttp3.HttpUrl.Builder url=okhttp3.HttpUrl.get(settings.endpoint(protocol,"usage")).newBuilder();
      if(ModelEndpoint.ANTHROPIC.equals(ProviderRegistry.get(protocol).protocol) && settings.endpointPath(protocol,"usage").equals(ProviderRegistry.defaultPath(protocol,"usage"))) {
        url.addQueryParameter("starting_at",java.time.Instant.now().minusSeconds(86400).toString());
        url.addQueryParameter("ending_at",java.time.Instant.now().toString());
      }
      usageButton.setEnabled(false);
      client.newCall(request.url(url.build()).get().build()).enqueue(new okhttp3.Callback(){
        void done(String result){a.runOnUiThread(()->{
          if(closed)return;usageButton.setEnabled(true);if(!requested.equals(protocol))return;
          android.app.AlertDialog info=new android.app.AlertDialog.Builder(a).setTitle("用量 / 余额").setMessage(result).setPositiveButton("关闭",null).create();
          info.show();a.styleDialog(info,false);
        });}
        public void onFailure(okhttp3.Call call,IOException error){done("查询失败，请检查基地址、接口和权限");}
        public void onResponse(okhttp3.Call call,okhttp3.Response response){
          try(okhttp3.Response r=response){
            if(!r.isSuccessful()){done("查询返回 HTTP "+r.code()+"。Claude 官方接口需 Admin API Key；中转站需支持所填接口。");return;}
            String text=new String(dev.guga.dsh.files.FileTools.readLimited(r.body().byteStream(),128*1024),java.nio.charset.StandardCharsets.UTF_8);
            for(String value:new String[]{settings.apiKey(requested),settings.usageKey(requested)})if(!value.isEmpty())text=text.replace(value,"[已隐藏]");
            try{text=new JSONObject(text).toString(2);}catch(Exception ignored){}
            done(text.length()>20000?text.substring(0,20000)+"\n[已截断]":text);
          }catch(Exception error){done("用量响应无法读取");}
        }
      });
    }catch(Exception error){usageButton.setEnabled(true);setState(error.getMessage());}
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
        if (field == base || field == key || field == endpointFields.get("models")) { catalogGeneration++; refresh.setEnabled(true); models.clear(); models.add(selected); names.clear(); modelButton.setText(quick ? display(selected) : "选择启用的模型"); if (popup != null) popup.dismiss(); }
        if (pending != null) a.handler.removeCallbacks(pending);
        pending = () -> { if (!closed) persist(); };
        a.handler.postDelayed(pending, 800);
      }
      public void afterTextChanged(Editable e) {}
    });
    field.setOnFocusChangeListener((v, focused) -> { if (!focused && !closed) persist(); });
  }
  boolean persist() {
    if(suspended)return true;
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
      if (!quick) {
        Map<String,String> paths=new LinkedHashMap<>();
        for(String kind:ProviderRegistry.ENDPOINTS)paths.put(kind,ModelEndpoint.path(endpointFields.get(kind).getText().toString()));
        settings.saveProfile(protocol, base.getText().toString(), model,key.getText().toString(), !effort.equals("none"), count, false);
        settings.saveEndpoints(protocol,paths);settings.saveUsageKey(protocol,usageKey.getText().toString());
      }
      selected = model;
      modelButton.setText(quick ? display(selected) : "选择启用的模型");
      settings.saveReasoningEffort(effort);
      settings.saveWeb(search.isChecked(), quick ? settings.webFetch() : fetch.isChecked());
      if(!quick)settings.saveAutoImageModel(autoImageModel.isChecked());
      a.status.setText(settings.model());
      return true;
    } catch (Exception e) { setState(e instanceof NumberFormatException ? "请输入有效的步骤数" : e.getMessage()); return false; }
  }
  void choose() {
    if(!quick && ModelEndpoint.ANTHROPIC.equals(ProviderRegistry.get(protocol).protocol))ModelList.newestClaudeFirst(models,settings.modelCatalog(protocol));
    if(!quick)models.sort((left,right)->Boolean.compare(settings.enabledModels(protocol).contains(right),settings.enabledModels(protocol).contains(left)));
    if(quick){chooseChatModel();return;}
    if (popup != null && popup.isShowing()) { popup.dismiss(); return; }
    LinearLayout rows = new LinearLayout(a); rows.setOrientation(LinearLayout.VERTICAL);
    rows.setPadding(a.dp(8), a.dp(8), a.dp(8), a.dp(8));
    for (String id : models) {
      LinearLayout row = a.row(); row.setPadding(a.dp(12), a.dp(12), a.dp(12), a.dp(12));
      row.setMinimumHeight(a.dp(64));
      row.setBackground(a.ui.surface(settings.enabledModels(protocol).contains(id) ? UiStyle.SURFACE : Color.TRANSPARENT, 12));
      LinearLayout text = new LinearLayout(a); text.setOrientation(LinearLayout.VERTICAL);
      TextView name = a.label(display(id), 16, a.ink);
      name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
      text.addView(name);
      TextView api = a.label(id, 12, a.muted); api.setPadding(0, a.dp(4), 0, 0); text.addView(api);
      row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
      ImageView check = new ImageView(a);
      check.setImageResource(R.drawable.ic_check);
      check.setImageTintList(android.content.res.ColorStateList.valueOf(a.accent));
      check.setVisibility(settings.enabledModels(protocol).contains(id) ? View.VISIBLE : View.INVISIBLE);
      LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(a.dp(24),a.dp(24));
      checkParams.setMarginStart(a.dp(12));row.addView(check,checkParams);
      row.setContentDescription("选择 " + id); row.setFocusable(true);
      row.setOnClickListener(v -> {
        try {
          if(a.busy) throw new Exception("任务进行中，停止任务后再修改设置");
          boolean enabled=!settings.enabledModels(protocol).contains(id);
          settings.setModelEnabled(protocol,id,enabled);
          a.status.setText(settings.model());
          check.setVisibility(enabled?View.VISIBLE:View.INVISIBLE);
          row.setBackground(a.ui.surface(enabled?UiStyle.SURFACE:Color.TRANSPARENT,12));
          popup.dismiss();choose();
        }catch(Exception error){setState(error.getMessage());}

      });
      rows.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }
    showDropdown(selector,rows,models.size());
  }
  void chooseChatModel() {
    if(popup!=null && popup.isShowing()){popup.dismiss();return;}
    LinearLayout rows=new LinearLayout(a);rows.setOrientation(LinearLayout.VERTICAL);
    rows.setPadding(a.dp(8),a.dp(8),a.dp(8),a.dp(8));
    int count=0;
    for(ProviderRegistry.Provider provider:settings.providersByEnabled()) {
      if(!settings.providerEnabled(provider.id))continue;
      Map<String,String> labels=new HashMap<>();
      try {JSONArray data=new JSONObject(settings.modelCatalog(provider.id)).optJSONArray("data");
        if(data!=null)for(int i=0;i<data.length();i++){JSONObject m=data.optJSONObject(i);if(m!=null)labels.put(m.optString("id"),m.optString("name",m.optString("display_name",m.optString("id"))));}
      }catch(Exception ignored){}
      List<String> enabled=new ArrayList<>(settings.enabledModels(provider.id));
      if(ModelEndpoint.ANTHROPIC.equals(provider.protocol))ModelList.newestClaudeFirst(enabled,settings.modelCatalog(provider.id));
      for(String id:enabled) {
        count++;
        LinearLayout row=a.row();row.setPadding(a.dp(12),a.dp(8),a.dp(12),a.dp(8));row.setMinimumHeight(a.dp(72));
        boolean current=provider.id.equals(settings.provider())&&id.equals(settings.model());
        row.setBackground(a.ui.surface(current?UiStyle.SURFACE:Color.TRANSPARENT,12));
        LinearLayout text=new LinearLayout(a);text.setOrientation(LinearLayout.VERTICAL);
        text.addView(a.label(labels.getOrDefault(id,id),16,a.ink));text.addView(a.label(provider.label+" · "+id,12,a.muted));
        row.addView(text,new LinearLayout.LayoutParams(0,-2,1));
        ImageView check=new ImageView(a);check.setImageResource(R.drawable.ic_check);check.setImageTintList(android.content.res.ColorStateList.valueOf(a.accent));check.setVisibility(current?View.VISIBLE:View.INVISIBLE);row.addView(check,new LinearLayout.LayoutParams(a.dp(24),a.dp(24)));
        row.setOnClickListener(v->{try {
          if(a.busy)throw new Exception("任务进行中，停止任务后再切换模型");
          settings.selectModel(provider.id,id);protocol=provider.id;selected=id;names.clear();names.putAll(labels);
          modelButton.setText(display(id));a.status.setText(id);popup.dismiss();
        }catch(Exception error){setState(error.getMessage());}});
        rows.addView(row,new LinearLayout.LayoutParams(-1,-2));
      }
    }
    if(count==0){rows.addView(a.label("请启用供应商并选择聊天模型",14,a.muted));count=1;}
    showDropdown(selector,rows,count);
  }

  void catalog(String json) throws Exception {
    List<String> available = ModelList.parse(json);
    JSONArray data = new JSONObject(json).getJSONArray("data");
    names.clear();
    for (int i = 0; i < data.length(); i++) {
      JSONObject item = data.optJSONObject(i);
      if (item != null) names.put(item.optString("id"), item.optString("name", item.optString("display_name")));
    }
    models.clear(); models.addAll(available); for(String id:settings.enabledModels(protocol)) if(!models.contains(id))models.add(id); if(ModelEndpoint.ANTHROPIC.equals(ProviderRegistry.get(protocol).protocol))ModelList.newestClaudeFirst(models,json); loaded = true;
    // Refresh never changes a user's selected model or silently saves the first option.
    modelButton.setText(quick ? display(selected) : "选择启用的模型");
    setState("");
  }
  void load() {
    if (closed || !refresh.isEnabled()) return;
    try {
      if (!quick && !persist()) return;
      final int generation = ++catalogGeneration;
      final String requestedProvider=protocol;
      String secret = key == null ? "" : key.getText().toString().trim();
      if (secret.isEmpty()) secret = settings.apiKey(protocol);
      if (secret.isEmpty()) { setState("请先在供应商中填写 API Key"); return; }
      settings.markCatalogRefresh(protocol,dev.guga.dsh.settings.ModelCatalogRefresh.today());
      final int requestVersion=settings.beginCatalogRequest(protocol);
      refresh.setEnabled(false); setState(""); modelButton.setText("正在获取模型列表…");
      client.newCall(ModelEndpoint.configured(settings,protocol,"models",secret).build()).enqueue(new okhttp3.Callback() {
        void fail(String text) { a.runOnUiThread(() -> {
          if (closed || generation != catalogGeneration || !settings.isCatalogRequestCurrent(requestedProvider,requestVersion)) return; refresh.setEnabled(true); modelButton.setText(quick ? display(selected) : "选择启用的模型"); setState(""); a.toast(text);
        }); }
        public void onFailure(okhttp3.Call call, IOException e) { fail("获取失败，点击右侧刷新图标重试"); }
        public void onResponse(okhttp3.Call call, okhttp3.Response response) {
          try (okhttp3.Response r = response) {
            if (!r.isSuccessful()) { fail("获取失败（HTTP " + r.code() + "），请检查网络和密钥"); return; }
            String json = r.body().string(); ModelList.parse(json);
            a.runOnUiThread(() -> {
              if (closed || generation != catalogGeneration || !settings.isCatalogRequestCurrent(requestedProvider,requestVersion)) return;
              refresh.setEnabled(true);
              try {
                catalog(json);
                settings.saveModelCatalog(requestedProvider,json);
                if (popup != null && popup.isShowing()) { popup.dismiss(); choose(); }
              } catch (Exception e) { setState("模型列表格式异常，请重试"); }
            });
          } catch (Exception e) { fail("模型列表格式异常，请重试"); }
        }
      });
    } catch (Exception e) { refresh.setEnabled(true); modelButton.setText(quick ? display(selected) : "选择启用的模型"); setState("无法读取密钥，请重新填写"); }
  }
  void close() {
    if (closed) return;
    if (pending != null) { a.handler.removeCallbacks(pending); persist(); }
    closed = true;
    if (popup != null) popup.dismiss();
    MainActivity.closeModelClient(client);
  }
}
