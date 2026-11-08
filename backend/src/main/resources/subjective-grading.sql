-- Old rows keep NULL grading_status and retain their original scores.
DROP PROCEDURE IF EXISTS migrate_subjective_grading;
DELIMITER //
CREATE PROCEDURE migrate_subjective_grading()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
      WHERE table_schema=DATABASE() AND table_name='answer_record' AND column_name='grading_status') THEN
    ALTER TABLE answer_record
      ADD COLUMN grade_points SMALLINT DEFAULT NULL,
      ADD COLUMN grading_status VARCHAR(24) DEFAULT NULL,
      ADD COLUMN grading_feedback TEXT DEFAULT NULL,
      ADD COLUMN grade_token CHAR(36) DEFAULT NULL,
      ADD COLUMN grade_lease_until DATETIME DEFAULT NULL,
      ADD INDEX idx_grading_queue (grading_status,grade_lease_until,id);
  END IF;
END//
DELIMITER ;
CALL migrate_subjective_grading();
DROP PROCEDURE migrate_subjective_grading;
