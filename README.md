# 帧序 FrameFlow

大型网站程序设计 + 数据库原理课程设计。

短视频创作者素材与收益结算管理系统。面向工作室 / MCN，连接项目协作、素材授权、多平台发布、收益对账、合同分账、结算审核与付款登记。

## 运行

项目位于 WSL Ubuntu 22.04：`/home/techno/JavaWebSQL`。

```bash
cd /home/techno/JavaWebSQL
bash scripts/dev.sh
```

浏览器打开 **http://localhost:18080**。后端 8080，MySQL 3306；开发服务日志在 `.runtime/`。停止时执行 `bash scripts/stop.sh`。

Windows / IDEA 终端可以直接执行：

```powershell
wsl -d Ubuntu-22.04 -- bash /home/techno/JavaWebSQL/scripts/dev.sh
```

本机前端使用 18080。Windows 保留端口范围会变化；2026-11-16 排查发现 3000 落入 2989–3088 保留区，WSL 内服务正常而 Windows localhost 访问失败。排查步骤见 [开发说明](docs/development.md#运行排查)。

## 注册与身份

打开 http://localhost:18080/register 注册创作者账号。系统同步建立个人创作者档案，登录后可管理本人资料、平台账号和素材，加入项目后开展作品协作并查看个人分账。

在账号中心的“身份申请”提交项目统筹、财务、审核或管理员岗位申请，已有管理员审核通过后追加权限。项目统筹负责立项和管理本人负责项目，创作者参与分配项目；素材通过项目授权共享。撤回附加岗位后保留创作者身份及历史资产、分账关系。具体范围见 [权限说明](docs/permissions.md)。

## 素材预览

素材卡片显示真实图片或视频封面。鼠标停留 800ms 后，在卡片旁放大预览：图片显示原图，视频播放 3 秒静音循环，循环接缝采用 150ms 交叉淡化。点击卡片打开居中的完整素材窗口，视频与音频使用原文件及播放控件；系统启用“减少动态效果”时，悬停仅展示封面。

视频预览由 FFmpeg 生成，最长边 640px、保持纵横比，采用 H.264 / yuv420p；不足 3 秒的原视频循环补足。原始文件保持完整，视频封面及预览存放在 `UPLOAD_DIR/previews/{id}/`。所有预览请求都检查当前素材权限，项目授权撤回后立即限制后续访问。接口使用 `/api/assets/{id}/media` 获取描述，`/poster` 和 `/preview` 读取视频封面与片段；完整接口合同见 [媒体开发说明](docs/implementation-contract.md#asset-media-previews)。

## 平台账号与发布

“平台账号”管理各平台账号档案，保留平台 Logo、账号归属、平台账号 ID 和状态；“作品与发布”提供作品库、发布记录及原平台作品链接。手工账号与发布记录继续用于收入对账和结算。

第三方账号授权、自动同步、待申请/待配置卡片及“平台作品”页签默认关闭。已有 YouTube / Instagram Login 适配器和同步数据模型独立保留，未来取得平台应用权限后可显式开启。配置和验证方式见 [平台接入说明](docs/platform-integrations.md)。

## 工作室界面与结算操作

界面采用纸白、墨蓝和朱砂橙，顶部业务分区搭配二级导航。概览以制作档案、待办和财务记录组织；项目卡片展示实际简介、预算、负责人和日期，素材继续使用真实封面及延迟预览。登录与注册使用相同的工作室档案视觉。

财务页提供“约定分成 → 确认收支 → 结算与审核 → 登记付款”步骤提示。收益结算详情中的草稿及退回单可“修改并重算”“重新计算”“撤销结算”，均记录操作理由。修改和重算沿用原单号，旧金额与依据可在“查阅内容”及“操作记录”查看；撤销释放收入、成本并保留历史。已批准结算保持固定，线下付款记录单独管理。

数据库增量为 `database/settlement-workflow.sql`，初始化脚本会应用到业务库和测试库；新建与重算共用 `sp_calculate_settlement` 计算规则。独立测试库8组流程验证和11组原主业务回归通过，后端25项测试及前端构建通过。详见 [结算流程验证](docs/settlement-workflow-verification.json)。

## 演示账号

账号分别为 `admin`（管理员）、`producer`（项目统筹）、`finance`（财务）、`reviewer`（审核）、`creator`（创作者林知夏）。演示密码均为 `FrameFlow2026!`。页面提供角色快捷填入。

工作室、创作者、项目、收入与付款均为演示数据；素材由本项目脚本生成。平台数据通过人工录入 / CSV 导入，付款功能登记实际线下付款与凭据。

## 环境与 IDEA

- Ubuntu 22.04.5 / WSL2；JDK 21、Maven 3.6.3、Node 24.12、MySQL 8.0.46、FFmpeg / ffprobe 4.4.2。
- 后端：Spring Boot 3.5.12、MyBatis 3.0.5、Bean Validation、数据库会话与 BCrypt。
- 前端：Vue 3、Vite、Vue Router、Axios、Element Plus；精确依赖版本见 package-lock.json。
- IDEA 打开 `\\wsl.localhost\Ubuntu-22.04\home\techno\JavaWebSQL`，将 backend/pom.xml 作为 Maven 项目加载。详细设置见 [开发说明](docs/development.md)。

## 首次初始化

当前机器已经初始化。新的 Ubuntu 环境需要安装 JDK 21、Maven、Node 24 和 MySQL 8。视频处理工具从 Ubuntu 官方软件源安装，再初始化项目：

```bash
sudo apt update
sudo apt install -y ffmpeg python3-pil fonts-dejavu-core
cd /home/techno/JavaWebSQL
python3 scripts/create_demo_assets.py
sudo python3 scripts/init_database.py
cd frontend && npm ci && cd ..
bash scripts/dev.sh
```

初始化脚本创建业务库 `frameflow`、独立测试库 `frameflow_test`、应用账号和本地 `.env`，已有业务表会保留，并执行 `database/identity-workflow.sql` 增量更新。建表、视图、过程、触发器、种子与授权脚本位于 `database/`，SQL 不会由应用启动自动重建。

可选执行 `python3 scripts/create_demo_video.py`，生成自制 6 秒视频 `.runtime/media-demo/forest-light.mp4`，再从素材库上传。该脚本依赖 FFmpeg、`python3-pil` 与 DejaVu 字体；上述安装命令已包含这些依赖。视频处理要求 `ffmpeg` 和 `ffprobe` 位于后端进程的 PATH 中。

## 业务与验证

- 36 张表、3 个业务视图，具备主外键、唯一性与业务 CHECK 约束。
- 存储过程在 Java 事务中生成结算，锁定收入/成本分配，保存分账快照和尾差。
- 审核与提交分离；退款继承原协议，负向结算抵扣后续收益；付款依据已批准余额。
- 独立测试库的身份与主业务 HTTP 集成验证 30 组通过：14 组注册、岗位与项目权限、11 组主业务流程、5 组协作权限。原始结果分别见 [注册验证](docs/registration-verification.json)、[主流程验证](docs/verification-results.json)、[协作验证](docs/collaboration-verification.json)。
- 媒体验证 6 组通过：真实封面/3 秒静音预览/原文件保留、授权与撤权、短视频补足与竖屏比例、静态图片、无效视频上传回滚，以及生成锁占用时的缓存并发读取。实测预览 3.0 秒、76324 字节，原文件 3345446 字节，见 [媒体验证结果](docs/media-verification.json)。
- 平台模块启用状态的历史验证：13 项 Java 测试、6 组独立库 HTTP 验证与浏览器交互验收通过。外部账号联调等待应用凭据；本地结果见 [平台模块验证](docs/platform-integration-verification.json)。
- Java 打包与 Vue 生产构建通过；浏览器验证创作者注册入口、首次登录引导、岗位申请、获批刷新与管理员审批记录，页面控制台无错误。
- 百万行独立表已实际生成。同一查询实测 617ms → 2.5ms，扫描 1000000 → 134 行，详见 [原始执行计划](docs/performance-result.txt)。这是本机单次测量；SQL 日期改写另有独立对比。

```bash
bash scripts/build.sh                 # Java 构建及前端生产构建
bash scripts/test_backend.sh          # 另开终端：8082 独立测试服务
python3 scripts/verify_api.py
python3 scripts/verify_settlement_workflow.py  # 草稿修改、重算、撤销与重新结算
python3 scripts/verify_collaboration.py
python3 scripts/verify_registration.py
# 可选模块测试：先以 PLATFORM_INTEGRATIONS_ENABLED=true 启动独立测试服务
python3 scripts/verify_platform_integrations.py  # 启用状态与本地内容权限
python3 scripts/verify_media.py        # 需 FFmpeg；使用 8082 的 frameflow_test
sudo mysql --table frameflow_test < database/verify.sql
```

性能表首次生成：`bash scripts/benchmark.sh`。它要求空的 perf_revenue 表；已存在百万行结果时直接查看执行计划材料。其数据与日常工作室收入隔离。

[需求追踪](docs/requirements.md) · [业务词汇](CONTEXT.md) · [架构](docs/architecture.md) · [功能代码导航](docs/code-walkthrough.md)

正式实验报告暂缓；当前继续系统多轮迭代，等用户明确要求开始后再按学习通模板编写。
