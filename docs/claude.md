# Claude 与中转站测试版

本分支在 Android 自有适配层加入 Anthropic Messages API，保留原来的 DeepSeek Chat Completions 路径，不修改固定版本的 `vendor/harness`。

## 安装和配置

安装 `app-full-arm64-v8a-claudeTest.apk`（常见 ARM64 手机）或对应 x86_64 包。测试版名称为 **DSH Claude 测试版**，包名 `dev.guga.dsh.full.claudetest`，与正式完整版 `dev.guga.dsh.full`、轻量版分别存储聊天和设置。测试版关闭正式发布渠道的自动和手动更新；后续使用同签名测试 APK 覆盖安装可保留测试数据。

1. 打开「设置 → 模型与服务」。
2. 供应商列表默认只开启 DeepSeek。打开 **Claude / Anthropic** 的开关，再点击该供应商进入具体设置。开启的供应商自动排到关闭项之前；关闭保留配置，但不会在聊天模型菜单中出现。
3. 在该供应商的「更多」页面，「API 基础地址」填服务地址，例如 `https://api.lumivia.cn`，也支持 `https://api.lumivia.cn/v1`。有路径前缀的中转服务可以填 `https://example.com/proxy/v1`。不要填完整的 `/messages` 路径。
4. 回到供应商详情，填入该服务的 API Key。密钥仅在手机上由 Android Keystore 加密保存；不随 APK 或源码分发。
5. 点击模型框内的刷新按钮，在下拉列表勾选聊天页允许使用的模型。中转站未实现模型列表接口时，可在「更多」填写「模型 ID」并点击「添加到聊天模型」，默认 `claude-opus-5-5`。地址、密钥和模型输入会自动保存；非法地址不覆盖已保存配置。
6. 可再配置 DeepSeek 并勾选模型；返回聊天的模型下拉，两家已启用的模型会同时列出，选择时自动切换地址和密钥。发送短消息验证，再测试附件和文件处理。

模型列表请求为 `GET <base>/v1/models`，对话为 `POST <base>/v1/messages`，基础地址末尾已有 `/v1` 时不会重复添加。两者带 `x-api-key` 和 `anthropic-version: 2023-06-01`；模型列表额外带同一服务密钥的 Bearer 头，兼容中转站的列表认证。仅接受 HTTPS，禁止携带用户名、密码、查询参数或 URL 片段，且不会跟随认证请求的重定向。

DeepSeek 和 Claude 分别保存地址、密钥、模型和模型缓存。设置页切换供应商仅编辑该配置，不改变聊天模型。选择以供应商 ID 和模型 ID 组合保存，相同模型名不会互相覆盖；请求携带供应商快照，另一对话切换模型不会串用密钥。模型列表刷新不会自动选择第一项。更换地址或密钥会清除旧列表，过期网络回调不会覆盖新配置。已有 DeepSeek 用户无需迁移数据。

打开应用或回到前台时，每个已开启且配置密钥的供应商按手机本地日期最多自动尝试更新列表一次。失败也计入当天次数，保留旧缓存；手动刷新不限次数，并计入当天已更新。刷新不新增勾选、不移除已有勾选。设置页已勾选的模型即时置顶，Claude 在相同启用状态内及聊天下拉中按数字版本降序、同版本按日期排序；无法识别的自定义别名放末尾，不虚构发布日期。

## 搜索和本地后备

Claude 搜索由现有 `web_search` 工具入口发起独立的 Messages 请求，使用**当前所填基地址、当前模型及该供应商密钥**，携带官方 `web_search_20250305` 工具。填写 `https://relay.example/gateway/v1` 时，实际请求为 `https://relay.example/gateway/v1/messages`，不会偷偷改为 Anthropic 官方域名。官方基础地址仅是未配置时的默认值。

云端 HTTP 错误、超时、HTTP 200 中的工具错误、未实际执行搜索均触发手机端 DuckDuckGo 网页搜索后备。成功但零结果不当作失败。暂停的云端搜索最多续接一次，续接保留原始加密块。源链接和引用摘要会转换为现有 Harness 搜索结果；不将加密内容当成明文证据。取消操作不触发后备搜索。两种途径都失败时明确报错，不编造结果。

DeepSeek 默认基地址为官方地址，搜索默认路径为 `/v1/tools/search`，同样允许在“更多”修改基地址与搜索路径。搜索使用当前 DeepSeek 配置的密钥；云端失败时回退手机访问公开网页。网页读取默认路径为基地址下的 `/v1/tools/fetch`，也可修改。Claude 网页读取优先调用当前基地址的 Messages 接口及官方 web_fetch_20250910 工具，HTTP 失败、工具错误或无有效文本时回退手机读取。这里的“本地”是由手机联网请求公开搜索/网页，不是离线索引；网络限制、验证码或网页结构变化仍可能导致失败。

Claude 云端搜索产生搜索费用及额外模型 token 用量，详情以所配置服务为准。依据：[官方 Web Search](https://platform.claude.com/docs/en/agents-and-tools/tool-use/web-search-tool)。

## 更多设置与恢复

「设置 → 模型与服务」首先显示所有已支持供应商的列表，每项有独立开关，开启项置顶。仅 DeepSeek 默认开启，Claude 默认关闭。点击卡片进入详情，详情提供 API Key、模型勾选和手动刷新；基地址、手填模型 ID、接口路径和用量查询在详情的“更多”中。布局继续使用当前应用的主题与公共圆角控件，不复制参考图的颜色或品牌卡片样式。编辑供应商与聊天当前供应商分开；从更多返回详情时同步最新配置，避免旧页面覆盖新设置。

| 接口 | Claude 默认路径 | DeepSeek 默认路径 |
| --- | --- | --- |
| 对话 | `/v1/messages` | `/chat/completions` |
| 模型列表 | `/v1/models` | `/models` |
| 云端搜索 | `/v1/messages` | `/v1/tools/search` |
| 网页读取 | `/v1/messages` | `/v1/tools/fetch` |
| 用量 / 余额 | `/v1/organizations/usage_report/messages` | `/user/balance` |

路径自动保存，必须以 `/` 开头；实际地址为基地址加路径，避免重复 `/v1`。例如基地址 `https://relay.example/prefix/v1` 和搜索路径 `/custom/search` 会请求 `https://relay.example/prefix/v1/custom/search`。模型主请求也携带最终接口快照，防止配置变更后混用路由。

“查询用量 / 余额”仅手动触发。Claude 默认采用官方仍支持的 Usage Report API，查询最近 24 小时，通常需要 Admin API Key；可填写独立的用量查询密钥并加密保存，留空则用当前服务密钥。自定义接口可返回自己的 JSON 报表，不假定所有中转站格式相同。参考：[Claude 用量接口](https://platform.claude.com/docs/en/api/beta/organization/usage_report/retrieve_messages)、[Admin API Key](https://platform.claude.com/docs/en/manage-claude/admin-api-keys)、[DeepSeek 余额](https://api-docs.deepseek.com/api/get-user-balance/)。

“恢复当前供应商初始设置”恢复该供应商的官方默认基地址、接口路径及手填默认模型 ID，清理其模型缓存；保留密钥、勾选模型、聊天记录及其他供应商配置。模型思考档位、通用设置和工具开关不随连接配置重置。

## 供应商扩展

`ProviderRegistry` 统一注册稳定供应商 ID、显示名、传输协议、默认地址及默认模型。`Settings` 用 ID 隔离密钥/缓存/勾选项，旧 DeepSeek 字段兼容保留；`ModelEndpoint` 与 `runtime/providers` 实现协议。新增兼容供应商可注册相应元数据；新协议需增加端点认证与流解析适配，不修改 vendor。自动刷新、模型管理和聊天列表遍历注册表。

## 功能范围

- 支持流式中文回复、思考展示、工具调用与结果回传、图片、标题生成，以及 Harness 的上下文整理、取消和重试。
- 通过 Harness 的 replay envelope 持久化签名思考与加密思考块，在同一路由中续传。跨地址、跨模型不会复用签名。
- 思考强度在聊天底部面板按当前模型加载；档位及默认值来自与运行时共享的能力表，按供应商与模型分别保存。旧 Claude 模型显示 token 预算，未知别名省略思考参数。中转站明确拒绝思考参数时，在收到流之前移除这些可选参数重试一次；不对已输出、鉴权失败或上下文溢出的请求盲目重试。
- Claude 模型使用原生 base64 图片块；图片自动切换只查找当前服务列表里的视觉模型，绝不转到 DeepSeek。非视觉模型无可用替代时仅收到文件说明。
- 联网搜索按上文策略走当前 Claude 服务或 DeepSeek 官方接口，并提供手机端后备；不需要为 Claude 搜索另配 DeepSeek 密钥。
- 暂不支持 Bedrock/Vertex 专用签名协议、聊天主流中未经适配的 Claude 服务端工具内容块、服务端 fallback 内容块。中转站必须提供兼容的 Messages SSE 协议。

测试时乐米站点的模型列表曾成功返回，但一次真实对话请求返回 `502 / Upstream access forbidden`。客户端兼容不保证账户或上游通道可用；此错误需要中转站处理。仓库不包含真实密钥，自动测试全部使用模拟数据。

## 构建和测试

构建工具可安装到任意账号可写路径。通过 `DSH_BUILD_TOOLS` 指定包含 `node_modules` 的工具目录，默认仍兼容原工程的 `/opt/dsh-android/build-tools`。Android SDK、NDK 和 nodejs-mobile 分别由 `ANDROID_HOME`、`NDK_DIR`、`NODE_MOBILE_DIR` 指定。依赖版本与完整构建准备见 [开发文档](development.md)。

```sh
node scripts/bundle.mjs
bash scripts/build-native.sh
# 准备两种架构的 Full 运行环境，参见 scripts/build.sh
bash scripts/termux/build-guard.sh
./gradlew --no-daemon -PtestBuildType=claudeTest \
  :app:assembleFullClaudeTest :app:assembleFullClaudeTestAndroidTest

node tests/anthropic.mjs
node tests/anthropic-runtime.mjs
node tests/runtime.mjs
node tests/polyfills.mjs
node tests/file-cleanup.mjs
node tests/archives.mjs
```

`ClaudeSettingsTests` 覆盖地址验证、认证头、老设置兼容、服务反复切换、密钥与模型缓存隔离，以及无联网密钥的行为。专用无密钥模拟器上可运行界面回归；不可对带真实账号数据的手机运行这些测试。

协议依据：[Anthropic 流式消息](https://platform.claude.com/docs/en/build-with-claude/streaming)、[思考参数兼容](https://platform.claude.com/docs/en/build-with-claude/thinking-troubleshooting)。

## 本次验证限制

自动测试验证了 Claude 流式协议、实际 Harness 工具循环、签名思考持久化、重启恢复、断流反复重试、取消和图片传输；原有 DeepSeek 核心回归通过。ARM64、x86_64 与通用测试 APK 均编译成功，已核验测试包名及独立的 Provider authorities。

在无 KVM 的 Android 11 x86_64 软件模拟器里，测试版与原完整版包名的构建同时安装成功。但两个构建均在内嵌 nodejs-mobile 18.20.4 读取 `bootstrap.cjs` 时出现 `Buffer.utf8Slice / ERR_OUT_OF_RANGE`，随后原生进程退出。错误发生于加载业务运行时之前；尚未确定根因，也未证明 ARM64 真机是否受影响。

本轮 19 项 Android 测试在隔离原生核心后通过，新增通用设置保存/校验/返回、跨模型思考档位切换与独立持久化、Claude 云端读取成功及失败解析；包含触摸事件驱动的供应商重复切换、全选/取消/再次选择、聊天跨供应商切换、更多页面可见性及恢复操作；另覆盖供应商开关、启用置顶、详情返回、每日刷新、端点隔离、用量密钥、云端成功/HTTP 错误/超时/工具错误回退和模型排序。Android 设置测试可通过 `-e skipNativeCore true` 单独验证 Java 设置层和真实触摸操作；这不代表 Android 原生核心端到端验证通过。ARM64 真机启动、实际中转站对话及后台任务/通知生命周期仍待验证。没有将真实账户、密钥或聊天数据写入测试包。

## 离线开发分支

本次交付包含 `claude-relay-support.bundle`，保存完整 Git 历史及 `feat/claude-relay-support` 分支；另附本次迭代补丁。当前连接的远程写入被审批策略拒绝，分支尚未推送 GitHub。可在有写权限的电脑导入后推送：

```sh
git clone -b feat/claude-relay-support claude-relay-support.bundle dsh-claude
cd dsh-claude
git remote set-url origin git@github.com:did2818233534/deepseek-harness-for-android.git
git push -u origin feat/claude-relay-support
```


## 通用设置

「设置 → 通用设置」集中保存最长思考时间、联网搜索、网页读取、图片自动切换模型和每轮最多步骤。供应商详情及“更多”不再包含这些选项。沿用原有偏好键，升级保留旧值；编辑或重置供应商不会覆盖通用设置。聊天快捷面板的联网搜索与这里共享设置；思考强度只在聊天面板按模型设置。数值校验失败不覆盖已保存值，通用设置一次性保存。

## 运行中引导的接口边界（2026-11-07）

目前已接入的 Claude Messages 与 DeepSeek Chat Completions 都使用现有 Harness 引导队列，不中断当前请求，在后续步骤消费新增消息。

- [Claude Message threads](https://platform.claude.com/docs/en/build-with-claude/message-threads)：必须收到 message_stop 后才可从该消息继续；不是运行中追加接口。
- [Claude Managed Agents](https://platform.claude.com/docs/en/managed-agents/overview)：支持运行中发送用户事件，但属于另一套代理执行服务，须单独创建 agent、environment 和 session，不能把手机 Harness 的会话 ID 直接用于它。
- [DeepSeek Responses 兼容说明](https://api-docs.deepseek.com/guides/responses_api/)：为无状态 API，不支持 conversation、previous_response_id 或 background；未找到适用于当前协议的原生运行中追加入口。

运行时新增 NativeSteering 扩展协议和统一分发：绑定当前会话的传输适配器提供能力时先调用原生入口，明确接受后不重复入队；不支持或调用失败则进入现有队列。当前两个适配器均返回“不支持”，没有虚构或探测 /steer 云端路径，也未启用 Managed Agents。未来原生适配器必须复用当前供应商基地址和认证，保存真实远端会话，并负责本地记录同步及确认丢失的去重；本次只有扩展与回退逻辑，没有宣称实际云端原生引导已接通。


## 模型档位与通用能力回退

- DeepSeek V4 / Flash：关闭、低、高、最高。
- Claude Opus 5.5：低、中、高、超高、最高，默认中，不能关闭。Sonnet 4.6 不提供超高；旧版 Haiku / Sonnet 4.x 使用思考 token 预算。未知别名没有已确认的档位时不显示滑条，使用服务默认行为。
- 每个供应商 + 模型分别记住档位，切换聊天模型立即刷新滑条。旧全局档位作为兼容初值，仅在新模型支持该值时沿用；不支持的旧值使用模型默认档。
- 联网搜索和网页读取：当前供应商原生接口优先，失败使用手机侧后备；两者均失败时报告工具错误。
- 最长思考时间：当前接口没有等价的秒数参数，由应用侧计时回退；不可关闭思考的模型使用其最低合法档位配合直接回答提示，不保证厂商立即停止内部推理。
- 图片自动切换与每轮步骤上限：由客户端模型选择与 Harness 控制，没有将不同语义的 token 预算或工具次数冒充成这些原生参数。
- 没有已确认原生能力且没有等价通用回退的可选配置不发送。扩展新供应商时需显式声明能力，不能只因模型名包含 Claude 或兼容 OpenAI 就猜测支持。

依据：[DeepSeek 思考档位](https://api-docs.deepseek.com/guides/thinking_mode/)、[Claude Effort](https://platform.claude.com/docs/en/build-with-claude/effort)、[Claude Web Fetch](https://platform.claude.com/docs/en/agents-and-tools/tool-use/web-fetch-tool)。
