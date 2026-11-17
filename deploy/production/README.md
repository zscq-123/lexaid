# LexAid 服务器部署

服务器工作目录为 `/opt/lexaid-study`，Compose 项目名为 `lexaid-study`。网页与桌面客户端均使用 `https://154.36.185.110:18443`。MySQL 和 Java 后端通过 Compose 内部网络通信。

## 首次部署

1. 把本目录的 Compose 文件、Nginx 配置、脚本和证书定时单元放入服务器工作目录。在本地仓库运行 `python3 deploy/production/prepare-seed.py <输出目录>`，将其内容传到服务器的 `init/`。该脚本导出公开资料和示例题，剔除开发演示账号。
2. 创建仅服务器管理员可读的 `secrets/`。在 `db.env` 设置 `MYSQL_DATABASE=lexaid`、`MYSQL_USER=lexaid`、随机的 `MYSQL_PASSWORD` 和 `MYSQL_ROOT_PASSWORD`。在 `backend.env` 设置 `SPRING_PROFILES_ACTIVE=prod`、指向 `database:3306/lexaid` 的 MySQL JDBC URL（`SPRING_DATASOURCE_URL`）、对应的 `SPRING_DATASOURCE_USERNAME` 和 `SPRING_DATASOURCE_PASSWORD`，以及独立随机的 `APP_JWT_SECRET`。在 `deepseek.env` 设置 `DEEPSEEK_API_KEY`。这三个文件的权限应为 `600`。
3. 在 `backend/` 运行 `mvn verify`，把 JAR 放到服务器的 `app/lexaid.jar`。在 `frontend/` 运行 `npm ci && npm run build`，将 `dist/` 的内容发布至 `web/`，图片、字体与 JavaScript 等静态文件需允许 Nginx 读取。
4. 在服务器工作目录运行 `docker compose up -d database`，数据库健康后运行 `./import-data.sh`。每个已完成的导入步骤都有 SHA-256 记录，中断后可再次运行同一脚本。
5. 使用 Certbot 为该公网 IP 签发受信任的 shortlived 证书。当前部署使用独立的 `acme/config`、`acme/work` 和 `acme/logs` 目录，证书名为 `lexaid-ip`。HTTP 验证必须能从公网 80 端口读取挑战文件；本机已有 HTTP 服务，使用下文说明的 `webroot` 方式。运行 `./install-tls.sh`，再运行 `docker compose up -d backend web`。安装本目录的 systemd 单元并执行 `systemctl enable --now lexaid-study-certificate.timer`，续期后会自动重新加载证书。

## 更新和检查

### HTTPS 证书续期

当前主机的 80 端口由 `trademark-scanner.service` 提供 HTTP 服务。LexAid 证书使用 Certbot 的 `webroot` 验证方式，验证目录为 `/opt/trademark-scanner/public`；不再使用需要独占 80 端口的 `standalone`。现有服务直接提供该目录下的 `/.well-known/acme-challenge/` 文件，无需停服。更换 80 端口服务或静态目录时，需同时保留这个验证路径或重新配置 Certbot。

续期参数保存在服务器的 `acme/config/renewal/lexaid-ip.conf`。如需重新设置，先确认公网能够读取验证目录中的临时文件，再执行以下命令；`reconfigure` 会先通过测试环境验证再保存参数。不要手动修改或提交证书私钥。

```sh
/opt/certbot/bin/certbot reconfigure --non-interactive --cert-name lexaid-ip \
  --webroot --webroot-path /opt/trademark-scanner/public \
  --config-dir /opt/lexaid-study/acme/config \
  --work-dir /opt/lexaid-study/acme/work \
  --logs-dir /opt/lexaid-study/acme/logs
systemctl start lexaid-study-certificate.service
```

现有定时器每天检查两次。实际续期成功后，部署钩子 `install-tls.sh` 自动发布证书并重新加载 Nginx。验收使用不带 `-k` 的 HTTPS 请求；仅看到容器运行或跳过证书校验后返回 200，不代表证书已恢复正常。

### 应用更新

包含资源库的版本首次部署前，先在现有数据库执行 `backend/src/main/resources/resources.sql` 创建 `learning_resource` 表；该脚本可重复执行。包含私信的版本还需执行 `backend/src/main/resources/messages.sql` 创建 `direct_message` 表。

学习质量升级还需先执行 `backend/src/main/resources/question-quality.sql`。该幂等脚本仅建立复核记录表，不删除题目或改写历史作答；新后端会动态隔离未达到质量门槛的题目。首次导入已通过 `30-question-quality.sql` 纳入初始化清单。详见 `docs/learning-quality-upgrade-2026-11-11.md`。

复审版本另需执行 `backend/src/main/resources/question-seed-corrections.sql`：只修正原内容仍匹配的种子题 15、17、27，将原文与修订内容存入 `question_content_revision`，不重写历史作答。新数据卷通过 `31-question-seed-corrections.sql` 初始化。升级后所有外部导入题统一等待教学复核；旧答卷中的参考答案须满足当前资格与作答时点检查。部署前先备份并在目标库检查迁移匹配数量，详见 `docs/question-audit-2026-11-11.md`。

2026-11-12 全量审计另有两个幂等迁移，按顺序执行 `question-seed-review-20261112.sql`（种子题 13、22 的理论/条文适用范围）和 `question-structure-review-20261112.sql`（87 道 LexEval 题的选项结构）。文件均在 `backend/src/main/resources/`，新数据卷分别通过 `32-question-seed-review.sql`、`33-question-structure-review.sql` 导入。迁移仅匹配原始内容并归档修订；结构迁移还跳过已有作答记录的题目，输出每题的应用或跳过原因。不要将结构修复或迁移成功视为法律答案审核通过。部署前保留数据库备份并核实输出；本地验证不代表已部署生产。逐题证据和核验边界见 `docs/full-question-review-2026-11-12.md`。

教学改写第 001 批使用 `backend/src/main/resources/question-teaching-batch-001.sql`（新数据卷初始化序号 35），需在题库和学科映射导入、前述迁移完成后执行。它修订 216 道 LeCoQA 题并补齐 9 道分类，只有原文、分类及内容哈希均一致时才应用；同名修订档案冲突时跳过。核对输出应为 216 条 `APPLIED_OR_ALREADY_APPLIED`，其他状态须先检查原因。它保留历史作答/评分，并保持外部题库发布审核门禁；不能用迁移完成代替正式法律审核。逐题预览和批次证据见 `docs/teaching/2026-11-12/batch-001/`，后续范围见 `datasets/question-explanations/plan.jsonl`。

独立模拟卷库随后执行 `mock-exam-content-review-20261112.sql`，对应第 34 号初始化文件，修订 3 道原创主观题。它使用 `mock_exam_content_revision` 保存前后内容，不改写 `mock_exam_item` 中已开考试的题目快照。若原题或既有归档已被其他维护者修改，迁移会跳过并输出题号。

Nginx 必须为 `/ws/` 配置 WebSocket Upgrade 转发（本目录的 `nginx.conf` 已包含）；否则私信会自动降级为轮询同步。

先发布新静态资源，最后替换 `web/index.html`。将安装包和同名的 `.sha256` 文件上传到 `packages/`，再执行 `./publish-package.sh 安装包文件名`。脚本校验 SHA-256 并发布到 `web/downloads/`，同名旧版本的内容若不同会拒绝发布。更新后端时先把 JAR 上传到同目录的临时文件，再原子替换 `app/lexaid.jar` 并执行 `docker compose restart backend`。数据库保存在该项目的命名卷中。可通过以下命令检查运行状态。

```sh
docker compose ps
docker compose logs --tail=50 backend
systemctl list-timers lexaid-study-certificate.timer
curl -fsS https://154.36.185.110:18443/ -o /dev/null
```

## 社区推理服务

社区观点整理默认使用现有推理服务。若已有可从后端容器访问的本地模型服务，在 `secrets/backend.env` 设置 `DISCUSSION_AI_BASE_URL` 和实际的 `DISCUSSION_AI_MODEL`，需要认证时再设置 `DISCUSSION_AI_API_KEY`。用 `docker compose up -d --force-recreate backend` 重新读取这些环境变量。

## 当前主机的内存配置

这台 4 GB 的共享主机在 `/etc/fstab` 启用了 `/swap.img`。首次导入时，宿主的 `vm.swappiness=0` 伴随持续内存回收和 OOM。部署后通过独立文件 `/etc/sysctl.d/99-zz-lexaid-study-memory.conf` 将其设为 `30`，应用后确认已有服务恢复健康。该参数作用于整台主机。若需恢复部署前的设置，移除这一个覆盖文件并执行 `sysctl -w vm.swappiness=0`。Compose 文件中的内存限额仅作用于 LexAid 容器。
