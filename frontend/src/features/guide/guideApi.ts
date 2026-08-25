import type { GuideResponse, ProblemDetail } from './guideTypes'
import { fetchWithCsrfRetry } from '../auth/authApi'

export class GuideApiError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'GuideApiError'
  }
}

export class GuideAuthenticationRequiredError extends GuideApiError {
  constructor() {
    super('登录状态已过期，请重新输入试用访问码。')
    this.name = 'GuideAuthenticationRequiredError'
  }
}

export async function createGuide(repositoryUrl: string): Promise<GuideResponse> {
  let response: Response

  try {
    response = await sendGuideRequest(repositoryUrl)
  } catch {
    throw new GuideApiError('暂时无法连接导览服务，请确认后端已启动后重试。')
  }

  if (response.status === 401) {
    throw new GuideAuthenticationRequiredError()
  }

  if (!response.ok) {
    const problem = await readProblemDetail(response)
    throw new GuideApiError(problem.detail ?? '导览请求失败了，请稍后重试。')
  }

  return response.json() as Promise<GuideResponse>
}

function sendGuideRequest(repositoryUrl: string): Promise<Response> {
  return fetchWithCsrfRetry('/api/guides', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ repositoryUrl }),
  })
}

async function readProblemDetail(response: Response): Promise<ProblemDetail> {
  try {
    return (await response.json()) as ProblemDetail
  } catch {
    return {}
  }
}
