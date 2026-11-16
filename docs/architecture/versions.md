# 版本锁与支持状态

核对日期：2026-11-15 至 2026-11-16。下面是学习项目基线，不是生产安全认证。

| 组件 | 固定版本 | 管理位置 / 依据 |
|---|---|---|
| Java | 21；本机验证 Temurin 21.0.13+9 | Enforcer 限制 [21,22)，本地 JDK zip 已对照官方 SHA256 |
| Maven | 3.9.11 | Wrapper 3.3.4，发行包 SHA256 锁定；已核对 Central SHA512 |
| Spring Boot | 3.5.16 | 根 parent，3.5 系列最后一个 OSS 发布版 |
| Spring Cloud | 2025.0.3 | 根 Cloud BOM，解析 commons/context 4.3.3 |
| Spring Cloud Alibaba | 2025.0.0.0 | Alibaba BOM，discovery starter |
| Nacos | 客户端/服务端 3.0.3 | BOM 实际解析 + 本地发行包真实注册发现 |
| RocketMQ | 原生 remoting 客户端/Broker 5.3.1 | 根 dependencyManagement + 本地发行包真实收发；不使用 gRPC client-java/Proxy |
| Node / npm | 本机 22.14.0 / 10.9.0 | .node-version、packageManager、engines；不声称是当前最新安全补丁 |
| Vue | 3.5.45 | package.json + package-lock.json |
| Vite / Vue 插件 | 7.3.8 / 6.0.1 | package.json + package-lock.json |
| TypeScript / vue-tsc | 5.9.3 / 3.1.1 | package.json + package-lock.json |
| MySQL / Redis | 8.4.13 / 7.4.12 | deploy/versions.env；Day 2 已做 Compose 与真实集成验证 |

镜像使用 tag 与不可变 manifest digest，见 [versions.env](../../deploy/versions.env)。Day 2 已添加 MySQL/Redis Compose 和测试依赖；容器启动验收状态见 [Day 2 日志](../progress/day-02.md)。Nacos/MQ 的 Day 1 验证用相同版本的原生发行包，不等同于镜像验证。

## Day 2 依赖补充

全部沿用 Boot 3.5.16 已管理的精确版本，没有升级根 BOM：

| 组件 | 实际解析版本 | 范围 |
|---|---|---|
| Flyway core / mysql / Maven plugin | 11.7.2 | 测试 + 显式迁移插件 |
| MySQL Connector/J | 9.7.0 | 测试 + 迁移插件 |
| Lettuce | 6.6.0.RELEASE | 测试 |
| Testcontainers | 1.21.4 | 测试 |
| Maven Failsafe | 3.5.6 | integration profile |

[Day 2 依赖树](../reports/day2/trade-dependencies.txt)保存实际解析结果。客户端均不进入生产运行包，不触发业务应用的数据源/迁移自动配置。本地便携 Compose CLI 2.39.4、actionlint 1.7.7 只用于校验，保存在忽略的 .tools 中。

Day 2 后续使用用户安装的 Docker Desktop 完成实际容器验证，client/server 29.9.3、Compose 5.7.0。Flyway 11.7.2 对 MySQL 8.4 发出高于已测试版本的警告；本地两轮测试通过不等于扩大官方支持范围，详见 Day 2 报告。

## 官方关系与支持边界

- [Cloud 支持与 Boot 对应关系](https://github.com/spring-cloud/spring-cloud-release/wiki/Supported-Versions)：2025.0 对应 Boot 3.5。
- [Alibaba 官方版本关系](https://sca.aliyun.com/en/docs/2025.x/overview/version-explain/)：2025.0.0.0 基于 Cloud 2025.0.0 / Boot 3.5.0，Nacos 3.0.3、RocketMQ 5.3.1。本项目使用同代较高补丁 Cloud 2025.0.3 / Boot 3.5.16，并用启动、兼容检查和真实客户端实验验证，不把版本表当运行证据。
- [Nacos 2025.x 快速开始](https://sca.aliyun.com/en/docs/2025.x/user-guide/nacos/quick-start/)：public/空 namespace 使用 Nacos 3.x。
- [Boot 3.5.16 官方发布公告](https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/)明确它是最后一个 3.5.x OSS 发布。按计划保留 3.5 供本地学习，不描述为仍有免费安全维护。对外发布前必须决定升级受支持版本或采用商业支持，不能仅以“兼容”证明“安全”。
- [Spring 安全公告](https://spring.io/security/)已查阅；当前未引入 Security、Data JPA、Cloud Config 等组件。未做全量 Java 传递依赖 CVE 扫描，不能宣称零漏洞；Nacos/MQ 固定的是兼容基线，也不宣称最新版本。
- 前端首次审计发现 Vue server-renderer 与 Vite 公告漏洞，分别更新到 3.5.45 / 7.3.8；最终完整 npm audit 为 0 项。这只覆盖当次 npm 公告数据库，不覆盖 JDK/Node/Java 库和镜像。
- MySQL/Redis 版本和 digest 来自 Docker Hub 官方仓库标签 API；只记录实际可查询值，不据此推断支持状态或安全性。

## 依赖树如何解释

[Trade 依赖树](../reports/day1/trade-dependencies.txt)和 [Probe 依赖树](../reports/day1/probe-dependencies.txt)由 Maven 导出。Web/Actuator 属于 Boot 管理；Cloud、Alibaba 只在可选 probe 中引入实际组件。contracts 目前为空，空 JAR 警告符合 Day 1 边界。

Probe 中 RocketMQ → remoting → common → commons-validator 带入 commons-logging，与 Spring 的 spring-jcl 重复；仅从 RocketMQ 依赖入口排除 commons-logging，保留 spring-jcl。没有关闭 Cloud compatibility verifier，也没有为消除警告随意覆盖整套传递依赖。

Maven 仍报告 javassist 3.21.0-GA 传递 POM 的 effective-model 警告，以及测试框架 Mockito 动态 agent 警告；记录为已知警告，不隐瞒、不扩大本日范围。MQ pull API 有弃用提示，此处仅用于 Day 1 一条消息的读回探针，后续业务消费方案在消息阶段实现。

## 可重现校验值

- Maven 3.9.11 zip SHA256：`0d7125e8c91097b36edb990ea5934e6c68b4440eef4ea96510a0f6815e7eeadb`。
- 本机 Temurin 21.0.13+9 Windows x64 zip SHA256：`a4dbaa05fca4bd89d84584e58c459bd732efebb1722af80ab13c6516832004f9`。
- JDK [官方校验文件](https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13%2B9/OpenJDK21U-jdk_x64_windows_hotspot_21.0.13_9.zip.sha256.txt)；Maven [官方 SHA512](https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.zip.sha512)。
