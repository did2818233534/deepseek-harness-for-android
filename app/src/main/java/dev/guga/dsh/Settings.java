package dev.guga.dsh;

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

final class Settings {

  private final SharedPreferences prefs;
  private final android.content.res.Resources defaults;

  Settings(Context c) {
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

  String apiKey() throws Exception {
    String saved = prefs.getString("key", "");
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

  void save(
    String base,
    String model,
    String secret,
    boolean thinking,
    int steps
  ) throws Exception {
    if (!"https://api.deepseek.com".equals(base)) throw new Exception("Pocket 仅支持 DeepSeek 官方 API");
    if (model.trim().isEmpty()) throw new Exception("模型名称不能为空");
    SharedPreferences.Editor edit = prefs
      .edit()
      .putString("base", base.replaceAll("/+$", ""))
      .putString("model", model.trim())
      .putBoolean("thinking", thinking)
      .putInt("steps", Math.max(1, Math.min(128, steps)));
    if (!secret.isEmpty()) {
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key());
      edit.putString(
        "key",
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
    if (!edit.commit()) throw new Exception("设置保存失败");
  }

  String base() {
    return defaults.getString(R.string.default_api_base);
  }

  String model() {
    return prefs.getString("model", defaults.getString(R.string.default_model));
  }

  String reasoningEffort() {
    String value = prefs.getString("reasoningEffort", defaults.getString(R.string.default_reasoning_effort));
    return ("none".equals(value) || "low".equals(value) || "high".equals(value) || "max".equals(value)) ? value : defaults.getString(R.string.default_reasoning_effort);
  }

  void saveReasoningEffort(String value) throws Exception {
    if (!("none".equals(value) || "low".equals(value) || "high".equals(value) || "max".equals(value))) throw new Exception("思考强度无效");
    if (!prefs.edit().putString("reasoningEffort", value).commit()) throw new Exception("设置保存失败");
  }

  boolean autoImageModel(){return prefs.getBoolean("autoImageModel",defaults.getBoolean(R.bool.default_auto_image_model));}
  void saveAutoImageModel(boolean enabled)throws Exception{if(!prefs.edit().putBoolean("autoImageModel",enabled).commit())throw new Exception("设置保存失败");}
  boolean webSearch() { return prefs.getBoolean("webSearch", defaults.getBoolean(R.bool.default_web_search)); }
  boolean webFetch() { return prefs.getBoolean("webFetch", defaults.getBoolean(R.bool.default_web_fetch)); }
  void saveWeb(boolean search, boolean fetch) throws Exception {
    if (!prefs.edit().putBoolean("webSearch", search).putBoolean("webFetch", fetch).commit()) throw new Exception("设置保存失败");
  }

  org.json.JSONArray modelCapabilities(){
    org.json.JSONArray out=new org.json.JSONArray();
    try {org.json.JSONArray rows=new JSONObject(prefs.getString("modelCatalog","{}")).optJSONArray("data");
      if(rows!=null)for(int n=0;n<rows.length();n++){JSONObject row=rows.optJSONObject(n);if(row!=null)out.put(new JSONObject().put("id",row.optString("id")).put("input_modalities",row.optJSONArray("input_modalities")));}
    }catch(Exception ignored){}return out;
  }
  JSONObject publicData() throws Exception {
    return new JSONObject()
      .put("base", base()).put("webSearch", webSearch()).put("webFetch", webFetch())
      .put("model", model()).put("models",modelCapabilities()).put("autoImageModel",autoImageModel())
      .put("hasKey", !apiKey().isEmpty())
      .put("thinking", !"none".equals(reasoningEffort()))
      .put("reasoningEffort", reasoningEffort())
      .put("maxSteps", prefs.getInt("steps", defaults.getInteger(R.integer.default_max_steps)));
  }
}
