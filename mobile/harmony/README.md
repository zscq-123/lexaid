# LexAid 鸿蒙客户端

这是面向鸿蒙手机和平板的 ArkTS Stage 工程。入口 Ability 创建一个 ArkWeb 实例，加载 [LexAid 线上服务](https://154.36.185.110:18443/)。已有账号可在鸿蒙端登录并访问自己的收藏、帖子和学习记录。客户端使用系统图库处理头像和帖子配图，支持系统返回、键盘避让以及连接失败后重试；私信语音录制使用麦克风权限。访问站内资料需要联网。

## 构建调试包

用 DevEco Studio 打开本目录，选择 `default` 产品和 `entry` 模块。工程最低兼容 HarmonyOS 5.0.0（API 12）。安装工程配置对应的 HarmonyOS SDK，选择 Debug 模式后构建 HAP。使用官方 Command Line Tools 时，可在工程根目录运行：

```sh
hvigorw --mode module -p module=entry@default -p product=default -p buildMode=debug assembleHap
```

未签名产物位于 `entry/build/default/outputs/default/`。完整工程 ZIP 包含重新构建和配置签名需要的源码、资源及工程配置。

本次调试包用华为 Command Line Tools 5.0.5.310 内置的 HarmonyOS SDK（API 13）编译，兼容和目标 API 均为 12。已校验 HAP 的模块清单、Ark 字节码、页面索引和应用资源。构建时 HDC 未连接鸿蒙手机或模拟器，登录、图库选择和键盘在目标系统上的实际交互仍待设备验收。[官方工具下载及 SHA-256](https://repo.huaweicloud.com/harmonyos/ohpm/5.0.5/)

## 安装和验收

在兼容的鸿蒙模拟器中，可用 HDC 安装调试 HAP，再启动入口 Ability：

```sh
hdc list targets
hdc install -r LexAid-1.0.0-HarmonyOS-Debug-Unsigned.hap
hdc shell aa start -a EntryAbility -b com.lexaid.study.harmony
```

在真机上调试时，用自己的华为开发者账号在 DevEco Studio 的项目签名设置中配置调试证书和描述文件，将目标设备 UDID 加入调试 Profile，再构建并安装签名后的 HAP。版本为 5 或以上的普通用户也可以通过浏览器打开线上服务。[华为签名与分发说明](https://developer.huawei.com/consumer/cn/doc/app/agc-help-releasefaq-0000001110342644/)
