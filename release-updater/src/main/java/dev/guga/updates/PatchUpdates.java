package dev.guga.updates;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import java.io.*;
import java.nio.file.*;

final class PatchUpdates {
  static void prepare(Context c,Uri uri) throws Exception { prepare(c,uri,new File(c.getApplicationInfo().sourceDir)); }
  static void prepare(Context c,Uri uri,File base) throws Exception {
    File folder=AppUpdates.apk(c).getParentFile();folder.mkdirs();
    File staged=new File(folder,"patch-staged.apk");staged.delete();
    try {
      if(c.getApplicationInfo().splitSourceDirs!=null && c.getApplicationInfo().splitSourceDirs.length>0)
        throw new IOException("分包安装暂不支持补丁，请使用完整 APK");
      int session=AppUpdates.prefs(c).getInt("session",-1);
      if(session>=0 && c.getPackageManager().getPackageInstaller().getSessionInfo(session)!=null)
        throw new IOException("请先完成或取消当前系统安装，再导入补丁");
      try(InputStream input=c.getContentResolver().openInputStream(uri)) {
        if(input==null)throw new IOException("无法读取补丁文件");
        ApkPatch.apply(base,input,staged);
      }
      var next=c.getPackageManager().getPackageArchiveInfo(staged.getPath(),PackageManager.GET_SIGNING_CERTIFICATES);
      if(next==null||!AppUpdates.valid(c,staged,next.getLongVersionCode()))
        throw new IOException("安装包签名、包名或升级版本不匹配，请使用正确的完整 APK");
      Files.move(staged.toPath(),AppUpdates.apk(c).toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
      AppUpdates.prefs(c).edit().putLong("ready",next.getLongVersionCode()).putString("name",next.versionName).remove("session").commit();
      AppUpdates.readyResult=true;
    } finally {staged.delete();}
  }
}
