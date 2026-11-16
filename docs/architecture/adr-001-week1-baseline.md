# ADR-001：保留第一周版本与身份架构基线

日期：2026-11-16。状态：接受为本地学习基线；公网部署支持期评估待完成。代码基线566e5c0。

## 背景与决定

项目总计划固定Java21、Boot3.5/Cloud2025.0同代关系。第一周需要建立可解释的身份链路，而非提前引入服务拆分和治理。保持现有根BOM、镜像digest和package-lock，Day7不升级整套依赖、不增加服务。兼容探针保留为可选profile，Nacos/MQ不作为身份运行的前置条件。

Boot3.5.16已结束OSS发布，官方[发布公告](https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/)在本日再次核对；[Cloud支持表](https://github.com/spring-cloud/spring-cloud-release/wiki/Supported-Versions)给出2025.0对应Boot3.5。兼容运行不等于仍获免费安全修复。正式暴露环境前需独立安排升级到支持组合或商业支持，重新做兼容探针、回归和镜像评估；本日没有作生产安全认证。

## 当前依赖用途

| 组件 | 本项目实际版本/范围 | 决策理由与边界 |
|---|---|---|
| Java / Maven | 21（本机21.0.13+9）/3.9.11 | Enforcer与Wrapper固定，不宣称最新补丁 |
| Boot / Cloud / Alibaba BOM | 3.5.16 /2025.0.3 /2025.0.0.0 | 同代基线，探针有真实历史验证 |
| Security | 6.5.11，运行包 | BCrypt及Resource Server/Jose，不自写JWT签名算法 |
| JDBC / Hikari / MySQL driver | 6.2.19 /6.3.3 /9.7.0，运行包 | database profile显式数据源，JDBC直接可审SQL/锁序 |
| Flyway | 11.7.2，测试与显式插件 | 运行包无Flyway，不随启动迁移；MySQL8.4支持警告保留 |
| MySQL / Redis | 8.4.13 /7.4.12，digest见deploy/versions.env | MySQL是身份事实源；Redis当前只做基础设施验收 |
| Vue / Router / Pinia / Element Plus | 3.5.45 /4.6.5 /3.0.4 /2.14.8 | 已需要的页面壳依赖 |
| Vite / TS / Vitest | 7.3.8 /5.9.3 /4.1.12 | 锁定版本；Vite override统一测试依赖使用的主版本 |
| Vue Test Utils / jsdom | 2.4.6 /27.4.0，dev | 与本机Node22.14、npm10.9严格engine兼容 |

后端范围以[当日包清单](../reports/day7/runtime-libraries.txt)为证，前端以package-lock及[测试/审计证据](../progress/day-07.md)为准。原versions.md的Day1/Day2段是当时状态：Day3已引入运行期JDBC/驱动/Security，Day4已引入JWT，Day6已有前端状态管理。当前没有MyBatis或ORM；后续业务若需要再以新ADR决定，不在复盘日补装框架。

## 身份方案及取舍

- Stateless Bearer access+MySQL refresh：支持明确撤销与重放检测；每次鉴权查询DB，不能把JWT存在等同于完全无状态或零DB开销。
- HS256与环境密钥：单体学习环境足够，至少32随机字节，初始化脚本生成64字节；密钥不进仓库。没有密钥轮换、多issuer或第三方授权服务器。
- 一致user→refresh加锁顺序与本地事务：轮换与禁用有持久裁决。Reused必须提交撤销；审计异常必须回滚禁用，两类异常不能套同一回滚规则。
- cookie/session未用于认证，因此关闭CSRF；改为cookie必须重新审查CSRF、SameSite及跨站行为。sessionStorage仅标签页隔离，不能防同源脚本窃取。
- URL角色+方法授权+owner校验：UI隐藏入口只改善体验，服务端是安全边界。生产订单未实现，测试owner夹具不升级为订单功能声明。

## 风险与替代方案

本次npm全依赖审计仍有4项moderate（测试工具链）、无high/critical，见当日报告；不通过强制跨主版本覆盖掩盖问题。Java依赖与镜像尚无完整CVE扫描。Flyway警告、Boot支持期、Node补丁状态均需要在部署阶段再次评估。

替代：现在整体迁移Boot4/Cloud新代会把兼容性与身份复盘混在一起；现在增加Redis会话或认证服务会增加一致性与部署边界。本次保持基线，后续以明确触发条件重新决策。基础缺陷若由复盘负例证实则针对修复，不为制造提交而重构。
