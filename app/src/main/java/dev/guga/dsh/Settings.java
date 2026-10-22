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

  Settings(Context c) {
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
    return "https://api.deepseek.com";
  }

  String model() {
    return prefs.getString("model", "deepseek-v4-pro");
  }

  boolean webSearch() { return prefs.getBoolean("webSearch", true); }
  boolean webFetch() { return prefs.getBoolean("webFetch", true); }
  void saveWeb(boolean search, boolean fetch) throws Exception {
    if (!prefs.edit().putBoolean("webSearch", search).putBoolean("webFetch", fetch).commit()) throw new Exception("设置保存失败");
  }

  JSONObject publicData() throws Exception {
    return new JSONObject()
      .put("base", base()).put("webSearch", webSearch()).put("webFetch", webFetch())
      .put("model", model())
      .put("hasKey", !apiKey().isEmpty())
      .put("thinking", prefs.getBoolean("thinking", false))
      .put("maxSteps", prefs.getInt("steps", 32));
  }
}
