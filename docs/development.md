# 开发与部署说明

## 在 IDEA 中打开

1. IDEA → Open → `\\wsl.localhost\Ubuntu-22.04\home\techno\JavaWebSQL`。
2. 右键 `backend/pom.xml` → Add as Maven Project。
3. Project SDK 选择 WSL Ubuntu-22.04 中的 `/usr/lib/jvm/java-21-openjdk-amd64`；语言级别 21。
4. Settings → Build Tools → Maven 选择 WSL Maven `/usr/share/maven`，Runner JRE 选择相同 WSL JDK。
5. Vue / JavaScript 使用 WSL Node `/usr/local/bin/node`。前端依赖安装和运行在 WSL 终端内完成。
6. IDEA Terminal 可执行 `wsl -d Ubuntu-22.04`，再 `cd /home/techno/JavaWebSQL`；或把终端 Shell path 设为 `wsl.exe -d Ubuntu-22.04`。

终端运行 `bash scripts/dev.sh` 同时启动前后端，浏览器访问 `http://localhost:18080`。此入口读取 `.env`，所以无需在 IDE 配置中复制数据库密码。MySQL 由 systemd 管理，`systemctl is-active mysql` 查看状态，停止时 `sudo systemctl start mysql` 启动。

## 配置与产物

`.env` 保存本机 DB_URL、DB_USER、DB_PASSWORD、UPLOAD_DIR、JAVA_HOME；`.env.test` 指向 frameflow_test 和 8082。模板 `.env.example` 可随源码分发，实际密码与素材运行目录已加入忽略文件。

`bash scripts/build.sh` 生成 `backend/target/frameflow-1.0.0.jar` 与 `frontend/dist/`。运行打包后端时先加载环境：

```bash
set -a
source .env
set +a
java -jar backend/target/frameflow-1.0.0.jar
```

正式服务器部署可让静态服务器提供 frontend/dist，并把 `/api/` 转发至 8080，素材仍通过后端鉴权接口读取。当前已验证的是本地 WSL 开发运行和生产构建。

## 独立测试库

`frameflow_test` 使用相同表结构、视图、过程、触发器和演示数据，与主库分开。`bash scripts/test_backend.sh` 启动 8082。两个 HTTP 验证脚本在测试库创建可区分的验收项目、账号和流水，不改主库。SQL 验证使用事务回滚。

`python3 scripts/create_demo_assets.py` 同时准备业务和测试服务的演示 SVG 文件。真实上传存放在相应 UPLOAD_DIR 中。

## CSV

UTF-8 编码，支持 BOM。列为 `publicationId,sourceRef,revenueDate,grossAmount,feeAmount,note`；示例在 `database/samples/revenue-import.csv`。金额为人民币元，日期 `yyyy-MM-dd`，按毛额减平台费用得到净额。先预览行校验和重复来源，再导入为草稿，核对后确认。

## 性能实验

独立 perf_revenue 表含 100 万行、800 位创作者、3 平台和两年日期分布。performance.sql 保存生成语句、复合索引和四段 EXPLAIN ANALYZE。首次生成 58.0409 秒；组合索引对比 617ms 与 2.5ms。日期列函数写法与区间写法的两次测量约 1ms，主要比较执行计划对日期范围索引的使用方式。

## 运行排查

- 后端日志 `.runtime/backend.log`；前端日志 `.runtime/frontend.log`。
- 18080/8080 已被占用时先确认是否本项目已运行，用 `scripts/stop.sh` 停止后重启。
- 若 WSL 内能访问而 Windows localhost 无法访问，先比较 `wsl -d Ubuntu-22.04 -- curl -I http://localhost:18080` 与 Windows 的 `curl.exe -I http://localhost:18080`，再用 `netsh interface ipv4 show excludedportrange protocol=tcp` 检查 Windows 保留端口。2026-11-16 的旧端口 3000 落入 2989–3088 保留区；临时 18080 监听经 Windows localhost 请求返回 HTTP 200，因此当前入口改用 18080。
- 更新数据库结构时直接维护 database/ 原脚本，并在独立测试库验证。应用启动不隐式重建业务数据。
- 若出现环境阻塞，记录具体错误后决定处理方式，保持既定技术方案。
