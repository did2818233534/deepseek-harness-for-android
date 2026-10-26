package dev.guga.dsh;
import static org.junit.Assert.*;
import org.junit.Test;
import android.content.*;
import android.net.Uri;
import android.content.pm.PackageManager;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.util.*;
public class IncomingFilesTests {
 @Test public void resolverAndSafeUriCollection(){
  var c=InstrumentationRegistry.getInstrumentation().getTargetContext();Uri u=Uri.parse("content://files/report.pdf");
  for(String action:new String[]{Intent.ACTION_VIEW,Intent.ACTION_SEND,Intent.ACTION_SEND_MULTIPLE}){
   Intent x=new Intent(action).setType("application/pdf");if(action.equals(Intent.ACTION_VIEW))x.setDataAndType(u,"application/pdf");
   x.setPackage(c.getPackageName());assertFalse(c.getPackageManager().queryIntentActivities(x,PackageManager.MATCH_DEFAULT_ONLY).isEmpty());
  }
  Intent x=new Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM,u);x.setClipData(ClipData.newRawUri("file",u));assertEquals(1,IncomingFiles.uris(x).size());
  assertTrue(IncomingFiles.uris(new Intent(Intent.ACTION_VIEW,Uri.parse("file:///data/private"))).isEmpty());
  assertTrue(IncomingFiles.uris(new Intent(Intent.ACTION_VIEW,Uri.parse("https://example.com"))).isEmpty());
  ArrayList<Uri> batch=new ArrayList<>();for(int n=0;n<20;n++)batch.add(Uri.parse("content://files/"+n));
  assertEquals(9,IncomingFiles.uris(new Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM,batch)).size());
 }
 @Test public void coldAndWarmOpenCopyToDraftAndLeaveOriginal()throws Exception{
  var i=InstrumentationRegistry.getInstrumentation();var c=i.getTargetContext();
  File dir=ChatShareProvider.directory(c);dir.mkdirs();File original=new File(dir,"chat-abcdef.md");java.nio.file.Files.write(original.toPath(),"# 原件".getBytes(java.nio.charset.StandardCharsets.UTF_8));
  Uri u=new Uri.Builder().scheme("content").authority(c.getPackageName()+".chat-share").appendPath(original.getName()).build();
  Intent intent=new Intent(c,MainActivity.class).setAction(Intent.ACTION_VIEW).setDataAndType(u,"text/markdown").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_GRANT_READ_URI_PERMISSION);
  MainActivity a=(MainActivity)i.startActivitySync(intent);ArrayList<String> copies=new ArrayList<>();String[] firstId={""};
  try{
   i.runOnMainSync(()->{assertEquals(0,a.attachments.pending.size());assertTrue(DeviceTests.containsText(a.dialogs.peek().getWindow().getDecorView(),"选择对话"));a.onBackPressed();assertEquals(0,a.attachments.pending.size());a.onNewIntent(intent);});
   tap(i,a,"新建对话");tap(i,a,"添加到对话");awaitImports(i,a);
   i.runOnMainSync(()->{assertEquals(1,a.attachments.pending.size());copies.add(a.attachments.pending.get(0).optString("path"));firstId[0]=a.currentId;assertEquals("",a.input.getText().toString());a.attachments.reset();a.settings();a.onNewIntent(intent);});
   tap(i,a,"新建对话");tap(i,a,"添加到对话");awaitImports(i,a);
   i.runOnMainSync(()->{assertSame(a.chatRoot,a.currentPage);assertEquals(1,a.attachments.pending.size());copies.add(a.attachments.pending.get(0).optString("path"));});
   assertNotEquals(copies.get(0),copies.get(1));
   IncomingConversationPicker[] picker={null};
   i.runOnMainSync(()->{a.attachments.reset();picker[0]=new IncomingConversationPicker(a,java.util.List.of(u));picker[0].open();});
   for(int n=0;n<100;n++){boolean[] loaded={false};i.runOnMainSync(()->loaded[0]=!picker[0].loading);if(loaded[0])break;Thread.sleep(50);}
   i.runOnMainSync(()->{try{picker[0].sessions=new org.json.JSONArray().put(new org.json.JSONObject().put("id",firstId[0]).put("title","已有目标对话"));picker[0].search.setText("不存在");assertEquals(0,picker[0].list.getChildCount());picker[0].search.setText("目标");assertEquals(1,picker[0].list.getChildCount());}catch(Exception e){throw new RuntimeException(e);}});
   tap(i,a,"已有目标对话");tap(i,a,"添加到对话");awaitImports(i,a);
   i.runOnMainSync(()->{assertEquals(firstId[0],a.currentId);assertEquals(1,a.attachments.pending.size());copies.add(a.attachments.pending.get(0).optString("path"));});

   for(String p:copies){File copy=new FileTools(c).resolve(p);assertEquals("# 原件",new String(java.nio.file.Files.readAllBytes(copy.toPath()),java.nio.charset.StandardCharsets.UTF_8));assertTrue(copy.delete());}
   assertEquals("# 原件",new String(java.nio.file.Files.readAllBytes(original.toPath()),java.nio.charset.StandardCharsets.UTF_8));
  }finally{i.runOnMainSync(a::finish);for(String p:copies)new FileTools(c).resolve(p).delete();original.delete();}
 }
 android.view.View find(android.view.View v,String text){
  if(text.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;
  if(v instanceof android.widget.Button&&text.contentEquals(((android.widget.Button)v).getText()))return v;
  if(v instanceof android.view.ViewGroup){var g=(android.view.ViewGroup)v;for(int n=0;n<g.getChildCount();n++){var found=find(g.getChildAt(n),text);if(found!=null)return found;}}return null;
 }
 void tap(android.app.Instrumentation i,MainActivity a,String text)throws Exception{
  i.waitForIdleSync();int[] xy=new int[2];i.runOnMainSync(()->{var v=find(a.dialogs.peek().getWindow().getDecorView(),text);assertNotNull(v);assertTrue(v.isEnabled());v.getLocationOnScreen(xy);xy[0]+=v.getWidth()/2;xy[1]+=v.getHeight()/2;});
  long t=android.os.SystemClock.uptimeMillis();var down=android.view.MotionEvent.obtain(t,t,0,xy[0],xy[1],0);var up=android.view.MotionEvent.obtain(t,t+60,1,xy[0],xy[1],0);i.sendPointerSync(down);i.sendPointerSync(up);down.recycle();up.recycle();i.waitForIdleSync();
 }
 void awaitImports(android.app.Instrumentation i,MainActivity a)throws Exception{
  for(int n=0;n<400;n++){boolean[] ready={false};i.runOnMainSync(()->ready[0]=a.attachments.importing==0&&!a.attachments.pending.isEmpty());if(ready[0])return;Thread.sleep(50);}StreamingTests.screenshot(i.getTargetContext(),"incoming-failure.png");fail("Import timed out");
 }
}
