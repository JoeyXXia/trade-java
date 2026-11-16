import { ref } from 'vue'
import { defineStore } from 'pinia'
import { ApiError, describe, jsonBody, parseTokens, parseUser, request, type User } from '../api'

export const REFRESH_KEY = 'commerceflow.refresh.v1'
export const useSession = defineStore('session', () => {
  const user = ref<User | null>(null)
  const notice = ref('')
  let accessToken = ''
  let epoch = 0
  let ready = false
  let bootstrap: Promise<void> | undefined
  let rotation: Promise<void> | undefined
  function clear() {
    epoch++; accessToken = ''; user.value = null
    sessionStorage.removeItem(REFRESH_KEY)
  }
  function accept(data: unknown, generation: number) {
    const tokens = parseTokens(data)
    if (generation !== epoch) throw new ApiError(401, 'SESSION_CHANGED', '会话已变更，请重新登录')
    sessionStorage.setItem(REFRESH_KEY, tokens.refreshToken)
    accessToken = tokens.accessToken; user.value = tokens.user
  }
  async function login(loginName: string, password: string, registering = false) {
    clear(); ready = true; notice.value = ''
    const generation = epoch
    if (registering) await request('/api/auth/register', jsonBody({ loginName, password }))
    accept(await request('/api/auth/login', jsonBody({ loginName, password })), generation)
  }
  function refresh(): Promise<void> {
    if (rotation) return rotation
    const generation = epoch
    const raw = sessionStorage.getItem(REFRESH_KEY)
    const task = (async () => {
      try {
        if (!raw) throw new ApiError(401, 'UNAUTHORIZED', '请先登录')
        accept(await request('/api/auth/refresh', jsonBody({ refreshToken: raw })), generation)
      } catch (error) {
        if (generation === epoch) { clear(); notice.value = describe(error) }
        throw error
      }
    })()
    rotation = task
    // Handle both outcomes without creating an unobserved rejected finally() promise.
    void task.then(() => { if (rotation === task) rotation = undefined }, () => { if (rotation === task) rotation = undefined })
    return task
  }
  async function ensureReady() {
    if (ready) return
    if (!bootstrap) bootstrap = (async () => {
      try { if (sessionStorage.getItem(REFRESH_KEY)) await refresh() }
      catch { /* refresh records the visible failure and clears local credentials. */ }
      finally { ready = true }
    })()
    await bootstrap
  }
  async function api(path: string, init: RequestInit = {}): Promise<unknown> {
    if (!path.startsWith('/api/')) throw new Error('Authenticated requests must stay on this API origin')
    await ensureReady()
    if (!accessToken) throw new ApiError(401, 'UNAUTHORIZED', '请先登录')
    const generation = epoch
    const used = accessToken
    const send = async () => {
      const headers = new Headers(init.headers); headers.set('Authorization', `Bearer ${accessToken}`)
      const data = await request(path, { ...init, headers })
      if (generation !== epoch) throw new ApiError(401, 'SESSION_CHANGED', '会话已变更，请重新登录')
      return data
    }
    try { return await send() }
    catch (error) {
      if (!(error instanceof ApiError) || error.status !== 401 || generation !== epoch) throw error
      if (used === accessToken) await refresh()
      try { return await send() }
      catch (retryError) {
        if (retryError instanceof ApiError && retryError.status === 401 && generation === epoch) clear()
        throw retryError
      }
    }
  }
  async function reloadUser() {
    const generation = epoch
    const current = parseUser(await api('/api/me'))
    if (generation === epoch) user.value = current
  }
  async function logout() {
    const raw = sessionStorage.getItem(REFRESH_KEY)
    clear(); ready = true; notice.value = ''
    if (!raw) return
    try { await request('/api/auth/logout', jsonBody({ refreshToken: raw })) }
    catch (error) { notice.value = `本地已退出，服务器注销未确认。${describe(error)}` }
  }
  return { user, notice, login, refresh, ensureReady, api, reloadUser, logout }
})
