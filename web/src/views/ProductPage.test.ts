import { beforeEach, it, expect, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createMemoryHistory } from 'vue-router'
import { makeRouter } from '../router'
import { useSession } from '../stores/session'
import ProductPage from './ProductPage.vue'

beforeEach(() => sessionStorage.clear())
it('escapes descriptions and submits integer quantity without accepting a client price', async () => {
  const pinia = createPinia(); const session = useSession(pinia)
  session.user = { id: '1', loginName: 'buyer', role: 'BUYER' }
  const router = makeRouter(pinia, createMemoryHistory()); await router.push('/catalog/1'); await router.isReady()
  const api = vi.spyOn(session, 'api').mockResolvedValueOnce({ id: '1', title: 'Product', description: '<script>alert(1)</script>', published: true, version: '1', skus: [{ id: '2', skuCode: 'A', name: 'Large', priceCents: '9007199254740993', enabled: true }] })
    .mockResolvedValueOnce({ totalCents: '18014398509481986', currency: 'CNY' })
  const wrapper = mount(ProductPage, { global: { plugins: [pinia, router] } }); await flushPromises()
  expect(wrapper.find('script').exists()).toBe(false)
  expect(wrapper.text()).toContain('<script>alert(1)</script>')
  expect(wrapper.text()).toContain('¥90071992547409.93')
  await wrapper.get('input[aria-label="数量"]').setValue('1.5')
  await wrapper.findAll('button').find(b => b.text() === '计算当前总价')!.trigger('click'); await flushPromises()
  expect(api).toHaveBeenCalledTimes(1)
  await wrapper.get('input[aria-label="数量"]').setValue('2')
  await wrapper.findAll('button').find(b => b.text() === '计算当前总价')!.trigger('click'); await flushPromises()
  expect(api.mock.calls[1]![0]).toBe('/api/catalog/skus/2/quote')
  expect(JSON.parse(api.mock.calls[1]![1]!.body as string)).toEqual({ quantity: 2 })
  expect(wrapper.text()).toContain('¥180143985094819.86')
  wrapper.unmount()
})
it('does not replace the newly selected product with a delayed response from the previous route', async () => {
  const pinia = createPinia(); const session = useSession(pinia)
  session.user = { id: '1', loginName: 'buyer', role: 'BUYER' }
  const router = makeRouter(pinia, createMemoryHistory()); await router.push('/catalog/1'); await router.isReady()
  let resolveOld!: (value: unknown) => void
  const old = new Promise<unknown>(resolve => { resolveOld = resolve })
  const product = (id: string, title: string) => ({ id, title, description: '', published: true, version: '1', skus: [] })
  const api = vi.spyOn(session, 'api').mockImplementationOnce(() => old).mockResolvedValueOnce(product('2', 'Current product'))
  const wrapper = mount(ProductPage, { global: { plugins: [pinia, router] } }); await flushPromises()
  expect(api).toHaveBeenCalledTimes(1)
  await router.push('/catalog/2'); await flushPromises()
  expect(wrapper.text()).toContain('Current product')
  resolveOld(product('1', 'Stale product')); await flushPromises()
  expect(wrapper.text()).toContain('Current product'); expect(wrapper.text()).not.toContain('Stale product')
  wrapper.unmount()
})
