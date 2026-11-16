<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElAlert, ElButton, ElTag } from 'element-plus'
import { describe } from '../api'
import { useSession } from '../stores/session'
const session = useSession(); const router = useRouter()
const busy = ref(false); const error = ref('')
async function reload() {
  busy.value = true; error.value = ''
  try { await session.reloadUser() }
  catch (reason) { error.value = describe(reason); if (!session.user) await router.replace('/login') }
  finally { busy.value = false }
}
</script>
<template><section class="panel"><p class="eyebrow">MY SPACE</p><h1>你好，{{ session.user?.loginName }}</h1><ElTag>{{ session.user?.role }}</ElTag><dl><dt>用户编号</dt><dd>{{ session.user?.id }}</dd><dt>当前能力</dt><dd>身份验证、查看个人信息与安全退出</dd></dl><ElAlert v-if="error" :title="error" type="error" :closable="false" role="alert" /><ElButton :loading="busy" @click="reload">重新验证身份</ElButton></section></template>
