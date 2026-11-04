package dev.guga.dsh;
import static org.junit.Assert.*;
import org.junit.Test;
public class SvgTests {
  @Test public void svgGeometryAndDeclarativeAnimationSurvive() {
    String source="```svg\n<svg viewBox='0 0 100 100'><defs><linearGradient id='g'><stop offset='0' stop-color='red'/></linearGradient></defs><circle cx='50' cy='50' r='20' fill='url(#g)'><animate attributeName='r' values='10;20;10' dur='2s' repeatCount='indefinite'/></circle></svg>\n```";
    assertTrue(SvgPreview.standalone(source));String safe=SvgPreview.sanitize(source);
    assertTrue(safe.contains("viewBox"));assertTrue(safe.contains("linearGradient"));assertTrue(safe.contains("<animate"));assertTrue(safe.contains("url(#g)"));
    assertFalse(SvgPreview.standalone("说明\n"+source));assertEquals("",SvgPreview.extract("<svg><circle"));
  }
  @Test public void activeContentAndExternalResourcesCannotSurvive() {
    String source="<svg onload='alert(1)'><script>alert(1)</script><foreignObject><iframe src='file:///secret'/></foreignObject><image href='https://bad/x'/><use href='data:image/svg+xml,bad'/><path style='fill:url(https://bad/x)' fill='url(https://bad/x)'/><animate attributeName='href' values='javascript:alert(1)'/><rect><animate attributeName='fill' values='url(https://bad/x)'/></rect><use href='#safe'/></svg>";
    String safe=SvgPreview.sanitize(source);assertFalse(safe.contains("script"));assertFalse(safe.contains("foreignObject"));assertFalse(safe.contains("onload"));assertFalse(safe.contains("https://bad"));assertFalse(safe.contains("file:"));assertFalse(safe.contains("data:"));assertFalse(safe.contains("attributeName=\"href\""));assertTrue(safe.contains("href=\"#safe\""));
    assertTrue(SvgPreview.html(source).contains("script-src 'none'"));
  }
  @Test public void svgPreviewCopiesSourceReturnsAndLongPressSelects() throws Exception {
    var i=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation();var c=i.getTargetContext();
    dev.guga.updates.UpdateTestDriver.prefs(c).edit().putString("checkedDay",dev.guga.updates.UpdateTestDriver.today()).commit();
    MainActivity a=(MainActivity)i.startActivitySync(new android.content.Intent(c,MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
    String raw="<svg viewBox='0 0 100 100'><circle cx='50' cy='50' r='20' fill='#6750a4'><animate attributeName='r' values='10;20;10' dur='2s' repeatCount='indefinite'/></circle></svg>";
    var state=new org.json.JSONObject().put("id","svg-test").put("revision",992).put("turns",new org.json.JSONArray().put(new org.json.JSONObject().put("id","1").put("userText","动画").put("text",raw)));
    android.webkit.WebView[] preview=new android.webkit.WebView[1];
    try {
      i.runOnMainSync(()->{a.visible=false;a.render(state);a.turnViews.get("1").answer.performClick();assertNotSame(a.chatRoot,a.currentPage);
        var page=(android.widget.LinearLayout)a.currentPage;var web=(android.webkit.WebView)page.getChildAt(1);
        preview[0]=web;assertFalse(web.getSettings().getJavaScriptEnabled());assertTrue(web.getSettings().getBlockNetworkLoads());assertFalse(web.getSettings().getAllowFileAccess());
        ((android.widget.LinearLayout)page.getChildAt(0)).getChildAt(2).performClick();
        assertEquals(raw,((android.content.ClipboardManager)c.getSystemService(android.content.Context.CLIPBOARD_SERVICE)).getPrimaryClip().getItemAt(0).getText().toString());
      });
      Thread.sleep(700);
      // Test-only observation: production SVG previews always leave JavaScript disabled.
      i.runOnMainSync(()->preview[0].getSettings().setJavaScriptEnabled(true));
      assertEquals("true",PreviewTests.js(preview[0],"document.querySelector('circle').getBBox().width>0 && document.querySelector('svg').getCurrentTime()>0"));
      double first=Double.parseDouble(PreviewTests.js(preview[0],"document.querySelector('circle').r.animVal.value"));
      Thread.sleep(250);
      double second=Double.parseDouble(PreviewTests.js(preview[0],"document.querySelector('circle').r.animVal.value"));
      assertTrue(first>=9.9&&first<=20.1);assertTrue(second>=9.9&&second<=20.1);assertTrue("SMIL radius must animate",Math.abs(first-second)>.05);
      i.runOnMainSync(()->preview[0].getSettings().setJavaScriptEnabled(false));
      StreamingTests.screenshot(c,"svg-preview.png");
      i.runOnMainSync(()->{a.onBackPressed();assertSame(a.chatRoot,a.currentPage);});
      long time=android.os.SystemClock.uptimeMillis();
      i.runOnMainSync(()->{var event=android.view.MotionEvent.obtain(time,time,android.view.MotionEvent.ACTION_DOWN,30,30,0);a.turnViews.get("1").answer.dispatchTouchEvent(event);event.recycle();});
      Thread.sleep(android.view.ViewConfiguration.getLongPressTimeout()+150);
      i.runOnMainSync(()->{var event=android.view.MotionEvent.obtain(time,android.os.SystemClock.uptimeMillis(),android.view.MotionEvent.ACTION_UP,30,30,0);a.turnViews.get("1").answer.dispatchTouchEvent(event);event.recycle();assertTrue(a.messageActions.active());assertSame(a.chatRoot,a.currentPage);});
    } finally {i.runOnMainSync(a::finish);}
  }
}
