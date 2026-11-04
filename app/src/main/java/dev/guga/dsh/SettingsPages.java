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

  SettingsPages(MainActivity activity) {
    this.activity = activity;
  }

  void settings() {
    LinearLayout entries = new LinearLayout(activity);
    entries.setOrientation(LinearLayout.VERTICAL);
    entries.setPadding(activity.dp(4), activity.dp(12), activity.dp(4), activity.dp(20));
    activity.settingSection(entries, "模型与服务");
    activity.settingEntry(entries, R.drawable.ic_model, "DeepSeek", "模型、API Key 与工具设置",
      true, true, v -> activity.modelSettings(false));
    activity.settingSection(entries, "聊天");
    activity.settingEntry(entries, R.drawable.ic_settings, "个性化", "设置适用于所有聊天的自定义指令", true, true, v -> personalization());
    activity.settingSection(entries, "数据管理");
    activity.settingEntry(entries, R.drawable.ic_archive, "已归档对话", "查看和恢复归档的聊天",
      true, false, v -> activity.navigation(true));
    activity.settingEntry(entries, R.drawable.ic_folder, "文件库", "管理导入资料和生成的文件",
      false, true, v -> activity.files());
    activity.settingSection(entries, "应用");
    activity.settingEntry(entries, R.drawable.ic_refresh, "应用更新", "自动更新与版本检查",
      true, true, v -> updateSettings());
    activity.showPage("设置", entries);
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
    edit.setBackground(activity.outline(0xFFF7F4FA, 16));
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
