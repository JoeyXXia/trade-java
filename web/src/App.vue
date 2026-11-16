<script setup lang="ts">
import { ElButton } from 'element-plus'
import { useRouter } from 'vue-router'
import { useSession } from './stores/session'
const session = useSession(); const router = useRouter()
async function logout() { await session.logout(); await router.replace('/login') }
</script>
<template>
  <header class="topbar"><RouterLink class="brand" to="/">CF<span>CommerceFlow</span></RouterLink><nav aria-label="主导航"><RouterLink to="/">概览</RouterLink><RouterLink to="/account">我的空间</RouterLink><RouterLink v-if="session.user && ['OPERATOR','ADMIN'].includes(session.user.role)" to="/operations">运营</RouterLink><RouterLink v-if="session.user?.role === 'ADMIN'" to="/admin">管理</RouterLink><ElButton v-if="session.user" text @click="logout">退出</ElButton><RouterLink v-else to="/login">登录</RouterLink></nav></header>
  <main><RouterView /></main><footer>CommerceFlow · 交易与订单履约学习项目</footer>
</template>
