package dev.guga.dsh;
import static org.junit.Assert.*;
import android.content.*;
import android.content.pm.*;
import android.provider.MediaStore;
import java.util.*;
import org.junit.Test;
public class GalleryPickerTests {
 static ResolveInfo activity(String pkg){ResolveInfo r=new ResolveInfo();r.activityInfo=new ActivityInfo();r.activityInfo.packageName=pkg;r.activityInfo.name=pkg+".PickerFromInstalledManifest";r.activityInfo.enabled=true;r.activityInfo.exported=true;return r;}
 @Test public void selectsOnlyInstalledUsableVendorGallery(){
  ResolveInfo files=activity("com.android.fileexplorer");
  for(String pkg:new String[]{"com.miui.gallery","com.coloros.gallery3d","com.oplus.gallery","com.vivo.gallery"}){
   ResolveInfo gallery=activity(pkg);assertSame(gallery,GalleryPicker.choose(Arrays.asList(files,gallery)));
   gallery.activityInfo.exported=false;assertNull(GalleryPicker.choose(Arrays.asList(files,gallery)));gallery.activityInfo.exported=true;gallery.activityInfo.enabled=false;assertNull(GalleryPicker.choose(Arrays.asList(files,gallery)));
  }
  assertNull(GalleryPicker.choose(Collections.emptyList()));
 }
 @Test public void requestsKeepImageTypeAndUriAndHaveFallback(){
  Intent gallery=GalleryPicker.galleryRequest();assertEquals(Intent.ACTION_PICK,gallery.getAction());assertEquals(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,gallery.getData());assertEquals("image/*",gallery.getType());assertTrue(gallery.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE,false));
  Intent fallback=GalleryPicker.fallback(1);assertEquals("image/*",fallback.getType());assertFalse(fallback.hasExtra(MediaStore.EXTRA_PICK_IMAGES_MAX));
  assertEquals(Intent.ACTION_GET_CONTENT,GalleryPicker.documentRequest().getAction());assertTrue(GalleryPicker.documentRequest().hasCategory(Intent.CATEGORY_OPENABLE));
 }
}
