package dev.guga.dsh.settings;
import android.content.Context;
import org.json.*;
import java.util.*;
/** Shared with the runtime transport: no synthetic provider-independent effort scale. */
public final class ReasoningOptions {
  private static JSONArray rules;
  public final String[] levels;
  public final String defaultValue, mode, off;
  private ReasoningOptions(JSONObject row,String model) throws Exception {
    JSONArray values=row.optJSONArray("levels");
    levels=new String[values==null?0:values.length()];
    for(int i=0;i<levels.length;i++)levels[i]=values.getString(i);
    String fallback=row.optString("default","default");
    defaultValue=model.matches("^claude-(opus|haiku)-5-5.*")?"medium":fallback;
    mode=row.optString("mode","");off=row.optString("off","");
  }
  public static ReasoningOptions forModel(Context context,String provider,String model) {
    try {
      if(rules==null)try(var input=context.getAssets().open("reasoning-models.json")) {
        rules=new JSONArray(new String(dev.guga.dsh.files.FileTools.readLimited(input,65536),java.nio.charset.StandardCharsets.UTF_8));
      }
      String normalized=model.toLowerCase(Locale.ROOT).replace('.','-');
      for(int i=0;i<rules.length();i++){
        JSONObject row=rules.getJSONObject(i);
        if(row.getString("provider").equals(provider)&&normalized.matches(row.getString("pattern")))
          return new ReasoningOptions(row,normalized);
      }
      return new ReasoningOptions(new JSONObject(),normalized);
    }catch(Exception e){throw new IllegalStateException("无法加载模型思考能力",e);}
  }
  public boolean supports(String value){return Arrays.asList(levels).contains(value);}
  public String label(String value) {
    if(value.equals("none"))return off.equals("between_tools")?"仅工具间思考":"关闭";
    if(mode.equals("budget"))return value.equals("low")?"低 · 1024 tokens":value.equals("max")?"高 · 8192 tokens":"中 · 4096 tokens";
    switch(value){case "low":return "低";case "medium":return "中";case "high":return "高";case "xhigh":return "超高";case "max":return "最高";default:return "模型默认";}
  }
}
