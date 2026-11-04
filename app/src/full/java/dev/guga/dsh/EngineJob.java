package dev.guga.dsh;
import dev.guga.dsh.files.FileTools;
import android.content.Context;
import android.util.Base64;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.json.*;
final class EngineJob {
 static JSONObject run(Context c,FileTools files,JSONObject args)throws Exception{
    File prefix=TermuxEnvironment.ensure(c),base=new File(prefix.getParentFile(),"packages");
    File dir=Files.createTempDirectory(c.getCacheDir().toPath(),"job-").toFile();
    File input=new File(dir,"in"),output=new File(dir,"out"),error=new File(dir,"err"),worker=new File(dir,"worker.py");
    Process process=null;
    try {
        byte[] data;String path=args.optString("input_path");
        if(!path.isEmpty()){try(InputStream in=new FileInputStream(files.resolve(path))){data=FileTools.readLimited(in,FileTools.MAX_BYTES);}}
        else data=args.optString("input_text").getBytes(StandardCharsets.UTF_8);
        File guard=new File(c.getApplicationInfo().nativeLibraryDir,"libdshguard.so");
        if(!guard.isFile())throw new IOException("当前安装包缺少任务隔离组件");
        String program=args.optString("program","python");List<String> cmd=new ArrayList<>();cmd.add("/system/bin/linker64");
        List<String> libs=new ArrayList<>();libs.add(new File(prefix,"lib").toString());boolean python=program.equals("python");
        if(python){
            cmd.add(new File(prefix,"bin/python3").getCanonicalPath());cmd.add("-s");cmd.add(worker.toString());cmd.add(guard.toString());
            try(InputStream in=c.getAssets().open("engine/python_worker.py")){Files.copy(in,worker.toPath());}
            Files.write(input.toPath(),new JSONObject().put("code",args.getString("code")).put("data",Base64.encodeToString(data,Base64.NO_WRAP)).toString().getBytes(StandardCharsets.UTF_8));
        }else{
            if(!program.matches("[a-z0-9][a-z0-9+.\\-]{0,99}"))throw new IOException("无效工具名");
            File index=new File(base,"installed.json");if(!index.isFile())throw new IOException("工具尚未安装");
            JSONObject installed=new JSONObject(new String(Files.readAllBytes(index.toPath()),StandardCharsets.UTF_8));
            File binary=null;for(Iterator<String> it=installed.keys();it.hasNext();){
                String key=it.next();String hash=installed.getJSONObject(key).getString("directory");
                if(!hash.matches("[a-f0-9]{64}"))throw new IOException("安装记录损坏");
                File pkg=new File(base,hash).getCanonicalFile();libs.add(new File(pkg,"lib").toString());
                File exe=new File(pkg,"bin/"+program).getCanonicalFile();
                if(exe.toPath().startsWith(pkg.toPath())&&exe.isFile())binary=exe;
            }
            if(binary==null)throw new IOException("未找到已安装的工具");
            // Static executables could skip LD_PRELOAD. Admit only dynamically linked ELF with PT_INTERP.
            if(!dynamicElf(binary))throw new IOException("该工具尚不支持隔离执行");
            cmd.add(binary.toString());JSONArray argv=args.optJSONArray("args");if(argv!=null)for(int i=0;i<argv.length();i++)cmd.add(argv.getString(i));
            Files.write(input.toPath(),data);
        }
        ProcessBuilder pb=new ProcessBuilder(cmd);pb.directory(dir);Map<String,String> env=pb.environment();env.clear();
        env.put("LD_LIBRARY_PATH",String.join(":",libs));env.put("PYTHONHOME",prefix.toString());env.put("PYTHONDONTWRITEBYTECODE","1");
        if(!python){env.put("LD_PRELOAD",guard.toString());env.put("DSH_LOCK_AT_LOAD","1");}
        process=pb.redirectInput(input).redirectOutput(output).redirectError(error).start();
        if(!process.waitFor(40,TimeUnit.SECONDS))throw new IOException("处理文件超时");
        if(process.exitValue()!=0){String reason;try(InputStream e=new FileInputStream(error)){byte[] b=new byte[2048];int n=e.read(b);reason=n>0?new String(b,0,n,StandardCharsets.UTF_8):"";}
            throw new IOException("处理文件失败 ("+process.exitValue()+") "+reason);}
        String out=args.optString("output_name");
        if(out.isEmpty()){
            if(output.length()>65536)throw new IOException("输出较大，请指定输出文件名");
            return new JSONObject().put("text",new String(Files.readAllBytes(output.toPath()),StandardCharsets.UTF_8));
        }
        try(InputStream in=new FileInputStream(output)){return new JSONObject().put("path",files.importFile(in,out)).put("bytes",output.length());}
    }finally{if(process!=null&&process.isAlive())process.destroyForcibly();for(File f:new File[]{input,output,error,worker,dir})f.delete();}
 }
 private static boolean dynamicElf(File f)throws IOException{
    try(RandomAccessFile r=new RandomAccessFile(f,"r")){byte[] header=new byte[64];r.readFully(header);
        java.nio.ByteBuffer b=java.nio.ByteBuffer.wrap(header).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        if(b.getInt(0)!=0x464c457f||header[4]!=2||header[5]!=1)return false;
        long start=b.getLong(32);int size=b.getShort(54)&65535,count=b.getShort(56)&65535;
        if(start<0||size<56||count>1024||start>f.length()-(long)size*count)return false;
        for(int i=0;i<count;i++){r.seek(start+(long)i*size);if(Integer.reverseBytes(r.readInt())==3)return true;}return false;
    }
 }
}
