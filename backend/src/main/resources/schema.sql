-- ============================================================
-- LexAid 法律学习系统 · 建表脚本（幂等，可重复执行）
-- 所有表 utf8mb4，主键 BIGINT AUTO_INCREMENT，通用 created_at / updated_at
-- ============================================================
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `user` (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  username   VARCHAR(50)  NOT NULL,
  password   VARCHAR(100) NOT NULL,
  nickname   VARCHAR(50)  DEFAULT NULL,
  role       VARCHAR(20)  NOT NULL DEFAULT 'USER',
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户表';

CREATE TABLE IF NOT EXISTS legal_provision (
  id                 BIGINT        NOT NULL AUTO_INCREMENT,
  law_name           VARCHAR(2000) NOT NULL,
  category           VARCHAR(100)  NOT NULL,
  chapter            VARCHAR(1000) DEFAULT NULL,
  article_no         VARCHAR(191)  NOT NULL,
  content            LONGTEXT      NOT NULL,
  effective_date     DATE          DEFAULT NULL,
  issuing_authority  VARCHAR(255)  DEFAULT NULL,
  jurisdiction       VARCHAR(16)   NOT NULL DEFAULT 'CN',
  language_code      VARCHAR(16)   NOT NULL DEFAULT 'zh-CN',
  status             VARCHAR(64)   DEFAULT NULL,
  source_dataset     VARCHAR(100)  DEFAULT NULL,
  source_item_id     VARCHAR(191)  DEFAULT NULL,
  source_url         VARCHAR(1000) DEFAULT NULL,
  license_name       VARCHAR(100)  DEFAULT NULL,
  promulgation_date  DATE          DEFAULT NULL,
  metadata           JSON          DEFAULT NULL,
  created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_provision_source_item (source_dataset, source_item_id),
  KEY idx_provision_law (law_name(191)),
  KEY idx_provision_category (category),
  KEY idx_provision_article (article_no),
  KEY idx_provision_jurisdiction (jurisdiction),
  KEY idx_provision_language (language_code),
  KEY idx_provision_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '法条表';

CREATE TABLE IF NOT EXISTS case_info (
  id                 BIGINT        NOT NULL AUTO_INCREMENT,
  title              VARCHAR(2000) NOT NULL,
  case_no            VARCHAR(191)  DEFAULT NULL,
  court              VARCHAR(500)  DEFAULT NULL,
  judge_date         DATE          DEFAULT NULL,
  case_type          VARCHAR(100)  DEFAULT NULL,
  summary            LONGTEXT      NOT NULL,
  holding            LONGTEXT      DEFAULT NULL,
  `procedure`        VARCHAR(100)  DEFAULT NULL,
  content            LONGTEXT      DEFAULT NULL,
  jurisdiction       VARCHAR(16)   NOT NULL DEFAULT 'CN',
  language_code      VARCHAR(16)   NOT NULL DEFAULT 'zh-CN',
  `status`           VARCHAR(64)   DEFAULT NULL,
  source_dataset     VARCHAR(100)  DEFAULT NULL,
  source_item_id     VARCHAR(191)  DEFAULT NULL,
  source_url         VARCHAR(1000) DEFAULT NULL,
  license_name       VARCHAR(100)  DEFAULT NULL,
  metadata           JSON          DEFAULT NULL,
  created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_case_source_item (source_dataset, source_item_id),
  KEY idx_case_type (case_type),
  KEY idx_case_judge_date (judge_date),
  KEY idx_case_jurisdiction (jurisdiction),
  KEY idx_case_language (language_code),
  KEY idx_case_status (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '案例表';

CREATE TABLE IF NOT EXISTS scholar_viewpoint (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  scholar_name TEXT         NOT NULL,
  title        TEXT         NOT NULL,
  summary      MEDIUMTEXT   NOT NULL,
  content      MEDIUMTEXT   DEFAULT NULL,
  source       VARCHAR(500) DEFAULT NULL,
  publish_year INT          DEFAULT NULL,
  language_code VARCHAR(16) DEFAULT NULL,
  source_dataset VARCHAR(120) DEFAULT NULL,
  source_item_id VARCHAR(512) COLLATE utf8mb4_bin DEFAULT NULL,
  source_url   TEXT         DEFAULT NULL,
  doi          VARCHAR(512) COLLATE utf8mb4_bin DEFAULT NULL,
  license_name VARCHAR(120) DEFAULT NULL,
  summary_kind VARCHAR(32)  DEFAULT NULL,
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_viewpoint_scholar (scholar_name(191)),
  UNIQUE KEY uk_viewpoint_source (source_dataset, source_item_id),
  UNIQUE KEY uk_viewpoint_doi (doi),
  KEY idx_viewpoint_language (language_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '学者观点表';

CREATE TABLE IF NOT EXISTS question (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  type       VARCHAR(20) NOT NULL COMMENT 'SINGLE/MULTIPLE/JUDGE/SHORT',
  stem       MEDIUMTEXT  NOT NULL,
  options    MEDIUMTEXT  DEFAULT NULL COMMENT '选择题选项 JSON [{key,text}]',
  answer     MEDIUMTEXT  NOT NULL COMMENT '选择题键数组/判断题 true|false/简答参考答案',
  analysis   MEDIUMTEXT  DEFAULT NULL,
  difficulty TINYINT     NOT NULL DEFAULT 3 COMMENT '1~5',
  category   VARCHAR(50) DEFAULT NULL,
  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_question_type (type),
  KEY idx_question_category (category)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '题目表';

CREATE TABLE IF NOT EXISTS question_source (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  question_id    BIGINT       NOT NULL,
  dataset        VARCHAR(64)  NOT NULL,
  source_item_id VARCHAR(191) NOT NULL,
  source_task    VARCHAR(100) DEFAULT NULL,
  license_name   VARCHAR(100) DEFAULT NULL,
  source_url     VARCHAR(500) DEFAULT NULL,
  metadata       JSON         DEFAULT NULL,
  imported_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_question_source_item (dataset, source_item_id),
  KEY idx_question_source_question (question_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '公开题目来源与许可信息';

CREATE TABLE IF NOT EXISTS exam_session (
  id           BIGINT   NOT NULL AUTO_INCREMENT,
  user_id      BIGINT   NOT NULL,
  config       TEXT     NOT NULL COMMENT '组卷条件 JSON',
  score        INT      NOT NULL DEFAULT 0,
  total        INT      NOT NULL DEFAULT 0,
  correct      INT      NOT NULL DEFAULT 0,
  start_time   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  end_time     DATETIME DEFAULT NULL,
  duration_sec INT      DEFAULT NULL,
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_session_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '答题会话表';

CREATE TABLE IF NOT EXISTS answer_record (
  id           BIGINT   NOT NULL AUTO_INCREMENT,
  session_id   BIGINT   NOT NULL,
  user_id      BIGINT   NOT NULL,
  question_id  BIGINT   NOT NULL,
  user_answer  TEXT     DEFAULT NULL,
  is_correct   BOOLEAN  DEFAULT NULL COMMENT '未判分为 NULL，满分为 true',
  grade_points SMALLINT DEFAULT NULL,
  grading_status VARCHAR(24) DEFAULT NULL,
  grading_feedback TEXT DEFAULT NULL,
  grade_token CHAR(36) DEFAULT NULL,
  grade_lease_until DATETIME DEFAULT NULL,
  answered_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_answer_session (session_id),
  KEY idx_grading_queue (grading_status,grade_lease_until,id),
  KEY idx_answer_user (user_id, is_correct)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '答题明细表';

CREATE TABLE IF NOT EXISTS note (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  user_id     BIGINT       NOT NULL,
  target_type VARCHAR(20)  NOT NULL COMMENT 'PROVISION/CASE/VIEWPOINT/QUESTION',
  target_id   BIGINT       NOT NULL,
  content     TEXT         NOT NULL,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_note_user (user_id),
  KEY idx_note_target (target_type, target_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '笔记表';

CREATE TABLE IF NOT EXISTS favorite_folder (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  user_id    BIGINT      NOT NULL,
  name       VARCHAR(40) NOT NULL,
  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_favorite_folder_name (user_id, name),
  CONSTRAINT fk_favorite_folder_user FOREIGN KEY (user_id) REFERENCES `user` (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '收藏分区';

CREATE TABLE IF NOT EXISTS favorite (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  user_id     BIGINT      NOT NULL,
  target_type VARCHAR(20) NOT NULL,
  target_id   BIGINT      NOT NULL,
  folder_id   BIGINT      DEFAULT NULL,
  created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_favorite (user_id, target_type, target_id),
  CONSTRAINT fk_favorite_folder FOREIGN KEY (folder_id) REFERENCES favorite_folder (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '收藏表';

CREATE TABLE IF NOT EXISTS study_record (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  user_id      BIGINT       NOT NULL,
  action_type  VARCHAR(20)  NOT NULL COMMENT 'VIEW/SEARCH/ANSWER/CHAT/NOTE',
  target_type  VARCHAR(20)  DEFAULT NULL,
  target_id    BIGINT       DEFAULT NULL,
  duration_sec INT          NOT NULL DEFAULT 0,
  detail       VARCHAR(500) DEFAULT NULL,
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_study_user_time (user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '学习记录表';

CREATE TABLE IF NOT EXISTS entity_relation (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  source_type   VARCHAR(20)  NOT NULL,
  source_id     BIGINT       NOT NULL,
  target_type   VARCHAR(20)  NOT NULL,
  target_id     BIGINT       NOT NULL,
  relation_type VARCHAR(20)  NOT NULL COMMENT 'RELIES_ON/INTERPRETS/ANALYZES/EXAMINES/RELATED',
  description   VARCHAR(255) DEFAULT NULL,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_relation (source_type, source_id, target_type, target_id, relation_type),
  KEY idx_relation_source (source_type, source_id),
  KEY idx_relation_target (target_type, target_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '实体关联表';

CREATE TABLE IF NOT EXISTS tag (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  name       VARCHAR(50) NOT NULL,
  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_tag_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '标签表';

CREATE TABLE IF NOT EXISTS entity_tag (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  target_type VARCHAR(20) NOT NULL,
  target_id   BIGINT      NOT NULL,
  tag_id      BIGINT      NOT NULL,
  created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_entity_tag (target_type, target_id, tag_id),
  KEY idx_entity_tag_tag (tag_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '实体标签表';

CREATE TABLE IF NOT EXISTS chat_message (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  user_id    BIGINT      NOT NULL,
  role       VARCHAR(10) NOT NULL COMMENT 'USER/ASSISTANT',
  content    TEXT        NOT NULL,
  refs       TEXT        DEFAULT NULL COMMENT '引用来源 JSON 数组',
  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_chat_user (user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'AI 对话记录表';
