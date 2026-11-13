-- 账号身份与项目素材授权的增量更新，可在同一数据库重复执行。
-- 先选择目标库；保留已有 staff 身份和历史共享的 creator_id，不加唯一约束。
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS role_application (
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

CREATE TABLE IF NOT EXISTS project_asset (
  project_id BIGINT UNSIGNED NOT NULL,
  asset_id BIGINT UNSIGNED NOT NULL,
  shared_by BIGINT UNSIGNED NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (project_id,asset_id),
  CONSTRAINT fk_project_asset_project FOREIGN KEY (project_id) REFERENCES project(id),
  CONSTRAINT fk_project_asset_asset FOREIGN KEY (asset_id) REFERENCES asset(id),
  CONSTRAINT fk_project_asset_sharer FOREIGN KEY (shared_by) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

START TRANSACTION;
UPDATE app_role SET name='项目统筹' WHERE code='PRODUCER';
UPDATE app_role SET name='审核' WHERE code='REVIEWER';

-- 仅转换旧 USER-only 账号；已经绑定档案时保留原档案，否则新建独立档案。
CREATE TEMPORARY TABLE identity_user_migration AS
SELECT u.id AS user_id,u.display_name,u.creator_id,CONCAT('member-',UUID()) AS creator_handle
FROM app_user u
WHERE EXISTS(SELECT 1 FROM user_role ur JOIN app_role r ON r.id=ur.role_id WHERE ur.user_id=u.id AND r.code='USER')
  AND NOT EXISTS(SELECT 1 FROM user_role ur JOIN app_role r ON r.id=ur.role_id WHERE ur.user_id=u.id AND r.code<>'USER');
INSERT INTO creator(name,handle,bio)
SELECT display_name,creator_handle,'普通用户注册创建的个人创作者档案' FROM identity_user_migration WHERE creator_id IS NULL;
UPDATE app_user u JOIN identity_user_migration m ON m.user_id=u.id
JOIN creator c ON c.handle=m.creator_handle
SET u.creator_id=c.id WHERE m.creator_id IS NULL;
INSERT INTO user_role(user_id,role_id)
SELECT m.user_id,r.id FROM identity_user_migration m JOIN app_role r ON r.code='CREATOR'
WHERE NOT EXISTS(SELECT 1 FROM user_role ur WHERE ur.user_id=m.user_id AND ur.role_id=r.id);
DELETE ur FROM user_role ur JOIN app_role r ON r.id=ur.role_id
JOIN identity_user_migration m ON m.user_id=ur.user_id WHERE r.code='USER';
DROP TEMPORARY TABLE identity_user_migration;

-- 把现有作品引用转换为初始项目授权；后续新增和撤回授权由业务接口管理。
INSERT INTO project_asset(project_id,asset_id,shared_by)
SELECT w.project_id,wa.asset_id,MIN(w.created_by)
FROM work_asset wa JOIN work w ON w.id=wa.work_id
WHERE NOT EXISTS(SELECT 1 FROM project_asset pa WHERE pa.project_id=w.project_id AND pa.asset_id=wa.asset_id)
GROUP BY w.project_id,wa.asset_id;
COMMIT;

-- 主库应用角色的新表权限见 security.sql，增量执行后由管理员重新应用该授权脚本。
SELECT COUNT(*) AS remaining_legacy_user_accounts
FROM user_role ur JOIN app_role r ON r.id=ur.role_id WHERE r.code='USER';
SELECT creator_id,COUNT(*) AS linked_accounts FROM app_user WHERE creator_id IS NOT NULL GROUP BY creator_id HAVING COUNT(*)>1;
