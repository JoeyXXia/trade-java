# Day 4：令牌生命周期

日期：2026-11-16。沿用Day 3的JDBC、BCrypt和本地事务；未增加服务、Redis业务依赖或ORM。

## 当前实现、最小方案与审查分组

1. V3的用户enabled、refresh_session和login_throttle；JWT配置、数据库时区与密钥脚本。
2. TokenService/ActiveUserFilter、登录/刷新/退出/禁用接口及原子入口限流。
3. TokenLifecycleIT真实容器负例、运行手册和本报告。主要文件均位于trade-service的identity包及infrastructure测试包，另有start-trade/init-auth-secret脚本。

## 五项可观察验收

1. 登录返回Bearer令牌对，access 300秒、HS256；错误issuer/audience/签名/算法/过期和缺aud均401；正常Bearer可以访问/api/me。
2. refresh只存SHA-256，不存原文；旋转返回新令牌，旧令牌重放401并提交整族撤销。两个真实并发请求只有一个200，另一个401，成功方refresh也因重放而失效。
3. 退出204后refresh401；旧access仍可访问，明确承认最长300+30秒剩余窗口。重复退出幂等；过期refresh拒绝。
4. 买家禁用接口403；管理员禁用用户后，该用户既有access、refresh和再次登录均401。角色和enabled每次从当前数据库读取。
5. 同账号+真实来源前10次错误登录401，第11次429及Retry-After:60；伪造X-Forwarded-For不能绕过；其他账号仍401。无Set-Cookie，匿名请求不继承上一请求身份。

实际命令：`./mvnw.cmd -B -ntp -Pintegration verify`。最终[verify-retry.txt](../reports/day4/verify-retry.txt)为完整容器与普通HTTP验收记录；IT20（MySQL6、Redis1、凭据5、令牌8）与普通HTTP4，均无失败/错误/跳过。首轮12项通过后补充生命周期用例；新增测试首次因“过期令牌的iat晚于exp”夹具错误失败，见[原始失败](../reports/day4/verify-final.txt)，将iat调整到过去后完整复跑。未伪造远程CI或人工复现结果。

## 不变量、事务与最可能失败的边界

refresh绝对期限7天，旋转不滑动延期。用户行→令牌/族行采用一致加锁顺序，避免与disable反向锁序。旋转同事务标记旧令牌并插入新令牌；重放异常特设noRollbackFor，确保撤销不是随401回滚。禁用同事务修改用户和撤销其刷新会话，后续新建凭据必须锁定并检查用户状态。

数据库连接与会话固定UTC；refresh字节数、令牌算法及声明校验明确。JWT不含角色快照用于授权，当前用户状态来自DB。JWT密钥不进Git，测试密钥仅测试进程随机生成。未新增自建ThreadLocal；Spring Security负责请求身份上下文，真实“正常→无凭据→正常”请求覆盖了不继承身份的路径，未宣称覆盖所有线程调度情况。

最可能失败：客户端并发刷新误触发严格重放撤销；旧库未显式迁移V3；密钥缺失/太短；依赖DB不可用；注销后误认为access立刻失效。上述前两类会导致明确拒绝或启动失败，不隐式降级。无密钥轮换、全局IP限流、分布式压测或自动记录清理；节流桶和会话保留需要后续运维策略。Boot/Flyway支持警告沿用既有报告。

学习复现待用户完成：解释JWT签名与撤销差异、noRollbackFor的作用、SecurityContext在线程复用时的清理；亲自重放旧refresh并观察数据库撤销记录。
