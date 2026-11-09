CREATE TABLE IF NOT EXISTS wrong_question_practice (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  source_question_id BIGINT NOT NULL,
  source_record_id BIGINT NOT NULL,
  round_no INT NOT NULL,
  exercise JSON NOT NULL,
  selected CHAR(1) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  answered_at DATETIME DEFAULT NULL,
  UNIQUE KEY uk_practice_round (user_id, source_record_id, round_no),
  FOREIGN KEY (user_id) REFERENCES `user`(id) ON DELETE CASCADE,
  FOREIGN KEY (source_record_id) REFERENCES answer_record(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
