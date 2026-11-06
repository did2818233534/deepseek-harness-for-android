package dev.guga.dsh;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;

/** Shared inventory, rebuilt only when either authoritative manifest changes. */
final class EnvironmentInfo {
  private static String fingerprint="";
  private static JSONObject cached;
  static synchronized void invalidate(){fingerprint="";cached=null;}
  static String read(File file)throws IOException{return file.isFile()?new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8):"{}";}
  static synchronized JSONObject snapshot(Context c,boolean detailed,int offset)throws Exception {
    File prefix=new File(c.getNoBackupFilesDir(),"termux/usr");
    String seed=read(new File(prefix,"seed-packages.json"));
    String added=read(new File(prefix.getParentFile(),"packages/installed.json"));
    String hash=android.util.Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest((seed+"\n"+added).getBytes(StandardCharsets.UTF_8)),android.util.Base64.NO_WRAP);
    if(cached==null||!hash.equals(fingerprint)) {
      JSONObject seeds=new JSONObject(seed),installed=new JSONObject(added);TreeSet<String> names=new TreeSet<>();
      seeds.keys().forEachRemaining(names::add);installed.keys().forEachRemaining(names::add);
      JSONArray packages=new JSONArray();List<String> brief=new ArrayList<>();
      for(String name:names){boolean builtin=seeds.has(name);String version=builtin?seeds.optString(name):installed.getJSONObject(name).optString("version");
        packages.put(new JSONObject().put("name",name).put("version",version).put("builtin",builtin));
        if(brief.size()<12&&(!builtin||name.equals("python")||name.contains("pillow")))brief.add(name+" "+version);
      }
      String summary=names.isEmpty()?"内置执行环境尚未初始化；首次执行或安装时自动准备。":
        "共享环境已记录 "+names.size()+" 个包。"+String.join("、",brief)+"。已安装的包无需重复安装；包存在不保证所有命令均兼容隔离执行。详细包名和版本请调用 environment_info 分页查询。";
      cached=new JSONObject().put("revision",hash).put("summary",summary).put("packages",packages);
      fingerprint=hash;
    }
    JSONObject out=new JSONObject().put("revision",cached.getString("revision")).put("summary",cached.getString("summary"));
    if(detailed){JSONArray all=cached.getJSONArray("packages"),page=new JSONArray();int start=Math.min(all.length(),Math.max(0,offset)),end=start+Math.min(50,all.length()-start);
      for(int i=start;i<end;i++)page.put(new JSONObject(all.getJSONObject(i).toString()));out.put("packages",page).put("total",all.length()).put("nextOffset",end<all.length()?end:JSONObject.NULL);}
    return out;
  }
}
