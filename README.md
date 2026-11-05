# DSH Pocket

DSH Pocket 是 DeepSeek Harness 的非官方原生 Android 移植，适合在手机上联网检索、整理文档和处理图片。应用通过 JNI 在本机运行 Harness 核心，界面、文件选择、密钥存储和系统分享使用 Android API。

**手机版版本为 0.0.2，移植自官方 DeepSeek Harness 0.2.1-rc.5。** 两者独立编号。[上游版本与移植范围](docs/architecture.md)

## 安装与使用

从 [GitHub Releases](https://github.com/did2818233534/deepseek-harness-for-android/releases) 选择已发布的 APK。多数手机选 `arm64-v8a`，也提供 `x86_64` 和双架构的 `universal` 包。

| 版本 | 适合的任务 |
| --- | --- |
| 轻量版 | 聊天、联网搜索、网页读取，以及内置工具支持的文档读写 |
| 完整版 | 包含轻量版功能，另带 Python、Pillow 和按需安装工具的文件处理环境 |

两版可共存，聊天、API Key 和更新渠道各自独立。在「设置 → 模型与服务」填入自己的 DeepSeek API Key，选择模型即可开始。试着附上一份 CSV，让它按月份汇总，再点回复中的文件链接打开结果。导入文件会复制到应用工作区，原件保留在原处。

## 文档

- [使用指南](docs/usage.md) · [当前版本记录](CHANGELOG.md)
- [架构、目录与上游版本](docs/architecture.md) · [构建、测试与打包](docs/development.md)
- [界面规范](docs/ui-design.md) · [独立更新库的接入说明](release-updater/README.md)

本项目代码与上游同用 [MIT 许可证](LICENSE)。随包分发的第三方库、运行环境和工具包适用各自的许可证，详见[第三方声明](THIRD_PARTY_NOTICES.md)。
