package dev.guga.dsh.settings;

import java.util.*;
import org.json.*;

public final class ModelList {
  public static List<String> parse(String json) throws JSONException {
    JSONArray data = new JSONObject(json).getJSONArray("data");
    Set<String> ids = new LinkedHashSet<>();
    for (int i = 0; i < data.length(); i++) {
      JSONObject item = data.optJSONObject(i);
      if (item == null || !(item.opt("id") instanceof String)) continue;
      String id = item.getString("id").trim();
      if (!id.isEmpty()) ids.add(id);
    }
    if (ids.isEmpty()) throw new JSONException("Empty model list");
    return new ArrayList<>(ids);
  }
  /** Numeric versions first; release dates break ties. Unknown aliases remain stable at the end. */
  public static void newestClaudeFirst(List<String> ids,String catalog) {
    Map<String,Long> dates=new HashMap<>();
    try {
      JSONArray data=new JSONObject(catalog).optJSONArray("data");
      if(data!=null)for(int i=0;i<data.length();i++) {
        JSONObject item=data.optJSONObject(i);if(item==null)continue;
        long date=item.optLong("created",0);
        try{date=java.time.Instant.parse(item.optString("created_at")).getEpochSecond();}catch(Exception ignored){}
        dates.put(item.optString("id"),date);
      }
    }catch(Exception ignored){}
    ids.sort((a,b)->{
      int[] av=version(a),bv=version(b);
      for(int i=0;i<av.length;i++){int compared=Integer.compare(bv[i],av[i]);if(compared!=0)return compared;}
      int date=Long.compare(dates.getOrDefault(b,0L),dates.getOrDefault(a,0L));
      return date!=0?date:a.compareToIgnoreCase(b);
    });
  }
  static int[] version(String id) {
    String normalized=id.toLowerCase(java.util.Locale.ROOT).replace('.', '-');
    java.util.regex.Matcher modern=java.util.regex.Pattern.compile("^claude-(?:opus|sonnet|haiku|fable|mythos)-(\\d{1,3})(?:-(\\d{1,2})(?=-|$))?(?=-|$)").matcher(normalized);
    java.util.regex.Matcher legacy=java.util.regex.Pattern.compile("^claude-(\\d{1,3})(?:-(\\d{1,2}))?-(?:opus|sonnet|haiku)(?=-|$)").matcher(normalized);
    java.util.regex.Matcher m=modern.find()?modern:legacy.find()?legacy:null;
    if(m==null)return new int[]{-1,-1,0};
    java.util.regex.Matcher date=java.util.regex.Pattern.compile("(?:^|-)(20\\d{6})(?:-|$)").matcher(normalized);
    return new int[]{Integer.parseInt(m.group(1)),m.group(2)==null?0:Integer.parseInt(m.group(2)),date.find()?Integer.parseInt(date.group(1)):0};
  }
}
