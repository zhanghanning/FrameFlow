-- 业务数据库对象。先运行 schema.sql，再运行本文件；部署时由数据库管理员执行。
-- 视图各自先聚合再连接，避免作品、收入、成本的一对多连接放大金额。
CREATE OR REPLACE SQL SECURITY DEFINER VIEW v_project_finance AS
SELECT p.id AS project_id,p.name AS project_name,p.budget,p.status,
       COALESCE(r.total_income,0) AS total_income,
       COALESCE(c.total_cost,0) AS total_cost,
       COALESCE(r.total_income,0)-COALESCE(c.total_cost,0) AS net_income
FROM project p
LEFT JOIN (
  SELECT w.project_id,SUM(r.net_amount) AS total_income
  FROM revenue r JOIN publication pub ON pub.id=r.publication_id JOIN work w ON w.id=pub.work_id
  WHERE r.status='CONFIRMED' GROUP BY w.project_id
) r ON r.project_id=p.id
LEFT JOIN (
  SELECT project_id,SUM(amount) AS total_cost FROM project_cost WHERE status='APPROVED' GROUP BY project_id
) c ON c.project_id=p.id;

-- 审批产生应付，付款登记抵扣应付；负向结算自然抵扣以后期间的收入。
CREATE OR REPLACE SQL SECURITY DEFINER VIEW v_creator_balance AS
SELECT c.id AS creator_id,c.name AS creator_name,
       COALESCE(e.earned,0) AS earned,COALESCE(p.paid,0) AS paid,
       COALESCE(e.earned,0)-COALESCE(p.paid,0) AS balance
FROM creator c
LEFT JOIN (
  SELECT l.creator_id,SUM(l.amount) AS earned FROM settlement_line l
  JOIN settlement s ON s.id=l.settlement_id WHERE s.status='APPROVED' GROUP BY l.creator_id
) e ON e.creator_id=c.id
LEFT JOIN (SELECT creator_id,SUM(amount) AS paid FROM payment GROUP BY creator_id) p ON p.creator_id=c.id;

-- 日期随查询当天变化，授权到期不需要定时修改素材状态。
CREATE OR REPLACE SQL SECURITY DEFINER VIEW v_asset_license_expiry AS
SELECT a.id AS asset_id,a.name AS asset_name,a.creator_id,c.name AS creator_name,
       a.license_type,a.license_start,a.license_end,
       IF(a.all_platforms,'ALL',(
         SELECT GROUP_CONCAT(p.code ORDER BY p.id SEPARATOR ',')
         FROM asset_platform ap JOIN platform p ON p.id=ap.platform_id WHERE ap.asset_id=a.id
       )) AS allowed_platforms,
       DATEDIFF(a.license_end,CURRENT_DATE()) AS days_remaining,
       CASE WHEN a.license_end<CURRENT_DATE() THEN 'EXPIRED' ELSE 'EXPIRING' END AS license_status
FROM asset a JOIN creator c ON c.id=a.creator_id
WHERE a.status='ACTIVE' AND a.license_end IS NOT NULL AND a.license_end<=DATE_ADD(CURRENT_DATE(),INTERVAL 30 DAY);

DELIMITER $$

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

-- 激活时检查分账总比例与日期重叠；生效后修改业务条款需要新建协议。
-- 应用层激活时锁 project 行，保证同项目两个并发激活操作仍按顺序检查。
CREATE TRIGGER tr_contract_before_update BEFORE UPDATE ON contract FOR EACH ROW
BEGIN
  DECLARE v_rate_total DECIMAL(11,4);
  DECLARE v_participants INT;
  IF OLD.status='ACTIVE' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='生效协议已冻结，请创建新的分账协议';
  END IF;
  IF NEW.status='ACTIVE' THEN
    SELECT COUNT(*),COALESCE(SUM(rate),0) INTO v_participants,v_rate_total
    FROM contract_participant WHERE contract_id=OLD.id;
    IF v_participants=0 OR v_rate_total+NEW.studio_rate<>100 THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='分账参与人至少一位，所有比例之和必须为100%';
    END IF;
    IF EXISTS(SELECT 1 FROM contract c WHERE c.project_id=NEW.project_id AND c.id<>OLD.id AND c.status='ACTIVE'
      AND c.start_date<=NEW.end_date AND c.end_date>=NEW.start_date) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='同项目的生效协议日期范围重叠';
    END IF;
  END IF;
END$$

CREATE TRIGGER tr_contract_participant_insert BEFORE INSERT ON contract_participant FOR EACH ROW
BEGIN
  IF (SELECT status FROM contract WHERE id=NEW.contract_id)='ACTIVE' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='生效协议的分账参与人已冻结';
  END IF;
END$$

CREATE TRIGGER tr_contract_participant_update BEFORE UPDATE ON contract_participant FOR EACH ROW
BEGIN
  IF EXISTS(SELECT 1 FROM contract WHERE id IN(OLD.contract_id,NEW.contract_id) AND status='ACTIVE') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='生效协议的分账参与人已冻结';
  END IF;
END$$

CREATE TRIGGER tr_contract_participant_delete BEFORE DELETE ON contract_participant FOR EACH ROW
BEGIN
  IF (SELECT status FROM contract WHERE id=OLD.contract_id)='ACTIVE' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='生效协议的分账参与人已冻结';
  END IF;
END$$

-- 确认后的收入保留原始事实；退款单单独记录，保留原收入及原协议引用。
CREATE TRIGGER tr_revenue_before_update BEFORE UPDATE ON revenue FOR EACH ROW
BEGIN
  IF OLD.status='CONFIRMED' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='已确认收入已冻结，请通过退款流水调整';
  END IF;
END$$

CREATE TRIGGER tr_revenue_before_delete BEFORE DELETE ON revenue FOR EACH ROW
BEGIN
  IF OLD.status='CONFIRMED' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='已确认收入必须保留';
  END IF;
END$$

CREATE TRIGGER tr_cost_before_update BEFORE UPDATE ON project_cost FOR EACH ROW
BEGIN
  IF EXISTS(SELECT 1 FROM settlement_cost WHERE cost_id=OLD.id) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='已纳入结算的成本已冻结';
  END IF;
END$$

-- 状态流转以及提交后的金额快照由数据库直接约束。
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

CREATE TRIGGER tr_settlement_after_insert AFTER INSERT ON settlement FOR EACH ROW
BEGIN
  INSERT INTO settlement_history(settlement_id,from_status,to_status,actor_id,reason)
  VALUES(NEW.id,NULL,NEW.status,NEW.last_actor_id,'生成结算草稿');
END$$

CREATE TRIGGER tr_settlement_after_update AFTER UPDATE ON settlement FOR EACH ROW
BEGIN
  IF NEW.status<>OLD.status THEN
    INSERT INTO settlement_history(settlement_id,from_status,to_status,actor_id,reason)
    VALUES(NEW.id,OLD.status,NEW.status,NEW.last_actor_id,CASE WHEN NEW.status='DRAFT' THEN NEW.reject_reason WHEN NEW.status='CANCELLED' THEN NEW.cancel_reason ELSE NULL END);
  END IF;
END$$

CREATE TRIGGER tr_settlement_line_before_update BEFORE UPDATE ON settlement_line FOR EACH ROW
BEGIN
  IF (SELECT status FROM settlement WHERE id=OLD.settlement_id)<>'DRAFT' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='已提交结算的分账明细已冻结';
  END IF;
END$$

CREATE TRIGGER tr_settlement_line_before_delete BEFORE DELETE ON settlement_line FOR EACH ROW
BEGIN
  IF (SELECT status FROM settlement WHERE id=OLD.settlement_id)<>'DRAFT' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='已提交结算的分账明细已冻结';
  END IF;
END$$

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
