# Day 1 — 范围与版本锁定

开始：2026-11-15；继续与收尾：2026-11-16（Asia/Shanghai）。仍只执行计划中的 Day 1，不因跨日自动进入 Day 2。

状态：**Day 1 工程实现与自动验证完成；学习者亲自复现待完成。**

## 本日目标与实际交付

以“后端健康接口、前端启动、可解释依赖树、版本锁”为验收目标。仓库原来只有三份计划，没有代码、Git 或额外 AGENTS.md。

- 确认并记录 [项目约束](../architecture/day1-constraints.md)：前四周模块化单体，支付提供方独立；库存拆分第五周；保留金额、库存、支付关闭、权限及消息事务约束。
- 初始化 Maven 父工程与 contracts、trade-service、payment-simulator；inventory-service 仅预留目录。独立 compatibility profile 加入探针，无业务微服务拆分。
- 两个 Boot 应用只提供 Actuator health，绑定回环地址；不暴露 env/beans，不连接数据库。
- 初始化 Vue/TS/Vite，提供健康检查按钮和失败反馈；未引入 Day 6 的 Router/Pinia/Element Plus。
- 固定 Java 21、Boot 3.5.16、Cloud 2025.0.3、Alibaba 2025.0.0.0、Nacos 3.0.3、MQ 5.3.1；提交 Wrapper 配置与 npm lockfile。
- [版本说明](../architecture/versions.md)记录 BOM 来源、支持状态、校验和及依赖警告；[镜像版本文件](../../deploy/versions.env)固定 tag 与 digest。
- 提供启动说明、本地中间件辅助脚本、实际依赖树、构建记录与浏览器截图。

## 实际命令与结果

以下由 Codex 执行，非学习者人工操作声明。完整命令前先在根目录加载 JDK：`. ./scripts/env.ps1`，本次 Wrapper 缓存在 `.tools/maven-user`。

| 命令 / 实验 | 实际结果 | 证据 |
|---|---|---|
| 首轮 `./.tools/apache-maven-3.9.11/bin/mvn.cmd -B -ntp -Pcompatibility verify` | 全模块成功；6 个测试通过，2 个 live 默认跳过 | 首轮 surefire 已被最终运行覆盖；不将跳过计为 live 成功 |
| 最终 `./mvnw.cmd -B -ntp -Pcompatibility '-Dmiddleware.live=true' clean verify` | BUILD SUCCESS；8 个测试，0 失败、0 错误、0 跳过 | [后端原始输出](../reports/day1/backend-verify.txt) |
| HTTP GET 8080、8081 的 `/actuator/health` | 两个独立 Java 进程均返回 200 / status UP | 后端测试覆盖真实 HTTP；浏览器见下 |
| 两服务 GET `/actuator/env`、`/actuator/beans` | 均为 404 | 两服务 HealthEndpointTest |
| Nacos live 测试 | 3.0.3 客户端连接 3.0.3 服务，注册随机实例、查询 IP/端口、注销成功 | LiveMiddlewareTest，最终运行 2 项均通过 |
| RocketMQ live 测试 | 5.3.1 客户端连接 5.3.1 Broker，SEND_OK，按返回队列/offset 读回完全相同 payload | 同上；含最多 10 秒等待 commitlog 分发 |
| Java 17 执行 `./mvnw.cmd -o -B -ntp -N validate` | 退出 1，明确在 RequireJavaVersion 拒绝 17.0.8 | [负例输出](../reports/day1/java17-negative.txt) |
| Cloud verifier 接受版本故意设为 0.0.x | 启动失败，断言 CompatibilityNotMetException；没有关闭兼容检查 | CompatibilityRejectionTest |
| Maven dependency plugin 3.8.1 导出 tree | 成功；Nacos 3.0.3、MQ 5.3.1、Cloud commons 4.3.3；排除重复日志桥后无 commons-logging | [Trade](../reports/day1/trade-dependencies.txt)、[Probe](../reports/day1/probe-dependencies.txt) |
| 最终 `npm --prefix web ci --cache .tools/npm-cache` | 根据 lockfile 重装成功 | [前端原始输出](../reports/day1/frontend-verify.txt) |
| `npm --prefix web run build` | vue-tsc 与 Vite 生产构建成功 | 同上 |
| `npm --prefix web audit --json` | 退出 0，0 项漏洞 | [审计 JSON](../reports/day1/npm-audit.json) |
| Playwright / Chrome 打开 5173，点击健康检查 | 页面从“尚未检查”变为 UP；修正 favicon 后成功页面无控制台错误 | [快照](../reports/day1/browser-up.yml)、[截图](../reports/day1/health-up.png) |
| 停止 Trade 后再点击健康检查 | 显示 HTTP 500，按钮恢复可点击；这是 Vite 代理连接拒绝的实际响应 | [快照](../reports/day1/browser-down.yml)、[截图](../reports/day1/health-down.png) |

测试计数：Trade 2 + Payment 2 + Probe 启动 1 + verifier 负例 1 + live 2 = 8。没有 mock 中间件成功；未把基础收发实验当成 MQ 重投、ACK 或事务验证。

## 实际判断、失败与修正

1. 没有现有业务代码可复用，因此只建立最小骨架；BOM 不等于引入组件，业务应用不添加 Nacos/MQ starter。兼容性检查放在单独探针，避免提前建设第五周架构。
2. 官方确认 Boot 3.5.16 是该代最后一个 OSS 发布。保持计划的 3.5 基线并记录支持风险，没有擅自升级整套技术栈；这不是生产安全结论。
3. AI 初稿使用 Vue 3.5.22、Vite 7.1.12，首次 audit 报 3 项 high。根据实际公告修正为 Vue 3.5.45 / Vite 7.3.8，重新生成锁文件，最终 audit 为 0。
4. Nacos 首次因必需 identity 配置为空失败；补随机身份后，server 分离部署模式又缺 HealthHandler bean，改用默认 merged 模式，console 端口改为 18880 避开 Trade 8080。失败进程未退出导致后续 Derby 占用，清理后启动成功。
5. 停止脚本初稿以 JSON 时间字符串比较进程身份，PowerShell 自动解析日期后比较不可靠；修正为 UTC ticks，实测停止 Nacos、NameServer、Broker 成功。只停止本任务记录的 PID 且核对启动时间。
6. Broker properties 用 ASCII 写入绝对中文路径，导致路径变成 ??、Broker 退出，首轮 live 报无路由；改为仓库内相对路径后启动，真实收发通过。
7. 依赖树定位到 RocketMQ 传递引入 commons-logging，排除该入口日志桥后保留 spring-jcl，最终启动没有重复日志桥告警。
8. Windows 上运行中的支付 JAR 阻止 repackage，运行中的 Vite/esbuild 阻止 npm ci。停止本任务进程后重新 clean verify / npm ci，最终均成功。README 记录操作顺序。
9. JDK 官方 hash 文件为二进制响应，第一次当字符串比较失败；正确按 UTF-8 解码后，下载包 SHA256 与官方一致。Maven 发行包也已对照官方 SHA512，Wrapper 固定 SHA256。

## 不变量、状态与事务边界

本日没有业务状态、写库或跨服务事务。健康检查 UP 只说明进程就绪，不能说明数据库、库存或支付正确。Nacos 测试只临时注册/注销实例；MQ 测试创建随机 topic/group 和一条本地测试消息。后续金额/库存/支付/消息约束以约束文档为准，本日没有实现或验证其业务保证。

## 限制与未验证项

- Docker 当前不在 PATH。镜像 tag/digest 已从 Docker Hub 查询锁定，但没有拉取或启动镜像；Nacos/MQ 用同版本原生发行包实测。MySQL/Redis 和 Compose 留 Day 2。
- 未做全量 Java 依赖/镜像漏洞扫描，未对 JDK/Node 声称最新安全补丁。Boot 3.5 OSS 支持已结束，不能直接沿用为公网生产基线。
- javassist 传递 POM、Mockito 动态 agent、MQ pull API 弃用警告见版本说明；不隐藏为“无警告”。
- 未验证 Linux/macOS、全新机器、CI、数据库、MQ ACK/重投、可靠消息、业务并发或安全身份；这些属于后续计划。
- 自动验证运行环境为 Windows / PowerShell 7.6.9、Temurin 21.0.13+9、Maven 3.9.11、Node 22.14.0、npm 10.9.0。开发工具下载到忽略的 .tools，没有修改系统 PATH。
- 验收浏览器和本任务启动的应用、中间件、Vite 均已停止；不保留无鉴权测试服务常驻。

## 学习者待亲自完成

- [ ] 独立执行 README 的构建、启动与健康检查。
- [ ] 使用 JDK 17 运行负例，读懂 Enforcer 报错。
- [ ] 运行 CompatibilityRejectionTest，说明为何必须保留 verifier。
- [ ] 对照依赖树解释 parent、BOM、starter 与条件自动配置各自负责什么。
- [ ] 用自己的话讲述一次失败（例如中文 properties 路径）与一次取舍（探针隔离、暂不拆库存）。

这些为人工学习清单，不把 AI 的运行记录冒充为用户已掌握或已亲测。

## Git 与建议提交

Day 1 验收结束时已在当前目录初始化 main 分支，当时尚未创建 commit 或远程推送。后续按用户要求统一提交至 JoeyXXia/trade-java，实际提交记录以 Git 历史为准。原始计划文档保持原内容，只添加执行记录入口。

可按实际工作拆分：

1. `chore: bootstrap reproducible application`：Maven/Wrapper、两个应用、Vue 工程和锁文件。
2. `test: verify day-one runtime compatibility`：HTTP 负例、Cloud verifier、真实中间件探针和辅助脚本。
3. `docs: record day-one constraints and verification`：README、约束、版本、报告、进度。

没有空提交、补造日期或编造 commit 链接。Day 2 未开始。
