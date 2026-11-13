-- 结算草稿编辑、重算和撤销增量；先选择数据库，可重复执行。
SET NAMES utf8mb4;
CREATE TABLE IF NOT EXISTS settlement_revision (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  settlement_id BIGINT UNSIGNED NOT NULL,
  action VARCHAR(32) NOT NULL,
  reason VARCHAR(1000) NOT NULL,
  actor_id BIGINT UNSIGNED NOT NULL,
  snapshot_json JSON NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (settlement_id) REFERENCES settlement(id),
  FOREIGN KEY (actor_id) REFERENCES app_user(id),
  CONSTRAINT ck_settlement_revision_action CHECK (action IN ('EDIT','RECALCULATE','CANCEL')),
  INDEX ix_settlement_revision (settlement_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

DELIMITER $$
DROP PROCEDURE IF EXISTS sp_migrate_settlement_workflow$$
CREATE PROCEDURE sp_migrate_settlement_workflow()
BEGIN
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='settlement' AND column_name='cancel_reason') THEN
    ALTER TABLE settlement ADD COLUMN cancel_reason VARCHAR(1000),
      ADD COLUMN cancelled_by BIGINT UNSIGNED,ADD COLUMN cancelled_at DATETIME,
      ADD CONSTRAINT fk_settlement_cancelled_by FOREIGN KEY (cancelled_by) REFERENCES app_user(id);
  END IF;
  ALTER TABLE settlement DROP CHECK ck_settlement_status,DROP CHECK ck_settlement_submit,
    ADD CONSTRAINT ck_settlement_status CHECK (status IN ('DRAFT','SUBMITTED','APPROVED','CANCELLED')),
    ADD CONSTRAINT ck_settlement_submit CHECK (status IN ('DRAFT','CANCELLED') OR (submitted_by IS NOT NULL AND submitted_at IS NOT NULL));
  IF NOT EXISTS(SELECT 1 FROM information_schema.table_constraints WHERE table_schema=DATABASE() AND table_name='settlement' AND constraint_name='ck_settlement_cancel') THEN
    ALTER TABLE settlement ADD CONSTRAINT ck_settlement_cancel CHECK (status<>'CANCELLED' OR (cancel_reason IS NOT NULL AND cancelled_by IS NOT NULL AND cancelled_at IS NOT NULL));
  END IF;
END$$
CALL sp_migrate_settlement_workflow()$$
DROP PROCEDURE sp_migrate_settlement_workflow$$
DROP PROCEDURE IF EXISTS sp_generate_settlement$$
DROP PROCEDURE IF EXISTS sp_calculate_settlement$$
-- 金额计算只有此过程一处；调用方持有合同与结算行锁，并统一提交或回滚。
CREATE PROCEDURE sp_calculate_settlement(
  IN p_settlement_id BIGINT,IN p_contract_id BIGINT,IN p_start DATE,IN p_end DATE,IN p_actor BIGINT
)
SQL SECURITY INVOKER
MODIFIES SQL DATA
BEGIN
  DECLARE v_project_id BIGINT UNSIGNED DEFAULT NULL;
  DECLARE v_studio_rate DECIMAL(7,4);
  DECLARE v_status VARCHAR(16);
  DECLARE v_settlement_status VARCHAR(16) DEFAULT NULL;
  DECLARE v_income DECIMAL(14,2) DEFAULT 0;
  DECLARE v_cost DECIMAL(14,2) DEFAULT 0;
  DECLARE v_basis DECIMAL(14,2) DEFAULT 0;
  DECLARE v_creator_total DECIMAL(14,2) DEFAULT 0;
  DECLARE v_revenue_count INT DEFAULT 0;
  DECLARE v_cost_count INT DEFAULT 0;

  IF p_start IS NULL OR p_end IS NULL OR p_start>p_end THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='结算期间无效';
  END IF;
  SELECT project_id,studio_rate,status INTO v_project_id,v_studio_rate,v_status
  FROM contract WHERE id=p_contract_id FOR UPDATE;
  IF v_project_id IS NULL OR v_status<>'ACTIVE' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='请选择已生效的分账协议';
  END IF;
  SELECT status INTO v_settlement_status FROM settlement WHERE id=p_settlement_id FOR UPDATE;
  IF v_settlement_status IS NULL OR v_settlement_status<>'DRAFT' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='只有草稿或退回的结算单可以重算';
  END IF;

  DELETE FROM settlement_revenue WHERE settlement_id=p_settlement_id;
  DELETE FROM settlement_cost WHERE settlement_id=p_settlement_id;
  DELETE FROM settlement_line WHERE settlement_id=p_settlement_id;

  INSERT INTO settlement_revenue(settlement_id,revenue_id,net_amount)
  SELECT p_settlement_id,r.id,r.net_amount FROM revenue r
  WHERE r.contract_id=p_contract_id AND r.status='CONFIRMED' AND r.revenue_date BETWEEN p_start AND p_end
    AND NOT EXISTS(SELECT 1 FROM settlement_revenue sr WHERE sr.revenue_id=r.id)
  ORDER BY r.id;
  SET v_revenue_count=ROW_COUNT();

  INSERT INTO settlement_cost(settlement_id,cost_id,amount)
  SELECT p_settlement_id,c.id,c.amount FROM project_cost c
  WHERE c.contract_id=p_contract_id AND c.status='APPROVED' AND c.cost_date BETWEEN p_start AND p_end
    AND NOT EXISTS(SELECT 1 FROM settlement_cost sc WHERE sc.cost_id=c.id)
  ORDER BY c.id;
  SET v_cost_count=ROW_COUNT();

  IF v_revenue_count+v_cost_count=0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='该协议在所选期间没有待结算收入或成本';
  END IF;
  SELECT COALESCE(SUM(net_amount),0) INTO v_income FROM settlement_revenue WHERE settlement_id=p_settlement_id;
  SELECT COALESCE(SUM(amount),0) INTO v_cost FROM settlement_cost WHERE settlement_id=p_settlement_id;
  SET v_basis=v_income-v_cost;

  INSERT INTO settlement_line(settlement_id,creator_id,rate,amount)
  SELECT p_settlement_id,creator_id,rate,ROUND(v_basis*rate/100,2)
  FROM contract_participant WHERE contract_id=p_contract_id ORDER BY creator_id;
  SELECT COALESCE(SUM(amount),0) INTO v_creator_total FROM settlement_line WHERE settlement_id=p_settlement_id;

  UPDATE settlement SET contract_id=p_contract_id,project_id=v_project_id,period_start=p_start,period_end=p_end,
    total_income=v_income,total_cost=v_cost,distributable_amount=v_basis,studio_rate=v_studio_rate,
    studio_amount=v_basis-v_creator_total,submitted_by=NULL,submitted_at=NULL,approved_by=NULL,approved_at=NULL,
    reject_reason=NULL,last_actor_id=p_actor,updated_at=NOW()
  WHERE id=p_settlement_id;
END$$

-- 新建与重算共用上面的分配与计算过程；单号只在首次生成时创建。
CREATE PROCEDURE sp_generate_settlement(
  IN p_contract_id BIGINT,IN p_start DATE,IN p_end DATE,IN p_actor BIGINT,OUT p_settlement_id BIGINT
)
SQL SECURITY INVOKER
MODIFIES SQL DATA
BEGIN
  DECLARE v_project_id BIGINT UNSIGNED DEFAULT NULL;
  DECLARE v_studio_rate DECIMAL(7,4);
  DECLARE v_status VARCHAR(16);
  SELECT project_id,studio_rate,status INTO v_project_id,v_studio_rate,v_status
  FROM contract WHERE id=p_contract_id FOR UPDATE;
  IF v_project_id IS NULL OR v_status<>'ACTIVE' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='请选择已生效的分账协议';
  END IF;
  IF p_start IS NULL OR p_end IS NULL OR p_start>p_end THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='结算期间无效';
  END IF;
  INSERT INTO settlement(settlement_no,contract_id,project_id,period_start,period_end,studio_rate,created_by,last_actor_id)
  VALUES(CONCAT('JS-',UUID_SHORT()),p_contract_id,v_project_id,p_start,p_end,v_studio_rate,p_actor,p_actor);
  SET p_settlement_id=LAST_INSERT_ID();
  UPDATE settlement SET settlement_no=CONCAT('JS-',DATE_FORMAT(CURRENT_DATE(),'%Y%m%d'),'-',LPAD(p_settlement_id,6,'0'))
  WHERE id=p_settlement_id;
  CALL sp_calculate_settlement(p_settlement_id,p_contract_id,p_start,p_end,p_actor);
END$$

DROP TRIGGER IF EXISTS tr_settlement_before_update$$
CREATE TRIGGER tr_settlement_before_update BEFORE UPDATE ON settlement FOR EACH ROW
BEGIN
  IF OLD.status IN ('APPROVED','CANCELLED') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='已批准或已撤销结算已冻结';
  END IF;
  IF NEW.status<>OLD.status AND NOT (
    (OLD.status='DRAFT' AND NEW.status IN ('SUBMITTED','CANCELLED')) OR
    (OLD.status='SUBMITTED' AND NEW.status IN('DRAFT','APPROVED'))
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='结算状态流转无效';
  END IF;
  IF OLD.status='SUBMITTED' AND (
    NEW.contract_id<>OLD.contract_id OR NEW.project_id<>OLD.project_id OR
    NEW.period_start<>OLD.period_start OR NEW.period_end<>OLD.period_end OR
    NEW.total_income<>OLD.total_income OR NEW.total_cost<>OLD.total_cost OR
    NEW.distributable_amount<>OLD.distributable_amount OR NEW.studio_rate<>OLD.studio_rate OR NEW.studio_amount<>OLD.studio_amount
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='已提交结算的金额和依据已冻结';
  END IF;
  IF NEW.status='APPROVED' AND (NEW.approved_by IS NULL OR NEW.approved_by=NEW.submitted_by) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='结算提交人和审核人必须不同';
  END IF;
END$$

DROP TRIGGER IF EXISTS tr_settlement_after_update$$
CREATE TRIGGER tr_settlement_after_update AFTER UPDATE ON settlement FOR EACH ROW
BEGIN
  IF NEW.status<>OLD.status THEN
    INSERT INTO settlement_history(settlement_id,from_status,to_status,actor_id,reason)
    VALUES(NEW.id,OLD.status,NEW.status,NEW.last_actor_id,CASE WHEN NEW.status='DRAFT' THEN NEW.reject_reason WHEN NEW.status='CANCELLED' THEN NEW.cancel_reason ELSE NULL END);
  END IF;
END$$

DROP TRIGGER IF EXISTS tr_settlement_revenue_before_delete$$
DROP TRIGGER IF EXISTS tr_settlement_cost_before_delete$$
CREATE TRIGGER tr_settlement_revenue_before_delete BEFORE DELETE ON settlement_revenue FOR EACH ROW
BEGIN
  IF (SELECT status FROM settlement WHERE id=OLD.settlement_id)<>'DRAFT' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='只有草稿结算可以释放收入来源';
  END IF;
END$$

CREATE TRIGGER tr_settlement_cost_before_delete BEFORE DELETE ON settlement_cost FOR EACH ROW
BEGIN
  IF (SELECT status FROM settlement WHERE id=OLD.settlement_id)<>'DRAFT' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='只有草稿结算可以释放成本来源';
  END IF;
END$$

DELIMITER ;
-- 应用权限变更见 security.sql，执行增量后重新应用授权。
