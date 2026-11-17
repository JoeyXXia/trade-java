<script setup lang="ts">
import { ref } from 'vue'
import { ElButton } from 'element-plus'
import { describe, record, request } from '../api'
const status = ref('尚未检查'); const checking = ref(false)
async function health() {
  checking.value = true
  try { const data = await request('/actuator/health'); status.value = record(data) && data.status === 'UP' ? 'UP' : '健康响应异常' }
  catch (error) { status.value = describe(error) }
  finally { checking.value = false }
}
</script>
<template>
  <section class="hero"><p class="eyebrow">COMMERCEFLOW / ENGINEERING IN PROGRESS</p><h1>让每一笔交易，<br>有迹可循。</h1><p class="muted">从身份与权限开始，构建可验证的交易平台。</p><RouterLink class="primary-link" to="/account">进入我的空间 →</RouterLink></section>
  <section class="panel"><h2>工程连接检查</h2><p role="status">Trade 服务：{{ status }}</p><ElButton :loading="checking" @click="health">检查后端健康</ElButton></section>
  <p class="muted">商品目录与规格价格已提供；库存、订单、支付及履约将按项目计划逐步实现。</p>
</template>
