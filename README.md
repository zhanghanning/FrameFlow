# 帧序 FrameFlow

短视频创作者素材与收益结算管理系统。面向工作室 / MCN，连接项目协作、素材授权、多平台发布、收益对账、合同分账、结算审核与付款登记。

## 运行

项目位于 WSL Ubuntu 22.04：`/home/techno/JavaWebSQL`。

```bash
cd /home/techno/JavaWebSQL
bash scripts/dev.sh
```

浏览器打开 **http://localhost:3000**。后端 8080，MySQL 3306；开发服务日志在 `.runtime/`。停止时执行 `bash scripts/stop.sh`。

Windows / IDEA 终端可以直接执行：

```powershell
wsl -d Ubuntu-22.04 -- bash /home/techno/JavaWebSQL/scripts/dev.sh
```

Windows 的 5142–5241 属于系统保留端口，本机前端选用 3000 使 WSL localhost 转发正常。

## 注册与身份

打开 http://localhost:3000/register 注册创作者账号。系统同步建立个人创作者档案，登录后可管理本人资料、平台账号和素材，加入项目后开展作品协作并查看个人分账。

在账号中心的“身份申请”提交项目统筹、财务、审核或管理员岗位申请，已有管理员审核通过后追加权限。项目统筹负责立项和管理本人负责项目，创作者参与分配项目；素材通过项目授权共享。撤回附加岗位后保留创作者身份及历史资产、分账关系。具体范围见 [权限说明](docs/permissions.md)。

## 演示账号

账号分别为 `admin`（管理员）、`producer`（项目统筹）、`finance`（财务）、`reviewer`（审核）、`creator`（创作者林知夏）。演示密码均为 `FrameFlow2026!`。页面提供角色快捷填入。

工作室、创作者、项目、收入与付款均为演示数据；素材由本项目脚本生成。平台数据通过人工录入 / CSV 导入，付款功能登记实际线下付款与凭据。

## 环境与 IDEA

- Ubuntu 22.04.5 / WSL2；JDK 21、Maven 3.6.3、Node 24.12、MySQL 8.0.46。
- 后端：Spring Boot 3.5.12、MyBatis 3.0.5、Bean Validation、数据库会话与 BCrypt。
- 前端：Vue 3、Vite、Vue Router、Axios、Element Plus；精确依赖版本见 package-lock.json。
- IDEA 打开 `\\wsl.localhost\Ubuntu-22.04\home\techno\JavaWebSQL`，将 backend/pom.xml 作为 Maven 项目加载。详细设置见 [开发说明](docs/development.md)。

## 首次初始化

当前机器已经初始化。新的 Ubuntu 环境需要安装 JDK 21、Maven、Node 24 和 MySQL 8，再执行：

```bash
python3 scripts/create_demo_assets.py
sudo python3 scripts/init_database.py
cd frontend && npm ci && cd ..
bash scripts/dev.sh
```

初始化脚本创建业务库 `frameflow`、独立测试库 `frameflow_test`、应用账号和本地 `.env`，已有业务表会保留，并执行 `database/identity-workflow.sql` 增量更新。建表、视图、过程、触发器、种子与授权脚本位于 `database/`，SQL 不会由应用启动自动重建。

## 业务与验证

- 31 张表、3 个业务视图，具备主外键、唯一性与业务 CHECK 约束。
- 存储过程在 Java 事务中生成结算，锁定收入/成本分配，保存分账快照和尾差。
- 审核与提交分离；退款继承原协议，负向结算抵扣后续收益；付款依据已批准余额。
- 独立测试库的 HTTP 集成验证共 30 组通过：14 组注册、岗位与项目权限、11 组主业务流程、5 组协作权限。原始结果分别见 [注册验证](docs/registration-verification.json)、[主流程验证](docs/verification-results.json)、[协作验证](docs/collaboration-verification.json)。
- Java 打包与 Vue 生产构建通过；浏览器验证创作者注册入口、首次登录引导、岗位申请、获批刷新与管理员审批记录，页面控制台无错误。
- 百万行独立表已实际生成。同一查询实测 617ms → 2.5ms，扫描 1000000 → 134 行，详见 [原始执行计划](docs/performance-result.txt)。这是本机单次测量；SQL 日期改写另有独立对比。

```bash
bash scripts/build.sh                 # Java 构建及前端生产构建
bash scripts/test_backend.sh          # 另开终端：8082 独立测试服务
python3 scripts/verify_api.py
python3 scripts/verify_collaboration.py
python3 scripts/verify_registration.py
sudo mysql --table frameflow_test < database/verify.sql
```

性能表首次生成：`bash scripts/benchmark.sh`。它要求空的 perf_revenue 表；已存在百万行结果时直接查看执行计划材料。其数据与日常工作室收入隔离。

[需求追踪](docs/requirements.md) · [业务词汇](CONTEXT.md) · [架构](docs/architecture.md) · [功能代码导航](docs/code-walkthrough.md)

正式实验报告暂缓；当前继续系统多轮迭代，等用户明确要求开始后再按学习通模板编写。
