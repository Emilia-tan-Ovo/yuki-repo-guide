import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  GuideApiError,
  GuideAuthenticationRequiredError,
  retryReleases,
} from '../src/features/guide/guideApi.ts'
import { applyReleaseRetry, isCurrentReleaseRetry } from '../src/features/guide/releaseRetry.ts'
import type { GuideResponse, ReleaseRetryResponse } from '../src/features/guide/guideTypes.ts'

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('Release retry', () => {
  it('preserves the non-retryable 422 contract without retrying automatically', async () => {
    const requests: Array<{ input: RequestInfo | URL; init?: RequestInit }> = []
    const responses = [
      jsonResponse({ headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'csrf-token' }),
      jsonResponse({
        title: 'Release 历史超出支持范围',
        status: 422,
        detail: '该仓库的 Release 历史超过 1000 条，暂不支持完整导览。',
        code: 'RELEASE_HISTORY_UNSUPPORTED',
        retryable: false,
      }, 422),
    ]
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      requests.push({ input, init })
      return responses.shift() as Response
    }))

    await expect(retryReleases('https://github.com/octo/example')).rejects.toMatchObject({
      name: 'GuideApiError',
      code: 'RELEASE_HISTORY_UNSUPPORTED',
      status: 422,
    } satisfies Partial<GuideApiError>)

    expect(requests).toHaveLength(2)
    expect(requests[1]?.input).toBe('/api/guides/releases/retry')
  })

  it('preserves Retry-After and identifies an expired Session for recovery', async () => {
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) =>
      input === '/api/auth/csrf'
        ? jsonResponse({ headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'csrf-token' })
        : jsonResponse(
        { code: 'GITHUB_RATE_LIMITED', retryAfterSeconds: 90 },
        429,
        { 'Retry-After': '120' },
      )))

    await expect(retryReleases('https://github.com/octo/example')).rejects.toMatchObject({
      code: 'GITHUB_RATE_LIMITED',
      status: 429,
      retryAfterSeconds: 120,
    } satisfies Partial<GuideApiError>)

    vi.unstubAllGlobals()
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) =>
      input === '/api/auth/csrf'
        ? jsonResponse({ headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'csrf-token' })
        : new Response(null, { status: 401 })))

    await expect(retryReleases('https://github.com/octo/example'))
      .rejects.toBeInstanceOf(GuideAuthenticationRequiredError)
  })

  it('replaces only release evidence and rejects stale retry results', () => {
    const guide = {
      repository: {
        owner: 'octo', name: 'example', description: null,
        canonicalUrl: 'https://github.com/octo/example', stars: 1,
        createdAt: '2026-01-01T00:00:00Z', pushedAt: null,
        projectWebsiteUrl: null, evidenceId: 'repository-metadata',
      },
      readme: { status: 'NOT_PROVIDED', candidates: [], truncated: false, failure: null },
      languages: { status: 'NOT_PROVIDED', items: [], failure: null, evidenceId: null },
      releases: {
        status: 'AVAILABLE', latestStable: null, latestPrerelease: null, failure: null,
      },
      evidence: {
        'repository-metadata': { type: 'REPOSITORY', source: 'GitHub', languages: [] },
        'old-release': { type: 'RELEASE', source: 'GitHub Releases REST API', languages: [] },
        'old-asset': { type: 'RELEASE_ASSET', source: 'GitHub Releases REST API', languages: [] },
      },
    } satisfies GuideResponse
    const retried = {
      releases: {
        status: 'AVAILABLE', latestStable: null, latestPrerelease: null, failure: null,
      },
      evidence: {
        'new-release': { type: 'RELEASE', source: 'GitHub Releases REST API', languages: [] },
      },
    } satisfies ReleaseRetryResponse

    const updated = applyReleaseRetry(guide, retried)

    expect(updated.repository).toBe(guide.repository)
    expect(updated.readme).toBe(guide.readme)
    expect(updated.languages).toBe(guide.languages)
    expect(Object.keys(updated.evidence).sort()).toEqual(['new-release', 'repository-metadata'])
    expect(isCurrentReleaseRetry('https://github.com/octo/example', 3, guide, 3)).toBe(true)
    expect(isCurrentReleaseRetry('https://github.com/octo/example', 2, guide, 3)).toBe(false)
  })
})

function jsonResponse(
  body: unknown,
  status = 200,
  headers: Record<string, string> = {},
): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...headers },
  })
}
