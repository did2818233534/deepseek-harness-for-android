package dev.guga.dsh;
import android.content.*;
import java.io.*;
import java.nio.file.*;
import org.json.*;
public class EnvironmentCheck {
 static void check(boolean value)throws Exception{if(!value)throw new Exception("assertion failed");}
 public static void main(String[] args)throws Exception{
  File root=new File(args[0]);root.mkdirs();Context c=new ContextWrapper(null){public File getNoBackupFilesDir(){return root;}};
  File seed=new File(root,"termux/usr/seed-packages.json"),manifest=new File(root,"termux/packages/installed.json");seed.getParentFile().mkdirs();manifest.getParentFile().mkdirs();
  Files.writeString(seed.toPath(),"{\"python\":\"3.12\"}");
  JSONObject a=EnvironmentInfo.snapshot(c,false,0);check(a.getString("summary").contains("python"));
  String hash="a".repeat(64);JSONObject pkg=new JSONObject().put("version","1").put("directory",hash).put("dependencies",new JSONArray());
  JSONObject all=new JSONObject().put("test-tool",pkg);Files.writeString(manifest.toPath(),all.toString());
  File payload=new File(manifest.getParent(),hash);payload.mkdirs();Files.writeString(new File(payload,"data").toPath(),"test");
  JSONObject b=EnvironmentInfo.snapshot(c,true,0);check(!a.getString("revision").equals(b.getString("revision")));check(b.getJSONArray("packages").length()==2);
  try{EditionTools.uninstall(c,"python");throw new AssertionError();}catch(IOException expected){}
  all.put("dependent",new JSONObject().put("version","1").put("directory","b".repeat(64)).put("dependencies",new JSONArray().put("test-tool")));Files.writeString(manifest.toPath(),all.toString());
  try{EditionTools.uninstall(c,"test-tool");throw new AssertionError();}catch(IOException expected){}
  all.remove("dependent");Files.writeString(manifest.toPath(),all.toString());
  check(EditionTools.uninstall(c,"test-tool").getBoolean("removed"));check(!payload.exists());
  check(EditionTools.uninstall(c,"test-tool").getBoolean("alreadyAbsent"));
  check(EnvironmentInfo.snapshot(c,true,0).getJSONArray("packages").length()==1);
  check(!b.getString("revision").equals(EnvironmentInfo.snapshot(c,false,0).getString("revision")));
  // Returned detailed rows must not mutate the shared cache; large offsets must not wrap.
  JSONObject page=EnvironmentInfo.snapshot(c,true,0);page.getJSONArray("packages").getJSONObject(0).put("name","corrupted");
  check(!EnvironmentInfo.snapshot(c,true,0).getJSONArray("packages").getJSONObject(0).getString("name").equals("corrupted"));
  check(EnvironmentInfo.snapshot(c,true,Integer.MAX_VALUE).getJSONArray("packages").length()==0);
  Files.writeString(manifest.toPath(),new JSONObject().put("test-tool",pkg).toString());payload.mkdirs();
  var pool=java.util.concurrent.Executors.newFixedThreadPool(4);
  try {
    EngineJob.entered=new java.util.concurrent.CountDownLatch(2);EngineJob.release=new java.util.concurrent.CountDownLatch(1);
    var first=pool.submit(()->EditionTools.run(c,null,new JSONObject()));
    var second=pool.submit(()->EditionTools.run(c,null,new JSONObject()));
    check(EngineJob.entered.await(2,java.util.concurrent.TimeUnit.SECONDS));
    var removing=pool.submit(()->EditionTools.uninstall(c,"test-tool"));
    try{removing.get(150,java.util.concurrent.TimeUnit.MILLISECONDS);throw new AssertionError("uninstall raced running jobs");}catch(java.util.concurrent.TimeoutException expected){}
    check(payload.exists());check(EnvironmentInfo.snapshot(c,true,0).getJSONArray("packages").length()==2);
    EngineJob.release.countDown();first.get(2,java.util.concurrent.TimeUnit.SECONDS);second.get(2,java.util.concurrent.TimeUnit.SECONDS);
    check(removing.get(2,java.util.concurrent.TimeUnit.SECONDS).getBoolean("removed"));check(!payload.exists());
    check(EditionTools.run(c,null,new JSONObject())!=null);
    // Interrupted queued jobs must not execute after cancellation.
    EngineJob.entered=new java.util.concurrent.CountDownLatch(1);EngineJob.release=new java.util.concurrent.CountDownLatch(1);
    var busy=pool.submit(()->EditionTools.run(c,null,new JSONObject()));check(EngineJob.entered.await(2,java.util.concurrent.TimeUnit.SECONDS));
    var canceled=pool.submit(()->EditionTools.uninstall(c,"absent"));canceled.cancel(true);
    EngineJob.release.countDown();busy.get(2,java.util.concurrent.TimeUnit.SECONDS);
  }finally{EngineJob.release.countDown();pool.shutdownNow();check(pool.awaitTermination(3,java.util.concurrent.TimeUnit.SECONDS));}
  Process process=new ProcessBuilder("sleep","10").start();
  Thread.currentThread().interrupt();EditionTools.stopProcess(process);
  check(!process.isAlive());check(Thread.interrupted());
  System.out.println("PASS shared cache invalidation, package versions, dependency protection, built-in protection, uninstall, repeat uninstall, physical cleanup, concurrent readers, exclusive uninstall, cancellation, pagination, cache isolation, process exit");
 }
}
