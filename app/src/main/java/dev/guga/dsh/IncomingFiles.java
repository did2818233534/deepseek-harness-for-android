package dev.guga.dsh;
import android.content.*;
import android.net.Uri;
import java.util.*;

/** External content is copied into drafts, never automatically sent or modified. */
final class IncomingFiles {
 static boolean accepts(Intent intent){return intent!=null&&(Intent.ACTION_VIEW.equals(intent.getAction())||Intent.ACTION_SEND.equals(intent.getAction())||Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction()));}
 static List<Uri> uris(Intent intent){
  LinkedHashSet<Uri> result=new LinkedHashSet<>();
  if(!accepts(intent))return new ArrayList<>();
  if(Intent.ACTION_VIEW.equals(intent.getAction()))add(result,intent.getData());
  if(Intent.ACTION_SEND.equals(intent.getAction())){Object value=intent.getParcelableExtra(Intent.EXTRA_STREAM);if(value instanceof Uri)add(result,(Uri)value);}
  if(Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())){ArrayList<?> values=intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);if(values!=null)for(Object value:values)if(value instanceof Uri)add(result,(Uri)value);}
  ClipData clip=intent.getClipData();if(clip!=null)for(int n=0;n<clip.getItemCount();n++)add(result,clip.getItemAt(n).getUri());
  return new ArrayList<>(result);
 }
 private static void add(Set<Uri> result,Uri uri){if(uri!=null&&"content".equals(uri.getScheme())&&result.size()<9)result.add(uri);}
 static void receive(MainActivity host,Intent intent){
  if(!accepts(intent))return;
  try{
   List<Uri> files=uris(intent);
   if(files.isEmpty()){host.toast("未收到可读取的文件，请从来源应用重新分享或打开");return;}
   new IncomingConversationPicker(host,files).open();
  }catch(Exception e){host.toast("无法读取来源文件，请重新分享并允许读取文件");}
 }
}
