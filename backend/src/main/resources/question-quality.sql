-- Teaching review is separate from source attribution. Changing content invalidates approval.
CREATE TABLE IF NOT EXISTS question_quality_review (
  question_id BIGINT NOT NULL PRIMARY KEY,
  content_hash CHAR(64) NOT NULL,
  first_reviewer VARCHAR(100) NOT NULL,
  second_reviewer VARCHAR(100) NOT NULL,
  legal_basis TEXT NOT NULL,
  key_facts TEXT NOT NULL,
  common_mistakes TEXT NOT NULL,
  reasoning TEXT NOT NULL,
  reviewed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (TRIM(first_reviewer) <> '' AND TRIM(second_reviewer) <> '' AND first_reviewer <> second_reviewer),
  CHECK (CHAR_LENGTH(TRIM(legal_basis)) > 0 AND CHAR_LENGTH(TRIM(key_facts)) > 0
     AND CHAR_LENGTH(TRIM(common_mistakes)) > 0 AND CHAR_LENGTH(TRIM(reasoning)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
