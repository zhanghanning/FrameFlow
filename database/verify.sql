-- 可复现的数据库业务验证。仅用于已加载 schema/objects/seed 的 frameflow_test 测试库。
-- 执行：mysql --force --table < database/verify.sql > docs/database-verification.txt 2>&1
-- 标为 EXPECTED 的语句应返回业务错误或唯一约束错误；--force 使后续验证继续执行。
-- 每组验证使用显式事务并回滚，保留种子业务数据。自增序列允许留下正常间隙。
SET NAMES utf8mb4;
USE frameflow_test;

SELECT '基线：余额及结算守恒' AS verification;
SELECT creator_id,earned,paid,balance FROM v_creator_balance WHERE creator_id IN (1,4,5);
SELECT s.id,s.total_income,s.total_cost,s.distributable_amount,s.studio_amount,SUM(l.amount) AS creator_amount,
       s.distributable_amount=s.studio_amount+SUM(l.amount) AS balanced
FROM settlement s JOIN settlement_line l ON l.settlement_id=s.id GROUP BY s.id;

START TRANSACTION;
SELECT '退款：沿用到期的原协议，负向结算抵扣余额' AS verification;
CALL sp_generate_settlement(3,'2026-11-01','2026-11-30',3,@refund_settlement);
SELECT total_income,total_cost,distributable_amount,studio_amount FROM settlement WHERE id=@refund_settlement;
SELECT creator_id,rate,amount FROM settlement_line WHERE settlement_id=@refund_settlement ORDER BY creator_id;
UPDATE settlement SET status='SUBMITTED',submitted_by=3,submitted_at=NOW(),last_actor_id=3 WHERE id=@refund_settlement;
UPDATE settlement SET status='APPROVED',approved_by=4,approved_at=NOW(),last_actor_id=4 WHERE id=@refund_settlement;
SELECT creator_id,earned,paid,balance FROM v_creator_balance WHERE creator_id IN (1,5);
SELECT from_status,to_status,actor_id FROM settlement_history WHERE settlement_id=@refund_settlement ORDER BY id;
ROLLBACK;
SELECT COUNT(*)=0 AS refund_allocation_rolled_back FROM settlement_revenue WHERE revenue_id=15;

START TRANSACTION;
SELECT '新期间：只选取已确认且未分配流水' AS verification;
CALL sp_generate_settlement(1,'2026-08-01','2026-10-31',3,@next_settlement);
SELECT total_income,total_cost,distributable_amount,studio_amount FROM settlement WHERE id=@next_settlement;
SELECT COUNT(*)=3 AS expected_three_revenues FROM settlement_revenue WHERE settlement_id=@next_settlement;
ROLLBACK;

START TRANSACTION;
SELECT '舍入：分账保留分，公司承接尾差' AS verification;
UPDATE contract SET status='ACTIVE' WHERE id=8;
INSERT INTO project_cost(project_id,contract_id,category,description,amount,cost_date,status,approved_by,approved_at,created_by)
VALUES(8,8,'验证','五分钱成本用于分账舍入验证',0.05,'2026-11-01','APPROVED',4,NOW(),2);
CALL sp_generate_settlement(8,'2026-11-01','2026-11-30',3,@rounding_settlement);
SELECT s.distributable_amount,s.studio_amount,SUM(l.amount) AS creator_amount,
       s.distributable_amount=s.studio_amount+SUM(l.amount) AS balanced
FROM settlement s JOIN settlement_line l ON l.settlement_id=s.id WHERE s.id=@rounding_settlement GROUP BY s.id;
SELECT creator_id,rate,amount FROM settlement_line WHERE settlement_id=@rounding_settlement ORDER BY creator_id;
ROLLBACK;

START TRANSACTION;
SELECT 'EXPECTED: 已批准结算冻结' AS assertion;
UPDATE settlement SET studio_amount=studio_amount+1,last_actor_id=1 WHERE id=1;
SELECT 'EXPECTED: 已确认收入冻结' AS assertion;
UPDATE revenue SET gross_amount=99999 WHERE id=1;
SELECT 'EXPECTED: 已分配成本冻结' AS assertion;
UPDATE project_cost SET amount=99999 WHERE id=1;
SELECT 'EXPECTED: 生效协议比例冻结' AS assertion;
UPDATE contract_participant SET rate=49 WHERE contract_id=1 AND creator_id=1;
SELECT 'EXPECTED: 生效协议日期冻结' AS assertion;
UPDATE contract SET end_date='2027-01-31' WHERE id=1;
SELECT 'EXPECTED: 重复纳入已结算收入' AS assertion;
INSERT INTO settlement_revenue(settlement_id,revenue_id,net_amount) VALUES(4,1,12160);
SELECT 'EXPECTED: 同一人提交审核被拒绝' AS assertion;
UPDATE settlement SET status='APPROVED',approved_by=3,approved_at=NOW(),last_actor_id=3 WHERE id=3;
SELECT 'EXPECTED: 全部已分配期间拒绝重复结算' AS assertion;
CALL sp_generate_settlement(1,'2026-07-01','2026-07-31',3,@empty_settlement);
ROLLBACK;
SELECT COUNT(*)=4 AS original_settlements_preserved FROM settlement;

START TRANSACTION;
SELECT '退回：实际审核人进入状态历史' AS verification;
UPDATE settlement SET status='DRAFT',reject_reason='补充结算说明',last_actor_id=4 WHERE id=3;
SELECT from_status,to_status,actor_id,reason FROM settlement_history WHERE settlement_id=3 ORDER BY id DESC LIMIT 1;
ROLLBACK;

START TRANSACTION;
INSERT INTO contract(project_id,name,start_date,end_date,studio_rate,created_by) VALUES(1,'范围重叠检查','2026-11-01','2026-12-31',50,3);
SET @overlap_contract=LAST_INSERT_ID();
INSERT INTO contract_participant(contract_id,creator_id,rate) VALUES(@overlap_contract,1,50);
SELECT 'EXPECTED: 同项目协议日期重叠' AS assertion;
UPDATE contract SET status='ACTIVE' WHERE id=@overlap_contract;
ROLLBACK;

START TRANSACTION;
INSERT INTO contract(project_id,name,start_date,end_date,studio_rate,created_by) VALUES(8,'分账比例检查','2027-01-01','2027-12-31',30,3);
SET @rate_contract=LAST_INSERT_ID();
INSERT INTO contract_participant(contract_id,creator_id,rate) VALUES(@rate_contract,6,50);
SELECT 'EXPECTED: 分账比例合计不是100' AS assertion;
UPDATE contract SET status='ACTIVE' WHERE id=@rate_contract;
ROLLBACK;
