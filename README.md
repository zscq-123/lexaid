# LexAid 法律学习桌面系统

基于 Vue 3 + Spring Boot 3 + MySQL 8 的法律学习桌面系统，打通「法条—案例—观点—题目」四类知识实体的关联，形成「学—练—记—考」闭环。

## 功能模块

- 法条检索 / 案例查询 / 学者观点导航：多条件检索、详情、标签、收藏、笔记、关联内容跳转
- 模拟答题：随机组卷 / 按实体组卷、自动判分、逐题解析、错题本（仅错题 / 全部）
- AI 辅助问答：可携带上下文提问，回答附引用来源；未配置 API Key 时降级为本地检索结果
- 学习笔记：对任意实体记笔记，仅本人可增删改
- 学习统计：概览、近 30 天答题趋势、类别正确率、学习足迹
- 桌面壳：Electron 最小壳（`electron/main.js` 加载 `frontend/dist`）

## 技术栈

| 层 | 技术 |
| --- | --- |
| 前端 | Vue 3.4（组合式 API + `<script setup>`）、Vite 5、Pinia、Vue Router 4、Element Plus、ECharts、Axios |
| 后端 | Java 17、Spring Boot 3.2、Spring Security + JWT、MyBatis-Plus 3.5、springdoc-openapi |
| 数据库 | MySQL 8（utf8mb4） |
| AI | Spring WebClient 调用 OpenAI 兼容 `/chat/completions`（可配置 baseUrl 兼容 Ollama） |

## 环境要求

- JDK 17+（本机验证使用 JDK 21，编译目标为 17）
- Maven 3.8+（未安装可使用 `~/.m2/wrapper/dists` 中的发行版，或项目内执行 `mvn -N wrapper:wrapper` 生成 wrapper）
- Node.js 18+、npm
- Docker（用于启动 MySQL）或本机 MySQL 8

## 启动步骤

### 1. 启动数据库

```bash
docker compose up -d
```

- 容器：`lexaid-mysql`，数据库 `lexaid`，账号 `root`，密码 `lexaid123`
- 宿主机端口：**3308**（本机 3306 被其他项目占用，故映射到 3308；如需改回 3306 修改 `docker-compose.yml`）
- 首次启动自动执行 `backend/src/main/resources/schema.sql` 与 `data.sql`（幂等，可重复执行）
- 如使用本机 MySQL：创建数据库并手动执行上述两个脚本，再修改 `backend/src/main/resources/application.yml` 中的连接配置

### 2. 启动后端

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

### 4. （可选）Electron 桌面壳

```bash
cd frontend && npm run build   # 生成 frontend/dist
cd ../electron && npm install electron && npx electron .
```

## 演示账号

| 账号 | 密码 | 角色 |
| --- | --- | --- |
| demo | 123456 | USER（内置，密码 BCrypt 加密） |

注册的新账号默认为 USER 角色。

## AI 配置（环境变量）

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `AI_BASE_URL` | `https://api.openai.com/v1` | OpenAI 兼容接口地址（可指向 Ollama，如 `http://localhost:11434/v1`） |
| `AI_API_KEY` | 空 | 未配置时 AI 问答返回本地检索降级结果，不影响系统使用 |
| `AI_MODEL` | `gpt-4o-mini` | 模型名 |

## 项目结构

```text
lexaid/
├─ backend/                 Spring Boot 后端（src/main/resources 内含 schema.sql / data.sql）
├─ frontend/                Vue 3 + Vite 前端
├─ electron/main.js         Electron 最小壳
├─ docker-compose.yml       MySQL 8
└─ README.md
```

## 验收情况

- [x] `docker compose up -d` 启动 MySQL，schema.sql / data.sql 可重复执行（已验证幂等）
- [x] 后端启动成功，Swagger UI 可访问
- [x] 前端 `npm run dev` 启动成功，代理生效（见前端联调记录）
- [x] 注册 → 登录 → 工作台完整链路可用，token 过期自动跳登录
- [x] 法条/案例/观点检索、详情、关联区块跳转可用
- [x] 组卷 → 答题 → 交卷 → 解析 → 错题本闭环可用，判分规则正确
- [x] 未配置 AI_API_KEY 时 AI 问答返回本地检索降级结果
- [x] 笔记增删改查与详情页联动可用，仅本人可改（越权返回 403）
- [x] 统计页数据与答题/浏览行为一致
- [x] 页面符合 UI 规范（主色 #2E5FA3、白底、8px 圆角、简洁克制）
- [x] README 包含启动步骤、环境变量说明、演示账号

## 已知说明

- 本机 8080 端口被其他项目占用，验证时后端使用 8081、前端代理指向 8081；代码默认仍为 8080，可通过环境变量调整。
- 案例/观点为公开信息整理的教学示例数据，法条为公开真实条文；正式使用前建议按需补充或替换数据。
