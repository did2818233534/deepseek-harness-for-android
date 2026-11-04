package dev.guga.dsh;

import android.content.Context;
import android.graphics.Matrix;
import android.view.*;
import android.widget.ImageView;

/** Bounded pan and pinch zoom; double tap toggles fit and 2.5x. */
final class ZoomImageView extends ImageView {
  float zoom=1f,base=1f,tx,ty,lastX,lastY;
  final Matrix matrix=new Matrix();final ScaleGestureDetector scale;final GestureDetector gestures;
  ZoomImageView(Context c){
    super(c);setScaleType(ScaleType.MATRIX);setBackgroundColor(0xFF101015);setClickable(true);setLongClickable(true);
    scale=new ScaleGestureDetector(c,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
      @Override public boolean onScale(ScaleGestureDetector d){zoomTo(Math.max(1f,Math.min(6f,zoom*d.getScaleFactor())),d.getFocusX(),d.getFocusY());return true;}
    });
    gestures=new GestureDetector(c,new GestureDetector.SimpleOnGestureListener(){
      @Override public boolean onDown(MotionEvent e){return true;}
      @Override public boolean onDoubleTap(MotionEvent e){if(zoom>1.05f)fit();else zoomTo(2.5f,e.getX(),e.getY());return true;}
      @Override public void onLongPress(MotionEvent e){if(!scale.isInProgress())performLongClick();}
      @Override public boolean onSingleTapConfirmed(MotionEvent e){performClick();return true;}
    });
  }
  @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);fit();}
  void fit(){if(getDrawable()==null||getWidth()==0||getHeight()==0)return;base=Math.min(getWidth()/(float)getDrawable().getIntrinsicWidth(),getHeight()/(float)getDrawable().getIntrinsicHeight());zoom=1;tx=(getWidth()-getDrawable().getIntrinsicWidth()*base)/2;ty=(getHeight()-getDrawable().getIntrinsicHeight()*base)/2;apply();}
  void zoomTo(float next,float x,float y){float ratio=next/zoom;tx=x-(x-tx)*ratio;ty=y-(y-ty)*ratio;zoom=next;apply();}
  void apply(){if(getDrawable()==null)return;float w=getDrawable().getIntrinsicWidth()*base*zoom,h=getDrawable().getIntrinsicHeight()*base*zoom;tx=w<=getWidth()?(getWidth()-w)/2:Math.min(0,Math.max(getWidth()-w,tx));ty=h<=getHeight()?(getHeight()-h)/2:Math.min(0,Math.max(getHeight()-h,ty));matrix.setScale(base*zoom,base*zoom);matrix.postTranslate(tx,ty);setImageMatrix(matrix);}
  @Override public boolean onTouchEvent(MotionEvent event){
    scale.onTouchEvent(event);gestures.onTouchEvent(event);
    if(event.getActionMasked()==MotionEvent.ACTION_MOVE&&!scale.isInProgress()&&event.getPointerCount()==1){tx+=event.getX()-lastX;ty+=event.getY()-lastY;apply();}
    lastX=event.getX();lastY=event.getY();return true;
  }
  @Override public boolean performClick(){return super.performClick();}
}
