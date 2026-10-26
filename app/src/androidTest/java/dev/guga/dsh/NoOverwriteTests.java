package dev.guga.dsh;
import static org.junit.Assert.*;
import org.junit.Test;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;import java.util.*;import java.util.concurrent.*;import org.json.*;
public class NoOverwriteTests {
 @Test public void sameNamesAlwaysCreateNewFilesIncludingConcurrentWriters()throws Exception{
  var c=InstrumentationRegistry.getInstrumentation().getTargetContext();FileTools files=new FileTools(c);String folder="no-overwrite-"+System.nanoTime();File dir=files.resolve(folder);dir.mkdirs();ExecutorService pool=Executors.newFixedThreadPool(4);
  try{
   for(String ext:new String[]{"txt","docx","pdf"}){
    String requested=folder+"/报告."+ext;boolean doc=!ext.equals("txt");JSONObject args=new JSONObject().put("path",requested).put("content","original").put("overwrite",true);
    String first=files.write(args,doc).getString("path");assertTrue(first.matches(folder+"/报告_[0-9]{8}_[0-9]{6}_[0-9]{3}(\\([0-9]+\\))?\\."+ext));byte[] original=java.nio.file.Files.readAllBytes(files.resolve(first).toPath());
    args.put("content","new");String second=files.write(args,doc).getString("path"),third=new FileTools(c).write(args,doc).getString("path");assertNotEquals(first,second);assertNotEquals(second,third);assertArrayEquals(original,java.nio.file.Files.readAllBytes(files.resolve(first).toPath()));
   }
   List<Future<String>> futures=new ArrayList<>();for(int n=0;n<8;n++){int index=n;futures.add(pool.submit(()->new FileTools(c).write(new JSONObject().put("path",folder+"/parallel.txt").put("content","writer-"+index).put("overwrite",true),false).getString("path")));}
   Set<String> names=new HashSet<>(),contents=new HashSet<>();for(Future<String> future:futures){String name=future.get(10,TimeUnit.SECONDS);assertTrue(names.add(name));contents.add(new String(java.nio.file.Files.readAllBytes(files.resolve(name).toPath()),java.nio.charset.StandardCharsets.UTF_8));}assertEquals(8,contents.size());
  }finally{pool.shutdownNow();pool.awaitTermination(5,TimeUnit.SECONDS);for(File f:dir.listFiles())f.delete();dir.delete();}
 }
}
