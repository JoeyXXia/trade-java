# Day 8：商品与SKU

日期：2026-11-17（上海）。本轮仅Day8，复用JDBC、MySQL、Security、Vue/Router/Pinia/Element Plus；不引入ORM、缓存、图片服务、库存或订单。

## 开工现状与三组最小改动

Day7工程及Actions已成功，main开工为6550d55，本地远端一致，未发现另一轮同项目工作。人工讲解与v0.1标签保留待办，不阻塞工程推进。原.gitignore、docs/handbook、scripts/build_handbook.py修改保留且不纳入提交。

1. V5的catalog_product/SPU、catalog_sku、索引与约束；catalog包的严格金额/数量DTO、Repository、管理员服务及可售校验；统一错误响应补充。
2. 买家列表/详情/报价、管理员草稿/元数据/SKU/上下架页面；catalog.ts运行时类型校验与BigInt格式化；路由、导航与登录回跳。
3. CatalogIT真实MySQL负例、前端精度/组件/路由竞争测试、真实浏览器联调与本报告、运行手册、必要计划入口。

## 五项可观察验收条件

1. 金额为1至Long.MAX_VALUE的CNY分字符串，拒绝JSON数字、小数、指数、空白、前导零及溢出；9007199254740993分精确往返，乘2为18014398509481986分。总价只从DB单价乘数量，Math.multiplyExact拒绝溢出。
2. quantity只接收JSON整数1–999，拒绝字符串、浮点、0、负值及超范围；未知额外字段400。V5数据库CHECK也拒绝价格0。
3. 草稿/下架详情404；下架或禁用SKU报价409；上架须有启用SKU，最后启用规格需先下架才能禁用。旧RR快照之后改价/下架，可售guard仍用当前锁定读裁决。
4. 匿名目录401，BUYER/OPERATOR后台写403；管理API与方法层限制ADMIN。created_by/updated_by来自服务器主体。父商品version同版本并发修改一方200、一方409，避免覆盖。
5. 排序newest/oldest/title白名单、页码1–10000/每页1–50；值用SQL参数，注入标题按字面保存、排序注入拒绝。SKU编码唯一且不可变，复合索引存在；没有填写EXPLAIN/吞吐量等未测结果。

## 实际失败与修正

首轮[backend-initial.txt](../reports/day8/backend-initial.txt)因Docker Linux引擎未启动而失败，启动用户已安装Docker Desktop后继续，未跳过真实容器。第二轮[backend-retry.txt](../reports/day8/backend-retry.txt)发现@Repository代理对象的直接字段读取为null，商品写入500；改为给服务显式注入受管理JdbcTemplate、Repository字段私有。[定向修复](../reports/day8/catalog-fix.txt)6个商品IT成功后跑完整回归，再补RR旧快照与跨商品迟到响应边界，最终记录如下。这个失败说明代理对象不能当作公开字段数据包使用；不通过关闭代理绕过。

## 最终验证与交付

- 根目录`./mvnw.cmd -B -ntp -Pintegration verify`：[backend-final2.txt](../reports/day8/backend-final2.txt) BUILD SUCCESS，IT32（授权5、目录7、迁移6、Redis1、令牌8、凭据5）、普通后端5，0失败/错误/跳过。7项目录用例包括金额/数量非法、下架/禁用、角色/未知字段、编码/版本竞争、参数化/索引/约束以及旧RR快照后的改价与下架。
- web目录`npm test`：[frontend-verified.txt](../reports/day8/frontend-verified.txt)6文件18项通过；`npm run build`：[build-verified.txt](../reports/day8/build-verified.txt)类型检查与生产构建通过。前后端合计55项，不把旧测试算作新增55项。没有新增依赖；[当次审计](../reports/day8/npm-audit.json)仍为4项中危测试依赖、0高危/严重，未宣称零漏洞。
- [管理员真实页面结果](../reports/day8/admin-browser.txt)：从表单创建草稿、添加9007199254740993分SKU并上架；[买家结果](../reports/day8/buyer-browser.txt)：页面两件总价18014398509481986分、管理写403、390px无横向溢出；[下架结果](../reports/day8/off-shelf-browser.txt)：同一商品下架后详情404、报价409。用现有JAR+真实开发MySQL、Vite同源代理及隔离Playwright会话，不以组件mock替代联调。
- [显式V5迁移](../reports/day8/migrate.txt)成功。联调账号由本轮明确注册，并仅对该临时管理员做受控角色设定；没有新增默认管理员、迁移seed或公网初始化入口。[清理结果](../reports/day8/browser-cleanup.txt)账号与SKU剩余0，相关产品和refresh同事务删除；本次浏览器/Vite/Trade停止，开发卷保留。
- 已查看[管理员页](../reports/day8/admin-catalog.png)、[买家详情](../reports/day8/buyer-product.png)、[390px页面](../reports/day8/buyer-mobile.png)和[下架状态](../reports/day8/admin-off-shelf.png)。录屏/工具原始命令不作为交付，报告只保留脱敏布尔值、状态码和商品数值，无密码或令牌。

真实浏览器还发现原生input pattern在现代v标志下要求转义字符类中的连字符；修正后真实验证BAD CODE被拒、VALID-CODE接受，见下架结果文件。最终前端复验在此修复后执行。当前商品详情的旧路由迟到响应也有单独组件负例，避免快速切换商品后被旧响应覆盖。

后端与前端作为两项可独立审查的真实提交，报告与证据单独提交；最终远程SHA和CI状态在核对后补录，不将本地成功推断成远程通过。

第一轮远程[Actions 44001563989](https://github.com/JoeyXXia/trade-java/actions/runs/44001563989)在Maven Wrapper下载3.9.11时失败，尚未执行测试，integration被依赖跳过；[失败摘录](../reports/day8/github-first-failure.txt)保留。GitHub连接器请求重跑返回403 Resource not accessible by integration，未伪称已重跑。后续正常提交该失败记录与复现说明触发新一轮CI；没有空提交或为了网络故障随意升级构建工具。

最终交付：后端[9541e4b](https://github.com/JoeyXXia/trade-java/commit/9541e4b)、前端[6688b6b](https://github.com/JoeyXXia/trade-java/commit/6688b6b)、报告[9eb0c2f](https://github.com/JoeyXXia/trade-java/commit/9eb0c2f)，以及CI失败/复现说明832fb5a均正常推送。第二轮[Actions 44001949432](https://github.com/JoeyXXia/trade-java/actions/runs/44001949432)绑定832fb5a2f8289b5816918e65999e091ec6d67f46，2026-11-17 10:22:51（上海）完成success；checks和integration（两轮真实容器）均success，见[远程步骤证据](../reports/day8/github-actions.json)。业务代码与本地最终验收一致，远程结果来自实际查询。

Day8工程和必要验收已完成。本次未推进Day9，未修改PDF源稿、归档、既有未提交内容或创建v0.1标签；学习者本人独立复现仍待完成。

## 事务与范围边界

写入先锁product，再比较version，商品及其SKU共享聚合版本，每次成功+1。CatalogAvailability.requireSellable要求外层事务并锁product→SKU；SKU当前锁定读避免早先RR快照回读旧价。报价事务返回后锁释放，报价不锁定将来价格；未来订单须在自己的事务重新校验并保存快照。本日没有订单或库存，不能据此宣称已验证下单/防超卖。

SPU/SKU生命周期无删除接口、无批量操作、无图片上传、无库存数量；SKU编码全局唯一且不可变。当前目录要求登录，OPERATOR可浏览但不能编辑。价格上限是数据类型边界，不是性能指标或真实商业定价建议。已知Boot/Flyway支持警告、测试依赖中危问题与用户学习清单沿用真实状态。

技术拆解、完整API和复现命令见[商品运行手册](../runbooks/day8-catalog.md)。
