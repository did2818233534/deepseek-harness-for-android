package dev.guga.dsh;
import dev.guga.dsh.settings.Settings;

import android.app.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import dev.guga.updates.ReleaseUpdater;
import dev.guga.updates.UpdateButton;

final class SettingsPages {
  private final MainActivity activity;
  LinearLayout providerRows;

  SettingsPages(MainActivity activity) {
    this.activity = activity;
  }

  void settings() {
    LinearLayout entries = new LinearLayout(activity);
    entries.setOrientation(LinearLayout.VERTICAL);
    entries.setPadding(activity.dp(4), activity.dp(12), activity.dp(4), activity.dp(20));
    activity.settingSection(entries, "模型");
    activity.settingEntry(entries,R.drawable.ic_model,"模型与服务","管理供应商与聊天可用模型",true,true,v->providers());
    activity.settingSection(entries, "聊天");
    activity.settingEntry(entries, R.drawable.ic_settings, "个性化", "设置适用于所有聊天的自定义指令", true, true, v -> personalization());
    activity.settingSection(entries, "数据管理");
    activity.settingEntry(entries, R.drawable.ic_archive, "已归档对话", "查看和恢复归档的聊天",
      true, false, v -> activity.navigation(true));
    activity.settingEntry(entries, R.drawable.ic_folder, "文件库", "管理导入资料和生成的文件",
      false, true, v -> activity.files());
    activity.settingSection(entries, "应用");
    activity.settingEntry(entries,R.drawable.ic_settings,"外观","选择跟随系统、浅色或深色主题",true,false,v -> appearance());
    activity.settingEntry(entries, R.drawable.ic_refresh, "应用更新", "自动更新与版本检查",
      false, true, v -> updateSettings());
    activity.showPage("设置", entries);
  }

  void providers() {
    Settings settings=new Settings(activity);
    LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);
    body.setPadding(activity.dp(4),activity.dp(12),activity.dp(4),activity.dp(20));
    providerRows=new LinearLayout(activity);providerRows.setOrientation(LinearLayout.VERTICAL);body.addView(providerRows);
    renderProviders(providerRows,settings);
    activity.showPage("供应商",body);
  }
  void renderProviders(LinearLayout body,Settings settings) {
    body.removeAllViews();
    for(dev.guga.dsh.settings.ProviderRegistry.Provider provider:settings.providersByEnabled()) {
      LinearLayout card=activity.row();card.setPadding(activity.dp(16),activity.dp(12),activity.dp(12),activity.dp(12));
      card.setBackground(activity.ui.surface(UiStyle.SURFACE,24));
      LinearLayout text=new LinearLayout(activity);text.setOrientation(LinearLayout.VERTICAL);text.setPadding(0,activity.dp(4),activity.dp(12),activity.dp(4));
      text.addView(activity.label(provider.label,18,activity.ink));
      TextView status=activity.label(settings.providerEnabled(provider.id)?"已开启":"已关闭",13,settings.providerEnabled(provider.id)?activity.accent:activity.muted);
      text.addView(status);card.addView(text,new LinearLayout.LayoutParams(0,-2,1));
      Switch toggle=new Switch(activity);toggle.setChecked(settings.providerEnabled(provider.id));toggle.setContentDescription("启用 "+provider.label);
      card.addView(toggle,new LinearLayout.LayoutParams(activity.dp(56),activity.dp(56)));
      card.setContentDescription("配置 "+provider.label);card.setFocusable(true);
      card.setOnClickListener(v->{settings.saveEditingProvider(provider.id);activity.modelSettings(false);});
      toggle.setOnCheckedChangeListener((button,enabled)->{
        try {
          if(activity.busy)throw new Exception("任务进行中，停止任务后再修改供应商开关");
          settings.setProviderEnabled(provider.id,enabled);activity.status.setText(settings.model());
          activity.handler.post(()->renderProviders(body,settings));
        }catch(Exception error){activity.toast(error.getMessage());activity.handler.post(()->renderProviders(body,settings));}
      });
      LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=activity.dp(12);body.addView(card,lp);
    }
  }

  void appearance(){
    String[] values={"system","light","dark"}, labels={"跟随系统","浅色","深色"};
    var prefs=activity.getSharedPreferences("appearance",0);
    String current=prefs.getString("theme","system");
    LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);
    RadioGroup options=new RadioGroup(activity);
    for(int i=0;i<values.length;i++){
      final String value=values[i];RadioButton button=new RadioButton(activity);button.setText(labels[i]);button.setTextColor(activity.ink);button.setTextSize(16);button.setPadding(activity.dp(12),activity.dp(8),activity.dp(12),activity.dp(8));button.setMinimumHeight(activity.dp(56));button.setButtonTintList(android.content.res.ColorStateList.valueOf(activity.accent));button.setChecked(value.equals(current));
      options.addView(button,new LinearLayout.LayoutParams(-1,-2));button.setOnClickListener(v->{if(!value.equals(prefs.getString("theme","system"))){prefs.edit().putString("theme",value).apply();activity.restoringAppearance=true;activity.recreate();}});
    }
    body.addView(options);activity.showPage("外观",body);
  }

  void personalization() {
    Settings prefs = new Settings(activity);
    LinearLayout body = new LinearLayout(activity);
    body.setOrientation(LinearLayout.VERTICAL);
    body.setPadding(activity.dp(4), activity.dp(12), activity.dp(4), activity.dp(20));
    body.setFocusableInTouchMode(true);
    body.addView(activity.standaloneHelp("自定义指令", 18, activity.ink, "写下希望助手记住的回答偏好或背景。自动保存，从下一条消息起适用于新旧对话；清空即停用。"));
    EditText edit = new EditText(activity);
    edit.setContentDescription("自定义指令");
    edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
    edit.setGravity(Gravity.TOP | Gravity.START);
    edit.setMinLines(5);
    edit.setTextSize(16); edit.setTextColor(activity.ink); edit.setHintTextColor(activity.muted);
    edit.setBackground(activity.outline(UiStyle.LOW, 16));
    edit.setPadding(activity.dp(16),activity.dp(14),activity.dp(16),activity.dp(14));
    edit.setHint("例如：用中文回答，先给结论。");
    edit.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(1500)});
    edit.setText(prefs.customInstructions());
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.topMargin=activity.dp(8);
    body.addView(edit,lp);
    edit.addTextChangedListener(new android.text.TextWatcher(){
      public void beforeTextChanged(CharSequence s,int start,int count,int after){}
      public void onTextChanged(CharSequence s,int start,int before,int count){prefs.saveCustomInstructions(s.toString());}
      public void afterTextChanged(android.text.Editable s){}
    });
    activity.showPage("个性化",body);
  }

  void updateSettings() {
    LinearLayout entries = new LinearLayout(activity);
    entries.setOrientation(LinearLayout.VERTICAL);
    entries.setPadding(activity.dp(4), activity.dp(12), activity.dp(4), activity.dp(20));
    String version="";
    try { version=activity.getPackageManager().getPackageInfo(activity.getPackageName(),0).versionName; } catch (Exception ignored) {}
    activity.settingSection(entries, "当前版本 " + version);
    if (BuildConfig.CLAUDE_TEST) {
      entries.addView(activity.label("Claude 独立测试版：通过测试 APK 手动更新，与正式完整版的数据互不影响。",16,activity.muted));
      activity.showPage("应用更新", entries); return;
    }
    LinearLayout update = activity.row();
    update.setPadding(activity.dp(16), activity.dp(16), activity.dp(16), activity.dp(16));
    update.setBackground(activity.shape(UiStyle.SURFACE, 24));
    update.addView(activity.helpTitle("自动更新",18,activity.ink,"开启后，每天首次进入应用时检查并下载更新，当天最多自动检查一次。连接失败不会弹出警告；手动检查不受每日次数限制，并会显示结果。安装可能需要系统授权与确认。"),new LinearLayout.LayoutParams(0,-2,1));
    Switch autoUpdate=new Switch(activity); autoUpdate.setContentDescription("自动更新");
    autoUpdate.setChecked(ReleaseUpdater.isAutomaticEnabled(activity));
    autoUpdate.setOnCheckedChangeListener((v, checked) -> ReleaseUpdater.setAutomaticEnabled(activity, checked));
    update.addView(autoUpdate,new LinearLayout.LayoutParams(activity.dp(MainActivity.SETTINGS_ACTION_DP),activity.dp(48)));
    entries.addView(update);
    UpdateButton checkUpdate=new UpdateButton(activity);
    checkUpdate.setDetailsHandler(message -> {
      AlertDialog detail=new AlertDialog.Builder(activity).setTitle("更新详情").setMessage(message)
        .setPositiveButton("知道了",null).create();
      detail.show(); activity.styleDialog(detail,false);
    });
    activity.addAction(entries,checkUpdate,false);
    activity.showPage("应用更新", entries);
  }
}
