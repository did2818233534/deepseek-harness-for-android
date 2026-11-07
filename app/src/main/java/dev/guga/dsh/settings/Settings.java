package dev.guga.dsh.settings;

import dev.guga.dsh.R;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;

public final class Settings {

  private final SharedPreferences prefs;
  private final android.content.res.Resources defaults;

  public Settings(Context c) {
    defaults = c.getResources();
    prefs = c.getSharedPreferences("settings", Context.MODE_PRIVATE);
  }

  private SecretKey key() throws Exception {
    KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
    ks.load(null);
    if (!ks.containsAlias("dsh-api-key")) {
      KeyGenerator gen = KeyGenerator.getInstance(
        KeyProperties.KEY_ALGORITHM_AES,
        "AndroidKeyStore"
      );
      gen.init(
        new KeyGenParameterSpec.Builder(
          "dsh-api-key",
          KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        )
          .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
          .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
          .build()
      );
      gen.generateKey();
    }
    return (SecretKey) ks.getKey("dsh-api-key", null);
  }

  public String apiKey() throws Exception {
    return apiKey(provider());
  }

  public String apiKey(String protocol) throws Exception {
    return decrypt(field(protocol,"key"));
  }
  private String decrypt(String name) throws Exception {
    String saved = prefs.getString(name, "");
    if (saved.isEmpty()) return "";
    String[] p = saved.split(":");
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(
      Cipher.DECRYPT_MODE,
      key(),
      new GCMParameterSpec(128, Base64.decode(p[0], Base64.NO_WRAP))
    );
    return new String(
      cipher.doFinal(Base64.decode(p[1], Base64.NO_WRAP)),
      java.nio.charset.StandardCharsets.UTF_8
    );
  }

  public void save(
    String base,
    String model,
    String secret,
    boolean thinking,
    int steps
  ) throws Exception {
    save(provider(), base, model, secret, thinking, steps);
  }

  public void save(String protocol, String base, String model, String secret, boolean thinking, int steps) throws Exception {
    saveProfile(protocol, base, model, secret, thinking, steps, true);
  }

  public void saveProfile(String protocol, String base, String model, String secret, boolean thinking, int steps, boolean activate) throws Exception {
    ProviderRegistry.get(protocol);
    base = ModelEndpoint.normalize(base);
    if (model.trim().isEmpty()) throw new Exception("模型名称不能为空");
    SharedPreferences.Editor edit = prefs
      .edit()
            .putString(field(protocol,"base"), base)
      .putString(field(protocol,"model"), model.trim())
      .putBoolean("thinking", thinking)
      .putInt("steps", Math.max(1, Math.min(128, steps)));
    if (activate) edit.putString("provider", protocol).remove("activeModel");
    else {
      // Freeze the active chat selection while editing a provider's draft model ID.
      if(!prefs.contains("activeModel")) edit.putString("activeModel",model());
      if(!prefs.contains(field(protocol,"enabledModels"))) {
        java.util.Set<String> enabled=enabledModels(protocol);
        if(enabled.isEmpty() && !secret.trim().isEmpty()) enabled.add(model.trim());
        edit.putStringSet(field(protocol,"enabledModels"),enabled);
      }
    }
    if (!secret.isEmpty()) {
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key());
      edit.putString(
        field(protocol,"key"),
        Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) +
          ":" +
          Base64.encodeToString(
            cipher.doFinal(
              secret.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8)
            ),
            Base64.NO_WRAP
          )
      );
    }
    if (!base.equals(base(protocol)) || (!secret.isEmpty() && !secret.trim().equals(apiKey(protocol))))
      edit.remove(field(protocol,"modelCatalog"));
    if (!edit.commit()) throw new Exception("设置保存失败");
  }

  private static String field(String protocol, String name) {
    return ModelEndpoint.DEEPSEEK.equals(ProviderRegistry.get(protocol).id) ? name : protocol + "." + name;
  }
  public String editingProvider(){String id=prefs.getString("editingProvider",provider());try{return ProviderRegistry.get(id).id;}catch(Exception ignored){return provider();}}
  public void saveEditingProvider(String id){ProviderRegistry.get(id);prefs.edit().putString("editingProvider",id).apply();}
  public String provider() { return prefs.getString("provider", ModelEndpoint.DEEPSEEK); }
  public String base() { return base(provider()); }
  public String base(String protocol) {
    return prefs.getString(field(protocol,"base"), !ModelEndpoint.DEEPSEEK.equals(protocol)
      ? ProviderRegistry.get(protocol).base : defaults.getString(R.string.default_api_base));
  }
  public String model() { return prefs.getString("activeModel",model(provider())); }
  public String model(String protocol) {
    return prefs.getString(field(protocol,"model"), !ModelEndpoint.DEEPSEEK.equals(protocol)
      ? ProviderRegistry.get(protocol).model : defaults.getString(R.string.default_model));
  }
  public String modelCatalog() { return modelCatalog(provider()); }
  public String modelCatalog(String provider) { return prefs.getString(field(provider,"modelCatalog"), ""); }
  public void saveModelCatalog(String value) { saveModelCatalog(provider(), value); }
  public void saveModelCatalog(String provider, String value) { prefs.edit().putString(field(provider,"modelCatalog"),value).apply(); }
  public boolean providerEnabled(String id) {ProviderRegistry.get(id);return prefs.getBoolean(field(id,"enabled"),ModelEndpoint.DEEPSEEK.equals(id));}
  public java.util.List<ProviderRegistry.Provider> providersByEnabled() {
    java.util.List<ProviderRegistry.Provider> result=new java.util.ArrayList<>(ProviderRegistry.ALL);
    result.sort((a,b)->Boolean.compare(providerEnabled(b.id),providerEnabled(a.id)));return result;
  }
  public void setProviderEnabled(String id,boolean enabled) throws Exception {
    ProviderRegistry.get(id);SharedPreferences.Editor edit=prefs.edit().putBoolean(field(id,"enabled"),enabled);
    if(!enabled && id.equals(provider())) {
      outer: for(ProviderRegistry.Provider candidate:providersByEnabled())if(!candidate.id.equals(id) && providerEnabled(candidate.id)) {
        for(String model:enabledModels(candidate.id)){edit.putString("provider",candidate.id).putString("activeModel",model);break outer;}
      }
    }
    if(!edit.commit())throw new Exception("供应商开关保存失败");
  }
  public java.util.Set<String> enabledModels(String provider) {
    java.util.Set<String> fallback=new java.util.LinkedHashSet<>();
    if (!prefs.getString(field(provider,"key"), "").isEmpty()) fallback.add(model(provider));
    return new java.util.TreeSet<>(prefs.getStringSet(field(provider,"enabledModels"), fallback));
  }
  public void setModelEnabled(String provider,String id,boolean enabled) throws Exception {
    if(id.trim().isEmpty()) throw new Exception("模型 ID 不能为空");
    java.util.Set<String> ids=enabledModels(provider);
    if(enabled) ids.add(id.trim()); else ids.remove(id.trim());
    SharedPreferences.Editor edit=prefs.edit().putStringSet(field(provider,"enabledModels"),ids);
    if(!enabled && provider.equals(provider()) && id.equals(model())) {
      outer: for(ProviderRegistry.Provider candidate:ProviderRegistry.ALL) {
        if(!providerEnabled(candidate.id))continue;
        java.util.Set<String> choices=candidate.id.equals(provider)?ids:enabledModels(candidate.id);
        for(String choice:choices){edit.putString("provider",candidate.id).putString("activeModel",choice);break outer;}
      }
    }
    if(!edit.commit()) throw new Exception("模型选择保存失败");
  }
  public void selectModel(String provider,String id) throws Exception {
    if(!providerEnabled(provider))throw new Exception("请先启用该供应商");
    if(!enabledModels(provider).contains(id)) throw new Exception("请先在供应商设置中启用此模型");
    if(!prefs.edit().putString("provider",provider).putString("activeModel",id).commit()) throw new Exception("模型选择保存失败");
  }
  public String endpointPath(String provider,String kind) { return prefs.getString(field(provider,"endpoint."+kind),ProviderRegistry.defaultPath(provider,kind)); }
  public String endpoint(String provider,String kind) { return ModelEndpoint.resolve(base(provider),endpointPath(provider,kind)); }
  public void saveEndpoints(String provider,java.util.Map<String,String> values) throws Exception {
    SharedPreferences.Editor edit=prefs.edit();boolean changed=false;
    for(String kind:ProviderRegistry.ENDPOINTS) {
      String path=ModelEndpoint.path(values.get(kind));
      changed|="models".equals(kind) && !path.equals(endpointPath(provider,kind));
      edit.putString(field(provider,"endpoint."+kind),path);
    }
    if(changed){edit.remove(field(provider,"modelCatalog"));edit.putInt(field(provider,"catalogRequest"),prefs.getInt(field(provider,"catalogRequest"),0)+1);}
    if(!edit.commit())throw new Exception("接口设置保存失败");
  }
  public String usageKey(String provider) throws Exception {return decrypt(field(provider,"usageKey"));}
  public void saveUsageKey(String provider,String value) throws Exception {
    if(value.equals(usageKey(provider)))return;
    SharedPreferences.Editor edit=prefs.edit();
    if(value.trim().isEmpty())edit.remove(field(provider,"usageKey"));
    else {
      Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
      edit.putString(field(provider,"usageKey"),Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(cipher.doFinal(value.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8)),Base64.NO_WRAP));
    }
    if(!edit.commit())throw new Exception("用量查询密钥保存失败");
  }
  public void resetProviderDefaults(String provider) throws Exception {
    ProviderRegistry.Provider defaults=ProviderRegistry.get(provider);
    SharedPreferences.Editor edit=prefs.edit().putString(field(provider,"base"),defaults.base).putString(field(provider,"model"),defaults.model)
      .remove(field(provider,"modelCatalog")).putInt(field(provider,"catalogRequest"),prefs.getInt(field(provider,"catalogRequest"),0)+1);
    for(String kind:ProviderRegistry.ENDPOINTS)edit.remove(field(provider,"endpoint."+kind));
    if(!edit.commit())throw new Exception("恢复设置失败");
  }
  public int beginCatalogRequest(String provider) {
    synchronized(Settings.class) {
      String name=field(provider,"catalogRequest");int version=prefs.getInt(name,0)+1;
      prefs.edit().putInt(name,version).commit();return version;
    }
  }
  public boolean isCatalogRequestCurrent(String provider,int version){return prefs.getInt(field(provider,"catalogRequest"),0)==version;}
  public synchronized boolean claimCatalogRefresh(String provider, String day) {
    // Claim before requesting: failed automatic attempts also count for the day.
    synchronized(Settings.class) {
      String name=field(provider,"catalogAttemptDay");
      if(day.equals(prefs.getString(name,"")))return false;
      return prefs.edit().putString(name,day).commit();
    }
  }
  public void markCatalogRefresh(String provider,String day) { prefs.edit().putString(field(provider,"catalogAttemptDay"),day).apply(); }
  public boolean hasWebKey() {
    String base=base(ModelEndpoint.DEEPSEEK);
    return ("https://api.deepseek.com".equals(base) || "https://api.deepseek.com/v1".equals(base))
      && !prefs.getString("key", "").isEmpty();
  }


  public void saveThinkingLimit(boolean enabled,int seconds) {
    prefs.edit().putBoolean("limitThinking",enabled).putInt("thinkingSeconds",seconds).apply();
  }

  public String reasoningEffort() {
    String value = prefs.getString("reasoningEffort", defaults.getString(R.string.default_reasoning_effort));
    return ("none".equals(value) || "low".equals(value) || "high".equals(value) || "max".equals(value)) ? value : defaults.getString(R.string.default_reasoning_effort);
  }

  public void saveReasoningEffort(String value) throws Exception {
    if (!("none".equals(value) || "low".equals(value) || "high".equals(value) || "max".equals(value))) throw new Exception("思考强度无效");
    if (!prefs.edit().putString("reasoningEffort", value).commit()) throw new Exception("设置保存失败");
  }

  public boolean cleanupConversationFiles(){return prefs.getBoolean("cleanupConversationFiles",true);}
  public void saveCleanupConversationFiles(boolean enabled)throws Exception{if(!prefs.edit().putBoolean("cleanupConversationFiles",enabled).commit())throw new Exception("设置保存失败");}
  public String customInstructions(){return prefs.getString("customInstructions","");}
  public void saveCustomInstructions(String value){prefs.edit().putString("customInstructions",value).apply();}
  public boolean autoImageModel(){return prefs.getBoolean("autoImageModel",defaults.getBoolean(R.bool.default_auto_image_model));}
  public void saveAutoImageModel(boolean enabled)throws Exception{if(!prefs.edit().putBoolean("autoImageModel",enabled).commit())throw new Exception("设置保存失败");}
  public boolean webSearch() { return prefs.getBoolean("webSearch", defaults.getBoolean(R.bool.default_web_search)); }
  public boolean webFetch() { return prefs.getBoolean("webFetch", defaults.getBoolean(R.bool.default_web_fetch)); }
  public void saveWeb(boolean search, boolean fetch) throws Exception {
    if (!prefs.edit().putBoolean("webSearch", search).putBoolean("webFetch", fetch).commit()) throw new Exception("设置保存失败");
  }

  public org.json.JSONArray modelCapabilities(){
    org.json.JSONArray out=new org.json.JSONArray();
    try {
      org.json.JSONArray rows=new JSONObject(modelCatalog().isEmpty()?"{}":modelCatalog()).optJSONArray("data");
      for(String id:enabledModels(provider())) {
        JSONObject entry=new JSONObject().put("id",id);
        if(rows!=null)for(int n=0;n<rows.length();n++) {
          JSONObject row=rows.optJSONObject(n);
          if(row!=null && id.equals(row.optString("id")))entry.put("input_modalities",row.optJSONArray("input_modalities"));
        }
        out.put(entry);
      }
    }catch(Exception ignored){}return out;
  }
  public JSONObject publicData() throws Exception {
    return new JSONObject()
      .put("providerId",provider()).put("provider",ProviderRegistry.get(provider()).protocol).put("base", base()).put("modelEndpoint",endpoint(provider(),"model"))
      .put("webSearch", webSearch()).put("webFetch", webFetch())
      .put("model", model()).put("models",modelCapabilities()).put("autoImageModel",autoImageModel())
      .put("customInstructions",customInstructions()).put("cleanupConversationFiles",cleanupConversationFiles())
      .put("hasKey", providerEnabled(provider()) && !apiKey().isEmpty() && enabledModels(provider()).contains(model()))
      .put("thinking", !"none".equals(reasoningEffort()))
      .put("reasoningEffort", reasoningEffort())
      .put("limitThinking",prefs.getBoolean("limitThinking",false)).put("thinkingSeconds",prefs.getInt("thinkingSeconds",30))
      .put("maxSteps", prefs.getInt("steps", defaults.getInteger(R.integer.default_max_steps)));
  }
}
