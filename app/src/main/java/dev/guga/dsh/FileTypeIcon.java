package dev.guga.dsh;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import java.util.Locale;

/** Resolution-independent attachment artwork; thumbnails remain unchanged. */
final class FileTypeIcon extends Drawable {
  final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
  final String mark;
  final int color;
  int opacity=255;
  FileTypeIcon(Context context,String name) {
    mark=type(name);
    boolean dark=android.graphics.Color.luminance(UiStyle.BACKGROUND)<0.5f;
    switch(mark){
      case "W":color=dark?0xff8ab4f8:0xff376fc1;break;
      case "X":color=dark?0xff81c995:0xff348057;break;
      case "P":color=dark?0xffffb477:0xffbd662d;break;
      case "PDF":color=dark?0xffff9a98:0xffc44e50;break;
      case "ZIP":color=dark?0xffe3cb8b:0xffa88a43;break;
      case "audio":color=dark?0xffc7a4ed:0xff8c63b3;break;
      default:color=dark?0xffaebdcc:0xff697e91;
    }
  }
  static String type(String name){
    if(name==null)return "";
    String base=name.substring(Math.max(name.lastIndexOf('/'),name.lastIndexOf('\\'))+1);
    int dot=base.lastIndexOf('.');if(dot<=0||dot==base.length()-1)return "";
    switch(base.substring(dot+1).toLowerCase(Locale.ROOT)){
      case "doc":case "docx":case "odt":case "rtf":return "W";
      case "xls":case "xlsx":case "csv":case "ods":return "X";
      case "ppt":case "pptx":case "odp":return "P";
      case "pdf":return "PDF";
      case "txt":case "md":case "log":return "TXT";
      case "zip":case "rar":case "7z":case "tar":case "gz":case "bz2":case "xz":return "ZIP";
      case "mp3":case "wav":case "flac":case "aac":case "ogg":case "m4a":case "opus":case "wma":case "aiff":case "amr":case "mid":case "midi":return "audio";
      case "mp4":case "mkv":case "mov":case "avi":case "webm":case "m4v":case "3gp":case "mpeg":case "mpg":case "wmv":case "flv":case "ts":return "video";
      case "png":case "jpg":case "jpeg":case "gif":case "webp":case "svg":case "bmp":case "heic":case "heif":case "avif":case "tif":case "tiff":case "ico":return "image";
      default:return "";
    }
  }
  @Override public void draw(Canvas canvas){
    Rect b=getBounds();float scale=Math.min(b.width()/32f,b.height()/38f);
    int save=canvas.save();canvas.translate(b.centerX()-16*scale,b.centerY()-19*scale);canvas.scale(scale,scale);
    paint.setColor(color);paint.setAlpha(opacity);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.6f);paint.setStrokeJoin(Paint.Join.ROUND);paint.setStrokeCap(Paint.Cap.ROUND);
    Path p=new Path();p.moveTo(18,3);p.lineTo(8,3);p.quadTo(4,3,4,7);p.lineTo(4,31);p.quadTo(4,35,8,35);p.lineTo(24,35);p.quadTo(28,35,28,31);p.lineTo(28,13);p.close();canvas.drawPath(p,paint);
    p.reset();p.moveTo(18,3);p.lineTo(18,10);p.quadTo(18,13,21,13);p.lineTo(28,13);canvas.drawPath(p,paint);
    canvas.save();canvas.translate(0,4);
    if(mark.equals("audio")){
      p.reset();p.moveTo(17,22);p.lineTo(17,14);p.lineTo(21,15.5f);canvas.drawPath(p,paint);paint.setStyle(Paint.Style.FILL);canvas.drawOval(11,20,17,24,paint);
    }else if(mark.equals("video")){
      p.reset();p.moveTo(13,14);p.lineTo(21,19);p.lineTo(13,24);p.close();canvas.drawPath(p,paint);
    }else if(mark.equals("image")){
      canvas.drawRoundRect(10,14,22,24,1,1,paint);p.reset();p.moveTo(10,22);p.lineTo(14,18);p.lineTo(18,22);p.lineTo(20,20);p.lineTo(22,22);canvas.drawPath(p,paint);canvas.drawCircle(19,17,1,paint);
    }else if(!mark.isEmpty()){
      paint.setStyle(Paint.Style.FILL);paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(mark.length()==1?12:7.3f);Paint.FontMetrics fm=paint.getFontMetrics();canvas.drawText(mark,16,20-(fm.ascent+fm.descent)/2,paint);
    }
    canvas.restore();canvas.restoreToCount(save);
  }
  @Override public void setAlpha(int alpha){opacity=alpha;invalidateSelf();}
  @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
  @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
