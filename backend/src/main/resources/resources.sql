SET NAMES utf8mb4;

-- 学习资源库：个人资源（PRIVATE）与社区公开资源（PUBLIC）
-- 已存在的数据卷执行本文件即可；新数据卷由 schema.sql 与 Docker 初始化自动创建。
CREATE TABLE IF NOT EXISTS learning_resource (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  owner_id       BIGINT       NOT NULL,
  title          VARCHAR(160) NOT NULL,
  description    VARCHAR(1200) NOT NULL DEFAULT '',
  category       VARCHAR(40)  NOT NULL DEFAULT '其他',
  visibility     VARCHAR(16)  NOT NULL DEFAULT 'PRIVATE' COMMENT 'PRIVATE/PUBLIC',
  file_name      VARCHAR(220) NOT NULL,
  file_ext       VARCHAR(16)  NOT NULL,
  media_type     VARCHAR(160) NOT NULL,
  file_size      BIGINT       NOT NULL,
  file_bytes     LONGBLOB     NOT NULL,
  download_count BIGINT       NOT NULL DEFAULT 0,
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_resource_public (visibility, id),
  KEY idx_resource_owner (owner_id, id),
  KEY idx_resource_downloads (visibility, download_count),
  KEY idx_resource_category (category, id),
  KEY idx_resource_ext (file_ext),
  CONSTRAINT fk_resource_owner FOREIGN KEY (owner_id) REFERENCES `user` (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学习资源库';
