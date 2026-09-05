import type {
  GuideErrorCode,
  GuideResponse,
  LanguageRetryResponse,
  ReadmeRetryResponse,
  ReleaseRetryResponse,
  ProblemDetail,
} from './guideTypes.ts'
import { fetchWithCsrfRetry } from '../auth/authApi.ts'

export class GuideApiError extends Error {
  readonly code?: string
  readonly status?: number
  readonly retryAfterSeconds?: number

  constructor(
    message: string,
    code?: string,
    status?: number,
    retryAfterSeconds?: number,
  ) {
    super(message)
    this.name = 'GuideApiError'
    this.code = code
    this.status = status
    this.retryAfterSeconds = retryAfterSeconds
  }
}

export class GuideAuthenticationRequiredError extends GuideApiError {
  constructor() {
    super('登录状态已过期，请重新输入试用访问码。')
    this.name = 'GuideAuthenticationRequiredError'
  }
}

export async function createGuide(repositoryUrl: string): Promise<GuideResponse> {
  return requestGuide<GuideResponse>(() => sendGuideRequest(repositoryUrl))
}

export async function retryLanguages(canonicalUrl: string): Promise<LanguageRetryResponse> {
  return requestGuide<LanguageRetryResponse>(() => sendLanguageRetryRequest(canonicalUrl))
}

export async function retryReadme(canonicalUrl: string): Promise<ReadmeRetryResponse> {
  return requestGuide<ReadmeRetryResponse>(() => sendReadmeRetryRequest(canonicalUrl))
}

export async function retryReleases(canonicalUrl: string): Promise<ReleaseRetryResponse> {
  return requestGuide<ReleaseRetryResponse>(() => sendReleaseRetryRequest(canonicalUrl))
}

async function requestGuide<T>(sendRequest: () => Promise<Response>): Promise<T> {
  let response: Response

  try {
    response = await sendRequest()
  } catch {
    throw new GuideApiError('网络连接似乎不稳定，请检查连接后重试。')
  }

  if (response.status === 401) {
    throw new GuideAuthenticationRequiredError()
  }

  if (!response.ok) {
    const problem = await readProblemDetail(response)
    const retryAfterSeconds = retryAfterFrom(response, problem)
    throw new GuideApiError(
      messageForCode(problem.code as GuideErrorCode | undefined, problem.detail),
      problem.code,
      response.status,
      retryAfterSeconds,
    )
  }

  return response.json() as Promise<T>
}

function sendGuideRequest(repositoryUrl: string): Promise<Response> {
  return fetchWithCsrfRetry('/api/guides', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ repositoryUrl }),
  })
}

function sendLanguageRetryRequest(canonicalUrl: string): Promise<Response> {
  return fetchWithCsrfRetry('/api/guides/languages/retry', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ canonicalUrl }),
  })
}

function sendReadmeRetryRequest(canonicalUrl: string): Promise<Response> {
  return fetchWithCsrfRetry('/api/guides/readme/retry', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ canonicalUrl }),
  })
}

function sendReleaseRetryRequest(canonicalUrl: string): Promise<Response> {
  return fetchWithCsrfRetry('/api/guides/releases/retry', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ canonicalUrl }),
  })
}

export function messageForCode(code?: GuideErrorCode, fallback?: string): string {
  switch (code) {
    case 'REPOSITORY_NOT_ACCESSIBLE':
      return '仓库不存在或仓库为私有，请检查地址后重试。'
    case 'GITHUB_RATE_LIMITED':
      return 'GitHub 暂时限制了请求，请稍后重试。'
    case 'GITHUB_UPSTREAM_FAILURE':
      return 'GitHub 上游暂时出现故障，请稍后重试。'
    case 'GITHUB_SERVICE_UNAVAILABLE':
      return 'GitHub 暂时无法提供数据，这不是你的操作造成的，请稍后重试。'
    case 'GITHUB_TIMEOUT':
      return '连接 GitHub 超时，请检查网络状况或稍后重试。'
    case 'README_CONTENT_UNSUPPORTED':
      return '当前 README 内容格式暂不受支持，无法识别在线体验入口。'
    case 'RELEASE_HISTORY_UNSUPPORTED':
      return '该仓库的 Release 历史超过 1000 条，暂不支持完整导览。'
    default:
      return fallback ?? '导览请求失败了，请稍后重试。'
  }
}

export function messageForReadmeCode(code?: string, fallback?: string): string {
  if (code === 'REPOSITORY_NOT_ACCESSIBLE') {
    return 'GitHub 暂时无法读取这个仓库的 README，请稍后重试。'
  }
  return messageForCode(code as GuideErrorCode | undefined, fallback)
}

export function messageForLanguageCode(code?: string, fallback?: string): string {
  if (code === 'REPOSITORY_NOT_ACCESSIBLE') {
    return 'GitHub 暂时无法读取这个仓库的语言统计，请稍后重试。'
  }
  return messageForCode(code as GuideErrorCode | undefined, fallback)
}

export function messageForReleaseCode(code?: string, fallback?: string): string {
  if (code === 'REPOSITORY_NOT_ACCESSIBLE') {
    return 'GitHub 暂时无法读取这个仓库的 Release，请稍后重试。'
  }
  return messageForCode(code as GuideErrorCode | undefined, fallback)
}

function retryAfterFrom(response: Response, problem: ProblemDetail): number | undefined {
  const headerValue = response.headers.get('Retry-After')
  const parsedHeader = headerValue === null ? Number.NaN : Number(headerValue)
  if (Number.isFinite(parsedHeader) && parsedHeader > 0) {
    return Math.ceil(parsedHeader)
  }
  return problem.retryAfterSeconds && problem.retryAfterSeconds > 0
    ? Math.ceil(problem.retryAfterSeconds)
    : undefined
}

async function readProblemDetail(response: Response): Promise<ProblemDetail> {
  try {
    return (await response.json()) as ProblemDetail
  } catch {
    return {}
  }
}
