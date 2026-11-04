package dev.guga.dsh;
import dev.guga.dsh.files.FileTools;
import android.content.Context;
import android.os.Build;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;
final class EditionTools {
    static JSONObject run(Context c,FileTools f,JSONObject a)throws Exception{return EngineJob.run(c,f,a);}
    static boolean enabled(){return true;}
    static synchronized JSONObject install(Context c,String name)throws Exception {
        if(!name.matches("[a-z0-9][a-z0-9+.\\-]{0,99}"))throw new IOException("需要单个有效的软件包名");
        File p=TermuxEnvironment.ensure(c);
        File script=File.createTempFile("install-",".py",c.getCacheDir()),log=File.createTempFile("install-",".log",c.getCacheDir());
        Process proc=null;
        try {
            try(InputStream in=c.getAssets().open("engine/install.py")){Files.copy(in,script.toPath(),StandardCopyOption.REPLACE_EXISTING);}
            ProcessBuilder pb=new ProcessBuilder("/system/bin/linker64",new File(p,"bin/python3").getCanonicalPath(),"-s",script.toString(),name,Build.SUPPORTED_ABIS[0].equals("arm64-v8a")?"aarch64":"x86_64");
            pb.environment().clear();pb.environment().put("PATH","/system/bin");pb.environment().put("LD_LIBRARY_PATH",new File(p,"lib").toString());pb.environment().put("PYTHONHOME",p.toString());pb.environment().put("TMPDIR",c.getCacheDir().toString());pb.environment().put("HOME",p.getParent());
            proc=pb.redirectErrorStream(true).redirectOutput(log).start();
            if(!proc.waitFor(180,TimeUnit.SECONDS))throw new IOException("安装超时");
            String result=new String(Files.readAllBytes(log.toPath()),StandardCharsets.UTF_8).trim();String last=result.substring(result.lastIndexOf('\n')+1);
            if(proc.exitValue()!=0)throw new IOException("安装失败："+last.substring(0,Math.min(350,last.length())));
            return new JSONObject(last);
        }finally{if(proc!=null&&proc.isAlive())proc.destroyForcibly();script.delete();log.delete();}
    }
}
