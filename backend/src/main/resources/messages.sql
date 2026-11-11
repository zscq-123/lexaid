-- 即时通讯：好友之间的文字、图片、文件私信与消息撤回
-- 已存在的数据卷执行本文件即可；新数据卷由 schema.sql 与 Docker 初始化自动创建。
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS direct_message (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  sender_id    BIGINT       NOT NULL,
  recipient_id BIGINT       NOT NULL,
  content      VARCHAR(2000) NOT NULL,
  kind         VARCHAR(16)  NOT NULL DEFAULT 'TEXT',
  file_name    VARCHAR(220) DEFAULT NULL,
  media_type   VARCHAR(160) DEFAULT NULL,
  file_size    BIGINT       DEFAULT NULL,
  duration_seconds INT     DEFAULT NULL,
  file_bytes   LONGBLOB     DEFAULT NULL,
  read_at      DATETIME(6)  DEFAULT NULL,
  recalled_at  DATETIME(6)  DEFAULT NULL,
  quote_id     BIGINT       DEFAULT NULL,
  quote_sender_id BIGINT    DEFAULT NULL,
  quote_kind   VARCHAR(16)  DEFAULT NULL,
  quote_preview VARCHAR(300) DEFAULT NULL,
  created_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_direct_message_sender (sender_id, id),
  KEY idx_direct_message_recipient (recipient_id, id),
  KEY idx_direct_message_unread (recipient_id, read_at, id),
  CONSTRAINT fk_direct_message_sender FOREIGN KEY (sender_id) REFERENCES `user` (id) ON DELETE CASCADE,
  CONSTRAINT fk_direct_message_recipient FOREIGN KEY (recipient_id) REFERENCES `user` (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='好友私信';


CREATE TABLE IF NOT EXISTS message_conversation_clear (
  user_id           BIGINT   NOT NULL,
  peer_id           BIGINT   NOT NULL,
  cleared_before_id BIGINT   NOT NULL DEFAULT 0,
  cleared_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (user_id, peer_id),
  KEY idx_message_clear_peer (peer_id),
  CONSTRAINT fk_message_clear_user FOREIGN KEY (user_id) REFERENCES `user` (id) ON DELETE CASCADE,
  CONSTRAINT fk_message_clear_peer FOREIGN KEY (peer_id) REFERENCES `user` (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户清空私信记录的边界';

CREATE TABLE IF NOT EXISTS message_conversation_setting (
  user_id    BIGINT   NOT NULL,
  peer_id    BIGINT   NOT NULL,
  muted      TINYINT(1) NOT NULL DEFAULT 0,
  pinned     TINYINT(1) NOT NULL DEFAULT 0,
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (user_id, peer_id),
  KEY idx_message_setting_peer (peer_id),
  CONSTRAINT fk_message_setting_user FOREIGN KEY (user_id) REFERENCES `user` (id) ON DELETE CASCADE,
  CONSTRAINT fk_message_setting_peer FOREIGN KEY (peer_id) REFERENCES `user` (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='私信会话设置';

CREATE TABLE IF NOT EXISTS message_user_delete (
  user_id    BIGINT NOT NULL,
  message_id BIGINT NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (user_id, message_id),
  KEY idx_message_user_delete_message (message_id),
  CONSTRAINT fk_message_delete_user FOREIGN KEY (user_id) REFERENCES `user` (id) ON DELETE CASCADE,
  CONSTRAINT fk_message_delete_message FOREIGN KEY (message_id) REFERENCES direct_message (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户单条删除的私信';

CREATE TABLE IF NOT EXISTS message_reminder (
  id         BIGINT NOT NULL AUTO_INCREMENT,
  user_id    BIGINT NOT NULL,
  message_id BIGINT NOT NULL,
  peer_id    BIGINT NOT NULL,
  preview    VARCHAR(300) NOT NULL,
  remind_at  DATETIME(6) NOT NULL,
  notified_at DATETIME(6) DEFAULT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_message_reminder_due (notified_at, remind_at),
  KEY idx_message_reminder_user (user_id, id),
  CONSTRAINT fk_message_reminder_user FOREIGN KEY (user_id) REFERENCES `user` (id) ON DELETE CASCADE,
  CONSTRAINT fk_message_reminder_message FOREIGN KEY (message_id) REFERENCES direct_message (id) ON DELETE CASCADE,
  CONSTRAINT fk_message_reminder_peer FOREIGN KEY (peer_id) REFERENCES `user` (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='私信提醒';

-- 兼容已经执行过早期 messages.sql 的数据库。
DROP PROCEDURE IF EXISTS lexaid_message_attachment_columns;
DELIMITER //
CREATE PROCEDURE lexaid_message_attachment_columns()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'direct_message' AND COLUMN_NAME = 'kind') THEN
    ALTER TABLE direct_message ADD COLUMN kind VARCHAR(16) NOT NULL DEFAULT 'TEXT' AFTER content;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'direct_message' AND COLUMN_NAME = 'file_name') THEN
    ALTER TABLE direct_message ADD COLUMN file_name VARCHAR(220) DEFAULT NULL AFTER kind;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'direct_message' AND COLUMN_NAME = 'media_type') THEN
    ALTER TABLE direct_message ADD COLUMN media_type VARCHAR(160) DEFAULT NULL AFTER file_name;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'direct_message' AND COLUMN_NAME = 'file_size') THEN
    ALTER TABLE direct_message ADD COLUMN file_size BIGINT DEFAULT NULL AFTER media_type;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'direct_message' AND COLUMN_NAME = 'duration_seconds') THEN
    ALTER TABLE direct_message ADD COLUMN duration_seconds INT DEFAULT NULL AFTER file_size;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'direct_message' AND COLUMN_NAME = 'file_bytes') THEN
    ALTER TABLE direct_message ADD COLUMN file_bytes LONGBLOB DEFAULT NULL AFTER duration_seconds;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'direct_message' AND COLUMN_NAME = 'recalled_at') THEN
    ALTER TABLE direct_message ADD COLUMN recalled_at DATETIME(6) DEFAULT NULL AFTER read_at;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'direct_message' AND COLUMN_NAME = 'quote_id') THEN
    ALTER TABLE direct_message ADD COLUMN quote_id BIGINT DEFAULT NULL AFTER recalled_at;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'direct_message' AND COLUMN_NAME = 'quote_sender_id') THEN
    ALTER TABLE direct_message ADD COLUMN quote_sender_id BIGINT DEFAULT NULL AFTER quote_id;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'direct_message' AND COLUMN_NAME = 'quote_kind') THEN
    ALTER TABLE direct_message ADD COLUMN quote_kind VARCHAR(16) DEFAULT NULL AFTER quote_sender_id;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'direct_message' AND COLUMN_NAME = 'quote_preview') THEN
    ALTER TABLE direct_message ADD COLUMN quote_preview VARCHAR(300) DEFAULT NULL AFTER quote_kind;
  END IF;
END//
DELIMITER ;
CALL lexaid_message_attachment_columns();
DROP PROCEDURE lexaid_message_attachment_columns;
