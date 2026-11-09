# LexAid 法律学习桌面系统

LexAid 面向需要把法律规则用到具体案情中的学习者。在工作台选择一个争点，就能沿着同一组材料读法条、看案例、做关联题，再带着自己的答卷向 AI 提问。错题复习检验的是能否把同一考点用在新的情境中。

在「错题本」选择「情景迁移练习」，先解答新案情，再查看判断依据与两道题的事实差异。练习记录归当前学习者所有。在「交流天地」选择「讨论知识库」，可按关键词检索观点整理，并从每条要点返回原帖或具体评论。

## 功能模块

- 法条检索 / 案例查询 / 学者观点导航：多条件检索、详情、标签、收藏、笔记、关联内容跳转
- 法条检索可按法域和法律展开原始目录，逐级选择编、分编、章、节。选择上级会包含其下属条文。
- 模拟答题支持按法学门类、科目以及有明确依据的法典和编组卷。当前题库有 8 个门类，19,007 题归入具体科目，701 题涉及多学科，5,920 题待判定。5,480 题有明确的编级依据，一题可关联多编。
- 答题进度：组卷页显示当前筛选范围内的已做题数和剩余题数。交卷后按题去重，答错和待评分均计入，空白答案不计入。答题页实时显示本卷已答题数。
- 模拟考试独立计时，支持历年真题、模拟客观卷和主观案例卷，可设置 1–480 分钟。交卷规则、评分及来源见[正式考试题库](datasets/formal-exams/README.md)。
- 日常练习支持随机组卷或按法条、案例等关联内容组卷，可在任意一道题交卷，随后查看成绩、逐题解析和错题记录。
- AI 辅助问答：可携带上下文提问，回答附引用来源；未配置 API Key 时降级为本地检索结果
- 学习笔记：对任意实体记笔记，仅本人可增删改
- 交流天地：登录后可发布文字或多图动态，排列封面及图片顺序，选择「全部用户」或「仅好友」，并点赞、评论。通讯录支持搜索账号或昵称、填写验证消息、接受或拒绝申请、删除好友。
- 个人中心支持预览、更换头像或恢复默认头像。可上传不超过 4 MB 的 JPG 或 PNG 图片，头像同步显示在导航、帖子和通讯录。
- 学习统计：概览、近 30 天答题趋势、类别正确率、学习足迹
- 用户管理后台：管理员可查看用户增长与活跃趋势，按角色、活跃状态筛选用户，并查看学习、答题、笔记、收藏和社区行为明细
- 桌面壳：Electron 最小壳（`electron/main.js` 加载 `frontend/dist`）
- 公开题库：内置 LawBench、LexEval 和 LeCoQA 的 25,693 条来源记录，精确去重后为 25,598 道题
- 公开法典：内置中国、美国、欧盟、英国 394,122 条法条/法律文书，保留来源、许可与原文链接
- 公开案例：内置中国指导性/公报案例、美国最高法院意见与欧洲人权法院案例事实，共 19,523 条
- 法学论文在「学者观点」中检索。已收录 6,439 条论文和预印本记录，其中 1,501 条有作者摘要、388 条有可检索正文。可按作者、关键词或 DOI 查询。[论文数据说明](datasets/legal-papers/README.md)

## 技术栈

| 层 | 技术 |
| --- | --- |
| 前端 | Vue 3.5（组合式 API + `<script setup>`）、Vite 6、Pinia、Vue Router 4、Element Plus、ECharts 6、Axios |
| 后端 | Java 17、Spring Boot 3.5、Spring Security + JWT、MyBatis-Plus 3.5、springdoc-openapi |
| 数据库 | MySQL 8（utf8mb4） |
| AI | 后端通过 DeepSeek Chat Completions 提供问答和主观题评分 |

## 环境要求

- JDK 17+（编译目标为 17；已验证 JDK 27）
- Maven 3.8+（未安装可使用 `~/.m2/wrapper/dists` 中的发行版，或项目内执行 `mvn -N wrapper:wrapper` 生成 wrapper）
- Node.js 18+、npm
- Docker（用于启动 MySQL）或本机 MySQL 8

## 启动步骤

### 1. 启动数据库

```bash
docker compose up -d
```

- 容器：`lexaid-mysql`，数据库 `lexaid`，应用账号 `lexaid`，本机开发密码 `lexaid-local`
- MySQL 只绑定 `127.0.0.1`；可复制 `.env.example` 为 `.env` 覆盖本机开发密码
- 宿主机端口：**3308**（本机 3306 被其他项目占用，故映射到 3308；如需改回 3306 修改 `docker-compose.yml`）
- 首次启动会依次执行表结构、示例数据、公开题库、法典与案例压缩 SQL；完成后共有 25,628 道题、394,140 条法条/文书和 19,529 个案例（均包含项目示例数据）
- 如使用本机 MySQL：创建数据库并按 [`datasets/README.md`](datasets/README.md) 中的说明手动导入

### 2. 启动后端

已有 MySQL 数据卷升级交流模块、头像、收藏分区和主观题评分时，在项目根目录执行以下命令。原有收藏会归入“未分区”。新数据卷由 Docker 自动初始化。

```bash
docker exec -i lexaid-mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u "$MYSQL_USER" "$MYSQL_DATABASE"' < backend/src/main/resources/community-friends.sql
docker exec -i lexaid-mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u "$MYSQL_USER" "$MYSQL_DATABASE"' < backend/src/main/resources/community-posts.sql
docker exec -i lexaid-mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u "$MYSQL_USER" "$MYSQL_DATABASE"' < backend/src/main/resources/user-avatar.sql
docker exec -i lexaid-mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u "$MYSQL_USER" "$MYSQL_DATABASE"' < backend/src/main/resources/favorite-folders.sql
docker exec -i lexaid-mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u "$MYSQL_USER" "$MYSQL_DATABASE"' < backend/src/main/resources/subjective-grading.sql
docker exec -i lexaid-mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u "$MYSQL_USER" "$MYSQL_DATABASE"' < backend/src/main/resources/scenario-practice.sql
docker exec -i lexaid-mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u "$MYSQL_USER" "$MYSQL_DATABASE"' < backend/src/main/resources/community-discussion-summaries.sql
gzip -dc datasets/question-legal-subjects.sql.gz | docker exec -i lexaid-mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 -u "$MYSQL_USER" "$MYSQL_DATABASE"'
docker exec -i lexaid-mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 -u "$MYSQL_USER" "$MYSQL_DATABASE"' < backend/src/main/resources/mock-exams.sql
gzip -dc datasets/formal-exams/formal-exam-bank.sql.gz | docker exec -i lexaid-mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 -u "$MYSQL_USER" "$MYSQL_DATABASE"'
gzip -dc datasets/formal-exams/formal-exam-bank-reviewed.sql.gz | docker exec -i lexaid-mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 -u "$MYSQL_USER" "$MYSQL_DATABASE"'
```

```bash
cd backend
mvn spring-boot:run
```

- 默认端口 **8080**，接口文档：<http://localhost:8080/swagger-ui>
- 若 8080 被其他程序占用（本机即为这种情况），通过环境变量切换端口：

```powershell
$env:SERVER_PORT = '8081'
mvn spring-boot:run
```

macOS / Linux：

```bash
SERVER_PORT=8081 mvn spring-boot:run
```

### 3. 启动前端

```bash
cd frontend
npm install
npm run dev
```

- 开发地址：<http://localhost:5173>，`/api` 代理到 `http://localhost:8080`
- 若后端端口不同（如 8081），设置代理目标：

```powershell
$env:VITE_PROXY_TARGET = 'http://localhost:8081'
npm run dev
```

macOS / Linux：

```bash
VITE_PROXY_TARGET=http://localhost:8081 npm run dev
```

### 4. （可选）Electron 桌面壳

```bash
cd frontend && npm run build   # 生成 frontend/dist
cd ../electron && npm ci && npm start
```

开发模式默认连接 `http://127.0.0.1:8080/api`。如后端使用其他端口：

```bash
LEXAID_API_URL=http://127.0.0.1:8081/api npm start
```

若当前网络无法访问 GitHub Release，可在首次启动时指定镜像（下载内容仍由 Electron 包内校验和验证）：

```bash
ELECTRON_MIRROR=https://npmmirror.com/mirrors/electron/ npm start
```

### 5. macOS 正式版

正式版连接同一套线上账号和资料。macOS 13 及以上按芯片选择 [Apple Silicon 安装包](https://154.36.185.110:18443/downloads/LexAid-1.3.0-macOS-arm64.dmg) 或 [Intel 安装包](https://154.36.185.110:18443/downloads/LexAid-1.3.0-macOS-x64.dmg)，打开磁盘映像，将 LexAid 拖到「应用程序」。两个安装包都采用本机临时签名，尚未经过 Apple 公证。

要在 Mac 上重建两个架构的 DMG，请先构建前端，再运行打包命令。产物和 SHA-256 校验文件位于项目根目录的 `release/`。

```sh
(cd frontend && npm ci && npm run build)
(cd electron && npm ci && LEXAID_RELEASE_API_URL=https://154.36.185.110:18443/api npm run package:mac)
```

### 6. Windows 与移动端

| 平台 | 安装与使用 |
| --- | --- |
| Windows 10/11 x64 | 运行 [Windows 安装程序](https://154.36.185.110:18443/downloads/LexAid-1.3.0-Windows-x64-Setup.exe)，从开始菜单打开 LexAid。[打包说明](electron/packaging/windows/README.md) |
| Android 9 及以上 | 在手机上下载并打开 [Android APK](https://154.36.185.110:18443/downloads/LexAid-1.0.0-Android.apk)。[Android 工程](mobile/android/README.md) |
| HarmonyOS NEXT / HarmonyOS 5 及以上 | 鸿蒙手机与平板使用 ArkWeb 加载同一线上服务。提供[未签名调试 HAP](https://154.36.185.110:18443/downloads/LexAid-1.0.0-HarmonyOS-Debug-Unsigned.hap) 和 [DevEco Studio 工程](https://154.36.185.110:18443/downloads/LexAid-1.0.0-HarmonyOS-DevEco.zip)。真机安装前须配置设备调试签名。[构建与安装说明](mobile/harmony/README.md) |
| iPhone / iPad | 用 Safari 打开[线上版](https://154.36.185.110:18443/)，从分享菜单选择「添加到主屏幕」。原生客户端支持 iOS 16 及以上，提供[模拟器包](https://154.36.185.110:18443/downloads/LexAid-1.0.0-iOS-Simulator.zip)和 [Xcode 工程](https://154.36.185.110:18443/downloads/LexAid-1.0.0-iOS-Xcode.zip)。[真机归档](https://154.36.185.110:18443/downloads/LexAid-1.0.0-iOS-Device-Unsigned.xcarchive.zip)需要 Apple 开发团队签名后才能安装。[iOS 签名与构建说明](mobile/ios/README.md) |

手机端使用底部标签切换工作台、答题、交流和个人中心，完整模块与资料搜索位于顶部。各客户端登录同一线上账号即可访问自己的收藏和学习记录。首次登录需要联网。

需要一次下载全部平台时，可使用[全平台安装包（含鸿蒙调试包和工程）](https://154.36.185.110:18443/downloads/LexAid-All-Platforms-1.3.0.zip)，解压后按平台选择。

## 本地开发演示账号

| 账号 | 密码 | 角色 |
| --- | --- | --- |
| demo | 123456 | USER（内置，密码 BCrypt 加密） |

注册的新账号默认为 USER 角色。

### 启用用户管理后台

管理后台位于 `/admin`，后端会对全部 `/api/admin/**` 接口强制校验 `ADMIN` 角色。先注册一个仅用于管理的账号，再从本机数据库将它提升为管理员（将 `your-admin-account` 替换为实际账号）：

```bash
docker exec lexaid-mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u "$MYSQL_USER" "$MYSQL_DATABASE" -e "UPDATE \`user\` SET role=\"ADMIN\" WHERE username=\"your-admin-account\";"'
```

角色写入 JWT，提升权限后需要退出并重新登录。不要将公开演示账号设为管理员。

## AI 配置（环境变量）

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `DEEPSEEK_API_KEY` | 空 | 问答和评分共用的密钥，仅发送至 DeepSeek 官方 HTTPS 接口 |
| `AI_BASE_URL` | `https://api.deepseek.com` | 服务端接口根地址 |
| `AI_API_KEY` | 空 | 兼容旧配置；如使用自建兼容服务，在此设置该服务的密钥 |
| `AI_MODEL` | `deepseek-flash` | 问答与评分的模型名 |
| `AI_MAX_TOKENS` | `1200` | 单次问答的输出上限 |
| `DISCUSSION_AI_BASE_URL` | 空 | 仅社区观点整理使用的兼容推理服务地址。省略时沿用当前 DeepSeek 配置 |
| `DISCUSSION_AI_MODEL` | 空 | 指定独立服务时，填写该服务实际提供的模型名 |
| `DISCUSSION_AI_API_KEY` | 空 | 独立服务的认证密钥，免认证的本地服务可留空 |

公开帖子达到 3 条评论且至少有 2 位评论作者后，后台会整理原帖和最近 24 条评论，并在「交流天地 → 讨论知识库」收录有实质观点的结果。每条观点可追溯到原文，新增评论会触发重新整理。好友可见帖不参与自动整理。

使用已经运行的 Ollama 等兼容服务时，可将 `DISCUSSION_AI_BASE_URL` 设为其 `/v1` 地址，并选择支持 JSON 输出的模型。部署在容器中时，地址必须可从后端容器访问。该设置仅作用于社区整理。[Ollama 兼容接口说明](https://docs.ollama.com/api/openai-compatibility)

本机可以在 `~/.config/lexaid/deepseek.properties` 中设置 `DEEPSEEK_API_KEY` 并将文件权限设为 `600`，或在启动后端的环境中设置同名变量。更改配置后重启后端。评分会向模型发送题干、参考答案和本次作答。[DeepSeek 接口说明](https://api-docs.deepseek.com/) · [JSON 输出说明](https://api-docs.deepseek.com/guides/json_mode/)

## 生产环境

当前线上部署使用独立的 HTTPS 网关、Java 服务和 MySQL 数据库，部署文件、资料导入及更新步骤见[服务器部署说明](deploy/production/README.md)。

生产部署必须启用 `prod` 配置并提供数据库密码和随机 JWT 密钥；生产配置默认关闭 Swagger：

```bash
SPRING_PROFILES_ACTIVE=prod \
SPRING_DATASOURCE_URL='jdbc:mysql://db:3306/lexaid' \
SPRING_DATASOURCE_USERNAME='lexaid' \
SPRING_DATASOURCE_PASSWORD='replace-me' \
APP_JWT_SECRET='replace-with-at-least-32-random-characters' \
java -jar backend/target/lexaid-backend-1.0.0.jar
```

## 验证命令

交流模块的跨账号回归脚本会在指定的测试后端注册三个用户。连接独立测试库的后端启动后，运行 `COMMUNITY_TEST_API=http://127.0.0.1:18081/api node scripts/community-check.mjs`，可验证发帖、图片读取、好友审批、撤销好友权限与删帖。

```bash
cd backend && mvn clean verify
cd ../frontend && npm ci && npm run build && npm audit
cd ../electron && npm ci && npm start
cd .. && node --test scripts/question-dataset.test.mjs
python3 scripts/legal-code-dataset.test.py
python3 scripts/legal-case-dataset.test.py
python3 scripts/legal-paper-dataset.test.py
gzip -t datasets/lexaid-public-questions.sql.gz
gzip -t datasets/legal-codes/*.sql.gz
gzip -t datasets/legal-cases/*.sql.gz
gzip -t datasets/legal-papers/lexaid-papers.sql.gz
```

前端冒烟脚本由 `frontend` 管理 Playwright 依赖。后端、前端和数据库启动后执行：

```bash
cd frontend
npx playwright install chromium   # 首次运行
npm run smoke
npm run ui-check
```

## AI 站内检索

在「AI 问答」中可直接用自然语言搜索全站，或选择特定范围。公共资料可按条号、案号、法院、作者、DOI、标签及正文片段查询。选择「仅论文」可以限定论文记录。询问题库或馆藏数量时，系统从当前数据库统计。

交流天地的帖子及评论按当前可见性检索。个人笔记、收藏和分区、试卷成绩、错题及学习记录按登录账号查询。问答会给出编号来源，点击可核对命中片段、查看原文或继续针对该资料提问。来源权限失效后，依赖该来源的历史回答也会隐藏。

全站搜索使用文字、元数据和评论。要读取帖子里的图片，打开原帖，点「针对帖子提问」并明确询问图片内容；系统会重新核验该帖的访问权限，再把所选帖子的附件交给 DeepSeek 识别。

## 主观题评分

交卷时，客观题按答案判分，每题 1 分。对有参考答案的简答题，后端将题干、参考答案和本次作答提交给 DeepSeek，每题满分 10 分。成绩页显示每题得分和理由，批改期间可以离开，稍后重新打开成绩页查看。

有参考答案的主观题交白卷记 0 分。缺少参考答案的题目会标明原因，服务暂时不可用时可在成绩页重试。这些未评分的题目不计入当前总分的分母。已有历史试卷保留原成绩。

## 项目结构

```text
lexaid/
├─ backend/                 Spring Boot 后端（src/main/resources 内含 schema.sql / data.sql）
├─ datasets/                公开题库、法典、案例压缩 SQL、清单与许可说明
├─ frontend/                Vue 3 + Vite 前端
├─ electron/                Electron 桌面壳
├─ scripts/                 冒烟验证与题库构建脚本
├─ docker-compose.yml       MySQL 8
└─ README.md
```

## 验收情况

- [x] `docker compose up -d` 启动 MySQL，基础数据与 25,693 条公开题库来源记录自动导入
- [x] 中国、美国、欧盟、英国法典/法规 394,122 条自动导入，支持法域筛选与来源追溯
- [x] 中国、美国最高法院、欧洲人权法院案例 19,523 条自动导入，支持全文检索与来源追溯
- [x] 后端启动成功，Swagger UI 可访问
- [x] 前端 `npm run dev` 启动成功，代理生效（见前端联调记录）
- [x] 注册 → 登录 → 工作台完整链路可用，token 过期自动跳登录
- [x] 法条/案例/观点检索、详情、关联区块跳转可用
- [x] 组卷 → 答题 → 交卷 → 解析 → 错题本闭环可用，判分规则正确
- [x] 未配置模型密钥时 AI 问答返回本地检索结果
- [x] 笔记增删改查与详情页联动可用，仅本人可改（越权返回 HTTP 403）
- [x] 统计页数据与答题/浏览行为一致
- [x] 页面符合 UI 规范（主色 #2E5FA3、白底、8px 圆角、简洁克制）
- [x] README 包含启动步骤、环境变量说明、演示账号

## 已知说明

- 若 8080 端口被其他项目占用，可使用上述环境变量同步调整后端端口和前端代理目标。
- 「学者观点」同时包含原有教学观点和可追溯的论文记录。选中「仅看论文」即可查阅论文。
- 题库可能依据历史法律版本制作，不可将参考答案直接视为现行法律意见；数据来源、许可和未直接再发布的数据集见 [`datasets/README.md`](datasets/README.md)。
