# DSH Pocket — 安卓原生 Harness 适配

为轻量文件处理和联网研究制作的个人测试版，非 DeepSeek 官方客户端。安卓原生 Java 界面、Storage Access Framework 文件导入导出、Android Keystore 密钥加密、原生网络和文档处理。应用不包含 Ubuntu、PRoot、容器或操作系统虚拟机。

## 安装和使用

从 [GitHub Releases](https://github.com/did2818233534/deepseek-harness-for-android/releases/latest) 下载 ARM64 安装包（普通安卓手机），x86_64 包用于对应设备，universal 包同时包含两种架构。在设置中填写 DeepSeek API Key；默认地址 `https://api.deepseek.com`，默认模型 `deepseek-v4-pro`；仅连接 DeepSeek 官方 API，通过官方接口获取模型列表并下拉选择。API 使用费用由所配置服务收取，搜索 API 也可能计费。安装包不包含任何真实密钥。

点击“导入”选择文件，它会复制到应用工作区，不会修改手机上的原件。输入任务，例如“读取这个 CSV，按金额汇总，生成一个新 CSV”，或“总结这份 DOCX，并生成 PDF”。在“文件”面板选择结果，点击“导出到手机”。原件重名时自动增加序号。新建对话和历史对话保存在应用私有目录。

任务执行期间使用安卓前台服务和通知，可停止任务；进程被系统终止后，重新打开应用可恢复历史，未完成任务不会自动重做有副作用的操作。厂商省电策略仍可能终止进程；本版本不承诺锁屏后无限时运行。

## 实际复用范围

`vendor/harness/UPSTREAM.json` 固定上游提交及固定的依赖包。直接复用 Cordis、Agent 循环、工具校验及调度、会话事件模型、模型适配接口、系统提示词组装和会话投影；不是重新实现一套聊天循环。核心源码保持原样，构建时将 Unicode 属性正则展开为等价字符范围。

安卓适配新增：Chat Completions 流式协议转换、本地持久化后端、安卓工具桥接、前台服务和界面。配置了适用于手机文件任务的提示词；未照搬桌面端所有插件和提示词，因此不声称与桌面完整产品行为完全一致。

手机中通过 JNI 嵌入 **nodejs-mobile 18.20.4 原生库** 执行 JavaScript 核心，不模拟 x86 CPU 或 Linux 系统。它是兼容性验证版所用运行时；上游 Harness 要求较新的 Node，本项目只验证当前固定核心和提供的工具。此运行时较旧；后续仍需升级或自行构建受维护版本并重新验证。当前预编译 `libnode.so` 采用 **4 KB ELF 对齐，尚不支持原生 16 KB 内存页设备**；不能把 APK 标为已全面兼容所有新安卓手机。

## 功能与限制

- 读取 UTF-8 文本、CSV、TSV、JSON、Markdown、HTML、DOCX 正文、文字型 PDF。单个导入文件限 16 MB，PDF 限 150 页；文字按页段返回模型。
- 创建文本类文件、简单 DOCX 和 PDF。DOCX 输出为段落文本，不保留输入文档复杂排版；PDF 使用安卓字体和原生分页绘制。
- DeepSeek 官方搜索 API，以及 DeepSeek 官方 Fetch API 网页读取。模型可多轮搜索、读取来源、继续验证；页面内容由 DeepSeek Fetch API 返回，不保证能读取登录页面或动态页面。
- 流式回答、工具调用展示、取消、会话恢复、可配置模型和每轮步骤上限。
- 未实现：扫描件 OCR、XLS/XLSX 编辑、复杂 Office 排版、Python/Shell、桌面浏览器自动化、桌面插件市场、任意 MCP 插件、自动上下文压缩。超长会话请新建对话。
- 云端模型处理上传到 API 的消息及工具提取内容；文件解析和生成在手机执行。文本/CSV/JSON 结果不需要云端文件下载接口。
- 本版为测试构建。尚未使用真实 API Key 验证付费推理和搜索服务，也未做 ARM 真机耗电、速度或长期后台测试，不能保证“零性能损耗”。

## 工程结构

- `runtime/`：上游插件组合、安卓模型适配器、持久化适配器。
- `vendor/harness/`：固定的上游源码子集与许可证。
- `app/src/main/java/dev/guga/dsh/`：原生界面、服务、网络桥、文件工具和密钥存储。
- `app/src/main/cpp/runtime.cpp`：JNI 启动入口。
- `tests/runtime.mjs`：无 API Key 的核心集成测试。
- `app/src/androidTest/`：安卓文件工具及真实 JNI/Core/Native 闭环测试；模型响应由测试拦截器提供，不访问外网模型。
- `dist/`：可安装包、校验值和测试结果。

## 本机构建

依赖位于 `/opt/dsh-android`，Android SDK 位于 `/opt/android-sdk`，Gradle 缓存位于 `/opt/gradle-cache`。项目本身按工作区习惯位于项目目录。无须启动 Android Studio。

```bash
# 构建依赖的锁文件在 scripts/package-lock.json。
# 首次构建准备依赖（需要 Node.js / npm 和 JDK 21）：
mkdir -p /opt/dsh-android/build-tools
cp scripts/package*.json /opt/dsh-android/build-tools/
npm ci --prefix /opt/dsh-android/build-tools
# 安装 Android SDK 35 / build-tools 和 NDK 29.0.14206865，配置 local.properties 的 sdk.dir。
# 按 scripts/toolchain-lock.json 的 URL 和 SHA256 下载、验证并解压 nodejs-mobile。
node scripts/bundle.mjs
scripts/build-native.sh
GRADLE_USER_HOME=/opt/gradle-cache ./gradlew --no-daemon :app:assembleDebug
node tests/runtime.mjs
```

原生库下载自 nodejs-mobile 官方 GitHub v18.20.4，解压到 `/opt/dsh-android/nodejs-mobile`；NDK 为 `/opt/android-sdk/ndk/29.0.14206865`。`NODE_MOBILE_DIR` 和 `NDK_DIR` 可覆盖默认路径。此项目的依赖安装不会向家目录新增软件目录。

测试在独立的无窗口 Android 15 x86_64 测试器中进行；它仅用于开发验证，不随应用分发，也不是应用运行所需依赖。USB 真机 23117RK66C（安卓 15、ARM64、4 KB 内存页）已通过 2 项仪器测试（2.903 秒），覆盖文件操作与真实 JNI/Harness 核心/安卓工具闭环。该手机限制测试进程从后台启动 Activity，测试时由 ADB 显式打开应用后通过。测试使用模拟模型响应，不代表真实 API 或长期后台测试通过。

## 0.3.0 更新

- 精简聊天界面，侧栏管理对话和设置，消息气泡按内容伸缩。空白会话不显示宣传文案、图标或大标题。布局使用 dp/sp、可用宽度和系统窗口 Insets，不绑定某一手机尺寸。
- 页面打开时自动启动服务；空闲进入后台后可释放安卓服务，返回时接回同一进程的核心。任务期间保留前台服务。内部核心状态不显示在聊天页。
- 直接使用固定上游的 `web`、`tool-web`、DeepSeek Search/Fetch 提供器与 timeout-policy 源码。`web_search` 支持最多 4 个关键词、去重并发检索、最多 8 个来源；Search API 使用 `need_content=false`，发送规范化的原问题；`web_fetch` 读取官方返回的 Markdown。模型自主选择是否调用。搜索和读取的启用配置与上游一致，默认均为开启，可在设置中分别关闭。
- 安卓仅替换网络和密钥传输：官方 HTTPS、Keystore API Key、30 秒请求期限、8 MB 响应上限。不包含桌面账号登录或其他搜索提供器。不能声称所有 Harness 插件均已移植。
- 所有联网测试使用本地拦截响应，没有调用付费 API。新界面和联网实现仅在无窗口模拟器验证，0.3.0 未安装到用户真机。

## 对话管理
侧栏每条对话右侧有归档和删除按钮。归档立即移入“已归档对话”，可在其中恢复；删除需确认，永久移除聊天记录，保留文件库。正在处理任务时需先停止任务。


## 界面与导航（0.4.4）

- 聊天标题/输入框模型按钮：仅提供模型选择与联网搜索开关。
- 侧栏右下齿轮进入完整设置；模型与服务、已归档对话、文件库均为独立页面。
- 系统返回手势与左上返回按页面层级返回；仅在聊天首页返回时切到后台。
- 按钮使用统一紫色/浅紫圆角样式，删除图标为红色；删除对话需要确认。
- 模型列表通过官方 `GET /models` 获取，使用当前设备保存的 Key；失败保留当前选择并允许重试。
- 桌面图标采用上游 Harness 的鲸鱼轮廓，转换为 Android 自适应矢量图标。本项目仍为个人适配版，并非官方安卓客户端。

## 仓库管理

本地 Git 仓库不包含签名密钥、API Key、本机 SDK 配置、Gradle 缓存、APK 或下载的原生运行库。
首次构建需要按下方/上文构建说明准备 SDK、NDK、Node 依赖和 nodejs-mobile 库；应用测试不得读取或提交真实账号密钥。
测试后关闭自行启动的无窗口模拟器和测试进程。发布安装包保留在被忽略的 `dist/` 目录。

### 0.4.5 界面修复
去掉对话列表背景，用文字颜色区分当前对话，并增加行间距；开关与输入控件增加外边距。侧栏和删除确认弹窗分别处理返回，取消删除保留列表，归档/删除成功直接更新当前列表，不再先关闭再重开。

修复模型列表成功连接后返回闪退：取消请求及关闭连接池在后台线程执行，避免 Android 主线程网络操作异常。

### 0.4.6
对话列表不显示背景。仅执行任务时展示前台服务通知；通知小图标使用 DSH 鲸鱼轮廓，打开应用初始化核心时不再显示通知。

设置页参考 RikkaHub 的大标题、圆形返回按钮和带图标/说明的分组列表布局，保留 Pocket 的原有浅紫配色与现有功能范围。

### 0.4.7
全局控件遵循 UI_DESIGN.md；后续开发遵守 AGENTS.md。聊天快捷设置为底部弹层，模型/搜索即时保存。模型下拉显示名称及 ID，框内右侧图标刷新，完整返回接口模型列表并缓存。DeepSeek V4.1 Flash 的 API ID 为 deepseek-flash，旧 V4 Flash 名称为兼容别名。

### 0.4.8
API Key 输入框以等长密码圆点显示已保存密钥，移除已保存提示；点入可全选替换。

### 0.4.9
模型框改用矢量下拉箭头；API Key 默认隐藏，右侧眼睛可切换显示/隐藏。


## 0.5.0：应用更新与对话细节

- 自动更新默认开启。按设备本地日期，每天首次进入应用时检查一次；当天失败也不自动重试，不在后台定时轮询。设置中的“检查更新”可以随时手动触发。
- 参考 CC Switch 使用 Release 内 `latest.json` 的方式，通过 HTTPS 读取本仓库最新正式版清单；根据 versionCode 和设备 ABI 选择 APK。GitHub 无法访问时静默结束，不影响聊天。
- 下载完成后校验大小、SHA-256、应用包名、版本和安装签名。只有同一签名的更高版本可以更新；签名密钥不随源码发布。
- 使用 Android PackageInstaller 安装。普通手机可能需要允许“安装未知应用”并进行系统确认；这不是绕过系统权限的静默安装。下载后可在设置中点击“安装已下载的更新”。关闭自动更新不影响手动检查。
- 未发送消息的空对话不出现在历史列表；发送第一条消息后显示。确认弹窗的操作为无边框文字，删除为红色。

### 发布

签名配置放在 `/opt/dsh-android/signing/signing.properties`，包含 `storeFile`、`storePassword`、`keyAlias`、`keyPassword`；请使用自己的密钥，勿提交到仓库。自行签名的构建不能覆盖官方仓库里不同签名的安装包。

增加 `app/build.gradle` 中的 versionCode/versionName，构建 `:app:assembleRelease`，运行 `python3 scripts/package-release.py`。将生成的 APK、`latest.json`、`SHA256SUMS` 一起上传到对应 `v版本号` 的正式 GitHub Release。发布前确保 APK 使用相同发布签名；不要将测试 APK 放到正式更新清单。

### 0.5.1
设置首页只保留“应用更新”入口；当前版本、自动更新开关、手动检查和已下载更新安装统一放在独立页面，返回回到设置。

### 0.5.2
修复手动检查更新没有反馈：在更新页面显示连接、下载百分比、校验、最新版本或具体失败提示，并允许重试；已有后台检查时显示其进度。手动下载后由用户点击安装。自动检查失败仍静默，不弹警告。

### 0.5.3
手动点击安装后直接进入系统安装确认；权限不足、安装包无效或系统安装失败在当前界面显示弹窗，可取消或重试。自动检查仍不主动弹窗。包含 0.5.2 的手动检查反馈修复。
