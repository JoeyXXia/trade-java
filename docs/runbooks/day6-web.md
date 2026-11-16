# Day 6：前端身份流程

1. 按[身份运行手册](identity.md)启动MySQL、显式迁移、生成本地JWT密钥、构建并启动database profile的Trade JAR。
2. web目录执行npm ci、npm test、npm run build。开发时npm run dev，访问http://127.0.0.1:5173。Windows执行npm ci前停止对应Vite，避免esbuild文件锁。
3. 登录页可显式注册BUYER，不提供默认账号、角色选择或管理员自动种子。已有账号可直接登录。OPERATOR/ADMIN仍需要受控的数据操作，不通过注册提权。
4. 刷新页面应恢复本人且旋转refresh；退出后回到登录页。服务端不可达会显示错误和已知traceId；不要反馈密码/token。注销失败只证明本地清除，页面会提示服务器未确认。

access只在当前应用实例内存，refresh在标签页sessionStorage；刷新合并只覆盖同标签页。不要复制storage中的凭据到其他标签页或客户端，重放会撤销整族。浏览器可执行同源脚本仍能接触refresh，故不能把本地实现当作生产XSS防护方案。

/account为登录后个人空间，/operations给OPERATOR/ADMIN，/admin给ADMIN。后台目前为页面壳，业务模块尚未实现。篡改浏览器用户状态不能改变服务端API权限。

开发代理将/api、/actuator转至127.0.0.1:8080；生产需自行配置静态history回退与同源API代理，本日未部署。npm run preview只静态预览，不提供本日代理。

验收、实际依赖漏洞和截图见[Day 6报告](../progress/day-06.md)。
