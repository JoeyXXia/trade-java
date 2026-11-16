<script setup lang="ts">
import { ref } from 'vue'

const status = ref('尚未检查')
const checking = ref(false)
async function checkHealth() {
  checking.value = true
  try {
    const response = await fetch('/actuator/health')
    if (!response.ok) throw new Error('HTTP ' + response.status)
    const data: unknown = await response.json()
    if (typeof data !== 'object' || data === null || !('status' in data) || data.status !== 'UP') {
      throw new Error('健康响应格式异常或服务未就绪')
    }
    status.value = 'UP'
  } catch (error) {
    status.value = error instanceof Error ? error.message : '检查失败'
  } finally {
    checking.value = false
  }
}
</script>

<template>
  <main>
    <p class="eyebrow">COMMERCEFLOW · DAY 1</p>
    <h1>交易平台，从可验证的基础开始。</h1>
    <p>Java 21 · Spring Boot 3.5 · Vue 3 · TypeScript</p>
    <section aria-label="后端健康检查">
      <h2>工程连接检查</h2>
      <p role="status">Trade 服务：{{ status }}</p>
      <button :disabled="checking" @click="checkHealth">{{ checking ? '检查中…' : '检查后端健康' }}</button>
    </section>
    <p class="note">当前仅完成工程骨架。商品、订单、支付与履约按每日计划逐步实现。</p>
  </main>
</template>
