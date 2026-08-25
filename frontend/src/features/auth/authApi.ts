import type { AuthProblemDetail, CsrfTokenResponse, SessionResponse } from './authTypes'

let csrfToken: CsrfTokenResponse | null = null

export class AuthApiError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'AuthApiError'
  }
}

export async function initializeAuthentication(): Promise<SessionResponse> {
  await refreshCsrfToken()
  return getSession()
}

export async function refreshCsrfToken(): Promise<void> {
  const response = await fetchAuthResource('/api/auth/csrf')
  if (!response.ok) {
    throw await authError(response, '暂时无法初始化安全会话，请稍后重试。')
  }
  csrfToken = (await response.json()) as CsrfTokenResponse
}

export async function getSession(): Promise<SessionResponse> {
  const response = await fetchAuthResource('/api/auth/session')
  if (!response.ok) {
    throw await authError(response, '暂时无法确认登录状态，请稍后重试。')
  }
  return response.json() as Promise<SessionResponse>
}

export async function login(accessCode: string): Promise<void> {
  const response = await fetchWithCsrfRetry('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ accessCode }),
  })
  if (!response.ok) {
    throw await authError(response, '访问码验证失败，请稍后重试。')
  }
  await refreshCsrfToken()
}

export async function logout(): Promise<void> {
  const response = await fetchWithCsrfRetry('/api/auth/logout', { method: 'POST' })
  if (!response.ok) {
    throw await authError(response, '退出失败，请稍后重试。')
  }
  await refreshCsrfToken()
}

export async function fetchWithCsrf(input: RequestInfo | URL, init: RequestInit): Promise<Response> {
  if (!csrfToken) {
    await refreshCsrfToken()
  }

  const headers = new Headers(init.headers)
  headers.set(csrfToken!.headerName, csrfToken!.token)
  try {
    return await fetch(input, {
      ...init,
      headers,
      credentials: 'include',
    })
  } catch {
    throw new AuthApiError('暂时无法连接导览服务，请确认后端已启动后重试。')
  }
}

export async function fetchWithCsrfRetry(
  input: RequestInfo | URL,
  init: RequestInit,
): Promise<Response> {
  let response = await fetchWithCsrf(input, init)
  if (response.status === 403) {
    await refreshCsrfToken()
    response = await fetchWithCsrf(input, init)
  }
  return response
}

async function fetchAuthResource(input: RequestInfo | URL): Promise<Response> {
  try {
    return await fetch(input, { credentials: 'include' })
  } catch {
    throw new AuthApiError('暂时无法连接导览服务，请确认后端已启动后重试。')
  }
}

async function authError(response: Response, fallback: string): Promise<AuthApiError> {
  const problem = await readProblem(response)
  return new AuthApiError(problem.detail ?? fallback)
}

async function readProblem(response: Response): Promise<AuthProblemDetail> {
  try {
    return (await response.json()) as AuthProblemDetail
  } catch {
    return {}
  }
}
