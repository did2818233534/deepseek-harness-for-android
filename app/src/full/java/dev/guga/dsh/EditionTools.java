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
    private static final java.util.concurrent.locks.ReentrantReadWriteLock environmentLock=new java.util.concurrent.locks.ReentrantReadWriteLock(true);
    static JSONObject run(Context c,FileTools f,JSONObject a)throws Exception{
        environmentLock.readLock().lockInterruptibly();try{return EngineJob.run(c,f,a);}finally{environmentLock.readLock().unlock();}
    }
    static JSONObject install(Context c,String name)throws Exception{
        environmentLock.writeLock().lockInterruptibly();try{return installLocked(c,name);}finally{environmentLock.writeLock().unlock();}
    }
    static JSONObject uninstall(Context c,String name)throws Exception{
        environmentLock.writeLock().lockInterruptibly();try{return uninstallLocked(c,name);}finally{environmentLock.writeLock().unlock();}
    }
    static boolean enabled(){return true;}
    static JSONObject environment(Context c,JSONObject args)throws Exception{return EnvironmentInfo.snapshot(c,args.optBoolean("detailed",true),args.optInt("offset"));}
    private static JSONObject uninstallLocked(Context c,String name)throws Exception {
        if(!name.matches("[a-z0-9][a-z0-9+.\\-]{0,99}"))throw new IOException("需要单个有效的软件包名");
        File prefix=new File(c.getNoBackupFilesDir(),"termux/usr");
        JSONObject seed=new JSONObject(EnvironmentInfo.read(new File(prefix,"seed-packages.json")));
        if(seed.has(name))throw new IOException("内置基础包不能卸载");
        File base=new File(prefix.getParentFile(),"packages"),manifest=new File(base,"installed.json");
        JSONObject installed=new JSONObject(EnvironmentInfo.read(manifest));
        if(!installed.has(name))return new JSONObject().put("package",name).put("alreadyAbsent",true);
        // Older records have no dependency metadata: conservatively retain them.
        for(java.util.Iterator<String> keys=installed.keys();keys.hasNext();) {
            String other=keys.next();if(other.equals(name))continue;
            org.json.JSONArray deps=installed.getJSONObject(other).optJSONArray("dependencies");
            if(deps==null)throw new IOException("旧安装记录缺少依赖信息，暂不能安全卸载");
            for(int i=0;i<deps.length();i++)if(name.equals(deps.optString(i)))throw new IOException("软件包仍被 "+other+" 依赖，不能卸载");
        }
        String directory=installed.getJSONObject(name).getString("directory");
        if(!directory.matches("[a-f0-9]{64}"))throw new IOException("安装记录损坏");
        installed.remove(name);
        File stage=File.createTempFile("manifest-",".json",base);
        try {Files.write(stage.toPath(),installed.toString().getBytes(StandardCharsets.UTF_8));
            Files.move(stage.toPath(),manifest.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }finally{stage.delete();EnvironmentInfo.invalidate();}
        // Exclusive mutation waits for active jobs; ordinary jobs can run concurrently.
        boolean shared=false;
        for(java.util.Iterator<String> keys=installed.keys();keys.hasNext();)if(directory.equals(installed.getJSONObject(keys.next()).optString("directory")))shared=true;
        JSONObject result=new JSONObject().put("package",name).put("removed",true);
        if(!shared)try{deleteTree(new File(base,directory).toPath());}
        catch(IOException cleanup){result.put("cleanupPending",true);}
        return result.put("environment",EnvironmentInfo.snapshot(c,false,0));
    }
    static void stopProcess(Process process) {
        if(process==null)return;
        boolean interrupted=Thread.interrupted();
        try {
            if(process.isAlive())process.destroyForcibly();
            for(;;)try{process.waitFor();break;}catch(InterruptedException e){interrupted=true;}
        }finally{if(interrupted)Thread.currentThread().interrupt();}
    }
    private static void deleteTree(Path path)throws IOException {
        if(!Files.exists(path,LinkOption.NOFOLLOW_LINKS))return;
        if(Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS))try(DirectoryStream<Path> children=Files.newDirectoryStream(path)){for(Path child:children)deleteTree(child);}
        Files.delete(path);
    }
    private static JSONObject installLocked(Context c,String name)throws Exception {
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
            EnvironmentInfo.invalidate();
            return new JSONObject(last).put("environment",EnvironmentInfo.snapshot(c,false,0));
        }finally{stopProcess(proc);EnvironmentInfo.invalidate();script.delete();log.delete();}
    }
}
