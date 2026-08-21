import type { GuideResponse, ProblemDetail } from './guideTypes'

export class GuideApiError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'GuideApiError'
  }
}

export async function createGuide(repositoryUrl: string): Promise<GuideResponse> {
  let response: Response

  try {
    response = await fetch('/api/guides', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ repositoryUrl }),
    })
  } catch {
    throw new GuideApiError('暂时无法连接导览服务，请确认后端已启动后重试。')
  }

  if (!response.ok) {
    const problem = await readProblemDetail(response)
    throw new GuideApiError(problem.detail ?? '导览请求失败了，请稍后重试。')
  }

  return response.json() as Promise<GuideResponse>
}

async function readProblemDetail(response: Response): Promise<ProblemDetail> {
  try {
    return (await response.json()) as ProblemDetail
  } catch {
    return {}
  }
}
