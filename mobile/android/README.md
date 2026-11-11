# LexAid Android

Android 9 及以上版本可安装。客户端通过系统 WebView 加载线上 LexAid，在同一站点注册或登录即可使用桌面端已有的账号和资料。首次打开及访问资料需要联网。

原生层处理系统返回、键盘和刘海屏边距、连接失败重试、图片/文档文件选择，以及私信语音录制所需的麦克风授权。外部论文和法条来源通过浏览器打开。应用声明网络和麦克风权限，图片与文档由系统文件选择器授权读取。

## 构建与签名

在此目录使用 Android SDK Platform 36、Build Tools 36.0.0 和兼容 Gradle 9.3.1 的 JDK（本项目使用 JDK 25）。将 `ANDROID_HOME` 设为 Android SDK 的安装目录。

```sh
./gradlew :app:assembleRelease
./sign-release.sh
```

安装文件与校验和位于项目根目录的 `release/LexAid-1.0.0-Android.apk` 和同名 `.sha256` 文件。通过 USB 安装时可运行 `adb install -r ../../release/LexAid-1.0.0-Android.apk`。

首次运行签名脚本会在 `~/.config/lexaid/android-signing/` 创建专用发行密钥及口令文件。请同时备份这两个文件，后续版本沿用原签名并递增 `app/build.gradle` 中的 `versionCode`。可通过 `LEXAID_ANDROID_SIGNING_DIR` 指向恢复的签名目录。
