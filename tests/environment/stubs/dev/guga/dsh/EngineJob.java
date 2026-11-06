package dev.guga.dsh;
public class EngineJob {
 static volatile java.util.concurrent.CountDownLatch entered,release;
 static org.json.JSONObject run(android.content.Context c,dev.guga.dsh.files.FileTools f,org.json.JSONObject a)throws Exception {
  if(entered!=null){entered.countDown();if(!release.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("task timeout");}
  return new org.json.JSONObject();
 }
}
