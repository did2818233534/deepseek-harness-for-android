package dev.guga.dsh;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.Spanned;
import android.text.style.URLSpan;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.TextView;
import io.noties.markwon.Markwon;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.ext.tasklist.TaskListPlugin;

/** Native spans only: model output is never executed as HTML or JavaScript. */
final class ChatMarkdown {
  final Markwon renderer;
  String source;
  ChatMarkdown(Context context) {
    renderer=Markwon.builder(context)
      .usePlugin(StrikethroughPlugin.create())
      .usePlugin(TablePlugin.create(context))
      .usePlugin(TaskListPlugin.create(context)).build();
  }
  void update(TextView view,String markdown) {
    if(!markdown.equals(source)) {
      source=markdown;
      // Table rows discover wrapped cell heights during drawing. Markwon's
      // afterSetText hook attaches the invalidator that remeasures those rows.
      renderer.setParsedMarkdown(view,renderer.toMarkdown(markdown));
      // Keep our short-tap links and long-press message selection behavior.
      view.setMovementMethod(null);
    }
    view.setVisibility(markdown.isEmpty()?android.view.View.GONE:android.view.View.VISIBLE);
  }
  // Keep normal long-press selection and message multi-select; only short link taps open URLs.
  static void links(TextView view, MainActivity host) {
    view.setLinkTextColor(UiStyle.ACCENT);
    view.setOnTouchListener(new android.view.View.OnTouchListener() {
      float x,y; long down; boolean moved;
      public boolean onTouch(android.view.View v,MotionEvent e) {
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN) {
          x=e.getX();y=e.getY();down=e.getEventTime();moved=false;
        } else if(e.getActionMasked()==MotionEvent.ACTION_MOVE) {
          int slop=ViewConfiguration.get(host).getScaledTouchSlop();
          moved|=Math.abs(e.getX()-x)>slop||Math.abs(e.getY()-y)>slop;
        } else if(e.getActionMasked()==MotionEvent.ACTION_UP && !moved
            && e.getEventTime()-down<ViewConfiguration.getLongPressTimeout()
            && !host.messageActions.active() && view.getLayout()!=null && view.getText() instanceof Spanned) {
          float px=e.getX()-view.getTotalPaddingLeft()+view.getScrollX();
          float py=e.getY()-view.getTotalPaddingTop()+view.getScrollY();
          android.text.Layout layout=view.getLayout();
          if(py<0||py>=layout.getHeight())return false;
          int line=layout.getLineForVertical((int)py);
          if(px<layout.getLineLeft(line)||px>layout.getLineRight(line))return false;
          int offset=layout.getOffsetForHorizontal(line,px);
          URLSpan[] spans=((Spanned)view.getText()).getSpans(offset,offset,URLSpan.class);
          if(spans.length>0) {
            Uri uri=Uri.parse(spans[0].getURL());String scheme=uri.getScheme();
            if("https".equalsIgnoreCase(scheme)||"http".equalsIgnoreCase(scheme)) {
              try {host.startActivity(new Intent(Intent.ACTION_VIEW,uri));}
              catch(android.content.ActivityNotFoundException ignored) {
                android.widget.Toast.makeText(host,"没有可打开链接的应用",android.widget.Toast.LENGTH_SHORT).show();
              }
            }
            return true;
          }
        }
        return false;
      }
    });
  }
}
