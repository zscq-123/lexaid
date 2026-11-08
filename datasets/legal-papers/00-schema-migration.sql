-- MySQL 8 performs this InnoDB ALTER atomically. Seven existing teaching
-- records (and their IDs used by notes and favorites) keep NULL paper fields.
SET @paper_migration = IF(
  EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
          AND table_name = 'scholar_viewpoint' AND column_name = 'summary_kind'),
  'DO 0',
  'ALTER TABLE scholar_viewpoint
     DROP INDEX idx_viewpoint_scholar,
     MODIFY scholar_name TEXT NOT NULL,
     MODIFY title TEXT NOT NULL,
     MODIFY summary MEDIUMTEXT NOT NULL,
     MODIFY content MEDIUMTEXT DEFAULT NULL,
     MODIFY source VARCHAR(500) DEFAULT NULL,
     ADD COLUMN language_code VARCHAR(16) DEFAULT NULL,
     ADD COLUMN source_dataset VARCHAR(120) DEFAULT NULL,
     ADD COLUMN source_item_id VARCHAR(512) COLLATE utf8mb4_bin DEFAULT NULL,
     ADD COLUMN source_url TEXT DEFAULT NULL,
     ADD COLUMN doi VARCHAR(512) COLLATE utf8mb4_bin DEFAULT NULL,
     ADD COLUMN license_name VARCHAR(120) DEFAULT NULL,
     ADD COLUMN summary_kind VARCHAR(32) DEFAULT NULL,
     ADD INDEX idx_viewpoint_scholar (scholar_name(191)),
     ADD UNIQUE INDEX uk_viewpoint_source (source_dataset, source_item_id),
     ADD UNIQUE INDEX uk_viewpoint_doi (doi),
     ADD INDEX idx_viewpoint_language (language_code)'
);
PREPARE paper_schema FROM @paper_migration;
EXECUTE paper_schema;
DEALLOCATE PREPARE paper_schema;
