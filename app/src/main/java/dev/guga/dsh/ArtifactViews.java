package dev.guga.dsh;

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
  static void fill(ChatTurnView owner,JSONArray files){
    LinearLayout row=owner.outputFiles;row.removeAllViews();MainActivity host=owner.host;
    for(int n=0;n<files.length();n++){
      JSONObject file=files.optJSONObject(n);if(file==null)continue;String path=file.optString("path");if(WorkspaceFiles.linkPath(path)==null)continue;
      boolean svg=path.toLowerCase(Locale.ROOT).endsWith(".svg");
      boolean image=WorkspaceFiles.mime(path).startsWith("image/");
      View card;
      if(svg){
        InlineSvgView picture=new InlineSvgView(host);card=picture;
        int width=Math.min(host.dp(260),host.getResources().getDisplayMetrics().widthPixels-host.dp(64));card.setLayoutParams(new LinearLayout.LayoutParams(width,host.dp(180)));
        picture.setContentDescription("SVG 图片："+file.optString("name",path));
        host.io.execute(()->{try{File actual=new FileTools(host).resolve(path);if(actual.length()>512*1024)throw new IOException("SVG 过大");String source=new String(java.nio.file.Files.readAllBytes(actual.toPath()),java.nio.charset.StandardCharsets.UTF_8);host.runOnUiThread(()->picture.content(source));}catch(Exception ignored){}});
      }else if(image){
        ImageView picture=new ImageView(host);picture.setScaleType(ImageView.ScaleType.FIT_CENTER);picture.setBackground(host.ui.surface(UiStyle.LOW,18));picture.setClipToOutline(true);picture.setImageResource(R.drawable.ic_image);picture.setContentDescription("图片："+file.optString("name",path));
        int width=Math.min(host.dp(260),host.getResources().getDisplayMetrics().widthPixels-host.dp(64));
        card=picture;card.setLayoutParams(new LinearLayout.LayoutParams(width,host.dp(180)));
        host.io.execute(()->{try{File actual=new FileTools(host).resolve(path);if(actual.length()>FileTools.MAX_BYTES)throw new IOException("图片过大");var drawable=WorkspaceFiles.decode(actual,1024);
          host.runOnUiThread(()->{if(host.isDestroyed())return;picture.setImageDrawable(drawable);if(drawable instanceof Animatable)((Animatable)drawable).start();
            picture.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){if(drawable instanceof Animatable)((Animatable)drawable).start();}public void onViewDetachedFromWindow(View v){if(drawable instanceof Animatable)((Animatable)drawable).stop();}});
          });
        }catch(Exception e){host.runOnUiThread(()->picture.setContentDescription("图片不可用："+path));}});
      }else{
        TextView link=host.label(file.optString("name",new File(path).getName()),15,UiStyle.ACCENT);link.setMinHeight(host.dp(48));link.setGravity(Gravity.CENTER_VERTICAL);link.setMaxLines(2);link.setEllipsize(android.text.TextUtils.TruncateAt.END);link.setPadding(0,host.dp(4),host.dp(4),host.dp(4));
        var icon=host.getDrawable(R.drawable.ic_file).mutate();icon.setTint(UiStyle.ACCENT);icon.setBounds(0,0,host.dp(18),host.dp(18));link.setCompoundDrawablesRelative(icon,null,null,null);link.setCompoundDrawablePadding(host.dp(8));link.setBackground(host.ui.surface(android.graphics.Color.TRANSPARENT,12));link.setContentDescription("打开文件："+path);card=link;
      }
      View target=card instanceof InlineSvgView?((InlineSvgView)card).touch:card;
      target.setOnClickListener(v->{if(host.messageActions.active())host.messageActions.toggle(owner.turnId+"/a");else if(image)WorkspaceFiles.preview(host,path);else WorkspaceFiles.open(host,path);});
      target.setOnLongClickListener(v->{WorkspaceFiles.actions(host,path,image&&!svg);return true;});
      LinearLayout.LayoutParams p=card.getLayoutParams() instanceof LinearLayout.LayoutParams?(LinearLayout.LayoutParams)card.getLayoutParams():new LinearLayout.LayoutParams(-2,-2);p.bottomMargin=host.dp(image?8:2);row.addView(card,p);
    }
    row.setVisibility(row.getChildCount()==0?View.GONE:View.VISIBLE);
  }
}
