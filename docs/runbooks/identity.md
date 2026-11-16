# 身份接口与本地运行

先启动 Day 2 Compose，再显式运行 `scripts/migrate-day2.ps1`（包括新增迁移）。执行 `scripts/init-auth-secret.ps1`，向忽略的 deploy/.env 写入64随机字节的Base64密钥，已有配置不覆盖。构建完成后执行 `scripts/start-trade.ps1`，读取trade_app口令和JWT密钥，不输出秘密；固定启用database profile。没有该profile时仅为健康骨架，不提供身份业务接口。

POST /api/auth/register：JSON字段loginName、password。用户名ASCII字母数字开头，3-64字符，允许字母数字及_.-，服务端转小写；密码12-72字符、UTF-8最多72字节，不trim。注册固定BUYER，不接受额外字段。成功201返回字符串id、loginName、role；重复409，非法400。

POST /api/auth/login：相同字段，验证已有BCrypt凭据；成功200返回accessToken、tokenType=Bearer、expiresIn=300、refreshToken和user。错误口令/未知用户均401 INVALID_CREDENTIALS。JWT使用HS256，校验issuer/audience/时间/subject；GET /api/me使用Authorization: Bearer。每次认证读取当前数据库用户状态及角色。

POST /api/auth/refresh：JSON仅含refreshToken，成功返回新令牌对。refresh为32随机字节、43字符Base64URL；数据库仅存SHA-256。有效期从首次登录起7天，旋转不延长绝对期限。旧令牌再次使用401，且同family全部刷新凭据撤销。两个并发刷新只有一个成功，但失败方的重放检测也会撤销成功方的refresh；客户端必须串行刷新。

POST /api/auth/logout：JSON仅含refreshToken，204，同family撤销；不存在的合法格式令牌也返回204。注销后的access最多仍有效300秒加30秒容差；并未实现单令牌access黑名单。POST /api/admin/users/{id}/disable仅ADMIN可用，禁用账号并撤销该用户所有refresh；已有access下次请求也被拒绝。无默认管理员或公开提权接口。

登录入口按规范化账号+实际连接来源，在60秒固定窗口原子预约最多10次校验，成功清空；第11次429 LOGIN_THROTTLED、Retry-After: 60。忽略X-Forwarded-For，未经配置不信任代理头；此策略不是全站DDoS防护。尚无自动清理节流桶/过期会话的后台任务。

当前没有认证cookie/session，因此CSRF关闭；未来切换cookie必须重新设计CSRF与SameSite。密钥轮换、浏览器存储和跨标签页刷新协调尚未实现；不要记录令牌或将当前本地学习环境直接暴露公网。

GET /api/users/{id}仅本人可读；身份来自签名subject，查询userId和X-User-Id不能替换身份。运营能力使用ResourceAccess.requireOperations（OPERATOR/ADMIN），资源归属使用requireOwner（所有者），管理员禁用在URL与服务方法层限制。生产尚无订单或运营业务写入接口。

常见错误统一code/message/traceId，响应头X-Trace-Id为服务端UUID；反馈问题可提供该ID，不提供密码或token。禁用成功在identity_audit记录当前actor、target、USER_DISABLED、SUCCESS、时间和trace；审计失败回滚禁用与会话撤销。没有对外审计查询或修改接口。

密码不入日志或响应；没有默认管理员/默认口令。OPERATOR/ADMIN只能通过受控数据操作或后续管理流程创建，不能通过注册字段指定。HTTPS、部署访问控制与支持版本评估仍是正式部署前要求，当前服务绑定本地回环。

默认普通构建：`./mvnw.cmd -B -ntp verify`。完整真实数据库验收：`./mvnw.cmd -B -ntp -Pintegration verify`，需要Docker Linux引擎；不跳过引擎错误。
