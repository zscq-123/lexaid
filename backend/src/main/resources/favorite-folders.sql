SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS favorite_folder (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  user_id    BIGINT      NOT NULL,
  name       VARCHAR(40) NOT NULL,
  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_favorite_folder_name (user_id, name),
  CONSTRAINT fk_favorite_folder_user FOREIGN KEY (user_id) REFERENCES `user` (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '收藏分区';

DROP PROCEDURE IF EXISTS migrate_favorite_folders;
DELIMITER //
CREATE PROCEDURE migrate_favorite_folders()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'favorite' AND column_name = 'folder_id'
  ) THEN
    ALTER TABLE favorite ADD COLUMN folder_id BIGINT DEFAULT NULL AFTER target_id;
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.referential_constraints
    WHERE constraint_schema = DATABASE() AND table_name = 'favorite' AND constraint_name = 'fk_favorite_folder'
  ) THEN
    ALTER TABLE favorite ADD CONSTRAINT fk_favorite_folder
      FOREIGN KEY (folder_id) REFERENCES favorite_folder (id) ON DELETE SET NULL;
  END IF;
END//
DELIMITER ;
CALL migrate_favorite_folders();
DROP PROCEDURE migrate_favorite_folders;
