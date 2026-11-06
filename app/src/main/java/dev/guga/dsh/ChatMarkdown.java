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
  boolean plain;
  ChatMarkdown(Context context) {
    MainActivity host=context instanceof MainActivity?(MainActivity)context:null;
    if(host!=null&&host.markdownRenderer!=null){renderer=host.markdownRenderer;return;}
    renderer=Markwon.builder(context)
      .usePlugin(new io.noties.markwon.AbstractMarkwonPlugin(){
        @Override public void configureTheme(io.noties.markwon.core.MarkwonTheme.Builder b){
          float d=context.getResources().getDisplayMetrics().density;
          b.headingTextSizeMultipliers(new float[]{1.35f,1.22f,1.12f,1.06f,1f,1f})
            .headingBreakHeight(0).thematicBreakHeight(Math.max(1,(int)d)).thematicBreakColor(UiStyle.color(0xFFDCD5E5))
            .blockMargin((int)(16*d)).bulletWidth((int)(4*d)).blockQuoteWidth((int)(3*d)).blockQuoteColor(0xFFB6A4D4)
            .codeBackgroundColor(UiStyle.color(0xFFE8E1F0)).codeBlockBackgroundColor(UiStyle.color(0xFFE8E1F0)).codeTextColor(UiStyle.color(0xFF51416D))
            .linkColor(UiStyle.ACCENT).isLinkUnderlined(false);
        }
      })
      .usePlugin(StrikethroughPlugin.create())
      .usePlugin(TablePlugin.create(b->b.tableBorderColor(UiStyle.color(0xFFDCD5E5)).tableBorderWidth(1)
        .tableCellPadding((int)(8*context.getResources().getDisplayMetrics().density))
        .tableHeaderRowBackgroundColor(UiStyle.color(0xFFE8E1F0)).tableOddRowBackgroundColor(0x0C6750A4)))
      .usePlugin(TaskListPlugin.create(context)).build();
    if(host!=null)host.markdownRenderer=renderer;
  }
  // A plain paragraph after a formatted block can grow without resetting the
  // spans and measured layout of the blocks above it.
  boolean appendParagraph(TextView view, String next) {
    if (source == null || source.isEmpty() || !next.startsWith(source)) return false;
    int start = source.lastIndexOf("\n\n");
    if (start < 0 && !plain) return false;
    int paragraphStart = start < 0 ? 0 : start + 2;
    if (paragraphStart >= source.length()) return false;
    String tail = next.substring(paragraphStart);
    if (!tail.codePoints().allMatch(c -> Character.isLetterOrDigit(c) || c == ' ' ||
        "，。！？、；：".indexOf(c) >= 0) || !Character.isLetterOrDigit(tail.codePointAt(0))) return false;
    CharSequence displayed = view.getText();
    int oldTail = source.length() - paragraphStart;
    if (!(displayed instanceof Spanned) || displayed.length() < oldTail ||
        !android.text.TextUtils.regionMatches(displayed, displayed.length()-oldTail, source, paragraphStart, oldTail)) return false;
    Spanned spans = (Spanned) displayed;
    if (spans.getSpans(displayed.length()-oldTail, displayed.length(), android.text.style.CharacterStyle.class).length != 0 ||
        spans.getSpans(displayed.length()-oldTail, displayed.length(), android.text.style.ParagraphStyle.class).length != 0) return false;
    view.append(next, source.length(), next.length());
    return true;
  }
  void update(TextView view,String markdown) {
    if (!markdown.equals(source) && appendParagraph(view, markdown)) {
      source = markdown;
      return;
    }
    if(!markdown.equals(source)) {
      source=markdown;
      // Table rows discover wrapped cell heights during drawing. Markwon's
      // afterSetText hook attaches the invalidator that remeasures those rows.
      android.text.Spanned parsed=renderer.toMarkdown(MarkdownFiles.normalize(markdown));
      boolean nextPlain=parsed.getSpans(0,parsed.length(),Object.class).length==0;
      if(plain && nextPlain && view.getText() instanceof android.text.Editable
          && parsed.toString().startsWith(view.getText().toString())) {
        ((android.text.Editable)view.getText()).append(parsed.subSequence(view.length(),parsed.length()));
      } else if(nextPlain) {
        view.setText(parsed,TextView.BufferType.EDITABLE);
      } else {
        prepareTables(view,parsed);
        renderer.setParsedMarkdown(view,parsed);
      }
      plain=nextPlain;
      // Keep our short-tap links and long-press message selection behavior.
      view.setMovementMethod(null);
      view.setClickable(true);
      view.setLongClickable(true);
    }
    view.setVisibility(markdown.isEmpty()?android.view.View.GONE:android.view.View.VISIBLE);
  }
  // TableRowSpan normally learns its height in the first visible draw, causing
  // every streaming update to briefly collapse then expand long wrapped rows.
  // Prime the new rows offscreen before TextView performs its first measurement.
  static void prepareTables(TextView view,android.text.Spanned parsed) {
    io.noties.markwon.ext.tables.TableRowSpan[] rows=parsed.getSpans(0,parsed.length(),io.noties.markwon.ext.tables.TableRowSpan.class);
    if(rows.length==0)return;
    int width=view.getLayoutParams()!=null&&view.getLayoutParams().width>0?view.getLayoutParams().width:view.getMaxWidth();
    if(width<=0||width==Integer.MAX_VALUE)width=view.getWidth()>0?view.getWidth():view.getResources().getDisplayMetrics().widthPixels;
    width=Math.max(1,width-view.getPaddingLeft()-view.getPaddingRight());
    android.graphics.Bitmap scratch=android.graphics.Bitmap.createBitmap(width,1,android.graphics.Bitmap.Config.ARGB_8888);
    try {
      android.graphics.Canvas canvas=new android.graphics.Canvas(scratch);
      for(io.noties.markwon.ext.tables.TableRowSpan row:rows)
        row.draw(canvas,parsed,parsed.getSpanStart(row),parsed.getSpanEnd(row),0,0,0,1,view.getPaint());
    } finally {scratch.recycle();}
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
            if(view instanceof MessageTextView){
              ((MessageTextView)view).linkClick=()->{
            if(WorkspaceFiles.openLink(host,spans[0].getURL()))return;
            Uri uri=Uri.parse(spans[0].getURL());String scheme=uri.getScheme();
            if("https".equalsIgnoreCase(scheme)||"http".equalsIgnoreCase(scheme)) {
              try {host.startActivity(new Intent(Intent.ACTION_VIEW,uri));}
              catch(android.content.ActivityNotFoundException ignored) {
                android.widget.Toast.makeText(host,"没有可打开链接的应用",android.widget.Toast.LENGTH_SHORT).show();
              }
            }
              };
              view.performClick();
            }
            return true;
          }
        }
        return false;
      }
    });
  }
}
