# 2048 (Android)

Kotlin + Jetpack Compose 写的 2048，带道具、每日签到和背景音乐。

## 功能

- 4×4 经典 2048，滑动合并，新棋子 80% 出 2 / 20% 出 4
- 方块滑动、合并弹出、生成缩放动画
- 最高分持久化；棋盘进度自动存档（杀进程回来接着玩）
- 撤销：每局 3 次，界面上显示剩余/总数
- 四种道具：锤子（敲掉一个方块）、翻倍（某个方块 ×2）、重排（所有方块重新撒到全盘）、净化（清掉 2 和 4）
- 合成 2048 掉 1~2 个道具，4096 掉 2~4 个，8192 掉 4~8 个，逐级翻倍
- 每日签到（右上角日历），随机掉道具
- 合出 2048 / 死局各有弹窗，可以继续挑战
- 设置：震动开关、音效音量、音乐音量
- 危险操作（用道具、撤销、重开）都要二次确认

## 构建

```bash
./gradlew assembleDebug     # 产出 2048-v<version>-debug.apk
./gradlew assembleRelease   # 需要 keystore.properties + keystore，见下
./gradlew test              # 单元测试
```

release 签名配置读取根目录 `keystore.properties`（该文件与 keystore 都不入库）：

```properties
storeFile=release.keystore
storePassword=******
keyAlias=game2048
keyPassword=******
```

## 版本

- 1.2.0 换正式背景音乐（Kevin MacLeod《Easy Lemon》, CC BY 4.0）、新棋子 4 概率提到 20%、自动存档、胜负弹窗
- 1.1.0 设置页（震动/音效/音乐）、背景音乐、应用图标
- 1.0.0 首个版本

## 音乐版权

`app/src/main/res/raw/bgm.mp3` 为 Kevin MacLeod 的《Easy Lemon》，来自 incompetech.com，
遵循 CC BY 4.0，署名保留在应用设置页。
