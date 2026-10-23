package dev.guga.dsh;

import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import org.json.*;

/** Local attachment drafts and bounded thumbnail decoding. Files are sent only on Send. */
final class Attachments {
  final MainActivity host;
  int generation, importing;
  void beginImport(){importing++;host.send.setEnabled(false);}
  void endImport(){host.runOnUiThread(()->{importing--;if(!host.isDestroyed())host.send.setEnabled(!host.sending&&importing==0);});}
  final ArrayList<JSONObject> pending=new ArrayList<>();
  final HorizontalScrollView tray;
  final LinearLayout row;
  Attachments(MainActivity host) {
    this.host=host; tray=new HorizontalScrollView(host);tray.setHorizontalScrollBarEnabled(false);
    row=new LinearLayout(host);row.setPadding(0,host.dp(4),0,host.dp(8));tray.addView(row);render();
  }
  void reset(){generation++;pending.clear();render();}
  JSONArray snapshot(){return new JSONArray(pending);}
  void restore(String value){try{JSONArray a=new JSONArray(value);for(int n=0;n<Math.min(8,a.length());n++)pending.add(a.getJSONObject(n));}catch(Exception ignored){}render();}
  void clearSent(JSONArray sent){for(int n=0;n<sent.length();n++){String path=sent.optJSONObject(n).optString("path");pending.removeIf(a->a.optString("path").equals(path));}render();}
  void add(JSONObject item) {
    for(JSONObject old:pending)if(old.optString("path").equals(item.optString("path")))return;
    long total=item.optLong("bytes");for(JSONObject old:pending)total+=old.optLong("bytes");
    if(pending.size()>=8||total>32L*1024*1024){host.toast("每条消息最多添加 8 个附件，总大小不超过 32 MB");return;}
    pending.add(item);render();
  }
  void render(){row.removeAllViews();tray.setVisibility(pending.isEmpty()?View.GONE:View.VISIBLE);for(JSONObject item:pending)row.addView(card(host,item,()->{pending.remove(item);render();},null,null));}
  static JSONObject describe(FileTools tools,String path,String suggestedMime)throws Exception {
    File f=tools.resolve(path);if(!f.isFile()||f.length()>FileTools.MAX_BYTES)throw new IOException("附件不存在或超过 16 MB");
    BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(f.getPath(),bounds);
    String mime=bounds.outMimeType;
    if(mime==null){mime=suggestedMime;if(mime==null||mime.isEmpty()){String ext=android.webkit.MimeTypeMap.getFileExtensionFromUrl(f.getName().replace(" ","_"));mime=android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase(Locale.ROOT));}}
    return new JSONObject().put("path",path).put("name",f.getName()).put("mime",mime==null?"application/octet-stream":mime).put("bytes",f.length());
  }
  void addPath(String path){int token=generation;beginImport();host.io.execute(()->{try{JSONObject item=describe(new FileTools(host),path,null);host.runOnUiThread(()->{if(token==generation&&!host.isDestroyed())add(item);});}catch(Exception e){host.toast(e.getMessage());}finally{endImport();}});}
  void importUris(List<Uri> uris){int token=generation;beginImport();host.io.execute(()->{try{
    for(Uri uri:uris)try{
      String name="附件";try(Cursor c=host.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())name=c.getString(0);}
      FileTools tools=new FileTools(host);String path;try(InputStream in=host.getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("无法读取附件");path=tools.importFile(in,name);}
      JSONObject item=describe(tools,path,host.getContentResolver().getType(uri));host.runOnUiThread(()->{if(token==generation&&!host.isDestroyed())add(item);});
    }catch(Exception e){host.toast(e.getMessage());}
    }finally{endImport();}
  });}
  static View card(MainActivity host,JSONObject item,Runnable remove,Runnable click,Runnable hold){
    LinearLayout card=new LinearLayout(host);card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(host.dp(8),host.dp(8),0,host.dp(8));card.setBackground(host.ui.surface(UiStyle.LOW,16));
    LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(host.dp(remove==null?224:216),-2);cp.rightMargin=host.dp(8);cp.bottomMargin=host.dp(6);card.setLayoutParams(cp);
    ImageView image=new ImageView(host);image.setImageResource(R.drawable.ic_folder);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setContentDescription(item.optString("name"));card.addView(image,new LinearLayout.LayoutParams(host.dp(48),host.dp(48)));
    TextView name=host.label(item.optString("name",item.optString("path")),13,host.ink);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);name.setPadding(host.dp(8),0,host.dp(8),0);card.addView(name,new LinearLayout.LayoutParams(0,-2,1));
    if(remove!=null)card.addView(host.icon(R.drawable.ic_close,"移除 "+item.optString("name"),v->remove.run()),new LinearLayout.LayoutParams(host.dp(48),host.dp(48)));
    if(click!=null)card.setOnClickListener(v->click.run());if(hold!=null)card.setOnLongClickListener(v->{hold.run();return true;});
    if(item.optString("mime").startsWith("image/"))host.io.execute(()->{try{
      File file=new FileTools(host).resolve(item.optString("storedPath",item.optString("path")));BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),o);
      int size=host.dp(96);o.inSampleSize=1;while(o.outWidth/o.inSampleSize>size*2||o.outHeight/o.inSampleSize>size*2)o.inSampleSize*=2;o.inJustDecodeBounds=false;
      android.graphics.Bitmap bitmap=BitmapFactory.decodeFile(file.getPath(),o);if(bitmap!=null)host.runOnUiThread(()->{if(!host.isDestroyed())image.setImageBitmap(bitmap);});
    }catch(Exception ignored){}});
    return card;
  }
}
