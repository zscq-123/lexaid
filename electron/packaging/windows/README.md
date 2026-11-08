# LexAid Windows 安装包

支持 64 位 Windows 10 和 Windows 11。运行 `LexAid-1.0.2-Windows-x64-Setup.exe`，安装完成后在开始菜单中打开 LexAid。安装位置为当前用户的 `%LOCALAPPDATA%\Programs\LexAid`。应用连接正式 HTTPS 服务，用户可在登录页注册账号。

升级时先退出 LexAid，再运行新版安装程序。可从 Windows 的「已安装的应用」卸载。卸载保留当前用户的本地登录状态和显示偏好。安装程序支持静默参数 `/S`。当前安装包没有 Authenticode 签名，Windows 可能显示未知发布者提示。

## 重新构建

构建机需要 Node.js 和 NSIS 3，`makensis` 需要在 PATH 中。以下命令在 macOS 的项目根目录运行。

```sh
(cd frontend && npm ci && npm run build)
(cd electron && npm ci && LEXAID_RELEASE_API_URL=https://154.36.185.110:18443/api npm run package:win)
```

安装包及同名的 SHA-256 文件写入项目的 `release/`。若要使用已下载的 Electron 官方 Windows x64 ZIP，设置 `LEXAID_ELECTRON_ZIP_DIR` 为该文件所在目录。打包脚本会对照锁定版本的官方散列值校验 ZIP。
