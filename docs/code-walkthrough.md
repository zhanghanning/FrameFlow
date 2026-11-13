# 功能代码导航

开发辅助材料：用于定位实现和准备验收讲解，正式报告另按课程模板编写。

| 功能 | Vue 页面 | Controller | Service | SQL |
|---|---|---|---|---|
| 注册、登录、改密、注销 | Register.vue / Login.vue / Profile.vue | AuthController | AuthService | AuthMapper.xml |
| 项目、成员、创作者、账号 | Projects.vue / Resource.vue | StudioController | StudioService | StudioMapper.xml |
| 素材上传、授权、引用读取 | Assets.vue | StudioController | AssetService | StudioMapper.xml |
| 作品与发布校验 | Works.vue | StudioController | StudioService.publish | StudioMapper.xml |
| 收入、退款与对账 | Revenues.vue | FinanceController | FinanceService | StudioMapper.xml |
| CSV 预览与导入 | Revenues.vue | FinanceController | RevenueImportService | StudioMapper.xml |
| 分账协议 | Contracts.vue | FinanceController | FinanceService | StudioMapper.xml |
| 生成与审核结算 | Settlements.vue | FinanceController | FinanceService | generateSettlement / objects.sql |
| 余额与付款 | Balances.vue | FinanceController | FinanceService.payment | v_creator_balance / StudioMapper.xml |
| 岗位申请与审核 | RoleApplications.vue | AuthController | AuthService | AuthMapper.xml / role_application |
| 团队账号与角色 | Resource.vue | AuthController | AuthService | AuthMapper.xml |

Vue 页面位于 frontend/src/views；Java 位于 backend/src/main/java/com/frameflow；Mapper XML 位于 backend/src/main/resources/mapper。

## 自选功能：收益结算

1. Settlements.vue 表单采集 contractId、periodStart、periodEnd，点击生成。
2. frontend/src/lib/api.js 中统一 Axios 请求携带登录令牌，POST /api/settlements。
3. FinanceController 绑定并校验 DTO；FinanceService.generate 检查角色和期间，在事务中调用 MyBatis。
4. Mapper 以 CALLABLE 调用 sp_generate_settlement，OUT 参数接收结算 ID。
5. 过程锁住协议，将尚未分配的确认收入和批准成本写入来源表，保存参与方比例与金额；工作室承接分位尾差。
6. Vue 请求结算详情，显示来源、成本、分成与历史。提交和批准由不同人完成，数据库触发器记录状态变化。
7. v_creator_balance 汇总批准金额，付款登记校验余额并记录实际支付。退款保留原收入和协议的关联。

重点解释：为什么使用事务和行锁、为什么来源分配 UNIQUE、为什么保存快照、为什么审核和付款分开。

## 另一条完整流程：素材到作品发布

1. Assets.vue 构造 multipart/form-data，文件和授权信息一起提交。
2. AssetService 写文件和素材记录，标签与允许平台以联系表保存。
3. Works.vue 创建作品并绑定素材；选择平台账号登记发布。
4. StudioService.publish 检查作品状态、项目成员、账号、素材授权日期与平台，然后写 publication。
5. 该发布记录随后成为收入归属的依据；同作品可以对应多个发布记录。

随机功能讲解按同样路线定位：页面事件 → 请求方法与参数 → Controller DTO → Service 判断和事务 → Mapper SQL → 外键约束 → 页面刷新。
