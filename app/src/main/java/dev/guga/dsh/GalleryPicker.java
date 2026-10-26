package dev.guga.dsh;

import android.content.*;
import android.content.pm.*;
import android.provider.MediaStore;
import java.util.*;

final class GalleryPicker {
  static Intent galleryRequest(){return new Intent(Intent.ACTION_PICK).setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,"image/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);}
  static int rank(String pkg){
    String[] preferred={"com.miui.gallery","com.coloros.gallery3d","com.oplus.gallery","com.vivo.gallery","com.android.gallery3d","com.android.gallery"};
    for(int n=0;n<preferred.length;n++)if(preferred[n].equals(pkg))return n;
    return pkg.toLowerCase(Locale.ROOT).contains("gallery")?preferred.length:100;
  }
  static Intent preferred(Context c){
    Intent request=galleryRequest();ResolveInfo best=choose(c.getPackageManager().queryIntentActivities(request,PackageManager.MATCH_DEFAULT_ONLY));
    if(best!=null)request.setComponent(new ComponentName(best.activityInfo.packageName,best.activityInfo.name));
    return request;
  }
  static ResolveInfo choose(List<ResolveInfo> candidates){
    ResolveInfo best=null;
    for(ResolveInfo candidate:candidates){
      ActivityInfo info=candidate.activityInfo;
      if(info==null||!info.exported||!info.enabled||rank(info.packageName)==100)continue;
      if(best==null||rank(info.packageName)<rank(best.activityInfo.packageName))best=candidate;
    }
    return best;
  }
  static Intent fallback(int remaining){
    if(android.os.Build.VERSION.SDK_INT>=33){Intent request=new Intent(MediaStore.ACTION_PICK_IMAGES).setType("image/*");if(remaining>1)request.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX,Math.min(remaining,MediaStore.getPickImagesMaxLimit()));return request;}
    return documentRequest();
  }
  static Intent documentRequest(){return new Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);}
}
