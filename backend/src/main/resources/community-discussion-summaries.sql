SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS community_discussion_summary (
  post_id BIGINT NOT NULL,
  state ENUM('PENDING','RUNNING','READY','FAILED') NOT NULL DEFAULT 'PENDING',
  next_attempt DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  lease_token CHAR(36) DEFAULT NULL,
  source_count INT NOT NULL DEFAULT 0,
  source_latest BIGINT NOT NULL DEFAULT 0,
  caption_hash CHAR(64) NOT NULL DEFAULT '',
  attempts TINYINT UNSIGNED NOT NULL DEFAULT 0,
  payload JSON DEFAULT NULL,
  search_text TEXT,
  point_count SMALLINT NOT NULL DEFAULT 0,
  generated_at DATETIME(6) DEFAULT NULL,
  PRIMARY KEY(post_id),
  KEY idx_discussion_work(state,next_attempt),
  KEY idx_discussion_ready(state,generated_at,post_id),
  FOREIGN KEY(post_id) REFERENCES community_post(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO community_discussion_summary(post_id)
SELECT c.post_id FROM community_post_comment c JOIN community_post p ON p.id=c.post_id
WHERE p.visibility='PUBLIC' GROUP BY c.post_id
HAVING COUNT(*)>=3 AND COUNT(DISTINCT c.author_id)>=2;
