SET @add_legal_exam_date = IF(
  EXISTS(SELECT 1 FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'user_profile' AND column_name = 'legal_exam_date'),
  'SELECT 1',
  'ALTER TABLE user_profile ADD COLUMN legal_exam_date DATE DEFAULT NULL AFTER master_exam_year'
);
PREPARE add_legal_exam_date_statement FROM @add_legal_exam_date;
EXECUTE add_legal_exam_date_statement;
DEALLOCATE PREPARE add_legal_exam_date_statement;

SET @add_master_exam_date = IF(
  EXISTS(SELECT 1 FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'user_profile' AND column_name = 'master_exam_date'),
  'SELECT 1',
  'ALTER TABLE user_profile ADD COLUMN master_exam_date DATE DEFAULT NULL AFTER legal_exam_date'
);
PREPARE add_master_exam_date_statement FROM @add_master_exam_date;
EXECUTE add_master_exam_date_statement;
DEALLOCATE PREPARE add_master_exam_date_statement;
