package dev.guga.dsh;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.*;
import org.json.JSONObject;

final class Api {

  static final OkHttpClient client = new OkHttpClient.Builder()
    .connectTimeout(3, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .build();

  static JSONObject call(String route, JSONObject body) throws Exception {
    int port = 0;
    long deadline = android.os.SystemClock.elapsedRealtime() + 15000;
    while (port == 0) {
      try { port = HarnessService.port(); }
      catch (Exception pending) {
        if (!HarnessService.failure.isEmpty() || android.os.SystemClock.elapsedRealtime() >= deadline)
          throw new IOException("暂时无法处理，请关闭并重新打开应用", pending);
        Thread.sleep(100);
      }
    }
    Request r = new Request.Builder()
      .url("http://127.0.0.1:" + port + route)
      .header("Authorization", "Bearer " + HarnessService.token)
      .post(
        RequestBody.create(body.toString(), MediaType.get("application/json; charset=utf-8"))
      )
      .build();
    try (Response response = client.newCall(r).execute()) {
      JSONObject data = new JSONObject(response.body().string());
      if (!response.isSuccessful()) throw new IOException(
        data.optString("error", "请求失败")
      );
      return data;
    }
  }
}
