package dev.guga.dsh;
import dev.guga.dsh.files.FileTools;

import android.graphics.drawable.Animatable;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;

/** Renders successful file outputs and local Markdown image references. */
final class ArtifactViews {
  static JSONArray collect(JSONObject turn){
    LinkedHashMap<String,JSONObject> files=new LinkedHashMap<>();JSONArray outputs=turn.optJSONArray("artifacts");
    if(outputs!=null)for(int n=0;n<outputs.length();n++){JSONObject f=outputs.optJSONObject(n);if(f!=null)files.put(f.optString("path"),f);}
    Matcher images=Pattern.compile("!\\[[^\\]]*\\]\\((?:<([^>]+)>|([^\\s)]+))\\)").matcher(turn.optString("text"));
    while(images.find()){String path=WorkspaceFiles.linkPath(images.group(1)!=null?images.group(1):images.group(2));if(path!=null&&!files.containsKey(path))try{files.put(path,new JSONObject().put("path",path).put("name",new File(path).getName()));}catch(Exception ignored){}}
    return new JSONArray(files.values());
  }
  static View fileCard(ChatTurnView owner,JSONObject file,String path){
    MainActivity host=owner.host;
        LinearLayout panel=host.row();panel.setPadding(host.dp(12),host.dp(10),host.dp(4),host.dp(10));
        android.graphics.drawable.GradientDrawable background=host.ui.shape(UiStyle.LOW,20);background.setStroke(host.dp(1),UiStyle.color(0xFFE1DBE8));panel.setBackground(background);
        ImageView icon=new ImageView(host);icon.setImageDrawable(new FileTypeIcon(host,path));panel.addView(icon,new LinearLayout.LayoutParams(host.dp(34),host.dp(40)));
        LinearLayout words=new LinearLayout(host);words.setOrientation(LinearLayout.VERTICAL);words.setPadding(host.dp(12),0,host.dp(4),0);
        TextView name=host.label(file.optString("name",new File(path).getName()),15,host.ink);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);words.addView(name);
        TextView size=host.label("",12,host.muted);size.setPadding(0,host.dp(5),0,0);words.addView(size);panel.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        long bytes=file.optLong("bytes",-1);if(bytes>=0)size.setText(sizeLabel(bytes));
        else host.io.execute(()->{try{long length=new FileTools(host).resolve(path).length();host.runOnUiThread(()->{if(!host.isDestroyed())size.setText(sizeLabel(length));});}catch(Exception ignored){}});
        panel.addView(host.icon(R.drawable.ic_file_more,"文件操作",v->{if(host.messageActions.active())host.messageActions.toggle(owner.turnId+"/a");else WorkspaceFiles.actions(host,path,WorkspaceFiles.mime(path).startsWith("image/"));}),new LinearLayout.LayoutParams(host.dp(48),host.dp(48)));
        panel.setContentDescription("打开文件："+path);return panel;

  }
  static String sizeLabel(long bytes){return bytes>=1024*1024?String.format(Locale.ROOT,"%.1f MB",bytes/(1024.0*1024)):Math.max(1,(bytes+1023)/1024)+" KB";}
  static void fill(ChatTurnView owner,JSONArray files){
    LinearLayout row=owner.outputFiles;row.removeAllViews();owner.fileCards.removeAllViews();owner.hasPhoto=false;MainActivity host=owner.host;
    for(int n=0;n<files.length();n++){
      JSONObject file=files.optJSONObject(n);if(file==null)continue;String path=file.optString("path");if(WorkspaceFiles.linkPath(path)==null)continue;
      boolean svg=path.toLowerCase(Locale.ROOT).endsWith(".svg");
      boolean image=WorkspaceFiles.mime(path).startsWith("image/");
      View card;
      if(svg){
        InlineSvgView picture=new InlineSvgView(host);card=picture;
        int width=Math.min(host.dp(260),host.getResources().getDisplayMetrics().widthPixels-host.dp(64));card.setLayoutParams(new LinearLayout.LayoutParams(width,host.dp(180)));
        picture.setContentDescription("SVG 图片："+file.optString("name",path));
        String replySnapshot=owner.markdown.source==null?"":owner.markdown.source;
        host.io.execute(()->{try{File actual=new FileTools(host).resolve(path);if(actual.length()>512*1024)throw new IOException("SVG 过大");String source=new String(java.nio.file.Files.readAllBytes(actual.toPath()),java.nio.charset.StandardCharsets.UTF_8);boolean duplicate=SvgMessageContent.parts(replySnapshot).stream().anyMatch(part->part.svg && SvgPreview.sanitize(part.text).trim().equals(SvgPreview.sanitize(source).trim()));
          host.runOnUiThread(()->{if(host.isDestroyed()||picture.getParent()!=row)return;if(duplicate)picture.setVisibility(View.GONE);else picture.content(source);});}catch(Exception ignored){}});
      }else if(image){
        owner.hasPhoto=true;
        ImageView picture=new ImageView(host);picture.setAdjustViewBounds(true);picture.setScaleType(ImageView.ScaleType.FIT_CENTER);picture.setBackground(host.ui.surface(UiStyle.LOW,18));picture.setClipToOutline(true);picture.setImageResource(R.drawable.ic_image);picture.setContentDescription("图片："+file.optString("name",path));
        card=picture;card.setLayoutParams(new LinearLayout.LayoutParams(-1,-2));
        host.io.execute(()->{try{File actual=new FileTools(host).resolve(path);if(actual.length()>FileTools.MAX_BYTES)throw new IOException("图片过大");var drawable=WorkspaceFiles.decode(actual,1024);
          host.runOnUiThread(()->{if(host.isDestroyed()||picture.getParent()!=row)return;picture.setImageDrawable(drawable);if(drawable instanceof Animatable && picture.isAttachedToWindow())((Animatable)drawable).start();
            picture.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){if(drawable instanceof Animatable)((Animatable)drawable).start();}public void onViewDetachedFromWindow(View v){if(drawable instanceof Animatable)((Animatable)drawable).stop();}});
          });
        }catch(Exception e){host.runOnUiThread(()->picture.setContentDescription("图片不可用："+path));}});
      }else{
        card=fileCard(owner,file,path);
      }
      View target=card instanceof InlineSvgView?((InlineSvgView)card).touch:card;
      target.setOnClickListener(v->{if(host.messageActions.active())host.messageActions.toggle(owner.turnId+"/a");else if(image)WorkspaceFiles.preview(host,path);else WorkspaceFiles.open(host,path);});
      target.setOnLongClickListener(v->{WorkspaceFiles.actions(host,path,image&&!svg);return true;});
      LinearLayout.LayoutParams p=card.getLayoutParams() instanceof LinearLayout.LayoutParams?(LinearLayout.LayoutParams)card.getLayoutParams():new LinearLayout.LayoutParams(-2,-2);
      if(image){
        View attachment=fileCard(owner,file,path);
        attachment.setOnClickListener(v->{if(host.messageActions.active())host.messageActions.toggle(owner.turnId+"/a");else WorkspaceFiles.preview(host,path);});
        attachment.setOnLongClickListener(v->{WorkspaceFiles.actions(host,path,!svg);return true;});
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,-2);ap.bottomMargin=host.dp(12);owner.fileCards.addView(attachment,ap);
      }
      p.bottomMargin=host.dp(image?8:12);if(!image){p.width=LinearLayout.LayoutParams.MATCH_PARENT;owner.fileCards.addView(card,p);}else row.addView(card,p);
    }
    row.getLayoutParams().width=owner.hasPhoto?LinearLayout.LayoutParams.MATCH_PARENT:LinearLayout.LayoutParams.WRAP_CONTENT;
    row.setVisibility(row.getChildCount()==0?View.GONE:View.VISIBLE);
    owner.fileCards.setVisibility(owner.fileCards.getChildCount()==0?View.GONE:View.VISIBLE);
    owner.attachmentSelection();
  }
}
