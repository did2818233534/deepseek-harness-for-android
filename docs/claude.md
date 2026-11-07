# Claude 与中转站测试版

本分支在 Android 自有适配层加入 Anthropic Messages API，保留原来的 DeepSeek Chat Completions 路径，不修改固定版本的 `vendor/harness`。

## 安装和配置

安装 `app-full-arm64-v8a-claudeTest.apk`（常见 ARM64 手机）或对应 x86_64 包。测试版名称为 **DSH Claude 测试版**，包名 `dev.guga.dsh.full.claudetest`，与正式完整版 `dev.guga.dsh.full`、轻量版分别存储聊天和设置。测试版关闭正式发布渠道的自动和手动更新；后续使用同签名测试 APK 覆盖安装可保留测试数据。

1. 打开「设置 → 模型与服务 → DeepSeek / Claude」。
2. 「服务协议」选 **Claude / Anthropic**。
3. 「API 基础地址」填服务地址，例如 `https://api.lumivia.cn`，也支持 `https://api.lumivia.cn/v1`。有路径前缀的中转服务可以填 `https://example.com/proxy/v1`。不要填完整的 `/messages` 路径。
4. 填入该服务的 API Key。密钥仅在手机上由 Android Keystore 加密保存；不随 APK 或源码分发。
5. 点击模型框内的刷新按钮，从下拉列表选择模型。中转站未实现模型列表接口时，可直接填写「模型 ID」，例如 `claude-sonnet-4-6`。地址、密钥和模型输入会自动保存；非法地址不覆盖已保存配置。
6. 返回聊天，发送一条短消息验证，再测试附件和文件处理。

模型列表请求为 `GET <base>/v1/models`，对话为 `POST <base>/v1/messages`，基础地址末尾已有 `/v1` 时不会重复添加。两者带 `x-api-key` 和 `anthropic-version: 2023-06-01`；模型列表额外带同一服务密钥的 Bearer 头，兼容中转站的列表认证。仅接受 HTTPS，禁止携带用户名、密码、查询参数或 URL 片段，且不会跟随认证请求的重定向。

DeepSeek 和 Claude 分别保存地址、密钥、模型和模型缓存。切换服务不会把另一服务的密钥带过去；模型列表刷新不会自动选择第一项。更换地址或密钥会清除旧列表，过期网络回调不会覆盖新配置。已有 DeepSeek 用户无需迁移数据。

## 功能范围

- 支持流式中文回复、思考展示、工具调用与结果回传、图片、标题生成，以及 Harness 的上下文整理、取消和重试。
- 通过 Harness 的 replay envelope 持久化签名思考与加密思考块，在同一路由中续传。跨地址、跨模型不会复用签名。
- 手动思考模型按低/高/最高设置 1024/4096/8192 token 预算；支持自适应思考的新模型使用 adaptive 和 effort。最高档在支持的 Opus 上为 max，其余自适应模型保守使用 high。未知中转别名不猜测思考参数；模型本身是否支持关闭思考由服务决定。
- Claude 模型使用原生 base64 图片块；图片自动切换只查找当前服务列表里的视觉模型，绝不转到 DeepSeek。非视觉模型无可用替代时仅收到文件说明。
- 联网搜索/网页读取依然是 **DeepSeek 官方工具接口**。需要在 DeepSeek 服务里保存官方地址和官方密钥；只有 Claude 密钥或 DeepSeek 中转密钥时，不提供这些工具。尚未接入 Claude 服务端搜索工具。
- 暂不支持 Bedrock/Vertex 专用签名协议、Claude 服务端工具内容块、服务端 fallback 内容块。中转站必须提供兼容的 Messages SSE 协议。

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

3 项 Android 设置测试在隔离原生核心后通过，包含触摸事件驱动的 DeepSeek/Claude 重复切换、手填模型、非法地址回退及设置重开。Android 设置测试可通过 `-e skipNativeCore true` 单独验证 Java 设置层和真实触摸操作；这不代表 Android 原生核心端到端验证通过。ARM64 真机启动、实际中转站对话及后台任务/通知生命周期仍待验证。没有将真实账户、密钥或聊天数据写入测试包。

## 离线开发分支

本次交付包含 `claude-relay-support.bundle`，保存完整 Git 历史及 `feat/claude-relay-support` 分支；另附单提交补丁。当前连接的远程写入被审批策略拒绝，分支尚未推送 GitHub。可在有写权限的电脑导入后推送：

```sh
git clone -b feat/claude-relay-support claude-relay-support.bundle dsh-claude
cd dsh-claude
git remote set-url origin git@github.com:did2818233534/deepseek-harness-for-android.git
git push -u origin feat/claude-relay-support
```
