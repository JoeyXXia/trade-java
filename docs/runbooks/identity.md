# 身份接口与本地运行

先启动 Day 2 Compose，再显式运行 `scripts/migrate-day2.ps1`。构建完成后执行 `scripts/start-trade.ps1`，它从忽略的 deploy/.env 读取trade_app口令，不输出口令；固定启用database profile。没有该profile时仅为健康骨架，不提供身份业务接口。

POST /api/auth/register：JSON字段loginName、password。用户名ASCII字母数字开头，3-64字符，允许字母数字及_.-，服务端转小写；密码12-72字符、UTF-8最多72字节，不trim。注册固定BUYER，不接受额外字段。成功201返回字符串id、loginName、role；重复409，非法400。

POST /api/auth/login：相同字段，验证已有BCrypt凭据；成功200用户视图，错误口令/未知用户均401 INVALID_CREDENTIALS。Day 3只是凭据验证，未颁发session/token；后续Day 4新增令牌。不要把此阶段接口当成完整会话系统部署公网。

密码不入日志或响应；没有默认管理员/默认口令。OPERATOR/ADMIN只能通过受控数据操作或后续管理流程创建，不能通过注册字段指定。HTTPS、部署访问控制与支持版本评估仍是正式部署前要求，当前服务绑定本地回环。

默认普通构建：`./mvnw.cmd -B -ntp verify`。完整真实数据库验收：`./mvnw.cmd -B -ntp -Pintegration verify`，需要Docker Linux引擎；不跳过引擎错误。
