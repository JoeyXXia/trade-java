# Day 6：Vue页面壳、会话恢复与CI

日期：2026-11-16。工程范围仅Day 6；未推进Day 7。基于Day 5已有身份API，后端业务代码和迁移不变。

## 当前实现与最小方案

原前端仅健康检查页，没有Router、状态管理或登录流程。本次分三组供独立审查：

1. 锁定Vue Router 4.6.5、Pinia 3.0.4、Element Plus 2.14.8；App导航、登录/注册买家、个人空间、运营/管理员壳、403页；Vite增加/api同源代理。
2. api.ts运行时响应校验与错误结构，stores/session.ts内存access+标签页refresh恢复、合并刷新、一次重试、注销及异步竞争保护；router.ts角色守卫。
3. Vitest 4.1.12、Vue Test Utils 2.4.6、jsdom 27.4.0的组件/路由/会话测试，CI新增npm test，真实浏览器验收和本报告。

涉及文件：web/package{,-lock}.json、vite/vitest配置、src/api.ts、router.ts、stores/session.ts及测试、App/main/style、views五个页面及登录组件测试，.github/workflows/ci.yml。没有增加Axios、UI自动导入插件、服务端会话框架或新服务。

## 五项可观察验收

1. 真实页面注册买家并登录到/account，展示服务端用户ID/角色；登录错误显示code/trace，密码提交后清空。后台入口按角色展示，不提供尚未实现的商品/订单写入功能。
2. 页面重载后通过旧refresh换取新令牌，仍能显示本人；仅refresh进入sessionStorage，access保留内存，localStorage为空。
3. 并发401只发一次refresh，各请求最多重试一次；较晚到达的旧401复用已经更新的access，不再旋转；403不刷新。重试再401则清理会话。
4. 匿名转登录，BUYER访问/admin、/operations转403页，OPERATOR只允许运营入口，ADMIN允许管理入口；真实买家直接POST管理API也403，前端守卫不替代后端授权。
5. 退出清空本地身份，真实旧refresh再调用401；刷新或本人响应晚于退出不能恢复旧身份；网络注销失败明确提示服务器撤销未确认。390×844窄屏无横向溢出，1280×720桌面截图已检查。

## 实际验证证据

- [npm ci](../reports/day6/npm-ci.txt)完成清洁安装；[最终前端测试](../reports/day6/test-final.txt)3文件12项通过，0失败；[vue-tsc与生产构建](../reports/day6/build-final.txt)通过。首轮10项通过后补充延迟401与重试上限两项。
- 真实Docker MySQL已启动并[显式迁移](../reports/day6/migrate.txt)至V4；JAR以trade_app启动。Playwright通过真实UI注册/登录；[重载与权限结果](../reports/day6/browser-session.txt)、[退出与窄屏结果](../reports/day6/browser-logout.txt)仅保存布尔值与状态码，没有保存令牌或浏览器认证状态。
- [桌面截图](../reports/day6/login-desktop.png)、[窄屏截图](../reports/day6/login-mobile.png)均人工视觉检查。组件测试用模拟HTTP确定性覆盖竞争边界；真实联调验证本地浏览器→Vite代理→JAR→MySQL，不冒充完整订单E2E或多浏览器矩阵。
- [全依赖审计](../reports/day6/audit-initial.json)有4个moderate、0 high/critical，均来自测试工具js-beautify/editorconfig/glob/minimatch链；[生产依赖审计](../reports/day6/audit-production.json)0项。现有[high阈值](../reports/day6/audit-high.txt)通过，不把“低于阈值”写成全依赖零漏洞。未强制跨主版本audit fix。
- 功能提交：9a74c0f301c1120be4e4daa4de62155a316a45c5。GitHub Actions运行：[43848857292](https://github.com/JoeyXXia/trade-java/actions/runs/43848857292)，2026-11-16 18:30:47（上海）完成，conclusion=success。checks包含npm ci/test/build/audit，integration包含Docker Linux检查及两轮真实集成测试，均success；[远程步骤证据](../reports/day6/github-actions.json)记录对应SHA、job与step结果。

本次浏览器合成账号day6-browser-20261116及其refresh记录已清理，按该登录名查询剩余0；没有添加演示种子。验收浏览器、Vite及Trade进程已停止，开发MySQL卷保留。未修改PDF手册或推进Day 7。

## 实际失败与修复

npm 10解析测试依赖的另一版Vite时出现edgesOut内部错误，overrides将其统一为项目直接锁定的Vite7.3.8；Vue Test Utils2.6间接nopt要求比本机22.14更高的Node22补丁，改选2.4.6，保留engine-strict。锁文件通过npm ci实际重建，未升级Node或绕过peer/engine检查。

Windows下Playwright CLI多行run-code参数解析失败，合并单行后完成真实检查；这是工具调用修复。清洁安装首次因停止Vite时误用相对路径而锁住esbuild，修正为已记录且命令行核实的PID后停止，重做npm ci/test/build成功。没有把失败尝试当作通过。

## 最可能失败的边界与取舍

sessionStorage仍可被同源脚本读取，不能防XSS；本方案为了本地学习和标签页隔离，不宣称HttpOnly级保护。复制标签页可能复制refresh，严格重放策略会使整族撤销；没有跨标签页锁。刷新请求网络超时可能已在服务端旋转，此时清理本地状态并重新登录，不无限重试旧令牌。

epoch阻止异步旧响应跨越退出/重新登录后恢复旧用户。刷新为每标签页单个Promise，request只对401进行一次重试，不对网络失败自动重放写请求。10秒请求超时是配置值，未测量响应时间或吞吐量。

路由角色是UI提示；数据库角色变化仍由每次API的后端当前角色检查裁决。生产history路由需要静态托管回退到index.html及同源/api反向代理，Vite dev proxy不能当生产部署配置。当前没有部署公网、CSP体系、浏览器跨版本验收或商品/订单界面。用户亲自学习复现仍待完成。

学习资源：[Vue Router守卫](https://router.vuejs.org/guide/advanced/navigation-guards.html)、[Pinia状态与动作](https://pinia.vuejs.org/core-concepts/)、[Element Plus使用方式](https://element-plus.org/en-US/guide/quickstart.html)、[Vitest入门](https://vitest.dev/guide/)。结合本项目解释“TS类型断言不验证网络JSON”“响应式用户状态与非响应式凭据的边界”“为何必须串行刷新”。
