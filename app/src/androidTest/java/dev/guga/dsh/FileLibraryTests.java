package dev.guga.dsh;
import static org.junit.Assert.*;
import org.junit.Test;
import androidx.test.platform.app.InstrumentationRegistry;
import android.content.*;
import android.view.*;
import java.io.*;
public class FileLibraryTests {
 @Test public void multiSelectCancelDeleteAndReopen()throws Exception{
  var i=InstrumentationRegistry.getInstrumentation();var c=i.getTargetContext();FileTools tools=new FileTools(c);
  File one=tools.resolve("000-multiselect-a.txt"),two=tools.resolve("000-multiselect-b.txt"),keep=tools.resolve("000-multiselect-keep.txt"),original=new File(c.getCacheDir(),"multiselect-original.txt");
  for(File f:new File[]{one,two,keep,original})java.nio.file.Files.write(f.toPath(),"test".getBytes());
  MainActivity a=(MainActivity)i.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  try{
   i.runOnMainSync(()->{a.settings();a.files();});waitReady(i,a);
   Thread.sleep(400);StreamingTests.screenshot(c,"library-initial.png");tap(i,a.fileLibrary.mode);assertTrue(a.fileLibrary.selecting);tap(i,a.fileLibrary.all);assertEquals(a.fileLibrary.rows.length(),a.fileLibrary.selected.size());tap(i,a.fileLibrary.all);assertTrue(a.fileLibrary.selected.isEmpty());tap(i,a.fileLibrary.all);assertFalse(a.fileLibrary.selected.isEmpty());
   i.runOnMainSync(a::onBackPressed);assertFalse(a.fileLibrary.selecting);assertSame(a.currentPage,a.fileLibrary.page);tap(i,a.fileLibrary.mode);
   StreamingTests.screenshot(c,"library-before-select.png");tap(i,row(a,"000-multiselect-a.txt"));tap(i,row(a,"000-multiselect-b.txt"));assertEquals(2,a.fileLibrary.selected.size());
   tap(i,a.fileLibrary.remove);tap(i,((android.app.AlertDialog)a.dialogs.peek()).getButton(-2));assertTrue(one.exists());assertTrue(two.exists());assertEquals(2,a.fileLibrary.selected.size());
   tap(i,a.fileLibrary.remove);tap(i,((android.app.AlertDialog)a.dialogs.peek()).getButton(-1));
   for(int n=0;n<100;n++){boolean[] done={false};i.runOnMainSync(()->done[0]=!a.fileLibrary.deleting);if(done[0])break;Thread.sleep(50);}
   assertFalse(one.exists());assertFalse(two.exists());assertTrue(keep.exists());assertTrue(original.exists());assertFalse(a.fileLibrary.selecting);
   i.runOnMainSync(()->{a.onBackPressed();a.files();});waitReady(i,a);assertNull(row(a,"000-multiselect-a.txt"));assertNotNull(row(a,"000-multiselect-keep.txt"));
   StreamingTests.screenshot(c,"file-library-multiselect.png");
  }finally{i.runOnMainSync(a::finish);for(File f:new File[]{one,two,keep,original})f.delete();}
 }
 View row(MainActivity a,String path){for(int n=0;n<a.fileLibrary.list.getChildCount();n++){View v=a.fileLibrary.list.getChildAt(n);if(path.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;}return null;}
 void waitReady(android.app.Instrumentation i,MainActivity a)throws Exception{for(int n=0;n<100;n++){boolean[] ready={false};i.runOnMainSync(()->ready[0]=a.fileLibrary.rows.length()>0);if(ready[0]){i.waitForIdleSync();return;}Thread.sleep(50);}fail("File list timed out");}
 void tap(android.app.Instrumentation i,View v)throws Exception{assertNotNull(v);Thread.sleep(150);i.waitForIdleSync();int[] p=new int[2];i.runOnMainSync(()->{v.getLocationOnScreen(p);p[0]+=v.getWidth()/2;p[1]+=v.getHeight()/2;});long t=android.os.SystemClock.uptimeMillis();MotionEvent d=MotionEvent.obtain(t,t,0,p[0],p[1],0),u=MotionEvent.obtain(t,t+60,1,p[0],p[1],0);i.sendPointerSync(d);i.sendPointerSync(u);d.recycle();u.recycle();i.waitForIdleSync();}
 @Test public void deleteNeverEscapesWorkspace()throws Exception{var c=InstrumentationRegistry.getInstrumentation().getTargetContext();FileTools tools=new FileTools(c);File original=new File(c.getFilesDir(),"source-test.txt");java.nio.file.Files.write(original.toPath(),new byte[]{1});try{try{tools.deleteLibraryFile("../source-test.txt");fail("must reject");}catch(IOException expected){}assertTrue(original.exists());tools.deleteLibraryFile("already-removed-file.txt");}finally{original.delete();}}
}
