CREATE TABLE IF NOT EXISTS mock_exam_bank (
  id BIGINT PRIMARY KEY,
  source_type VARCHAR(16) NOT NULL,
  paper_type VARCHAR(16) NOT NULL,
  kind VARCHAR(16) NOT NULL,
  subject VARCHAR(100) NOT NULL,
  difficulty TINYINT NOT NULL,
  stem MEDIUMTEXT NOT NULL,
  options_json JSON NULL,
  reference_answer MEDIUMTEXT NOT NULL,
  explanation TEXT NULL,
  source_label VARCHAR(500) NOT NULL,
  source_url VARCHAR(2000) NULL,
  reading_url VARCHAR(2000) NULL,
  KEY idx_mock_pool (source_type,paper_type,kind,difficulty)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS mock_exam_session (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  source_type VARCHAR(16) NOT NULL,
  paper_type VARCHAR(16) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  started_at BIGINT NOT NULL,
  deadline BIGINT NOT NULL,
  submitted_at BIGINT NULL,
  active_slot TINYINT GENERATED ALWAYS AS (IF(status='ACTIVE',1,NULL)) STORED,
  UNIQUE KEY uk_mock_active_user (user_id,active_slot),
  KEY idx_mock_expiry (status,deadline),
  KEY idx_mock_history (user_id,id),
  CONSTRAINT fk_mock_user FOREIGN KEY (user_id) REFERENCES `user`(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Snapshot each item so an update to the public dataset cannot alter an ongoing exam.
CREATE TABLE IF NOT EXISTS mock_exam_item (
  session_id BIGINT NOT NULL,
  ordinal_no INT NOT NULL,
  bank_id BIGINT NOT NULL,
  kind VARCHAR(16) NOT NULL,
  subject VARCHAR(100) NOT NULL,
  stem MEDIUMTEXT NOT NULL,
  options_json JSON NULL,
  reference_answer MEDIUMTEXT NOT NULL,
  explanation TEXT NULL,
  source_label VARCHAR(500) NOT NULL,
  source_url VARCHAR(2000) NULL,
  reading_url VARCHAR(2000) NULL,
  max_points INT NOT NULL,
  answer MEDIUMTEXT NOT NULL,
  revision INT NOT NULL DEFAULT 0,
  grade_status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
  points INT NULL,
  feedback TEXT NULL,
  claim_token CHAR(36) NULL,
  claim_until BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (session_id,ordinal_no),
  UNIQUE KEY uk_mock_drawn (session_id,bank_id),
  KEY idx_mock_grading (grade_status,claim_until),
  CONSTRAINT fk_mock_item_session FOREIGN KEY (session_id) REFERENCES mock_exam_session(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
