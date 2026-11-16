# Day 2 — 迁移与测试底座

日期：2026-11-16，Asia/Shanghai。

**当前状态：Day 2 工程实现与本地验收已完成，包括真实 Compose 与连续两轮 Testcontainers 验证。** GitHub CI 骨架通过静态检查，尚未远程运行；学习者人工复现另列。

## 范围与约束

沿用 Day 1 的 Java/Boot/Cloud 与镜像版本，不拆服务、不做注册登录/令牌/权限接口、不添加 ORM、业务 Redis 缓存、MQ/Nacos 治理或监控。

本日没有业务状态机、订单/库存写入或跨服务事务。数据库初始化只创建 schema/独立账号；Flyway 显式创建最小用户基础表。应用启动不迁移、不 seed、不清库；测试数据不进入演示库。

用户安装 Docker Desktop 后，实际启动 Linux 引擎并完成了此前保留的全部容器验收。没有用 mock、H2 或静默跳过替代真实 MySQL/Redis。

## 三组可独立审查的改动

| 组 | 文件 | 重点 |
|---|---|---|
| 1. 本地基础设施 | deploy/compose.yml、deploy/.env.example、deploy/mysql/init/10-create-trade-users.sh、scripts/init-day2-env.ps1 | 仅 MySQL/Redis、回环端口、固定 digest、健康检查、随机口令、迁移/运行账号分离 |
| 2. 显式版本迁移 | Trade pom.xml、db/migration/V1__create_app_user.sql、scripts/migrate-day2.ps1 | 复用 Boot 管理的 Flyway/驱动；不绑定启动、无 seed、禁 clean/自动 baseline、配置缺失失败 |
| 3. 测试与 CI | Trade infrastructure 下测试、scripts/test-day2.ps1、.github/workflows/ci.yml、运行手册与报告 | 真实 MySQL/Redis，随机隔离、负例、两轮运行、CI 失败报告 |

根 parent 已管理所需依赖版本，不额外改根 BOM。支付模拟器与前端源码没有新增功能。

## 已执行结果

| 检查 | 当前实测结果 | 证据 |
|---|---|---|
| 普通 Maven verify | BUILD SUCCESS；Trade 2 + Payment 2 个 HTTP 测试通过；所有新 IT 源码编译通过，IT 不属于此命令的执行范围 | [后端输出](../reports/day2/backend-verify.txt) |
| Compose config --quiet | 使用已锁版本和本地生成口令，配置通过；未启动容器 | [静态检查](../reports/day2/static-checks.txt) |
| Bash / PowerShell 语法 | 通过；Bash 检查在允许启动 MSYS 子进程的环境执行 | [静态检查](../reports/day2/static-checks.txt) |
| 缺少/错误迁移配置 | 缺少 TRADE_MIGRATION_PASSWORD 或端口为 abc 时连接前拒绝；未执行迁移 | [静态检查](../reports/day2/static-checks.txt) |
| 口令文件 Git 忽略 | deploy/.env 被忽略；初始化未输出口令 | git check-ignore |
| GitHub Actions 静态检查 | actionlint 1.7.7 退出 0；未宣称 GitHub CI 通过 | [静态检查](../reports/day2/static-checks.txt) |
| 首轮 -Pintegration verify | 退出 1；两类测试在容器启动前因无有效 Docker 环境报错，0 跳过 | [原始失败输出](../reports/day2/integration-initial.txt) |
| 前端 npm ci / build | 退出 0；安装与 TypeScript/Vite 生产构建通过 | [前端输出](../reports/day2/frontend-verify.txt) |
| npm audit | 退出 0；0 项漏洞，仅为当次 npm 审计范围 | [审计文件](../reports/day2/npm-audit.json) |
| 真实依赖树 | 导出成功：Flyway 11.7.2、MySQL driver 9.7.0、Lettuce 6.6.0.RELEASE、Testcontainers 1.21.4 | [Trade 依赖树](../reports/day2/trade-dependencies.txt) |
| 生产运行包 | 包含迁移 SQL，不包含 Flyway/MySQL/Lettuce/Testcontainers 的运行 JAR | jar tf 实际检查 |
| Flyway Maven info | 插件/驱动已加载，连接 127.0.0.1:13306 被拒，命令失败；没有运行数据库迁移 | [初次查询输出](../reports/day2/flyway-info-initial.txt)；待真实实例启动后重跑 |
| 最终 IT 源码编译 | 修正 Redis 握手负例及跨库断言后 test-compile 成功 | [编译输出](../reports/day2/final-test-compile.txt) |
| 已有口令文件保护 | 再次生成时拒绝覆盖，文件 hash 不变 | [静态检查](../reports/day2/static-checks.txt) |
| Compose 实际启动 | 固定 digest 拉取成功；MySQL、Redis 均 healthy；端口仅绑定 127.0.0.1 | [启动输出](../reports/day2/compose-up.txt)、[状态](../reports/day2/compose-state.txt) |
| 开发库显式迁移 | info 显示 V1 Pending；migrate 应用 1 次；validate 成功；再次 migrate 无需变更 | [迁移原始输出](../reports/day2/compose-migrations.txt) |
| 实际结构与空数据 | version=1、success=1；app_user 0 行；PRIMARY 与 uk_app_user_login_name 均为唯一索引 | [数据库查询](../reports/day2/development-database.txt) |
| 第 1 轮 integration verify | BUILD SUCCESS；7 个 IT 全通过，0 错误/失败/跳过；另有 4 个 HTTP 测试通过 | [第 1 轮输出](../reports/day2/integration-run-1.txt) |
| 第 2 轮 integration verify | BUILD SUCCESS；7 个 IT 全通过，0 错误/失败/跳过；另有 4 个 HTTP 测试通过 | [第 2 轮输出](../reports/day2/integration-run-2.txt) |
| 测试后开发环境数据 | MySQL 8.4.13，app_user 仍为 0 行；Redis 7.4.12，DBSIZE=0 | [MySQL](../reports/day2/final-database-state.txt)、[Redis](../reports/day2/final-redis-state.txt) |
| 清理 | 测试容器自动清理；本次 Compose 容器停止，命名卷保留 | [清理记录](../reports/day2/cleanup.txt) |

本机初始无 docker 命令，最初的引擎缺失及连接拒绝已保留为历史失败。用户随后完成 Docker Desktop 的用户级安装，当前会话 PATH 尚未更新；通过注册表定位安装目录、启动 Desktop 并等待 Linux 引擎就绪。测试脚本补充用户级安装路径探测和本进程 PATH，不修改系统 PATH。

实际 Docker client/server 为 29.9.3，Linux x86_64，API 1.56 / MinAPI 1.40，Compose 为 5.7.0，见 [版本输出](../reports/day2/docker-version.txt)。便携 Compose CLI 2.39.4 仅用于先前的无引擎配置检查，其下载已核对官方 SHA256；它不提供引擎。

实际命令：`scripts/init-day2-env.ps1`；`docker --context desktop-linux compose --env-file deploy/versions.env --env-file deploy/.env -f deploy/compose.yml up -d --wait --wait-timeout 180`；`scripts/migrate-day2.ps1 -Action info/migrate/validate/migrate`（四次分别执行）；`scripts/test-day2.ps1 -Runs 2`；最后对该 Compose 项目执行 `down`，没有 `-v`。

## 真实容器验收清单

- [x] Compose 实际拉取固定 digest 并启动 MySQL/Redis，两者 healthy。
- [x] 显式迁移空库成功、validate 成功，第二次 migrate 不重复执行；查询用户表为空和实际唯一索引。
- [x] Testcontainers 全部 7 个用例通过：迁移/数据保留、唯一键与回滚、checksum 冲突、clean 禁用、错误口令、权限隔离、Redis 认证/写读/TTL/清理。
- [x] integration profile 连续两次均通过，0 错误、0 跳过；保留两次输出。
- [x] 开发库不含测试夹具；测试结束无本次临时容器残留。

运行方式见 [Day 2 手册](../runbooks/day2-local.md)。以上均由 Codex 实际执行；不冒充学习者已经亲自验证。

## 已知限制与未验证项

- GitHub CI 只完成骨架与 actionlint 检查，以及同等本地构建/测试命令验证；没有远程仓库或 Actions run，不虚构 CI 成功链接。Linux CI 的完整执行仍待推送后验证。
- Flyway 11.7.2 实际发出 MySQL 8.4 高于其已测试支持版本（8.1）的警告。保留 Boot 管理版本，未屏蔽警告；本日两轮实验仅证明本次迁移/约束/负例可工作，不能推断所有 MySQL 8.4 DDL 或升级路径都受官方支持。
- 沿用 Boot 3.5 已结束 OSS 支持的限制；没有新增全量 Java/镜像漏洞扫描，也没有性能压测结果。
- 仅建立 Day 3 所需的最小用户基础表，登录名规范化、角色、认证与业务 DAO 尚未实现。

## 最可能失败的边界与当前处理

- Docker CLI 存在不等于 Linux 引擎启动；test-day2.ps1 先检查引擎，Failsafe 本身不允许无 Docker 静默跳过。
- 新依赖引发数据源自动装配：客户端全为 test scope，无 JDBC starter；原有无中间件健康测试实际通过。
- 初始化脚本只对空卷生效：不自动重置口令、不删卷，已有数据与初始化失败按手册人工核对。
- MySQL DDL 部分提交与 checksum 冲突：不自动 repair/baseline/clean；只新增后续迁移。
- 测试污染演示数据：测试连接信息完全来自容器；每测随机 schema，清理只限 cf_test_<随机串>；Redis 只删除自己的 key。
- Redis 未认证连接可能在 RESP 握手时失败：断言覆盖连接及命令阶段，不假定一定等到 PING 才拒绝。
- 跨库拒绝测试使用实际存在的其他 schema 表，避免把“表不存在”误当权限校验成功。

## 待人工学习与提交

- [ ] 解释 IoC 创建 DataSource/Flyway Bean 的条件，以及为何本日不让它们在业务启动中出现。
- [ ] 独立复现一次迁移和一次失败路径，区分配置错误、权限错误与引擎不可用。
- [ ] 说明容器生命周期、测试 schema 清理、事务回滚各自解决什么。
- [ ] 审查 MySQL 实际表、索引、权限，不只相信 ORM 或迁移文件。

Day 2 本地验收结束时尚未创建 commit 或推送。随后用户指定 JoeyXXia/trade-java，统一提交 Day 1/Day 2 已验证成果；三组改动仍可按上表分别审查，实际提交以 Git 历史为准，不补造历史日期。没有性能测量或吞吐结论。
