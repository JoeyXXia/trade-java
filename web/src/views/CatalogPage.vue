<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElAlert, ElButton } from 'element-plus'
import { describe } from '../api'
import { money, parsePage, type CatalogPage } from '../catalog'
import { useSession } from '../stores/session'
const session = useSession()
const result = ref<CatalogPage | null>(null); const page = ref(1); const sort = ref('newest')
const busy = ref(false); const error = ref('')
async function load() {
  busy.value = true; error.value = ''
  try { result.value = parsePage(await session.api(`/api/catalog/products?page=${page.value}&size=12&sort=${sort.value}`)) }
  catch (reason) { error.value = describe(reason) }
  finally { busy.value = false }
}
function turn(delta: number) { page.value += delta; void load() }
onMounted(load)
</script>
<template><section class="panel"><p class="eyebrow">CATALOG</p><h1>发现商品</h1>
  <label>排序 <select v-model="sort" :disabled="busy" @change="page = 1; load()"><option value="newest">新建优先</option><option value="oldest">商品编号升序</option><option value="title">商品名称</option></select></label>
  <ElAlert v-if="error" :title="error" type="error" :closable="false" role="alert" />
  <p v-if="busy" role="status">加载中…</p><p v-else-if="result?.items.length === 0">暂时没有上架商品。</p>
  <div class="catalog-grid"><article v-for="item in result?.items" :key="item.id" class="catalog-tile"><h2><RouterLink :to="`/catalog/${item.id}`">{{ item.title }}</RouterLink></h2><p>{{ item.minPriceCents ? money(item.minPriceCents) + ' 起' : '暂无可售规格' }}</p></article></div>
  <div class="catalog-actions"><ElButton :disabled="busy || page === 1" @click="turn(-1)">上一页</ElButton><span>第 {{ page }} 页 · 共 {{ result?.total ?? 0 }} 件商品</span><ElButton :disabled="busy || !result || page * 12 >= result.total" @click="turn(1)">下一页</ElButton></div>
</section></template>
