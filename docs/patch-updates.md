# 本地补丁更新

## 使用

首次需要完整安装带补丁功能的 v0.0.3（versionCode 3）或更高版本。之前的 APK 没有导入入口，不能直接安装补丁。

以后每次提供完整 `.apk` 和从上一发布版本生成的 `.dshpatch`，可任选：

1. 完整 APK：直接打开并覆盖安装。
2. 补丁：下载后打开「设置 → 应用更新 → 选择补丁文件」，选择 `.dshpatch`。合成完成后点击「安装已准备更新」，按系统提示授予安装权限并确认。

补丁只适用于文件名注明的起始版本、相同包名和架构。跨版本、架构不同、旧 APK 被重新签名时，请下载完整 APK。失败不会覆盖已安装应用或删除聊天和设置。合成需额外腾出一个完整 APK 的空间，系统安装还可能需要额外空间。补丁减少下载量，不减少最终安装体积。取消文件选择或安装均不会删除用户数据；已准备好的更新可以重试安装。

## 生成与保留基准

versionCode 必须递增。默认构建编号在 `app/build.gradle` 中设置，也可用 `-PappVersionCode=4 -PappVersionName=v0.0.4` 构建下一版。

首次打包：

```sh
./gradlew :app:assembleFullClaudeTest
python3 scripts/package-patch-update.py --bootstrap
```

后续每次构建后：

```sh
python3 scripts/package-patch-update.py
```

产物放在 `dist/patch-update-版本号-arm64-v8a/`，包括完整 APK、补丁、SHA256SUMS 和说明元数据。首次 bootstrap 只有完整包。指定 `--abi x86_64` 可为模拟器单独建立更新链。

精确基准存放在 `.local/patch-bases/包名-架构/`，不加入 Git，不随历史下载包清理；不能重新编译旧提交来替代已发布基准。后续发布必须同签名且版本号递增。可以清理 `dist` 的旧 APK，但不能删除仍用于生成补丁的基准包。生成器只写本项目目录，不改全局环境。仅创建本地产物，不自动发布到 GitHub。

## 格式和验证

`DSHPAT01` 固定头包含旧 APK SHA-256、新 APK SHA-256 和目标大小。gzip 操作流以复制旧文件字节段、写入新增字节两种指令组成，结束指令后不接受额外解压内容。生成器复用未变化的 ZIP 压缩载荷，签名块与目录按目标原样保存；不会在手机上重新压缩或签名。

解码器流式处理，限制目标大小（512 MiB）、操作数量、偏移和长度；失败清理暂存文件。先核验当前已安装 APK 哈希，再核验合成文件哈希、应用包名、签名及更高版本号，最后复用系统 PackageInstaller 安装和权限流程。所有文件操作均在工作线程执行。分包安装暂不支持，使用完整 APK。

验证命令：`python3 tests/apk-patch.py`。该测试跨 Python 生成器和实际 Java 解码器验证完整还原、ZIP 偏移变化、签名尾部保留、错误基准、截断、哈希错误、非法长度/偏移/操作和失败后重试。Android 仪器测试另检查合成后的安装准备和界面入口。

安装 API 依据：[PackageManager](https://developer.android.com/reference/android/content/pm/PackageManager)、[PackageInstaller](https://developer.android.com/reference/android/content/pm/PackageInstaller)。
