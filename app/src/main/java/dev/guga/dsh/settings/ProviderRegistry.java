package dev.guga.dsh.settings;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Provider metadata is separate from transport adapters in ModelEndpoint/runtime/providers. */
public final class ProviderRegistry {
  public static final class Provider {
    public final String id, label, protocol, base, model;
    Provider(String id, String label, String protocol, String base, String model) {
      this.id=id; this.label=label; this.protocol=protocol; this.base=base; this.model=model;
    }
  }
  public static final List<Provider> ALL = Collections.unmodifiableList(Arrays.asList(
    new Provider("deepseek", "DeepSeek", ModelEndpoint.DEEPSEEK, "https://api.deepseek.com", "deepseek-flash"),
    new Provider("anthropic", "Claude", ModelEndpoint.ANTHROPIC, "https://api.anthropic.com", "claude-opus-5-5")
  ));
  public static Provider get(String id) {
    for (Provider p : ALL) if (p.id.equals(id)) return p;
    throw new IllegalArgumentException("未知供应商：" + id);
  }
  public static final String[] ENDPOINTS={"model","models","search","fetch","usage"};
  public static String defaultPath(String provider,String kind) {
    boolean claude=ModelEndpoint.ANTHROPIC.equals(get(provider).protocol);
    switch(kind) {
      case "model":return claude?"/v1/messages":"/chat/completions";
      case "models":return claude?"/v1/models":"/models";
      case "search":return claude?"/v1/messages":"/v1/tools/search";
      case "fetch":return claude?"/v1/messages":"/v1/tools/fetch";
      case "usage":return claude?"/v1/organizations/usage_report/messages":"/user/balance";
      default:throw new IllegalArgumentException("未知接口类型");
    }
  }
  private ProviderRegistry() {}
}
