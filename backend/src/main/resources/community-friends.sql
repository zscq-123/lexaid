CREATE TABLE IF NOT EXISTS community_friend_pair (
  user_low     BIGINT       NOT NULL,
  user_high    BIGINT       NOT NULL,
  request_id   CHAR(36)     CHARACTER SET ascii NOT NULL,
  requester_id BIGINT       NOT NULL,
  state        ENUM('PENDING','ACCEPTED','DECLINED') NOT NULL,
  message      VARCHAR(120) NOT NULL DEFAULT '',
  requested_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (user_low, user_high),
  UNIQUE KEY uk_friend_request (request_id),
  KEY idx_friend_high_state (user_high,state),
  CONSTRAINT fk_friend_low FOREIGN KEY (user_low) REFERENCES `user` (id),
  CONSTRAINT fk_friend_high FOREIGN KEY (user_high) REFERENCES `user` (id),
  CONSTRAINT chk_friend_pair CHECK (user_low < user_high AND requester_id IN (user_low, user_high))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
