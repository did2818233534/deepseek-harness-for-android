# 架构与上游版本

Android 0.0.1 对应官方 [DeepSeek Harness 0.2.1-rc.5](https://github.com/deepseek-ai/deepseek-harness/releases/tag/dsh-v0.2.1-rc.5)，固定提交为 [b830ec950faba0a46dda564be3e2d854afed94f1](https://github.com/deepseek-ai/deepseek-harness/commit/b830ec950faba0a46dda564be3e2d854afed94f1)。纳入的包和构建适配点以[上游清单](../vendor/harness/UPSTREAM.json)为准。

## 一次请求如何运行

Android 前台服务通过 JNI 启动 nodejs-mobile 18.20.4，执行从 TypeScript 打包的核心。原生聊天界面把消息交给运行时。上游 Agent 循环负责模型步骤、工具调度、中途引导和上下文压缩。安卓适配层负责流式模型响应、会话落盘及文件权限，原生网络桥发送实际请求。联网检索和网页读取复用上游 DeepSeek 提供器，传输接入 Android HTTPS。模型在云端推理，文件工作区位于手机。

## 源码边界

| 目录 | 职责 |
| --- | --- |
| `vendor/harness/` | 官方源码子集和许可证。平台差异在外部适配，升级时核对提交和包清单 |
| `runtime/main.ts` | 运行时入口，装配插件、对话实例、工具和本地请求路由 |
| `runtime/providers/` | 模型协议适配、图片输入能力和联网工具的传输桥 |
| `runtime/sessions/` | 会话持久化、归档恢复、标题和供界面消费的消息表示 |
| `runtime/files/` | 附件准备、工作区文件身份与删除保护 |
| `runtime/compat/` | 内嵌 Node 18 所需的兼容实现 |
| `app/src/main/` | 两版共享的原生界面、前台服务、安全存储、Android 工具桥和 JNI 启动入口 |
| `app/src/lite/`、`app/src/full/` | 按版本提供文件处理能力；完整版执行器、种子环境接入集中在 full |
| `release-updater/` | 与聊天核心解耦的 Android 更新库 |
| `scripts/`、`scripts/termux/` | 核心打包、原生构建、发布物生成，以及完整版运行环境准备 |
| `tests/`、`app/src/androidTest/` | 宿主机核心回归及 Android 仪器测试 |
| `design/`、`docs/` | 设计素材及项目文档；应用使用的资源位于 app 源码集 |

`app/src/main/assets/runtime/main.cjs` 是随 APK 分发的生成文件。修改运行时后从仓库根目录执行 `node scripts/bundle.mjs` 重建。上游升级时同时核对内嵌 Node 的兼容性、运行回归并更新清单和版本文档。当前预编译 libnode 使用 4 KB ELF 对齐，原生 16 KB 页设备尚不受支持。

## Android 维护入口

以下路径相对于 `app/src/main/java/dev/guga/dsh/`。

| 要修改的行为 | 首先查看 |
| --- | --- |
| Activity 生命周期、聊天输入、页面返回 | `MainActivity.java` |
| 对话抽屉、归档与删除操作 | `ConversationDrawer.java`；归档列表由 `ArchivedChatsPage.java` 显示 |
| 设置首页、个性化、应用更新页面 | `SettingsPages.java`；模型表单位于 `ModelSettingsPanel.java` |
| 偏好、密钥与模型列表解析 | `settings/` |
| 文件选取、导入、命名、原子写入与删除 | `files/GalleryPicker.java` 和 `files/FileTools.java` |
| 文本提取、DOCX/PDF 格式处理 | `files/DocumentFormats.java`，工作区负责校验路径和发布结果 |
| 聊天渲染、消息选择与记录缓存 | `ChatTurnView.java`、`MessageActions.java`、`TranscriptViews.java` |
| 前台服务、安卓工具与模型传输桥 | `HarnessService.java` 和 `NativeBridge.java` |

页面共用 Activity 提供的导航与样式，文件和配置包可由服务、界面及不同版本的工具直接调用。新增 Android 功能优先置于对应平台边界；同步官方版本时，维持 `vendor/harness/` 内的上游目录和源码布局，安卓专属变更放在该目录之外。

### 共享执行环境清单

完整版的 `EnvironmentInfo` 从基础包清单和 `packages/installed.json` 生成共享版本摘要，按内容指纹缓存；不启动解释器，不初始化尚未使用的环境。模型请求适配层在每次普通请求（含工具后的续答）注入当前简短摘要，不写入会话历史；标题与压缩请求不注入。`environment_info` 按 50 项分页查询完整包名与版本。

安装和卸载成功后刷新缓存；清单被其他路径更改时，下一次读取同样自动失效。`uninstall_package` 只卸载后装包，拒绝基础包、被依赖包和无法确认安全性的旧依赖记录。新安装记录保存已解析的依赖。执行任务共享读锁，安装与卸载持有写锁，防止删除正在使用的文件，同时保留普通任务并发。轻量版不注册这些工具。

测试：`node tests/runtime.mjs` 覆盖跨会话刷新；`python3 tests/environment/run.py /path/to/org.json.jar` 使用隔离目录验证清单变化、卸载、依赖保护及重复操作，不接触用户数据。

环境操作的并发约束：执行任务持有共享读锁直到子进程确定退出；安装/卸载持有独占写锁，等待锁可响应线程中断。查询清单不等待长安装，读取的是原子替换前或后的完整已提交清单。卸载提交后清理文件失败返回 `removed: true, cleanupPending: true`，不误报软件包仍已安装；再次安装会重新解包验证，不复用未登记的残缺目录。缓存详细条目使用独立副本，分页偏移会收敛到列表范围。

额外回归：`python3 tests/environment/installer.py` 检查残留目录、提取失败和共享目录；Java 环境测试还覆盖并发读任务、卸载等待、进程退出、缓存对象隔离与大分页偏移。测试使用隔离目录和替代执行任务，不代表所有远端软件包均已适配。

### 会话运行资源生命周期

Android 运行时最多保留 8 个空闲会话 agent（含当前选中会话），按最近选择顺序淘汰；正在执行的会话不计入此上限，完成后再次收缩缓存。释放时取消标题任务，释放 agent 和会话专属模型适配器，并清理临时状态；历史仍由会话持久化存储保存，重选时重新注册。切换、发送、删除等控制请求串行提交，避免异步加载与淘汰交错；实际模型执行仍支持多个对话并发。

聊天内停止只取消当前会话；通知“停止所有对话任务”使用 `/cancel-all`，取消所有正在运行的会话。前台运行保护由核心汇报的全部会话/归档实际状态驱动，不因取消请求返回而无条件移除。
