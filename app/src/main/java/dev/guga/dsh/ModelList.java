package dev.guga.dsh;

import java.util.*;
import org.json.*;

final class ModelList {
  static List<String> parse(String json) throws JSONException {
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
}
