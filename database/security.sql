-- 由数据库管理员运行。创建账户及密码在部署阶段完成，本文件只定义数据库角色。
-- 应用只访问业务对象；建表、建索引、创建触发器等管理权限留给数据库管理员。
CREATE ROLE IF NOT EXISTS 'frameflow_app_role','frameflow_analyst_role';

GRANT SELECT,INSERT,UPDATE ON frameflow.app_user TO 'frameflow_app_role';
GRANT SELECT ON frameflow.app_role TO 'frameflow_app_role';
GRANT SELECT,INSERT,DELETE ON frameflow.user_role TO 'frameflow_app_role';
GRANT SELECT,INSERT,DELETE ON frameflow.user_session TO 'frameflow_app_role';
GRANT SELECT,INSERT,UPDATE ON frameflow.role_application TO 'frameflow_app_role';
GRANT SELECT,INSERT,UPDATE ON frameflow.creator TO 'frameflow_app_role';
GRANT SELECT ON frameflow.platform TO 'frameflow_app_role';
GRANT SELECT,INSERT,UPDATE ON frameflow.platform_account TO 'frameflow_app_role';
GRANT SELECT,INSERT,UPDATE ON frameflow.project TO 'frameflow_app_role';
GRANT SELECT,INSERT,DELETE ON frameflow.project_member TO 'frameflow_app_role';
GRANT SELECT,INSERT,UPDATE ON frameflow.asset TO 'frameflow_app_role';
GRANT SELECT,INSERT,DELETE ON frameflow.project_asset TO 'frameflow_app_role';
GRANT SELECT,INSERT,DELETE ON frameflow.asset_platform TO 'frameflow_app_role';
GRANT SELECT,INSERT ON frameflow.tag TO 'frameflow_app_role';
GRANT SELECT,INSERT,DELETE ON frameflow.asset_tag TO 'frameflow_app_role';
GRANT SELECT,INSERT,UPDATE ON frameflow.work TO 'frameflow_app_role';
GRANT SELECT,INSERT,DELETE ON frameflow.work_asset TO 'frameflow_app_role';
GRANT SELECT,INSERT,UPDATE ON frameflow.publication TO 'frameflow_app_role';
GRANT SELECT,INSERT,UPDATE ON frameflow.contract TO 'frameflow_app_role';
GRANT SELECT,INSERT ON frameflow.contract_participant TO 'frameflow_app_role';
GRANT SELECT,INSERT,UPDATE ON frameflow.import_batch TO 'frameflow_app_role';
GRANT SELECT,INSERT,UPDATE ON frameflow.revenue TO 'frameflow_app_role';
GRANT SELECT,INSERT,UPDATE ON frameflow.project_cost TO 'frameflow_app_role';
GRANT SELECT,INSERT,UPDATE ON frameflow.settlement TO 'frameflow_app_role';
GRANT SELECT,INSERT ON frameflow.settlement_revenue TO 'frameflow_app_role';
GRANT SELECT,INSERT ON frameflow.settlement_cost TO 'frameflow_app_role';
GRANT SELECT,INSERT ON frameflow.settlement_line TO 'frameflow_app_role';
GRANT SELECT,INSERT ON frameflow.payment TO 'frameflow_app_role';
GRANT SELECT ON frameflow.settlement_history TO 'frameflow_app_role';
GRANT SELECT,INSERT ON frameflow.audit_log TO 'frameflow_app_role';
GRANT SELECT ON frameflow.v_project_finance TO 'frameflow_app_role';
GRANT SELECT ON frameflow.v_creator_balance TO 'frameflow_app_role';
GRANT SELECT ON frameflow.v_asset_license_expiry TO 'frameflow_app_role';
GRANT EXECUTE ON PROCEDURE frameflow.sp_generate_settlement TO 'frameflow_app_role';

-- 分析角色只读：视图使用定义者权限，分析账号可以看汇总，无法读取密码或会话。
GRANT SELECT ON frameflow.v_project_finance TO 'frameflow_analyst_role';
GRANT SELECT ON frameflow.v_creator_balance TO 'frameflow_analyst_role';
GRANT SELECT ON frameflow.v_asset_license_expiry TO 'frameflow_analyst_role';
GRANT SELECT ON frameflow.perf_revenue TO 'frameflow_analyst_role';

-- 账户绑定由部署脚本执行，例如：
-- GRANT 'frameflow_app_role' TO 'frameflow_app'@'localhost';
-- SET DEFAULT ROLE 'frameflow_app_role' TO 'frameflow_app'@'localhost';
