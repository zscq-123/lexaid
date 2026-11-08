-- Expands the original teaching-data table for provenance-aware international legislation.
-- Safe to execute on both an existing LexAid database and a freshly initialized database.
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS migrate_legal_provision;
DELIMITER //
CREATE PROCEDURE migrate_legal_provision()
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'legal_provision'
      AND index_name = 'idx_provision_law'
  ) THEN
    ALTER TABLE legal_provision DROP INDEX idx_provision_law;
  END IF;

  ALTER TABLE legal_provision
    MODIFY law_name VARCHAR(2000) NOT NULL,
    MODIFY category VARCHAR(100) NOT NULL,
    MODIFY chapter VARCHAR(1000) DEFAULT NULL,
    MODIFY article_no VARCHAR(191) NOT NULL,
    MODIFY content LONGTEXT NOT NULL,
    MODIFY issuing_authority VARCHAR(255) DEFAULT NULL;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'legal_provision'
      AND column_name = 'jurisdiction'
  ) THEN
    ALTER TABLE legal_provision
      ADD COLUMN jurisdiction VARCHAR(16) NOT NULL DEFAULT 'CN' AFTER issuing_authority;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'legal_provision'
      AND column_name = 'language_code'
  ) THEN
    ALTER TABLE legal_provision
      ADD COLUMN language_code VARCHAR(16) NOT NULL DEFAULT 'zh-CN' AFTER jurisdiction;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'legal_provision'
      AND column_name = 'status'
  ) THEN
    ALTER TABLE legal_provision ADD COLUMN `status` VARCHAR(64) DEFAULT NULL AFTER language_code;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'legal_provision'
      AND column_name = 'source_dataset'
  ) THEN
    ALTER TABLE legal_provision
      ADD COLUMN source_dataset VARCHAR(100) DEFAULT NULL AFTER `status`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'legal_provision'
      AND column_name = 'source_item_id'
  ) THEN
    ALTER TABLE legal_provision
      ADD COLUMN source_item_id VARCHAR(191) DEFAULT NULL AFTER source_dataset;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'legal_provision'
      AND column_name = 'source_url'
  ) THEN
    ALTER TABLE legal_provision ADD COLUMN source_url VARCHAR(1000) DEFAULT NULL AFTER source_item_id;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'legal_provision'
      AND column_name = 'license_name'
  ) THEN
    ALTER TABLE legal_provision ADD COLUMN license_name VARCHAR(100) DEFAULT NULL AFTER source_url;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'legal_provision'
      AND column_name = 'promulgation_date'
  ) THEN
    ALTER TABLE legal_provision ADD COLUMN promulgation_date DATE DEFAULT NULL AFTER license_name;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'legal_provision'
      AND column_name = 'metadata'
  ) THEN
    ALTER TABLE legal_provision ADD COLUMN metadata JSON DEFAULT NULL AFTER promulgation_date;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'legal_provision'
      AND index_name = 'uk_provision_source_item'
  ) THEN
    ALTER TABLE legal_provision
      ADD UNIQUE INDEX uk_provision_source_item (source_dataset, source_item_id);
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'legal_provision'
      AND index_name = 'idx_provision_law'
  ) THEN
    ALTER TABLE legal_provision ADD INDEX idx_provision_law (law_name(191));
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'legal_provision'
      AND index_name = 'idx_provision_jurisdiction'
  ) THEN
    ALTER TABLE legal_provision ADD INDEX idx_provision_jurisdiction (jurisdiction);
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'legal_provision'
      AND index_name = 'idx_provision_language'
  ) THEN
    ALTER TABLE legal_provision ADD INDEX idx_provision_language (language_code);
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'legal_provision'
      AND index_name = 'idx_provision_status'
  ) THEN
    ALTER TABLE legal_provision ADD INDEX idx_provision_status (`status`);
  END IF;
END//
DELIMITER ;

CALL migrate_legal_provision();
DROP PROCEDURE migrate_legal_provision;
