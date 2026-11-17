<script setup lang="ts">
import { useSession } from '../stores/session'
defineProps<{ title: string }>()
const session = useSession()
</script>
<template><section class="panel"><p class="eyebrow">WORKSPACE</p><h1>{{ title }}</h1><p>当前账号：{{ session.user?.loginName }} · {{ session.user?.role }}</p><h2>工作区已就绪</h2><p><RouterLink v-if="session.user?.role === 'ADMIN'" to="/admin/catalog">维护商品与规格 →</RouterLink><RouterLink v-else to="/catalog">查看商品目录 →</RouterLink></p><p class="muted">库存与订单管理页面将随对应业务模块上线。</p><RouterLink to="/account">查看个人身份 →</RouterLink></section></template>
