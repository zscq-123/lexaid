-- Expands case_info for full-text, provenance-aware Chinese and international case law.
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS migrate_case_info;
DELIMITER //
CREATE PROCEDURE migrate_case_info()
BEGIN
  ALTER TABLE case_info
    MODIFY title VARCHAR(2000) NOT NULL,
    MODIFY case_no VARCHAR(191) DEFAULT NULL,
    MODIFY court VARCHAR(500) DEFAULT NULL,
    MODIFY case_type VARCHAR(100) DEFAULT NULL,
    MODIFY summary LONGTEXT NOT NULL,
    MODIFY holding LONGTEXT DEFAULT NULL,
    MODIFY `procedure` VARCHAR(100) DEFAULT NULL;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'case_info' AND column_name = 'content'
  ) THEN
    ALTER TABLE case_info ADD COLUMN content LONGTEXT DEFAULT NULL AFTER `procedure`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'case_info' AND column_name = 'jurisdiction'
  ) THEN
    ALTER TABLE case_info ADD COLUMN jurisdiction VARCHAR(16) NOT NULL DEFAULT 'CN' AFTER content;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'case_info' AND column_name = 'language_code'
  ) THEN
    ALTER TABLE case_info ADD COLUMN language_code VARCHAR(16) NOT NULL DEFAULT 'zh-CN' AFTER jurisdiction;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'case_info' AND column_name = 'status'
  ) THEN
    ALTER TABLE case_info ADD COLUMN `status` VARCHAR(64) DEFAULT NULL AFTER language_code;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'case_info' AND column_name = 'source_dataset'
  ) THEN
    ALTER TABLE case_info ADD COLUMN source_dataset VARCHAR(100) DEFAULT NULL AFTER `status`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'case_info' AND column_name = 'source_item_id'
  ) THEN
    ALTER TABLE case_info ADD COLUMN source_item_id VARCHAR(191) DEFAULT NULL AFTER source_dataset;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'case_info' AND column_name = 'source_url'
  ) THEN
    ALTER TABLE case_info ADD COLUMN source_url VARCHAR(1000) DEFAULT NULL AFTER source_item_id;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'case_info' AND column_name = 'license_name'
  ) THEN
    ALTER TABLE case_info ADD COLUMN license_name VARCHAR(100) DEFAULT NULL AFTER source_url;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'case_info' AND column_name = 'metadata'
  ) THEN
    ALTER TABLE case_info ADD COLUMN metadata JSON DEFAULT NULL AFTER license_name;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'case_info'
      AND index_name = 'uk_case_source_item'
  ) THEN
    ALTER TABLE case_info ADD UNIQUE INDEX uk_case_source_item (source_dataset, source_item_id);
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'case_info'
      AND index_name = 'idx_case_jurisdiction'
  ) THEN
    ALTER TABLE case_info ADD INDEX idx_case_jurisdiction (jurisdiction);
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'case_info'
      AND index_name = 'idx_case_language'
  ) THEN
    ALTER TABLE case_info ADD INDEX idx_case_language (language_code);
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'case_info'
      AND index_name = 'idx_case_status'
  ) THEN
    ALTER TABLE case_info ADD INDEX idx_case_status (`status`);
  END IF;
END//
DELIMITER ;

CALL migrate_case_info();
DROP PROCEDURE migrate_case_info;
