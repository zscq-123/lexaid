# LexAid for iPhone and iPad

LexAid 支持 iOS 和 iPadOS 16 及以上版本。SwiftUI 应用通过 WKWebView 打开线上学习站点，使用同一套账号、内容与登录状态。内置浏览器保留会话，支持系统左右滑动前进和后退、键盘避让、系统图片与文件选取。外部 HTTPS 资料在 Safari 阅读窗口中打开。打开页面需要联网。

## 在模拟器安装

解压 `LexAid-1.0.0-iOS-Simulator.zip`。在已安装 iOS 模拟器运行时的 Mac 上启动一台 iPhone 或 iPad 模拟器，再运行下列命令。

```sh
xcrun simctl install booted /path/to/LexAid.app
xcrun simctl launch booted com.lexaid.study.mobile
```

从源码运行时，在 Xcode 中打开本目录的 `LexAid.xcodeproj`，选择 LexAid scheme 和目标模拟器，再按 ⌘R。工程文件已随源码附带。如果调整 `project.yml`，可在本目录运行 `xcodegen generate` 同步工程。

## iPhone 真机

本次提供 arm64 真机 Release 归档 `LexAid-1.0.0-iOS-Device-Unsigned.xcarchive.zip`。发行物缺少 Apple 开发者签名和描述文件。要安装到自己的手机，请在 Xcode 的 LexAid target「Signing & Capabilities」中选择自己的 Team，按需修改 Bundle Identifier，再选择连接的 iPhone 运行。正式分发使用该团队的签名重新 Archive 并按目标分发渠道导出。

## 重建 Release

```sh
xcodebuild -project LexAid.xcodeproj -scheme LexAid -configuration Release \
  -destination 'generic/platform=iOS Simulator' -derivedDataPath build build
xcodebuild -project LexAid.xcodeproj -scheme LexAid -configuration Release \
  -destination 'generic/platform=iOS' -archivePath build/LexAid.xcarchive \
  CODE_SIGNING_ALLOWED=NO archive
```

`LexAidUITests` 包含线上首页加载检查。使用自行创建的验收账号，并在启动测试进程前通过 `TEST_RUNNER_LEXAID_QA_USERNAME` 和 `TEST_RUNNER_LEXAID_QA_PASSWORD` 注入时，还会验证登录、法条导航和进程重启后的会话。临时开发签名和 App Store 分发凭据由签名方管理。
