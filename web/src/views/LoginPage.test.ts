import { afterEach, beforeEach, it, expect, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createMemoryHistory } from 'vue-router'
import LoginPage from './LoginPage.vue'
import { makeRouter } from '../router'

beforeEach(() => sessionStorage.clear())
afterEach(() => vi.unstubAllGlobals())
it('renders a server login error with trace and clears the password', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: 'INVALID_CREDENTIALS', message: 'Invalid credentials', traceId: 'trace-login' }), { status: 401 })))
  const pinia = createPinia(); const router = makeRouter(pinia, createMemoryHistory())
  await router.push('/login'); await router.isReady()
  const wrapper = mount(LoginPage, { global: { plugins: [pinia, router] } })
  await wrapper.get('input[autocomplete="username"]').setValue('buyer')
  await wrapper.get('input[type="password"]').setValue('fixture-password-123')
  await wrapper.get('form').trigger('submit'); await flushPromises()
  expect(wrapper.text()).toContain('INVALID_CREDENTIALS'); expect(wrapper.text()).toContain('trace-login')
  expect((wrapper.get('input[type="password"]').element as HTMLInputElement).value).toBe('')
  expect(wrapper.text()).not.toContain('fixture-password-123')
  wrapper.unmount()
})
