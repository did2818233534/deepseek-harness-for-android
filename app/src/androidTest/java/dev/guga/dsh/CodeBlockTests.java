package dev.guga.dsh;
import static org.junit.Assert.*;
import org.junit.Test;
public class CodeBlockTests {
 @Test public void wrapsVisuallyAndCopiesExactOriginal()throws Exception{
  var i=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation();var c=i.getTargetContext();
  dev.guga.updates.UpdateTestDriver.prefs(c).edit().putString("checkedDay",dev.guga.updates.UpdateTestDriver.today()).commit();
  MainActivity a=(MainActivity)i.startActivitySync(new android.content.Intent(c,MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
  String raw="    const value = '"+"abcdef0123456789".repeat(25)+"';  \n\treturn value;\n";
  SvgMessageContent.CodeCard[] card=new SvgMessageContent.CodeCard[1];
  try{
   i.runOnMainSync(()->{try{a.visible=false;a.render(new org.json.JSONObject().put("id","code-wrap").put("revision",11234).put("turns",new org.json.JSONArray().put(new org.json.JSONObject().put("id","1").put("text","```js\n"+raw+"```"))));card[0]=(SvgMessageContent.CodeCard)a.turnViews.get("1").svgContent.getChildAt(0);}catch(Exception e){throw new RuntimeException(e);}});
   i.waitForIdleSync();Thread.sleep(300);
   i.runOnMainSync(()->{assertEquals(raw,card[0].text.getText().toString());assertTrue(card[0].text.getLineCount()>3);assertTrue(card[0].getWidth()<=a.getResources().getDisplayMetrics().widthPixels);});
   InlineSvgTests.tap(i,((android.widget.LinearLayout)card[0].getChildAt(0)).getChildAt(0));
   i.runOnMainSync(()->assertEquals(raw,((android.content.ClipboardManager)c.getSystemService(android.content.Context.CLIPBOARD_SERVICE)).getPrimaryClip().getItemAt(0).getText().toString()));
   i.runOnMainSync(()->assertEquals("已复制代码",card[0].copy.getContentDescription()));
   Thread.sleep(1000);InlineSvgTests.tap(i,card[0].copy);Thread.sleep(800);
   i.runOnMainSync(()->assertEquals("已复制代码",card[0].copy.getContentDescription()));Thread.sleep(1000);
   i.runOnMainSync(()->assertEquals("复制代码",card[0].copy.getContentDescription()));
   String html=MessagePreview.html("```js\n"+raw+"```");assertTrue(html.contains("white-space:pre-wrap"));var doc=org.jsoup.Jsoup.parse(html);assertEquals(raw,doc.selectFirst("pre code").wholeText());assertEquals("https://preview.invalid/code-copy/0",doc.selectFirst("pre a").attr("href"));
  }finally{i.runOnMainSync(a::finish);}
 }
}
