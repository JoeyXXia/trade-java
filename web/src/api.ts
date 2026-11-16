export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string, public traceId = '') { super(message) }
}
export function record(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}
export function describe(error: unknown): string {
  return error instanceof ApiError
    ? `${error.message}（${error.code}）${error.traceId ? ` · 追踪 ${error.traceId}` : ''}`
    : '响应格式异常，请重新登录或稍后重试。'
}
export async function request(path: string, init: RequestInit = {}): Promise<unknown> {
  let response: Response
  try {
    response = await fetch(path, { ...init, credentials: 'omit', cache: 'no-store', signal: AbortSignal.timeout(10000) })
  } catch { throw new ApiError(0, 'NETWORK_ERROR', '无法连接服务，请检查网络后重试') }
  let data: unknown = null
  try { if (response.status !== 204) data = await response.json() } catch { /* Reject malformed successes at the type boundary. */ }
  if (!response.ok) {
    const body = record(data) ? data : {}
    throw new ApiError(response.status, typeof body.code === 'string' ? body.code : 'HTTP_ERROR',
      typeof body.message === 'string' ? body.message : `请求失败 ${response.status}`,
      typeof body.traceId === 'string' ? body.traceId : response.headers.get('X-Trace-Id') ?? '')
  }
  return data
}
export const jsonBody = (body: unknown): RequestInit => ({
  method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body),
})
export type Role = 'BUYER' | 'OPERATOR' | 'ADMIN'
export interface User { id: string; loginName: string; role: Role }
export function parseUser(data: unknown): User {
  if (!record(data) || typeof data.id !== 'string' || !/^[1-9]\d*$/.test(data.id)
    || typeof data.loginName !== 'string' || typeof data.role !== 'string'
    || !['BUYER', 'OPERATOR', 'ADMIN'].includes(data.role)) throw new Error('Invalid user response')
  return { id: data.id, loginName: data.loginName, role: data.role as Role }
}
export function parseTokens(data: unknown) {
  if (!record(data) || typeof data.accessToken !== 'string' || !data.accessToken
    || data.tokenType !== 'Bearer' || typeof data.expiresIn !== 'number' || data.expiresIn <= 0
    || typeof data.refreshToken !== 'string' || !/^[A-Za-z0-9_-]{43}$/.test(data.refreshToken)) throw new Error('Invalid token response')
  return { accessToken: data.accessToken, refreshToken: data.refreshToken, user: parseUser(data.user) }
}
