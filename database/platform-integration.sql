-- 平台账号授权与作品同步；保留现有账号、项目、发布与财务记录，可重复执行。
SET NAMES utf8mb4;
-- 平台原始标识区分大小写，例如 YouTube channel/video ID。
ALTER TABLE platform_account MODIFY external_id VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL;

CREATE TABLE IF NOT EXISTS platform_connection (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  platform_account_id BIGINT UNSIGNED NOT NULL UNIQUE,
  authorized_by BIGINT UNSIGNED NOT NULL,
  access_token_encrypted TEXT,
  refresh_token_encrypted TEXT,
  token_expires_at DATETIME,
  refresh_expires_at DATETIME,
  granted_scopes TEXT,
  status VARCHAR(16) NOT NULL DEFAULT 'CONNECTED',
  last_synced_at DATETIME,
  last_error VARCHAR(1000),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (platform_account_id) REFERENCES platform_account(id),
  FOREIGN KEY (authorized_by) REFERENCES app_user(id),
  CONSTRAINT ck_platform_connection_status CHECK (status IN ('CONNECTED','EXPIRED','DISCONNECTED','ERROR'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS oauth_attempt (
  state VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
  platform_id BIGINT UNSIGNED NOT NULL,
  user_id BIGINT UNSIGNED NOT NULL,
  creator_id BIGINT UNSIGNED NOT NULL,
  code_verifier VARCHAR(128),
  expires_at DATETIME NOT NULL,
  consumed_at DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (platform_id) REFERENCES platform(id),
  FOREIGN KEY (user_id) REFERENCES app_user(id),
  FOREIGN KEY (creator_id) REFERENCES creator(id),
  INDEX ix_oauth_attempt_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS platform_content (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  connection_id BIGINT UNSIGNED NOT NULL,
  external_id VARCHAR(160) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  title VARCHAR(500) NOT NULL,
  description TEXT,
  cover_url VARCHAR(2000),
  permalink VARCHAR(2000),
  media_type VARCHAR(32) NOT NULL,
  published_at DATETIME,
  view_count BIGINT UNSIGNED,
  like_count BIGINT UNSIGNED,
  comment_count BIGINT UNSIGNED,
  last_synced_at DATETIME NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (connection_id) REFERENCES platform_connection(id),
  UNIQUE KEY uk_platform_content_remote (connection_id,external_id),
  INDEX ix_platform_content_published (connection_id,published_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS platform_sync_job (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  connection_id BIGINT UNSIGNED NOT NULL,
  triggered_by BIGINT UNSIGNED NOT NULL,
  status VARCHAR(16) NOT NULL,
  synced_count INT UNSIGNED NOT NULL DEFAULT 0,
  started_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  finished_at DATETIME,
  error_message VARCHAR(1000),
  FOREIGN KEY (connection_id) REFERENCES platform_connection(id),
  FOREIGN KEY (triggered_by) REFERENCES app_user(id),
  CONSTRAINT ck_platform_sync_job_status CHECK (status IN ('RUNNING','SUCCEEDED','FAILED')),
  INDEX ix_platform_sync_job_started (connection_id,started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO platform(code,name) VALUES ('DOUYIN','抖音'),('BILIBILI','哔哩哔哩'),('XIAOHONGSHU','小红书'),('KUAISHOU','快手'),('YOUTUBE','YouTube'),('INSTAGRAM','Instagram') ON DUPLICATE KEY UPDATE name=VALUES(name);
