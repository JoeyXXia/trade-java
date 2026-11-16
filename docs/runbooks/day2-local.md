# Day 2：本地基础设施与迁移

本日只引入 MySQL、Redis、显式迁移与测试底座。Trade 仍可无中间件启动；支付模拟器没有新增持久化依赖。不要为了本日启动 Nacos、MQ、库存服务或完整环境。

## 前置条件与口令

Windows 需要 PowerShell 7、JDK 21 和运行中的 Docker Desktop **Linux 容器**引擎。其他系统可用 Docker Engine + Compose。运行所有命令前回到仓库根目录。

```powershell
. ./scripts/env.ps1
$env:MAVEN_USER_HOME = Join-Path (Get-Location) '.tools/maven-user'
docker info --format '{{.OSType}}'  # 必须为 linux
./scripts/init-day2-env.ps1
```

初始化脚本只生成一次 `deploy/.env`；已存在会拒绝覆盖。四个口令为随机 64 位十六进制字符串，不显示、不提交；不要输出渲染后的 Compose 全文或 env 内容。样例文件不含可用口令。

Compose 命令始终同时读取版本锁和本地口令，两个 env-file 的职责不同：

```powershell
docker compose --env-file deploy/versions.env --env-file deploy/.env -f deploy/compose.yml config --quiet
docker compose --env-file deploy/versions.env --env-file deploy/.env -f deploy/compose.yml up -d --wait --wait-timeout 180
docker compose --env-file deploy/versions.env --env-file deploy/.env -f deploy/compose.yml ps
```

MySQL 映射 `127.0.0.1:13306`，Redis 映射 `127.0.0.1:16379`，避开常见本机服务端口。两个服务均有鉴权与健康检查，镜像直接复用 Day 1 的 tag/digest，不使用 latest。

MySQL 只创建 `commerceflow_trade` schema，及两个仅对该 schema 有权限的账号：

| 账号 | 用途 | 权限 |
|---|---|---|
| root | 容器初始化、内部健康检查 | 不作为业务连接账号 |
| trade_migrator | 显式迁移 | CRUD、CREATE、ALTER、INDEX、REFERENCES；当前不授予 DROP |
| trade_app | 未来业务访问 | 仅 CRUD，无 DDL、无其他 schema 权限 |

未来支付提供方需要独立 schema/账号时再创建。初始化脚本只在**空数据目录**运行；已有卷不会因修改 env 或初始化脚本而更新口令和权限。遇到初始化失败先查看日志，不自动删卷、repair 或重置账号。

## 显式迁移

```powershell
./scripts/migrate-day2.ps1 -Action info
./scripts/migrate-day2.ps1 -Action migrate
./scripts/migrate-day2.ps1 -Action validate
./scripts/migrate-day2.ps1 -Action migrate
```

脚本从忽略的本地 env 文件读取口令，临时传给 Flyway 的进程环境；不将口令写入命令行，完成后恢复原环境。只允许 info/migrate/validate，固定目标为本地 `commerceflow_trade`；缺失/错误格式的配置在连接前失败。无条件关闭 Flyway clean 和自动 baseline，不在应用启动或普通 Maven 生命周期绑定迁移。

SQL 在 `backend/trade-service/src/main/resources/db/migration`。V1 只创建 `app_user`：ID、唯一登录名、密码哈希列、创建时间；没有账号和业务种子。不实现注册登录，登录名目前使用区分大小写/重音的排序规则，Day 3 需与接口规范化规则一起审查。

当前 Boot 管理的 Flyway 11.7.2 会对 MySQL 8.4 提示高于其已测试版本（8.1）。本项目已实测本日迁移和负例两轮通过，但保留此支持边界，未屏蔽警告，也不据此推断其他 DDL/升级路径都兼容。

查询实际迁移及业务表：

```powershell
docker compose --env-file deploy/versions.env --env-file deploy/.env -f deploy/compose.yml exec -T mysql sh -c 'MYSQL_PWD="$TRADE_APP_PASSWORD" mysql -utrade_app -e "SELECT version, success FROM commerceflow_trade.flyway_schema_history; SELECT COUNT(*) AS user_count FROM commerceflow_trade.app_user; SHOW INDEX FROM commerceflow_trade.app_user;"'
```

期望值是验收标准：空库首次应用 V1、用户表 0 行、唯一索引存在；再次 migrate 不应再次建表或删除数据。只有真实执行后才能将期望改为结果。不要编辑已经应用的版本迁移；新增 V2。MySQL DDL 不能假定整段失败自动回滚，失败后需核对表结构与历史，再决定修复步骤。

## 测试与演示数据隔离

- **迁移**只维护结构，禁止带管理员或演示用户。
- **测试**只连接 Testcontainers 随机映射端口，不读取开发库 URL/口令，不挂载开发卷，不启用 reuse。
- MySQL 每个测试分配 `cf_test_<随机串>` schema，结束只删除该测试 schema；账号权限测试使用该容器自己的 bootstrap schema。
- Redis 每次只操作自己的 `cf:test:<随机串>` key 并显式删除，禁止 FLUSHDB/FLUSHALL。
- 夹具由测试方法内的参数化 SQL 创建；密码列为不可用于登录的测试字符串，不提供真实登录账号。
- **演示数据**本日为空；Day 3 有认证实现后再提供独立、显式的演示 seed。不能把测试夹具复制进正式迁移。

## 分层验证

```powershell
./mvnw.cmd -B -ntp verify          # 普通构建 + 原有真实 HTTP 测试
./scripts/test-day2.ps1           # Docker 检查 + 完整 integration verify 连续两次
```

也可在任意支持 Docker 的环境从根目录执行：

```text
./mvnw -B -ntp -Pintegration verify
```

Windows 使用 `mvnw.cmd`。integration profile 由 Failsafe 运行 `*IT`，正常 verify 不运行它们；明确运行 integration 时 Docker 缺失会失败，不能当成测试跳过。MySQL 6 个测试、Redis 1 个测试覆盖：

1. 空库迁移、第二次迁移无变化、保留哨兵数据。
2. 实际唯一索引、重复插入拒绝、事务回滚后无夹具残留。
3. 修改后的迁移 checksum 验证失败且不应用变更。
4. clean 被拒绝且原数据保留。
5. 错误数据库口令失败且不建表。
6. Compose 共用初始化脚本的迁移/运行账号与跨库访问隔离。
7. Redis 认证、PING、写读 TTL、自有 key 清理、无口令访问拒绝。

`scripts/test-day2.ps1` 默认连续运行两次，逐次保存输出到 `docs/reports/day2`，任何一次失败即停止。容器成功启动后交给 Testcontainers 生命周期管理，测试不依赖 Compose 的开发实例。测试驱动、Flyway 和 Lettuce 均为 test scope，生产运行包只有迁移 SQL，没有数据库客户端或自动迁移行为。

## CI 与停止环境

`.github/workflows/ci.yml` 在 push/PR/手动触发时运行：普通后端检查、Compose config、前端 ci/build/audit，随后在 Linux Docker 环境连续两次执行集成测试，失败也上传报告。Actions 固定到官方提交 SHA。工作流写好或 actionlint 通过，不等于已在 GitHub 运行成功。

停止本项目开发容器并保留数据：

```powershell
docker compose --env-file deploy/versions.env --env-file deploy/.env -f deploy/compose.yml down
```

不加 `-v`，不自动删除 MySQL/Redis 卷。若需要重建空库，应先确认数据可丢弃并单独执行；日常验证使用临时测试容器。

依据：[Compose 变量与多 env-file](https://docs.docker.com/compose/how-tos/environment-variables/variable-interpolation/)、[Flyway Maven 配置](https://documentation.red-gate.com/flyway/reference/usage/maven-goal)、[Testcontainers JUnit 5 生命周期](https://java.testcontainers.org/test_framework_integration/junit_5/)。
