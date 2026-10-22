package dev.guga.dsh;
import static org.junit.Assert.*;
import android.text.Spanned;
import android.text.style.URLSpan;
import android.widget.TextView;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;

public class MarkdownTests {
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
