# LexAid 服务器部署

服务器工作目录为 `/opt/lexaid-study`，Compose 项目名为 `lexaid-study`。网页与桌面客户端均使用 `https://154.36.185.110:18443`。MySQL 和 Java 后端通过 Compose 内部网络通信。

## 首次部署

1. 把本目录的 Compose 文件、Nginx 配置、脚本和证书定时单元放入服务器工作目录。在本地仓库运行 `python3 deploy/production/prepare-seed.py <输出目录>`，将其内容传到服务器的 `init/`。该脚本导出公开资料和示例题，剔除开发演示账号。
2. 创建仅服务器管理员可读的 `secrets/`。在 `db.env` 设置 `MYSQL_DATABASE=lexaid`、`MYSQL_USER=lexaid`、随机的 `MYSQL_PASSWORD` 和 `MYSQL_ROOT_PASSWORD`。在 `backend.env` 设置 `SPRING_PROFILES_ACTIVE=prod`、指向 `database:3306/lexaid` 的 MySQL JDBC URL（`SPRING_DATASOURCE_URL`）、对应的 `SPRING_DATASOURCE_USERNAME` 和 `SPRING_DATASOURCE_PASSWORD`，以及独立随机的 `APP_JWT_SECRET`。在 `deepseek.env` 设置 `DEEPSEEK_API_KEY`。这三个文件的权限应为 `600`。
3. 在 `backend/` 运行 `mvn verify`，把 JAR 放到服务器的 `app/lexaid.jar`。在 `frontend/` 运行 `npm ci && npm run build`，将 `dist/` 的内容发布至 `web/`，图片、字体与 JavaScript 等静态文件需允许 Nginx 读取。
4. 在服务器工作目录运行 `docker compose up -d database`，数据库健康后运行 `./import-data.sh`。每个已完成的导入步骤都有 SHA-256 记录，中断后可再次运行同一脚本。
5. 使用 Certbot 为该公网 IP 签发受信任的 shortlived 证书。当前部署使用独立的 `acme/config`、`acme/work` 和 `acme/logs` 目录，证书名为 `lexaid-ip`，standalone HTTP 验证在签发和续期时需要 80 端口。运行 `./install-tls.sh`，再运行 `docker compose up -d backend web`。安装本目录的 systemd 单元并执行 `systemctl enable --now lexaid-study-certificate.timer`，续期后会自动重新加载证书。

## 更新和检查

先发布新静态资源，最后替换 `web/index.html`。将安装包和同名的 `.sha256` 文件上传到 `packages/`，再执行 `./publish-package.sh 安装包文件名`。脚本校验 SHA-256 并发布到 `web/downloads/`，同名旧版本的内容若不同会拒绝发布。更新后端时先把 JAR 上传到同目录的临时文件，再原子替换 `app/lexaid.jar` 并执行 `docker compose restart backend`。数据库保存在该项目的命名卷中。可通过以下命令检查运行状态。

```sh
docker compose ps
docker compose logs --tail=50 backend
systemctl list-timers lexaid-study-certificate.timer
curl -fsS https://154.36.185.110:18443/ -o /dev/null
```

## 当前主机的内存配置

这台 4 GB 的共享主机在 `/etc/fstab` 启用了 `/swap.img`。首次导入时，宿主的 `vm.swappiness=0` 伴随持续内存回收和 OOM。部署后通过独立文件 `/etc/sysctl.d/99-zz-lexaid-study-memory.conf` 将其设为 `30`，应用后确认已有服务恢复健康。该参数作用于整台主机。若需恢复部署前的设置，移除这一个覆盖文件并执行 `sysctl -w vm.swappiness=0`。Compose 文件中的内存限额仅作用于 LexAid 容器。
