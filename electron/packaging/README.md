# LexAid macOS 发布

从项目根目录执行以下命令。将 `LEXAID_RELEASE_API_URL` 设为实际部署的 HTTPS API 路径。

```sh
(cd frontend && npm ci && npm run build)
(cd electron && npm ci && LEXAID_RELEASE_API_URL=https://your-host.example/api npm run package:mac)
```

两个安装映像及各自的 SHA-256 清单位于项目根目录的 `release`。在 Apple 芯片 Mac 上使用 arm64，在 Intel Mac 上使用 x64。双击 DMG，将 LexAid 拖入“应用程序”。应用要求 macOS 13 或以上，并通过网络连接其 API 服务。

桌面壳随包提供界面、蓝色书页图标及公开的 API 地址。账号会话保存在本机应用专用的稳定来源中。构建流程使用本机 ad-hoc 签名；未使用 Developer ID 身份，也未提交 Apple 公证。若 macOS 阻止已核验来源的下载，可在尝试打开后，前往“系统设置 → 隐私与安全性”，为这个应用选择“仍要打开”。

在无法直接下载 Electron 的构建环境中，将与 `electron/package.json` 相同版本的 macOS 官方 ZIP 放入同一目录，并设置环境变量 `LEXAID_ELECTRON_ZIP_DIR` 指向该目录。构建脚本会按 npm Electron 包内的 SHA-256 校验归档；也可以在命令末尾传入 `-- arm64` 或 `-- x64` 单独构建其一。
