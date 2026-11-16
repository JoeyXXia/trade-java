# CommerceFlow Java

分布式电商交易与订单履约学习项目。Day 1/2 工程与数据库底座已验证，Day 3 已实现 BCrypt 注册与凭据登录、角色基础和安全过滤链。商品、订单、支付账本尚未实现。

[身份运行手册](docs/runbooks/identity.md) · [Day 3 验收](docs/progress/day-03.md)

[项目计划](docs/planning/CommerceFlow-Java-项目总计划.md) · [Day 1 约束](docs/architecture/day1-constraints.md) · [版本与支持状态](docs/architecture/versions.md) · [Day 1 进度](docs/progress/day-01.md)

[Day 2 启动与测试手册](docs/runbooks/day2-local.md) · [Day 2 实际进度与验收](docs/progress/day-02.md)

## 目录与边界

```text
backend/
  contracts/             仅预留 DTO/事件契约
  trade-service/         前四周的模块化单体，端口 8080
  payment-simulator/     独立模拟提供方骨架，端口 8081
  inventory-service/     第五周拆分预留，当前不是 Maven 模块
  compatibility-probe/  可选 profile；不参与业务部署
web/                     Vue 3 + TS + Vite，端口 5173
deploy/                  镜像锁、MySQL/Redis Compose、独立账号初始化
docs/                    计划、约束、版本、进度与验证证据
scripts/                 Windows 本地实验辅助脚本
```

## 本地构建与启动

要求 JDK 21、Node 22（>=22.12，当前 engines 限制 22.x）、npm 10，Windows 辅助脚本要求 PowerShell 7。Maven Wrapper 固定 3.9.11，首次运行需联网。当前 Boot 3.5 已结束 OSS 支持，仅作本地学习；详见版本说明。

**所有 Maven 命令在仓库根目录执行**，以便 `.tools/m2` 本地缓存路径一致。PowerShell：

```powershell
# 已设置 JAVA_HOME 时使用该 JDK；否则寻找 .tools/jdk-21*。
. ./scripts/env.ps1
# 可选：将 Wrapper 下载缓存也放进项目，避免写入用户目录。
$env:MAVEN_USER_HOME = Join-Path (Get-Location) '.tools/maven-user'
./mvnw.cmd -B -ntp verify
java -jar backend/trade-service/target/trade-service-0.1.0-SNAPSHOT.jar
```

另开根目录终端启动支付模拟器；没有模拟支付功能，当前仅验证独立进程：

```powershell
. ./scripts/env.ps1
java -jar backend/payment-simulator/target/payment-simulator-0.1.0-SNAPSHOT.jar
```

前端终端：

重新构建后端 JAR 前先停止对应 java 进程；重新执行 npm ci 前先停止 Vite。Windows 会锁住运行中的 JAR/esbuild.exe。

```powershell
cd web
npm ci
npm run dev
```

打开 [本地前端](http://127.0.0.1:5173)，点击“检查后端健康”，应显示 UP。Vite 将 `/actuator` 代理到 Trade 服务，不需要放开 CORS。前端 build 的部署反向代理留待部署阶段，`npm run preview` 仅适合静态预览。

```powershell
Invoke-RestMethod http://127.0.0.1:8080/actuator/health
Invoke-RestMethod http://127.0.0.1:8081/actuator/health
cd web
npm run build
npm audit --audit-level=high
```

两个服务绑定 127.0.0.1；Actuator 仅暴露 health、无详情，env/beans 应返回 404。应用仍不依赖 MySQL/Redis/Nacos/MQ 即可启动。Day 2 的数据库迁移必须显式执行，不随应用启动；没有演示账号或自动 seed。

macOS/Linux 可设置 JDK 21 的 JAVA_HOME 后在根目录运行 `./mvnw verify`，其余 java/npm 命令相同；本次仅实测 Windows PowerShell，未宣称跨系统验收。

## Nacos / RocketMQ 兼容性实验

不连接中间件的启动/依赖/兼容性负例：

```powershell
./mvnw.cmd -B -ntp -Pcompatibility verify
```

默认跳过两个 live 测试，不能用默认通过宣称真实注册/收发成功。真实实验需要 Nacos 3.0.3、RocketMQ Broker + NameServer 5.3.1。当前 Windows 无 Docker，使用发行包实测：

1. 将 [Nacos 3.0.3 zip](https://github.com/alibaba/nacos/releases/download/3.0.3/nacos-server-3.0.3.zip) 解压至 `.tools/nacos`。
2. 将 [RocketMQ 5.3.1 zip](https://archive.apache.org/dist/rocketmq/5.3.1/rocketmq-all-5.3.1-bin-release.zip) 解压至 `.tools/rocketmq-all-5.3.1-bin-release`。
3. 确保 8848、9848、9849、18880、9876、10909–10912 未占用；使用隔离的本地开发环境。探针未配置客户端鉴权，中间件不可用于公网。
4. 在根目录执行以下命令。检查 `.tools/nacos.out.log`、`.tools/namesrv.out.log`、`.tools/broker.out.log` 均启动成功后再跑测试；失败不能仅靠进程存在判断就绪。

```powershell
./scripts/start-day1-middleware.ps1
./mvnw.cmd -B -ntp -Pcompatibility '-Dmiddleware.live=true' verify
./scripts/stop-day1-middleware.ps1
```

也可使用已有同版本环境，传入 `-Dnacos.address=127.0.0.1:8848`、`-Drocketmq.namesrv=127.0.0.1:9876`。探针注册随机服务名并注销，MQ 创建随机 topic/group、只读回自己刚发送的消息；本地实验数据在 `.tools/runtime`。这只证明版本组合与基础通信，不证明 Outbox、ACK 重投、事务消息或业务一致性。

## 验证与学习

测试包括真实 HTTP 健康检查、诊断端点负例、Cloud compatibility verifier 负例，以及可选真实 Nacos/MQ 通信。JDK 17 应在 Enforcer 的 RequireJavaVersion 阶段失败。人工复现清单与实际执行结果见 [Day 1 日志](docs/progress/day-01.md)。

```powershell
./mvnw.cmd -B -ntp -Pcompatibility org.apache.maven.plugins:maven-dependency-plugin:3.8.1:tree '-DoutputFile=target/dependency-tree.txt'
```

前端提交 package-lock，后端固定 parent/BOM/插件并保存实际依赖树。集成测试显式使用 `-Pintegration verify`，普通 verify 不执行容器测试。Day 4 及之后尚未开始。旧版本手册保留其生成时的代码快照，不随新功能改写历史。
