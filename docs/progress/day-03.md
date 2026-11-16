# Day 3：注册与凭据登录

日期：2026-11-16。工程与本地验收完成；人工学习复现不冒充已完成。

## 当前实现与最小方案

基于 Day 2 的用户表、显式 Flyway、容器隔离，分三组实现：V2 角色字段及 database profile；BCrypt/AuthenticationManager/过滤链及两个接口；真实 MySQL 负例与启动说明。未做令牌与限流，留 Day 4；没有 ORM 或新服务。

文件：Trade POM、identity 包、application-database.yml、V2 SQL、UserAuthenticationIT、原迁移回归测试和 scripts/start-trade.ps1。

## 五项验收及实际结果

1. POST /api/auth/register 固定 BUYER，登录名转小写，真实唯一约束处理重复为409；角色字段注入400且无插入。
2. 密码最少12字符，最多72字符且UTF-8最多72字节；BCrypt cost10，数据库哈希60字符。响应、DTO/Principal toString及捕获日志不包含测试口令或哈希。
3. POST /api/auth/login 用 DaoAuthenticationProvider/PasswordEncoder 校验；错误口令与未知账号均401、相同 code/message，未声称响应时间恒定。
4. 过滤链无默认用户、Basic、表单登录或session cookie；匿名私有路径401，health200，env404。登录目前仅返回验证后的用户视图，尚不颁发可复用凭据。
5. 默认模式仍可无数据库启动；显式database profile需环境配置、可连接账号及已迁移表，缺失配置实际启动失败，不自动迁移/清库。

实际命令：根目录 `./mvnw.cmd -B -ntp -Pintegration verify`。结果 BUILD SUCCESS，普通HTTP测试4、IT12（原MySQL6+Redis1+身份5），0失败/错误/跳过。输出：[完整验证](../reports/day3/verify-initial.txt)。随后直接运行JAR的database profile、清空本进程数据库配置，启动非零退出，见[启动负例](../reports/day3/missing-database-config.txt)。

## 不变量、事务及边界

注册不接受角色/ID/哈希；用户唯一键裁决并发，注册写入本地事务。密码不trim，不静默截断；用户名限制ASCII后统一小写，不按数据库大小写规则猜测身份。

DataSourceAutoConfiguration明确排除，由database profile显式创建Hikari、JdbcTemplate、事务管理器并验证连接/列；这是health-only模式与业务模式的边界，database配置错误不会降级为健康模式。Flyway仍显式执行。Spring Security引入的错误不输出内部异常或请求值。

最可能失败：已有库未迁移V2；UTF-8字节数超过BCrypt边界；新依赖触发意外数据源；注册竞争唯一键；异常处理泄漏输入。上述主要路径均有真实测试；未做登录抗暴力、token或对象授权，不宣称具备这些能力。

原Day2迁移测试更新为检查“全部待迁移记录应用一次、再次执行0项”，checksum负例复制完整迁移集后只改V1，避免新增V2后把缺文件误当checksum覆盖。

学习：解释请求过滤链与控制器认证入口、AuthenticationManager/Provider/UserDetailsService职责；跟踪密码校验失败到401的路径。人工复现待做。沿用Boot支持期、Flyway版本警告及Mockito agent警告，不隐瞒或扩大本次依赖升级。
