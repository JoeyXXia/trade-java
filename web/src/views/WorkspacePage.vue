<script setup lang="ts">
import { useSession } from '../stores/session'
defineProps<{ title: string }>()
const session = useSession()
</script>
<template><section class="panel"><p class="eyebrow">WORKSPACE</p><h1>{{ title }}</h1><p>当前账号：{{ session.user?.loginName }} · {{ session.user?.role }}</p><h2>工作区已就绪</h2><p class="muted">商品维护、库存与订单管理页面将随对应业务模块上线。</p><RouterLink to="/account">查看个人身份 →</RouterLink></section></template>
