import { beforeEach, afterEach, describe, it, expect, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { ApiError, parseTokens } from '../api'
import { REFRESH_KEY, useSession } from './session'

export const tokens = (access = 'access-one', refresh = 'a'.repeat(43)) => ({
  accessToken: access, refreshToken: refresh, tokenType: 'Bearer', expiresIn: 300,
  user: { id: '1', loginName: 'buyer', role: 'BUYER' },
})
const response = (data: unknown, status = 200) => new Response(JSON.stringify(data), { status, headers: { 'Content-Type': 'application/json' } })
const denied = () => response({ code: 'UNAUTHORIZED', message: 'Authentication required', traceId: 'fixture-trace' }, 401)
function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>(r => { resolve = r })
  return { promise, resolve }
}
beforeEach(() => { setActivePinia(createPinia()); sessionStorage.clear(); localStorage.clear() })
afterEach(() => { vi.unstubAllGlobals() })

describe('session boundary', () => {
  it('keeps only refresh in tab storage and restores a reloaded tab by rotation', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(response(tokens()))
      .mockResolvedValueOnce(response(tokens('access-two', 'b'.repeat(43))))
      .mockResolvedValueOnce(response(tokens().user))
    vi.stubGlobal('fetch', fetcher)
    await useSession().login('buyer', 'fixture-password-123')
    expect(sessionStorage.getItem(REFRESH_KEY)).toBe('a'.repeat(43))
    expect(JSON.stringify(sessionStorage)).not.toContain('access-one')
    expect(localStorage.length).toBe(0)
    setActivePinia(createPinia())
    const restored = useSession()
    await Promise.all([restored.ensureReady(), restored.ensureReady()])
    expect(fetcher.mock.calls.filter(([path]) => path === '/api/auth/refresh')).toHaveLength(1)
    expect(restored.user?.id).toBe('1')
    await restored.reloadUser()
    expect(new Headers(fetcher.mock.calls[2]![1].headers).get('Authorization')).toBe('Bearer access-two')
    expect(sessionStorage.getItem(REFRESH_KEY)).toBe('b'.repeat(43))
  })
  it('coalesces concurrent 401s and retries each request only once', async () => {
    const gate = deferred<Response>()
    let calls = 0
    const fetcher = vi.fn((path: string) => {
      if (path === '/api/auth/login') return Promise.resolve(response(tokens()))
      if (path === '/api/auth/refresh') return gate.promise
      return Promise.resolve(++calls <= 2 ? denied() : response(tokens().user))
    })
    vi.stubGlobal('fetch', fetcher)
    const session = useSession(); await session.login('buyer', 'fixture-password-123')
    const pending = Promise.all([session.api('/api/me'), session.api('/api/me')])
    await vi.waitFor(() => expect(fetcher.mock.calls.filter(([p]) => p === '/api/auth/refresh')).toHaveLength(1))
    gate.resolve(response(tokens('access-two', 'b'.repeat(43))))
    await pending
    expect(calls).toBe(4)
    expect(fetcher.mock.calls.filter(([p]) => p === '/api/auth/refresh')).toHaveLength(1)
  })
  it('does not refresh a 403 and preserves the server error and trace', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(response(tokens())).mockResolvedValueOnce(response({ code: 'FORBIDDEN', message: 'Access denied', traceId: 'trace-403' }, 403))
    vi.stubGlobal('fetch', fetcher)
    const session = useSession(); await session.login('buyer', 'fixture-password-123')
    await expect(session.api('/api/admin/users/1/disable', { method: 'POST' })).rejects.toMatchObject({ status: 403, traceId: 'trace-403' })
    expect(fetcher).toHaveBeenCalledTimes(2)
  })
  it('reuses an already rotated access token when an older 401 arrives late', async () => {
    const oldReply = deferred<Response>()
    let requests = 0
    const fetcher = vi.fn((path: string) => {
      if (path === '/api/auth/login') return Promise.resolve(response(tokens()))
      if (path === '/api/auth/refresh') return Promise.resolve(response(tokens('new-access', 'b'.repeat(43))))
      requests++
      if (requests === 1) return oldReply.promise
      return Promise.resolve(requests === 2 ? denied() : response(tokens().user))
    })
    vi.stubGlobal('fetch', fetcher)
    const session = useSession(); await session.login('buyer', 'fixture-password-123')
    const late = session.api('/api/me')
    await vi.waitFor(() => expect(requests).toBe(1))
    await session.api('/api/me')
    oldReply.resolve(denied()); await late
    expect(fetcher.mock.calls.filter(([p]) => p === '/api/auth/refresh')).toHaveLength(1)
    expect(requests).toBe(4)
  })
  it('stops after a retried access request is also unauthorized', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(response(tokens())).mockResolvedValueOnce(denied())
      .mockResolvedValueOnce(response(tokens('new-access', 'b'.repeat(43)))).mockResolvedValueOnce(denied())
    vi.stubGlobal('fetch', fetcher)
    const session = useSession(); await session.login('buyer', 'fixture-password-123')
    await expect(session.api('/api/me')).rejects.toMatchObject({ status: 401 })
    expect(fetcher).toHaveBeenCalledTimes(4)
    expect(session.user).toBeNull(); expect(sessionStorage.length).toBe(0)
  })
  it('clears rejected refresh, redirects bootstrap to a logged-out state and never loops', async () => {
    sessionStorage.setItem(REFRESH_KEY, 'a'.repeat(43))
    const fetcher = vi.fn().mockResolvedValue(denied()); vi.stubGlobal('fetch', fetcher)
    const session = useSession(); await session.ensureReady()
    expect(session.user).toBeNull(); expect(sessionStorage.length).toBe(0)
    await expect(session.api('/api/me')).rejects.toBeInstanceOf(ApiError)
    expect(fetcher).toHaveBeenCalledTimes(1)
    expect(session.notice).toContain('fixture-trace')
  })
  it('does not resurrect a session when refresh finishes after logout', async () => {
    const gate = deferred<Response>()
    const fetcher = vi.fn().mockResolvedValueOnce(response(tokens())).mockImplementationOnce(() => gate.promise)
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetcher)
    const session = useSession(); await session.login('buyer', 'fixture-password-123')
    const pending = session.refresh(); const rejected = expect(pending).rejects.toMatchObject({ code: 'SESSION_CHANGED' })
    await session.logout(); gate.resolve(response(tokens('late-access', 'b'.repeat(43))))
    await rejected
    expect(session.user).toBeNull(); expect(sessionStorage.length).toBe(0)
  })
  it('does not restore user data from a protected response completing after logout', async () => {
    const gate = deferred<Response>()
    const fetcher = vi.fn().mockResolvedValueOnce(response(tokens())).mockImplementationOnce(() => gate.promise)
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetcher)
    const session = useSession(); await session.login('buyer', 'fixture-password-123')
    const pending = session.reloadUser(); const rejected = expect(pending).rejects.toMatchObject({ code: 'SESSION_CHANGED' })
    await vi.waitFor(() => expect(fetcher).toHaveBeenCalledTimes(2))
    await session.logout(); gate.resolve(response(tokens().user)); await rejected
    expect(session.user).toBeNull()
  })
  it('clears local state on network logout failure without claiming server revocation', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(response(tokens())).mockRejectedValueOnce(new TypeError('offline')))
    const session = useSession(); await session.login('buyer', 'fixture-password-123'); await session.logout()
    expect(session.user).toBeNull(); expect(sessionStorage.length).toBe(0)
    expect(session.notice).toContain('服务器注销未确认')
  })
  it('rejects malformed token payloads at the runtime boundary', () => {
    expect(() => parseTokens({ ...tokens(), user: { id: 1, role: 'ROOT' } })).toThrow()
    expect(() => parseTokens({ ...tokens(), refreshToken: 'short' })).toThrow()
  })
})
