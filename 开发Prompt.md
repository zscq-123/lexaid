# LexAid 法律学习桌面系统 · 开发 Prompt

## 使用说明

- 将本文件内容整体粘贴给 Codex（或其他 AI 编码工具），在项目根目录 `D:\Users\Lenovo\Documents\ChatGPT\lexaid` 执行；
- 按第 12 节"开发步骤"顺序执行，每完成一步验证可运行后再继续；
- 遇到未明确的业务细节时，按本文档默认约定实现，不要反问；
- 最终交付前逐项勾选第 11 节"验收标准"。

---

## 1. 角色

你是一名资深全栈工程师，精通 Vue 3 与 Spring Boot，负责从零开发一套桌面端法律学习系统。你的产出应结构清晰、可运行、可维护，代码关键处使用中文注释。

## 2. 项目目标

开发"LexAid 法律学习桌面系统"：基于 Vue 3 + Spring Boot 3，实现法条检索、案例查询、模拟答题、AI 辅助问答、学习笔记、学者观点导航、学习记录七个功能模块，并打通"法条—案例—观点—题目"四类知识实体之间的关联，形成"学—练—记—考"闭环。页面要求简洁、统一、克制。

## 3. 技术栈（强制约束）

### 前端

- Vue 3.4（组合式 API、`<script setup>`）+ Vite 5，使用 JavaScript（不引入 TypeScript）；
- Pinia（登录态）、Vue Router 4（路由守卫）；
- Element Plus（UI 组件）、ECharts（统计图表）、Axios；
- 桌面壳：提供最小 Electron 目录（`electron/main.js` 加载 `frontend/dist`），不强制完善打包脚本。

### 后端

- Java 17 + Spring Boot 3.2；
- Spring Web、Spring Security + JWT（jjwt 0.11+）；
- MyBatis-Plus 3.5（分页插件）、MySQL 8；
- springdoc-openapi（/swagger-ui）；
- AI：使用 Spring WebClient 调用 OpenAI 兼容的 `/chat/completions` 接口。

### 环境

- 提供 `docker-compose.yml` 启动 MySQL 8（database: lexaid，user: root，password: lexaid123）；
- 后端默认端口 8080，前端开发端口 5173 并配置代理 `/api` → `http://localhost:8080`；
- 不引入计划外的大型依赖；确需引入时说明理由。

## 4. 项目结构要求

```text
lexaid/
├─ backend/
│  ├─ pom.xml
│  └─ src/main/
│     ├─ java/com/lexaid/
│     │  ├─ LexAidApplication.java
│     │  ├─ common/      # Result、PageResult、GlobalExceptionHandler、JwtUtil
│     │  ├─ config/      # SecurityConfig、MybatisPlusConfig、WebClientConfig、CorsConfig
│     │  ├─ auth/        # User、AuthController、AuthService、UserMapper
│     │  ├─ content/     # Provision、CaseInfo、Viewpoint 及其 Controller/Service/Mapper
│     │  ├─ exam/        # Question、ExamSession、AnswerRecord、WrongQuestion
│     │  ├─ relation/    # EntityRelation、RelationService
│     │  ├─ note/        # Note
│     │  ├─ ai/          # AiController、AiService、ChatMessage
│     │  └─ stats/       # StatsController、StatsService
│     └─ resources/
│        ├─ application.yml
│        ├─ schema.sql
│        └─ data.sql
├─ frontend/
│  ├─ package.json
│  ├─ vite.config.js
│  └─ src/
│     ├─ main.js
│     ├─ App.vue
│     ├─ router/index.js
│     ├─ stores/user.js
│     ├─ api/           # request.js（axios 封装）+ 各模块 api 文件
│     ├─ layouts/MainLayout.vue
│     └─ views/
│        ├─ login/Login.vue
│        ├─ dashboard/Dashboard.vue
│        ├─ provisions/ProvisionList.vue、ProvisionDetail.vue
│        ├─ cases/CaseList.vue、CaseDetail.vue
│        ├─ viewpoints/ViewpointList.vue、ViewpointDetail.vue
│        ├─ exam/ExamSetup.vue、ExamRun.vue、ExamResult.vue
│        ├─ wrong/WrongBook.vue
│        ├─ ai/AiChat.vue
│        ├─ notes/NoteList.vue
│        ├─ stats/Stats.vue
│        └─ profile/Profile.vue
├─ electron/main.js     # 可选，加载 frontend/dist
├─ docker-compose.yml
└─ README.md            # 启动步骤、环境变量、账号说明
```

## 5. 数据模型（建表脚本必须包含）

所有表使用 `utf8mb4`，主键 `BIGINT AUTO_INCREMENT`，通用字段 `created_at`、`updated_at DATETIME`。

### 5.1 user 用户表

username VARCHAR(50) UNIQUE、password VARCHAR(100)（BCrypt）、nickname VARCHAR(50)、role VARCHAR(20) 默认 `USER`。

### 5.2 legal_provision 法条表

law_name VARCHAR(100)、category VARCHAR(50)（刑法/民法/行政法等）、chapter VARCHAR(100)、article_no VARCHAR(30)、content TEXT、effective_date DATE、issuing_authority VARCHAR(100)（可空）。索引：law_name、category、article_no。

### 5.3 case_info 案例表

title VARCHAR(200)、case_no VARCHAR(50)、court VARCHAR(100)、judge_date DATE、case_type VARCHAR(50)、summary TEXT（案情摘要）、holding TEXT（裁判要旨）、procedure VARCHAR(50)（一审/二审/再审）。索引：case_type、judge_date。

### 5.4 scholar_viewpoint 学者观点表

scholar_name VARCHAR(50)、title VARCHAR(200)、summary TEXT、content TEXT（可空）、source VARCHAR(200)（出处）、publish_year INT。索引：scholar_name。

### 5.5 question 题目表

type VARCHAR(20)（SINGLE/MULTIPLE/JUDGE/SHORT）、stem TEXT、options TEXT（选择题 JSON 数组 `[{key,text}]`，其他题型为 NULL）、answer TEXT（选择题键数组/判断题 true|false/简答参考答案）、analysis TEXT（解析）、difficulty TINYINT（1~5）、category VARCHAR(50)。

### 5.6 exam_session / answer_record

- exam_session：user_id、config TEXT（组卷条件 JSON）、score、total、correct、start_time、end_time、duration_sec；
- answer_record：session_id、user_id、question_id、user_answer TEXT、is_correct BOOLEAN（简答为 NULL）、answered_at。

### 5.7 note / favorite / study_record

- note：user_id、target_type、target_id、content TEXT；
- favorite：user_id、target_type、target_id，唯一约束 `(user_id,target_type,target_id)`；
- study_record：user_id、action_type（VIEW/SEARCH/ANSWER/CHAT/NOTE）、target_type、target_id（可空）、duration_sec、detail TEXT、created_at，索引 `(user_id, created_at)`。

### 5.8 entity_relation / tag / entity_tag

- entity_relation：source_type、source_id、target_type、target_id、relation_type、description VARCHAR(255)，唯一约束 `(source_type,source_id,target_type,target_id,relation_type)`；
- tag：name VARCHAR(50) UNIQUE；
- entity_tag：target_type、target_id、tag_id，唯一约束 `(target_type,target_id,tag_id)`。

### 5.9 chat_message

user_id、role VARCHAR(10)（USER/ASSISTANT）、content TEXT、refs TEXT（JSON：引用来源数组）。

### 5.10 关系类型

RELIES_ON（案例依据法条）、INTERPRETS（观点阐释法条）、ANALYZES（观点评析案例）、EXAMINES（题目考察实体）、RELATED（一般相关）。

## 6. 后端接口要求

统一响应 `{ code, message, data }`；分页返回 `{ records, total }`；除 `/api/auth/register`、`/api/auth/login` 外，所有接口要求请求头 `Authorization: Bearer <token>`。

### 6.1 认证

- `POST /api/auth/register`：body `{username, password, nickname}`，密码 BCrypt 加密；
- `POST /api/auth/login`：返回 `{token, user:{id,username,nickname,role}}`，token 有效期 24 小时；
- `GET /api/auth/me`：当前用户信息。

### 6.2 法条 / 案例 / 观点（content 模块）

- `GET /api/provisions?keyword=&lawName=&category=&tag=&page=&size=`
  keyword 对 law_name/chapter/article_no/content 做 LIKE 匹配；列表项包含 id、law_name、category、article_no、content 摘要（前 120 字）、tags；
- `GET /api/provisions/{id}`：完整详情 + tags + 当前用户收藏状态；浏览行为写入 study_record；
- `GET /api/provisions/{id}/related`：返回 `{cases:[], viewpoints:[], questions:[]}`（每项含 id、标题、摘要）；
- `GET /api/cases?keyword=&caseType=&court=&year=&page=&size=`：keyword 匹配 title/summary/holding；
- `GET /api/cases/{id}`、`GET /api/cases/{id}/related`：同上；
- `GET /api/viewpoints?scholar=&keyword=&tag=&page=&size=`；
- `GET /api/viewpoints/{id}`、`GET /api/viewpoints/{id}/related`；
- 收藏：`POST /api/favorites`（body `{targetType,targetId}`）、`GET /api/favorites?type=`、`DELETE /api/favorites/{id}`。

### 6.3 答题（exam 模块）

- `POST /api/exam/sessions`：body `{category?, difficulty?, count, sourceType?, sourceId?}`；按条件随机选题且不重复；若提供 sourceType/sourceId，优先选取关联该实体的题；不足时补随机题；
- `GET /api/exam/sessions/{id}`：返回题目列表（不含 answer/analysis），附带该题关联实体 id 列表；
- `POST /api/exam/sessions/{id}/submit`：body `{answers:[{questionId, answer}]}`；判分并返回 `{sessionId, score, total, correct, details:[{questionId, userAnswer, correctAnswer, isCorrect, analysis, related:[{type,id,title}]}]}`；判分规则：SINGLE/JUDGE 严格相等，MULTIPLE 全对得分，SHORT 不判分（isCorrect=null，展示参考答案）；
- `GET /api/exam/sessions`：历史列表（分页）；
- `GET /api/wrong-questions?page=&size=`：当前用户最近错题（含解析与关联实体）。

### 6.4 关联（relation 模块）

- `GET /api/relations/related?sourceType=&sourceId=`：按类型聚合返回关联实体（双向查询）；
- `POST /api/relations`（可选，管理用）：body `{sourceType,sourceId,targetType,targetId,relationType,description}`，写入时自动补反向记录。

### 6.5 笔记（note 模块）

- `GET /api/notes?targetType=&targetId=`：无参数返回我的全部笔记；有参数返回某实体下的笔记；
- `POST /api/notes`：body `{targetType,targetId,content}`；
- `PUT /api/notes/{id}`、`DELETE /api/notes/{id}`：仅本人可操作。

### 6.6 AI 辅助问答（ai 模块）

- `POST /api/ai/chat`：body `{message, contextType?, contextId?, history:[{role,content}]}`；
- 后端构造 system prompt："你是 LexAid 法律学习助手。请用简洁准确的中文回答，必须结合提供的知识内容，回答末尾列出引用来源。无法确定时明确说明，并建议用户查阅原文。";
- 当提供 contextType/contextId 时，读取该实体内容及其关联实体摘要注入 prompt，并要求回答输出 `refs: [{type,id,title}]`（解析 AI 返回的 JSON 或由后端根据注入内容自行构造 refs）；
- 使用 WebClient 调用 `${AI_BASE_URL}/chat/completions`（默认 `https://api.openai.com/v1`），model 默认 `gpt-4o-mini`，header `Authorization: Bearer ${AI_API_KEY}`；
- 未配置 AI_API_KEY 时返回 `{answer: "AI 服务未配置，以下为本地相关检索结果。", refs: 本地相关实体}`，不报错；
- 响应格式 `{answer, refs:[{type,id,title}]}`；`GET /api/ai/history` 返回当前用户最近 20 条对话（含 refs）。

### 6.7 学习记录与统计（stats 模块）

- 后端在以下动作写入 study_record：查看详情（VIEW）、搜索（SEARCH）、答题交卷（ANSWER）、AI 提问（CHAT）、保存笔记（NOTE）；
- `GET /api/stats/overview`：`{totalStudyMinutes, totalAnswer, totalCorrect, accuracy, streakDays}`；
- `GET /api/stats/daily?days=30`：`[{date, answerCount, correctCount, studyMinutes}]`（无数据补 0）；
- `GET /api/stats/category-accuracy`：`[{category, total, correct, accuracy}]`；
- `GET /api/stats/records?page=&size=`：学习足迹列表。

## 7. 前端页面与交互要求

1. 布局：顶部栏（Logo、全局搜索框、AI 问答入口、用户菜单）+ 左侧导航（法条、案例、观点、答题、笔记、统计）+ 内容区；
2. axios 封装：baseURL `/api`，自动带 token，401 跳转登录页，统一错误提示（ElMessage）；
3. 路由守卫：未登录跳 /login；
4. 工作台：概览卡片（今日学习时长、答题正确率、连续天数）+ 快捷入口 + 薄弱类别推荐（法条/案例/观点卡片）；
5. 检索页：顶部筛选表单 + 列表 + 分页，空状态提示；
6. 详情页：内容主体 + "相关内容"区块（按类型分组、卡片可跳转）+ 收藏按钮 + 笔记入口 + "针对本内容提问"按钮（跳转 AI 页并携带上下文）；
7. 答题页：进度条 + 计时器 + 题卡导航；交卷前二次确认；选择题点击选项即选中，判断题单选按钮，简答文本框；
8. 结果页：得分环形图（ECharts）+ 正确率 + 逐题解析列表（解析内"查看法条/案例/观点"链接）+ 重新练习按钮；
9. 错题本：列表 + 筛选（仅错题/全部）+ 每题显示解析与相关材料；
10. AI 页：消息列表（气泡）、引用来源卡片（点击跳转详情）、输入框、快捷指令（全局/法条/案例/观点）；加载中状态；
11. 笔记页：列表 + 新增/编辑弹窗（或右侧编辑器），支持按实体类型筛选；
12. 统计页：ECharts 折线图（近 30 天答题量/正确率）+ 类别正确率柱状图 + 学习足迹表格；
13. 全站中文界面，所有加载、空、错误状态有明确提示。

## 8. UI 规范

- 主色 `#2E5FA3`，白底；正文 `#303133`，次要文字 `#909399`，边框 `#EBEEF5`，圆角 8px；
- 顶部栏 56px，侧边栏 220px，内容最大宽度 1280px；
- 使用系统字体栈，字号 13/14/16/20；
- 页面简洁：无轮播、无弹窗轰炸、无多余动效；卡片阴影轻量；
- 样式写在组件 `<style scoped>` 中，不引入全局样式库。

## 9. 种子数据要求（data.sql）

- 法条至少 10 条，覆盖 3 个类别（如刑法、民法、行政法），使用真实公开条文文本；
- 案例至少 6 个，每个关联 2~4 条法条（RELIES_ON）；
- 观点至少 6 条，每条至少关联 1 条法条（INTERPRETS）或 1 个案例（ANALYZES）；
- 题目至少 30 道：SINGLE 12 / MULTIPLE 6 / JUDGE 8 / SHORT 4，每道关联 1~3 个实体（EXAMINES）；
- 每类实体打上 2~5 个标签；写一个内置演示账号 `demo / 123456`；
- 所有 SQL 幂等（INSERT IGNORE 或固定主键），可重复执行。

## 10. 非功能要求

- 分页统一；SQL 使用参数绑定；列表接口返回摘要而非全文；
- 后端统一异常处理，日志简洁；CORS 允许 `http://localhost:5173`；
- 越权防护：笔记、收藏、答题记录均校验当前用户；
- application.yml 中 AI 配置使用环境变量：`AI_BASE_URL`、`AI_API_KEY`、`AI_MODEL`，未配置不阻塞启动。

## 11. 验收标准（全部满足才算完成）

- [ ] `docker compose up -d` 启动 MySQL，`schema.sql`、`data.sql` 可重复执行；
- [ ] 后端 `mvn spring-boot:run` 启动成功，8080 端口 `/swagger-ui` 可访问；
- [ ] 前端 `npm install && npm run dev` 启动成功，5173 端口页面可访问，代理 `/api` 生效；
- [ ] 注册 → 登录 → 工作台完整链路可用，token 过期自动跳登录；
- [ ] 法条/案例/观点检索、详情、关联区块跳转均可用；
- [ ] 组卷 → 答题 → 交卷 → 解析 → 错题本闭环可用，判分规则正确；
- [ ] 未配置 AI_API_KEY 时 AI 问答返回降级结果；配置后返回带引用来源的回答；
- [ ] 笔记增删改查与详情页联动可用，仅本人可改；
- [ ] 统计页数据与答题/浏览行为一致；
- [ ] 页面符合第 8 节 UI 规范，浏览器控制台无报错；
- [ ] README 包含启动步骤、环境变量说明、演示账号。

## 12. 开发步骤（按顺序执行）

1. 初始化仓库结构（backend/frontend/electron/scripts）、README 骨架；
2. 编写 `schema.sql`、`data.sql` 与 `docker-compose.yml`，启动数据库并导入脚本；
3. 搭建后端骨架：common（Result、全局异常、JWT 工具）、SecurityConfig、application.yml，启动验证；
4. 实现认证模块，用 curl 验证注册登录；
5. 实现内容模块：法条/案例/观点 CRUD 与检索（含标签、分页）；
6. 实现关联模块与 `/api/relations/related` 双向查询；
7. 实现答题模块：组卷、取卷、交卷判分、错题本；
8. 实现笔记、收藏；
9. 实现 study_record 埋点与统计接口；
10. 实现 AI 模块（含未配置降级）；
11. 搭建前端骨架：布局、路由、axios 封装、Pinia store；
12. 实现内容页面（检索/详情/关联区块/收藏/笔记入口）；
13. 实现答题页面（组卷/答题/结果）与错题本；
14. 实现 AI 页面（对话 + 引用卡片 + 上下文携带）；
15. 实现笔记页、统计页、工作台、个人中心；
16. 可选：electron/main.js 加载构建产物；
17. 全量联调，按第 11 节逐项自测，补充 README，汇报完成情况。

## 13. 工作约束

- 代码关键处使用中文注释；
- 保持项目可运行：每完成一个模块执行一次启动验证；
- 不擅自改变技术栈与数据模型；如需调整，先说明理由；
- 最终交付时报告每个验收项的实测结果。
