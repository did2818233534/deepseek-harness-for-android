package dev.guga.updates;
import static org.junit.Assert.*;
import android.net.Uri;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.io.*;
import java.util.Arrays;

public class PatchUpdateTests {
  @Test public void preparesSignedUpgradeAndRejectsWrongBaseWithoutLosingReadyApk() throws Exception {
    var i=InstrumentationRegistry.getInstrumentation();var c=i.getTargetContext();
    org.junit.Assume.assumeTrue("Requires matching v3/v4 signed fixtures",c.getPackageManager().getPackageInfo(c.getPackageName(),0).getLongVersionCode()==3 && java.util.Arrays.asList(i.getContext().getAssets().list("")).contains("upgrade.dshpatch"));
    assertFalse(c.getSharedPreferences("settings",0).contains("key"));
    assertFalse(c.getSharedPreferences("settings",0).contains("anthropic.key"));
    File patch=new File(c.getCacheDir(),"test-update.dshpatch");
    try {
      try(InputStream in=i.getContext().getAssets().open("upgrade.dshpatch");OutputStream out=new FileOutputStream(patch)){byte[] bytes=new byte[65536];int count;while((count=in.read(bytes))!=-1)out.write(bytes,0,count);}
      for(int n=0;n<2;n++) {
        PatchUpdates.prepare(c,Uri.fromFile(patch));assertTrue(ReleaseUpdater.hasDownloadedUpdate(c));
        assertEquals(4,AppUpdates.prefs(c).getLong("ready",0));assertTrue(AppUpdates.valid(c,AppUpdates.apk(c),4));
      }
      byte[] ready=ApkPatch.digest(AppUpdates.apk(c));
      try(RandomAccessFile file=new RandomAccessFile(patch,"rw")){file.seek(8);file.write(new byte[32]);}
      try{PatchUpdates.prepare(c,Uri.fromFile(patch));fail("Wrong base accepted");}catch(IOException expected){}
      assertTrue(Arrays.equals(ready,ApkPatch.digest(AppUpdates.apk(c))));assertTrue(ReleaseUpdater.hasDownloadedUpdate(c));
      assertFalse(new File(AppUpdates.apk(c).getParentFile(),"patch-staged.apk").exists());
      // A target with the correct digest but a damaged APK signature must not become installable.
      try(InputStream in=i.getContext().getAssets().open("invalid-signature.dshpatch");OutputStream out=new FileOutputStream(patch)){byte[] bytes=new byte[65536];int count;while((count=in.read(bytes))!=-1)out.write(bytes,0,count);}
      try{PatchUpdates.prepare(c,Uri.fromFile(patch));fail("Invalid signature accepted");}catch(IOException expected){}
      assertTrue(Arrays.equals(ready,ApkPatch.digest(AppUpdates.apk(c))));
    }finally{patch.delete();AppUpdates.apk(c).delete();AppUpdates.prefs(c).edit().remove("ready").remove("name").remove("session").commit();}
  }
}
