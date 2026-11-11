package dev.guga.dsh.settings;

import okhttp3.HttpUrl;
import okhttp3.Request;

/** Shared endpoint and authentication rules for model discovery and inference. */
public final class ModelEndpoint {
  private ModelEndpoint() {}
  public static final String DEEPSEEK = "deepseek", ANTHROPIC = "anthropic";

  public static String provider(String value) {
    if (!DEEPSEEK.equals(value) && !ANTHROPIC.equals(value))
      throw new IllegalArgumentException("请选择 DeepSeek 或 Claude 协议");
    return value;
  }

  public static String normalize(String value) {
    HttpUrl url = HttpUrl.parse(value.trim());
    if (url == null || !"https".equals(url.scheme()) || !url.username().isEmpty()
        || !url.password().isEmpty() || url.query() != null || url.fragment() != null)
      throw new IllegalArgumentException("请输入 HTTPS 基础地址，不含账号、查询参数或片段");
    String base = url.toString().replaceAll("/+$", "");
    if (base.endsWith("/messages") || base.endsWith("/chat/completions") || base.endsWith("/models"))
      throw new IllegalArgumentException("请填写基础地址或 /v1 地址，不要填写具体接口路径");
    return base;
  }

  public static String url(String protocol, String base, boolean models) {
    provider(protocol);
    base = normalize(base);
    if (ANTHROPIC.equals(protocol) && !base.endsWith("/v1")) base += "/v1";
    return base + (models ? "/models" : ANTHROPIC.equals(protocol) ? "/messages" : "/chat/completions");
  }

  public static String path(String value) {
    value=value.trim();
    if(!value.startsWith("/") || value.startsWith("//") || value.contains("?") || value.contains("#") || value.contains("\\") || value.contains("..") || value.contains(":") || value.contains(" "))
      throw new IllegalArgumentException("接口路径须以 / 开头，不含域名、查询参数或片段");
    return value;
  }
  public static String resolve(String base,String endpoint) {
    base=normalize(base);endpoint=path(endpoint);
    if(base.endsWith("/v1") && endpoint.startsWith("/v1/"))base=base.substring(0,base.length()-3);
    return base+endpoint;
  }

  public static Request.Builder configured(Settings settings,String provider,String kind,String secret) {
    String protocol=ProviderRegistry.get(provider).protocol;
    return request(protocol,settings.base(provider),"models".equals(kind),secret).url(settings.endpoint(provider,kind));
  }

  public static Request.Builder request(String protocol, String base, boolean models, String secret) {
    if (secret.trim().isEmpty()) throw new IllegalArgumentException("请在设置中填写 API Key");
    Request.Builder request = new Request.Builder().url(url(protocol, base, models));
    if (ANTHROPIC.equals(protocol)) {
      // Messages uses x-api-key; some relays require Bearer for /models as well.
      request.header("x-api-key", secret.trim()).header("anthropic-version", "2023-06-01");
      if (models) request.header("Authorization", "Bearer " + secret.trim());
    } else request.header("Authorization", "Bearer " + secret.trim());
    return request;
  }
}
