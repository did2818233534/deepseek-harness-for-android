# 宿主与动态业务模块

本分支采用固定宿主与可替换业务模块。首次需要安装带模块加载器的完整 APK；之后兼容的业务更新可在应用内导入，下次启动生效，不调用 Android 安装器。系统设置中的 APK 版本仍是宿主版本，应用更新页另行显示正在运行的业务模块版本。

## 用户操作

1. 首次覆盖安装完整测试 APK，保留聊天和设置；包名仍为 `dev.guga.dsh.full.claudetest`，与正式完整版共存。
2. 在「设置 → 应用更新 → 动态模块更新」点击「导入模块更新」，选择 `.dshota` 增量包。完整的、同签名兼容 APK 也可作为模块导入，扩展名可为 `.dshmodule`。
3. 校验完成后可继续使用当前版本；等聊天任务完成，点击「退出并应用更新」，再次打开时启用新模块。一个进程内不会混用两个业务版本。
4. 如需撤回，选择「恢复宿主内置模块」，下次启动恢复。此操作保留聊天和设置，也取消待启用模块。

首次发布没有旧宿主可用的模块补丁，必须完整安装一次。增量包必须匹配当前**实际运行的模块**，不能拿宿主版本号猜测。跨多个版本可导入兼容完整模块，或依次应用对应增量包；每一步生效后才能应用下一步。

## 更新边界

| 内容 | 动态业务更新 | 需更新宿主 |
| --- | --- | --- |
| `dev.guga.dsh.*` Java 业务代码、程序生成的界面 | 是 | 否 |
| 模型协议、设置行为、聊天和工具调度 | 是 | 否 |
| `runtime/main.cjs`、`runtime/bootstrap.cjs`、`reasoning-models.json` | 是 | 否 |
| Android `res/`、资源 ID、图标、主题 XML | 否 | 是 |
| Manifest 系统组件声明、权限、SDK 配置 | 否 | 是 |
| Node/JNI/Python/Termux 原生运行环境、第三方 Java 依赖 | 否 | 是 |
| 宿主加载器、签名验证、传统 APK 更新器 | 否 | 是 |

业务包是包含完整 DEX 的签名 APK；宿主只加载其中的业务命名空间和明确列出的业务资产，其余部分仍使用宿主。使用已有资源可以调整程序生成的界面；新增或改变 Android 编译资源必须升级宿主。新增业务类可动态加载，新增系统注册组件则必须随宿主安装。

## 加载与回退

- `module-host` 提供固定 `HostApplication`、`ModuleFactory`、`ModuleRuntime` 与 `BusinessLoader`。使用 Android 公开的 `AppComponentFactory`，不反射修改系统 ClassLoader，不使用隐藏 API。
- `BusinessLoader` 仅对业务命名空间使用模块优先；宿主、Android、`R` 和第三方依赖来自固定父加载器。模块缺失业务类时失败，避免从旧版本补齐造成混用。
- API 28 起的 Activity、Service、Receiver、Provider 均由同一个进程级模块加载器创建。原生库搜索路径仍指向已安装宿主。
- 签名 APK 使用 `apksig` 完整验证，签名证书须与宿主当前签名一致，包名也必须一致。比较宿主契约哈希及所有不可变 ZIP 内容的 SHA-256，拒绝跨宿主、资源或原生库变更。
- 增量解码检查基准和目标 SHA-256、复制范围、目标长度与空间，保留完整签名 APK 的逐字节内容。完整模块限制为 512 MiB；没有网络下载或后台自动执行新模块。
- 模块保存在应用私有目录，验证后设为只读，再原子改名。状态使用 `AtomicFile` 持久化；导入失败不替换运行版本或已准备的有效模块。
- 新模块首次启动前记录未完成标记；主页面持续恢复 30 秒后由宿主确认；已确认模块不会因为后续短暂打开就被回退。用户主动准备另一更新或恢复内置时优先尊重该操作。试运行中被杀进程、断电或崩溃，下次启动保守回退到宿主内置版本。Java 未捕获异常也安排回退。健康确认后的 native crash 不保证自动识别；可使用恢复内置入口或安装完整 APK。
- 只保留当前和待启用的模块文件。增量下载很小，但本地仍需合成完整模块，必须留有完整 APK 大小的空间；运行中导入还需要保留当前模块。
- 本实现不迁移或删除用户数据。后续业务修改必须保持设置及聊天格式向后兼容，否则回退旧代码可能无法读取新格式，需要单独设计数据迁移。

## 构建与发布

默认宿主为版本 4、内置模块 4。今后发布通过 Gradle 参数递增版本，避免仅为版本号修改宿主构建脚本：

```sh
./gradlew :app:assembleFullClaudeTest -PappVersionCode=5 -PappVersionName=v0.0.5 -PmoduleVersion=5
python scripts/package-module-update.py
```

首次宿主发布：

```sh
python scripts/package-module-update.py --bootstrap
```

输出 `dist/module-update-版本-arm64-v8a/`，包含完整 APK、发布清单及哈希。与上一精确基准兼容时同时生成 `.dshota`；不兼容时仅提供需系统安装的 `.dshpatch` 与完整 APK，清单明确 `requiresHostInstall`。宿主补丁也以当前正在运行的模块 APK 为合成基准，因此动态更新用户与选择完整安装的用户均可使用同一上一发布版本补丁；最终仍交给系统安装。不得把不兼容更新标记成免安装更新。`.local/module-bases` 保存精确已发布基准，不能随历史下载包清理或由重编译旧提交替代。

`moduleVersion` 必须递增；完整 APK 的 `versionCode` 也应递增，以便选择完整安装的用户升级。恢复内置版本不降低已接受模块的最高版本记录，防止重复安装旧模块；修复发布使用更高版本。

宿主契约覆盖加载器、传统更新器、Manifest 与构建依赖声明。业务资源和原生库另外做内容级比较，因此即使契约相同，也不能绕过资源兼容检查。业务新增资源导致的编译资源 ID 改变会明确要求完整安装。

## 验证与依据

`ModuleRuntimeTests` 使用真实签名的版本 4/5，分进程验证准备、切换、实际业务 ClassLoader、系统版本不变、设置保留及回退。错误签名、错误基准和不同宿主契约应在执行代码前拒绝。界面测试需要覆盖选择器取消、损坏文件、重复操作、帮助与返回；截图取实际应用 Window。

官方依据：[AppComponentFactory](https://developer.android.com/reference/android/app/AppComponentFactory)、[Android 14 动态加载只读要求](https://developer.android.com/about/versions/14/behavior-changes-14#safer-dynamic-code-loading)。若以后上架商店，需另行审查该渠道对动态代码分发的规则。

### 分阶段模拟器测试

此测试有跨进程状态，不能把整个 `ModuleRuntimeTests` 当作普通单进程测试套件运行。只在无真实凭据的测试模拟器使用，明确传入 `-e moduleScenario true`；未传入时自动跳过，避免普通回归改变动态模块状态。

先分别构建并归档版本 4/模块 4 与版本 5/模块 5 的 x86_64 签名 APK 为 `.local/module-validation/host4-x86.apk`、`module5-x86.apk`。运行 `python tests/module-fixtures.py` 生成真实签名与负例补丁，然后构建测试 APK、安装**归档的精确版本 4**。重编译版本 4 即使内容相同，也可能因 ZIP 元数据导致整包 SHA 不同，不能替代增量基准。

每一步单独执行：

```sh
adb shell am instrument -w -r -e moduleScenario true \
  -e class dev.guga.ota.ModuleRuntimeTests#prepareSignedModuleAndRejectDamage \
  dev.guga.dsh.full.claudetest.test/androidx.test.runner.AndroidJUnitRunner
```

依次将方法名换为 `dynamicBusinessReallyLoadsAndRenders`、`confirmedModuleSurvivesQuickRestart`、`queueRestoreWithoutClearingData`、`builtinRestored`。再次覆盖安装归档宿主以重置测试模块选择，然后依次运行 `stageOnly`、`simulateUnfinishedStartup`、`startupFailureRestored`，验证首次启动中断后的回退。设置测试键保留用于确认没有清除数据，不使用真实 API 或聊天记录。

本次验证环境为 Android 11 / API 30 的 x86_64 软件模拟器。上述八个模块阶段、八项界面/供应商回归、增量解码与打包流程测试通过。ARM64 完整包已构建并校验签名；尚未在真机或 Android 14+ 实际运行，也未完成真实模型与原生 Node/Python 任务端到端验证。界面测试隔离了软件模拟器已有的原生核心启动问题。
