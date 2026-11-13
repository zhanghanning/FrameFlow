-- 百万行 SQL 性能实验。只在 schema.sql 创建的空 perf_revenue 表运行一次。
-- 执行：mysql frameflow < database/performance.sql > docs/performance-result.txt
-- 同一语句分别在组合索引创建前后执行；EXPLAIN ANALYZE 返回实际执行耗时。
-- 测试表独立于业务账务，800位创作者、3个平台、两年的收益日期分布。
SET NAMES utf8mb4;
SET @generation_started=NOW(6);

INSERT INTO perf_revenue(id,creator_id,platform_id,revenue_date,status,gross_amount,fee_amount,source_ref)
WITH digits AS (
  SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
  UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
), numbers AS (
  SELECT a.n+10*b.n+100*c.n+1000*d.n+10000*e.n+100000*f.n AS n
  FROM digits a CROSS JOIN digits b CROSS JOIN digits c
  CROSS JOIN digits d CROSS JOIN digits e CROSS JOIN digits f
)
SELECT n+1,1+MOD(n*37,800),1+MOD(FLOOR(n/800),3),
       DATE_ADD('2025-01-01',INTERVAL MOD(FLOOR(n/13),730) DAY),
       IF(MOD(FLOOR(n/7),10)<8,'CONFIRMED','DRAFT'),
       10+MOD(n*97,200000)/100,ROUND((10+MOD(n*97,200000)/100)*0.05,2),
       CONCAT('PERF-',LPAD(n+1,10,'0'))
FROM numbers;

SELECT COUNT(*) AS generated_rows,TIMESTAMPDIFF(MICROSECOND,@generation_started,NOW(6))/1000000 AS generation_seconds FROM perf_revenue;
ANALYZE TABLE perf_revenue;
SELECT '优化前：相同业务查询，只有主键索引' AS experiment_stage;
EXPLAIN ANALYZE
SELECT COUNT(*) AS income_count,SUM(net_amount) AS net_income FROM perf_revenue
WHERE creator_id=42 AND status='CONFIRMED' AND revenue_date>='2026-06-01' AND revenue_date<'2026-09-01';

CREATE INDEX ix_perf_creator_status_date ON perf_revenue(creator_id,status,revenue_date);
ANALYZE TABLE perf_revenue;
SELECT '优化后：等值列在前，日期范围列在后的组合索引' AS experiment_stage;
EXPLAIN ANALYZE
SELECT COUNT(*) AS income_count,SUM(net_amount) AS net_income FROM perf_revenue
WHERE creator_id=42 AND status='CONFIRMED' AND revenue_date>='2026-06-01' AND revenue_date<'2026-09-01';

SELECT 'SQL改写对比：YEAR和MONTH包裹日期列，与半开日期区间' AS experiment_stage;
EXPLAIN ANALYZE
SELECT COUNT(*) AS income_count,SUM(net_amount) AS net_income FROM perf_revenue
WHERE creator_id=42 AND status='CONFIRMED' AND YEAR(revenue_date)=2026 AND MONTH(revenue_date) BETWEEN 6 AND 8;
EXPLAIN ANALYZE
SELECT COUNT(*) AS income_count,SUM(net_amount) AS net_income FROM perf_revenue
WHERE creator_id=42 AND status='CONFIRMED' AND revenue_date>='2026-06-01' AND revenue_date<'2026-09-01';
SELECT COUNT(*) AS income_count,SUM(net_amount) AS net_income FROM perf_revenue
WHERE creator_id=42 AND status='CONFIRMED' AND revenue_date>='2026-06-01' AND revenue_date<'2026-09-01';
