# Day 7：第一周工程复盘

日期：2026-11-16（上海）。工程复盘完成；用户独立讲解/闭卷复现待完成，因此未创建v0.1标签或Release。本轮最多推进Day7，不实现Day8业务，不修改PDF源稿或归档。

## 开工核对与最小方案

开工本地/远端main同为566e5c0，Day1–6记录完整，无Day7报告或同项目其他在途聊天。保留原.gitignore、docs/handbook和scripts/build_handbook.py改动，未纳入本次提交。读取三份planning、约束、版本、进度及身份/前端相关代码。

三组独立审查：①安全登录/Bearer/refresh图、ADR与历史版本文档纠偏；②真实本地录屏、回归和脱敏证据；③独立学习清单、进度与必要入口更新。涉及architecture/week1-security.md、adr-001-week1-baseline.md、versions.md、runbooks/week1-review.md、本报告、reports/day7、README与计划入口。未新增框架、服务、迁移、业务代码或伪造测试。

## 五项验收与实测

1. 安全链路图映射真实AuthService/AuthenticationManager、Bearer过滤器、当前DB角色、方法授权与事务；区分生产用户归属和测试订单夹具，明确330秒access窗口及重放撤销整族策略。
2. JAR清单确认Security6.5.11、JDBC6.2.19、Hikari6.3.3、MySQL驱动9.7.0，运行包无Flyway。修正versions.md仍把Security/驱动称为未引入或仅测试的当前时态；保留Day1/2历史。官方Boot公告再次核对为OSS结束，未升级BOM或宣称生产安全。
3. [真实浏览器结果](../reports/day7/browser-demo.txt)：匿名转登录、登录成功、重载旋转refresh、买家/admin转403、直接管理API403且有trace、退出后旧refresh401和storage清空。真实Trade JAR/database profile+MySQL+Vite代理，未用mock替代现场演示。
4. [后端verify](../reports/day7/backend-verify.txt) BUILD SUCCESS：真实IT25、普通后端测试5，0失败/错误/跳过；[前端测试](../reports/day7/frontend-test.txt)12项通过，[类型检查/生产构建](../reports/day7/frontend-build.txt)成功。合计42项。现有负例涵盖错误凭据、JWT声明/算法、旋转并发、禁用、对象授权、审计失败回滚和会话异步竞争，不是新增42个测试。
5. [短演示](../reports/day7/identity-demo.webm)为真实连续画面：仅裁去开始67秒等待，最终13.416秒、1000×760、12fps，478269字节；SHA256及抽帧位置见[manifest](../reports/day7/video-manifest.json)。检查2/4/5/6/8/11秒帧，文字可读，密码未显示，未打开令牌存储或请求详情。[账号](../reports/day7/account.png)、[拒绝页](../reports/day7/forbidden.png)截图也已查看。

## 实际命令与失败修正

根目录Maven：`. ./scripts/env.ps1`后运行`./mvnw.cmd -B -ntp -Pintegration verify`，本次从Docker引擎未启动开始，启动用户既有Docker Desktop后取得linux并完成验证。web目录`npm test`、`npm run build`、`npm audit --json`。后者返回4项moderate、0 high/critical，仍是测试依赖链的已知问题，见[npm-audit.json](../reports/day7/npm-audit.json)，未称全依赖零漏洞。未做性能测量、JDK/全部Java CVE扫描或全量镜像扫描。

显式`migrate-day2.ps1 -Action validate`[通过](../reports/day7/migration-validate.txt)，未清库/repair/新增种子；录屏合成用户和refresh记录已删除，[剩余0](../reports/day7/fixture-cleanup.txt)。本次浏览器/Vite/Trade进程结束，开发MySQL卷保留。

第一次视频抽帧尝试使用精简Playwright ffmpeg不包含的fps滤镜，失败记录保留在video-verify.txt；改用-ss与-frames:v 1成功解码，最终[decode记录](../reports/day7/video-decode-final.txt)对应裁剪后的文件。原始80.416秒录屏与检查帧留在忽略的output/playwright，正式证据仅保留短片。Git日志文本只规整行尾空白，未改写结果或时序。Maven打包前停止演示JAR以避免Windows文件锁。

## 主要边界和剩余事项

GitHub交付：复盘提交[1d57043](https://github.com/JoeyXXia/trade-java/commit/1d57043cdf74f8827012abf33e226e8b0a5de4b2)已推送，核对远端main SHA一致。[Actions 43881207326](https://github.com/JoeyXXia/trade-java/actions/runs/43881207326)结论success；checks、integration均成功（含前端测试/构建/审计及两轮真实容器回归），[远程步骤证据](../reports/day7/github-actions.json)绑定该SHA。此处是远程实测，不由本地结果推断。

未发现必须改动业务实现的新缺陷；本日文档纠偏不能包装为安全漏洞修复。当前只是本地学习身份基线，无生产订单、支付或跨服务保证。sessionStorage不防XSS、复制标签页可能重放、DB每请求检查有依赖成本，敏感操作并发状态边界未声称全局线性化；版本支持与4项中危测试依赖仍需后续专项处理。

[复现与学习清单](../runbooks/week1-review.md)列出集合、泛型、equals/hashCode、代理事务与认证授权问题。AI运行验证不等同用户能独立解释；v0.1门槛保留，人工清单不单独阻塞后续已授权工程。下一轮按真实进度选择Day8，本轮不提前实现。
