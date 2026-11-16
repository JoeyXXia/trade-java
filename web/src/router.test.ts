import { beforeEach, afterEach, it, expect, vi } from 'vitest'
import { createPinia } from 'pinia'
import { createMemoryHistory } from 'vue-router'
import { makeRouter } from './router'
import { useSession } from './stores/session'

beforeEach(() => { sessionStorage.clear() })
afterEach(() => vi.unstubAllGlobals())
it('sends anonymous routes to login and buyers away from admin routes', async () => {
  const pinia = createPinia(); const router = makeRouter(pinia, createMemoryHistory())
  await router.push('/admin'); await router.isReady()
  expect(router.currentRoute.value.path).toBe('/login')
  expect(router.currentRoute.value.query.redirect).toBe('/admin')
  useSession(pinia).user = { id: '1', loginName: 'buyer', role: 'BUYER' }
  await router.push('/admin'); expect(router.currentRoute.value.path).toBe('/forbidden')
  await router.push('/operations'); expect(router.currentRoute.value.path).toBe('/forbidden')
  useSession(pinia).user!.role = 'OPERATOR'
  await router.push('/operations'); expect(router.currentRoute.value.path).toBe('/operations')
  await router.push('/admin'); expect(router.currentRoute.value.path).toBe('/forbidden')
  useSession(pinia).user!.role = 'ADMIN'
  await router.push('/admin'); expect(router.currentRoute.value.path).toBe('/admin')
})
