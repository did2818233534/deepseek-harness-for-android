package dev.guga.dsh;
import dev.guga.dsh.files.FileTools;
import android.app.Activity;
import android.os.Bundle;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import android.util.Log;
import org.json.*;
public class TermuxProbe extends Activity {
 @Override public void onCreate(Bundle b){super.onCreate(b); new Thread(()->{
 try {
 File prefix=TermuxEnvironment.ensure(this);
 String script="import csv,io,sys; from PIL import Image; s=io.BytesIO(); Image.new('RGB',(3,2),(1,2,3)).save(s,'PNG'); s.seek(0); assert Image.open(s).getpixel((1,1))==(1,2,3); assert list(csv.reader(['a,b']))==[['a','b']]; print(sys.version, 'PNG and CSV roundtrip OK')";
 File python=new File(prefix,"bin/python3").getCanonicalFile();
 ProcessBuilder pb=new ProcessBuilder("/system/bin/linker64",python.toString(),"-c",script);
 pb.environment().put("LD_LIBRARY_PATH",new File(prefix,"lib").toString());pb.environment().put("PYTHONHOME",prefix.toString());
 Process proc=pb.redirectErrorStream(true).start();try(java.io.BufferedReader reader=new java.io.BufferedReader(new java.io.InputStreamReader(proc.getInputStream(),StandardCharsets.UTF_8))){String line;while((line=reader.readLine())!=null)Log.i("DSHTermuxProbe",line);}
 Log.i("DSHTermuxProbe","exit="+proc.waitFor());
 if(getIntent().getBooleanExtra("installTest",false)){
     FileTools f=new FileTools(this);
     Log.i("DSHTermuxProbe","install="+EditionTools.install(this,"jq"));
     Log.i("DSHTermuxProbe","jq="+EditionTools.run(this,f,new JSONObject().put("program","jq").put("args",new JSONArray().put(".x+1")).put("input_text","{\"x\":41}")));
     Log.i("DSHTermuxProbe","python="+EditionTools.run(this,f,new JSONObject().put("code","buf=io.BytesIO(); Image.new('RGB',(8,4)).save(buf,'PNG'); result=str(Image.open(io.BytesIO(buf.getvalue())).size)")));
     try{EditionTools.run(this,f,new JSONObject().put("code","result=open('/proc/self/status').read()"));throw new AssertionError("guard missing");}
     catch(IOException expected){if(!expected.toString().contains("Operation not permitted"))throw expected;}
     Log.i("DSHTermuxProbe","blocked file read");
 }
 }catch(Exception e){Log.e("DSHTermuxProbe","failed",e);}finally{runOnUiThread(this::finish);}
 }).start();}
}
