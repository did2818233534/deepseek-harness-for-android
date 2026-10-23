package dev.guga.dsh;
import static org.junit.Assert.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.*;
import org.junit.Test;

public class ArtifactTests {
 @Test public void selectedFilesAreReadOnlyAndCannotEscapeWorkspace() throws Exception {
  var c=InstrumentationRegistry.getInstrumentation().getTargetContext();File file=new FileTools(c).resolve("artifact-test.txt");
  java.nio.file.Files.write(file.toPath(),"中文文件".getBytes(StandardCharsets.UTF_8));
  try {
   Uri uri=WorkspaceProvider.uri(c,"artifact-test.txt");
   try(InputStream in=c.getContentResolver().openInputStream(uri)){assertEquals("中文文件",new String(in.readAllBytes(),StandardCharsets.UTF_8));}
   try{c.getContentResolver().openFileDescriptor(uri,"w");fail("write grant accepted");}catch(FileNotFoundException expected){}
   try{WorkspaceProvider.resolve(c,WorkspaceProvider.uri(c,"../runtime/runtime-port"));fail("escaped workspace");}catch(FileNotFoundException expected){}
   assertNull(WorkspaceFiles.linkPath("../secret"));assertNull(WorkspaceFiles.linkPath("https://workspace.invalid"));assertNull(WorkspaceFiles.linkPath("file:///private"));
   assertEquals("文件.txt",WorkspaceFiles.linkPath("%E6%96%87%E4%BB%B6.txt"));
   Intent open=WorkspaceFiles.intent(c,"artifact-test.txt",false);assertEquals(Intent.ACTION_VIEW,open.getAction());assertEquals("text/plain",open.getType());assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION,open.getFlags());
   Intent share=WorkspaceFiles.intent(c,"artifact-test.txt",true);assertEquals(Intent.ACTION_SEND,share.getAction());assertEquals(uri,share.getParcelableExtra(Intent.EXTRA_STREAM));assertEquals(uri,share.getClipData().getItemAt(0).getUri());
   assertNotEquals(MainActivity.IMPORT_IMAGE,MessageActions.SAVE_MARKDOWN);
  }finally{file.delete();}
 }
 static void touch(View v,int action,long down,long time,float x,float y){MotionEvent e=MotionEvent.obtain(down,time,action,x,y,0);v.dispatchTouchEvent(e);e.recycle();}
 @Test public void inlineImageZoomLongPressAndMessageSelection() throws Exception {
  var i=InstrumentationRegistry.getInstrumentation();var c=i.getTargetContext();
  dev.guga.updates.UpdateTestDriver.prefs(c).edit().putString("checkedDay",dev.guga.updates.UpdateTestDriver.today()).commit();
  File file=new FileTools(c).resolve("artifact-image.png");Bitmap bitmap=Bitmap.createBitmap(320,180,Bitmap.Config.ARGB_8888);bitmap.eraseColor(0xFF8674C2);try(FileOutputStream out=new FileOutputStream(file)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
  MainActivity a=(MainActivity)i.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  JSONObject state=new JSONObject().put("id","artifacts").put("revision",9191).put("turns",new JSONArray().put(new JSONObject().put("id","1").put("userText","查看处理后的图片").put("text","已生成图片").put("artifacts",new JSONArray().put(new JSONObject().put("path",file.getName()).put("name",file.getName())))));
  try {
   i.runOnMainSync(()->{a.visible=false;a.render(state);assertEquals(1,a.turnViews.get("1").outputFiles.getChildCount());assertSame(a.turnViews.get("1").assistantBody,a.turnViews.get("1").outputFiles.getParent());assertSame(a.turnViews.get("1").assistantBody,a.turnViews.get("1").answer.getParent());a.turnViews.get("1").outputFiles.getChildAt(0).performClick();});
   long deadline=System.currentTimeMillis()+5000;while(a.currentPage==a.chatRoot&&System.currentTimeMillis()<deadline)Thread.sleep(30);
   i.waitForIdleSync();Thread.sleep(150);ZoomImageView[] image=new ZoomImageView[1];
   i.runOnMainSync(()->image[0]=(ZoomImageView)((LinearLayout)a.currentPage).getChildAt(1));
   long t=android.os.SystemClock.uptimeMillis();
   i.runOnMainSync(()->{touch(image[0],0,t,t,150,150);touch(image[0],1,t,t+40,150,150);touch(image[0],0,t+120,t+120,150,150);touch(image[0],1,t+120,t+160,150,150);assertTrue(image[0].zoom>1);});
   Thread.sleep(350);long press=android.os.SystemClock.uptimeMillis();i.runOnMainSync(()->touch(image[0],0,press,press,150,150));Thread.sleep(ViewConfiguration.getLongPressTimeout()+150);
   i.runOnMainSync(()->{touch(image[0],1,press,android.os.SystemClock.uptimeMillis(),150,150);assertFalse(a.dialogs.isEmpty());a.onBackPressed();assertNotSame(a.chatRoot,a.currentPage);});
   i.waitForIdleSync();i.runOnMainSync(()->{a.onBackPressed();assertSame(a.chatRoot,a.currentPage);});
   long textPress=android.os.SystemClock.uptimeMillis();i.runOnMainSync(()->touch(a.turnViews.get("1").answer,0,textPress,textPress,20,20));Thread.sleep(ViewConfiguration.getLongPressTimeout()+150);
   i.runOnMainSync(()->{touch(a.turnViews.get("1").answer,1,textPress,android.os.SystemClock.uptimeMillis(),20,20);assertTrue(a.messageActions.active());});
  }finally{i.runOnMainSync(a::finish);file.delete();}
 }
}
