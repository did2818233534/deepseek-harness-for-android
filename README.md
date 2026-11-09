# DSH for Android

DSH for Android 是 DeepSeek Harness 的非官方原生 Android 移植，适合在手机上联网检索、整理文档和处理图片。应用通过 JNI 在本机运行 Harness 核心，界面、文件选择、密钥存储和系统分享使用 Android API。

**手机版宿主版本为 v0.0.4，移植自官方 DeepSeek Harness 0.2.1-rc.5。** 两者独立编号。[上游版本与移植范围](docs/architecture.md)

## 安装与使用

从 [GitHub Releases](https://github.com/did2818233534/deepseek-harness-for-android/releases) 选择已发布的 APK。多数手机选 `arm64-v8a`，也提供 `x86_64` 和双架构的 `universal` 包。

| 版本 | 适合的任务 |
| --- | --- |
| 轻量版 | 聊天、联网搜索、网页读取，以及内置工具支持的文档读写 |
| 完整版 | 包含轻量版功能，另带 Python、Pillow 和按需安装工具的文件处理环境 |

两版可共存，聊天、API Key 和更新渠道各自独立。在「设置 → 模型与服务」填入自己的 DeepSeek API Key，选择模型即可开始。试着附上一份 CSV，让它按月份汇总，再点回复中的文件链接打开结果。导入文件会复制到应用工作区，原件保留在原处。

## Claude 与多供应商开发分支

`feat/claude-relay-support` 提供 **DSH Claude 测试版**，支持 DeepSeek、Claude 官方服务及兼容中转站。测试版包名为 `dev.guga.dsh.full.claudetest`，可与正式完整版共存；同签名测试包覆盖安装保留测试版聊天和设置。

### 配置模型

1. 打开「设置 → 模型与服务」，开启需要的供应商，再点击进入详情。默认仅启用 DeepSeek。
2. 在「模型」页填写自己的 API Key；使用中转站时，到「更多」页填写它的 API 基础地址。
3. 返回「模型」页刷新列表，开启想在聊天中使用的模型。新获取或手动添加的模型默认关闭，已有选择保留。
4. 打开聊天设置选择模型；DeepSeek 与 Claude 的已启用模型可以同时出现在列表中。没有可选模型时，可点击「去设置」完成配置。

「更多」页可修改模型、消息、搜索、网页读取及用量接口，并恢复当前供应商的默认配置。Claude 消息接口默认为 `/v1/messages`，请求使用所配置的基地址。不要把 API Key 写入代码或提交到仓库。

### 分支功能

- 供应商详情采用「模型 / 更多」双页签，复用应用现有主题和控件。
- 模型列表每天进入应用时自动尝试更新一次，也可随时手动刷新；Claude 模型按版本排序，已开启项置顶。
- 思考强度在聊天设置中按当前模型显示，并分别保存；时间限制、联网工具和步骤上限集中在通用设置。
- 云端搜索和网页读取优先使用配置的服务；不支持或请求失败时回退到本地工具。
- 对话引导沿用当前队列机制，预留原生能力扩展；未把 Managed Agents 会话接口混入 Messages 聊天。

### 构建与验证

```sh
./gradlew :app:assembleFullClaudeTest
```

APK 位于 `app/build/outputs/apk/full/claudeTest/`。完整依赖和运行时准备见[构建指南](docs/development.md)，接口配置与兼容性见[Claude 配置说明](docs/claude.md)。本分支测试包不通过正式版更新渠道升级。

设置层和模拟服务回归已通过；软件模拟器内嵌原生运行时仍有启动问题，测试结果不等于所有真机和中转站均已完成端到端验证。具体范围及限制见配置说明。

## 宿主与动态模块更新

首次安装本版完整 APK 后，可在「设置 → 应用更新 → 动态模块更新」导入 `.dshota`，校验通过后退出并重新打开应用即可启用，无需系统安装。界面分别显示宿主与业务模块版本；聊天和设置保留，也可恢复宿主内置模块。

业务代码和运行脚本支持动态更新；系统权限、Android 编译资源、第三方依赖和原生运行环境变更仍需完整 APK 更新。后续发布同时提供完整 APK 与对应补丁：兼容更新为 `.dshota`，宿主升级为需系统安装的 `.dshpatch`。首次启用必须安装一次完整 APK。

详见[动态模块架构与使用说明](docs/dynamic-modules.md)及[宿主补丁说明](docs/patch-updates.md)。

## 文档

- [使用指南](docs/usage.md) · [当前版本记录](CHANGELOG.md)
- [架构、目录与上游版本](docs/architecture.md) · [构建、测试与打包](docs/development.md)
- [界面规范](docs/ui-design.md) · [帮助按钮对齐与例外白名单](docs/help-alignment.md) · [独立更新库的接入说明](release-updater/README.md)

本项目代码与上游同用 [MIT 许可证](LICENSE)。随包分发的第三方库、运行环境和工具包适用各自的许可证，详见[第三方声明](THIRD_PARTY_NOTICES.md)。
