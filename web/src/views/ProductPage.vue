<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElAlert, ElButton } from 'element-plus'
import { describe, jsonBody } from '../api'
import { money, parseProduct, quantity, quoteTotal, type Product } from '../catalog'
import { useSession } from '../stores/session'
const session = useSession(); const route = useRoute()
const product = ref<Product | null>(null); const selected = ref(''); const count = ref('1')
const busy = ref(false); const error = ref(''); const total = ref('')
let generation = 0
async function load() {
  const requestGeneration = ++generation
  busy.value = true; error.value = ''; total.value = ''; product.value = null
  try {
    const loaded = parseProduct(await session.api(`/api/catalog/products/${String(route.params.id)}`))
    if (requestGeneration === generation) { product.value = loaded; selected.value = loaded.skus[0]?.id ?? '' }
  }
  catch (reason) { if (requestGeneration === generation) error.value = describe(reason) }
  finally { if (requestGeneration === generation) busy.value = false }
}
async function quote() {
  total.value = ''; error.value = ''
  let qty: number
  try { qty = quantity(count.value) } catch { error.value = '数量必须是1–999之间的整数。'; return }
  busy.value = true
  const requestGeneration = generation
  try { const value = money(quoteTotal(await session.api(`/api/catalog/skus/${selected.value}/quote`, jsonBody({ quantity: qty })))); if (requestGeneration === generation) total.value = value }
  catch (reason) { if (requestGeneration === generation) error.value = describe(reason) }
  finally { if (requestGeneration === generation) busy.value = false }
}
watch([selected, count], () => { total.value = '' })
watch(() => route.params.id, load)
onMounted(load)
</script>
<template><section class="panel"><RouterLink to="/catalog">← 返回商品列表</RouterLink><ElAlert v-if="error" :title="error" type="error" :closable="false" role="alert" /><p v-if="busy" role="status">加载中…</p>
  <template v-if="product"><h1>{{ product.title }}</h1><p class="product-description">{{ product.description }}</p>
    <fieldset :disabled="busy"><legend>选择规格</legend><label v-for="sku in product.skus" :key="sku.id" class="sku-option"><input v-model="selected" type="radio" :value="sku.id" name="sku">{{ sku.name }} · {{ money(sku.priceCents) }}</label></fieldset>
    <label class="quantity-label">数量 <input v-model="count" aria-label="数量" inputmode="numeric" maxlength="3" :disabled="busy"></label>
    <ElButton type="primary" :disabled="busy || !selected" @click="quote">计算当前总价</ElButton><p v-if="total" role="status">当前总价：{{ total }}</p>
    <p class="muted">报价按当前商品状态重新计算，不会创建订单或预留库存。</p>
  </template><ElButton :disabled="busy" @click="load">刷新商品</ElButton>
</section></template>
