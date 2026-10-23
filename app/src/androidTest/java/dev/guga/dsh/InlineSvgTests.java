package dev.guga.dsh;
import static org.junit.Assert.*;
import org.junit.Test;
import android.view.*;
public class InlineSvgTests {
  final String svg="<svg viewBox='0 0 100 100'><circle cx='50' cy='50' r='30' fill='purple'/></svg>";
  @Test public void onlyClosedFencesBecomePreviews(){
    assertFalse(SvgMessageContent.parts("```svg\n"+svg).get(0).svg);
    var parts=SvgMessageContent.parts("说明\n```svg\n"+svg+"\n```\n后文\n~~~xml\n"+svg+"\n~~~");
    assertEquals(4,parts.size());assertTrue(parts.get(1).svg);assertTrue(parts.get(3).svg);
    String python="```python\nprint('hello')\n```\n";
    var mixed=SvgMessageContent.parts(python+"```svg\n"+svg+"\n```\n"+python);
    assertTrue(mixed.get(0).code);assertEquals("print('hello')\n",mixed.get(0).raw);assertTrue(mixed.get(2).svg);assertTrue(mixed.get(4).code);
    assertFalse(SvgMessageContent.parts("```text\n"+svg+"\n```").get(0).svg);
    assertFalse(SvgMessageContent.parts("```svg\n<svg><circle\n```").get(0).svg);
  }
  @Test public void inlineToggleExpandLongPressAndSelection()throws Exception{
    var i=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation();var c=i.getTargetContext();
    dev.guga.updates.UpdateTestDriver.prefs(c).edit().putString("checkedDay",dev.guga.updates.UpdateTestDriver.today()).commit();
    MainActivity a=(MainActivity)i.startActivitySync(new android.content.Intent(c,MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
    ChatTurnView[] turn=new ChatTurnView[1];SvgMessageContent.Card[] card=new SvgMessageContent.Card[1];
    String raw="说明\n```svg\n"+svg+"\n```\n后文";
    try{
      i.runOnMainSync(()->{try{
        a.visible=false;var t=new org.json.JSONObject().put("id","svg-inline").put("userText","图片").put("text","说明\n```svg\n"+svg).put("busy",true);
        a.render(new org.json.JSONObject().put("id","inline-test").put("revision",9991).put("turns",new org.json.JSONArray().put(t)));
        turn[0]=a.turnViews.get("svg-inline");assertEquals(View.VISIBLE,turn[0].svgContent.getVisibility());assertTrue(turn[0].svgContent.getChildAt(1) instanceof SvgMessageContent.CodeCard);
        t.put("text",raw);turn[0].update(t);assertEquals(View.GONE,turn[0].answer.getVisibility());assertEquals(3,turn[0].svgContent.getChildCount());
        card[0]=(SvgMessageContent.Card)turn[0].svgContent.getChildAt(1);t.put("text",raw+"继续输出");turn[0].update(t);assertSame(card[0],turn[0].svgContent.getChildAt(1));assertEquals(raw+"继续输出",turn[0].markdown.source);
      }catch(Exception e){throw new RuntimeException(e);}});
      i.waitForIdleSync();Thread.sleep(800);
      StreamingTests.screenshot(c,"inline-svg-natural.png");
      i.runOnMainSync(()->{assertEquals(1,card[0].getChildCount());assertNotNull(turn[0].svgContent.getBackground());});
      for(int n=0;n<2;n++){
        tap(i,card[0].preview.touch);Thread.sleep(600);i.waitForIdleSync();
        i.runOnMainSync(()->{assertNotSame(a.chatRoot,a.currentPage);a.onBackPressed();assertSame(a.chatRoot,a.currentPage);});
      }
      hold(i,card[0].preview.touch);Thread.sleep(500);i.waitForIdleSync();
      i.runOnMainSync(()->assertFalse("SVG long press opens actions",a.dialogs.isEmpty()));
      java.io.File directory=new FileTools(c).resolve("svg-previews");boolean found=false;
      for(java.io.File file:directory.listFiles())if(new String(java.nio.file.Files.readAllBytes(file.toPath()),java.nio.charset.StandardCharsets.UTF_8).equals(svg)){
        found=true;var intent=WorkspaceFiles.intent(c,"svg-previews/"+file.getName(),true);assertEquals("image/svg+xml",intent.getType());
        android.net.Uri uri=intent.getParcelableExtra(android.content.Intent.EXTRA_STREAM);
        try(var input=c.getContentResolver().openInputStream(uri)){assertEquals(svg,new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}
      }assertTrue("Original SVG is shareable without conversion",found);
      // Close actions using Android Back, then ordinary text must still enter multi-selection.
      i.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);i.waitForIdleSync();
      hold(i,turn[0].svgContent.getChildAt(0));i.runOnMainSync(()->{assertTrue(a.messageActions.active());assertTrue(a.messageActions.selected.contains("svg-inline/a"));});
      StreamingTests.screenshot(c,"inline-svg.png");
    }finally{i.runOnMainSync(a::finish);}
  }
  static void tap(android.app.Instrumentation i,View v)throws Exception{long t=android.os.SystemClock.uptimeMillis();i.runOnMainSync(()->ArtifactTests.touch(v,0,t,t,20,20));i.runOnMainSync(()->ArtifactTests.touch(v,1,t,t+30,20,20));i.waitForIdleSync();}
  static void hold(android.app.Instrumentation i,View v)throws Exception{long t=android.os.SystemClock.uptimeMillis();i.runOnMainSync(()->ArtifactTests.touch(v,0,t,t,20,20));Thread.sleep(ViewConfiguration.getLongPressTimeout()+150);i.runOnMainSync(()->ArtifactTests.touch(v,1,t,android.os.SystemClock.uptimeMillis(),20,20));i.waitForIdleSync();}
}
