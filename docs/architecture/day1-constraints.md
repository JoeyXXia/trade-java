# Day 1 约束确认

日期：2026-11-15。依据 `docs/planning` 三份计划；本日范围为工程初始化、版本锁定、兼容性和启动验证。

## 架构与业务不变量

- 前四周模块化单体：用户、商品、库存、订单、优惠和履约留在 trade-service；第五周才拆 inventory-service。
- payment-simulator 独立进程，未来独立 schema/账号；禁止跨服务 JOIN、Repository 共享和直接写对方数据库。
- contracts 只放 DTO/事件契约。本日无数据库、状态迁移或业务事务；健康检查只证明进程就绪。
- 金额为 CNY 分（long，检查溢出），API 使用字符串，价格由服务端计算并保存快照。
- 库存 `0 <= reserved <= physical`，available 为差值；支付不重复扣实物库存，MySQL 是事实源。
- 支付与关闭由提供方原子状态转换仲裁；未知保持 CLOSING，确认关闭后才释放库存/券。
- Outbox/Inbox 与各自本地业务同事务，必须接受重复消息；补偿是新业务动作。
- 买家只能访问自己的资源；内部调用也要可信身份；敏感信息和密钥不进仓库。

以上为后续实现约束，本日未声称实现这些业务保证。

## 本日不做

不做 MySQL/Redis Compose、迁移、Testcontainers、CI（Day 2）；不做注册登录（Day 3）、令牌（Day 4）、授权（Day 5）、Router/Pinia/Element Plus 页面壳（Day 6）。不提前做服务拆分、Feign、治理、数据库模型、库存、订单或支付账本。

长期不做项沿用总计划：多商家、复杂运费税费、真实支付/物流、部分退款、推荐搜索引擎、K8s、分库分表。治理只选 Resilience4j，不叠加 Sentinel。

## 依赖与自动装配取舍

Boot parent 固定插件和基础库版本；Cloud BOM 固定 Cloud 组件；Alibaba BOM 固定 Nacos 客户端。BOM 是版本管理，不会自行加入运行依赖。普通服务只有 Web/Actuator，不会因 BOM 自动连接 Nacos 或 MQ。

兼容性探针通过可选 Maven profile 加入，不参与业务运行。它加载真实 Nacos discovery starter，保持 Cloud compatibility verifier 开启；默认关闭自动注册以便无中间件启动测试，真实注册发现另有 live 测试。MQ 使用原生客户端，避免过早引入整套消息自动装配。

Actuator 仅暴露 health、隐藏详情，服务绑定 127.0.0.1；env/beans 返回 404。有意保持无数据库配置，Day 2 再添加持久化。

Maven 不使用范围或动态 RELEASE 依赖；Wrapper 固定 Maven，前端直接依赖使用精确版本并提交 package-lock。Maven 的可复现性来自固定 parent/BOM/插件及依赖树记录，不宣称有 npm 式全量传递依赖锁。

## 学习与人工验收

解释 `@SpringBootApplication` 的组件扫描与条件自动配置：Web starter 触发 Servlet 容器，Actuator 提供 health；没有 JDBC starter 就不会创建 DataSource。通过探针理解 BOM 管理与 starter 引入的区别。

负例：JDK 17 构建应在 Enforcer 失败；使用不兼容 Cloud BOM 应被 compatibility verifier 拒绝；env/beans 不应开放。AI 运行结果不替代学习者亲自复现，人工复现状态记录在每日日志中。
