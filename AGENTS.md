# 项目约定

安卓 2048（Kotlin + Jetpack Compose），仓库 https://github.com/C00LBL00D/Game2048 。

## 提交

- **每次改完就 commit 并 push 到 `origin main`**，不要攒着。
- 提交信息用中文，格式 `type: 简短说明`（feat / fix / style / build）。
- push 前跑一遍 `./gradlew test`。

## 构建

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew assembleDebug          # 2048-v<version>-debug.apk
./gradlew test                   # 单元测试
./gradlew assembleRelease        # 2048-v<version>.apk（签名配置见下）
```

## 不要入库

`keystore.properties`、`*.keystore`、`local.properties`、`build/`、`*.apk` 都已在 `.gitignore` 里排除。
release 签名信息只在本地 `keystore.properties`，不提交。

## 改动 UI 后的验证

装到模拟器截图确认（`adb install` + `uiautomator dump` 取文本、`screencap` 看像素），
不要只凭代码判断样式。
