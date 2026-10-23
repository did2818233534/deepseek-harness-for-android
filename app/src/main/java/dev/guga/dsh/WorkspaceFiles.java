package dev.guga.dsh;

import android.app.Dialog;
import android.content.*;
import android.graphics.ImageDecoder;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

final class WorkspaceFiles {
  static String mime(String name){
    String lower=name.toLowerCase(Locale.ROOT);if(lower.endsWith(".md"))return "text/markdown";if(lower.endsWith(".svg"))return "image/svg+xml";
    int dot=lower.lastIndexOf('.');String type=dot<0?null:android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(lower.substring(dot+1));return type==null?"application/octet-stream":type;
  }
  static String linkPath(String link){
    if(link==null||link.isEmpty())return null;
    Uri uri=Uri.parse(link);String path;
    if("https".equals(uri.getScheme())&&"workspace.invalid".equals(uri.getHost())){String encoded=uri.getEncodedPath();if(encoded==null||encoded.length()<2||uri.getQuery()!=null||uri.getFragment()!=null)return null;path=Uri.decode(encoded.substring(1));}
    else if(uri.getScheme()==null&&uri.getAuthority()==null&&!link.startsWith("/")&&uri.getQuery()==null&&uri.getFragment()==null)path=Uri.decode(uri.getEncodedPath());
    else return null;
    if(path==null||path.isEmpty()||path.startsWith("/")||path.contains("\\")||java.util.Arrays.asList(path.split("/")).contains(".."))return null;
    return path;
  }
  static boolean openLink(MainActivity host,String link){String path=linkPath(link);if(path==null)return false;open(host,path);return true;}
  static Intent intent(Context c,String path,boolean share){
    Uri uri=WorkspaceProvider.uri(c,path);String type=mime(path);
    Intent intent=new Intent(share?Intent.ACTION_SEND:Intent.ACTION_VIEW);
    if(share)intent.setType(type).putExtra(Intent.EXTRA_STREAM,uri);else intent.setDataAndType(uri,type);
    intent.setClipData(ClipData.newUri(c.getContentResolver(),new File(path).getName(),uri));
    return intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
  }
  static void open(MainActivity host,String path){
    host.io.execute(()->{try{File f=new FileTools(host).resolve(path);if(!f.isFile())throw new IOException("文件不存在或已删除");
      host.runOnUiThread(()->{try{host.startActivity(Intent.createChooser(intent(host,path,false),"打开方式"));}catch(ActivityNotFoundException e){host.toast("没有可以打开此文件的应用，可长按文件链接分享或保存");}});
    }catch(Exception e){host.toast(e.getMessage());}});
  }
  static void share(MainActivity host,String path){
    host.io.execute(()->{try{WorkspaceProvider.resolve(host,WorkspaceProvider.uri(host,path));host.runOnUiThread(()->{try{host.startActivity(Intent.createChooser(intent(host,path,true),"分享文件"));}catch(ActivityNotFoundException e){host.toast("没有可分享的应用");}});}catch(Exception e){host.toast(e.getMessage());}});
  }
  static void save(MainActivity host,String path){
    host.exportPath=path;host.startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime(path)).putExtra(Intent.EXTRA_TITLE,new File(path).getName()),MainActivity.EXPORT);
  }
  static void saveImage(MainActivity host,String path){
    if(Build.VERSION.SDK_INT<29){save(host,path);return;}
    host.io.execute(()->{Uri target=null;try{
      File file=new FileTools(host).resolve(path);String type=mime(path);
      if(!type.startsWith("image/")||type.equals("image/svg+xml"))throw new IOException("该格式请保存为文件");
      ContentValues values=new ContentValues();values.put(MediaStore.Images.Media.DISPLAY_NAME,file.getName());values.put(MediaStore.Images.Media.MIME_TYPE,type);values.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/DSH Pocket");values.put(MediaStore.Images.Media.IS_PENDING,1);
      target=host.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);if(target==null)throw new IOException("无法创建相册图片");
      try(InputStream in=new FileInputStream(file);OutputStream out=host.getContentResolver().openOutputStream(target)){if(out==null)throw new IOException("无法保存图片");FileTools.copy(in,out,FileTools.MAX_BYTES);}
      ContentValues done=new ContentValues();done.put(MediaStore.Images.Media.IS_PENDING,0);host.getContentResolver().update(target,done,null,null);
      host.runOnUiThread(()->Toast.makeText(host,"已保存到相册",Toast.LENGTH_SHORT).show());
    }catch(Exception e){if(target!=null)try{host.getContentResolver().delete(target,null,null);}catch(Exception ignored){}host.toast(e.getMessage());}});
  }
  static void actions(MainActivity host,String path,boolean image){
    LinearLayout body=new LinearLayout(host);body.setOrientation(LinearLayout.VERTICAL);Dialog[] dialog=new Dialog[1];
    Button share=host.button("分享",v->{dialog[0].dismiss();share(host,path);});body.addView(share,host.spacedControl());
    Button save=host.button(image&&Build.VERSION.SDK_INT>=29?"保存到相册":"保存到文件",v->{dialog[0].dismiss();if(image)saveImage(host,path);else save(host,path);});body.addView(save,host.spacedControl());
    dialog[0]=host.bottomPanel(new File(path).getName(),body);
  }
  static Drawable decode(File file,int limit)throws IOException {
    return ImageDecoder.decodeDrawable(ImageDecoder.createSource(file),(decoder,info,source)->{
      int width=info.getSize().getWidth(),height=info.getSize().getHeight();float factor=Math.min(1f,limit/(float)Math.max(width,height));decoder.setTargetSize(Math.max(1,(int)(width*factor)),Math.max(1,(int)(height*factor)));decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
    });
  }
  static void preview(MainActivity host,String path){
    host.io.execute(()->{try{
      File file=new FileTools(host).resolve(path);if(!file.isFile()||file.length()>FileTools.MAX_BYTES)throw new IOException("图片不存在或过大");
      if(mime(path).equals("image/svg+xml")){
        String svg=new String(java.nio.file.Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8);host.runOnUiThread(()->SvgPreview.open(host,svg,path));return;
      }
      Drawable drawable=decode(file,2048);
      host.runOnUiThread(()->{if(host.isDestroyed())return;
        LinearLayout page=host.showPage("图片预览",new LinearLayout(host));page.removeViewAt(1);LinearLayout header=(LinearLayout)page.getChildAt(0);
        TextView title=host.label(file.getName(),16,UiStyle.INK);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);title.setPadding(host.dp(12),0,0,0);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        header.addView(host.icon(R.drawable.ic_share,"分享图片",v->actions(host,path,true)),new LinearLayout.LayoutParams(host.dp(48),host.dp(48)));
        ZoomImageView image=new ZoomImageView(host);image.setContentDescription("图片预览，可双指缩放、双击放大，长按分享或保存");image.setImageDrawable(drawable);image.setOnLongClickListener(v->{actions(host,path,true);return true;});page.addView(image,new LinearLayout.LayoutParams(-1,0,1));
        if(drawable instanceof Animatable)((Animatable)drawable).start();
        host.pageCleanup.put(page,()->{if(drawable instanceof Animatable)((Animatable)drawable).stop();image.setImageDrawable(null);});
      });
    }catch(Exception e){host.toast("无法预览图片："+e.getMessage());}});
  }
}
