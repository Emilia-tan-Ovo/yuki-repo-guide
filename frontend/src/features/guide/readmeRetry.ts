import type { GuideResponse, ReadmeRetryResponse } from './guideTypes.ts'

export function applyReadmeRetry(
  guide: GuideResponse,
  retry: ReadmeRetryResponse,
): GuideResponse {
  const nonReadmeEvidence = Object.fromEntries(
    Object.entries(guide.evidence).filter(([, evidence]) => evidence.type !== 'README'),
  )
  return {
    ...guide,
    repository: guide.repository,
    readme: retry.readme,
    languages: guide.languages,
    evidence: { ...nonReadmeEvidence, ...retry.evidence },
  }
}

export function isCurrentReadmeRetry(
  requestedCanonicalUrl: string,
  requestedGuideVersion: number,
  currentGuide: Pick<GuideResponse, 'repository'> | null,
  currentGuideVersion: number,
): boolean {
  return requestedGuideVersion === currentGuideVersion
    && currentGuide?.repository.canonicalUrl === requestedCanonicalUrl
}
