package dev.guga.dsh;
import static org.junit.Assert.*;
import android.text.Spanned;
import android.text.style.URLSpan;
import android.widget.TextView;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;

public class MarkdownTests {
  @Test public void wrappedTableRowsRemeasureAtDifferentWidthsAndFontSizes() throws Exception {
    var i=InstrumentationRegistry.getInstrumentation();var c=i.getTargetContext();
    dev.guga.updates.UpdateTestDriver.prefs(c).edit().putString("checkedDay",dev.guga.updates.UpdateTestDriver.today()).commit();
    MainActivity a=(MainActivity)i.startActivitySync(new android.content.Intent(c,MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
    TextView[] target=new TextView[1];ChatMarkdown[] md=new ChatMarkdown[1];
    String raw="### 给个人开发者的实际建议\n\n|需求|推荐渠道|\n|---|---|\n|少量样片、研发打样|iCEasy（艾矽易）、立创商城、得捷电子|\n|玩开发板/开源硬件|中电港萤火工场、开源硬件渠道|\n|量产大货|原厂授权分销商|\n\n> 表格之后的正文，不应与表格重叠。";
    try {
      i.runOnMainSync(()->{a.visible=false;android.widget.LinearLayout root=new android.widget.LinearLayout(a);root.setPadding(16,40,16,16);root.setBackgroundColor(UiStyle.BACKGROUND);target[0]=new TextView(a);target[0].setTextColor(UiStyle.INK);root.addView(target[0]);a.setContentView(root);md[0]=new ChatMarkdown(a);});
      for(int width:new int[]{300,220})for(int font:new int[]{16,24}) {
        i.runOnMainSync(()->{target[0].setTextSize(font);target[0].setLayoutParams(new android.widget.LinearLayout.LayoutParams(a.dp(width),-2));md[0].source=null;md[0].update(target[0],raw);});
        // Allow draw -> table invalidation -> remeasure, then assert real line bounds.
        Thread.sleep(350);i.waitForIdleSync();
        i.runOnMainSync(()->{
          TextView v=target[0];Spanned s=(Spanned)v.getText();android.text.Layout layout=v.getLayout();
          var rows=s.getSpans(0,s.length(),io.noties.markwon.ext.tables.TableRowSpan.class);assertEquals(4,rows.length);
          for(var row:rows){assertTrue(row.cellWidth()>0);int start=s.getSpanStart(row);int line=layout.getLineForOffset(start);android.graphics.Paint.FontMetricsInt fm=new android.graphics.Paint.FontMetricsInt();row.getSize(v.getPaint(),s,start,s.getSpanEnd(row),fm);assertTrue("Wrapped row overlaps at width="+width+", font="+font,layout.getLineBottom(line)-layout.getLineTop(line)>=fm.descent-fm.ascent);}
        });
      }
      StreamingTests.screenshot(c,"markdown-table-layout.png");
    } finally {i.runOnMainSync(a::finish);}
  }
  @Test public void formattedMarkdownSurvivesEveryStreamingPrefix() {
    var i=InstrumentationRegistry.getInstrumentation();
    i.runOnMainSync(()->{
      var c=i.getTargetContext();ChatMarkdown md=new ChatMarkdown(c);TextView view=new TextView(c);
      String raw="# 标题\n\n**中文粗体**与 *斜体*\n\n- 列表\n\n> 引用\n\n```java\nString s = \"你好👋\";\n```\n\n[链接](https://example.com)\n\n|项目|数量|\n|---|---|\n|苹果|2|\n\n- [x] 完成\n\n~~删除~~";
      for(int n=0;n<=raw.length();n++)md.update(view,raw.substring(0,n));
      assertEquals(raw,md.source);assertTrue(view.getText() instanceof Spanned);
      assertFalse(view.getText().toString().contains("**中文粗体**"));
      assertTrue(view.getText().toString().contains("String s = \"你好👋\";"));
      Spanned text=(Spanned)view.getText();assertEquals(1,text.getSpans(0,text.length(),URLSpan.class).length);
      CharSequence before=view.getText();md.update(view,raw);assertSame(before,view.getText());
    });
  }
}
