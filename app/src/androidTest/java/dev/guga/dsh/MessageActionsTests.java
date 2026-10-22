package dev.guga.dsh;
import static org.junit.Assert.*;
import android.content.*;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.View;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.*;
import org.junit.Test;

public class MessageActionsTests {
  @Test public void longPressSelectExportsOnlySelectedMessagesAndBackExitsSelection() throws Exception {
    var i=InstrumentationRegistry.getInstrumentation();Context c=i.getTargetContext();
    dev.guga.updates.UpdateTestDriver.prefs(c).edit().putString("checkedDay",dev.guga.updates.UpdateTestDriver.today()).commit();
    MainActivity a=(MainActivity)i.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    JSONObject turn=new JSONObject().put("id","5").put("userText","中文问题👋").put("text","**中文答案**\n\n- 项目一").put("reasoning","不应导出思考内容").put("busy",false);
    JSONObject state=new JSONObject().put("id","export-test").put("revision",900).put("turns",new JSONArray().put(turn)).put("model","test");
    i.runOnMainSync(()->{
      a.visible=false;a.render(state);ChatTurnView view=a.turnViews.get("5");
      view.answer.performLongClick();assertTrue(a.messageActions.active());assertEquals(View.GONE,a.composer.getVisibility());
      assertTrue(a.messageActions.fork.isEnabled());view.user.performClick();assertEquals(2,a.messageActions.selected.size());assertFalse(a.messageActions.fork.isEnabled());
      String markdown=a.messageActions.markdown();assertTrue(markdown.indexOf("## 用户")<markdown.indexOf("## 助手"));
      assertTrue(markdown.contains("**中文答案**"));assertFalse(markdown.contains("不应导出"));
    });
    StreamingTests.screenshot(c,"message-selection.png");
    i.runOnMainSync(() -> {
      a.onBackPressed();assertFalse(a.messageActions.active());assertEquals(View.VISIBLE,a.composer.getVisibility());assertFalse(a.isFinishing());a.finish();
    });
  }
  @Test public void markdownFileCanBeSharedReadOnlyAndSavedToChosenUri() throws Exception {
    var i=InstrumentationRegistry.getInstrumentation();Context c=i.getTargetContext();
    dev.guga.updates.UpdateTestDriver.prefs(c).edit().putString("checkedDay",dev.guga.updates.UpdateTestDriver.today()).commit();
    MainActivity a=(MainActivity)i.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    String markdown="# 导出测试\n\n## 用户\n\n中文👋\n\n## 助手\n\n**答复**\n";
    File file=MessageActions.write(c,markdown);Intent share=MessageActions.shareIntent(c,file);Uri uri=share.getParcelableExtra(Intent.EXTRA_STREAM);
    Uri destination=null;
    try {
      assertEquals(Intent.ACTION_SEND,share.getAction());assertEquals("text/markdown",share.getType());assertNotNull(share.getClipData());
      assertTrue((share.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0);assertEquals(0,share.getFlags()&Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
      try(InputStream in=c.getContentResolver().openInputStream(uri)){assertEquals(markdown,new String(in.readAllBytes(),StandardCharsets.UTF_8));}
      try{c.getContentResolver().openOutputStream(uri);fail("share accepted write");}catch(FileNotFoundException expected){}
      try{ChatShareProvider.resolve(c,Uri.parse("content://"+c.getPackageName()+".chat-share/../settings.xml"));fail("path escape accepted");}catch(FileNotFoundException expected){}
      ContentValues values=new ContentValues();values.put(MediaStore.Downloads.DISPLAY_NAME,"dsh-export-test-"+System.nanoTime()+".md");values.put(MediaStore.Downloads.MIME_TYPE,"text/markdown");
      destination=c.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);assertNotNull(destination);
      a.messageActions.pendingExport=file.getName();a.messageActions.saveTo(destination);
      try(InputStream in=c.getContentResolver().openInputStream(destination)){assertEquals(markdown,new String(in.readAllBytes(),StandardCharsets.UTF_8));}
    } finally {file.delete();if(destination!=null)c.getContentResolver().delete(destination,null,null);i.runOnMainSync(a::finish);}
  }
}
