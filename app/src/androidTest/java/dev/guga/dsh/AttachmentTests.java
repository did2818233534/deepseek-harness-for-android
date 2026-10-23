package dev.guga.dsh;
import static org.junit.Assert.*;
import android.graphics.*;
import android.widget.*;
import android.view.*;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.json.*;
import java.io.*;

public class AttachmentTests {
  @Test public void imageAndFileEntriesLaunchSeparateSystemPickers()throws Exception {
    var i=InstrumentationRegistry.getInstrumentation();var c=i.getTargetContext();
    dev.guga.updates.UpdateTestDriver.prefs(c).edit().putString("checkedDay",dev.guga.updates.UpdateTestDriver.today()).commit();
    MainActivity a=(MainActivity)i.startActivitySync(new android.content.Intent(c,MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
    android.content.IntentFilter imageFilter=new android.content.IntentFilter(android.os.Build.VERSION.SDK_INT>=33?android.provider.MediaStore.ACTION_PICK_IMAGES:android.content.Intent.ACTION_PICK);imageFilter.addDataType("image/*");
    android.content.IntentFilter fileFilter=new android.content.IntentFilter(android.content.Intent.ACTION_OPEN_DOCUMENT);fileFilter.addCategory(android.content.Intent.CATEGORY_OPENABLE);fileFilter.addDataType("*/*");
    var photo=new android.app.Instrumentation.ActivityMonitor(imageFilter,new android.app.Instrumentation.ActivityResult(0,null),true);
    var file=new android.app.Instrumentation.ActivityMonitor(fileFilter,new android.app.Instrumentation.ActivityResult(0,null),true);
    i.addMonitor(photo);i.addMonitor(file);
    try {
      i.runOnMainSync(()->{a.visible=false;assertEquals("image/*",a.imagePickerIntent().getType());a.importImage();a.importFile();a.importImage();a.importFile();});
      assertEquals(2,photo.getHits());assertEquals(2,file.getHits());
      i.runOnMainSync(()->{a.attachmentMenu();assertTrue(DeviceTests.containsText(a.dialogs.peek().getWindow().getDecorView(),"图片"));assertTrue(DeviceTests.containsText(a.dialogs.peek().getWindow().getDecorView(),"文件"));a.onBackPressed();assertSame(a.chatRoot,a.currentPage);});
    } finally {i.removeMonitor(photo);i.removeMonitor(file);i.runOnMainSync(a::finish);}
  }
  @Test public void draftsRemoveRestoreAndAttachmentOnlySelection() throws Exception {
    var i=InstrumentationRegistry.getInstrumentation();var c=i.getTargetContext();
    dev.guga.updates.UpdateTestDriver.prefs(c).edit().putString("checkedDay",dev.guga.updates.UpdateTestDriver.today()).commit();
    MainActivity a=(MainActivity)i.startActivitySync(new android.content.Intent(c,MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
    FileTools tools=new FileTools(c);File picture=tools.resolve("attachment-test.png");
    Bitmap bitmap=Bitmap.createBitmap(800,600,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.BLUE);try(FileOutputStream out=new FileOutputStream(picture)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
    JSONObject item=Attachments.describe(tools,"attachment-test.png","application/octet-stream");assertEquals("image/png",item.getString("mime"));
    try {
      i.runOnMainSync(()->{a.visible=false;a.attachments.add(item);assertEquals(1,a.attachments.pending.size());a.attachments.add(item);assertEquals(1,a.attachments.pending.size());
        String saved=a.attachments.snapshot().toString();a.attachments.reset();assertEquals(View.GONE,a.attachments.tray.getVisibility());a.attachments.restore(saved);assertEquals(1,a.attachments.row.getChildCount());
        // Remove through the visible close control, then add again.
        ((LinearLayout)a.attachments.row.getChildAt(0)).getChildAt(2).performClick();assertEquals(0,a.attachments.pending.size());a.attachments.add(item);
        JSONArray sent=a.attachments.snapshot();a.attachments.clearSent(sent);assertEquals(0,a.attachments.pending.size());
      });
      i.runOnMainSync(()->{try{
        a.render(new JSONObject().put("id","attachment-only-session").put("revision",99001).put("turns",new JSONArray().put(new JSONObject().put("id","attachment-only").put("userText","").put("attachments",new JSONArray().put(item)))));ChatTurnView turn=a.turnViews.get("attachment-only");
        assertEquals(View.GONE,turn.user.getVisibility());assertEquals(1,turn.attachmentRow.getChildCount());a.messageActions.selectAll();assertTrue(a.messageActions.selected.contains("attachment-only/u"));assertTrue(a.messageActions.markdown().contains("attachment-test.png"));a.messageActions.selectAll();assertTrue(a.messageActions.selected.isEmpty());a.messageActions.selectAll();assertEquals(1,a.messageActions.selected.size());a.messageActions.clear();
      }catch(Exception e){throw new RuntimeException(e);}});
      Thread.sleep(250);i.waitForIdleSync();
      StreamingTests.screenshot(c,"attachment-card.png");
      long time=android.os.SystemClock.uptimeMillis();
      i.runOnMainSync(()->{MotionEvent e=MotionEvent.obtain(time,time,MotionEvent.ACTION_DOWN,30,30,0);a.turnViews.get("attachment-only").attachmentRow.getChildAt(0).dispatchTouchEvent(e);e.recycle();});
      Thread.sleep(android.view.ViewConfiguration.getLongPressTimeout()+150);
      i.runOnMainSync(()->{MotionEvent e=MotionEvent.obtain(time,android.os.SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,30,30,0);a.turnViews.get("attachment-only").attachmentRow.getChildAt(0).dispatchTouchEvent(e);e.recycle();assertTrue(a.messageActions.selected.contains("attachment-only/u"));a.messageActions.clear();});
    }finally{i.runOnMainSync(a::finish);picture.delete();}
  }
  @Test public void originalEditedDoesNotChangeHistoricalThumbnailPath()throws Exception {
    var c=InstrumentationRegistry.getInstrumentation().getTargetContext();FileTools tools=new FileTools(c);
    File original=tools.resolve("attachment-original.txt"), snapshot=tools.resolve(".attachments/test-snapshot");snapshot.getParentFile().mkdirs();
    try{java.nio.file.Files.write(original.toPath(),"before".getBytes());java.nio.file.Files.copy(original.toPath(),snapshot.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);java.nio.file.Files.write(original.toPath(),"after".getBytes());
      JSONObject ref=new JSONObject().put("path","attachment-original.txt").put("storedPath",".attachments/test-snapshot");assertEquals("before",new String(java.nio.file.Files.readAllBytes(tools.resolve(ref.optString("storedPath",ref.optString("path"))).toPath()),java.nio.charset.StandardCharsets.UTF_8));
    }finally{original.delete();snapshot.delete();}
  }
}
