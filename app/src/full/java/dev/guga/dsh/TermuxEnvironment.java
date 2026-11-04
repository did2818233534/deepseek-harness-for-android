package dev.guga.dsh;

import android.content.Context;
import android.os.Build;
import android.system.Os;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.zip.*;

/** Full-only offline seed. A new APK never re-seeds an existing environment. */
final class TermuxEnvironment {
    private TermuxEnvironment() {}
    static synchronized File ensure(Context context) throws Exception {
        File base=new File(context.getNoBackupFilesDir(),"termux");
        if (!base.isDirectory() && !base.mkdirs()) throw new IOException("不能创建运行目录");
        File prefix=new File(base,"usr");
        if(new File(prefix,".seed-v1").isFile())return prefix;
        if(prefix.exists())throw new IOException("已有运行环境不完整；保留现有文件");
        File stage=new File(base,"initializing");
        if(stage.exists())clear(stage);
        if(!stage.mkdir())throw new IOException("不能创建临时目录");
        String arch=Build.SUPPORTED_ABIS[0].equals("arm64-v8a")?"aarch64":Build.SUPPORTED_ABIS[0];
        try {
            try(ZipInputStream in=new ZipInputStream(new BufferedInputStream(context.getAssets().open("termux/"+arch+".zip")))){
                byte[] buf=new byte[65536];ZipEntry entry;
                while((entry=in.getNextEntry())!=null){
                    File f=child(stage,entry.getName());
                    if(entry.isDirectory()){Files.createDirectories(f.toPath());continue;}
                    Files.createDirectories(f.getParentFile().toPath());
                    try(OutputStream out=Files.newOutputStream(f.toPath(), StandardOpenOption.CREATE_NEW)){int len;while((len=in.read(buf))!=-1)out.write(buf,0,len);}
                    if(entry.getName().startsWith("bin/") || entry.getName().startsWith("libexec/"))Os.chmod(f.toString(),0700);
                }
            }
            Path links=new File(stage,"SYMLINKS.txt").toPath();
            for(String line:Files.readAllLines(links,StandardCharsets.UTF_8)){
                String[] pair=line.split("←",2);if(pair.length!=2)throw new IOException("无效的链接记录");
                File link=child(stage,pair[1]);Path target=Paths.get(pair[0]);
                if(target.isAbsolute()) {
                    // Absolute Android system symlinks (e.g. /system/bin/sh) are read-only.
                    if(!target.normalize().startsWith("/system"))continue;
                }else if(!link.toPath().getParent().resolve(target).normalize().startsWith(stage.toPath()))continue;
                Files.createDirectories(link.toPath().getParent());Os.symlink(pair[0],link.toString());
            }
            Files.delete(links);Files.createFile(new File(stage,".seed-v1").toPath());
            Os.rename(stage.toString(),prefix.toString());return prefix;
        }finally{if(stage.exists())clear(stage);}
    }
    private static File child(File root,String name)throws IOException {
        Path p=root.toPath().resolve(name).normalize();if(!p.startsWith(root.toPath())||p.equals(root.toPath())||Paths.get(name).isAbsolute())throw new IOException("无效的环境路径");
        return p.toFile();
    }
    private static void clear(File f)throws IOException{
        if(!Files.isSymbolicLink(f.toPath())&&f.isDirectory()){File[] files=f.listFiles();if(files==null)throw new IOException("不能读取临时目录");for(File child:files)clear(child);}
        Files.delete(f.toPath());
    }
}
