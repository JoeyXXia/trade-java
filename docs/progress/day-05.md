# Day 5：对象授权、追踪与审计

日期：2026-11-16。基于Day 4的当前DB角色与Bearer身份，保留模块化单体；没有引入订单业务、AOP日志框架或外部追踪服务。

## 当前实现、最小方案与审查分组

1. ResourceAccess和方法授权：BUYER/OPERATOR/ADMIN，真实资源所有者判断；IdentityController增加本人用户查询，管理员操作同时检查URL和服务方法。
2. TraceFilter/TraceIds/ErrorResponses/ApiErrors：常见错误统一code/message/traceId；本地产生X-Trace-Id，日志MDC finally清理。
3. V4的updated_at、identity_audit，UserAdministration同事务禁用/撤销/审计；AuthorizationIT及TraceFilterTest负例与回滚测试。

## 五项可观察验收与范围

1. /api/me忽略查询userId及伪造X-User-Id；/api/users/{id}本人200，跨用户403，不回显对方用户。
2. 持久化的测试订单owner裁决访问，篡改userId仍403；所属买家可访问。订单表/接口仅存在测试夹具，生产订单待后续Day实现，T01这里只验初版辅助层。
3. BUYER运营能力403，OPERATOR/ADMIN允许；BUYER/OPERATOR不能禁用用户，即使绕过admin URL改走测试入口仍被方法授权拒绝。
4. 错误traceId与响应头一致且为服务端UUID，伪造trace不采纳；同一测试线程的成功与异常路径结束后MDC均为空。
5. 成功禁用的审计actor来自认证上下文，含target/action/outcome/时间/trace；人为使审计插入失败，账号状态与refresh撤销一起回滚。

## 实际验证

完整命令 `./mvnw.cmd -B -ntp -Pintegration verify`，BUILD SUCCESS：IT25（授权5、迁移6、Redis1、凭据5、令牌8），普通测试5（HTTP4、trace清理1），0失败/错误/跳过，见[最终执行日志](../reports/day5/verify-final.txt)。[首轮失败](../reports/day5/verify-initial.txt)保留：测试专用控制器未注册导致404，触发器故障注入受MySQL二进制日志权限限制；后改为显式Import和测试目标专属CHECK，不提升账号权限。人工复现和远程CI状态不冒充完成。

## 最可能失败的边界与学习

方法授权依赖代理，自调用会绕过切面；helper只能信任数据库owner，不自动查询不存在的订单。当前仅审计成功的用户禁用，不建设全量审计平台；审计写入失败必须回滚业务，不能用普通日志代替。常见输入/认证/授权/资源/DB错误已包装，未声称所有未知异常均自定义处理。

本次同步Servlet请求MDC清理有测试，异步线程传播及分布式追踪未实现。账号并发状态变更可能拒绝或事务失败，不声称全局线性化权限撤销。Boot/Flyway已知支持警告保留。

学习复现待用户完成：解释Filter→方法授权代理→事务→JDBC路径、RBAC与归属差异、自调用为何不能作为安全边界。没有把AI测试通过当作用户已掌握。
