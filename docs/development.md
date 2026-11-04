# 构建、测试与打包

以下命令均从仓库根目录执行。准备 JDK 21、Node.js/npm、Android SDK 35 和 NDK 29.0.14206865。在 local.properties 中设置 sdk.dir。工具链下载地址和 SHA256 见[工具链锁文件](../scripts/toolchain-lock.json)，将校验后的 nodejs-mobile 18.20.4 解压到 /opt/dsh-android/nodejs-mobile。

## 构建

```sh
mkdir -p /opt/dsh-android/build-tools
cp scripts/package*.json /opt/dsh-android/build-tools/
npm ci --prefix /opt/dsh-android/build-tools
export ANDROID_HOME=/opt/android-sdk
export GRADLE_USER_HOME=/opt/gradle-cache
bash scripts/build.sh
```

构建入口会打包运行时、构建 JNI、准备两种架构的完整版环境并构建两个 Debug 版本。只构建轻量版可依次执行 `node scripts/bundle.mjs`、`bash scripts/build-native.sh` 和 `./gradlew --no-daemon :app:assembleLiteDebug`。APK 在 app/build/outputs/apk 下按版本及构建类型分目录输出。

## 验证

```sh
node tests/polyfills.mjs
node tests/file-cleanup.mjs
node tests/archives.mjs
node tests/runtime.mjs
```

核心集成测试使用本地模拟模型，覆盖中途引导、工具、附件、上下文压缩和恢复。可通过 TEST_NODE 指定 Node 18 可执行文件验证手机兼容路径。连接专用测试设备或启动无窗口模拟器后，用 `./gradlew :app:connectedLiteDebugAndroidTest` 运行安卓仪器测试。结束后关闭自行启动的模拟器和测试进程。

## 签名和发布物

签名配置位于 /opt/dsh-android/signing/signing.properties，字段为 storeFile、storePassword、keyAlias 和 keyPassword。密钥和本地 SDK 配置不入库。修改手机版显示版本时，持续递增 app/build.gradle 中的 versionCode；更新器按该整数判断升级。

准备好完整构建环境后运行：

```sh
./gradlew --no-daemon :app:assembleLiteRelease :app:assembleFullRelease
python3 scripts/package-release.py
```

dist/release-版本号/ 中包含两版 APK、SHA256SUMS 和按版本区分的更新清单。旧客户端使用 latest.json（轻量版），新版分别使用 latest-lite.json 和 latest-full.json。发布前验证签名、包名、版本、校验值和覆盖安装；完整版的第三方分发要求见[第三方声明](../THIRD_PARTY_NOTICES.md)。
