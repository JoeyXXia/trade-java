<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElAlert, ElButton, ElForm, ElFormItem, ElInput } from 'element-plus'
import { describe } from '../api'
import { useSession } from '../stores/session'
const session = useSession()
const route = useRoute(); const router = useRouter()
const loginName = ref(''); const password = ref(''); const registering = ref(false)
const busy = ref(false); const error = ref('')
async function submit() {
  if (busy.value) return
  busy.value = true; error.value = ''; session.notice = ''
  try {
    await session.login(loginName.value, password.value, registering.value)
    const target = route.query.redirect
    await router.replace(typeof target === 'string' && ['/account', '/admin', '/operations'].includes(target) ? target : '/account')
  } catch (reason) { error.value = describe(reason) }
  finally { busy.value = false; password.value = '' }
}
</script>
<template>
  <section class="panel login-panel">
    <p class="eyebrow">WELCOME BACK</p><h1>{{ registering ? '创建买家账号' : '登录 CommerceFlow' }}</h1>
    <p class="muted">用一个可验证的身份，进入交易平台。</p>
    <ElAlert v-if="error || session.notice" :title="error || session.notice" type="error" :closable="false" show-icon role="alert" />
    <ElForm label-position="top" @submit.prevent="submit">
      <ElFormItem label="登录名"><ElInput v-model="loginName" aria-label="登录名" autocomplete="username" maxlength="64" :disabled="busy" required /></ElFormItem>
      <ElFormItem label="密码"><ElInput v-model="password" aria-label="密码" type="password" :autocomplete="registering ? 'new-password' : 'current-password'" show-password :disabled="busy" required /></ElFormItem>
      <p v-if="registering" class="muted">用户名3–64位ASCII字母数字及_.-；密码至少12字符，UTF-8最多72字节。</p>
      <ElButton native-type="submit" type="primary" :loading="busy">{{ registering ? '注册并登录' : '登录' }}</ElButton>
      <ElButton :disabled="busy" text @click="registering = !registering">{{ registering ? '已有账号，返回登录' : '注册买家账号' }}</ElButton>
    </ElForm>
  </section>
</template>
