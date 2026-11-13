-- 帧序 FrameFlow：业务表。由部署脚本提前创建并选择 frameflow 数据库。
-- 金额单位为人民币元；分账比例单位为百分比；所有业务表使用 InnoDB。
SET NAMES utf8mb4;

CREATE TABLE creator (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(80) NOT NULL,
  handle VARCHAR(80) NOT NULL UNIQUE,
  email VARCHAR(160), phone VARCHAR(32), bio VARCHAR(1000),
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT ck_creator_status CHECK (status IN ('ACTIVE','INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE app_user (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL UNIQUE,
  password_digest VARCHAR(100) NOT NULL,
  display_name VARCHAR(80) NOT NULL,
  creator_id BIGINT UNSIGNED,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_user_creator FOREIGN KEY (creator_id) REFERENCES creator(id),
  CONSTRAINT ck_user_status CHECK (status IN ('ACTIVE','INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE app_role (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(32) NOT NULL UNIQUE,
  name VARCHAR(80) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_role (
  user_id BIGINT UNSIGNED NOT NULL,
  role_id BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (user_id,role_id),
  FOREIGN KEY (user_id) REFERENCES app_user(id),
  FOREIGN KEY (role_id) REFERENCES app_role(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_session (
  token VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
  user_id BIGINT UNSIGNED NOT NULL,
  expires_at DATETIME NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES app_user(id),
  INDEX ix_session_user (user_id),
  INDEX ix_session_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 附加岗位申请保留审批记录；每个账号的同一岗位只允许一条待办。
CREATE TABLE role_application (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  requested_role VARCHAR(32) NOT NULL,
  reason VARCHAR(1000) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  review_note VARCHAR(1000),
  reviewed_by BIGINT UNSIGNED,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  reviewed_at DATETIME,
  pending_role VARCHAR(32) GENERATED ALWAYS AS (CASE WHEN status='PENDING' THEN requested_role ELSE NULL END) STORED,
  CONSTRAINT fk_role_application_user FOREIGN KEY (user_id) REFERENCES app_user(id),
  CONSTRAINT fk_role_application_role FOREIGN KEY (requested_role) REFERENCES app_role(code),
  CONSTRAINT fk_role_application_reviewer FOREIGN KEY (reviewed_by) REFERENCES app_user(id),
  CONSTRAINT ck_role_application_role CHECK (requested_role IN ('ADMIN','PRODUCER','FINANCE','REVIEWER')),
  CONSTRAINT ck_role_application_status CHECK (status IN ('PENDING','APPROVED','REJECTED')),
  CONSTRAINT ck_role_application_reviewer CHECK (reviewed_by IS NULL OR reviewed_by<>user_id),
  CONSTRAINT ck_role_application_review CHECK (
    (status='PENDING' AND reviewed_by IS NULL AND reviewed_at IS NULL) OR
    (status IN ('APPROVED','REJECTED') AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)
  ),
  UNIQUE KEY uk_role_application_pending (user_id,pending_role),
  INDEX ix_role_application_queue (status,created_at),
  INDEX ix_role_application_user (user_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE platform (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(32) NOT NULL UNIQUE,
  name VARCHAR(80) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE platform_account (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  platform_id BIGINT UNSIGNED NOT NULL,
  creator_id BIGINT UNSIGNED NOT NULL,
  name VARCHAR(100) NOT NULL,
  external_id VARCHAR(128) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (platform_id) REFERENCES platform(id),
  FOREIGN KEY (creator_id) REFERENCES creator(id),
  UNIQUE KEY uk_platform_account (platform_id,external_id),
  CONSTRAINT ck_account_status CHECK (status IN ('ACTIVE','INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE project (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(160) NOT NULL,
  client_name VARCHAR(160), description TEXT,
  budget DECIMAL(14,2) NOT NULL DEFAULT 0,
  start_date DATE NOT NULL, due_date DATE NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PLANNING',
  owner_id BIGINT UNSIGNED NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (owner_id) REFERENCES app_user(id),
  CONSTRAINT ck_project_dates CHECK (due_date >= start_date),
  CONSTRAINT ck_project_budget CHECK (budget >= 0),
  CONSTRAINT ck_project_status CHECK (status IN ('PLANNING','PRODUCING','REVIEW','COMPLETED','ARCHIVED')),
  INDEX ix_project_status (status,due_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE project_member (
  project_id BIGINT UNSIGNED NOT NULL, creator_id BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (project_id,creator_id),
  FOREIGN KEY (project_id) REFERENCES project(id),
  FOREIGN KEY (creator_id) REFERENCES creator(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE asset (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(160) NOT NULL,
  kind VARCHAR(16) NOT NULL,
  creator_id BIGINT UNSIGNED NOT NULL,
  original_filename VARCHAR(255) NOT NULL,
  storage_path VARCHAR(500) NOT NULL,
  mime_type VARCHAR(128) NOT NULL,
  size_bytes BIGINT UNSIGNED NOT NULL,
  license_type VARCHAR(16) NOT NULL,
  license_start DATE NOT NULL,
  license_end DATE,
  all_platforms BOOLEAN NOT NULL DEFAULT TRUE,
  description TEXT,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  uploaded_by BIGINT UNSIGNED NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (creator_id) REFERENCES creator(id),
  FOREIGN KEY (uploaded_by) REFERENCES app_user(id),
  CONSTRAINT ck_asset_kind CHECK (kind IN ('VIDEO','IMAGE','AUDIO','DOCUMENT')),
  CONSTRAINT ck_asset_license CHECK (license_type IN ('ORIGINAL','LICENSED')),
  CONSTRAINT ck_asset_dates CHECK (license_end IS NULL OR license_end >= license_start),
  CONSTRAINT ck_asset_status CHECK (status IN ('ACTIVE','ARCHIVED')),
  CONSTRAINT ck_asset_all_platforms CHECK (all_platforms IN (FALSE,TRUE)),
  INDEX ix_asset_creator (creator_id,status),
  INDEX ix_asset_expiry (license_end,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 素材所有者授权项目使用，作品引用与项目授权分别记录。
CREATE TABLE project_asset (
  project_id BIGINT UNSIGNED NOT NULL,
  asset_id BIGINT UNSIGNED NOT NULL,
  shared_by BIGINT UNSIGNED NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (project_id,asset_id),
  CONSTRAINT fk_project_asset_project FOREIGN KEY (project_id) REFERENCES project(id),
  CONSTRAINT fk_project_asset_asset FOREIGN KEY (asset_id) REFERENCES asset(id),
  CONSTRAINT fk_project_asset_sharer FOREIGN KEY (shared_by) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 素材授权平台是多对多关系；all_platforms=true 表示授权范围不限制平台。
CREATE TABLE asset_platform (
  asset_id BIGINT UNSIGNED NOT NULL,
  platform_id BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (asset_id,platform_id),
  FOREIGN KEY (asset_id) REFERENCES asset(id),
  FOREIGN KEY (platform_id) REFERENCES platform(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tag (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(40) NOT NULL UNIQUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE asset_tag (
  asset_id BIGINT UNSIGNED NOT NULL, tag_id BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (asset_id,tag_id),
  FOREIGN KEY (asset_id) REFERENCES asset(id),
  FOREIGN KEY (tag_id) REFERENCES tag(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE work (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT UNSIGNED NOT NULL,
  title VARCHAR(200) NOT NULL, description TEXT,
  status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
  created_by BIGINT UNSIGNED NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (project_id) REFERENCES project(id),
  FOREIGN KEY (created_by) REFERENCES app_user(id),
  CONSTRAINT ck_work_status CHECK (status IN ('DRAFT','EDITING','REVIEW','READY')),
  INDEX ix_work_project (project_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE work_asset (
  work_id BIGINT UNSIGNED NOT NULL, asset_id BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (work_id,asset_id),
  FOREIGN KEY (work_id) REFERENCES work(id),
  FOREIGN KEY (asset_id) REFERENCES asset(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE publication (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  work_id BIGINT UNSIGNED NOT NULL,
  platform_account_id BIGINT UNSIGNED NOT NULL,
  external_id VARCHAR(128) NOT NULL,
  published_at DATETIME NOT NULL,
  url VARCHAR(1000),
  view_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
  created_by BIGINT UNSIGNED NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (work_id) REFERENCES work(id),
  FOREIGN KEY (platform_account_id) REFERENCES platform_account(id),
  FOREIGN KEY (created_by) REFERENCES app_user(id),
  UNIQUE KEY uk_publication_external (platform_account_id,external_id),
  INDEX ix_publication_date (published_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE contract (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT UNSIGNED NOT NULL,
  name VARCHAR(160) NOT NULL,
  start_date DATE NOT NULL, end_date DATE NOT NULL,
  studio_rate DECIMAL(7,4) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
  created_by BIGINT UNSIGNED NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (project_id) REFERENCES project(id),
  FOREIGN KEY (created_by) REFERENCES app_user(id),
  CONSTRAINT ck_contract_dates CHECK (end_date >= start_date),
  CONSTRAINT ck_contract_rate CHECK (studio_rate BETWEEN 0 AND 100),
  CONSTRAINT ck_contract_status CHECK (status IN ('DRAFT','ACTIVE')),
  INDEX ix_contract_project_dates (project_id,status,start_date,end_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE contract_participant (
  contract_id BIGINT UNSIGNED NOT NULL,
  creator_id BIGINT UNSIGNED NOT NULL,
  rate DECIMAL(7,4) NOT NULL,
  PRIMARY KEY (contract_id,creator_id),
  FOREIGN KEY (contract_id) REFERENCES contract(id),
  FOREIGN KEY (creator_id) REFERENCES creator(id),
  CONSTRAINT ck_participant_rate CHECK (rate > 0 AND rate <= 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE import_batch (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  filename VARCHAR(255) NOT NULL,
  total_rows INT UNSIGNED NOT NULL,
  inserted_rows INT UNSIGNED NOT NULL,
  created_by BIGINT UNSIGNED NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (created_by) REFERENCES app_user(id),
  CONSTRAINT ck_import_counts CHECK (inserted_rows <= total_rows)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE revenue (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  publication_id BIGINT UNSIGNED NOT NULL,
  platform_account_id BIGINT UNSIGNED NOT NULL,
  source_ref VARCHAR(128) NOT NULL,
  revenue_date DATE NOT NULL,
  kind VARCHAR(16) NOT NULL DEFAULT 'INCOME',
  gross_amount DECIMAL(14,2) NOT NULL,
  fee_amount DECIMAL(14,2) NOT NULL DEFAULT 0,
  net_amount DECIMAL(14,2) GENERATED ALWAYS AS (CASE WHEN kind='REFUND' THEN -gross_amount ELSE gross_amount-fee_amount END) STORED,
  original_revenue_id BIGINT UNSIGNED,
  contract_id BIGINT UNSIGNED,
  status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
  note VARCHAR(1000),
  import_batch_id BIGINT UNSIGNED,
  confirmed_by BIGINT UNSIGNED,
  confirmed_at DATETIME,
  created_by BIGINT UNSIGNED NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (publication_id) REFERENCES publication(id),
  FOREIGN KEY (platform_account_id) REFERENCES platform_account(id),
  FOREIGN KEY (original_revenue_id) REFERENCES revenue(id),
  FOREIGN KEY (contract_id) REFERENCES contract(id),
  FOREIGN KEY (import_batch_id) REFERENCES import_batch(id),
  FOREIGN KEY (confirmed_by) REFERENCES app_user(id),
  FOREIGN KEY (created_by) REFERENCES app_user(id),
  UNIQUE KEY uk_revenue_source (platform_account_id,source_ref,kind),
  CONSTRAINT ck_revenue_kind CHECK (kind IN ('INCOME','REFUND')),
  CONSTRAINT ck_revenue_amount CHECK (gross_amount > 0 AND fee_amount >= 0 AND fee_amount <= gross_amount),
  CONSTRAINT ck_revenue_refund CHECK ((kind='INCOME' AND original_revenue_id IS NULL) OR (kind='REFUND' AND original_revenue_id IS NOT NULL AND fee_amount=0)),
  CONSTRAINT ck_revenue_status CHECK (status IN ('DRAFT','CONFIRMED')),
  CONSTRAINT ck_revenue_confirm CHECK (status='DRAFT' OR (contract_id IS NOT NULL AND confirmed_by IS NOT NULL AND confirmed_at IS NOT NULL)),
  INDEX ix_revenue_contract_period (contract_id,status,revenue_date),
  INDEX ix_revenue_period (revenue_date,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE project_cost (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT UNSIGNED NOT NULL,
  contract_id BIGINT UNSIGNED,
  category VARCHAR(64) NOT NULL,
  description VARCHAR(1000) NOT NULL,
  amount DECIMAL(14,2) NOT NULL,
  cost_date DATE NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
  approved_by BIGINT UNSIGNED, approved_at DATETIME,
  created_by BIGINT UNSIGNED NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (project_id) REFERENCES project(id),
  FOREIGN KEY (contract_id) REFERENCES contract(id),
  FOREIGN KEY (approved_by) REFERENCES app_user(id),
  FOREIGN KEY (created_by) REFERENCES app_user(id),
  CONSTRAINT ck_cost_amount CHECK (amount > 0),
  CONSTRAINT ck_cost_status CHECK (status IN ('DRAFT','APPROVED')),
  CONSTRAINT ck_cost_approval CHECK (status='DRAFT' OR (contract_id IS NOT NULL AND approved_by IS NOT NULL AND approved_at IS NOT NULL)),
  INDEX ix_cost_contract_period (contract_id,status,cost_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE settlement (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  settlement_no VARCHAR(64) NOT NULL UNIQUE,
  contract_id BIGINT UNSIGNED NOT NULL,
  project_id BIGINT UNSIGNED NOT NULL,
  period_start DATE NOT NULL, period_end DATE NOT NULL,
  total_income DECIMAL(14,2) NOT NULL DEFAULT 0,
  total_cost DECIMAL(14,2) NOT NULL DEFAULT 0,
  distributable_amount DECIMAL(14,2) NOT NULL DEFAULT 0,
  studio_rate DECIMAL(7,4) NOT NULL,
  studio_amount DECIMAL(14,2) NOT NULL DEFAULT 0,
  status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
  created_by BIGINT UNSIGNED NOT NULL,
  submitted_by BIGINT UNSIGNED, submitted_at DATETIME,
  approved_by BIGINT UNSIGNED, approved_at DATETIME,
  last_actor_id BIGINT UNSIGNED NOT NULL,
  reject_reason VARCHAR(1000),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (contract_id) REFERENCES contract(id),
  FOREIGN KEY (project_id) REFERENCES project(id),
  FOREIGN KEY (created_by) REFERENCES app_user(id),
  FOREIGN KEY (submitted_by) REFERENCES app_user(id),
  FOREIGN KEY (approved_by) REFERENCES app_user(id),
  FOREIGN KEY (last_actor_id) REFERENCES app_user(id),
  CONSTRAINT ck_settlement_period CHECK (period_end >= period_start),
  CONSTRAINT ck_settlement_rate CHECK (studio_rate BETWEEN 0 AND 100),
  CONSTRAINT ck_settlement_cost CHECK (total_cost >= 0),
  CONSTRAINT ck_settlement_status CHECK (status IN ('DRAFT','SUBMITTED','APPROVED')),
  CONSTRAINT ck_settlement_submit CHECK (status='DRAFT' OR (submitted_by IS NOT NULL AND submitted_at IS NOT NULL)),
  CONSTRAINT ck_settlement_approval CHECK (status<>'APPROVED' OR (approved_by IS NOT NULL AND approved_at IS NOT NULL AND approved_by<>submitted_by)),
  INDEX ix_settlement_period (period_end,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE settlement_revenue (
  settlement_id BIGINT UNSIGNED NOT NULL,
  revenue_id BIGINT UNSIGNED NOT NULL UNIQUE,
  net_amount DECIMAL(14,2) NOT NULL,
  PRIMARY KEY (settlement_id,revenue_id),
  FOREIGN KEY (settlement_id) REFERENCES settlement(id),
  FOREIGN KEY (revenue_id) REFERENCES revenue(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE settlement_cost (
  settlement_id BIGINT UNSIGNED NOT NULL,
  cost_id BIGINT UNSIGNED NOT NULL UNIQUE,
  amount DECIMAL(14,2) NOT NULL,
  PRIMARY KEY (settlement_id,cost_id),
  FOREIGN KEY (settlement_id) REFERENCES settlement(id),
  FOREIGN KEY (cost_id) REFERENCES project_cost(id),
  CONSTRAINT ck_settlement_cost_amount CHECK (amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE settlement_line (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  settlement_id BIGINT UNSIGNED NOT NULL,
  creator_id BIGINT UNSIGNED NOT NULL,
  rate DECIMAL(7,4) NOT NULL,
  amount DECIMAL(14,2) NOT NULL,
  FOREIGN KEY (settlement_id) REFERENCES settlement(id),
  FOREIGN KEY (creator_id) REFERENCES creator(id),
  UNIQUE KEY uk_settlement_creator (settlement_id,creator_id),
  CONSTRAINT ck_settlement_line_rate CHECK (rate > 0 AND rate <= 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE payment (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  creator_id BIGINT UNSIGNED NOT NULL,
  amount DECIMAL(14,2) NOT NULL,
  paid_at DATETIME NOT NULL,
  reference VARCHAR(128) NOT NULL UNIQUE,
  note VARCHAR(1000),
  created_by BIGINT UNSIGNED NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (creator_id) REFERENCES creator(id),
  FOREIGN KEY (created_by) REFERENCES app_user(id),
  CONSTRAINT ck_payment_amount CHECK (amount > 0),
  INDEX ix_payment_creator (creator_id,paid_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE settlement_history (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  settlement_id BIGINT UNSIGNED NOT NULL,
  from_status VARCHAR(16), to_status VARCHAR(16) NOT NULL,
  actor_id BIGINT UNSIGNED NOT NULL,
  reason VARCHAR(1000),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (settlement_id) REFERENCES settlement(id),
  FOREIGN KEY (actor_id) REFERENCES app_user(id),
  INDEX ix_history_settlement (settlement_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE audit_log (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT UNSIGNED,
  action VARCHAR(80) NOT NULL,
  entity_type VARCHAR(80) NOT NULL,
  entity_id BIGINT UNSIGNED,
  detail TEXT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES app_user(id),
  INDEX ix_audit_entity (entity_type,entity_id),
  INDEX ix_audit_time (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 独立性能实验表：模拟数据与业务账务完全分开；组合索引在 performance.sql 中创建。
CREATE TABLE perf_revenue (
  id BIGINT UNSIGNED PRIMARY KEY,
  creator_id BIGINT UNSIGNED NOT NULL,
  platform_id BIGINT UNSIGNED NOT NULL,
  revenue_date DATE NOT NULL,
  status VARCHAR(16) NOT NULL,
  gross_amount DECIMAL(14,2) NOT NULL,
  fee_amount DECIMAL(14,2) NOT NULL,
  net_amount DECIMAL(14,2) GENERATED ALWAYS AS (gross_amount-fee_amount) STORED,
  source_ref VARCHAR(128) NOT NULL,
  CONSTRAINT ck_perf_status CHECK (status IN ('DRAFT','CONFIRMED')),
  CONSTRAINT ck_perf_amount CHECK (gross_amount > 0 AND fee_amount BETWEEN 0 AND gross_amount)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
