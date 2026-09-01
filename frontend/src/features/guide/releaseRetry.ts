import type { GuideResponse, ReleaseRetryResponse } from './guideTypes.ts'

export function applyReleaseRetry(
  guide: GuideResponse,
  retry: ReleaseRetryResponse,
): GuideResponse {
  const nonReleaseEvidence = Object.fromEntries(
    Object.entries(guide.evidence).filter(([, evidence]) =>
      evidence.type !== 'RELEASE' && evidence.type !== 'RELEASE_ASSET'),
  )
  return {
    ...guide,
    releases: retry.releases,
    evidence: { ...nonReleaseEvidence, ...retry.evidence },
  }
}

export function isCurrentReleaseRetry(
  requestedCanonicalUrl: string,
  requestedGuideVersion: number,
  currentGuide: Pick<GuideResponse, 'repository'> | null,
  currentGuideVersion: number,
): boolean {
  return requestedGuideVersion === currentGuideVersion
    && currentGuide?.repository.canonicalUrl === requestedCanonicalUrl
}
