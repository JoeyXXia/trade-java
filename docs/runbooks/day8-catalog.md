# Day 8：商品目录、精确价格与管理员编辑

沿用[身份启动](identity.md)与[前端启动](day6-web.md)。停止旧Trade JAR再构建，显式运行scripts/migrate-day2.ps1应用到V5；database profile会验证目录表存在，不自动迁移、seed或清库。用受控方式准备管理员；公开注册仍仅BUYER，商品迁移不带账号或商品种子。

## 页面与权限

登录用户访问/catalog和/catalog/{id}浏览已上架商品及启用SKU。/admin/catalog只有ADMIN，创建草稿、添加/编辑SKU，再上架；BUYER/OPERATOR不能后台写入。所有目录API当前要求登录，匿名401。商品管理入口与服务方法均限制ADMIN。

SKU编码全局唯一、ASCII 1–64字符，创建后不可变；SKU名称可编辑。商品标题1–128字符，描述0–2000字符。商品上架至少需要一个启用SKU；上架商品最后一个启用SKU不可直接禁用，先下架，再禁用。没有删除接口，后续订单引用所需的稳定ID保留。

## API契约

| 方法与路径 | 说明 |
|---|---|
| GET /api/catalog/products?page=1&size=12&sort=newest | 已上架列表 |
| GET /api/catalog/products/{id} | 已上架详情，过滤禁用SKU；下架404 |
| POST /api/catalog/skus/{id}/quote | JSON quantity整数1–999；重新校验可售并计算总价 |
| GET /api/admin/catalog/products | 管理列表，包含草稿/下架 |
| GET /api/admin/catalog/products/{id} | 全部SKU及当前version |
| POST /api/admin/catalog/products | JSON title、description；201草稿 |
| PUT /api/admin/catalog/products/{id} | JSON title、description、published布尔、version字符串 |
| POST /api/admin/catalog/products/{id}/skus | JSON skuCode、name、priceCents字符串、enabled布尔、version字符串；201 |
| PUT /api/admin/catalog/products/{id}/skus/{skuId} | 同上；编码须保持原值 |

价格为CNY整数分，范围1–9223372036854775807。priceCents必须为JSON字符串，拒绝数字、负号、小数、指数、空白、前导零和溢出。version也是规范非负整数字符串；ID、价格和版本响应使用字符串，避免JS安全整数限制。quantity为JSON整数，拒绝字符串和浮点数，即使1.0也拒绝。未知JSON字段400。

例如priceCents="12345"显示¥123.45；客户端提交{"quantity":2}，服务端从DB价格计算totalCents="24690"。总价用Math.multiplyExact，超long返回400，不接受调用方总价。前端用BigInt格式化，价格不转Number。报价不是订单，不预留库存、不锁定未来价格；提交未来订单时必须在业务事务再次校验并保存快照。

分页page=1–10000、size=1–50；sort只接受newest（id DESC）、oldest（id ASC）、title（title ASC,id ASC）。只有switch映射的SQL常量进入ORDER BY，值通过占位符绑定。复合索引支持published/id与published/title/id查询；本日未做EXPLAIN负载或性能测量，不能宣称已验证大数据性能。

## 并发与事务

目录写入先锁product行，再校验请求version；商品与其所有SKU共用父版本，每次成功修改+1，过时请求409 CATALOG_STALE_VERSION，用户重新加载核对后再保存。实际并发同版本写入一方200、一方409。服务端created_by/updated_by来自JWT主体，客户端不能传入替换。

CatalogAvailability.requireSellable要求现有事务（MANDATORY），先定位不可变productId，再按product→SKU顺序做当前锁定读。SKU价格读取也使用FOR UPDATE，避免此前RR快照回读旧值。父锁与所有目录编辑共享；后续订单应经注入Bean调用并保持锁直到订单事务提交。本日只提供单SKU可售校验和无持久业务的报价入口，没有库存/订单事务，多SKU锁顺序留给对应Day。

下架或禁用SKU报价409 CATALOG_NOT_SELLABLE；发布无启用SKU409 CATALOG_NO_ACTIVE_SKU；编码重复409 SKU_CODE_UNAVAILABLE。未知资源404，输入400，依赖失败503，响应沿用code/message/traceId。不自动重试版本冲突或网络失败的写请求。

学习：[Java21精确算术](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Math.html#multiplyExact(long,int))、[MySQL8.4锁定读](https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html)。先说明整数分为何不需要舍入；若后续接收元的小数输入，再用BigDecimal.setScale(2,UNNECESSARY)、movePointRight(2)、longValueExact明确转换，不通过double猜测金额。本日元转分接口未实现。
