package dev.guga.dsh;

import fi.iki.elonen.NanoHTTPD;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import okhttp3.*;
import org.json.*;
import org.jsoup.Jsoup;

final class NativeBridge extends NanoHTTPD {

  volatile HarnessService service;
  final Settings settings;
  final FileTools files;
  final String token;
  OkHttpClient api = new OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(120, TimeUnit.SECONDS)
    .callTimeout(10, TimeUnit.MINUTES)
    .followRedirects(false)
    .build();
  final OkHttpClient web = new OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .callTimeout(45, TimeUnit.SECONDS)
    .followRedirects(false)
    .dns(host -> {
      List<InetAddress> addresses = Arrays.asList(
        InetAddress.getAllByName(host)
      );
      for (InetAddress a : addresses)
        if (
          a.isAnyLocalAddress() ||
          a.isLoopbackAddress() ||
          a.isSiteLocalAddress() ||
          a.isLinkLocalAddress() ||
          a.isMulticastAddress() ||
          (a.getAddress().length == 16 && (a.getAddress()[0] & 0xfe) == 0xfc)
        ) throw new UnknownHostException("只允许公开互联网地址");
      return addresses;
    })
    .build();

  NativeBridge(HarnessService s, String token) throws Exception {
    super("127.0.0.1", 0);
    service = s;
    settings = new Settings(s.getApplicationContext());
    files = new FileTools(s.getApplicationContext());
    this.token = token;
  }

  @Override
  public Response serve(IHTTPSession session) {
    if (
      !("Bearer " + token).equals(session.getHeaders().get("authorization"))
    ) return json(Response.Status.UNAUTHORIZED, "未授权");
    if (session.getMethod() != Method.POST) return json(
      Response.Status.METHOD_NOT_ALLOWED,
      "POST required"
    );
    try {
      long length = Long.parseLong(
        session.getHeaders().getOrDefault("content-length", "0")
      );
      if (length > 3 * 1024 * 1024) return json(
        Response.Status.PAYLOAD_TOO_LARGE,
        "请求过大"
      );
      Map<String, String> body = new HashMap<>();
      // All bridge payloads are UTF-8 JSON. NanoHTTPD otherwise defaults a
      // charset-less Content-Type to US-ASCII and replaces every Chinese byte.
      session.getHeaders().put("content-type", "application/json; charset=utf-8");
      session.parseBody(body);
      JSONObject input = new JSONObject(body.getOrDefault("postData", "{}"));
      switch (session.getUri()) {
        case "/settings":
          return ok(settings.publicData());
        case "/status":
          HarnessService activeService = service;
          if (activeService != null) activeService.taskStatus(input.optBoolean("busy"));
          return ok(new JSONObject().put("ok", true));
        case "/cancel-network":
          api.dispatcher().cancelAll();
          web.dispatcher().cancelAll();
          return ok(new JSONObject().put("ok", true));
        case "/web-api":
          return ok(webApi(input));
        case "/model":
          return model(input);
        case "/tool":
          return ok(tool(input.getString("name"), input.optJSONObject("args")));
        default:
          return json(Response.Status.NOT_FOUND, "接口不存在");
      }
    } catch (Exception e) {
      String msg =
        e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
      try {
        String secret = settings.apiKey();
        if (!secret.isEmpty()) msg = msg.replace(secret, "[已隐藏]");
      } catch (Exception ignored) {
        /* Original failure is reported without reading unavailable credentials. */
      }
      return json(Response.Status.BAD_REQUEST, msg);
    }
  }

  static Response ok(Object value) {
    return newFixedLengthResponse(
      Response.Status.OK,
      "application/json; charset=utf-8",
      value.toString()
    );
  }

  static Response json(Response.Status status, String error) {
    try {
      return newFixedLengthResponse(
        status,
        "application/json; charset=utf-8",
        new JSONObject().put("error", error).toString()
      );
    } catch (JSONException e) {
      throw new IllegalStateException(e);
    }
  }

  private Request request(String url, String body) throws Exception {
    String key = settings.apiKey();
    if (key.isEmpty()) throw new IOException("请在设置中填写 API Key");
    return new Request.Builder()
      .url(url)
      .header("Authorization", "Bearer " + key)
      .header("User-Agent", "DSH-Pocket/0.1 Android")
      .post(
        RequestBody.create(
          body,
          MediaType.get("application/json; charset=utf-8")
        )
      )
      .build();
  }

  private Response model(JSONObject input) throws Exception {
    okhttp3.Response remote = api
      .newCall(request(settings.base() + "/chat/completions", input.toString()))
      .execute();
    if (!remote.isSuccessful()) {
      String message = remote.body() == null ? "" : remote.body().string();
      int code = remote.code();
      remote.close();
      throw new IOException(
        "模型 API " +
          code +
          ": " +
          message.substring(0, Math.min(message.length(), 1200))
      );
    }
    if (remote.body() == null) {
      remote.close();
      throw new IOException("模型没有返回内容");
    }
    InputStream stream = new FilterInputStream(remote.body().byteStream()) {
      @Override
      public void close() throws IOException {
        try {
          super.close();
        } finally {
          remote.close();
        }
      }
    };
    return newChunkedResponse(
      Response.Status.OK,
      "text/event-stream; charset=utf-8",
      stream
    );
  }

  Object tool(String name, JSONObject a) throws Exception {
    if (a == null) a = new JSONObject();
    switch (name) {
      case "list_files":
        return new JSONObject().put("files", files.list());
      case "read_file":
        return files.read(a);
      case "write_file":
        return files.write(a, false);
      case "create_document":
        return files.write(a, true);
      default:
        throw new IOException("未知工具：" + name);
    }
  }

  JSONObject webApi(JSONObject input) throws Exception {
    String endpoint = input.getString("endpoint");
    boolean search = "https://api.deepseek.com/v1/tools/search".equals(endpoint);
    boolean fetch = "https://api.deepseek.com/v1/tools/fetch".equals(endpoint);
    if (!search && !fetch) throw new IOException("不支持的联网接口");
    if ((search && !settings.webSearch()) || (fetch && !settings.webFetch())) throw new IOException("此联网工具已关闭");
    try (okhttp3.Response response = api.newBuilder().callTimeout(30, TimeUnit.SECONDS).build().newCall(request(endpoint, input.getJSONObject("body").toString())).execute()) {
      if (!response.isSuccessful()) throw new IOException("DeepSeek 联网接口返回 HTTP " + response.code());
      return new JSONObject(new String(FileTools.readLimited(response.body().byteStream(), 8 * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8));
    }
  }
}
