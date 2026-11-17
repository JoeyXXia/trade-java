<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElAlert, ElButton } from 'element-plus'
import { describe, jsonBody } from '../api'
import { cents, money, parsePage, parseProduct, type CatalogPage, type Product, type Sku } from '../catalog'
import { useSession } from '../stores/session'
const session = useSession()
const list = ref<CatalogPage | null>(null); const page = ref(1); const current = ref<Product | null>(null)
const busy = ref(false); const error = ref(''); const notice = ref('')
const createTitle = ref(''); const createDescription = ref('')
const meta = reactive({ title: '', description: '', published: false })
const sku = reactive({ id: '', skuCode: '', name: '', priceCents: '', enabled: true })
function accept(value: unknown) { current.value = parseProduct(value); Object.assign(meta, { title: current.value.title, description: current.value.description, published: current.value.published }) }
async function loadList() { list.value = parsePage(await session.api(`/api/admin/catalog/products?page=${page.value}&size=12&sort=newest`)) }
async function act(work: () => Promise<void>) {
  if (busy.value) return
  busy.value = true; error.value = ''; notice.value = ''
  try { await work() } catch (reason) { error.value = describe(reason) }
  finally { busy.value = false }
}
function resetSku() { Object.assign(sku, { id: '', skuCode: '', name: '', priceCents: '', enabled: true }) }
async function select(id: string) { await act(async () => { accept(await session.api(`/api/admin/catalog/products/${id}`)); resetSku() }) }
async function create() { await act(async () => { accept(await session.api('/api/admin/catalog/products', jsonBody({ title: createTitle.value, description: createDescription.value }))); createTitle.value = ''; createDescription.value = ''; resetSku(); await loadList(); notice.value = '草稿已创建，请添加规格后上架。' }) }
async function saveMeta() { if (!current.value) return; await act(async () => { accept(await session.api(`/api/admin/catalog/products/${current.value!.id}`, { ...jsonBody({ ...meta, version: current.value!.version }), method: 'PUT' })); await loadList(); notice.value = '商品信息已保存。' }) }
function editSku(value: Sku) { Object.assign(sku, value) }
async function saveSku() {
  if (!current.value) return
  try { cents(sku.priceCents) } catch { error.value = '价格须为正整数分值，不能含小数、指数或超过9223372036854775807分。'; return }
  await act(async () => {
    const path = `/api/admin/catalog/products/${current.value!.id}/skus${sku.id ? '/' + sku.id : ''}`
    accept(await session.api(path, { ...jsonBody({ skuCode: sku.skuCode, name: sku.name, priceCents: sku.priceCents, enabled: sku.enabled, version: current.value!.version }), method: sku.id ? 'PUT' : 'POST' }))
    resetSku(); await loadList(); notice.value = '规格已保存。'
  })
}
function turn(delta: number) { page.value += delta; void act(loadList) }
onMounted(() => act(loadList))
</script>
<template><section class="panel"><p class="eyebrow">ADMIN / CATALOG</p><h1>商品管理</h1>
  <ElAlert v-if="error" :title="error" type="error" :closable="false" role="alert" /><p v-if="notice" role="status">{{ notice }}</p>
  <form class="catalog-form" @submit.prevent="create"><h2>创建商品草稿</h2><label>新商品名称<input v-model="createTitle" aria-label="新商品名称" required maxlength="128" :disabled="busy"></label><label>新商品描述<textarea v-model="createDescription" aria-label="新商品描述" maxlength="2000" :disabled="busy" /></label><ElButton native-type="submit" :disabled="busy">创建草稿</ElButton></form>
  <div class="catalog-grid"><article v-for="item in list?.items" :key="item.id" class="catalog-tile"><h2>{{ item.title }}</h2><p>{{ item.published ? '已上架' : '草稿 / 下架' }}</p><ElButton :disabled="busy" @click="select(item.id)">编辑商品</ElButton></article></div>
  <div class="catalog-actions"><ElButton :disabled="busy || page === 1" @click="turn(-1)">上一页</ElButton><span>第 {{ page }} 页</span><ElButton :disabled="busy || !list || page * 12 >= list.total" @click="turn(1)">下一页</ElButton></div>
</section>
<section v-if="current" class="panel"><h2>编辑：{{ current.title }}</h2><ElButton :disabled="busy" @click="select(current.id)">重新加载当前商品</ElButton><p class="muted">多人编辑冲突时，请重新加载并核对内容后保存。下架后可禁用最后一个规格。</p>
  <form class="catalog-form" @submit.prevent="saveMeta"><label>商品名称<input v-model="meta.title" aria-label="商品名称" required maxlength="128" :disabled="busy"></label><label>描述<textarea v-model="meta.description" aria-label="商品描述" maxlength="2000" :disabled="busy" /></label><label><input v-model="meta.published" type="checkbox" :disabled="busy">上架</label><ElButton native-type="submit" type="primary" :disabled="busy">保存商品信息</ElButton></form>
  <h2>规格</h2><article v-for="item in current.skus" :key="item.id" class="sku-option"><span>{{ item.name }} · {{ item.skuCode }} · {{ money(item.priceCents) }} · {{ item.enabled ? '启用' : '禁用' }}</span><ElButton text :disabled="busy" @click="editSku(item)">编辑规格</ElButton></article>
  <form class="catalog-form" @submit.prevent="saveSku"><h3>{{ sku.id ? '编辑规格' : '添加规格' }}</h3><label>规格编码<input v-model="sku.skuCode" aria-label="规格编码" required maxlength="64" :disabled="busy || !!sku.id" pattern="[A-Za-z0-9][A-Za-z0-9_.\-]{0,63}"></label><label>规格名称<input v-model="sku.name" aria-label="规格名称" required maxlength="128" :disabled="busy"></label><label>价格（CNY 分）<input v-model="sku.priceCents" aria-label="价格分值" inputmode="numeric" required maxlength="19" :disabled="busy"></label><label><input v-model="sku.enabled" type="checkbox" :disabled="busy">启用规格</label><div><ElButton native-type="submit" type="primary" :disabled="busy">保存规格</ElButton><ElButton :disabled="busy" @click="resetSku">清空规格表单</ElButton></div></form>
</section></template>
