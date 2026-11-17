import { it, expect, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { useSession } from '../stores/session'
import CatalogAdminPage from './CatalogAdminPage.vue'

it('sends exact string price and aggregate version when an administrator adds a SKU', async () => {
  sessionStorage.clear()
  const pinia = createPinia(); const session = useSession(pinia)
  const product = { id: '1', title: 'Product', description: '', published: false, version: '0', skus: [] }
  const page = { items: [{ id: '1', title: 'Product', published: false, version: '0', minPriceCents: null }], total: 1, page: 1, size: 12 }
  const api = vi.spyOn(session, 'api').mockResolvedValueOnce(page).mockResolvedValueOnce(product)
    .mockResolvedValueOnce({ ...product, version: '1' }).mockResolvedValueOnce(page)
  const wrapper = mount(CatalogAdminPage, { global: { plugins: [pinia] } }); await flushPromises()
  await wrapper.findAll('button').find(b => b.text() === '编辑商品')!.trigger('click'); await flushPromises()
  await wrapper.get('input[aria-label="规格编码"]').setValue('LARGE')
  await wrapper.get('input[aria-label="规格名称"]').setValue('Large')
  await wrapper.get('input[aria-label="价格分值"]').setValue('9007199254740993')
  await wrapper.findAll('form')[2]!.trigger('submit'); await flushPromises()
  expect(JSON.parse(api.mock.calls[2]![1]!.body as string)).toMatchObject({ priceCents: '9007199254740993', version: '0' })
  expect(wrapper.text()).toContain('规格已保存')
  wrapper.unmount()
})
