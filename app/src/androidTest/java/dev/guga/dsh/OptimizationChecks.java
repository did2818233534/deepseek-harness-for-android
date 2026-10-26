package dev.guga.dsh;
import static org.junit.Assert.*;
import org.junit.Test;
import android.content.*;
import java.io.*;
import java.util.*;
import org.json.*;
import androidx.test.platform.app.InstrumentationRegistry;

/** Stable local workloads: no remote model traffic, no user data. */
public class OptimizationChecks {
 static File fixture(){return new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir(),"optimization-encrypted.pdf");}
 @Test public void aPrepareFixtureIfNeeded()throws Exception{
  if(fixture().exists())return;
  var c=InstrumentationRegistry.getInstrumentation().getTargetContext();FileTools tools=new FileTools(c);
  String plain=tools.write(new JSONObject().put("path","optimization-fixture.pdf").put("content","PDF compatibility check\n".repeat(180)),true).getString("path");
  try(com.tom_roush.pdfbox.pdmodel.PDDocument doc=com.tom_roush.pdfbox.pdmodel.PDDocument.load(tools.resolve(plain))){
   var policy=new com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy("fixture-owner","",new com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission());policy.setEncryptionKeyLength(128);policy.setPreferAES(true);doc.protect(policy);doc.save(fixture());
  }finally{tools.resolve(plain).delete();}
 }
 @Test public void bEncryptedPdfAndLocalPerformance()throws Exception{
  var i=InstrumentationRegistry.getInstrumentation();var c=i.getTargetContext();assertTrue("Prepare baseline fixture first",fixture().exists());FileTools tools=new FileTools(c);
  File file=tools.resolve("optimization-read.pdf");java.nio.file.Files.copy(fixture().toPath(),file.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
  MainActivity a=(MainActivity)i.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  long[] cold=new long[7],warm=new long[7],pdf=new long[7];
  try{
   i.runOnMainSync(()->{a.visible=false;a.handler.removeCallbacks(a.poll);});
   for(int n=-2;n<7;n++){
    int index=n;JSONObject first=TranscriptPerformanceTests.state("audit-a-"+n,10000+n*2,35),second=TranscriptPerformanceTests.state("audit-b-"+n,10001+n*2,35);
    i.runOnMainSync(()->{long begin=System.nanoTime();a.render(first);if(index>=0)cold[index]=System.nanoTime()-begin;a.render(second);begin=System.nanoTime();a.render(first);if(index>=0)warm[index]=System.nanoTime()-begin;});i.waitForIdleSync();
    long begin=System.nanoTime();String text=tools.read(new JSONObject().put("path",file.getName())).getString("text");if(n>=0)pdf[n]=System.nanoTime()-begin;assertTrue(text.contains("PDF compatibility check"));
   }
   android.util.Log.i("DSH-Audit","cold="+Arrays.toString(cold)+" warm="+Arrays.toString(warm)+" pdf="+Arrays.toString(pdf));
  }finally{file.delete();i.runOnMainSync(a::finish);}
 }
}
