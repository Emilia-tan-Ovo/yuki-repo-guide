import type { GuideResponse, LanguageRetryResponse } from './guideTypes.ts'

export interface RetryAvailability {
  disabled: boolean
  remainingSeconds: number
  message: string
}

export function createRetryDeadline(
  retryAfterSeconds?: number | null,
  now = Date.now(),
): number | null {
  if (!retryAfterSeconds || retryAfterSeconds <= 0) {
    return null
  }
  return now + Math.ceil(retryAfterSeconds) * 1_000
}

export function retryAvailability(
  retryAvailableAt: number | null,
  now = Date.now(),
): RetryAvailability {
  if (retryAvailableAt === null || retryAvailableAt <= now) {
    return { disabled: false, remainingSeconds: 0, message: '请点击重试' }
  }

  const remainingSeconds = Math.ceil((retryAvailableAt - now) / 1_000)
  const message = remainingSeconds >= 60
    ? `约 ${Math.ceil(remainingSeconds / 60)} 分钟后可重试`
    : `约 ${remainingSeconds} 秒后可重试`
  return { disabled: true, remainingSeconds, message }
}

export function applyLanguageRetry(
  guide: GuideResponse,
  retry: LanguageRetryResponse,
): GuideResponse {
  return {
    ...guide,
    repository: guide.repository,
    languages: retry.languages,
    evidence: { ...guide.evidence, ...retry.evidence },
  }
}

export function isCurrentLanguageRetry(
  requestedCanonicalUrl: string,
  requestedGuideVersion: number,
  currentGuide: Pick<GuideResponse, 'repository'> | null,
  currentGuideVersion: number,
): boolean {
  return requestedGuideVersion === currentGuideVersion
    && currentGuide?.repository.canonicalUrl === requestedCanonicalUrl
}
