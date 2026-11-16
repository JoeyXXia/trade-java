# 第一周复盘与讲解验收

从[安全链路](../architecture/week1-security.md)和[版本ADR](../architecture/adr-001-week1-baseline.md)开始，再看[Day7实测记录](../progress/day-07.md)。本页不是“学习者已经掌握”的证明。

## 90秒介绍：只讲当前已交付内容

CommerceFlow是Java交易与订单履约学习项目，目前完成工程、显式迁移、真实容器测试、身份和Vue页面壳。登录使用BCrypt，access短期JWT与数据库refresh旋转分工；每次请求检查当前用户角色和禁用状态。敏感禁用操作与审计同事务，归属辅助层拒绝伪造userId。前端用单个Promise合并刷新，处理退出与迟到响应竞争。商品、库存、订单、支付、消息和服务拆分仍按后续计划实现，不能在介绍时改成过去时。

## 学习者需独立完成的检查

| 题目 | 应能定位/解释 | 状态 |
|---|---|---|
| 从登录到Bearer请求 | AuthenticationManager与过滤器认证不是同一个入口；指出数据库查询及错误分支 | 待本人复现 |
| 退出为什么仍可用access | 300秒+30秒容差，refresh撤销和账号禁用的区别 | 待本人讲解 |
| 两个请求同时刷新 | 行锁与used_at，失败方为何撤销成功方refresh | 待本人讲解 |
| Reused与审计异常 | 一个noRollbackFor必须提交，一个必须回滚，指出代理调用位置 | 待本人讲解 |
| 猜别人用户/订单ID | 签名subject、当前角色、持久owner；订单仅测试夹具 | 待本人复现 |
| Map键与equals/hashCode | 相等对象必须同hash，冲突不等于相等，键参与比较的状态不应被随意修改 | 待本人练习 |
| 泛型与JSON边界 | JdbcTemplate Map值仍需运行时转换；TS User接口不能验证网络JSON | 待本人练习 |
| 集合并发边界 | ConcurrentHashMap不能代替跨实例数据库轮换事务；List.of只限制结构修改 | 待本人练习 |

建议闭卷写出不可变record键、Map查找与等价性小程序；先预测再运行，保存本人实际结果，不复写AI给出的预期为实测。可对照[Java21 Object契约](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Object.html#hashCode())及项目中的ResourceAccess、UserPrincipal、session.ts。

## 复现演示

按[身份手册](identity.md)启动真实本地MySQL、显式迁移、database profile的Trade，再按[前端手册](day6-web.md)启动Vite。使用独立合成账号，不把默认管理员或口令写进迁移。

演示顺序：匿名进入个人页回登录→登录/本人信息→页面重载仍登录→买家打开/admin显示拒绝→直接管理API403→退出→旧refresh401。录屏只显示页面，不展示开发者工具的请求体/Authorization、sessionStorage值或deploy/.env。演示仅证明身份链路，不证明生产订单T01全覆盖。

回归命令（根目录Maven，web目录npm）：`./mvnw.cmd -B -ntp -Pintegration verify`、`npm test`、`npm run build`、`npm audit --json`。audit有非零结果要读severity和依赖范围，不把构建成功当漏洞为零。

Day7工程交付与学习验收分开记录。用户完成独立解释及负例复现后，才能满足计划中的v0.1标记条件；当前不自动创建标签或GitHub Release。人工学习清单不冒充已完成，也不单凭它阻止后续已授权工程进度。
