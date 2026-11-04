package dev.guga.dsh;
import dev.guga.dsh.settings.Settings;

import android.app.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import org.json.*;

final class ConversationDrawer {
  private final MainActivity activity;

  ConversationDrawer(MainActivity activity) {
    this.activity = activity;
  }

  void navigation(boolean archived) {
    if (archived) { activity.archivedPage(); return; }
    Dialog drawer = new Dialog(activity);
    LinearLayout panel = new LinearLayout(activity);
    panel.setOrientation(LinearLayout.VERTICAL);
    panel.setPadding(activity.dp(16), activity.dp(24), activity.dp(16), activity.dp(16));
    panel.setBackground(activity.shape(activity.bg, 24));
    TextView title = activity.label(archived ? "已归档" : "对话", 24, activity.ink);
    title.setPadding(activity.dp(8), 0, 0, activity.dp(12));
    panel.addView(title);
    ScrollView scrollList = new ScrollView(activity);
    LinearLayout list = new LinearLayout(activity);
    list.setOrientation(LinearLayout.VERTICAL);
    list.setPadding(0, activity.dp(4), 0, activity.dp(12));
    scrollList.addView(list);
    panel.addView(scrollList, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout footer = activity.row();
    footer.setPadding(0, activity.dp(12), 0, 0);
    Button create = activity.button(archived ? "返回对话" : "＋  新对话", v -> {
      drawer.dismiss(); if (archived) navigation(false); else activity.newChat();
    });
    create.setTextSize(16);
    create.setTextColor(Color.WHITE);
    create.setBackground(activity.shape(activity.accent, 26));
    footer.addView(create, new LinearLayout.LayoutParams(-2, activity.dp(52)));
    footer.addView(new View(activity), new LinearLayout.LayoutParams(0, 1, 1));
    ImageButton preferences = activity.icon(R.drawable.ic_settings, "设置", v -> { drawer.dismiss(); activity.settings(); });
    preferences.setBackground(activity.shape(0xFFECE6F0, 26));
    footer.addView(preferences, new LinearLayout.LayoutParams(activity.dp(52), activity.dp(52)));
    panel.addView(footer);
    drawer.setContentView(panel);
    drawer.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    drawer.getWindow().setGravity(Gravity.START);
    drawer.show();
    activity.trackDialog(drawer);
    drawer.getWindow().setLayout(Math.min(activity.dp(340), activity.getResources().getDisplayMetrics().widthPixels - activity.dp(48)), -1);
    activity.coreIo.execute(() -> {
      try {
        JSONArray sessions = Api.call("/sessions", new JSONObject().put("archived", archived)).getJSONArray("sessions");
        activity.runOnUiThread(() -> {
          if (!drawer.isShowing()) return;
          for (int i = 0; i < sessions.length(); i++) {
            JSONObject session = sessions.optJSONObject(i);
            Button item = activity.button(session.optString("title", "新对话"), v -> {
              drawer.dismiss();
              try { activity.request("/select", new JSONObject().put("id", session.getString("id")), () -> activity.revision = -1); }
              catch (Exception e) { activity.toast(e.getMessage()); }
            });
            item.setTextSize(17);
            item.setPadding(activity.dp(8), 0, activity.dp(4), 0);
            item.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            item.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout entry = activity.row();
            item.setBackground(new android.graphics.drawable.RippleDrawable(
              android.content.res.ColorStateList.valueOf(0x226750A4), activity.shape(Color.TRANSPARENT, 16), activity.shape(Color.WHITE, 24)));
            if (activity.currentId.equals(session.optString("id"))) {
              item.setTextColor(activity.accent);
              item.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            }
            entry.addView(item, new LinearLayout.LayoutParams(0, activity.dp(48), 1));
            ImageButton archive = activity.icon(archived ? R.drawable.ic_restore : R.drawable.ic_archive, archived ? "恢复对话" : "归档对话", v -> {
              archiveSession(session, archived, () -> list.removeView(entry));
            });
            archive.setPadding(activity.dp(15), activity.dp(15), activity.dp(15), activity.dp(15));
            archive.setTooltipText(archived ? "恢复对话" : "归档对话");
            entry.addView(archive, new LinearLayout.LayoutParams(activity.dp(48), activity.dp(48)));
            ImageButton delete = activity.icon(R.drawable.ic_delete, "删除对话", v -> { deleteSession(session, archived, () -> list.removeView(entry)); });
            delete.setPadding(activity.dp(15), activity.dp(15), activity.dp(15), activity.dp(15));
            delete.setImageTintList(android.content.res.ColorStateList.valueOf(0xFFC64040));
            delete.setTooltipText("删除对话");
            entry.addView(delete, new LinearLayout.LayoutParams(activity.dp(48), activity.dp(48)));
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, activity.dp(48));
            rowParams.bottomMargin = activity.dp(2);
            list.addView(entry, rowParams);
          }
        });
      } catch (Exception e) { activity.toast(e.getMessage()); }
    });
  }

  void archiveSession(JSONObject session, boolean archived, Runnable updated) {
    try {
      activity.request("/session/archive", new JSONObject().put("id", session.getString("id")).put("archived", !archived), () -> {
        activity.revision = -1;
        Toast.makeText(activity, archived ? "正在恢复对话" : "已归档，正在后台整理", Toast.LENGTH_SHORT).show();
        updated.run();
      });
    } catch (Exception e) { activity.toast(e.getMessage()); }
  }

  void deleteSession(JSONObject session, boolean archived, Runnable updated) {
    AlertDialog confirmation = new AlertDialog.Builder(activity).setTitle("删除对话？")
      .setMessage(new Settings(activity).cleanupConversationFiles() ? "将永久删除这段聊天记录及其关联文件，无法恢复。其他对话仍在使用的文件会保留。" : "将永久删除这段聊天记录，无法恢复。文件库中的文件会保留。")
      .setNegativeButton("取消", null)
      .setPositiveButton("删除", (dialog, which) -> {
        try {
          activity.request("/session/delete", new JSONObject().put("id", session.getString("id")), () -> {
            activity.transcriptViews.forget(session.optString("id"));
            activity.revision = -1;
            Toast.makeText(activity, "已删除", Toast.LENGTH_SHORT).show();
            updated.run();
          });
        } catch (Exception e) { activity.toast(e.getMessage()); }
      }).create();
    confirmation.show();
    activity.styleDialog(confirmation, true);
  }
}
